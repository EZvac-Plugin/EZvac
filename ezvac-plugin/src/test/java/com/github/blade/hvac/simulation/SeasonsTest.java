package com.github.blade.hvac.simulation;

import org.junit.jupiter.api.Test;

import static com.github.blade.hvac.simulation.Seasons.Season.*;
import static org.junit.jupiter.api.Assertions.*;

/** The yearly curve and the calendar maths (2.1 Pilot 2). */
class SeasonsTest {
    private static final int DAYS = 15;
    private static final long DAY = Seasons.TICKS_PER_DAY;
    private static final long SEASON = DAYS * DAY;

    @Test
    void warmestInMidSummerColdestInMidWinter() {
        assertEquals(0.0, Seasons.factor(SEASON / 2, DAYS), 1e-9, "mid-spring is average");
        assertEquals(1.0, Seasons.factor(SEASON + SEASON / 2, DAYS), 1e-9);
        assertEquals(0.0, Seasons.factor(2 * SEASON + SEASON / 2, DAYS), 1e-9, "mid-autumn is average");
        assertEquals(-1.0, Seasons.factor(3 * SEASON + SEASON / 2, DAYS), 1e-9);
        assertEquals(Seasons.factor(SEASON, DAYS), Seasons.factor(SEASON + Seasons.yearTicks(DAYS), DAYS), 1e-9,
                "every year is the same");
    }

    @Test
    void theCurveHasNoSteps() {
        double previous = Seasons.factor(0, DAYS);
        for (long ticks = DAY / 10; ticks < Seasons.yearTicks(DAYS); ticks += DAY / 10) {
            double now = Seasons.factor(ticks, DAYS);
            // A sine never changes faster than its full slope: 2 pi per year.
            assertTrue(Math.abs(now - previous) <= 2 * Math.PI * (DAY / 10.0) / Seasons.yearTicks(DAYS) + 1e-12,
                    "at " + ticks);
            previous = now;
        }
    }

    @Test
    void waterLagsTheAir() {
        long midSummer = SEASON + SEASON / 2;
        assertTrue(Seasons.waterFactor(midSummer, DAYS, 5) < 1.0);
        assertEquals(1.0, Seasons.waterFactor(midSummer + 5 * DAY, DAYS, 5), 1e-9, "lakes are warmest later");
        assertEquals(Seasons.factor(midSummer, DAYS), Seasons.waterFactor(midSummer, DAYS, 0), 1e-12);
    }

    @Test
    void seasonsAndDays() {
        assertEquals(SPRING, Seasons.seasonAt(0, DAYS));
        assertEquals(1, Seasons.dayOfSeason(0, DAYS));
        assertEquals(1, Seasons.dayOfSeason(DAY - 1, DAYS), "a day runs morning to morning");
        assertEquals(SPRING, Seasons.seasonAt(SEASON - 1, DAYS));
        assertEquals(15, Seasons.dayOfSeason(SEASON - 1, DAYS));
        assertEquals(SUMMER, Seasons.seasonAt(SEASON, DAYS));
        assertEquals(WINTER, Seasons.seasonAt(4 * SEASON - 1, DAYS));
        assertEquals(SPRING, Seasons.seasonAt(4 * SEASON, DAYS), "a new year");
    }

    @Test
    void jumpingToASeason() {
        long day = 70;   // year 2, day 11 of spring
        assertEquals(SPRING, Seasons.seasonAt(day * DAY, DAYS));
        long summer8 = Seasons.dayOf(SUMMER, 8, day, DAYS);
        assertEquals(SUMMER, Seasons.seasonAt(summer8 * DAY, DAYS));
        assertEquals(8, Seasons.dayOfSeason(summer8 * DAY, DAYS));
        assertEquals(60 + 15 + 7, summer8, "stays in the same year");
        assertThrows(IllegalArgumentException.class, () -> Seasons.dayOf(SUMMER, 16, day, DAYS));
        assertEquals(75, Seasons.firstDayOfNext(day, DAYS));
        assertEquals(120, Seasons.firstDayOfNext(Seasons.dayOf(WINTER, 3, day, DAYS), DAYS), "winter turns to spring");
    }

    @Test
    void seasonNames() {
        assertEquals(AUTUMN, Seasons.Season.parse("Fall"));
        assertEquals(WINTER, Seasons.Season.parse(" winter "));
        assertNull(Seasons.Season.parse("monsoon"));
        assertEquals("Summer", SUMMER.displayName());
        assertEquals(SPRING, WINTER.next());
    }
}
