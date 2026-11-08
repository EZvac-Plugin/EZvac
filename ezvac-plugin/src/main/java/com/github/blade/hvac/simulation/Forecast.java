package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.WeatherStation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A weather station's seven-day forecast (BETA 1.0), made honestly from what
 * EZvac knows. Temperatures follow the area's climate and the season
 * calendar, and the wind is drawn from the world's seed, so those are exact.
 * Rain and thunder are not: Minecraft only knows when the current spell ends,
 * so the forecast plays its weather clocks forward many times and gives a
 * chance for each day.
 */
public final class Forecast {
    public static final int DAYS = 7;
    /** How many times the weather is played forward. */
    static final int RUNS = 400;
    /** Rain shorter than this in a day (a minute) doesn't count as a rainy day. */
    static final int MINIMUM_TICKS = 1_200;
    /** An area this dry (desert, badlands) never gets rain or snow. */
    static final double DRY = 0.1;

    // Minecraft's weather clocks, in ticks: how long a spell of each kind lasts.
    static final int CLEAR_MIN = 12_000, CLEAR_MAX = 180_000;
    static final int RAIN_MIN = 12_000, RAIN_MAX = 24_000;
    static final int CALM_MIN = 12_000, CALM_MAX = 180_000;
    static final int THUNDER_MIN = 3_600, THUNDER_MAX = 15_600;

    public enum Sky {
        CLEAR("Clear"), RAIN("Rain"), SNOW("Snow"), THUNDER("Thunder");

        private final String displayName;
        Sky(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }

    /**
     * The world's weather clocks right now, as Minecraft keeps them.
     *
     * @param rainTicks    until rain starts or stops; 0 when it hasn't picked yet
     * @param thunderTicks the same for thunder
     * @param clearTicks   a /weather clear still holding, or 0
     * @param dayTime      the time of day, 0 at sunrise
     */
    public record Clocks(boolean raining, int rainTicks, boolean thundering, int thunderTicks, int clearTicks,
                         long dayTime) {}

    /** Each day's chance (0..1) of rain or snow, and of thunder. */
    public record Chances(double[] rain, double[] thunder) {}

    /**
     * One day of the forecast. {@code note} is what is known for certain about
     * the day, such as {@code Rain from 15:00} (to the nearest hour), or empty.
     */
    public record Day(int ahead, double highF, double lowF, double rainChance, double thunderChance, Sky sky,
                      WindModel.Day wind, double pollen, String note, int aqi) {
        /** As in BETA 1.0, before the day had an AQI. */
        public Day(int ahead, double highF, double lowF, double rainChance, double thunderChance, Sky sky,
                   WindModel.Day wind, double pollen, String note) {
            this(ahead, highF, lowF, rainChance, thunderChance, sky, wind, pollen, note, -1);
        }

        /** The day's wind on open ground, m/s; 0 with wind switched off. */
        public double windMs() { return wind == null ? 0.0 : wind.meanMs(); }

        /** The same day with another note (BETA 1.0 Pilot 4). */
        public Day withNote(String note) {
            return new Day(ahead, highF, lowF, rainChance, thunderChance, sky, wind, pollen, note, aqi);
        }

        /** The same day with its AQI (BETA 1.0 Pilot 3); -1 with air quality switched off. */
        public Day withAqi(int aqi) {
            return new Day(ahead, highF, lowF, rainChance, thunderChance, sky, wind, pollen, note, aqi);
        }
    }

    private Forecast() {}

    /** Ticks left of today: days change at sunrise, as Minecraft counts them. */
    static int leftToday(long dayTime) {
        return (int) (24_000L - Math.floorMod(dayTime, 24_000L));
    }

    /**
     * Plays the weather clocks forward {@link #RUNS} times from where they are
     * now. The seed keeps the same clocks giving the same chances, so a
     * forecast doesn't change each time it's looked at.
     */
    public static Chances chances(Clocks clocks, long seed) {
        int today = leftToday(clocks.dayTime());
        int horizon = today + (DAYS - 1) * 24_000;
        double[] rain = new double[DAYS], thunder = new double[DAYS];
        Random random = new Random(seed);
        for (int run = 0; run < RUNS; run++) {
            List<int[]> wet = spells(clocks.raining(), clocks.rainTicks(), clocks.clearTicks(), RAIN_MIN, RAIN_MAX,
                    CLEAR_MIN, CLEAR_MAX, horizon, random);
            List<int[]> storm = spells(clocks.thundering(), clocks.thunderTicks(), clocks.clearTicks(), THUNDER_MIN,
                    THUNDER_MAX, CALM_MIN, CALM_MAX, horizon, random);
            for (int day = 0; day < DAYS; day++) {
                int from = day == 0 ? 0 : today + (day - 1) * 24_000, to = day == 0 ? today : from + 24_000;
                if (overlap(wet, from, to) >= MINIMUM_TICKS) rain[day]++;
                if (overlap(both(wet, storm), from, to) >= MINIMUM_TICKS) thunder[day]++;
            }
        }
        for (int day = 0; day < DAYS; day++) {
            rain[day] /= RUNS;
            thunder[day] /= RUNS;
        }
        return new Chances(rain, thunder);
    }

    /**
     * The spells one clock is "on" for, from now to the horizon. A clock at 0
     * hasn't picked yet, as in a new world, so it picks now; a /weather clear
     * holds everything off until it ends, after which the clock picks afresh.
     */
    static List<int[]> spells(boolean on, int ticks, int clearTicks, int onMin, int onMax, int offMin, int offMax,
                              int horizon, Random random) {
        List<int[]> spells = new ArrayList<>();
        int time = 0;
        if (clearTicks > 0) {
            on = false;
            time = clearTicks;
            ticks = between(offMin, offMax, random);
        } else if (ticks <= 0) {
            ticks = on ? between(onMin, onMax, random) : between(offMin, offMax, random);
        }
        while (time < horizon) {
            int end = (int) Math.min((long) time + ticks, horizon);
            if (on) spells.add(new int[]{time, end});
            time = end;
            on = !on;
            ticks = on ? between(onMin, onMax, random) : between(offMin, offMax, random);
        }
        return spells;
    }

    private static int between(int min, int max, Random random) {
        return min + random.nextInt(max - min + 1);
    }

    /** How many ticks of these spells fall between from and to. */
    static int overlap(List<int[]> spells, int from, int to) {
        int total = 0;
        for (int[] spell : spells) total += Math.max(0, Math.min(spell[1], to) - Math.max(spell[0], from));
        return total;
    }

    /** Where both kinds of spell overlap: thunder only storms while it rains. */
    static List<int[]> both(List<int[]> a, List<int[]> b) {
        List<int[]> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            int from = Math.max(a.get(i)[0], b.get(j)[0]), to = Math.min(a.get(i)[1], b.get(j)[1]);
            if (from < to) out.add(new int[]{from, to});
            if (a.get(i)[1] < b.get(j)[1]) i++;
            else j++;
        }
        return out;
    }

    /**
     * What is known for certain about today or tomorrow: when the current
     * spell of rain (or snow) starts or ends, if that falls on the day.
     * Empty for later days, a dry area, or when nothing is known.
     */
    public static String note(Clocks clocks, int ahead, boolean snow, boolean dry) {
        if (dry || ahead > 1) return "";
        String word = snow ? "Snow" : "Rain";
        int today = leftToday(clocks.dayTime());
        int from = ahead == 0 ? 0 : today, to = ahead == 0 ? today : today + 24_000;
        if (clocks.clearTicks() > 0)
            return clocks.clearTicks() >= to ? (ahead == 0 ? "Clear all day" : "") : "";
        if (clocks.rainTicks() <= 0) return "";
        int at = clocks.rainTicks();
        if (at < from || at >= to) return clocks.raining() && at >= to && ahead == 0 ? word + " all day" : "";
        return (clocks.raining() ? word + " until " : word + " from ") + clock(clocks.dayTime() + at);
    }

    /** A time of day as a clock: sunrise (0) is 06:00, rounded to the hour. */
    static String clock(long dayTime) {
        long hour = (Math.floorMod(dayTime, 24_000L) + 6_000L + 500L) / 1_000L % 24L;
        return String.format(java.util.Locale.US, "%02d:00", hour);
    }

    /**
     * One day's weather for an area: its sky by the chances, and its high and
     * low with the season's shift and the expected cooling of rain on it.
     */
    public static Day day(int ahead, WeatherStation.Area area, double seasonShiftF, double rainChance,
                          double thunderChance, WindModel.Day wind, double pollen, String note,
                          ClimateSettings.Weather weather) {
        // A dry area's rain follows weather.dry-areas (BETA 1.0 Pilot 4): none, only thunderstorms', or all.
        if (dry(area)) {
            switch (weather.dryAreas()) {
                case NEVER -> { rainChance = 0.0; thunderChance = 0.0; }
                case THUNDER -> rainChance = thunderChance;
                case ALWAYS -> { }
            }
        }
        BiomeBlend.Blended blended = new BiomeBlend.Blended(area.noonF() + seasonShiftF,
                area.midnightF() + seasonShiftF, area.downfall(), area.rainShare(), area.snowShare(),
                area.seasonalF(), null, area.windExposure());
        WeatherModel.Effect effect = WeatherModel.effect(blended,
                new WeatherModel.Conditions(rainChance, thunderChance, rainChance >= 0.5, thunderChance >= 0.5),
                weather, wind == null ? weather.windClearMs() : wind.meanMs());
        double highF = effect.noonF(blended.noonF(), blended.midnightF());
        double lowF = effect.midnightF(blended.noonF(), blended.midnightF());
        // Snow or rain by the day's own air (BETA 1.0 Pilot 4; before, by the area's biomes).
        Sky sky = rainChance >= 0.5 && snows(highF, lowF, weather) ? Sky.SNOW
                : thunderChance >= 0.5 ? Sky.THUNDER
                : rainChance >= 0.5 ? Sky.RAIN
                : Sky.CLEAR;
        return new Day(ahead, highF, lowF, rainChance, thunderChance, sky, wind, pollen, note);
    }

    /** An area vanilla keeps dry: deserts, badlands, savannas. */
    public static boolean dry(WeatherStation.Area area) {
        return area.rainShare() + area.snowShare() < DRY;
    }

    /** A day whose rain comes down as snow: its air, between its high and its low, at or below the snow line. */
    public static boolean snows(double highF, double lowF, ClimateSettings.Weather weather) {
        return (highF + lowF) / 2.0 <= weather.snowAtOrBelowF();
    }
}
