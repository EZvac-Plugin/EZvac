package com.github.blade.hvac.model;

import com.github.blade.hvac.config.HvacSettings;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OwnershipTest {
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();

    @Test
    void onlyTheOwnerAndAdministratorsMayModify() {
        assertTrue(Ownership.mayModify(alice, alice, false), "an owner may edit their own device");
        assertFalse(Ownership.mayModify(alice, bob, false), "a stranger may not");
        assertTrue(Ownership.mayModify(alice, bob, true), "an administrator overrides ownership");
    }

    @Test
    void unownedDevicesAreAdministratorOnly() {
        // The point of the whole feature: devices from before ownership existed
        // must not become editable by everybody the moment building is opened up.
        assertFalse(Ownership.mayModify(null, alice, false));
        assertFalse(Ownership.mayModify(null, null, false));
        assertTrue(Ownership.mayModify(null, alice, true));
    }

    @Test
    void unreadableOwnersDegradeToUnownedRatherThanThrowing() {
        assertNull(Ownership.read(null));
        assertNull(Ownership.read(""));
        assertNull(Ownership.read("   "));
        assertNull(Ownership.read("not-a-uuid"));
        assertNull(Ownership.read(42));
        assertEquals(alice, Ownership.read(alice.toString()));
    }

    @Test
    void unownedRecordsStayFreeOfTheKey() {
        Map<String, Object> map = new LinkedHashMap<>();
        Ownership.write(map, null);
        assertTrue(map.isEmpty(), "an unowned device must not write an owner key at all");
        Ownership.write(map, alice);
        assertEquals(alice.toString(), map.get("owner"));
    }

    @Test
    void everyDeviceTypeCarriesItsOwnerThroughASaveAndLoad() {
        BlockKey position = new BlockKey(world, 4, 70, -9);
        HvacSettings settings = HvacSettings.defaults();

        Thermostat thermostat = new Thermostat(position, "main", "house", 72, 70, settings);
        thermostat.setOwner(alice);
        assertEquals(alice, Thermostat.fromMap(thermostat.toMap(), settings).owner());

        EquipmentUnit unit = new EquipmentUnit(position, "house", EquipmentType.HEAT_PUMP);
        unit.setOwner(alice);
        assertEquals(alice, EquipmentUnit.fromMap(unit.toMap()).owner());

        SettingsPanel panel = new SettingsPanel(position, "house", "Controls");
        panel.setOwner(alice);
        assertEquals(alice, SettingsPanel.fromMap(panel.toMap()).owner());

        ClimateVent vent = new ClimateVent(position, new GroupId(world, "house"), alice);
        assertEquals(alice, ClimateVent.fromMap(vent.toMap()).owner());

        Thermometer probe = new Thermometer(position, "Probe", alice);
        assertEquals(alice, Thermometer.fromMap(probe.toMap()).owner());

        RpmMonitor monitor = new RpmMonitor(position, new GroupId(world, "house"), "RPM",
                EquipmentType.HEAT_PUMP, alice);
        assertEquals(alice, RpmMonitor.fromMap(monitor.toMap()).owner());
    }

    @Test
    void recordsFromBeforeOwnershipLoadAsUnowned() {
        // A 1.6 registry has no owner keys anywhere. Loading one must work and
        // must leave every device unowned rather than guessing at an owner.
        BlockKey position = new BlockKey(world, 0, 64, 0);
        HvacSettings settings = HvacSettings.defaults();

        Map<String, Object> legacy = new LinkedHashMap<>(
                new Thermostat(position, "main", "house", 72, 70, settings).toMap());
        assertFalse(legacy.containsKey("owner"), "an unstamped thermostat writes no owner key");
        assertNull(Thermostat.fromMap(legacy, settings).owner());

        Map<String, Object> corrupt = new LinkedHashMap<>(legacy);
        corrupt.put("owner", "was-hand-edited");
        assertNull(Thermostat.fromMap(corrupt, settings).owner(),
                "a mangled owner must not prevent the device from loading");
    }
}
