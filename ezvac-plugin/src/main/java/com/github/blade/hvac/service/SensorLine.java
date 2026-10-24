package com.github.blade.hvac.service;

import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.model.Sensor;


/**
 * One sensor's reading as a single sign line: {@code Kitchen 72.4F},
 * {@code Cellar 58%}, {@code Kitchen 72F 45%}, {@code Garden Rain}, or
 * {@code Kitchen --} when it can't be read. The reading always fits; a long label is cut short to
 * make room for it, measured in pixels like every sign line. Pure.
 */
public final class SensorLine {
    private SensorLine() {}

    /** The line for a standing or wall sign. */
    public static String format(String label, Sensor.Kind reads, boolean loaded,
                                double temperatureF, double humidity, String weather) {
        return format(label, reads, loaded, temperatureF, humidity, weather, SignText.SIGN_WIDTH);
    }

    /**
     * @param loaded   false when the sensor's chunk is unloaded
     * @param humidity RH in percent; NaN where there is none (a sensor in water)
     * @param weather  what a weather sensor reports ({@code Rain}, {@code Covered}); unused by the others
     * @param width    the sign's line width in pixels
     */
    public static String format(String label, Sensor.Kind reads, boolean loaded,
                                double temperatureF, double humidity, String weather, int width) {
        return format(label, reads, loaded, temperatureF, humidity, weather, width, TemperatureUnit.F);
    }

    /** @param units the unit players read temperatures in */
    public static String format(String label, Sensor.Kind reads, boolean loaded, double temperatureF,
                                double humidity, String weather, int width, TemperatureUnit units) {
        String value = !loaded ? "--" : switch (reads) {
            case TEMPERATURE -> units.compact(temperatureF);
            case HUMIDITY -> percent(humidity);
            case BOTH -> units.whole(temperatureF) + " " + percent(humidity);
            case WEATHER -> weather;
        };
        String fitted = fit(label, width - SignText.width(" " + value));
        // Only a hanging sign showing an extreme reading has no room left for even one letter.
        return SignText.width(fitted + " " + value) <= width ? fitted + " " + value : value;
    }

    /** A label cut to fit the given pixels, never empty. */
    public static String fit(String label, int width) {
        String fitted = SignText.fit(label, width);
        return fitted.isEmpty() && !label.isEmpty() ? label.substring(0, 1) : fitted;
    }

    private static String percent(double humidity) {
        return Double.isFinite(humidity) ? Math.round(humidity) + "%" : "--%";
    }
}
