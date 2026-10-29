package com.github.blade.hvac.service;

import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.simulation.Forecast;

import java.util.Locale;

/**
 * How a weather station's forecast reads (BETA 1.0), on its screen and in the
 * forecast menu. Temperatures are whole degrees.
 */
public final class StationText {
    private StationText() {}

    /** {@code 71°F}. */
    public static String temperature(double fahrenheit, TemperatureUnit units) {
        if (!Double.isFinite(fahrenheit)) return "--°" + units.name();
        return Math.round(units.fromF(fahrenheit)) + "°" + units.name();
    }

    /** {@code Hi 74° Lo 55°}. */
    public static String highLow(Forecast.Day day, TemperatureUnit units) {
        return "Hi " + Math.round(units.fromF(day.highF())) + "° Lo " + Math.round(units.fromF(day.lowF())) + "°";
    }

    /** A chance as a whole percentage, {@code 40%}. */
    public static String percent(double chance) {
        return Math.round(chance * 100.0) + "%";
    }

    /**
     * The day's sky with its chance: {@code Rain 70%}, {@code Thunder 60%},
     * {@code Clear}, or {@code Clear, rain 30%} when a clear day may yet rain.
     */
    public static String sky(Forecast.Day day, boolean snow) {
        String wet = snow ? "snow" : "rain";
        return switch (day.sky()) {
            case THUNDER -> "Thunder " + percent(day.thunderChance());
            case RAIN, SNOW -> day.sky().displayName() + " " + percent(day.rainChance());
            case CLEAR -> day.rainChance() >= 0.2 ? "Clear, " + wet + " " + percent(day.rainChance()) : "Clear";
        };
    }

    /**
     * The screen's line for tomorrow, short: {@code Tmrw: Clear},
     * {@code Tmrw: Rain 40%} (a chance of it, even on a clear day) or
     * {@code Tmrw: Storm 60%}: "Thunder 100%" doesn't fit after it.
     */
    public static String tomorrow(Forecast.Day day, boolean snow) {
        String sky = day.sky() == Forecast.Sky.THUNDER ? "Storm " + percent(day.thunderChance())
                : day.sky() == Forecast.Sky.CLEAR && day.rainChance() < 0.2 ? "Clear"
                : (snow ? "Snow " : "Rain ") + percent(day.rainChance());
        return "Tmrw: " + sky;
    }

    /** {@code Today}, {@code Tomorrow}, then {@code Autumn, day 8}, or {@code In 3 days} without seasons. */
    public static String dayName(int ahead, String season, int dayOfSeason) {
        if (ahead == 0) return "Today";
        if (ahead == 1) return "Tomorrow";
        if (season == null) return "In " + ahead + " days";
        return season + ", day " + dayOfSeason;
    }

    /** How far away a station is, {@code 120 blocks away}. */
    public static String distance(double blocks) {
        return String.format(Locale.US, "%,d blocks away", Math.round(blocks));
    }
}
