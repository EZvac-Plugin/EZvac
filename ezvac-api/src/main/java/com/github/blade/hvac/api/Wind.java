package com.github.blade.hvac.api;

import java.util.Objects;

/**
 * The wind at a spot (EZvac 3.2). Each day of a world has a level, drawn from
 * the world seed and the day, and the wind gusts around that day's speed.
 * Terrain changes it: high ground and the sea are windier, forests and walls
 * shelter a spot, and there is none underground, indoors or outside the
 * overworld.
 *
 * @param speedMs how fast it blows, m/s
 * @param level   how the speed reads: Calm, Breezy, Windy or Gale
 * @since 1.5
 */
public record Wind(double speedMs, Level level) {
    public static final Wind CALM = new Wind(0.0, Level.CALM);

    public Wind {
        Objects.requireNonNull(level, "level");
        speedMs = Double.isFinite(speedMs) ? Math.max(0.0, speedMs) : 0.0;
    }

    public enum Level { CALM, BREEZY, WINDY, GALE }

    public double kmh() { return speedMs * 3.6; }

    public double mph() { return speedMs * 2.236_936; }

    /**
     * One day's wind in a world, on open, level ground.
     *
     * @param day         the day it was drawn for: the world's season calendar day while seasons run,
     *                    else the world's full time / 24000
     * @param level       the day's level
     * @param meanSpeedMs the day's mean speed, which it gusts around, m/s
     */
    public record Day(long day, Level level, double meanSpeedMs) {
        public Day {
            Objects.requireNonNull(level, "level");
        }
    }
}
