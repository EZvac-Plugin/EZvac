package com.github.blade.hvac.service;

import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.simulation.WindModel;

/**
 * How the wind reads on signs, the HUD and in chat (3.2): {@code Calm},
 * {@code Windy 22 km/h}. Speeds are in km/h with °C and mph with °F unless
 * {@code wind.units} says otherwise. Pure.
 */
public final class WindText {
    private WindText() {}

    /** The unit speeds show in. */
    public static WindSettings.Units units(WindSettings settings, TemperatureUnit temperature) {
        WindSettings.Units units = settings.units();
        if (units != WindSettings.Units.AUTO) return units;
        return temperature == TemperatureUnit.C ? WindSettings.Units.KMH : WindSettings.Units.MPH;
    }

    public static double convert(double ms, WindSettings.Units units) {
        return switch (units) {
            case MS -> ms;
            case KMH, AUTO -> ms * 3.6;
            case MPH -> ms * 2.236_936;
        };
    }

    public static String unit(WindSettings.Units units) {
        return switch (units) {
            case MS -> "m/s";
            case KMH, AUTO -> "km/h";
            case MPH -> "mph";
        };
    }

    /** {@code 22 km/h}. */
    public static String speed(double ms, WindSettings.Units units) {
        return Math.round(convert(Math.max(0.0, ms), units)) + " " + unit(units);
    }

    /** The level a speed reads as. */
    public static WindModel.Level level(double ms, WindSettings settings) {
        return WindModel.Level.of(ms, settings.speeds());
    }

    /** {@code Calm}, or {@code Windy 22 km/h}. */
    public static String describe(double ms, WindSettings settings, TemperatureUnit temperature) {
        WindModel.Level level = level(ms, settings);
        if (level == WindModel.Level.CALM) return level.displayName();
        return level.displayName() + " " + speed(ms, units(settings, temperature));
    }

    /** For a sign line, where every pixel counts: {@code Calm}, or {@code Gale 61km/h}. */
    public static String sign(double ms, WindSettings settings, TemperatureUnit temperature) {
        WindModel.Level level = level(ms, settings);
        if (level == WindModel.Level.CALM) return level.displayName();
        WindSettings.Units units = units(settings, temperature);
        return level.displayName() + " " + Math.round(convert(ms, units)) + unit(units);
    }
}
