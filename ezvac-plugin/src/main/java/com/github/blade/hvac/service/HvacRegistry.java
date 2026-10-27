package com.github.blade.hvac.service;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The sole owner of runtime device state and unified atomic persistence. */
public final class HvacRegistry {
    /**
     * Format 7 (3.0) links devices to systems instead of naming groups: each
     * device saves its system's key (an old group name becomes its system's
     * key), equipment saves its tier, and meta.next-system numbers new
     * systems. Format 6 files load as they are; the bump keeps the 2.x
     * original as hvac.yml.format6.bak, and 2.x refuses a 3.0 file instead
     * of misreading it.
     *
     * <p>Format 8 (3.1) gives equipment its tier: 1 single-stage, 2 two-stage,
     * 3 variable. A 3.0 file saved every unit as 2, which no longer means
     * what those units are, so an older file's equipment is all variable: the
     * full capacity every unit had before tiers.
     */
    public static final int FORMAT_VERSION = 8;

    public enum DeviceKind {
        THERMOSTAT, EQUIPMENT, VENT, THERMOMETER, RPM_MONITOR, SETTINGS_PANEL, DEHUMIDIFIER, HUMIDIFIER,
        AIR_PURIFIER, SENSOR, SENSOR_HUB, NONE
    }

    /**
     * @param removedUnits dehumidifiers and humidifiers whose blocks were gone,
     *                     so their airflow zones can be dropped as well
     */
    public record PruneResult(Set<SystemId> invalidatedSystems, int removedDevices, List<ClimateUnit> removedUnits) {}

    /** What a system holds, for messages. */
    public record Members(Thermostat thermostat, int units, int vents, int panels, int monitors) {
        public boolean empty() { return thermostat == null && units + vents + panels + monitors == 0; }
    }

    /**
     * What a merge moved.
     *
     * @param unlinkedPanel the absorbed system's settings panel, left unlinked
     *                      because the system it joined already had one; or null
     */
    public record MergeResult(int units, int vents, int monitors, boolean movedPanel, SettingsPanel unlinkedPanel) {}

    /**
     * The few server services persistence needs. The plugin supplies the real
     * ones; tests supply a plain folder and run work inline, which lets the
     * registry load and save real hvac.yml files without a running server.
     */
    public interface Host {
        File dataFolder();
        Logger logger();
        boolean isEnabled();
        void runAsync(Runnable task);
        /** May throw IllegalStateException while the plugin is shutting down. */
        void runSync(Runnable task);

        static Host of(HvacPlugin plugin) {
            Objects.requireNonNull(plugin, "plugin");
            return new Host() {
                @Override public File dataFolder() { return plugin.getDataFolder(); }
                @Override public Logger logger() { return plugin.getLogger(); }
                @Override public boolean isEnabled() { return plugin.isEnabled(); }
                @Override public void runAsync(Runnable task) {
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
                }
                @Override public void runSync(Runnable task) {
                    plugin.getServer().getScheduler().runTask(plugin, task);
                }
            };
        }
    }

    /** Sections this build understands; anything else is preserved verbatim. */
    private static final Set<String> KNOWN_SECTIONS = Set.of(
            "thermostats", "equipment", "cleanup", "vents",
            "thermometers", "rpmMonitors", "settingsPanels", "dehumidifiers", "humidifiers",
            "purifiers", "sensors", "sensorHubs");

    private final Host host;
    private final HvacSettings settings;
    private final Map<BlockKey, Thermostat> thermostats = new HashMap<>();
    private final Map<BlockKey, EquipmentUnit> equipment = new HashMap<>();
    private final Map<BlockKey, ClimateVent> vents = new HashMap<>();
    private final Map<BlockKey, Thermometer> thermometers = new HashMap<>();
    private final Map<BlockKey, RpmMonitor> rpmMonitors = new HashMap<>();
    private final Map<BlockKey, SettingsPanel> settingsPanels = new HashMap<>();
    /** Keyed by control sign. */
    private final Map<BlockKey, Dehumidifier> dehumidifiers = new HashMap<>();
    /** Keyed by control sign; saved in their own section. */
    private final Map<BlockKey, Humidifier> humidifiers = new HashMap<>();
    /** Keyed by control sign; saved in their own section (3.2). */
    private final Map<BlockKey, AirPurifier> purifiers = new HashMap<>();
    /** Keyed by button. */
    private final Map<BlockKey, Sensor> sensors = new HashMap<>();
    /** Keyed by sign. Few per world, so lookups by linked sensor simply scan them. */
    private final Map<BlockKey, SensorHub> sensorHubs = new HashMap<>();
    private final List<EquipmentUnit> pendingCleanup = new ArrayList<>();
    private final Map<String, List<Map<String, Object>>> preservedRecords = new HashMap<>();
    private final File dataFile;
    private final File backupFile;
    private boolean dirty;
    /**
     * False once a load has failed for any reason - a newer format, or a file
     * that cannot be read. Nothing may then be written for the rest of the
     * run, not even the save onDisable makes, because the registry is empty
     * and the file on disk is the only copy of the data. Before this applied
     * to unreadable files, a typo in hvac.yml with no usable backup was
     * replaced by an empty save on shutdown, and the next save rotated the
     * original out of hvac.yml.bak too, losing every device.
     */
    private boolean writable = true;
    /** The older format just upgraded from, backed up once before the first save; 0 when none. */
    private int upgradedFromFormat;
    /** The number the next new system gets: #1, #2 and so on, never reused. */
    private long nextSystem = 1;

    // Secondary indexes over the maps above. The maps stay the source of
    // truth; every mutation in this class keeps the indexes in step, and
    // indexDiscrepancy() rebuilds them from scratch in tests to prove it.
    // Before these existed, group lookups streamed and sorted every device,
    // often once per unit per controller cycle, and block-change events
    // scanned every vent and every unit on the server.
    private final Map<SystemId, TreeMap<BlockKey, Thermostat>> thermostatsBySystem = new HashMap<>();
    private final Map<SystemId, TreeMap<BlockKey, EquipmentUnit>> equipmentBySystem = new HashMap<>();
    private final Map<SystemId, TreeMap<BlockKey, ClimateVent>> ventsBySystem = new HashMap<>();
    private final Map<SystemId, TreeMap<BlockKey, SettingsPanel>> panelsBySystem = new HashMap<>();
    private final Map<SystemId, TreeMap<BlockKey, RpmMonitor>> monitorsBySystem = new HashMap<>();
    /** Vents per world on an x/z grid at least as wide as the airflow search radius. */
    private final Map<UUID, Long2ObjectOpenHashMap<List<ClimateVent>>> ventCells = new HashMap<>();
    /** Owned outputs of active and pending-cleanup units, kept current by each unit's watcher. */
    private final Map<BlockKey, List<EquipmentUnit>> unitsByOwnedOutput = new HashMap<>();
    /** Each standalone unit's iron block and water block, so neither can be registered as anything else. */
    private final Map<BlockKey, ClimateUnit> unitParts = new HashMap<>();
    /** Standalone units per world on the vent grid, by iron block: their zones reach no farther than a vent's. */
    private final Map<UUID, Long2ObjectOpenHashMap<List<ClimateUnit>>> unitCells = new HashMap<>();
    private final int ventCellShift;
    private volatile boolean writeInProgress;
    /** Serializes writers so a shutdown save can never race an in-flight async one. */
    private final Object writeLock = new Object();

    public HvacRegistry(HvacPlugin plugin, HvacSettings settings) {
        this(Host.of(plugin), settings);
    }

    public HvacRegistry(Host host, HvacSettings settings) {
        this.host = Objects.requireNonNull(host, "host");
        this.settings = Objects.requireNonNull(settings, "settings");
        if (!host.dataFolder().isDirectory() && !host.dataFolder().mkdirs())
            throw new IllegalStateException("Could not create EZvac data directory");
        dataFile = new File(host.dataFolder(), "hvac.yml");
        backupFile = new File(host.dataFolder(), "hvac.yml.bak");
        // Cells at least as wide as the airflow search radius keep a lookup to 2x2 cells.
        int reach = settings.airflowMaximumRange() + 2;
        int shift = 4;
        while ((1 << shift) < reach) shift++;
        ventCellShift = shift;
    }

    public void load() {
        clear();
        if (!dataFile.isFile()) return;
        try {
            loadExisting();
        } catch (RuntimeException failure) {
            writable = false;
            throw failure;
        }
    }

    private void loadExisting() {
        YamlConfiguration configuration = loadWithRecovery();
        checkFormat(configuration);
        preserveUnknownSections(configuration);
        readSection(configuration, "thermostats", map -> Thermostat.fromMap(map, settings),
                this::loadThermostat);
        readSection(configuration, "equipment", EquipmentUnit::fromMap,
                this::loadEquipment);
        readSection(configuration, "cleanup", EquipmentUnit::fromMap, pendingCleanup::add);
        readSection(configuration, "vents", ClimateVent::fromMap,
                this::loadVent);
        readSection(configuration, "thermometers", Thermometer::fromMap,
                this::loadThermometer);
        readSection(configuration, "rpmMonitors", RpmMonitor::fromMap,
                this::loadRpmMonitor);
        readSection(configuration, "settingsPanels", SettingsPanel::fromMap,
                this::loadSettingsPanel);
        readSection(configuration, "dehumidifiers", Dehumidifier::fromMap,
                this::loadDehumidifier);
        readSection(configuration, "humidifiers", Humidifier::fromMap,
                this::loadHumidifier);
        readSection(configuration, "purifiers", AirPurifier::fromMap,
                value -> loadByPosition(purifiers, "purifiers", value.position(), value, value.toMap()));
        readSection(configuration, "sensors", Sensor::fromMap,
                value -> loadByPosition(sensors, "sensors", value.position(), value, value.toMap()));
        readSection(configuration, "sensorHubs", SensorHub::fromMap,
                value -> loadByPosition(sensorHubs, "sensorHubs", value.position(), value, value.toMap()));
        if (upgradedFromFormat > 0 && upgradedFromFormat < 8) {
            for (EquipmentUnit unit : equipment.values()) unit.setTier(Tier.LEGACY);
            for (EquipmentUnit unit : pendingCleanup) unit.setTier(Tier.LEGACY);
        }
        resolveLoadedConflicts();
        rebuildIndexes();
        nextSystem = Math.max(1L, configuration.getLong("meta.next-system", 1L));
        // A hand-edited or partly restored file may hold a number already given out.
        for (SystemId system : allSystems()) nextSystem = Math.max(nextSystem, system.number() + 1);
        dirty = false;
    }

    /**
     * EZvac supports upgrades only. A file from a newer format is refused
     * outright: loading it here and saving it back in this build's format
     * would silently discard whatever the newer version stored. A file from an
     * older format loads normally and is backed up once before the first save,
     * so a manual rollback stays possible after a format change. A missing
     * format counts as current; every release since 1.0 has written one.
     */
    private void checkFormat(YamlConfiguration configuration) {
        int format = configuration.getInt("meta.format", FORMAT_VERSION);
        if (format > FORMAT_VERSION) {
            writable = false;
            throw new IllegalStateException("hvac.yml is format " + format + ", written by a newer"
                    + " version of EZvac, but this build understands format " + FORMAT_VERSION
                    + ". Downgrading EZvac is not supported, so the file has been left untouched"
                    + " and EZvac will not start. Update EZvac, or restore the hvac.yml.format"
                    + FORMAT_VERSION + ".bak backup made when the newer version first upgraded it.");
        }
        if (format < FORMAT_VERSION) upgradedFromFormat = format;
    }

    /**
     * Captures sections written by a newer build so a downgrade cannot delete
     * them. save() rebuilds the file from scratch, so anything not read here
     * would be lost the first time this build saved.
     */
    private void preserveUnknownSections(YamlConfiguration configuration) {
        for (String section : configuration.getKeys(false)) {
            if (section.equals("meta") || KNOWN_SECTIONS.contains(section)) continue;
            ConfigurationSection root = configuration.getConfigurationSection(section);
            if (root == null) continue;
            int preserved = 0;
            for (String key : root.getKeys(false)) {
                ConfigurationSection child = root.getConfigurationSection(key);
                if (child == null) continue;
                preservedRecords.computeIfAbsent(section, ignored -> new ArrayList<>())
                        .add(new LinkedHashMap<>(child.getValues(false)));
                preserved++;
            }
            if (preserved > 0)
                host.logger().warning("Preserving " + preserved + " unrecognised '" + section
                        + "' record(s) from a newer EZvac; they are kept but not active.");
        }
    }

    private YamlConfiguration loadWithRecovery() {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(dataFile);
            return configuration;
        } catch (IOException | InvalidConfigurationException primary) {
            if (!backupFile.isFile())
                throw new IllegalStateException("hvac.yml is unreadable and no backup exists. The file"
                        + " has been left untouched and EZvac will not start; fix the error reported"
                        + " below, or restore a copy of hvac.yml, then restart.", primary);
            try {
                configuration = new YamlConfiguration();
                configuration.load(backupFile);
            } catch (IOException | InvalidConfigurationException backupFailure) {
                primary.addSuppressed(backupFailure);
                throw new IllegalStateException("Both hvac.yml and hvac.yml.bak are unreadable. Both"
                        + " have been left untouched and EZvac will not start; fix the errors reported"
                        + " below, or restore a copy of hvac.yml, then restart.", primary);
            }
            // The unreadable file may hold changes newer than the backup, or hand
            // edits worth repairing, so it is kept aside rather than overwritten.
            File unreadable = new File(host.dataFolder(), "hvac.yml.unreadable-" + System.currentTimeMillis());
            try {
                Files.copy(dataFile.toPath(), unreadable.toPath());
                Files.copy(backupFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copyFailure) {
                primary.addSuppressed(copyFailure);
                throw new IllegalStateException("hvac.yml is unreadable and could not be set aside for"
                        + " recovery from hvac.yml.bak; nothing was changed and EZvac will not start.", primary);
            }
            host.logger().severe("Recovered unreadable hvac.yml from hvac.yml.bak; the unreadable file"
                    + " was kept as " + unreadable.getName());
            return configuration;
        }
    }

    private <T> void readSection(YamlConfiguration configuration, String section,
                                 Function<Map<String, Object>, T> parser,
                                 java.util.function.Consumer<T> destination) {
        ConfigurationSection root = configuration.getConfigurationSection(section);
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection child = root.getConfigurationSection(key);
            if (child == null) continue;
            Map<String, Object> values = new LinkedHashMap<>(child.getValues(false));
            try {
                destination.accept(parser.apply(values));
            } catch (RuntimeException exception) {
                host.logger().warning("Preserving invalid " + section + "." + key
                        + ": " + exception.getMessage());
                preservedRecords.computeIfAbsent(section, ignored -> new ArrayList<>()).add(values);
            }
        }
    }

    public void save() { save(false); }

    /**
     * Builds the persistence snapshot on the calling thread, which must be the
     * main thread. Only the YAML dump and file replacement may move off it:
     * the dump is roughly 90% of the cost and touches nothing but the snapshot.
     */
    public void save(boolean synchronous) {
        if (!writable) return;
        if (!dirty && dataFile.isFile()) return;
        // A slow disk must never queue a second write against the same files.
        // Leaving the registry dirty lets the next periodic save retry.
        if (!synchronous && writeInProgress) return;
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("meta.format", FORMAT_VERSION);
        configuration.set("meta.next-system", nextSystem);
        write(configuration, "thermostats", thermostats.values(), Thermostat::position, Thermostat::toMap);
        write(configuration, "equipment", equipment.values(), EquipmentUnit::position, EquipmentUnit::toMap);
        write(configuration, "cleanup", pendingCleanup, EquipmentUnit::position, EquipmentUnit::toMap);
        write(configuration, "vents", vents.values(), ClimateVent::position, ClimateVent::toMap);
        write(configuration, "thermometers", thermometers.values(), Thermometer::position, Thermometer::toMap);
        write(configuration, "rpmMonitors", rpmMonitors.values(), RpmMonitor::position, RpmMonitor::toMap);
        write(configuration, "settingsPanels", settingsPanels.values(),
                SettingsPanel::position, SettingsPanel::toMap);
        write(configuration, "dehumidifiers", dehumidifiers.values(),
                Dehumidifier::position, Dehumidifier::toMap);
        write(configuration, "humidifiers", humidifiers.values(),
                Humidifier::position, Humidifier::toMap);
        write(configuration, "purifiers", purifiers.values(), AirPurifier::position, AirPurifier::toMap);
        write(configuration, "sensors", sensors.values(), Sensor::position, Sensor::toMap);
        write(configuration, "sensorHubs", sensorHubs.values(), SensorHub::position, SensorHub::toMap);
        for (Map.Entry<String, List<Map<String, Object>>> entry : preservedRecords.entrySet()) {
            int index = sectionSize(configuration, entry.getKey());
            for (Map<String, Object> record : entry.getValue())
                configuration.set(entry.getKey() + ".preserved" + index++, record);
        }

        dirty = false;
        if (!backUpBeforeUpgradeSave()) {
            dirty = true;
            return;
        }
        if (synchronous || !host.isEnabled()) {
            if (!writeSnapshot(configuration)) dirty = true;
            return;
        }
        writeInProgress = true;
        host.runAsync(() -> {
            boolean written;
            try {
                written = writeSnapshot(configuration);
            } finally {
                writeInProgress = false;
            }
            if (!written && host.isEnabled()) {
                try {
                    host.runSync(() -> dirty = true);
                } catch (IllegalStateException disabling) {
                    // The plugin shut down mid-write; onDisable already saved.
                }
            }
        });
    }

    /**
     * Copies a file from an older format aside before it is first overwritten.
     * An existing backup is never replaced: the first one is the true original.
     * Runs on the calling thread, before any write can be queued.
     */
    private boolean backUpBeforeUpgradeSave() {
        if (upgradedFromFormat == 0 || !dataFile.isFile()) return true;
        File backup = new File(host.dataFolder(), "hvac.yml.format" + upgradedFromFormat + ".bak");
        try {
            if (!backup.isFile()) {
                Files.copy(dataFile.toPath(), backup.toPath());
                host.logger().info("Upgraded hvac.yml from format " + upgradedFromFormat + " to "
                        + FORMAT_VERSION + "; the original was kept as " + backup.getName());
            }
            upgradedFromFormat = 0;
            return true;
        } catch (IOException exception) {
            host.logger().log(Level.SEVERE, "Could not back up hvac.yml before upgrading it;"
                    + " it will not be overwritten until the backup succeeds", exception);
            return false;
        }
    }

    /**
     * Dumps and atomically replaces hvac.yml. Safe off the main thread: the
     * snapshot is built from freshly allocated maps that nothing else mutates.
     */
    private boolean writeSnapshot(YamlConfiguration configuration) {
        synchronized (writeLock) {
            return replaceDataFile(configuration);
        }
    }

    private boolean replaceDataFile(YamlConfiguration configuration) {
        File temporary = new File(host.dataFolder(), "hvac.yml.tmp");
        try {
            Files.writeString(temporary.toPath(), configuration.saveToString(),
                    StandardCharsets.UTF_8);
            if (dataFile.isFile())
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            host.logger().log(Level.SEVERE, "Could not save hvac.yml", exception);
            return false;
        }
    }

    private static <T> void write(YamlConfiguration configuration, String section,
                                  Collection<T> values, Function<T, BlockKey> key,
                                  Function<T, Map<String, Object>> serializer) {
        List<T> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparing(key));
        int index = 0;
        for (T value : sorted) configuration.set(section + ".item" + index++, serializer.apply(value));
    }

    private static int sectionSize(YamlConfiguration configuration, String section) {
        ConfigurationSection root = configuration.getConfigurationSection(section);
        return root == null ? 0 : root.getKeys(false).size();
    }

    private void loadThermostat(Thermostat value) {
        Thermostat existing = thermostats.putIfAbsent(value.position(), value);
        if (existing != null) preserve("thermostats", value.toMap(), "duplicate thermostat position");
    }

    private void loadEquipment(EquipmentUnit value) {
        EquipmentUnit existing = equipment.putIfAbsent(value.position(), value);
        if (existing != null) preserve("equipment", value.toMap(), "duplicate equipment position");
    }

    private void loadVent(ClimateVent value) {
        ClimateVent existing = vents.putIfAbsent(value.position(), value);
        if (existing != null) preserve("vents", value.toMap(), "duplicate vent position");
    }

    private void loadThermometer(Thermometer value) {
        Thermometer existing = thermometers.putIfAbsent(value.position(), value);
        if (existing != null) preserve("thermometers", value.toMap(), "duplicate thermometer position");
    }

    private void loadRpmMonitor(RpmMonitor value) {
        RpmMonitor existing = rpmMonitors.putIfAbsent(value.position(), value);
        if (existing != null) preserve("rpmMonitors", value.toMap(), "duplicate RPM-monitor position");
    }

    private void loadSettingsPanel(SettingsPanel value) {
        SettingsPanel existing = settingsPanels.putIfAbsent(value.position(), value);
        if (existing != null)
            preserve("settingsPanels", value.toMap(), "duplicate settings-panel position");
    }

    private void loadDehumidifier(Dehumidifier value) {
        Dehumidifier existing = dehumidifiers.putIfAbsent(value.position(), value);
        if (existing != null) preserve("dehumidifiers", value.toMap(), "duplicate dehumidifier position");
    }

    private <T> void loadByPosition(Map<BlockKey, T> target, String section, BlockKey position, T value,
                                    Map<String, Object> record) {
        if (target.putIfAbsent(position, value) != null)
            preserve(section, record, "duplicate " + section + " position");
    }

    private void loadHumidifier(Humidifier value) {
        Humidifier existing = humidifiers.putIfAbsent(value.position(), value);
        if (existing != null) preserve("humidifiers", value.toMap(), "duplicate humidifier position");
    }

    private void resolveLoadedConflicts() {
        // Labels may repeat since 3.0; a system still has one thermostat.
        Set<SystemId> systems = new HashSet<>();
        List<Thermostat> orderedThermostats = new ArrayList<>(thermostats.values());
        orderedThermostats.sort(Comparator.comparing(Thermostat::position));
        for (Thermostat thermostat : orderedThermostats) {
            if (systems.add(thermostat.system())) continue;
            thermostats.remove(thermostat.position());
            preserve("thermostats", thermostat.toMap(), "a second thermostat for one system");
        }

        Set<BlockKey> occupied = new HashSet<>(thermostats.keySet());
        removePositionConflicts(equipment, occupied, "equipment", EquipmentUnit::toMap);
        removePositionConflicts(vents, occupied, "vents", ClimateVent::toMap);
        removePositionConflicts(thermometers, occupied, "thermometers", Thermometer::toMap);
        removePositionConflicts(rpmMonitors, occupied, "rpmMonitors", RpmMonitor::toMap);
        removePositionConflicts(settingsPanels, occupied, "settingsPanels", SettingsPanel::toMap);
        removeUnitConflicts(dehumidifiers, occupied, "dehumidifiers");
        removeUnitConflicts(humidifiers, occupied, "humidifiers");
        removeUnitConflicts(purifiers, occupied, "purifiers");
        removePositionConflicts(sensors, occupied, "sensors", Sensor::toMap);
        removePositionConflicts(sensorHubs, occupied, "sensorHubs", SensorHub::toMap);
        int dangling = 0;
        for (SensorHub hub : sensorHubs.values())
            for (BlockKey linked : List.copyOf(hub.sensors()))
                if (!sensors.containsKey(linked) && hub.unlink(linked)) dangling++;
        if (dangling > 0)
            host.logger().warning("Unlinked " + dangling + " sensor hub link(s) to sensors that no longer exist.");

        Set<SystemId> panelSystems = new HashSet<>();
        List<SettingsPanel> orderedPanels = new ArrayList<>(settingsPanels.values());
        orderedPanels.sort(Comparator.comparing(SettingsPanel::position));
        for (SettingsPanel panel : orderedPanels) {
            if (panel.system() == null || panelSystems.add(panel.system())) continue;
            settingsPanels.remove(panel.position());
            preserve("settingsPanels", panel.toMap(), "a second settings panel for one system");
        }
    }

    /** A unit owns three blocks; any one already taken sets the whole record aside. */
    private <T extends ClimateUnit> void removeUnitConflicts(Map<BlockKey, T> source, Set<BlockKey> occupied,
                                                             String section) {
        List<T> ordered = new ArrayList<>(source.values());
        ordered.sort(Comparator.comparing(ClimateUnit::position));
        for (T unit : ordered) {
            if (!occupied.contains(unit.position()) && !occupied.contains(unit.unit())
                    && !occupied.contains(unit.part())) {
                occupied.addAll(List.of(unit.position(), unit.unit(), unit.part()));
                continue;
            }
            source.remove(unit.position());
            preserve(section, unit.toMap(), "another HVAC device owns one of its blocks");
        }
    }

    private <T> void removePositionConflicts(Map<BlockKey, T> source, Set<BlockKey> occupied,
                                             String section, Function<T, Map<String, Object>> serializer) {
        List<BlockKey> keys = new ArrayList<>(source.keySet());
        keys.sort(Comparator.naturalOrder());
        for (BlockKey key : keys) {
            T value = source.get(key);
            if (occupied.add(key)) continue;
            source.remove(key);
            preserve(section, serializer.apply(value), "another HVAC device owns the same block");
        }
    }

    private void preserve(String section, Map<String, Object> record, String reason) {
        preservedRecords.computeIfAbsent(section, ignored -> new ArrayList<>())
                .add(new LinkedHashMap<>(record));
        host.logger().warning("Preserving " + section + " record: " + reason);
    }

    public Collection<Thermostat> thermostats() { return Collections.unmodifiableCollection(thermostats.values()); }
    public Collection<EquipmentUnit> equipment() { return Collections.unmodifiableCollection(equipment.values()); }
    public Collection<ClimateVent> vents() { return Collections.unmodifiableCollection(vents.values()); }
    public Collection<Thermometer> thermometers() { return Collections.unmodifiableCollection(thermometers.values()); }
    public Collection<RpmMonitor> rpmMonitors() { return Collections.unmodifiableCollection(rpmMonitors.values()); }
    public Collection<SettingsPanel> settingsPanels() { return Collections.unmodifiableCollection(settingsPanels.values()); }
    public Collection<Dehumidifier> dehumidifiers() { return Collections.unmodifiableCollection(dehumidifiers.values()); }
    public Collection<Humidifier> humidifiers() { return Collections.unmodifiableCollection(humidifiers.values()); }
    public Collection<AirPurifier> purifiers() { return Collections.unmodifiableCollection(purifiers.values()); }
    /** Every dehumidifier, humidifier and air purifier, ordered by sign position. */
    public List<ClimateUnit> climateUnits() {
        List<ClimateUnit> units = new ArrayList<>(dehumidifiers.size() + humidifiers.size() + purifiers.size());
        units.addAll(dehumidifiers.values());
        units.addAll(humidifiers.values());
        units.addAll(purifiers.values());
        units.sort(Comparator.comparing(ClimateUnit::position));
        return units;
    }
    public Collection<Sensor> sensors() { return Collections.unmodifiableCollection(sensors.values()); }
    public Collection<SensorHub> sensorHubs() { return Collections.unmodifiableCollection(sensorHubs.values()); }
    public Sensor sensorAt(BlockKey key) { return sensors.get(key); }
    public SensorHub sensorHubAt(BlockKey key) { return sensorHubs.get(key); }
    /** The hubs a sensor is linked to, by position. */
    public List<SensorHub> hubsShowing(BlockKey sensor) {
        List<SensorHub> hubs = new ArrayList<>();
        for (SensorHub hub : sensorHubs.values()) if (hub.sensors().contains(sensor)) hubs.add(hub);
        hubs.sort(Comparator.comparing(SensorHub::position));
        return hubs;
    }
    public boolean hasClimateUnits() {
        return !dehumidifiers.isEmpty() || !humidifiers.isEmpty() || !purifiers.isEmpty();
    }
    /** The unit whose sign, iron block or other block (water, filter) is at this block, or null. */
    public ClimateUnit climateUnitAt(BlockKey key) {
        ClimateUnit value = dehumidifiers.get(key);
        if (value == null) value = humidifiers.get(key);
        if (value == null) value = purifiers.get(key);
        return value != null ? value : unitParts.get(key);
    }
    /** The dehumidifier any of whose blocks is at this block, or null. */
    public Dehumidifier dehumidifierAt(BlockKey key) {
        return climateUnitAt(key) instanceof Dehumidifier dehumidifier ? dehumidifier : null;
    }
    /** Standalone units on the vent grid, by iron block, or null when a world has none. */
    public Long2ObjectOpenHashMap<List<ClimateUnit>> climateUnitCells(UUID worldId) { return unitCells.get(worldId); }
    public Collection<EquipmentUnit> pendingCleanup() { return Collections.unmodifiableList(pendingCleanup); }
    public Thermostat thermostatAt(BlockKey key) { return thermostats.get(key); }
    public EquipmentUnit equipmentAt(BlockKey key) { return equipment.get(key); }
    public ClimateVent ventAt(BlockKey key) { return vents.get(key); }
    public Thermometer thermometerAt(BlockKey key) { return thermometers.get(key); }
    public RpmMonitor rpmMonitorAt(BlockKey key) { return rpmMonitors.get(key); }
    public SettingsPanel settingsPanelAt(BlockKey key) { return settingsPanels.get(key); }

    /** Thermostats in a world with this label, ignoring case, by position. Labels may repeat. */
    public List<Thermostat> thermostatsLabelled(UUID worldId, String label) {
        List<Thermostat> matching = new ArrayList<>();
        if (label == null) return matching;
        for (Thermostat thermostat : thermostats.values())
            if (thermostat.position().worldId().equals(worldId) && thermostat.id().equalsIgnoreCase(label.trim()))
                matching.add(thermostat);
        matching.sort(Comparator.comparing(Thermostat::position));
        return matching;
    }

    /** The system's thermostat, or null while it is headless. */
    public Thermostat thermostatFor(SystemId system) {
        return first(thermostatsBySystem.get(system));
    }

    /** The system's equipment, sorted by position. */
    public List<EquipmentUnit> equipmentFor(SystemId system) {
        TreeMap<BlockKey, EquipmentUnit> units = equipmentBySystem.get(system);
        return units == null ? List.of() : List.copyOf(units.values());
    }

    public SettingsPanel settingsPanelFor(SystemId system) {
        return first(panelsBySystem.get(system));
    }

    /** The RPM monitors showing a system, sorted by position. */
    public List<RpmMonitor> rpmMonitorsFor(SystemId system) {
        TreeMap<BlockKey, RpmMonitor> monitors = monitorsBySystem.get(system);
        return monitors == null ? List.of() : List.copyOf(monitors.values());
    }

    /** The system's vents, sorted by position. */
    public Collection<ClimateVent> ventsFor(SystemId system) {
        TreeMap<BlockKey, ClimateVent> systemVents = ventsBySystem.get(system);
        return systemVents == null ? List.of() : Collections.unmodifiableCollection(systemVents.values());
    }

    /** Every block some active or pending unit currently owns as its output. */
    public Set<BlockKey> ownedOutputs() {
        return new HashSet<>(unitsByOwnedOutput.keySet());
    }

    /** Grid shift for {@link #ventCells}; a cell spans {@code 1 << shift} blocks. */
    int ventCellShift() { return ventCellShift; }

    /** The world's vent grid keyed by {@link #cellKey}, or null when it has no vents. Read-only. */
    Long2ObjectOpenHashMap<List<ClimateVent>> ventCells(UUID worldId) {
        return ventCells.get(worldId);
    }

    static long cellKey(int cellX, int cellZ) {
        return (long) cellX << 32 | (cellZ & 0xFFFFFFFFL);
    }

    private static <T> T first(TreeMap<BlockKey, T> entries) {
        return entries == null || entries.isEmpty() ? null : entries.firstEntry().getValue();
    }

    /**
     * True when this system already has a loaded vent of the given medium. A
     * system is all air or all water; this is what enforces that at link time.
     */
    public boolean hasVentOfMedium(SystemId system, boolean water) {
        for (ClimateVent vent : ventsFor(system)) {
            if (!vent.position().isChunkLoaded()) continue;
            var location = vent.position().location();
            if (location == null) continue;
            if (ClimateVent.isWaterVent(location.getBlock().getType()) == water) return true;
        }
        return false;
    }

    /** True when this system already has equipment belonging to the given loop. */
    public boolean hasEquipmentOfLoop(SystemId system, boolean waterLoop) {
        for (EquipmentUnit unit : equipmentFor(system))
            if (unit.type().waterLoop() == waterLoop) return true;
        return false;
    }

    /** What decides the system's medium; vents in unloaded chunks are not counted. */
    public SystemLinks.Shape shapeOf(SystemId system) {
        SystemLinks.Shape shape = SystemLinks.Shape.EMPTY;
        for (EquipmentUnit unit : equipmentFor(system)) shape = shape.plus(SystemLinks.Shape.equipment(unit.type()));
        if (hasVentOfMedium(system, false)) shape = shape.plus(SystemLinks.Shape.vent(false));
        if (hasVentOfMedium(system, true)) shape = shape.plus(SystemLinks.Shape.vent(true));
        return shape;
    }

    /** The system of the thermostat, equipment, vent, settings panel or RPM monitor at a block, or null. */
    public SystemId systemAt(BlockKey key) {
        Thermostat thermostat = thermostats.get(key);
        if (thermostat != null) return thermostat.system();
        EquipmentUnit unit = equipment.get(key);
        if (unit != null) return unit.system();
        ClimateVent vent = vents.get(key);
        if (vent != null) return vent.system();
        SettingsPanel panel = settingsPanels.get(key);
        if (panel != null) return panel.system();
        RpmMonitor monitor = rpmMonitors.get(key);
        return monitor == null ? null : monitor.system();
    }

    public Members members(SystemId system) {
        TreeMap<BlockKey, ClimateVent> systemVents = ventsBySystem.get(system);
        TreeMap<BlockKey, EquipmentUnit> units = equipmentBySystem.get(system);
        TreeMap<BlockKey, SettingsPanel> panels = panelsBySystem.get(system);
        TreeMap<BlockKey, RpmMonitor> monitors = monitorsBySystem.get(system);
        return new Members(thermostatFor(system), units == null ? 0 : units.size(),
                systemVents == null ? 0 : systemVents.size(), panels == null ? 0 : panels.size(),
                monitors == null ? 0 : monitors.size());
    }

    /** Every system with at least one device, sorted. */
    public SortedSet<SystemId> allSystems() {
        SortedSet<SystemId> systems = new TreeSet<>();
        systems.addAll(thermostatsBySystem.keySet());
        systems.addAll(equipmentBySystem.keySet());
        systems.addAll(ventsBySystem.keySet());
        systems.addAll(panelsBySystem.keySet());
        systems.addAll(monitorsBySystem.keySet());
        return systems;
    }

    /** A new, empty system in a world. Its number is never given out again. */
    public SystemId newSystem(UUID worldId) {
        SystemId system = SystemId.numbered(worldId, nextSystem++);
        dirty = true;
        return system;
    }

    public OperatingProfile profileFor(SystemId system) {
        SettingsPanel panel = settingsPanelFor(system);
        return panel == null ? OperatingProfile.NORMAL : panel.profile();
    }

    public DeviceKind deviceKind(BlockKey key) {
        if (thermostats.containsKey(key)) return DeviceKind.THERMOSTAT;
        if (equipment.containsKey(key)) return DeviceKind.EQUIPMENT;
        if (vents.containsKey(key)) return DeviceKind.VENT;
        if (thermometers.containsKey(key)) return DeviceKind.THERMOMETER;
        if (rpmMonitors.containsKey(key)) return DeviceKind.RPM_MONITOR;
        if (settingsPanels.containsKey(key)) return DeviceKind.SETTINGS_PANEL;
        ClimateUnit unit = climateUnitAt(key);
        if (unit != null) return switch (unit) {
            case Dehumidifier ignored -> DeviceKind.DEHUMIDIFIER;
            case Humidifier ignored -> DeviceKind.HUMIDIFIER;
            case AirPurifier ignored -> DeviceKind.AIR_PURIFIER;
        };
        if (sensors.containsKey(key)) return DeviceKind.SENSOR;
        if (sensorHubs.containsKey(key)) return DeviceKind.SENSOR_HUB;
        return DeviceKind.NONE;
    }

    /**
     * Told about every device added or removed while the server runs (the
     * public API's device events): never about the devices hvac.yml brings
     * back at startup. Main thread.
     */
    public interface DeviceObserver {
        /** How a device went. */
        enum Cause { BROKEN, COMMAND, MISSING }

        void added(Object device);

        void removed(Object device, Cause cause);
    }

    private DeviceObserver observer;
    /** What removals are reported as: a break unless a command says otherwise. */
    private DeviceObserver.Cause removalCause = DeviceObserver.Cause.BROKEN;

    public void setObserver(DeviceObserver observer) { this.observer = observer; }

    /** Runs removals a command makes, so they are reported as such. */
    public void removingByCommand(Runnable removals) {
        removalCause = DeviceObserver.Cause.COMMAND;
        try {
            removals.run();
        } finally {
            removalCause = DeviceObserver.Cause.BROKEN;
        }
    }

    private void added(Object device) {
        if (observer != null) observer.added(device);
    }

    private void removed(Object device) {
        removed(device, removalCause);
    }

    private void removed(Object device, DeviceObserver.Cause cause) {
        if (observer != null && device != null) observer.removed(device, cause);
    }

    public void addThermostat(Thermostat thermostat) {
        requireFree(thermostat.position());
        if (thermostatFor(thermostat.system()) != null)
            throw new IllegalArgumentException("that system already has a thermostat");
        thermostats.put(thermostat.position(), thermostat);
        indexThermostat(thermostat);
        dirty = true;
        added(thermostat);
    }

    public void addEquipment(EquipmentUnit unit) {
        requireFree(unit.position());
        equipment.put(unit.position(), unit);
        indexEquipment(unit);
        dirty = true;
        added(unit);
    }

    /** Registers a vent, replacing (relinking) any vent already at that block. */
    public void addVent(ClimateVent vent) {
        DeviceKind kind = deviceKind(vent.position());
        if (kind != DeviceKind.NONE && kind != DeviceKind.VENT)
            throw new IllegalArgumentException("another HVAC device is registered at that block");
        ClimateVent previous = vents.put(vent.position(), vent);
        if (previous != null) unindexVent(previous);
        indexVent(vent);
        dirty = true;
        // Relinking moves a vent to another system: it leaves the old one.
        if (previous != null) removed(previous, DeviceObserver.Cause.COMMAND);
        added(vent);
    }

    public void addThermometer(Thermometer thermometer) {
        requireFree(thermometer.position());
        thermometers.put(thermometer.position(), thermometer);
        dirty = true;
        added(thermometer);
    }

    public void addRpmMonitor(RpmMonitor monitor) {
        requireFree(monitor.position());
        rpmMonitors.put(monitor.position(), monitor);
        indexMonitor(monitor);
        dirty = true;
        added(monitor);
    }

    public void addSettingsPanel(SettingsPanel panel) {
        requireFree(panel.position());
        if (panel.system() != null && settingsPanelFor(panel.system()) != null)
            throw new IllegalArgumentException("that system already has a settings panel");
        settingsPanels.put(panel.position(), panel);
        indexPanel(panel);
        dirty = true;
        added(panel);
    }

    public void addSensor(Sensor sensor) {
        requireFree(sensor.position());
        sensors.put(sensor.position(), sensor);
        dirty = true;
        added(sensor);
    }

    public void addSensorHub(SensorHub hub) {
        requireFree(hub.position());
        sensorHubs.put(hub.position(), hub);
        dirty = true;
        added(hub);
    }

    /** Removes a sensor and takes it off every hub that showed it. */
    public Sensor removeSensor(BlockKey key) {
        Sensor value = sensors.remove(key);
        if (value == null) return null;
        for (SensorHub hub : sensorHubs.values()) hub.unlink(key);
        dirty = true;
        removed(value);
        return value;
    }

    public SensorHub removeSensorHub(BlockKey key) {
        SensorHub value = sensorHubs.remove(key);
        dirty |= value != null;
        removed(value);
        return value;
    }

    public void addClimateUnit(ClimateUnit unit) {
        requireFree(unit.position());
        requireFree(unit.unit());
        requireFree(unit.part());
        switch (unit) {
            case Dehumidifier dehumidifier -> dehumidifiers.put(unit.position(), dehumidifier);
            case Humidifier humidifier -> humidifiers.put(unit.position(), humidifier);
            case AirPurifier purifier -> purifiers.put(unit.position(), purifier);
        }
        indexUnit(unit);
        dirty = true;
        added(unit);
    }

    /** Removes the unit any of whose three blocks is at this key. */
    public ClimateUnit removeClimateUnit(BlockKey key) {
        ClimateUnit value = climateUnitAt(key);
        if (value == null) return null;
        dehumidifiers.remove(value.position(), value);
        humidifiers.remove(value.position(), value);
        purifiers.remove(value.position(), value);
        unindexUnit(value);
        value.setPartWater(false);   // a removed unit never leaves water behind
        dirty = true;
        removed(value);
        return value;
    }

    public boolean removeThermostat(BlockKey key) {
        Thermostat value = thermostats.remove(key);
        if (value == null) return false;
        unindexThermostat(value);
        dirty = true;
        removed(value);
        return true;
    }

    public EquipmentUnit removeEquipment(BlockKey key) {
        EquipmentUnit unit = equipment.remove(key);
        if (unit == null) return null;
        unindex(equipmentBySystem, unit.system(), unit.position(), unit);
        unit.stop();
        if (unit.hasOwnedOutput()) pendingCleanup.add(unit);
        else untrackOutput(unit);
        dirty = true;
        removed(unit);
        return unit;
    }

    public ClimateVent removeVent(BlockKey key) {
        ClimateVent value = vents.remove(key);
        if (value != null) { unindexVent(value); dirty = true; removed(value); }
        return value;
    }

    public boolean removeThermometer(BlockKey key) {
        Thermometer value = thermometers.remove(key);
        if (value == null) return false;
        dirty = true;
        removed(value);
        return true;
    }

    public boolean removeRpmMonitor(BlockKey key) {
        RpmMonitor value = rpmMonitors.remove(key);
        if (value == null) return false;
        unindexMonitor(value);
        dirty = true;
        removed(value);
        return true;
    }

    public boolean removeSettingsPanel(BlockKey key) {
        SettingsPanel value = settingsPanels.remove(key);
        if (value == null) return false;
        unindexPanel(value);
        dirty = true;
        removed(value);
        return true;
    }

    // ---- linking ----
    // Every change of a device's system goes through these, so the indexes follow.

    /** Moves a registered thermostat to a system that has none. Use this, never Thermostat.setSystem. */
    public void linkThermostat(Thermostat thermostat, SystemId system) {
        if (thermostats.get(thermostat.position()) != thermostat)
            throw new IllegalArgumentException("that thermostat is not registered");
        Thermostat existing = thermostatFor(system);
        if (existing != null && existing != thermostat)
            throw new IllegalArgumentException("that system already has a thermostat");
        unindexThermostat(thermostat);
        try {
            thermostat.setSystem(system);
        } finally {
            indexThermostat(thermostat);
        }
        dirty = true;
    }

    /**
     * Moves registered equipment to a system. Use this, never
     * EquipmentUnit.setSystem. A running unit is stopped first, and a unit
     * whose water or lava is still waiting to be cleaned up cannot move.
     */
    public void linkEquipment(EquipmentUnit unit, SystemId system) {
        if (equipment.get(unit.position()) != unit)
            throw new IllegalArgumentException("that equipment is not registered");
        if (unit.system().equals(system)) return;
        unit.stop();
        if (unit.hasOwnedOutput())
            throw new IllegalArgumentException("its output is in an unloaded chunk and has to be cleaned"
                    + " up first; go near it and try again");
        unindex(equipmentBySystem, unit.system(), unit.position(), unit);
        try {
            unit.setSystem(system);
        } finally {
            index(equipmentBySystem, unit.system(), unit.position(), unit);
        }
        dirty = true;
    }

    /** Links a panel to a system, or unlinks it with null. A system has one panel at most. */
    public void linkSettingsPanel(SettingsPanel panel, SystemId system) {
        if (settingsPanels.get(panel.position()) != panel)
            throw new IllegalArgumentException("that settings panel is not registered");
        SettingsPanel existing = system == null ? null : settingsPanelFor(system);
        if (existing != null && existing != panel)
            throw new IllegalArgumentException("that system already has a settings panel");
        unindexPanel(panel);
        try {
            panel.setSystem(system);
        } finally {
            indexPanel(panel);
        }
        dirty = true;
    }

    /** Links a monitor to a system, or unlinks it with null. */
    public void linkRpmMonitor(RpmMonitor monitor, SystemId system) {
        if (rpmMonitors.get(monitor.position()) != monitor)
            throw new IllegalArgumentException("that RPM monitor is not registered");
        unindexMonitor(monitor);
        try {
            monitor.setSystem(system);
        } finally {
            indexMonitor(monitor);
        }
        dirty = true;
    }

    /** Moves a vent to another system; it stays the same vent, so no device event fires. */
    private void moveVent(ClimateVent vent, SystemId system) {
        ClimateVent moved = new ClimateVent(vent.position(), system);
        unindexVent(vent);
        vents.put(moved.position(), moved);
        indexVent(moved);
        dirty = true;
    }

    /**
     * Moves everything in a headless system into another: its equipment,
     * vents, RPM monitors, and its settings panel unless the other system has
     * one already, in which case that panel is left unlinked. Nothing moves
     * unless all of it can.
     */
    public MergeResult merge(SystemId from, SystemId into) {
        if (from.equals(into)) throw new IllegalArgumentException("a system cannot merge into itself");
        if (!from.worldId().equals(into.worldId()))
            throw new IllegalArgumentException("systems in different worlds cannot merge");
        if (thermostatFor(from) != null) throw new IllegalArgumentException("only a headless system can be merged");
        List<EquipmentUnit> units = equipmentFor(from);
        for (EquipmentUnit unit : units) unit.stop();
        for (EquipmentUnit unit : units)
            if (unit.hasOwnedOutput())
                throw new IllegalArgumentException("a unit's output is in an unloaded chunk and has to be"
                        + " cleaned up first; go near it and try again");
        for (EquipmentUnit unit : units) linkEquipment(unit, into);
        List<ClimateVent> moving = List.copyOf(ventsFor(from));
        for (ClimateVent vent : moving) moveVent(vent, into);
        List<RpmMonitor> monitors = rpmMonitorsFor(from);
        for (RpmMonitor monitor : monitors) linkRpmMonitor(monitor, into);
        SettingsPanel panel = settingsPanelFor(from);
        SettingsPanel unlinked = null;
        boolean movedPanel = false;
        if (panel != null) {
            if (settingsPanelFor(into) == null) {
                linkSettingsPanel(panel, into);
                movedPanel = true;
            } else {
                linkSettingsPanel(panel, null);
                unlinked = panel;
            }
        }
        dirty = true;
        return new MergeResult(units.size(), moving.size(), monitors.size(), movedPanel, unlinked);
    }

    /**
     * Makes the system's thermostat a pool or a room thermostat to match
     * what is linked to it. Nothing decides for an empty system.
     *
     * @return the thermostat when it changed, otherwise null
     */
    public Thermostat settleHead(SystemId system) {
        Thermostat head = thermostatFor(system);
        if (head == null) return null;
        Boolean pool = SystemLinks.pool(shapeOf(system));
        if (pool == null || !head.setPool(pool, settings)) return null;
        dirty = true;
        return head;
    }

    /**
     * Unregisters every device of a system, as /hvac remove would one by one:
     * equipment leaves its water or lava for cleanup, and each device is
     * reported removed. The blocks stay in the world.
     */
    public Members removeSystem(SystemId system) {
        Members members = members(system);
        Thermostat head = thermostatFor(system);
        if (head != null) removeThermostat(head.position());
        for (EquipmentUnit unit : equipmentFor(system)) removeEquipment(unit.position());
        for (ClimateVent vent : List.copyOf(ventsFor(system))) removeVent(vent.position());
        SettingsPanel panel = settingsPanelFor(system);
        if (panel != null) removeSettingsPanel(panel.position());
        for (RpmMonitor monitor : rpmMonitorsFor(system)) removeRpmMonitor(monitor.position());
        return members;
    }

    /**
     * Renames the thermometer or sensor at a block. Both are records, so the
     * registry swaps in the renamed one; hubs link sensors by position.
     *
     * @return false when neither is there
     */
    public boolean relabel(BlockKey key, String label) {
        Thermometer thermometer = thermometers.get(key);
        if (thermometer != null) {
            thermometers.put(key, new Thermometer(key, label, thermometer.reads()));
            dirty = true;
            return true;
        }
        Sensor sensor = sensors.get(key);
        if (sensor == null) return false;
        sensors.put(key, new Sensor(key, label, sensor.reads()));
        dirty = true;
        return true;
    }

    public void markDirty() { dirty = true; }
    /** False after a failed load: this run will never write hvac.yml. */
    public boolean isWritable() { return writable; }
    public boolean isDirty() { return dirty; }

    public boolean releaseOutputOwnership(BlockKey changed) {
        List<EquipmentUnit> owners = unitsByOwnedOutput.get(changed);
        if (owners == null) return false;
        boolean released = false;
        // Releasing updates the index through each unit's watcher, so iterate a copy.
        for (EquipmentUnit unit : List.copyOf(owners)) released |= unit.releaseOwnershipAt(changed);
        dirty |= released;
        return released;
    }

    public int reconcilePendingCleanup() {
        int resolved = 0;
        Iterator<EquipmentUnit> iterator = pendingCleanup.iterator();
        while (iterator.hasNext()) {
            EquipmentUnit unit = iterator.next();
            unit.stop();
            if (!unit.hasOwnedOutput()) {
                iterator.remove();
                untrackOutput(unit);
                resolved++;
            }
        }
        dirty |= resolved > 0;
        return resolved;
    }

    public PruneResult pruneMissingLoadedDevices() {
        Set<SystemId> invalidated = new HashSet<>();
        int removed = 0;
        Iterator<Thermostat> thermostatIterator = thermostats.values().iterator();
        while (thermostatIterator.hasNext()) {
            Thermostat value = thermostatIterator.next();
            if (!value.position().isChunkLoaded()) continue;
            var location = value.position().location();
            if (location != null && Tag.ALL_SIGNS.isTagged(location.getBlock().getType())) continue;
            thermostatIterator.remove(); removed++;
            removed(value, DeviceObserver.Cause.MISSING);
        }
        Iterator<EquipmentUnit> equipmentIterator = equipment.values().iterator();
        while (equipmentIterator.hasNext()) {
            EquipmentUnit value = equipmentIterator.next();
            if (!value.position().isChunkLoaded() || value.blockExists()) continue;
            value.stop();
            if (value.hasOwnedOutput()) pendingCleanup.add(value);
            else untrackOutput(value);
            equipmentIterator.remove(); removed++;
            removed(value, DeviceObserver.Cause.MISSING);
        }
        Iterator<ClimateVent> ventIterator = vents.values().iterator();
        while (ventIterator.hasNext()) {
            ClimateVent value = ventIterator.next();
            if (!value.position().isChunkLoaded()) continue;
            var location = value.position().location();
            if (location != null && ClimateVent.isVentBlock(location.getBlock().getType())) continue;
            invalidated.add(value.system()); ventIterator.remove(); removed++;
            removed(value, DeviceObserver.Cause.MISSING);
        }
        removed += pruneSigns(thermometers.values().iterator(), Thermometer::position);
        removed += pruneSigns(rpmMonitors.values().iterator(), RpmMonitor::position);
        removed += pruneSigns(settingsPanels.values().iterator(), SettingsPanel::position);
        List<ClimateUnit> removedUnits = new ArrayList<>();
        pruneUnits(dehumidifiers.values().iterator(), removedUnits);
        pruneUnits(humidifiers.values().iterator(), removedUnits);
        pruneUnits(purifiers.values().iterator(), removedUnits);
        removed += removedUnits.size();
        removedUnits.forEach(unit -> removed(unit, DeviceObserver.Cause.MISSING));
        removed += pruneSigns(sensorHubs.values().iterator(), SensorHub::position);
        // A button pops off when its wall is broken, without any break event of its own.
        Iterator<Sensor> sensorIterator = sensors.values().iterator();
        while (sensorIterator.hasNext()) {
            Sensor value = sensorIterator.next();
            if (blockIs(value.position(), type -> type == Sensor.button(value.reads()))) continue;
            sensorIterator.remove(); removed++;
            for (SensorHub hub : sensorHubs.values()) hub.unlink(value.position());
            removed(value, DeviceObserver.Cause.MISSING);
        }
        if (removed > 0) rebuildIndexes();
        dirty |= removed > 0;
        return new PruneResult(Set.copyOf(invalidated), removed, List.copyOf(removedUnits));
    }

    private <T> int pruneSigns(Iterator<T> iterator, Function<T, BlockKey> positionOf) {
        int removed = 0;
        while (iterator.hasNext()) {
            T value = iterator.next();
            BlockKey position = positionOf.apply(value);
            if (!position.isChunkLoaded()) continue;
            var location = position.location();
            if (location != null && Tag.ALL_SIGNS.isTagged(location.getBlock().getType())) continue;
            iterator.remove(); removed++;
            removed(value, DeviceObserver.Cause.MISSING);
        }
        return removed;
    }

    private static void pruneUnits(Iterator<? extends ClimateUnit> iterator, List<ClimateUnit> removed) {
        while (iterator.hasNext()) {
            ClimateUnit value = iterator.next();
            if (unitIntact(value)) continue;
            value.setPartWater(false);
            iterator.remove();
            removed.add(value);
        }
    }

    /**
     * Each of its blocks is either unloaded or still what it should be. An air
     * purifier's filter may be taken out: it waits for a new one.
     */
    private static boolean unitIntact(ClimateUnit value) {
        return blockIs(value.position(), type -> Tag.ALL_SIGNS.isTagged(type))
                && blockIs(value.unit(), type -> type == Material.IRON_BLOCK)
                && (!value.partRequired() || blockIs(value.part(), type -> type == value.partMaterial()));
    }

    private static boolean blockIs(BlockKey position, java.util.function.Predicate<Material> expected) {
        if (!position.isChunkLoaded()) return true;
        var location = position.location();
        return location != null && expected.test(location.getBlock().getType());
    }

    public void shutdownEquipment() {
        for (EquipmentUnit unit : equipment.values()) { unit.stop(); unit.resetMotor(); }
        for (EquipmentUnit unit : pendingCleanup) { unit.stop(); unit.resetMotor(); }
        dirty = true;
    }

    private void requireFree(BlockKey position) {
        if (deviceKind(position) != DeviceKind.NONE)
            throw new IllegalArgumentException("another HVAC device is already registered at that block");
    }

    private void clear() {
        writable = true;
        upgradedFromFormat = 0;
        thermostats.clear(); equipment.clear(); vents.clear(); thermometers.clear();
        rpmMonitors.clear(); settingsPanels.clear(); pendingCleanup.clear(); preservedRecords.clear();
        nextSystem = 1;
        dehumidifiers.clear(); humidifiers.clear(); purifiers.clear(); sensors.clear(); sensorHubs.clear();
        rebuildIndexes();
        dirty = false;
    }

    // ---- index maintenance ----

    private static <K, T> void index(Map<K, TreeMap<BlockKey, T>> index, K key, BlockKey position, T value) {
        index.computeIfAbsent(key, ignored -> new TreeMap<>()).put(position, value);
    }

    private static <K, T> void unindex(Map<K, TreeMap<BlockKey, T>> index, K key, BlockKey position, T value) {
        TreeMap<BlockKey, T> entries = index.get(key);
        if (entries == null) return;
        entries.remove(position, value);
        if (entries.isEmpty()) index.remove(key);
    }

    private void indexThermostat(Thermostat thermostat) {
        index(thermostatsBySystem, thermostat.system(), thermostat.position(), thermostat);
    }

    private void unindexThermostat(Thermostat thermostat) {
        unindex(thermostatsBySystem, thermostat.system(), thermostat.position(), thermostat);
    }

    private void indexEquipment(EquipmentUnit unit) {
        index(equipmentBySystem, unit.system(), unit.position(), unit);
        trackOutput(unit);
    }

    /** Unlinked panels and monitors have no system and are in no index. */
    private void indexPanel(SettingsPanel panel) {
        if (panel.system() != null) index(panelsBySystem, panel.system(), panel.position(), panel);
    }

    private void unindexPanel(SettingsPanel panel) {
        if (panel.system() != null) unindex(panelsBySystem, panel.system(), panel.position(), panel);
    }

    private void indexMonitor(RpmMonitor monitor) {
        if (monitor.system() != null) index(monitorsBySystem, monitor.system(), monitor.position(), monitor);
    }

    private void unindexMonitor(RpmMonitor monitor) {
        if (monitor.system() != null) unindex(monitorsBySystem, monitor.system(), monitor.position(), monitor);
    }

    private void indexVent(ClimateVent vent) {
        index(ventsBySystem, vent.system(), vent.position(), vent);
        BlockKey position = vent.position();
        ventCells.computeIfAbsent(position.worldId(), ignored -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(cellKey(position.x() >> ventCellShift, position.z() >> ventCellShift),
                        ignored -> new ArrayList<>())
                .add(vent);
    }

    private void unindexVent(ClimateVent vent) {
        unindex(ventsBySystem, vent.system(), vent.position(), vent);
        BlockKey position = vent.position();
        Long2ObjectOpenHashMap<List<ClimateVent>> cells = ventCells.get(position.worldId());
        if (cells == null) return;
        long cell = cellKey(position.x() >> ventCellShift, position.z() >> ventCellShift);
        List<ClimateVent> bucket = cells.get(cell);
        if (bucket == null) return;
        bucket.remove(vent);
        if (bucket.isEmpty()) cells.remove(cell);
        if (cells.isEmpty()) ventCells.remove(position.worldId());
    }

    private void indexUnit(ClimateUnit value) {
        unitParts.put(value.unit(), value);
        unitParts.put(value.part(), value);
        BlockKey unit = value.unit();
        unitCells.computeIfAbsent(unit.worldId(), ignored -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(cellKey(unit.x() >> ventCellShift, unit.z() >> ventCellShift),
                        ignored -> new ArrayList<>())
                .add(value);
    }

    private void unindexUnit(ClimateUnit value) {
        unitParts.remove(value.unit(), value);
        unitParts.remove(value.part(), value);
        BlockKey unit = value.unit();
        Long2ObjectOpenHashMap<List<ClimateUnit>> cells = unitCells.get(unit.worldId());
        if (cells == null) return;
        long cell = cellKey(unit.x() >> ventCellShift, unit.z() >> ventCellShift);
        List<ClimateUnit> bucket = cells.get(cell);
        if (bucket == null) return;
        bucket.remove(value);
        if (bucket.isEmpty()) cells.remove(cell);
        if (cells.isEmpty()) unitCells.remove(unit.worldId());
    }

    /** Starts following a unit's owned output; the unit reports every change itself. */
    private void trackOutput(EquipmentUnit unit) {
        unit.watchOwnedOutput((previous, current) -> {
            if (previous != null) removeOwner(previous, unit);
            if (current != null && isTracked(unit)) addOwner(current, unit);
        });
        if (unit.ownedOutput() != null) addOwner(unit.ownedOutput(), unit);
    }

    /** Stops following a unit that has left the registry entirely. */
    private void untrackOutput(EquipmentUnit unit) {
        unit.watchOwnedOutput(null);
        if (unit.ownedOutput() != null) removeOwner(unit.ownedOutput(), unit);
    }

    private boolean isTracked(EquipmentUnit unit) {
        if (equipment.get(unit.position()) == unit) return true;
        for (EquipmentUnit pending : pendingCleanup) if (pending == unit) return true;
        return false;
    }

    private void addOwner(BlockKey output, EquipmentUnit unit) {
        List<EquipmentUnit> owners = unitsByOwnedOutput.computeIfAbsent(output, ignored -> new ArrayList<>(1));
        for (EquipmentUnit owner : owners) if (owner == unit) return;
        owners.add(unit);
    }

    private void removeOwner(BlockKey output, EquipmentUnit unit) {
        List<EquipmentUnit> owners = unitsByOwnedOutput.get(output);
        if (owners == null) return;
        owners.removeIf(owner -> owner == unit);
        if (owners.isEmpty()) unitsByOwnedOutput.remove(output);
    }

    /** Rebuilds every index from the primary maps, after bulk changes such as load or prune. */
    private void rebuildIndexes() {
        thermostatsBySystem.clear();
        equipmentBySystem.clear();
        ventsBySystem.clear();
        panelsBySystem.clear();
        monitorsBySystem.clear();
        ventCells.clear();
        unitsByOwnedOutput.clear();
        unitParts.clear();
        unitCells.clear();
        thermostats.values().forEach(this::indexThermostat);
        equipment.values().forEach(this::indexEquipment);
        pendingCleanup.forEach(this::trackOutput);
        vents.values().forEach(this::indexVent);
        settingsPanels.values().forEach(this::indexPanel);
        rpmMonitors.values().forEach(this::indexMonitor);
        dehumidifiers.values().forEach(this::indexUnit);
        humidifiers.values().forEach(this::indexUnit);
        purifiers.values().forEach(this::indexUnit);
    }

    /**
     * Test support: rebuilds every index from scratch on the side and
     * describes the first difference from the live ones, or returns null when
     * they agree. Nothing live is modified.
     */
    String indexDiscrepancy() {
        Map<SystemId, TreeMap<BlockKey, Thermostat>> bySystem = new HashMap<>();
        for (Thermostat value : thermostats.values()) index(bySystem, value.system(), value.position(), value);
        if (!bySystem.equals(thermostatsBySystem)) return "thermostatsBySystem";
        Map<SystemId, TreeMap<BlockKey, EquipmentUnit>> units = new HashMap<>();
        for (EquipmentUnit value : equipment.values()) index(units, value.system(), value.position(), value);
        if (!units.equals(equipmentBySystem)) return "equipmentBySystem";
        Map<SystemId, TreeMap<BlockKey, ClimateVent>> systemVents = new HashMap<>();
        Map<UUID, Map<Long, Set<ClimateVent>>> cells = new HashMap<>();
        for (ClimateVent value : vents.values()) {
            index(systemVents, value.system(), value.position(), value);
            BlockKey position = value.position();
            cells.computeIfAbsent(position.worldId(), ignored -> new HashMap<>())
                    .computeIfAbsent(cellKey(position.x() >> ventCellShift, position.z() >> ventCellShift),
                            ignored -> new HashSet<>()).add(value);
        }
        if (!systemVents.equals(ventsBySystem)) return "ventsBySystem";
        Map<UUID, Map<Long, Set<ClimateVent>>> liveCells = new HashMap<>();
        ventCells.forEach((world, grid) -> grid.forEach((cell, bucket) -> {
            if (bucket.size() != new HashSet<>(bucket).size()) throw new IllegalStateException("duplicate vent in cell");
            liveCells.computeIfAbsent(world, ignored -> new HashMap<>()).put(cell, new HashSet<>(bucket));
        }));
        if (!cells.equals(liveCells)) return "ventCells";
        Map<SystemId, TreeMap<BlockKey, SettingsPanel>> panels = new HashMap<>();
        for (SettingsPanel value : settingsPanels.values())
            if (value.system() != null) index(panels, value.system(), value.position(), value);
        if (!panels.equals(panelsBySystem)) return "panelsBySystem";
        Map<SystemId, TreeMap<BlockKey, RpmMonitor>> monitors = new HashMap<>();
        for (RpmMonitor value : rpmMonitors.values())
            if (value.system() != null) index(monitors, value.system(), value.position(), value);
        if (!monitors.equals(monitorsBySystem)) return "monitorsBySystem";
        Map<BlockKey, Set<EquipmentUnit>> outputs = new HashMap<>();
        List<EquipmentUnit> tracked = new ArrayList<>(equipment.values());
        tracked.addAll(pendingCleanup);
        for (EquipmentUnit unit : tracked)
            if (unit.ownedOutput() != null)
                outputs.computeIfAbsent(unit.ownedOutput(), ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                        .add(unit);
        Map<BlockKey, Set<EquipmentUnit>> liveOutputs = new HashMap<>();
        unitsByOwnedOutput.forEach((output, owners) -> {
            Set<EquipmentUnit> set = Collections.newSetFromMap(new IdentityHashMap<>());
            set.addAll(owners);
            if (set.size() != owners.size()) throw new IllegalStateException("duplicate owner at " + output);
            liveOutputs.put(output, set);
        });
        if (!outputs.equals(liveOutputs)) return "unitsByOwnedOutput";
        Map<BlockKey, ClimateUnit> parts = new HashMap<>();
        for (ClimateUnit value : climateUnits()) { parts.put(value.unit(), value); parts.put(value.part(), value); }
        if (!parts.equals(unitParts)) return "unitParts";
        return null;
    }
}
