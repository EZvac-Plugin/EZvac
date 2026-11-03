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

    /** The same with where it blows from: {@code Gale 61km/h NW}; Calm has no direction (BETA 1.0 Pilot 3). */
    public static String sign(double ms, double fromDegrees, WindSettings settings, TemperatureUnit temperature) {
        String line = sign(ms, settings, temperature);
        return level(ms, settings) == WindModel.Level.CALM || !Double.isFinite(fromDegrees) ? line
                : line + " " + compass(fromDegrees);
    }

    /** {@code Calm}, or {@code Windy 22 km/h from the W} (BETA 1.0 Pilot 3). */
    public static String describe(double ms, double fromDegrees, WindSettings settings, TemperatureUnit temperature) {
        String line = describe(ms, settings, temperature);
        return level(ms, settings) == WindModel.Level.CALM || !Double.isFinite(fromDegrees) ? line
                : line + " from the " + compass(fromDegrees);
    }

    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /** Where the wind blows from as one of eight compass points: {@code N}, {@code NE} .. {@code NW}. */
    public static String compass(double fromDegrees) {
        return POINTS[(int) Math.floorMod(Math.round(WindModel.normalize(fromDegrees) / 45.0), 8L)];
    }

    /**
     * A direction typed in a command: a compass point ({@code nw}, also the
     * sixteen-point {@code wsw}) or degrees ({@code 270}); NaN when it is neither.
     */
    public static double parseDirection(String text) {
        if (text == null || text.isBlank()) return Double.NaN;
        String upper = text.trim().toUpperCase(java.util.Locale.ROOT);
        String[] sixteen = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW",
                "NW", "NNW"};
        for (int i = 0; i < sixteen.length; i++) if (sixteen[i].equals(upper)) return i * 22.5;
        try {
            double degrees = Double.parseDouble(upper.endsWith("°") ? upper.substring(0, upper.length() - 1) : upper);
            return Double.isFinite(degrees) && degrees >= 0.0 && degrees <= 360.0 ? WindModel.normalize(degrees)
                    : Double.NaN;
        } catch (NumberFormatException notANumber) {
            return Double.NaN;
        }
    }
}
