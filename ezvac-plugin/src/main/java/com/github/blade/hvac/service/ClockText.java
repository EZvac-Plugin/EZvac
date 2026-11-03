package com.github.blade.hvac.service;

import java.util.Locale;

/**
 * Minecraft's time of day as a clock (BETA 1.0 Pilot 3): {@code 14:30}, 24
 * hours, sunrise (tick 0) being 06:00, as EZvac's forecasts count it. Pure.
 */
public final class ClockText {
    private ClockText() {}

    /** Ticks in a Minecraft day... */
    static final long DAY = 24_000L;
    /** ...and in one of its hours. */
    static final double HOUR = 1_000.0;

    /** {@code 06:00} at sunrise (tick 0), {@code 18:00} at sunset (12000), {@code 00:00} at midnight. */
    public static String clock(long dayTime) {
        long ticks = Math.floorMod(dayTime + 6_000L, DAY);
        long hour = (long) (ticks / HOUR);
        long minute = (long) ((ticks - hour * HOUR) * 60.0 / HOUR);
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }
}
