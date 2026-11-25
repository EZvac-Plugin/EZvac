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
        return com.github.blade.hvac.lang.Lang.text("station.high-low", com.github.blade.hvac.lang.Lang.arg("high", Math.round(units.fromF(day.highF()))), com.github.blade.hvac.lang.Lang.arg("low", Math.round(units.fromF(day.lowF()))));
    }

    /** A chance as a whole percentage, {@code 40%}. */
    public static String percent(double chance) {
        return Math.round(chance * 100.0) + "%";
    }

    /**
     * The day's sky with its chance: {@code Rain 70%}, {@code Thunder 60%},
     * {@code Clear}, or {@code Clear, rain 30%} when a clear day may yet rain.
     */
    private static final java.util.regex.Pattern NOTE = java.util.regex.Pattern.compile(
            "^(?:Clear all day|(Rain|Snow) all day|(Rain|Snow) (until|from) ([0-9]{2}:00))(.*)$", java.util.regex.Pattern.DOTALL);

    /**
     * A forecast note as players read it (BETA 1.1). Notes stay English
     * inside, as addons see them; an addon's planned weather after EZvac's
     * own part stays as the addon wrote it.
     */
    public static String note(String note) {
        java.util.regex.Matcher match = NOTE.matcher(note);
        if (!match.matches()) return note;
        String own;
        if (match.group(1) != null)
            own = com.github.blade.hvac.lang.Lang.text(com.github.blade.hvac.lang.Lang.key("station.note", match.group(1) + "-all-day"));
        else if (match.group(2) != null)
            own = com.github.blade.hvac.lang.Lang.text(com.github.blade.hvac.lang.Lang.key("station.note", match.group(2) + "-" + match.group(3)),
                    com.github.blade.hvac.lang.Lang.arg("time", match.group(4)));
        else own = com.github.blade.hvac.lang.Lang.text("station.note.clear-all-day");
        return own + match.group(5);
    }

    public static String sky(Forecast.Day day, boolean snow) {
        return switch (day.sky()) {
            case THUNDER -> com.github.blade.hvac.lang.Lang.text("station.sky-chance", com.github.blade.hvac.lang.Lang.arg("sky", day.sky().displayName()), com.github.blade.hvac.lang.Lang.arg("chance", percent(day.thunderChance())));
            case RAIN, SNOW -> com.github.blade.hvac.lang.Lang.text("station.sky-chance", com.github.blade.hvac.lang.Lang.arg("sky", day.sky().displayName()), com.github.blade.hvac.lang.Lang.arg("chance", percent(day.rainChance())));
            case CLEAR -> day.rainChance() >= 0.2 ? com.github.blade.hvac.lang.Lang.text(snow ? "station.clear-snow" : "station.clear-rain",
                    com.github.blade.hvac.lang.Lang.arg("chance", percent(day.rainChance()))) : com.github.blade.hvac.lang.Lang.text("weather.sky.clear");
        };
    }

    /**
     * The screen's line for tomorrow, short: {@code Tmrw: Clear},
     * {@code Tmrw: Rain 40%} (a chance of it, even on a clear day) or
     * {@code Tmrw: Storm 60%}: "Thunder 100%" doesn't fit after it.
     */
    public static String tomorrow(Forecast.Day day, boolean snow) {
        String sky = day.sky() == Forecast.Sky.THUNDER ? com.github.blade.hvac.lang.Lang.text("station.storm", com.github.blade.hvac.lang.Lang.arg("chance", percent(day.thunderChance())))
                : day.sky() == Forecast.Sky.CLEAR && day.rainChance() < 0.2 ? com.github.blade.hvac.lang.Lang.text("weather.sky.clear")
                : com.github.blade.hvac.lang.Lang.text("station.sky-chance", com.github.blade.hvac.lang.Lang.arg("sky", com.github.blade.hvac.lang.Lang.text(snow ? "weather.sky.snow" : "weather.sky.rain")),
                com.github.blade.hvac.lang.Lang.arg("chance", percent(day.rainChance())));
        return com.github.blade.hvac.lang.Lang.text("station.tomorrow", com.github.blade.hvac.lang.Lang.arg("sky", sky));
    }

    /** {@code Today}, {@code Tomorrow}, then {@code Autumn, day 8}, or {@code In 3 days} without seasons. */
    public static String dayName(int ahead, String season, int dayOfSeason) {
        if (ahead == 0) return com.github.blade.hvac.lang.Lang.text("station.today");
        if (ahead == 1) return com.github.blade.hvac.lang.Lang.text("station.tomorrow-name");
        if (season == null) return com.github.blade.hvac.lang.Lang.text("station.in-days", com.github.blade.hvac.lang.Lang.arg("days", ahead));
        return com.github.blade.hvac.lang.Lang.text("station.season-day", com.github.blade.hvac.lang.Lang.arg("season", season), com.github.blade.hvac.lang.Lang.arg("day", dayOfSeason));
    }

    /** How far away a station is, {@code 120 blocks away}. */
    public static String distance(double blocks) {
        return com.github.blade.hvac.lang.Lang.text("station.away", com.github.blade.hvac.lang.Lang.arg("blocks", String.format(Locale.US, "%,d", Math.round(blocks))));
    }
}
