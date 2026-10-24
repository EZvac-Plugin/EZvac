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

    public enum Season { SPRING, SUMMER, AUTUMN, WINTER }
}
