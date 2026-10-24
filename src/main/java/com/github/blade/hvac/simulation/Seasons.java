package com.github.blade.hvac.simulation;

import java.util.Locale;

/**
 * The yearly cycle (2.1 Pilot 2). Seasons only move the temperature
 * baseline up and down along a smooth curve: coldest in mid-winter, warmest
 * in mid-summer, average in mid-spring and mid-autumn. Biomes, rain and snow
 * stay as Minecraft makes them. Pure.
 */
public final class Seasons {
    private Seasons() {}

    public static final long TICKS_PER_DAY = 24_000L;

    /** Natural water follows the air's seasons at this share: a lake warms and cools less than the air. */
    public static final double WATER_SHARE = 0.8;

    public enum Season {
        SPRING, SUMMER, AUTUMN, WINTER;

        public String displayName() {
            String name = name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        public Season next() { return values()[(ordinal() + 1) % 4]; }

        /** {@code summer}, {@code Fall} and the like; null when it names no season. */
        public static Season parse(String text) {
            if (text == null) return null;
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "spring" -> SPRING;
                case "summer" -> SUMMER;
                case "autumn", "fall" -> AUTUMN;
                case "winter" -> WINTER;
                default -> null;
            };
        }
    }

    /** Ticks in one year of seasons that each last this many days. */
    public static long yearTicks(int daysPerSeason) {
        return 4L * daysPerSeason * TICKS_PER_DAY;
    }

    /**
     * Where in the seasons a point of the year is: +1 in mid-summer, -1 in
     * mid-winter, 0 in mid-spring and mid-autumn. The year starts on the
     * first day of spring.
     */
    public static double factor(long calendarTicks, int daysPerSeason) {
        long year = yearTicks(daysPerSeason);
        double position = Math.floorMod(calendarTicks, year) / (double) year;
        return Math.sin(2.0 * Math.PI * position - Math.PI / 4.0);
    }

    /** The same for natural water, which lags the air by {@code lagDays}. */
    public static double waterFactor(long calendarTicks, int daysPerSeason, double lagDays) {
        return factor(calendarTicks - Math.round(lagDays * TICKS_PER_DAY), daysPerSeason);
    }

    public static Season seasonAt(long calendarTicks, int daysPerSeason) {
        long season = (long) daysPerSeason * TICKS_PER_DAY;
        long intoYear = Math.floorMod(calendarTicks, yearTicks(daysPerSeason));
        return Season.values()[(int) Math.min(3, intoYear / season)];
    }

    /** The day of its season, from 1 to {@code daysPerSeason}. */
    public static int dayOfSeason(long calendarTicks, int daysPerSeason) {
        long season = (long) daysPerSeason * TICKS_PER_DAY;
        long intoSeason = Math.floorMod(calendarTicks, yearTicks(daysPerSeason)) % season;
        return (int) (intoSeason / TICKS_PER_DAY) + 1;
    }

    /** The calendar day (0 = first day of spring) of this day of this season, in the same year as {@code day}. */
    public static long dayOf(Season season, int dayOfSeason, long day, int daysPerSeason) {
        if (dayOfSeason < 1 || dayOfSeason > daysPerSeason)
            throw new IllegalArgumentException("day must be 1-" + daysPerSeason);
        long year = 4L * daysPerSeason;
        long yearStart = day - Math.floorMod(day, year);
        return yearStart + (long) season.ordinal() * daysPerSeason + dayOfSeason - 1;
    }

    /** The calendar day the next season starts on, a year on when that is spring. */
    public static long firstDayOfNext(long day, int daysPerSeason) {
        Season current = seasonAt(day * TICKS_PER_DAY, daysPerSeason);
        long start = dayOf(current.next(), 1, day, daysPerSeason);
        return current == Season.WINTER ? start + 4L * daysPerSeason : start;
    }
}
