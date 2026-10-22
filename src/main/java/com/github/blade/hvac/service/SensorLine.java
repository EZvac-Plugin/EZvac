package com.github.blade.hvac.service;

import com.github.blade.hvac.model.Thermometer;

import java.util.Locale;

/**
 * One sensor's reading as a single sign line: {@code Kitchen 72.4F},
 * {@code Cellar 58%}, {@code Kitchen 72F 45%}, or {@code Kitchen --} when
 * it can't be read. The reading always fits; a long label is cut short to
 * make room for it. Pure.
 */
public final class SensorLine {
    /** Characters a sign line shows in full. */
    public static final int WIDTH = 15;

    private SensorLine() {}

    /**
     * @param loaded   false when the sensor's chunk is unloaded
     * @param humidity RH in percent; NaN where there is none (a sensor in water)
     */
    public static String format(String label, Thermometer.Reads reads, boolean loaded,
                                double temperatureF, double humidity) {
        String value = !loaded ? "--" : switch (reads) {
            case TEMPERATURE -> String.format(Locale.US, "%.1fF", temperatureF);
            case HUMIDITY -> percent(humidity);
            case BOTH -> String.format(Locale.US, "%.0fF %s", temperatureF, percent(humidity));
        };
        return fit(label, WIDTH - 1 - value.length()) + " " + value;
    }

    /** A label cut to fit, never empty. */
    public static String fit(String label, int width) {
        int room = Math.max(1, width);
        return label.length() <= room ? label : label.substring(0, room);
    }

    private static String percent(double humidity) {
        return Double.isFinite(humidity) ? Math.round(humidity) + "%" : "--%";
    }
}
