package com.github.blade.hvac.model;

import com.github.blade.hvac.simulation.Seasons;

/**
 * One world's own calendar (2.1). It follows the world's days as they
 * pass, so sleeping through the night or {@code /time add} moves it on by
 * every day they pass (BETA 1.0 Pilot 3: before, one day at most per look at
 * the clock), but setting the clock back (vanilla {@code /time set day}
 * restarts the day count) never sends the year back to spring. A new one
 * starts on Minecraft's own day count (BETA 1.0 Pilot 3), so day 0 of a world
 * is the first day of spring; the time of day is always the world's own. Main
 * thread only.
 */
public final class SeasonCalendar {
    private long day;
    private long lastWorldDay;
    private boolean seen;
    private boolean paused;

    /** A new calendar: on the world's own day, from the next time it sees the world's clock. */
    public SeasonCalendar() {}

    /** A saved calendar. */
    public SeasonCalendar(long day, long lastWorldDay, boolean paused) {
        this.day = Math.max(0L, day);
        this.lastWorldDay = lastWorldDay;
        this.seen = true;
        this.paused = paused;
    }

    /** Days since the calendar began, 0 being the first day of spring. */
    public long day() { return day; }
    public long lastWorldDay() { return lastWorldDay; }
    public boolean paused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

    /** Jumps to a calendar day, as {@code /hvac season set} does. */
    public void setDay(long day) { this.day = Math.max(0L, day); }

    /**
     * Looks at the world's clock ({@code World#getFullTime}).
     *
     * @return true when the calendar's day changed
     */
    public boolean advance(long worldFullTime) {
        long worldDay = Math.floorDiv(worldFullTime, Seasons.TICKS_PER_DAY);
        if (!seen) {
            seen = true;
            lastWorldDay = worldDay;
            long start = Math.max(0L, worldDay);
            boolean moved = start != day;
            day = start;
            return moved;
        }
        long passed = worldDay - lastWorldDay;
        lastWorldDay = worldDay;
        if (paused || passed <= 0) return false;
        day += passed;
        return true;
    }

    /** The calendar position in ticks, the world's time of day included. */
    public long ticks(long worldFullTime) {
        return day * Seasons.TICKS_PER_DAY + Math.floorMod(worldFullTime, Seasons.TICKS_PER_DAY);
    }
}
