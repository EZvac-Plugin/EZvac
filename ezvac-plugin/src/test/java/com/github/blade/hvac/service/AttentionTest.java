package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.Attention;
import com.github.blade.hvac.model.OperatingMode;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a thermostat counts beside its ⚠ (BETA 1.0 Pilot 2), and the mode its
 * Undersized is judged in.
 */
class AttentionTest {
    private static final HvacSettings SETTINGS = HvacSettings.defaults();

    @Test
    void aWellSetUpSystemNeedsNothing() {
        assertEquals(Set.of(), Attention.of(true, true, false, false));
    }

    @Test
    void eachProblemCountsOnceInTheOrderTheMenuListsThem() {
        assertEquals(List.of(Attention.NO_UNITS, Attention.NO_VENTS, Attention.UNITS_OFF, Attention.UNDERSIZED),
                List.copyOf(Attention.of(false, false, true, true)));
        assertEquals(EnumSet.of(Attention.NO_VENTS), Attention.of(true, false, false, false));
    }

    @Test
    void onlyUndersizedLetsTheSystemStart() {
        for (Attention attention : Attention.values())
            assertEquals(attention != Attention.UNDERSIZED, attention.blocking(), attention.name());
    }

    @Test
    void undersizedIsJudgedInTheModeItRunsOrWouldStartIn() {
        assertEquals(OperatingMode.HEATING, HvacController.neededMode(OperatingMode.HEATING, 80, 72, SETTINGS),
                "running: the mode it's in, wherever the room is");
        assertEquals(OperatingMode.COOLING, HvacController.neededMode(OperatingMode.IDLE, 75, 72, SETTINGS));
        assertEquals(OperatingMode.HEATING, HvacController.neededMode(OperatingMode.IDLE, 68, 72, SETTINGS));
        assertNull(HvacController.neededMode(OperatingMode.IDLE, 72.5, 72, SETTINGS),
                "holding inside its band: neither, so nothing to be undersized for");
    }
}
