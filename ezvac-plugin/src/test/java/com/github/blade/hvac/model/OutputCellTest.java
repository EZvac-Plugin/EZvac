package com.github.blade.hvac.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a unit may put its output over (BETA 1.0). */
class OutputCellTest {

    @Test
    void airAndFlowingFluidMakeWayForAnOutput() {
        assertTrue(EquipmentUnit.clearable(Material.AIR, true, 0));
        assertTrue(EquipmentUnit.clearable(Material.CAVE_AIR, true, 0));
        assertTrue(EquipmentUnit.clearable(Material.WATER, false, 1), "water spreading from a neighbour's output");
        assertTrue(EquipmentUnit.clearable(Material.WATER, false, 8), "water falling into the cell");
        assertTrue(EquipmentUnit.clearable(Material.LAVA, false, 2));
    }

    @Test
    void aSourceOrABlockIsLeftAlone() {
        assertFalse(EquipmentUnit.clearable(Material.WATER, false, 0), "a source may be a player's");
        assertFalse(EquipmentUnit.clearable(Material.LAVA, false, 0));
        assertFalse(EquipmentUnit.clearable(Material.STONE, false, 0));
        assertFalse(EquipmentUnit.clearable(Material.GLASS, false, 0));
    }
}
