package com.github.blade.hvac.service;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
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
    /** Bumped on every change to the vent set, so spatial indexes know when to rebuild. */
    private int ventsVersion;
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
    }

    public void load() {
        clear();
        if (!dataFile.isFile()) return;

        YamlConfiguration configuration = loadWithRecovery();
        warnOnNewerFormat(configuration);
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
        ventsVersion++;
        dirty = false;
    }

    /**
     * A newer file is loaded rather than refused: known records still work, and
     * anything this build does not understand is preserved by the reader below.
     */
    private void warnOnNewerFormat(YamlConfiguration configuration) {
        int format = configuration.getInt("meta.format", FORMAT_VERSION);
        if (format <= FORMAT_VERSION) return;
        host.logger().warning("hvac.yml is format " + format + " but this build of EZvac"
                + " understands format " + FORMAT_VERSION + ". It will still load. Records this"
                + " build does not recognise are kept untouched and written back unchanged, but"
                + " the devices they describe will not run until EZvac is updated.");
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
                throw new IllegalStateException("hvac.yml is unreadable and no backup exists", primary);
            try {
                configuration = new YamlConfiguration();
                configuration.load(backupFile);
                Files.copy(backupFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                host.logger().severe("Recovered unreadable hvac.yml from hvac.yml.bak");
                return configuration;
            } catch (IOException | InvalidConfigurationException backupFailure) {
                primary.addSuppressed(backupFailure);
                throw new IllegalStateException("Both hvac.yml and its backup are unreadable", primary);
            }
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
    public int ventsVersion() { return ventsVersion; }
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

    public Thermostat thermostatById(String id) {
        if (id == null) return null;
        return thermostats.values().stream().filter(value -> value.id().equalsIgnoreCase(id))
                .min(Comparator.comparing(Thermostat::position)).orElse(null);
    }

    public Thermostat thermostatFor(GroupId group) {
        return thermostats.values().stream().filter(value -> value.group().equals(group))
                .min(Comparator.comparing(Thermostat::position)).orElse(null);
    }

    public List<EquipmentUnit> equipmentFor(GroupId group) {
        return equipment.values().stream().filter(unit -> unit.group().equals(group))
                .sorted(Comparator.comparing(EquipmentUnit::position)).toList();
    }

    public SettingsPanel settingsPanelFor(GroupId group) {
        return settingsPanels.values().stream().filter(panel -> panel.group().equals(group))
                .min(Comparator.comparing(SettingsPanel::position)).orElse(null);
    }

    /**
     * True when this group already has a loaded vent of the given medium. A
     * group is all air or all water; this is what enforces that at link time.
     */
    public boolean hasVentOfMedium(GroupId group, boolean water) {
        for (ClimateVent vent : vents.values()) {
            if (!vent.group().equals(group) || !vent.position().isChunkLoaded()) continue;
            var location = vent.position().location();
            if (location == null) continue;
            if (ClimateVent.isWaterVent(location.getBlock().getType()) == water) return true;
        }
        return false;
    }

    /** True when this group already has equipment belonging to the given loop. */
    public boolean hasEquipmentOfLoop(GroupId group, boolean waterLoop) {
        for (EquipmentUnit unit : equipment.values())
            if (unit.group().equals(group) && unit.type().waterLoop() == waterLoop) return true;
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
        dirty = true;
    }

    public void addEquipment(EquipmentUnit unit) {
        requireFree(unit.position());
        equipment.put(unit.position(), unit);
        dirty = true;
    }

    public void addVent(ClimateVent vent) {
        DeviceKind kind = deviceKind(vent.position());
        if (kind != DeviceKind.NONE && kind != DeviceKind.VENT)
            throw new IllegalArgumentException("another HVAC device is registered at that block");
        vents.put(vent.position(), vent);
        ventsVersion++;
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
        dirty = true;
    }

    public boolean removeThermostat(BlockKey key) { boolean value = thermostats.remove(key) != null; dirty |= value; return value; }
    public EquipmentUnit removeEquipment(BlockKey key) {
        EquipmentUnit unit = equipment.remove(key);
        if (unit == null) return null;
        unit.stop();
        if (unit.hasOwnedOutput()) pendingCleanup.add(unit);
        dirty = true;
        return unit;
    }
    public ClimateVent removeVent(BlockKey key) {
        ClimateVent value = vents.remove(key);
        if (value != null) { ventsVersion++; dirty = true; }
        return value;
    }
    public boolean removeThermometer(BlockKey key) { boolean value = thermometers.remove(key) != null; dirty |= value; return value; }
    public boolean removeRpmMonitor(BlockKey key) { boolean value = rpmMonitors.remove(key) != null; dirty |= value; return value; }
    public boolean removeSettingsPanel(BlockKey key) {
        boolean value = settingsPanels.remove(key) != null;
        dirty |= value;
        return value;
    }

    public void markDirty() { dirty = true; }
    public boolean isDirty() { return dirty; }

    public boolean releaseOutputOwnership(BlockKey changed) {
        boolean released = false;
        for (EquipmentUnit unit : equipment.values()) released |= unit.releaseOwnershipAt(changed);
        for (EquipmentUnit unit : pendingCleanup) released |= unit.releaseOwnershipAt(changed);
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
            equipmentIterator.remove(); removed++;
        }
        Iterator<ClimateVent> ventIterator = vents.values().iterator();
        while (ventIterator.hasNext()) {
            ClimateVent value = ventIterator.next();
            if (!value.position().isChunkLoaded()) continue;
            var location = value.position().location();
            if (location != null && ClimateVent.isVentBlock(location.getBlock().getType())) continue;
            invalidated.add(value.group()); ventIterator.remove(); removed++; ventsVersion++;
        }
        removed += pruneSigns(thermometers.values().iterator(), Thermometer::position);
        removed += pruneSigns(rpmMonitors.values().iterator(), RpmMonitor::position);
        removed += pruneSigns(settingsPanels.values().iterator(), SettingsPanel::position);
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
        thermostats.clear(); equipment.clear(); vents.clear(); thermometers.clear();
        rpmMonitors.clear(); settingsPanels.clear(); pendingCleanup.clear(); preservedRecords.clear();
        ventsVersion++;
        dirty = false;
    }
}
