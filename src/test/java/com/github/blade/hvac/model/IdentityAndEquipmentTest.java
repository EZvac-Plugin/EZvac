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
        assertEquals(EquipmentType.HEAT_PUMP, EquipmentType.parse(""));
        assertEquals(EquipmentType.AIR_CONDITIONER, EquipmentType.parse("AC"));
        assertEquals(EquipmentType.AIR_CONDITIONER, EquipmentType.parse("air-conditioner"));
    }

    @Test
    void unknownEquipmentTypesAreRejectedRatherThanReinterpreted() {
        // A type written by a newer build must not come back as a different
        // device. Rejecting it lets the registry preserve the record verbatim
        // instead of turning, say, a heating-only unit into a heat pump that
        // also cools.
        assertThrows(IllegalArgumentException.class, () -> EquipmentType.parse("no_such_type"));
        assertThrows(IllegalArgumentException.class, () -> EquipmentType.parse("HEATPUMP2000"));
    }
}
