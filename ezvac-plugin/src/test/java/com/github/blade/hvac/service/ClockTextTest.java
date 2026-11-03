package com.github.blade.hvac.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The time of day on the HUD (BETA 1.0 Pilot 3). */
class ClockTextTest {
    @Test
    void minecraftsTicksReadAsAClock() {
        assertEquals("06:00", ClockText.clock(0), "sunrise");
        assertEquals("12:00", ClockText.clock(6_000), "noon");
        assertEquals("18:00", ClockText.clock(12_000), "sunset");
        assertEquals("00:00", ClockText.clock(18_000), "midnight");
        assertEquals("14:30", ClockText.clock(8_500));
        assertEquals("05:59", ClockText.clock(23_999));
        assertEquals("12:00", ClockText.clock(24_000L * 900 + 6_000), "any day");
    }
}
