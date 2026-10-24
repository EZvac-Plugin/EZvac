package com.github.blade.hvac.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PoolHeatPumpTest {
    @Test
    void aPoolHeatPumpHeatsAndCoolsWaterOnly() {
        EquipmentType type = EquipmentType.POOL_HEAT_PUMP;
        assertTrue(type.supports(OperatingMode.HEATING));
        assertTrue(type.supports(OperatingMode.COOLING));
        assertFalse(type.supports(OperatingMode.IDLE));
        assertTrue(type.waterLoop(), "water loop: links only to grates and pool thermostats");
        assertEquals(Material.WATER, type.outputMaterial());
        assertEquals("Pool heat pump", type.displayName());
    }

    @Test
    void itSharesTheWaterLoopWithTheBoilerButNotWithAirEquipment() {
        assertEquals(EquipmentType.BOILER.waterLoop(), EquipmentType.POOL_HEAT_PUMP.waterLoop());
        assertNotEquals(EquipmentType.HEAT_PUMP.waterLoop(), EquipmentType.POOL_HEAT_PUMP.waterLoop());
    }

    @Test
    void itParsesFromSavedRecords() {
        assertEquals(EquipmentType.POOL_HEAT_PUMP, EquipmentType.parse("POOL_HEAT_PUMP"));
        assertEquals(EquipmentType.POOL_HEAT_PUMP, EquipmentType.parse("pool-heat-pump"));
    }
}
