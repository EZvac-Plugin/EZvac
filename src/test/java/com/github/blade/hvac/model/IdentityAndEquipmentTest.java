package com.github.blade.hvac.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentityAndEquipmentTest {
    @Test
    void groupNormalizationIsLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("inside", new GroupId(UUID.randomUUID(), "INSIDE").label());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void groupLabelsRejectAmbiguousOrUnsafeValues() {
        UUID world = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new GroupId(world, ""));
        assertThrows(IllegalArgumentException.class, () -> new GroupId(world, "two words"));
        assertThrows(IllegalArgumentException.class, () -> new GroupId(world, "../group"));
    }

    @Test
    void equipmentCapabilitiesAndOutputsAreExplicit() {
        assertTrue(EquipmentType.HEAT_PUMP.supports(OperatingMode.COOLING));
        assertTrue(EquipmentType.HEAT_PUMP.supports(OperatingMode.HEATING));
        assertEquals(Material.WATER, EquipmentType.HEAT_PUMP.outputMaterial());
        assertTrue(EquipmentType.AIR_CONDITIONER.supports(OperatingMode.COOLING));
        assertFalse(EquipmentType.AIR_CONDITIONER.supports(OperatingMode.HEATING));
        assertTrue(EquipmentType.FURNACE.supports(OperatingMode.HEATING));
        assertFalse(EquipmentType.FURNACE.supports(OperatingMode.COOLING));
        assertEquals(Material.LAVA, EquipmentType.FURNACE.outputMaterial());
    }

    @Test
    void legacyEquipmentTypeValuesMigrateSafely() {
        assertEquals(EquipmentType.HEAT_PUMP, EquipmentType.parse(null));
        assertEquals(EquipmentType.HEAT_PUMP, EquipmentType.parse("unknown"));
        assertEquals(EquipmentType.AIR_CONDITIONER, EquipmentType.parse("AC"));
    }
}
