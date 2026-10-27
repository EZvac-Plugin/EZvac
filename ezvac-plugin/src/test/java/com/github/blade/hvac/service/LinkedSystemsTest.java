package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Linked systems (3.0): hidden keys, linking and merging, headless systems,
 * the thermostat following what is linked to it, and the upgrade from
 * named groups.
 */
class LinkedSystemsTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final UUID OTHER_WORLD = UUID.fromString("6a1f2b3c-4d5e-4f60-8a7b-9c0d1e2f3a4b");
    private static final HvacSettings SETTINGS = HvacSettings.defaults();

    @TempDir Path folder;
    private TestHost host;
    private HvacRegistry registry;

    @BeforeEach
    void setUp() {
        host = new TestHost(folder.toFile());
        registry = new HvacRegistry(host, SETTINGS);
    }

    private static BlockKey at(int x, int y, int z) {
        return new BlockKey(WORLD, x, y, z);
    }

    private static String position(int x, int y, int z) {
        return "    worldUuid: " + WORLD + "\n    x: " + x + "\n    y: " + y + "\n    z: " + z + "\n";
    }

    private Thermostat thermostat(int x, String label, boolean pool) {
        Thermostat thermostat = new Thermostat(at(x, 64, 0), label, registry.newSystem(WORLD),
                pool ? Thermostat.POOL_DEFAULT_F : Thermostat.ROOM_DEFAULT_F, 70, SETTINGS, pool);
        registry.addThermostat(thermostat);
        return thermostat;
    }

    private EquipmentUnit unit(int x, EquipmentType type) {
        EquipmentUnit unit = new EquipmentUnit(at(x, 64, 5), registry.newSystem(WORLD), type);
        registry.addEquipment(unit);
        return unit;
    }

    private String saved() throws IOException {
        registry.markDirty();
        registry.save(true);
        return Files.readString(folder.resolve("hvac.yml"), StandardCharsets.UTF_8);
    }

    @Test
    void keysAreOldGroupNamesOrNumbersThatNoGroupNameCouldBe() {
        assertEquals("house", new SystemId(WORLD, " House ").key(), "an old group name, as it always was");
        assertEquals(0, new SystemId(WORLD, "house").number());
        SystemId numbered = SystemId.numbered(WORLD, 12);
        assertEquals("#12", numbered.key());
        assertEquals(12, numbered.number());
        assertEquals(numbered, new SystemId(WORLD, "#12"), "read back from hvac.yml");
        assertThrows(IllegalArgumentException.class, () -> SystemId.numbered(WORLD, 0));
        assertThrows(IllegalArgumentException.class, () -> new SystemId(WORLD, "#0"));
        assertThrows(IllegalArgumentException.class, () -> new SystemId(WORLD, "#x"));
        assertThrows(IllegalArgumentException.class, () -> new SystemId(WORLD, "two words"));
    }

    @Test
    void newSystemsAreNumberedAndNumbersAreNeverGivenOutTwice() throws IOException {
        assertEquals("#1", registry.newSystem(WORLD).key());
        assertEquals("#2", registry.newSystem(WORLD).key());
        Thermostat home = thermostat(0, "Home", false);
        assertEquals("#3", home.system().key());
        assertTrue(saved().contains("next-system: 4"));

        HvacRegistry reloaded = new HvacRegistry(host, SETTINGS);
        reloaded.load();
        assertEquals("#4", reloaded.newSystem(WORLD).key(), "the counter is saved");

        // A file whose counter fell behind its devices (a hand edit, a partial restore) skips past them.
        Files.writeString(folder.resolve("hvac.yml"), "meta:\n  format: 7\n  next-system: 2\nthermostats:\n  a:\n"
                + position(0, 64, 0) + "    id: Home\n    system: '#9'\n", StandardCharsets.UTF_8);
        HvacRegistry behind = new HvacRegistry(host, SETTINGS);
        behind.load();
        assertEquals("#10", behind.newSystem(WORLD).key());
    }

    @Test
    void labelsMayRepeatButASystemHasOneThermostat() {
        Thermostat first = thermostat(0, "Home", false);
        Thermostat second = thermostat(1, "Home", false);
        assertNotEquals(first.system(), second.system());
        assertEquals(List.of(first, second), registry.thermostatsLabelled(WORLD, "home"));
        assertTrue(registry.thermostatsLabelled(OTHER_WORLD, "Home").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> registry.linkThermostat(second, first.system()));
    }

    @Test
    void linkingAUnitBringsItsWholeHeadlessSystem() {
        Thermostat home = thermostat(0, "Home", false);
        EquipmentUnit pump = unit(1, EquipmentType.HEAT_PUMP);
        SystemId loose = pump.system();
        registry.addVent(new ClimateVent(at(1, 70, 0), loose));
        RpmMonitor monitor = new RpmMonitor(at(1, 64, 9), "Fans");
        registry.addRpmMonitor(monitor);
        registry.linkRpmMonitor(monitor, loose);

        HvacRegistry.MergeResult merged = registry.merge(loose, home.system());
        assertEquals(1, merged.units());
        assertEquals(1, merged.vents());
        assertEquals(1, merged.monitors());
        assertEquals(home.system(), pump.system());
        assertEquals(home.system(), registry.ventAt(at(1, 70, 0)).system());
        assertEquals(home.system(), monitor.system());
        assertTrue(registry.members(loose).empty(), "the headless system is gone: nothing carries its key");
        assertFalse(registry.allSystems().contains(loose));
        assertEquals(new HvacRegistry.Members(home, 1, 1, 0, 1), registry.members(home.system()));
        assertNull(registry.indexDiscrepancy());
    }

    @Test
    void aSystemWithAThermostatIsNeverTakenAndWorldsDoNotMix() {
        Thermostat home = thermostat(0, "Home", false);
        Thermostat office = thermostat(1, "Office", false);
        assertThrows(IllegalArgumentException.class, () -> registry.merge(office.system(), home.system()));
        assertThrows(IllegalArgumentException.class, () -> registry.merge(home.system(), home.system()));
        SystemId elsewhere = registry.newSystem(OTHER_WORLD);
        assertThrows(IllegalArgumentException.class, () -> registry.merge(elsewhere, home.system()));
    }

    @Test
    void aSecondSettingsPanelIsLeftUnlinkedWhenSystemsMerge() {
        Thermostat home = thermostat(0, "Home", false);
        SettingsPanel kept = new SettingsPanel(at(0, 64, 9), "Kept");
        registry.addSettingsPanel(kept);
        registry.linkSettingsPanel(kept, home.system());
        EquipmentUnit pump = unit(1, EquipmentType.HEAT_PUMP);
        SettingsPanel extra = new SettingsPanel(at(1, 64, 9), "Extra");
        registry.addSettingsPanel(extra);
        registry.linkSettingsPanel(extra, pump.system());
        assertThrows(IllegalArgumentException.class, () -> registry.linkSettingsPanel(extra, home.system()),
                "one panel per system");

        HvacRegistry.MergeResult merged = registry.merge(pump.system(), home.system());
        assertSame(extra, merged.unlinkedPanel());
        assertFalse(merged.movedPanel());
        assertNull(extra.system());
        assertSame(kept, registry.settingsPanelFor(home.system()));
        assertNull(registry.indexDiscrepancy());
    }

    @Test
    void aBrokenThermostatLeavesItsSystemHeadlessForAnotherToTakeOver() {
        Thermostat home = thermostat(0, "Home", false);
        EquipmentUnit pump = unit(1, EquipmentType.HEAT_PUMP);
        registry.merge(pump.system(), home.system());
        SystemId system = home.system();
        registry.removeThermostat(home.position());
        assertNull(registry.thermostatFor(system));
        assertEquals(system, pump.system(), "the unit stays in its system");
        assertEquals(new HvacRegistry.Members(null, 1, 0, 0, 0), registry.members(system));

        Thermostat replacement = thermostat(2, "Home", false);
        registry.merge(system, replacement.system());
        assertEquals(replacement.system(), pump.system());
    }

    @Test
    void unlinkingAUnitGivesItAHeadlessSystemOfItsOwn() {
        Thermostat home = thermostat(0, "Home", false);
        EquipmentUnit pump = unit(1, EquipmentType.HEAT_PUMP);
        registry.merge(pump.system(), home.system());
        SystemId own = registry.newSystem(WORLD);
        registry.linkEquipment(pump, own);
        assertEquals(own, pump.system());
        assertTrue(registry.equipmentFor(home.system()).isEmpty());
        assertNull(registry.indexDiscrepancy());
    }

    @Test
    void removingASystemRemovesEveryDeviceAndReportsEachOne() {
        List<String> removed = new java.util.ArrayList<>();
        registry.setObserver(new HvacRegistry.DeviceObserver() {
            @Override public void added(Object device) {}
            @Override public void removed(Object device, Cause cause) {
                removed.add(device.getClass().getSimpleName() + " " + cause);
            }
        });
        Thermostat home = thermostat(0, "Home", false);
        EquipmentUnit pump = unit(1, EquipmentType.HEAT_PUMP);
        registry.merge(pump.system(), home.system());
        registry.addVent(new ClimateVent(at(1, 70, 0), home.system()));
        SettingsPanel panel = new SettingsPanel(at(0, 64, 9), "Panel");
        registry.addSettingsPanel(panel);
        registry.linkSettingsPanel(panel, home.system());
        Thermostat office = thermostat(5, "Office", false);

        registry.removingByCommand(() -> registry.removeSystem(home.system()));
        assertTrue(registry.members(home.system()).empty());
        assertEquals(List.of("Thermostat COMMAND", "EquipmentUnit COMMAND", "ClimateVent COMMAND",
                "SettingsPanel COMMAND"), removed);
        assertSame(office, registry.thermostatAt(office.position()), "other systems are left alone");
    }

    @Test
    void aThermostatFollowsWhatIsLinkedToIt() {
        Thermostat spa = thermostat(0, "Spa", false);
        assertNull(registry.settleHead(spa.system()), "an empty system decides nothing");
        EquipmentUnit boiler = unit(1, EquipmentType.BOILER);
        registry.merge(boiler.system(), spa.system());
        assertSame(spa, registry.settleHead(spa.system()));
        assertTrue(spa.pool());
        assertEquals(Thermostat.POOL_DEFAULT_F, spa.targetF(), "the room default moves to the pool default");
        assertNull(registry.settleHead(spa.system()), "nothing changes twice");

        // A water system takes no air equipment, and an air system no water equipment.
        assertNotNull(SystemLinks.conflict(registry.shapeOf(spa.system()),
                SystemLinks.Shape.equipment(EquipmentType.HEAT_PUMP)));
        assertNull(SystemLinks.conflict(registry.shapeOf(spa.system()),
                SystemLinks.Shape.equipment(EquipmentType.POOL_HEAT_PUMP)));

        registry.linkEquipment(boiler, registry.newSystem(WORLD));
        assertNull(registry.settleHead(spa.system()), "with nothing left it stays a pool thermostat");
        EquipmentUnit pump = unit(2, EquipmentType.HEAT_PUMP);
        registry.merge(pump.system(), spa.system());
        assertSame(spa, registry.settleHead(spa.system()));
        assertFalse(spa.pool());
        assertEquals(Thermostat.ROOM_DEFAULT_F, spa.targetF());
    }

    @Test
    void aChosenTargetIsClampedWhenTheRangeChanges() {
        Thermostat thermostat = new Thermostat(at(0, 64, 0), "Home", "house", 64, 70, SETTINGS, false);
        assertTrue(thermostat.setPool(true, SETTINGS));
        assertEquals(SETTINGS.poolMinimumTargetF(), thermostat.targetF(), "64 F is below the pool range");
        assertFalse(thermostat.setPool(true, SETTINGS));
        thermostat.setTargetF(100, SETTINGS);
        assertTrue(thermostat.setPool(false, SETTINGS));
        assertEquals(SETTINGS.maximumTargetF(), thermostat.targetF());
    }

    @Test
    void theRulesForWhatMayShareASystem() {
        SystemLinks.Shape air = SystemLinks.Shape.equipment(EquipmentType.FURNACE);
        SystemLinks.Shape water = SystemLinks.Shape.vent(true);
        assertNull(SystemLinks.conflict(SystemLinks.Shape.EMPTY, water), "an empty system takes anything");
        assertNull(SystemLinks.conflict(air, SystemLinks.Shape.vent(false)));
        assertTrue(SystemLinks.conflict(air, water).startsWith("is an air system"));
        assertTrue(SystemLinks.conflict(water, air).startsWith("is a water system"));
        assertNull(SystemLinks.pool(SystemLinks.Shape.EMPTY));
        assertEquals(Boolean.TRUE, SystemLinks.pool(water));
        assertEquals(Boolean.FALSE, SystemLinks.pool(air));
        assertNull(SystemLinks.pool(air.plus(water)), "a mixed system (only from a hand edit) decides nothing");
    }

    @Test
    void oldGroupsBecomeSystemsWithTheSameMembers() throws IOException {
        // Like Soms' test123: vents left behind with no thermostat and no equipment.
        Files.writeString(folder.resolve("hvac.yml"), "meta:\n  format: 6\nthermostats:\n  a:\n"
                + position(0, 64, 0) + "    id: homestat\n    group: house\n"
                + "equipment:\n  a:\n" + position(1, 64, 0) + "    group: house\n    equipmentType: HEAT_PUMP\n"
                + "vents:\n  a:\n" + position(2, 64, 0) + "    group: house\n"
                + "  b:\n" + position(3, 64, 0) + "    group: test123\n"
                + "rpmMonitors:\n  a:\n" + position(4, 64, 0) + "    group: house\n    label: house\n"
                + "    equipmentType: HEAT_PUMP\n"
                + "settingsPanels:\n  a:\n" + position(5, 64, 0) + "    group: house\n    label: house\n"
                + "    profile: ECO\n", StandardCharsets.UTF_8);
        registry.load();
        SystemId house = new SystemId(WORLD, "house");
        assertEquals("homestat", registry.thermostatFor(house).id());
        assertEquals(new HvacRegistry.Members(registry.thermostatFor(house), 1, 1, 1, 1), registry.members(house));
        assertEquals(OperatingProfile.ECO, registry.profileFor(house));
        SystemId leftover = new SystemId(WORLD, "test123");
        assertNull(registry.thermostatFor(leftover), "a group without a thermostat comes back headless");
        assertEquals(1, registry.members(leftover).vents());
        assertTrue(host.warnings().isEmpty(), () -> "unexpected warnings: " + host.warnings());

        String file = saved();
        assertFalse(file.contains("group:"), file);
        assertTrue(file.contains("system: house"));
        assertTrue(file.contains("system: test123"));
        assertTrue(file.contains("tier: 3"), "an upgraded heat pump is variable, as it always ran");
        assertTrue(Files.isRegularFile(folder.resolve("hvac.yml.format6.bak")), "the 2.x file is kept");
    }

    @Test
    void unlinkedPanelsAndMonitorsSaveWithoutASystem() throws IOException {
        registry.addSettingsPanel(new SettingsPanel(at(0, 64, 0), "Loose"));
        registry.addRpmMonitor(new RpmMonitor(at(1, 64, 0), "Loose"));
        assertFalse(saved().contains("    system:"), "only meta.next-system mentions systems");
        HvacRegistry reloaded = new HvacRegistry(host, SETTINGS);
        reloaded.load();
        assertNull(reloaded.settingsPanelAt(at(0, 64, 0)).system());
        assertNull(reloaded.rpmMonitorAt(at(1, 64, 0)).system());
        assertNull(reloaded.indexDiscrepancy());
    }
}
