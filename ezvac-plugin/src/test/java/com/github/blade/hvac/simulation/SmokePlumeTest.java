package com.github.blade.hvac.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Smoke from fires out in the open (BETA 1.0 Pilot 4). */
class SmokePlumeTest {
    private static final double WEST = 270.0;   // from the west: blowing east, toward +x

    @Test
    void itStretchesDownwindAndBarelyUpwind() {
        double beside = SmokePlume.share(0, 0, 0, 4.0, WEST, 96);
        double down = SmokePlume.share(30, 0, 0, 4.0, WEST, 96);
        double up = SmokePlume.share(-30, 0, 0, 4.0, WEST, 96);
        double across = SmokePlume.share(0, 0, 30, 4.0, WEST, 96);
        assertTrue(beside > down && down > 0.05, "thickest beside it, still there 30 blocks downwind: " + down);
        assertTrue(up < down / 20, "upwind there is next to none: " + up);
        assertTrue(across < down / 20, "nor off to the side: " + across);
        assertTrue(SmokePlume.share(0, 0, 30, 4.0, 0.0, 96) > 0.05, "a north wind carries it south, toward +z");
    }

    @Test
    void itWidensAndThinsWithDistanceAndWind() {
        assertTrue(SmokePlume.share(60, 0, 0, 4.0, WEST, 96) < SmokePlume.share(20, 0, 0, 4.0, WEST, 96));
        assertTrue(SmokePlume.share(60, 0, 12, 4.0, WEST, 96) > SmokePlume.share(20, 0, 12, 4.0, WEST, 96) * 0.3,
                "wider further out");
        assertTrue(SmokePlume.share(30, 0, 0, 14.0, WEST, 96) < SmokePlume.share(30, 0, 0, 4.0, WEST, 96) * 0.5,
                "a gale thins it");
    }

    @Test
    void aCalmDayLeavesItPooledRoundTheSource() {
        double east = SmokePlume.share(20, 0, 0, 0.3, WEST, 96);
        double west = SmokePlume.share(-20, 0, 0, 0.3, WEST, 96);
        assertEquals(east, west, 1e-12, "the same every way");
        assertTrue(east > 0.2, "and thick: " + east);
        assertEquals(east, SmokePlume.share(0, 0, -20, 0.0, Double.NaN, 96), 0.05, "no direction: pooled too");
    }

    @Test
    void itStopsAtItsReachAndFadesWithHeight() {
        assertEquals(0.0, SmokePlume.share(96, 0, 0, 4.0, WEST, 96));
        assertEquals(0.0, SmokePlume.share(70, 0, 70, 0.0, Double.NaN, 96), "past 96 blocks out");
        assertTrue(SmokePlume.share(95, 0, 0, 4.0, WEST, 96) < 0.005, "tapering to nothing");
        assertTrue(SmokePlume.share(10, 60, 0, 4.0, WEST, 96) < 0.01, "deep below or high above it, none");
        assertEquals(SmokePlume.share(10, 0, 0, 4.0, WEST, 96), SmokePlume.share(10, Double.NaN, 0, 4.0, WEST, 96),
                1e-12, "a map ignores heights");
    }

    @Test
    void aRowOfBlastFurnacesShowsDownwind() {
        // Ten blast furnaces (10 ug/m3 fine each), 20 blocks upwind in a steady 4 m/s breeze.
        double fine = 0.0;
        for (int i = 0; i < 10; i++) fine += 10.0 * SmokePlume.STRENGTH * SmokePlume.share(20, 0, i - 4.5, 4.0, WEST, 96);
        assertTrue(fine > 35.0 && fine < 150.0, "unhealthy for sensitive groups, or worse: " + fine);
    }
}
