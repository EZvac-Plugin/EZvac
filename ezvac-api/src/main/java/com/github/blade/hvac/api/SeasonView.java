package com.github.blade.hvac.api;

import java.util.Objects;

/**
 * Where a world is in EZvac's calendar.
 *
 * @param season        the season
 * @param dayOfSeason   its day, from 1 to {@code daysPerSeason}
 * @param daysPerSeason how long each season is, in Minecraft days
 * @param paused        true while an admin has paused the calendar
 * @since 1.0
 */
public record SeasonView(Season season, int dayOfSeason, int daysPerSeason, boolean paused) {
    public SeasonView {
        Objects.requireNonNull(season, "season");
    }

    /**
     * How far through the year the world is, from 0 at the start of spring
     * toward 1 at the end of winter, at the middle of today: for an addon
     * that changes gradually through the year, such as leaf colours.
     *
     * @since 1.9
     */
    public double progress() {
        int days = Math.max(1, daysPerSeason);
        int day = Math.max(1, Math.min(days, dayOfSeason));
        return (season.ordinal() * days + day - 0.5) / (4.0 * days);
    }

    public enum Season { SPRING, SUMMER, AUTUMN, WINTER }
}
