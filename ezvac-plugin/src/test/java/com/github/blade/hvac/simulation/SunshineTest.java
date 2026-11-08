package com.github.blade.hvac.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Feels-like in the sun (BETA 1.0 Pilot 4). */
class SunshineTest {
    private static double feels(double sunWm2, double windMs) {
        return Psychrometrics.apparentTemperatureF(85.0, 50.0, windMs, sunWm2);
    }

    @Test
    void aSummerNoonFieldFeelsWarmerThanTheShadeBesideIt() {
        double field = Sunshine.absorbedWm2(6_000, 1.0, 0.0, 0.0, true, 15, 1.0);
        double forest = Sunshine.absorbedWm2(6_000, 1.0, 0.0, 0.0, false, 12, 1.0);
        double gap = feels(field, 1.0) - feels(forest, 1.0);
        assertTrue(gap > 5.0 && gap < 15.0, "about 10 F: " + gap);
        assertEquals(Psychrometrics.apparentTemperatureF(85.0, 50.0, 1.0), feels(0.0, 1.0), 1e-12,
                "without sun it is exactly the shade value");
    }

    @Test
    void nightIndoorsAndSwitchedOffAddNothing() {
        assertEquals(0.0, Sunshine.absorbedWm2(18_000, 1.0, 0.0, 0.0, true, 15, 1.0), "night");
        assertEquals(0.0, Sunshine.absorbedWm2(12_000, 1.0, 0.0, 0.0, true, 15, 1.0), "sunset");
        assertEquals(0.0, Sunshine.absorbedWm2(6_000, 1.0, 0.0, 0.0, false, 0, 1.0), "no sky at all");
        assertEquals(0.0, Sunshine.absorbedWm2(6_000, 1.0, 0.0, 0.0, true, 15, 0.0), "sun-strength 0");
    }

    @Test
    void windRainWinterAndMorningShrinkIt() {
        double noon = Sunshine.absorbedWm2(6_000, 1.0, 0.0, 0.0, true, 15, 1.0);
        assertEquals(Sunshine.CLEAR_NOON_WM2, noon, 1e-9);
        assertTrue(feels(noon, 8.0) - feels(0.0, 8.0) < feels(noon, 1.0) - feels(0.0, 1.0), "a breeze carries it away");
        assertTrue(Sunshine.absorbedWm2(6_000, 1.0, 1.0, 0.0, true, 15, 1.0) < noon * 0.35, "rain clouds");
        assertTrue(Sunshine.absorbedWm2(6_000, -1.0, 0.0, 0.0, true, 15, 1.0) < noon * 0.7, "winter's lower sun");
        assertTrue(Sunshine.absorbedWm2(1_000, 1.0, 0.0, 0.0, true, 15, 1.0) < noon * 0.3, "early morning");
        assertEquals(2.0 * noon, Sunshine.absorbedWm2(6_000, 1.0, 0.0, 0.0, true, 15, 2.0), 1e-9);
    }
}
