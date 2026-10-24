package com.github.blade.hvac.gui;

import com.github.blade.hvac.config.TemperatureUnit;

/**
 * The arithmetic behind the control menu's buttons (2.1 Pilot 3). Pure.
 */
public final class Setpoints {
    private Setpoints() {}

    /**
     * A thermostat target moved by a step in the server's unit: +1 is a
     * degree Fahrenheit, or a degree Celsius on a Celsius server. The result is
     * rounded to a tenth in that unit, so pressing +0.1 ten times is exactly +1,
     * and stays within {@code minimumF}..{@code maximumF}.
     */
    public static double stepTarget(double targetF, double step, TemperatureUnit unit,
                                    double minimumF, double maximumF) {
        double shown = Math.round((unit.fromF(targetF) + step) * 10.0) / 10.0;
        double stepped = unit.toF(shown);
        return Math.max(minimumF, Math.min(maximumF, stepped));
    }

    /** A humidity setpoint moved by a step, within {@code minimum}..{@code maximum} percent. */
    public static int stepHumidity(int setpoint, int step, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, setpoint + step));
    }
}
