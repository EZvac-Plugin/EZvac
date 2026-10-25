package com.github.blade.hvac.model;

import com.github.blade.hvac.simulation.Seasons;

/**
 * One world's own calendar (2.1). It counts the world's days as they
 * pass, so sleeping through the night or {@code /time add} moves it on, but
 * setting the clock back (vanilla {@code /time set day} restarts the day count)
 * never sends the year back to spring. Its days start with the world's, and
 * the time of day is always the world's own. Main thread only.
 */
public final class SeasonCalendar {
    /** At most this many days count from one look at the clock to the next, so a stray huge /time set can't skip a season. */
    static final long MAXIMUM_DAYS_PER_STEP = 1L;

    private long day;
    private long lastWorldDay;
    private boolean seen;
    private boolean paused;

    /** A new calendar: the first day of spring, from the next time it sees the world's clock. */
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
            return false;
        }
        long passed = worldDay - lastWorldDay;
        lastWorldDay = worldDay;
        if (paused || passed <= 0) return false;
        day += Math.min(passed, MAXIMUM_DAYS_PER_STEP);
        return true;
    }

    /** The calendar position in ticks, the world's time of day included. */
    public long ticks(long worldFullTime) {
        return day * Seasons.TICKS_PER_DAY + Math.floorMod(worldFullTime, Seasons.TICKS_PER_DAY);
    }
}
