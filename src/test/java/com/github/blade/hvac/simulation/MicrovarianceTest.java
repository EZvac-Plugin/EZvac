package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Small differences from spot to spot, for readings only (2.0 Pilot 5). */
class MicrovarianceTest {
    private static final long SEED = 42L;
    private final ClimateSettings.Variance variance = ClimateSettings.defaults().variance();

    private double at(double spreadF, int x, int y, int z, double hours) {
        return Microvariance.offsetF(variance, spreadF, SEED, Microvariance.TEMPERATURE, x, y, z, hours);
    }

    @Test
    void theDefaultsAreTheAgreedSpreads() {
        assertEquals(0.5, variance.indoorF());
        assertEquals(1.5, variance.outdoorF());
        assertEquals(0.3, variance.poolF());
        assertEquals(0.8, variance.naturalWaterF());
        assertEquals(0.3, variance.dewPointF());
    }

    @Test
    void aSpotNeverStraysPastItsSpreadButSpotsDoDiffer() {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int x = -60; x < 60; x += 3)
            for (int z = -60; z < 60; z += 3)
                for (double hours : new double[] {0, 1.3, 7.9}) {
                    double offset = at(0.5, x, 64, z, hours);
                    assertTrue(Math.abs(offset) <= 0.5, "never past the spread: " + offset);
                    min = Math.min(min, offset);
                    max = Math.max(max, offset);
                }
        assertTrue(max - min > 0.5, "a house isn't one number: " + min + " .. " + max);
    }

    @Test
    void neighbouringBlocksReadAlmostTheSame() {
        for (int x = 0; x < 100; x++) {
            double step = Math.abs(at(1.5, x + 1, 64, 7, 2.0) - at(1.5, x, 64, 7, 2.0));
            assertTrue(step < 0.75, "changes gradually, not from block to block: " + step);
        }
    }

    @Test
    void aSpotIsSteadyFromMomentToMomentAndDriftsOverHours() {
        double now = at(0.5, 10, 64, 10, 5.0);
        assertEquals(now, at(0.5, 10, 64, 10, 5.0), "the same spot at the same moment reads the same");
        assertTrue(Math.abs(at(0.5, 10, 64, 10, 5.0 + 1.0 / 1000) - now) < 0.01, "one tick later: no visible change");
        boolean drifted = false;
        for (int x = 0; x < 20 && !drifted; x++)
            drifted = Math.abs(at(0.5, x, 64, 10, 5.0) - at(0.5, x, 64, 10, 11.0)) > 0.05;
        assertTrue(drifted, "hours later spots have moved on");
    }

    @Test
    void theSpreadBlendsWithHowFarASystemReaches() {
        assertEquals(1.5, Microvariance.airSpreadF(variance, 0.0), 1e-12, "outdoors");
        assertEquals(0.5, Microvariance.airSpreadF(variance, 1.0), 1e-12, "in a conditioned room");
        assertEquals(1.0, Microvariance.airSpreadF(variance, 0.5), 1e-12, "at a room's edge");
        assertEquals(0.8, Microvariance.waterSpreadF(variance, 0.0), 1e-12, "a lake");
        assertEquals(0.3, Microvariance.waterSpreadF(variance, 1.0), 1e-12, "a circulated pool");
    }

    @Test
    void switchedOffOrWithNoSpreadThereIsNone() {
        ClimateSettings.Variance off = new ClimateSettings.Variance(false, 0.5, 1.5, 0.3, 0.8, 0.3, 6.0, 3.0);
        assertEquals(0.0, Microvariance.offsetF(off, 0.5, SEED, Microvariance.TEMPERATURE, 3, 64, 3, 1.0));
        assertEquals(0.0, at(0.0, 3, 64, 3, 1.0));
        assertNotEquals(Microvariance.offsetF(variance, 0.5, SEED, Microvariance.TEMPERATURE, 3, 64, 3, 1.0),
                Microvariance.offsetF(variance, 0.5, SEED, Microvariance.DEW_POINT, 3, 64, 3, 1.0),
                "moisture has its own pattern");
    }
}
