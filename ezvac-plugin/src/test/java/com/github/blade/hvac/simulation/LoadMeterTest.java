package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.OperatingProfile;
import com.github.blade.hvac.model.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The thermostat's meter (3.1): the system's load, 0 to 100%, whatever the
 * number or tiers of its units. It used to be raw capacity, past 100%.
 */
class LoadMeterTest {
    private static final HvacSettings SETTINGS = HvacSettings.defaults();
    private static final double EPSILON = 1e-9;

    private static double full(EquipmentType type, Tier tier, OperatingProfile profile) {
        return PerformanceModel.fullCapacity(type, tier, profile, SETTINGS);
    }

    @Test
    void flatOutIsAHundredPercentWhateverTheTier() {
        for (Tier tier : Tier.values()) {
            double pump = full(EquipmentType.HEAT_PUMP, tier, OperatingProfile.NORMAL);
            assertEquals(1.0, PerformanceModel.load(0, pump, 0, pump, SETTINGS), EPSILON, tier.name());
            double furnace = full(EquipmentType.FURNACE, tier, OperatingProfile.NORMAL);
            assertEquals(1.0, PerformanceModel.load(furnace, 0, furnace, 0, SETTINGS), EPSILON, tier.name());
        }
        assertEquals(0.70, full(EquipmentType.HEAT_PUMP, Tier.SINGLE_STAGE, OperatingProfile.NORMAL), EPSILON,
                "a single-stage unit is the budget model");
        assertEquals(1.0, full(EquipmentType.HEAT_PUMP, Tier.VARIABLE, OperatingProfile.NORMAL), EPSILON);
        assertEquals(1.0, full(EquipmentType.BOILER, Tier.VARIABLE, OperatingProfile.TURBO), EPSILON,
                "profiles never touch fixed-speed units");
    }

    @Test
    void twoUnitsWithOneRunningAreNotAt141Percent() {
        double one = full(EquipmentType.HEAT_PUMP, Tier.VARIABLE, OperatingProfile.NORMAL);
        double load = PerformanceModel.load(0, one, 0, 2 * one, SETTINGS);
        assertEquals(1.0 / Math.sqrt(2.0), load, EPSILON, "one of two flat out: diminishing returns count");
        assertEquals(1.0, PerformanceModel.load(0, 2 * one, 0, 2 * one, SETTINGS), EPSILON);
        assertEquals(1.0, PerformanceModel.load(0, 5 * one, 0, 5 * one, SETTINGS), EPSILON, "past the system cap too");
    }

    @Test
    void theProfileDecidesWhatFlatOutIs() {
        double normalTop = PerformanceModel.variableCapacityFromRpm(SETTINGS.variableMaximumRpm(), SETTINGS);
        double turbo = full(EquipmentType.AIR_CONDITIONER, Tier.VARIABLE, OperatingProfile.TURBO);
        assertEquals(1.15, turbo, EPSILON);
        // Turbo's 115% is past one unit, so it too gets the diminishing returns.
        assertEquals(normalTop / Math.sqrt(turbo), PerformanceModel.load(0, normalTop, 0, turbo, SETTINGS), EPSILON,
                "Normal's top speed is not flat out in Turbo");
        double eco = full(EquipmentType.AIR_CONDITIONER, Tier.VARIABLE, OperatingProfile.ECO);
        assertEquals(1.0, PerformanceModel.load(0, eco, 0, eco, SETTINGS), EPSILON, "Eco's top speed is Eco's 100%");
    }

    @Test
    void aSystemWithNothingToRunReadsNothing() {
        assertEquals(0.0, PerformanceModel.load(0, 0, 0, 0, SETTINGS));
        assertEquals(0.0, PerformanceModel.load(0, 0, 1, 1, SETTINGS));
    }
}
