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
    public static final int FORMAT_VERSION = 5;

    public enum DeviceKind {
        THERMOSTAT, EQUIPMENT, VENT, THERMOMETER, RPM_MONITOR, SETTINGS_PANEL, NONE
    }

    public record PruneResult(Set<GroupId> invalidatedGroups, int removedDevices) {}

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
            "thermometers", "rpmMonitors", "settingsPanels");

    private final Host host;
    private final HvacSettings settings;
    private final Map<BlockKey, Thermostat> thermostats = new HashMap<>();
    private final Map<BlockKey, EquipmentUnit> equipment = new HashMap<>();
    private final Map<BlockKey, ClimateVent> vents = new HashMap<>();
    private final Map<BlockKey, Thermometer> thermometers = new HashMap<>();
    private final Map<BlockKey, RpmMonitor> rpmMonitors = new HashMap<>();
    private final Map<BlockKey, SettingsPanel> settingsPanels = new HashMap<>();
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

    // Secondary indexes over the maps above. The maps stay the source of
    // truth; every mutation in this class keeps the indexes in step, and
    // indexDiscrepancy() rebuilds them from scratch in tests to prove it.
    // Before these existed, group lookups streamed and sorted every device,
    // often once per unit per controller cycle, and block-change events
    // scanned every vent and every unit on the server.
    private final Map<GroupId, TreeMap<BlockKey, Thermostat>> thermostatsByGroup = new HashMap<>();
    private final Map<String, TreeMap<BlockKey, Thermostat>> thermostatsById = new HashMap<>();
    private final Map<GroupId, TreeMap<BlockKey, EquipmentUnit>> equipmentByGroup = new HashMap<>();
    private final Map<GroupId, TreeMap<BlockKey, ClimateVent>> ventsByGroup = new HashMap<>();
    private final Map<GroupId, TreeMap<BlockKey, SettingsPanel>> panelsByGroup = new HashMap<>();
    /** Vents per world on an x/z grid at least as wide as the airflow search radius. */
    private final Map<UUID, Long2ObjectOpenHashMap<List<ClimateVent>>> ventCells = new HashMap<>();
    /** Owned outputs of active and pending-cleanup units, kept current by each unit's watcher. */
    private final Map<BlockKey, List<EquipmentUnit>> unitsByOwnedOutput = new HashMap<>();
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
        resolveLoadedConflicts();
        rebuildIndexes();
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
        write(configuration, "thermostats", thermostats.values(), Thermostat::position, Thermostat::toMap);
        write(configuration, "equipment", equipment.values(), EquipmentUnit::position, EquipmentUnit::toMap);
        write(configuration, "cleanup", pendingCleanup, EquipmentUnit::position, EquipmentUnit::toMap);
        write(configuration, "vents", vents.values(), ClimateVent::position, ClimateVent::toMap);
        write(configuration, "thermometers", thermometers.values(), Thermometer::position, Thermometer::toMap);
        write(configuration, "rpmMonitors", rpmMonitors.values(), RpmMonitor::position, RpmMonitor::toMap);
        write(configuration, "settingsPanels", settingsPanels.values(),
                SettingsPanel::position, SettingsPanel::toMap);
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

    private void resolveLoadedConflicts() {
        Set<String> ids = new HashSet<>();
        Set<GroupId> groups = new HashSet<>();
        List<Thermostat> orderedThermostats = new ArrayList<>(thermostats.values());
        orderedThermostats.sort(Comparator.comparing(Thermostat::position));
        for (Thermostat thermostat : orderedThermostats) {
            boolean duplicateId = !ids.add(thermostat.id().toLowerCase(Locale.ROOT));
            boolean duplicateGroup = !groups.add(thermostat.group());
            if (!duplicateId && !duplicateGroup) continue;
            thermostats.remove(thermostat.position());
            preserve("thermostats", thermostat.toMap(), duplicateId
                    ? "duplicate thermostat id" : "duplicate group controller");
        }

        Set<BlockKey> occupied = new HashSet<>(thermostats.keySet());
        removePositionConflicts(equipment, occupied, "equipment", EquipmentUnit::toMap);
        removePositionConflicts(vents, occupied, "vents", ClimateVent::toMap);
        removePositionConflicts(thermometers, occupied, "thermometers", Thermometer::toMap);
        removePositionConflicts(rpmMonitors, occupied, "rpmMonitors", RpmMonitor::toMap);
        removePositionConflicts(settingsPanels, occupied, "settingsPanels", SettingsPanel::toMap);

        Set<GroupId> panelGroups = new HashSet<>();
        List<SettingsPanel> orderedPanels = new ArrayList<>(settingsPanels.values());
        orderedPanels.sort(Comparator.comparing(SettingsPanel::position));
        for (SettingsPanel panel : orderedPanels) {
            if (panelGroups.add(panel.group())) continue;
            settingsPanels.remove(panel.position());
            preserve("settingsPanels", panel.toMap(), "duplicate settings panel for group");
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
    public Collection<EquipmentUnit> pendingCleanup() { return Collections.unmodifiableList(pendingCleanup); }
    public Thermostat thermostatAt(BlockKey key) { return thermostats.get(key); }
    public EquipmentUnit equipmentAt(BlockKey key) { return equipment.get(key); }
    public ClimateVent ventAt(BlockKey key) { return vents.get(key); }
    public Thermometer thermometerAt(BlockKey key) { return thermometers.get(key); }
    public RpmMonitor rpmMonitorAt(BlockKey key) { return rpmMonitors.get(key); }
    public SettingsPanel settingsPanelAt(BlockKey key) { return settingsPanels.get(key); }

    /** Case-insensitive; the lowest position wins if two ever share an id. */
    public Thermostat thermostatById(String id) {
        if (id == null) return null;
        return first(thermostatsById.get(idKey(id)));
    }

    /** The group's controller: the lowest-position thermostat in the group. */
    public Thermostat thermostatFor(GroupId group) {
        return first(thermostatsByGroup.get(group));
    }

    /** The group's equipment, sorted by position. */
    public List<EquipmentUnit> equipmentFor(GroupId group) {
        TreeMap<BlockKey, EquipmentUnit> units = equipmentByGroup.get(group);
        return units == null ? List.of() : List.copyOf(units.values());
    }

    public SettingsPanel settingsPanelFor(GroupId group) {
        return first(panelsByGroup.get(group));
    }

    /** The group's vents, sorted by position. */
    public Collection<ClimateVent> ventsFor(GroupId group) {
        TreeMap<BlockKey, ClimateVent> groupVents = ventsByGroup.get(group);
        return groupVents == null ? List.of() : Collections.unmodifiableCollection(groupVents.values());
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
     * True when this group already has a loaded vent of the given medium. A
     * group is all air or all water; this is what enforces that at link time.
     */
    public boolean hasVentOfMedium(GroupId group, boolean water) {
        for (ClimateVent vent : ventsFor(group)) {
            if (!vent.position().isChunkLoaded()) continue;
            var location = vent.position().location();
            if (location == null) continue;
            if (ClimateVent.isWaterVent(location.getBlock().getType()) == water) return true;
        }
        return false;
    }

    /** True when this group already has equipment belonging to the given loop. */
    public boolean hasEquipmentOfLoop(GroupId group, boolean waterLoop) {
        for (EquipmentUnit unit : equipmentFor(group))
            if (unit.type().waterLoop() == waterLoop) return true;
        return false;
    }

    public OperatingProfile profileFor(GroupId group) {
        SettingsPanel panel = settingsPanelFor(group);
        return panel == null ? OperatingProfile.NORMAL : panel.profile();
    }

    public DeviceKind deviceKind(BlockKey key) {
        if (thermostats.containsKey(key)) return DeviceKind.THERMOSTAT;
        if (equipment.containsKey(key)) return DeviceKind.EQUIPMENT;
        if (vents.containsKey(key)) return DeviceKind.VENT;
        if (thermometers.containsKey(key)) return DeviceKind.THERMOMETER;
        if (rpmMonitors.containsKey(key)) return DeviceKind.RPM_MONITOR;
        if (settingsPanels.containsKey(key)) return DeviceKind.SETTINGS_PANEL;
        return DeviceKind.NONE;
    }

    public void addThermostat(Thermostat thermostat) {
        requireFree(thermostat.position());
        if (thermostatById(thermostat.id()) != null)
            throw new IllegalArgumentException("thermostat id already exists");
        if (thermostatFor(thermostat.group()) != null)
            throw new IllegalArgumentException("that world already has a thermostat for this group");
        thermostats.put(thermostat.position(), thermostat);
        indexThermostat(thermostat);
        dirty = true;
    }

    public void addEquipment(EquipmentUnit unit) {
        requireFree(unit.position());
        equipment.put(unit.position(), unit);
        indexEquipment(unit);
        dirty = true;
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
    }

    public void addThermometer(Thermometer thermometer) {
        requireFree(thermometer.position());
        thermometers.put(thermometer.position(), thermometer);
        dirty = true;
    }

    public void addRpmMonitor(RpmMonitor monitor) {
        requireFree(monitor.position());
        rpmMonitors.put(monitor.position(), monitor);
        dirty = true;
    }

    public void addSettingsPanel(SettingsPanel panel) {
        requireFree(panel.position());
        if (settingsPanelFor(panel.group()) != null)
            throw new IllegalArgumentException("that HVAC group already has a settings panel");
        settingsPanels.put(panel.position(), panel);
        indexPanel(panel);
        dirty = true;
    }

    public boolean removeThermostat(BlockKey key) {
        Thermostat value = thermostats.remove(key);
        if (value == null) return false;
        unindexThermostat(value);
        dirty = true;
        return true;
    }

    public EquipmentUnit removeEquipment(BlockKey key) {
        EquipmentUnit unit = equipment.remove(key);
        if (unit == null) return null;
        unindex(equipmentByGroup, unit.group(), unit.position(), unit);
        unit.stop();
        if (unit.hasOwnedOutput()) pendingCleanup.add(unit);
        else untrackOutput(unit);
        dirty = true;
        return unit;
    }

    public ClimateVent removeVent(BlockKey key) {
        ClimateVent value = vents.remove(key);
        if (value != null) { unindexVent(value); dirty = true; }
        return value;
    }
    public boolean removeThermometer(BlockKey key) { boolean value = thermometers.remove(key) != null; dirty |= value; return value; }
    public boolean removeRpmMonitor(BlockKey key) { boolean value = rpmMonitors.remove(key) != null; dirty |= value; return value; }
    public boolean removeSettingsPanel(BlockKey key) {
        SettingsPanel value = settingsPanels.remove(key);
        if (value == null) return false;
        unindex(panelsByGroup, value.group(), value.position(), value);
        dirty = true;
        return true;
    }

    /** Moves a registered thermostat to another group. Use this, never Thermostat.setGroup. */
    public void regroupThermostat(Thermostat thermostat, String label) {
        if (thermostats.get(thermostat.position()) != thermostat)
            throw new IllegalArgumentException("that thermostat is not registered");
        unindexThermostat(thermostat);
        try {
            thermostat.setGroup(label);
        } finally {
            indexThermostat(thermostat);
        }
        dirty = true;
    }

    /** Moves registered equipment to another group. Use this, never EquipmentUnit.setGroup. */
    public void regroupEquipment(EquipmentUnit unit, String label) {
        if (equipment.get(unit.position()) != unit)
            throw new IllegalArgumentException("that equipment is not registered");
        unindex(equipmentByGroup, unit.group(), unit.position(), unit);
        try {
            unit.setGroup(label);
        } finally {
            index(equipmentByGroup, unit.group(), unit.position(), unit);
        }
        dirty = true;
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
        Set<GroupId> invalidated = new HashSet<>();
        int removed = 0;
        Iterator<Thermostat> thermostatIterator = thermostats.values().iterator();
        while (thermostatIterator.hasNext()) {
            Thermostat value = thermostatIterator.next();
            if (!value.position().isChunkLoaded()) continue;
            var location = value.position().location();
            if (location != null && Tag.ALL_SIGNS.isTagged(location.getBlock().getType())) continue;
            thermostatIterator.remove(); removed++;
        }
        Iterator<EquipmentUnit> equipmentIterator = equipment.values().iterator();
        while (equipmentIterator.hasNext()) {
            EquipmentUnit value = equipmentIterator.next();
            if (!value.position().isChunkLoaded() || value.blockExists()) continue;
            value.stop();
            if (value.hasOwnedOutput()) pendingCleanup.add(value);
            else untrackOutput(value);
            equipmentIterator.remove(); removed++;
        }
        Iterator<ClimateVent> ventIterator = vents.values().iterator();
        while (ventIterator.hasNext()) {
            ClimateVent value = ventIterator.next();
            if (!value.position().isChunkLoaded()) continue;
            var location = value.position().location();
            if (location != null && ClimateVent.isVentBlock(location.getBlock().getType())) continue;
            invalidated.add(value.group()); ventIterator.remove(); removed++;
        }
        removed += pruneSigns(thermometers.values().iterator(), Thermometer::position);
        removed += pruneSigns(rpmMonitors.values().iterator(), RpmMonitor::position);
        removed += pruneSigns(settingsPanels.values().iterator(), SettingsPanel::position);
        if (removed > 0) rebuildIndexes();
        dirty |= removed > 0;
        return new PruneResult(Set.copyOf(invalidated), removed);
    }

    private static <T> int pruneSigns(Iterator<T> iterator, Function<T, BlockKey> positionOf) {
        int removed = 0;
        while (iterator.hasNext()) {
            BlockKey position = positionOf.apply(iterator.next());
            if (!position.isChunkLoaded()) continue;
            var location = position.location();
            if (location != null && Tag.ALL_SIGNS.isTagged(location.getBlock().getType())) continue;
            iterator.remove(); removed++;
        }
        return removed;
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
        rebuildIndexes();
        dirty = false;
    }

    // ---- index maintenance ----

    private static String idKey(String id) { return id.toLowerCase(Locale.ROOT); }

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
        index(thermostatsByGroup, thermostat.group(), thermostat.position(), thermostat);
        index(thermostatsById, idKey(thermostat.id()), thermostat.position(), thermostat);
    }

    private void unindexThermostat(Thermostat thermostat) {
        unindex(thermostatsByGroup, thermostat.group(), thermostat.position(), thermostat);
        unindex(thermostatsById, idKey(thermostat.id()), thermostat.position(), thermostat);
    }

    private void indexEquipment(EquipmentUnit unit) {
        index(equipmentByGroup, unit.group(), unit.position(), unit);
        trackOutput(unit);
    }

    private void indexPanel(SettingsPanel panel) {
        index(panelsByGroup, panel.group(), panel.position(), panel);
    }

    private void indexVent(ClimateVent vent) {
        index(ventsByGroup, vent.group(), vent.position(), vent);
        BlockKey position = vent.position();
        ventCells.computeIfAbsent(position.worldId(), ignored -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(cellKey(position.x() >> ventCellShift, position.z() >> ventCellShift),
                        ignored -> new ArrayList<>())
                .add(vent);
    }

    private void unindexVent(ClimateVent vent) {
        unindex(ventsByGroup, vent.group(), vent.position(), vent);
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
        thermostatsByGroup.clear();
        thermostatsById.clear();
        equipmentByGroup.clear();
        ventsByGroup.clear();
        panelsByGroup.clear();
        ventCells.clear();
        unitsByOwnedOutput.clear();
        thermostats.values().forEach(this::indexThermostat);
        equipment.values().forEach(this::indexEquipment);
        pendingCleanup.forEach(this::trackOutput);
        vents.values().forEach(this::indexVent);
        settingsPanels.values().forEach(this::indexPanel);
    }

    /**
     * Test support: rebuilds every index from scratch on the side and
     * describes the first difference from the live ones, or returns null when
     * they agree. Nothing live is modified.
     */
    String indexDiscrepancy() {
        Map<GroupId, TreeMap<BlockKey, Thermostat>> byGroup = new HashMap<>();
        Map<String, TreeMap<BlockKey, Thermostat>> byId = new HashMap<>();
        for (Thermostat value : thermostats.values()) {
            index(byGroup, value.group(), value.position(), value);
            index(byId, idKey(value.id()), value.position(), value);
        }
        if (!byGroup.equals(thermostatsByGroup)) return "thermostatsByGroup";
        if (!byId.equals(thermostatsById)) return "thermostatsById";
        Map<GroupId, TreeMap<BlockKey, EquipmentUnit>> units = new HashMap<>();
        for (EquipmentUnit value : equipment.values()) index(units, value.group(), value.position(), value);
        if (!units.equals(equipmentByGroup)) return "equipmentByGroup";
        Map<GroupId, TreeMap<BlockKey, ClimateVent>> groupVents = new HashMap<>();
        Map<UUID, Map<Long, Set<ClimateVent>>> cells = new HashMap<>();
        for (ClimateVent value : vents.values()) {
            index(groupVents, value.group(), value.position(), value);
            BlockKey position = value.position();
            cells.computeIfAbsent(position.worldId(), ignored -> new HashMap<>())
                    .computeIfAbsent(cellKey(position.x() >> ventCellShift, position.z() >> ventCellShift),
                            ignored -> new HashSet<>()).add(value);
        }
        if (!groupVents.equals(ventsByGroup)) return "ventsByGroup";
        Map<UUID, Map<Long, Set<ClimateVent>>> liveCells = new HashMap<>();
        ventCells.forEach((world, grid) -> grid.forEach((cell, bucket) -> {
            if (bucket.size() != new HashSet<>(bucket).size()) throw new IllegalStateException("duplicate vent in cell");
            liveCells.computeIfAbsent(world, ignored -> new HashMap<>()).put(cell, new HashSet<>(bucket));
        }));
        if (!cells.equals(liveCells)) return "ventCells";
        Map<GroupId, TreeMap<BlockKey, SettingsPanel>> panels = new HashMap<>();
        for (SettingsPanel value : settingsPanels.values()) index(panels, value.group(), value.position(), value);
        if (!panels.equals(panelsByGroup)) return "panelsByGroup";
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
        return null;
    }
}
