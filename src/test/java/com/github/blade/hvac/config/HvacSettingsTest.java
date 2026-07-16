package com.github.blade.hvac.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HvacSettingsTest {
    @Test
    void defaultTuningIsInternallyValid() {
        HvacSettings value = HvacSettings.defaults();
        assertEquals(20, value.controllerPeriodTicks());
        assertEquals(300, value.minimumRunTicks());
        assertEquals(200, value.minimumOffTicks());
        assertEquals(3.0, value.variableApproachBandF());
        assertEquals(0.40, value.variableMinimumCapacity());
        assertEquals(1_200.0, value.variableMinimumRpm());
        assertEquals(3_600.0, value.variableMaximumRpm());
        assertEquals(2_400.0, value.variableEcoMaximumRpm());
        assertEquals(4_200.0, value.variableTurboMaximumRpm());
        assertEquals(15, value.airflowCoreRange());
        assertEquals(30, value.airflowMaximumRange());
    }

    @Test
    void targetsClampButNonFiniteValuesAreRejected() {
        HvacSettings value = HvacSettings.defaults();
        assertEquals(60, value.clampTarget(40));
        assertEquals(85, value.clampTarget(100));
        assertThrows(IllegalArgumentException.class, () -> value.clampTarget(Double.NaN));
    }
}
