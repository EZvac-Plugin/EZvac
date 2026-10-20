package com.github.blade.hvac.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NaturalWaterTest {
    @Test
    void naturalWaterFollowsTheBiomeMeanAndStaysLiquid() {
        assertEquals(60.7, ThermalModel.naturalWaterTemperature(72, 52), 0.01, "temperate lake");
        assertEquals(73.03, ThermalModel.naturalWaterTemperature(85, 68), 0.01, "warm sea");
        assertEquals(83.65, ThermalModel.naturalWaterTemperature(100, 78), 0.01, "desert oasis, milder than the air");
        assertEquals(39.03, ThermalModel.naturalWaterTemperature(45, 28), 0.01, "cold lake");
        assertEquals(33.0, ThermalModel.naturalWaterTemperature(25, 5), "snowy water never goes below liquid");
        assertEquals(90.0, ThermalModel.naturalWaterTemperature(112, 112), "capped");
    }

    @Test
    void naturalWaterSitsBetweenTheDayExtremes() {
        double water = ThermalModel.naturalWaterTemperature(85, 68);
        assertTrue(water < 85 && water > 68, "steadier than the air: below a hot noon, above a mild night");
    }

    @Test
    void nonFiniteInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ThermalModel.naturalWaterTemperature(Double.NaN, 50));
    }
}
