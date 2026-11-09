package com.github.blade.hvac.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where rain meets snow on the land (BETA 1.0 Pilot 4). */
class SnowEdgeTest {
    @Test
    void anAreasMarginIsHowMuchColderThanItsLineItIs() {
        assertEquals(4.0, SnowEdge.margin(36.0, 40.0, null, 2.0, false), 1e-12);
        assertEquals(1.0, SnowEdge.margin(41.0, 40.0, true, 2.0, false), 1e-12, "snowing: it keeps 2 F more");
        assertEquals(-1.0, SnowEdge.margin(39.0, 40.0, false, 2.0, false), 1e-12, "raining: 2 F less");
        assertEquals(SnowEdge.HELD_F, SnowEdge.margin(50.0, 40.0, true, 2.0, true), 1e-12,
                "held: still snowing, only just");
        assertEquals(-SnowEdge.HELD_F, SnowEdge.margin(20.0, 40.0, false, 2.0, true), 1e-12);
    }

    @Test
    void marginsBlendSmoothlyBetweenAreaCentres() {
        assertEquals(4.0, SnowEdge.blend(4, -4, 4, -4, 0, 0.3), 1e-12, "at a centre, its own");
        assertEquals(0.0, SnowEdge.blend(4, -4, 4, -4, 0.5, 0.7), 1e-12, "halfway between snow and rain: the edge");
        double before = SnowEdge.blend(4, -4, 4, -4, 0.49, 0.5), after = SnowEdge.blend(4, -4, 4, -4, 0.51, 0.5);
        assertTrue(before > 0 && after < 0 && before - after < 0.2, "no step anywhere: " + before + " " + after);
    }

    @Test
    void theNoiseWandersALittleAndIsAlwaysTheSame() {
        double low = 1, high = -1, step = 0;
        for (int x = 0; x < 400; x++) {
            double n = SnowEdge.noise(42L, x, 7);
            low = Math.min(low, n);
            high = Math.max(high, n);
            if (x > 0) step = Math.max(step, Math.abs(n - SnowEdge.noise(42L, x - 1, 7)));
        }
        assertTrue(low >= -1 && high <= 1 && high - low > 0.8, "it uses its range: " + low + " to " + high);
        assertTrue(step < 0.25, "smooth from block to block: " + step);
        assertEquals(SnowEdge.noise(42L, 123, -456), SnowEdge.noise(42L, 123, -456));
        assertNotEquals(SnowEdge.noise(42L, 123, -456), SnowEdge.noise(43L, 123, -456), "another world, another edge");
    }
}
