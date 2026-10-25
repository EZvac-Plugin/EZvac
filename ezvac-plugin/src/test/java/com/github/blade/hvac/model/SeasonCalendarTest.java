package com.github.blade.hvac.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** EZvac's own calendar counts the world's days but never goes back (2.1). */
class SeasonCalendarTest {
    private static final long DAY = 24_000L;

    @Test
    void itStartsOnTheFirstDayOfSpringWhereverTheWorldsClockIs() {
        SeasonCalendar calendar = new SeasonCalendar();
        assertFalse(calendar.advance(900 * DAY + 6_000));
        assertEquals(0, calendar.day());
        assertEquals(6_000, calendar.ticks(900 * DAY + 6_000), "with the world's own time of day");
    }

    @Test
    void daysPassingMoveItOn() {
        SeasonCalendar calendar = new SeasonCalendar();
        calendar.advance(10 * DAY + 12_000);
        assertFalse(calendar.advance(10 * DAY + 23_999));
        assertTrue(calendar.advance(11 * DAY));          // morning comes, or someone slept
        assertEquals(1, calendar.day());
        calendar.advance(12 * DAY + 500);                  // /time add 24000
        assertEquals(2, calendar.day());
    }

    @Test
    void settingTheClockBackNeverResetsTheYear() {
        SeasonCalendar calendar = new SeasonCalendar();
        calendar.advance(500 * DAY + 13_000);
        calendar.advance(501 * DAY);
        calendar.advance(1_000);                            // vanilla /time set day: day count 0
        assertEquals(1, calendar.day());
        calendar.advance(DAY + 10);                         // and then days pass as usual
        assertEquals(2, calendar.day());
    }

    @Test
    void aHugeJumpCountsOneDay() {
        SeasonCalendar calendar = new SeasonCalendar();
        calendar.advance(0);
        calendar.advance(1_000 * DAY);
        assertEquals(1, calendar.day());
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
        SeasonCalendar calendar = new SeasonCalendar(42, 700, false);
        calendar.advance(701 * DAY);
        assertEquals(43, calendar.day());
        calendar.setDay(-5);
        assertEquals(0, calendar.day());
    }
}
