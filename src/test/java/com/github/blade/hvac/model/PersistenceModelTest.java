package com.github.blade.hvac.model;

import com.github.blade.hvac.config.HvacSettings;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceModelTest {
    private final HvacSettings settings = HvacSettings.defaults();

    @Test
    void thermostatRoundTripPreservesControlAndOutdoorStateWithoutLoadedWorld() {
        BlockKey position = new BlockKey(UUID.randomUUID(), 10, 64, -20);
        Thermostat source = new Thermostat(position, "Main", "downstairs", 74, 81, settings);
        source.setEnabled(false);
        source.setControlState(OperatingMode.COOLING, 287);
        source.setOutdoorProfile(100, 78);
        source.setLastOutdoorF(89);

        Thermostat restored = Thermostat.fromMap(source.toMap(), settings);
        assertEquals(position, restored.position());
        assertEquals("Main", restored.id());
        assertEquals("downstairs", restored.group().label());
        assertEquals(74, restored.targetF());
        assertEquals(81, restored.roomF());
        assertFalse(restored.enabled());
        assertEquals(OperatingMode.COOLING, restored.mode());
        assertEquals(287, restored.ticksInMode());
        assertEquals(100, restored.outdoorNoonF());
        assertEquals(78, restored.outdoorMidnightF());
    }

    @Test
    void legacyThermostatFieldsConvertToNewTickTimer() {
        UUID world = UUID.randomUUID();
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("worldUuid", world.toString());
        legacy.put("x", 1); legacy.put("y", 2); legacy.put("z", 3);
        legacy.put("label", "Legacy");
        legacy.put("hvacGroupLabel", "old_group");
        legacy.put("targetTempF", 70.0);
        legacy.put("roomTempF", 75.0);
        legacy.put("mode", "COOLING");
        legacy.put("modeElapsedMinecraftHours", 0.25);
        Thermostat restored = Thermostat.fromMap(legacy, settings);
        assertEquals(250, restored.ticksInMode());
        assertEquals("old_group", restored.group().label());
    }

    @Test
    void equipmentRoundTripPreservesOwnedOutputAndLegacyAcAlias() {
        UUID world = UUID.randomUUID();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("worldUuid", world.toString());
        values.put("x", 5); values.put("y", 70); values.put("z", 8);
        values.put("label", "upstairs");
        values.put("equipmentType", "AC");
        values.put("enabled", true);
        values.put("running", true);
        values.put("operatingMode", "COOLING");
        values.put("motorRpm", 2400.0);
        values.put("waterWorldUuid", world.toString());
        values.put("waterX", 6); values.put("waterY", 70); values.put("waterZ", 8);

        EquipmentUnit restored = EquipmentUnit.fromMap(values);
        EquipmentUnit roundTripped = EquipmentUnit.fromMap(restored.toMap());
        assertEquals(EquipmentType.AIR_CONDITIONER, roundTripped.type());
        assertEquals(new BlockKey(world, 6, 70, 8), roundTripped.ownedOutput());
        assertTrue(roundTripped.running());
        assertEquals(2400, roundTripped.motorRpm());
    }

    @Test
    void malformedPersistentCoordinatesAreRejectedInsteadOfDefaultingToOrigin() {
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("worldUuid", UUID.randomUUID().toString());
        invalid.put("x", 1); invalid.put("z", 3);
        invalid.put("id", "Broken"); invalid.put("group", "broken");
        assertThrows(IllegalArgumentException.class,
                () -> Thermostat.fromMap(invalid, settings));
    }

    @Test
    void thermometerRoundTripIsIndependentAndLegacyGroupBecomesOnlyALabelFallback() {
        UUID world = UUID.randomUUID();
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("worldUuid", world.toString());
        legacy.put("x", 15); legacy.put("y", 90); legacy.put("z", -4);
        legacy.put("group", "old_outdoor_group");

        Thermometer migrated = Thermometer.fromMap(legacy);
        assertEquals("old_outdoor_group", migrated.label());
        assertEquals(new BlockKey(world, 15, 90, -4), migrated.position());
        assertFalse(migrated.toMap().containsKey("group"));

        Thermometer restored = Thermometer.fromMap(migrated.toMap());
        assertEquals(migrated, restored);
        assertArrayEquals(new String[]{"position", "label"},
                java.util.Arrays.stream(Thermometer.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
    }

    @Test
    void settingsPanelPersistsProfileAndDefaultsUnknownValuesToNormal() {
        BlockKey position = new BlockKey(UUID.randomUUID(), 9, 70, 12);
        SettingsPanel source = new SettingsPanel(position, "main", "Controls");
        assertEquals(OperatingProfile.NORMAL, source.profile());
        source.setProfile(OperatingProfile.TURBO);

        SettingsPanel restored = SettingsPanel.fromMap(source.toMap());
        assertEquals(position, restored.position());
        assertEquals("main", restored.group().label());
        assertEquals("Controls", restored.label());
        assertEquals(OperatingProfile.TURBO, restored.profile());

        Map<String, Object> unknown = new LinkedHashMap<>(source.toMap());
        unknown.put("profile", "unsupported");
        assertEquals(OperatingProfile.NORMAL, SettingsPanel.fromMap(unknown).profile());
        assertEquals(OperatingProfile.NORMAL, OperatingProfile.parse(null));
    }

    @Test
    void settingsPanelProfileCyclingIsReversible() {
        assertEquals(OperatingProfile.TURBO, OperatingProfile.NORMAL.next());
        assertEquals(OperatingProfile.ECO, OperatingProfile.TURBO.next());
        assertEquals(OperatingProfile.NORMAL, OperatingProfile.ECO.next());
        for (OperatingProfile profile : OperatingProfile.values()) {
            assertEquals(profile, profile.next().previous());
            assertEquals(profile, profile.previous().next());
        }
    }
}
