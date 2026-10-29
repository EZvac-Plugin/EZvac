package com.github.blade.hvac.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceKindTest {

    @Test
    void everyClimateChangingDeviceHasAKind() {
        Set<DeviceKind> kinds = EnumSet.noneOf(DeviceKind.class);
        for (EquipmentType type : EquipmentType.values()) kinds.add(DeviceKind.of(type));
        for (ClimateUnit.Kind kind : ClimateUnit.Kind.values()) kinds.add(DeviceKind.of(kind));
        assertEquals(EnumSet.allOf(DeviceKind.class), kinds);
    }

    @Test
    void namesReadAsConfigWritesThem() {
        for (DeviceKind kind : DeviceKind.values()) assertEquals(kind, DeviceKind.parse(kind.configName()));
        assertEquals(DeviceKind.POOL_HEAT_PUMP, DeviceKind.parse(" Pool_Heat_Pump "));
        assertNull(DeviceKind.parse("thermostat"));
        assertNull(DeviceKind.parse(null));
    }

    @Test
    void aRefusalSaysWhereToSwitchItBack() {
        String refusal = DeviceKind.FURNACE.refusal();
        assertTrue(refusal.startsWith("Furnaces are switched off on this server"), refusal);
        assertTrue(refusal.contains("/hvac quicksettings"), refusal);
    }
}
