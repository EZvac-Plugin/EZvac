package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.OperatingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThermostatControlTest {
    private final HvacSettings settings = HvacSettings.defaults();

    @Test
    void startsOnlyAfterOffWindowAndStrictThreshold() {
        assertEquals(OperatingMode.IDLE, advance(OperatingMode.IDLE, 199, 74, 72,
                true, true, 0).mode());
        assertEquals(OperatingMode.COOLING, advance(OperatingMode.IDLE, 200, 74, 72,
                true, true, 0).mode());
        assertEquals(OperatingMode.IDLE, advance(OperatingMode.IDLE, 200, 73, 72,
                true, true, 0).mode());
    }

    @Test
    void activeCycleHonorsMinimumRuntimeAndOvershootBoundary() {
        assertEquals(OperatingMode.COOLING, advance(OperatingMode.COOLING, 299, 69.6, 70,
                true, true, 0).mode());
        assertEquals(OperatingMode.IDLE, advance(OperatingMode.COOLING, 300, 69.7, 70,
                true, true, 0).mode());
        assertEquals(OperatingMode.IDLE, advance(OperatingMode.HEATING, 300, 70.3, 70,
                true, true, 0).mode());
    }

    @Test
    void missingCompatibleEquipmentEndsDemandSafely() {
        assertEquals(OperatingMode.IDLE, ThermostatControl.advance(OperatingMode.COOLING,
                50, 80, 70, true, false, true, 20, settings).mode());
        assertEquals(OperatingMode.IDLE, ThermostatControl.advance(OperatingMode.HEATING,
                50, 60, 70, true, true, false, 20, settings).mode());
    }

    @Test
    void disabledControllerImmediatelyGoesIdleAndResetsTimer() {
        var state = ThermostatControl.advance(OperatingMode.COOLING, 250,
                80, 70, false, true, true, 20, settings);
        assertEquals(OperatingMode.IDLE, state.mode());
        assertEquals(0, state.ticksInMode());
        assertTrue(state.changed());
    }

    @Test
    void zeroTimeReconciliationCanActOnAlreadySatisfiedTimer() {
        var state = advance(OperatingMode.IDLE, 200, 60, 70, true, true, 0);
        assertEquals(OperatingMode.HEATING, state.mode());
        assertEquals(0, state.ticksInMode());
    }

    private ThermostatControl.State advance(OperatingMode mode, long ticks, double room,
                                             double target, boolean cooling, boolean heating,
                                             long elapsed) {
        return ThermostatControl.advance(mode, ticks, room, target, true,
                cooling, heating, elapsed, settings);
    }
}
