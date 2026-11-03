package com.github.blade.hvac.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EZvac's own calendar follows the world's days but never goes back (2.1);
 * since BETA 1.0 Pilot 3 it starts on Minecraft's own day and counts every day
 * a jump passes.
 */
class SeasonCalendarTest {
    private static final long DAY = 24_000L;

    @Test
    void aNewCalendarStartsOnTheWorldsOwnDay() {
        SeasonCalendar calendar = new SeasonCalendar();
        assertTrue(calendar.advance(900 * DAY + 6_000), "a new calendar has its day to save");
        assertEquals(900, calendar.day());
        assertEquals(900 * DAY + 6_000, calendar.ticks(900 * DAY + 6_000), "with the world's own time of day");
        SeasonCalendar fresh = new SeasonCalendar();
        assertFalse(fresh.advance(6_000), "a brand-new world: the first day of spring");
        assertEquals(0, fresh.day());
    }

    @Test
    void daysPassingMoveItOn() {
        SeasonCalendar calendar = new SeasonCalendar();
        calendar.advance(10 * DAY + 12_000);
        assertFalse(calendar.advance(10 * DAY + 23_999));
        assertTrue(calendar.advance(11 * DAY));          // morning comes, or someone slept
        assertEquals(11, calendar.day());
        calendar.advance(12 * DAY + 500);                  // /time add 24000
        assertEquals(12, calendar.day());
    }

    @Test
    void settingTheClockBackNeverResetsTheYear() {
        SeasonCalendar calendar = new SeasonCalendar();
        calendar.advance(500 * DAY + 13_000);
        calendar.advance(501 * DAY);
        calendar.advance(1_000);                            // vanilla /time set day: day count 0
        assertEquals(501, calendar.day());
        calendar.advance(DAY + 10);                         // and then days pass as usual
        assertEquals(502, calendar.day());
    }

    @Test
    void aJumpOfManyDaysCountsThemAll() {
        // Before Pilot 3 only one day counted, so /time add of ten days moved the seasons one day on.
        SeasonCalendar calendar = new SeasonCalendar();
        calendar.advance(0);
        calendar.advance(10 * DAY);
        assertEquals(10, calendar.day());
    }

    @Test
    void pausedTheDaysPassButTheSeasonStays() {
        SeasonCalendar calendar = new SeasonCalendar(30, 100, true);
        assertFalse(calendar.advance(103 * DAY));
        assertEquals(30, calendar.day());
        calendar.setPaused(false);
        calendar.advance(104 * DAY);
        assertEquals(31, calendar.day(), "only days after resuming count");
    }

    @Test
    void aSavedCalendarCarriesOnFromWhereItWas() {
        // So upgrading keeps every world on its season and day.
        SeasonCalendar calendar = new SeasonCalendar(42, 700, false);
        calendar.advance(701 * DAY);
        assertEquals(43, calendar.day());
        calendar.advance(705 * DAY);
        assertEquals(47, calendar.day());
        calendar.setDay(-5);
        assertEquals(0, calendar.day());
    }
}
