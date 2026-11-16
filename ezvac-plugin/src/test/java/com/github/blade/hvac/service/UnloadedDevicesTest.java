package com.github.blade.hvac.service;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Devices in unloaded chunks (BETA 1.0 Pilot 8). With no server in a unit
 * test, every chunk reads as unloaded, so these exercise the unloaded path.
 */
class UnloadedDevicesTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final HvacSettings SETTINGS = HvacSettings.defaults();

    @TempDir Path folder;
    private HvacRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new HvacRegistry(new TestHost(folder.toFile()), SETTINGS);
    }

    private static BlockKey at(int x, int y, int z) {
        return new BlockKey(WORLD, x, y, z);
    }

    private static Long2ByteOpenHashMap room(int cells) {
        Long2ByteOpenHashMap map = new Long2ByteOpenHashMap();
        map.defaultReturnValue((byte) -1);
        for (int i = 0; i < cells; i++) map.put(AirflowService.pack(i % 10, 64 + i / 100, (i / 10) % 10), (byte) 1);
        return map;
    }

    private Thermostat thermostat(int x) {
        Thermostat thermostat = new Thermostat(at(x, 64, 0), "Home" + x, registry.newSystem(WORLD),
                Thermostat.ROOM_DEFAULT_F, 70, SETTINGS, false);
        registry.addThermostat(thermostat);
        return thermostat;
    }

    @Test
    void itIsOnByDefault() {
        assertTrue(SETTINGS.unloadedDevices());
    }

    @Test
    void equipmentRunsInItsNumbersAndTouchesNoBlock() {
        EquipmentUnit unit = new EquipmentUnit(at(5, 64, 0), new SystemId(WORLD, "#1"), EquipmentType.HEAT_PUMP);
        assertFalse(unit.inReach(), "no server: its chunk reads as unloaded");
        assertTrue(unit.availableUnloaded(OperatingMode.HEATING));
        assertEquals(EquipmentUnit.Actuation.STARTED, unit.startUnloaded(OperatingMode.HEATING));
        assertTrue(unit.running() && unit.deliversUnloaded());
        assertNull(unit.ownedOutput(), "nothing placed");
        assertEquals(EquipmentUnit.Actuation.UNCHANGED, unit.startUnloaded(OperatingMode.HEATING));
        unit.advanceMotor(1.0, 200, SETTINGS);
        assertTrue(unit.motorRpm() > 0.0 && unit.operatingCapacity(SETTINGS) > 0.0, "its motor runs");

        unit.setEnabled(false);
        assertFalse(unit.availableUnloaded(OperatingMode.HEATING));
        assertFalse(unit.deliversUnloaded());
        assertEquals(EquipmentUnit.Actuation.STOPPED, unit.startUnloaded(OperatingMode.HEATING), "off stops it");
        assertFalse(new EquipmentUnit(at(6, 64, 0), new SystemId(WORLD, "#1"), EquipmentType.FURNACE)
                .availableUnloaded(OperatingMode.COOLING), "a furnace can't cool, loaded or not");
    }

    @Test
    void whatItLastSawInFrontOfItIsSaved() {
        EquipmentUnit unit = new EquipmentUnit(at(5, 64, 0), new SystemId(WORLD, "#1"), EquipmentType.HEAT_PUMP);
        Map<String, Object> map = unit.toMap();
        assertFalse(map.containsKey("frontDx") || map.containsKey("outputBlocked"), "nothing seen, nothing saved");
        map.put("frontDx", 1);
        map.put("frontDy", 0);
        map.put("frontDz", 0);
        map.put("outputBlocked", true);
        EquipmentUnit loaded = EquipmentUnit.fromMap(map);
        assertEquals(at(6, 64, 0), loaded.front());
        assertTrue(loaded.outputBlocked());
        assertFalse(loaded.availableUnloaded(OperatingMode.HEATING), "blocked when last seen: not counted on");
        assertEquals(map, loaded.toMap(), "and saved back the same");
    }

    @Test
    void aMapOutOfReachIsKeptNotWalkedAgain() {
        AirflowService airflow = new AirflowService(registry, new OutdoorTemperatureService(), SETTINGS);
        SystemId system = new SystemId(WORLD, "#1");
        airflow.putMappedSystem(system, room(400), false, 3);
        airflow.settleShapes();
        assertEquals(new AirSummary(400, 3, false, 400), airflow.summary(system));
        assertFalse(airflow.frozen(system));

        assertTrue(airflow.freezeNow(system));
        assertTrue(airflow.frozen(system));
        assertEquals(400, airflow.shape(system).owned(), "its size stands");
        assertEquals(3, airflow.shape(system).openings());
        assertEquals(400, airflow.mapSize(system));
        assertNull(airflow.summary(system), "a kept map isn't saved over the walked one's summary");
        assertNull(airflow.systemAt(WORLD, 1, 64, 1), "its cells are dropped");

        airflow.putMappedSystem(system, room(200), false, 0);
        airflow.settleShapes();
        assertEquals(200, airflow.shape(system).owned(), "walked again once in reach");
        assertFalse(airflow.freezeNow(new SystemId(WORLD, "#9")), "nothing to keep: walked as before");
    }

    @Test
    void theSummaryIsSavedAndKeptAfterARestart() {
        Thermostat thermostat = thermostat(0);
        thermostat.setAirSummary(new AirSummary(1_200, 4, false, 1_350));
        Thermostat loaded = Thermostat.fromMap(thermostat.toMap(), SETTINGS);
        assertEquals(new AirSummary(1_200, 4, false, 1_350), loaded.airSummary());
        assertNull(Thermostat.fromMap(thermostat(20).toMap(), SETTINGS).airSummary(), "older saves have none");

        AirflowService airflow = new AirflowService(registry, new OutdoorTemperatureService(), SETTINGS);
        assertEquals(1_200, airflow.shape(thermostat.system()).owned(), "it runs at its size before it is walked");
        assertEquals(1_350, airflow.mapSize(thermostat.system()));
        assertTrue(airflow.frozen(thermostat.system()));
    }

    @Test
    void aFridgeOutOfReachKeepsToItsRoom() {
        Thermostat thermostat = thermostat(0);
        thermostat.setRoomF(78.0);
        AirflowService airflow = new AirflowService(registry, new OutdoorTemperatureService(), SETTINGS);
        ClimateUnitService units = new ClimateUnitService(registry, airflow, new OutdoorTemperatureService(),
                new AirQualityService(airflow, AirQualitySettings.defaults()));
        Fridge fridge = new Fridge(at(3, 64, 0), at(2, 64, 0), at(2, 65, 0), "Kitchen", 37);
        assertTrue(Double.isNaN(units.airAround(fridge)), "serving nothing, never seen: it holds");
        fridge.setServedSystem(thermostat.system());
        assertEquals(78.0, units.airAround(fridge), 1e-9, "its room's temperature");
        fridge.setServedSystem(null);
        fridge.setLastAirF(55.0);
        assertEquals(55.0, units.airAround(fridge), 1e-9, "else the air it last stood in");
        units.setUnloadedDevices(false);
        assertTrue(Double.isNaN(units.airAround(fridge)), "switched off: as before Pilot 8");
    }

    @Test
    void aUnitKeepsTheRoomItServesAcrossARestart() {
        Fridge fridge = new Fridge(at(3, 64, 0), at(2, 64, 0), at(2, 65, 0), "Kitchen", 37);
        fridge.setServedSystem(new SystemId(WORLD, "#4"));
        fridge.setLastAirF(66.5);
        Map<String, Object> map = fridge.toMap();
        Fridge loaded = Fridge.fromMap(map);
        assertNull(loaded.servedSystem(), "the model alone doesn't read it");
        loaded.readUnloaded(map);
        assertEquals(new SystemId(WORLD, "#4"), loaded.servedSystem());
        assertEquals(66.5, loaded.lastAirF(), 1e-9);
    }

    @Test
    void aFilterFilledWhileAwayIsntTakenForANewOne() {
        AirPurifier purifier = new AirPurifier(at(0, 64, 0), at(1, 64, 0), at(1, 65, 0), "Hall", AirPurifier.Mode.AUTO);
        assertFalse(purifier.spentUnseen());
        purifier.showSpentFilter();
        assertTrue(purifier.spentUnseen(), "its coral couldn't die unloaded");
        assertTrue(AirPurifier.fromMap(purifier.toMap()).spentUnseen(), "remembered across a restart");
    }
}
