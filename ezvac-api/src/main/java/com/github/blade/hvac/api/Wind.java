package com.github.blade.hvac.api;

import java.util.Objects;

/**
 * The wind at a spot (EZvac 3.2). Each day of a world has a level, drawn from
 * the world seed and the day, and the wind gusts around that day's speed.
 * Terrain changes it: high ground and the sea are windier, forests and walls
 * shelter a spot, and there is none underground, indoors or outside the
 * overworld.
 *
 * <p>Since 1.8 (EZvac BETA 1.0 Pilot 3) it blows from a direction, and differs
 * from region to region: hot climates lie in the trade winds (from the
 * east-northeast), temperate ones in the westerlies (from the
 * west-southwest), cold ones in the polar easterlies (from the northeast), and
 * rain passing through turns the westerlies.
 *
 * @param speedMs     how fast it blows, m/s
 * @param level       how the speed reads: Calm, Breezy, Windy or Gale
 * @param fromDegrees where it blows from, in degrees: 0 north (Minecraft's -Z), 90 east, 180 south, 270 west;
 *                    NaN where it isn't known: no wind, or a view made as in 1.5 (1.8)
 * @since 1.5
 */
public record Wind(double speedMs, Level level, double fromDegrees) {
    public static final Wind CALM = new Wind(0.0, Level.CALM);

    public Wind {
        Objects.requireNonNull(level, "level");
        speedMs = Double.isFinite(speedMs) ? Math.max(0.0, speedMs) : 0.0;
        fromDegrees = normalize(fromDegrees);
    }

    /** The wind as in API 1.5, with no direction to it. */
    public Wind(double speedMs, Level level) {
        this(speedMs, level, Double.NaN);
    }

    public enum Level { CALM, BREEZY, WINDY, GALE }

    public double kmh() { return speedMs * 3.6; }

    public double mph() { return speedMs * 2.236_936; }

    /** Whether its direction is known (1.8). */
    public boolean hasDirection() { return Double.isFinite(fromDegrees); }

    /** Where it blows to, the way an arrow on a map points: opposite where it comes from; NaN when unknown (1.8). */
    public double towardDegrees() { return hasDirection() ? normalize(fromDegrees + 180.0) : Double.NaN; }

    /** Where it blows from as one of eight compass points, {@code N} .. {@code NW}; empty when unknown (1.8). */
    public String compass() { return compass(fromDegrees); }

    /** Degrees as one of eight compass points, {@code N} .. {@code NW}; empty for NaN (1.8). */
    public static String compass(double degrees) {
        if (!Double.isFinite(degrees)) return "";
        String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return points[(int) Math.floorMod(Math.round(normalize(degrees) / 45.0), 8L)];
    }

    /** Degrees in 0 .. 360; NaN stays NaN. */
    static double normalize(double degrees) {
        if (!Double.isFinite(degrees)) return Double.NaN;
        double d = degrees % 360.0;
        return d < 0.0 ? d + 360.0 : d;
    }

    /**
     * One day's wind in a world, on open, level ground.
     *
     * @param day         the day it was drawn for: the world's season calendar day while seasons run,
     *                    else the world's full time / 24000
     * @param level       the day's level
     * @param meanSpeedMs the day's mean speed, which it gusts around, m/s
     * @param fromDegrees where it blows from that day, as {@link Wind#fromDegrees()}: for a world, as it
     *                    blows in the westerlies; for a region or a station, as it blows there (1.8)
     */
    public record Day(long day, Level level, double meanSpeedMs, double fromDegrees) {
        public Day {
            Objects.requireNonNull(level, "level");
            fromDegrees = normalize(fromDegrees);
        }

        /** A day as in API 1.5, with no direction to it. */
        public Day(long day, Level level, double meanSpeedMs) {
            this(day, level, meanSpeedMs, Double.NaN);
        }

        /** Where it blows from as one of eight compass points; empty when unknown (1.8). */
        public String compass() { return Wind.compass(fromDegrees); }
    }
}
