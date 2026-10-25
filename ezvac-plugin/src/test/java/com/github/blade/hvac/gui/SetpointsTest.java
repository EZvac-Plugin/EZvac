package com.github.blade.hvac.gui;

import com.github.blade.hvac.config.TemperatureUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The control menu's buttons (2.1). */
class SetpointsTest {
    private static final double MIN = 60.0, MAX = 85.0;

    private static double step(double targetF, double step, TemperatureUnit unit) {
        return Setpoints.stepTarget(targetF, step, unit, MIN, MAX);
    }

    @Test
    void fahrenheitStepsAreFahrenheitDegrees() {
        assertEquals(75.0, step(70.0, 5, TemperatureUnit.F), 1e-9);
        assertEquals(71.0, step(70.0, 1, TemperatureUnit.F), 1e-9);
        assertEquals(69.9, step(70.0, -0.1, TemperatureUnit.F), 1e-9);
        double target = 70.0;
        for (int press = 0; press < 10; press++) target = step(target, 0.1, TemperatureUnit.F);
        assertEquals(71.0, target, 1e-9, "ten tenths are exactly one degree");
    }

    @Test
    void celsiusStepsAreCelsiusDegrees() {
        double twentyTwo = TemperatureUnit.C.toF(22.0);
        assertEquals(TemperatureUnit.C.toF(23.0), step(twentyTwo, 1, TemperatureUnit.C), 1e-9);
        assertEquals(TemperatureUnit.C.toF(27.0), step(twentyTwo, 5, TemperatureUnit.C), 1e-9);
        assertEquals(TemperatureUnit.C.toF(21.9), step(twentyTwo, -0.1, TemperatureUnit.C), 1e-9);
        // A target set in F (70 F = 21.11 C) lands on a tenth of a degree C after one press.
        assertEquals(TemperatureUnit.C.toF(22.1), step(70.0, 1, TemperatureUnit.C), 1e-9);
    }

    @Test
    void neverPastTheLimits() {
        assertEquals(MAX, step(83.0, 5, TemperatureUnit.F));
        assertEquals(MAX, step(MAX, 0.1, TemperatureUnit.F));
        assertEquals(MIN, step(62.0, -5, TemperatureUnit.F));
        assertEquals(MAX, step(MAX, 5, TemperatureUnit.C));
        assertEquals(104.0, Setpoints.stepTarget(100.0, 5, TemperatureUnit.F, 75.0, 104.0), "a pool's ceiling");
    }

    @Test
    void humidityMovesInFivesWithinItsRange() {
        assertEquals(50, Setpoints.stepHumidity(45, 5, 35, 60));
        assertEquals(60, Setpoints.stepHumidity(58, 5, 35, 60));
        assertEquals(35, Setpoints.stepHumidity(35, -5, 35, 60));
    }
}
