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
 * Pins the registry rules other code relies on, so internal changes (such as
 * indexing) cannot quietly change which device wins or what gets kept.
 */
class RegistryBehaviourTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final HvacSettings SETTINGS = HvacSettings.defaults();

    @TempDir Path folder;
    private TestHost host;

    @BeforeEach
    void setUp() {
        host = new TestHost(folder.toFile());
    }

    private static BlockKey at(int x, int y, int z) {
        return new BlockKey(WORLD, x, y, z);
    }

    private static String position(int x, int y, int z) {
        return "    worldUuid: " + WORLD + "\n    x: " + x + "\n    y: " + y + "\n    z: " + z + "\n";
    }

    private HvacRegistry loadYaml(String yaml) throws IOException {
        Files.writeString(folder.resolve("hvac.yml"), "meta:\n  format: 5\n" + yaml, StandardCharsets.UTF_8);
        HvacRegistry registry = new HvacRegistry(host, SETTINGS);
        registry.load();
        return registry;
    }

    private String saved(HvacRegistry registry) throws IOException {
        registry.markDirty();
        registry.save(true);
        return Files.readString(folder.resolve("hvac.yml"), StandardCharsets.UTF_8);
    }

    @Test
    void duplicateGroupControllerKeepsTheLowestPositionAndPreservesTheOther() throws IOException {
        HvacRegistry registry = loadYaml("""
                thermostats:
                  a:
                """ + position(50, 64, 0) + """
                    id: upper
                    group: house
                  b:
                """ + position(10, 64, 0) + """
                    id: lower
                    group: house
                """);
        GroupId house = new GroupId(WORLD, "house");
        assertEquals(1, registry.thermostats().size());
        assertEquals("lower", registry.thermostatFor(house).id());
        assertNull(registry.thermostatById("upper"));
        String file = saved(registry);
        assertTrue(file.contains("id: upper"), "the losing controller must be written back, not deleted");
    }

    @Test
    void duplicateIdsCompareCaseInsensitivelyAndTheLowestPositionWins() throws IOException {
        HvacRegistry registry = loadYaml("""
                thermostats:
                  a:
                """ + position(30, 64, 0) + """
                    id: Main
                    group: one
                  b:
                """ + position(20, 64, 0) + """
                    id: MAIN
                    group: two
                """);
        assertEquals(1, registry.thermostats().size());
        assertEquals("two", registry.thermostatById("main").group().label());
        assertEquals(at(20, 64, 0), registry.thermostatById("mAiN").position());
    }

    @Test
    void duplicateSettingsPanelsKeepTheLowestPosition() throws IOException {
        HvacRegistry registry = loadYaml("""
                settingsPanels:
                  a:
                """ + position(9, 64, 0) + """
                    group: house
                    label: second
                  b:
                """ + position(3, 64, 0) + """
                    group: house
                    label: first
                """);
        assertEquals(1, registry.settingsPanels().size());
        assertEquals("first", registry.settingsPanelFor(new GroupId(WORLD, "house")).label());
        assertTrue(saved(registry).contains("label: second"));
    }

    @Test
    void aDeviceOnAThermostatBlockIsPreservedNotLoaded() throws IOException {
        HvacRegistry registry = loadYaml("""
                thermostats:
                  a:
                """ + position(1, 64, 1) + """
                    id: main
                    group: house
                equipment:
                  a:
                """ + position(1, 64, 1) + """
                    group: house
                    equipmentType: HEAT_PUMP
                """);
        assertEquals(1, registry.thermostats().size());
        assertTrue(registry.equipment().isEmpty());
        assertTrue(saved(registry).contains("HEAT_PUMP"));
    }

    @Test
    void unreadableRecordsAndUnknownSectionsSurviveASave() throws IOException {
        HvacRegistry registry = loadYaml("""
                vents:
                  broken:
                    world: some_world_that_is_not_loaded
                    x: 1
                    y: 2
                    z: 3
                    group: house
                futureDevices:
                  a:
                    kind: something
                """);
        assertTrue(registry.vents().isEmpty());
        String file = saved(registry);
        assertTrue(file.contains("some_world_that_is_not_loaded"));
        assertTrue(file.contains("kind: something"));
    }

    @Test
    void equipmentForIsSortedByPosition() {
        HvacRegistry registry = new HvacRegistry(host, SETTINGS);
        registry.addEquipment(new EquipmentUnit(at(30, 64, 0), "house", EquipmentType.FURNACE));
        registry.addEquipment(new EquipmentUnit(at(10, 64, 0), "house", EquipmentType.HEAT_PUMP));
        registry.addEquipment(new EquipmentUnit(at(20, 64, 0), "house", EquipmentType.AIR_CONDITIONER));
        registry.addEquipment(new EquipmentUnit(at(15, 64, 0), "other", EquipmentType.HEAT_PUMP));
        List<BlockKey> order = registry.equipmentFor(new GroupId(WORLD, "house")).stream()
                .map(EquipmentUnit::position).toList();
        assertEquals(List.of(at(10, 64, 0), at(20, 64, 0), at(30, 64, 0)), order);
    }

    @Test
    void addingASecondControllerForAGroupIsRefused() {
        HvacRegistry registry = new HvacRegistry(host, SETTINGS);
        registry.addThermostat(new Thermostat(at(1, 64, 0), "a", "house", 72, 72, SETTINGS));
        assertThrows(IllegalArgumentException.class, () -> registry.addThermostat(
                new Thermostat(at(2, 64, 0), "b", "house", 72, 72, SETTINGS)));
        assertThrows(IllegalArgumentException.class, () -> registry.addThermostat(
                new Thermostat(at(3, 64, 0), "A", "elsewhere", 72, 72, SETTINGS)));
    }

    @Test
    void changingAnOwnedOutputReleasesItForActiveAndPendingUnits() throws IOException {
        HvacRegistry registry = loadYaml("""
                equipment:
                  a:
                """ + position(10, 64, 0) + """
                    group: house
                    equipmentType: HEAT_PUMP
                    running: true
                    operatingMode: COOLING
                """ + position(11, 64, 0).replace("    worldUuid", "    outputWorldUuid")
                        .replace("    x:", "    outputX:").replace("    y:", "    outputY:")
                        .replace("    z:", "    outputZ:") + """
                cleanup:
                  a:
                """ + position(20, 64, 0) + """
                    group: house
                    equipmentType: HEAT_PUMP
                """ + position(21, 64, 0).replace("    worldUuid", "    outputWorldUuid")
                        .replace("    x:", "    outputX:").replace("    y:", "    outputY:")
                        .replace("    z:", "    outputZ:"));
        EquipmentUnit active = registry.equipmentAt(at(10, 64, 0));
        EquipmentUnit pending = registry.pendingCleanup().iterator().next();
        assertEquals(at(11, 64, 0), active.ownedOutput());
        assertEquals(at(21, 64, 0), pending.ownedOutput());

        assertFalse(registry.releaseOutputOwnership(at(99, 64, 0)), "an unrelated block releases nothing");
        assertTrue(registry.releaseOutputOwnership(at(11, 64, 0)));
        assertNull(active.ownedOutput());
        assertTrue(registry.releaseOutputOwnership(at(21, 64, 0)));
        assertNull(pending.ownedOutput());
        assertFalse(registry.releaseOutputOwnership(at(11, 64, 0)), "already released");
    }

    @Test
    void removingEquipmentThatOwnsAnUnloadedOutputQueuesCleanup() throws IOException {
        HvacRegistry registry = loadYaml("""
                equipment:
                  a:
                """ + position(10, 64, 0) + """
                    group: house
                    equipmentType: FURNACE
                """ + position(11, 64, 0).replace("    worldUuid", "    outputWorldUuid")
                        .replace("    x:", "    outputX:").replace("    y:", "    outputY:")
                        .replace("    z:", "    outputZ:"));
        EquipmentUnit removed = registry.removeEquipment(at(10, 64, 0));
        assertNotNull(removed);
        assertNull(registry.equipmentAt(at(10, 64, 0)));
        // With no world loaded the output cannot be retracted yet, so it waits in cleanup.
        assertEquals(List.of(removed), List.copyOf(registry.pendingCleanup()));
        assertTrue(registry.equipmentFor(removed.group()).isEmpty());
    }

    @Test
    void ventsReplaceInPlaceAndRemoveCleanly() {
        HvacRegistry registry = new HvacRegistry(host, SETTINGS);
        registry.addVent(new ClimateVent(at(5, 66, 5), new GroupId(WORLD, "house")));
        registry.addVent(new ClimateVent(at(5, 66, 5), new GroupId(WORLD, "office")));
        assertEquals(1, registry.vents().size());
        assertEquals("office", registry.ventAt(at(5, 66, 5)).group().label());
        assertNotNull(registry.removeVent(at(5, 66, 5)));
        assertTrue(registry.vents().isEmpty());
        assertNull(registry.removeVent(at(5, 66, 5)));
    }

    @Test
    void sensorsAndHubsSaveAndARemovedSensorLeavesEveryHub() throws IOException {
        HvacRegistry registry = new HvacRegistry(host, SETTINGS);
        Sensor kitchen = new Sensor(at(5, 65, 5), "kitchen", Thermometer.Reads.BOTH);
        Sensor cellar = new Sensor(at(5, 60, 9), "cellar", Thermometer.Reads.HUMIDITY);
        registry.addSensor(kitchen);
        registry.addSensor(cellar);
        SensorHub hall = new SensorHub(at(0, 65, 0), "hall");
        SensorHub office = new SensorHub(at(9, 65, 0), "office");
        hall.link(kitchen.position());
        hall.link(cellar.position());
        office.link(kitchen.position());
        office.setAutoFlip(true);
        registry.addSensorHub(hall);
        registry.addSensorHub(office);
        assertEquals(HvacRegistry.DeviceKind.SENSOR, registry.deviceKind(at(5, 65, 5)));
        assertEquals(HvacRegistry.DeviceKind.SENSOR_HUB, registry.deviceKind(at(0, 65, 0)));
        assertThrows(IllegalArgumentException.class, () -> registry.addSensor(
                new Sensor(at(0, 65, 0), "x", Thermometer.Reads.TEMPERATURE)), "a hub's block is taken");
        assertEquals(List.of(hall, office), registry.hubsShowing(kitchen.position()), "one sensor, two hubs");

        saved(registry);
        HvacRegistry reloaded = new HvacRegistry(host, SETTINGS);
        reloaded.load();
        assertEquals(kitchen, reloaded.sensorAt(at(5, 65, 5)));
        assertEquals(List.of(kitchen.position(), cellar.position()), reloaded.sensorHubAt(at(0, 65, 0)).sensors());
        assertTrue(reloaded.sensorHubAt(at(9, 65, 0)).autoFlip());

        assertNotNull(reloaded.removeSensor(kitchen.position()));
        assertEquals(List.of(cellar.position()), reloaded.sensorHubAt(at(0, 65, 0)).sensors());
        assertTrue(reloaded.sensorHubAt(at(9, 65, 0)).sensors().isEmpty(), "gone from every hub");
        assertNull(reloaded.indexDiscrepancy());
    }

    @Test
    void aHubLinkToASensorThatNoLongerExistsIsDroppedOnLoad() throws IOException {
        HvacRegistry registry = loadYaml("""
                sensors:
                  item0:
                    worldUuid: %s
                    x: 5
                    y: 65
                    z: 5
                    label: kitchen
                    reads: BOTH
                sensorHubs:
                  item0:
                    worldUuid: %s
                    x: 0
                    y: 65
                    z: 0
                    label: hall
                    autoFlip: false
                    sensors: ["5,65,5", "7,65,7"]
                """.formatted(WORLD, WORLD));
        assertEquals(List.of(at(5, 65, 5)), registry.sensorHubAt(at(0, 65, 0)).sensors());
        assertTrue(host.warnings().stream().anyMatch(w -> w.contains("no longer exist")), host.warnings().toString());
    }

    @Test
    void dehumidifiersAndHumidifiersSaveInTheirOwnSectionsAndShareThePartIndex() throws IOException {
        HvacRegistry registry = new HvacRegistry(host, SETTINGS);
        Dehumidifier dry = new Dehumidifier(at(1, 65, 0), at(0, 65, 0), at(0, 64, 0), "basement", 50);
        Humidifier wet = new Humidifier(at(11, 65, 0), at(10, 65, 0), at(10, 66, 0), "nursery", 40);
        registry.addClimateUnit(dry);
        registry.addClimateUnit(wet);
        assertSame(wet, registry.climateUnitAt(at(10, 66, 0)), "the slab finds its humidifier");
        assertSame(dry, registry.dehumidifierAt(at(0, 64, 0)));
        assertNull(registry.dehumidifierAt(at(10, 65, 0)), "a humidifier is not a dehumidifier");
        assertEquals(HvacRegistry.DeviceKind.HUMIDIFIER, registry.deviceKind(at(10, 65, 0)));
        assertThrows(IllegalArgumentException.class, () -> registry.addVent(
                new ClimateVent(at(10, 66, 0), new GroupId(WORLD, "house"))), "a unit's block is taken");
        assertNull(registry.indexDiscrepancy());

        String file = saved(registry);
        assertTrue(file.contains("\ndehumidifiers:\n") && file.contains("\nhumidifiers:\n"), file);
        HvacRegistry reloaded = new HvacRegistry(host, SETTINGS);
        reloaded.load();
        assertEquals(List.of(at(1, 65, 0), at(11, 65, 0)),
                reloaded.climateUnits().stream().map(ClimateUnit::position).toList());
        assertInstanceOf(Humidifier.class, reloaded.climateUnitAt(at(11, 65, 0)));
        assertNull(reloaded.indexDiscrepancy());

        assertSame(wet.getClass(), reloaded.removeClimateUnit(at(10, 66, 0)).getClass(), "removed by its slab");
        assertNull(reloaded.climateUnitAt(at(11, 65, 0)));
        assertEquals(1, reloaded.climateUnits().size());
        assertNull(reloaded.indexDiscrepancy());
    }
}
