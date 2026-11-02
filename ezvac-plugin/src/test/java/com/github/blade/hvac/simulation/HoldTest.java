package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.OperatingProfile;
import com.github.blade.hvac.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Units ease down near the target, but never below what the room is losing
 * (BETA 1.0 Pilot 2). Found on the test server: three mini thermostats in the
 * open, leaking at the cap, sat 0.3° short of their target for good, as
 * easing down to the minimum made exactly what the room lost.
 */
class HoldTest {
    private static final HvacSettings SETTINGS = HvacSettings.defaults();
    private static final List<PerformanceModel.Unit> ONE_HEAT_PUMP =
            List.of(new PerformanceModel.Unit(EquipmentType.HEAT_PUMP, Tier.VARIABLE));
    /** The prototypes: open air at the openings cap, and a breeze. */
    private static final double LEAKY = 9.2;
    private static final double SMALL = 2.0;

    /** A room heated from 75 °F toward 85 °F, 55 °F outside, stepped the way the controller steps it. */
    private static double heat(boolean hold, double leakFactor, double sizeFactor, double hours) {
        double room = 75.0, target = 85.0, outdoor = 55.0;
        double loss = SETTINGS.activeLeakFPerHour() * leakFactor;
        double held = hold ? PerformanceModel.hold(ONE_HEAT_PUMP, OperatingMode.HEATING, OperatingProfile.NORMAL,
                sizeFactor, loss, SETTINGS).request() : 0.0;
        long step = 20;
        for (long ticks = 0; ticks < hours * 1000; ticks += step) {
            double staged = PerformanceModel.stagedRequest(Tier.VARIABLE, Math.abs(room - target), SETTINGS);
            double request = PerformanceModel.withHold(Tier.VARIABLE, staged, held);
            double capacity = SizingModel.combine(0.0, PerformanceModel.steadyCapacity(EquipmentType.HEAT_PUMP,
                    Tier.VARIABLE, request, OperatingProfile.NORMAL, SETTINGS), sizeFactor, SETTINGS);
            room = ThermalModel.step(room, target + SETTINGS.heatingStopAboveF(), outdoor, OperatingMode.HEATING,
                    capacity, step, SETTINGS, leakFactor).temperatureF();
            if (room >= target) return room;
        }
        return room;
    }

    @Test
    void easingDownToTheMinimumStalledALeakyRoomShortOfItsTarget() {
        double without = heat(false, LEAKY, SMALL, 48);
        assertTrue(without < 85.0 - 0.1, "before Pilot 2 it never got there: " + without);
        assertTrue(heat(true, LEAKY, SMALL, 6) >= 85.0, "with the hold it reaches its target");
    }

    @Test
    void aSealedRoomStillEasesDownAsItAlwaysDid() {
        PerformanceModel.Hold hold = PerformanceModel.hold(ONE_HEAT_PUMP, OperatingMode.HEATING, OperatingProfile.NORMAL,
                1.0, SETTINGS.activeLeakFPerHour(), SETTINGS);
        double staged = PerformanceModel.stagedRequest(Tier.VARIABLE, 0.3, SETTINGS);
        assertTrue(hold.keepsUp());
        assertEquals(staged, PerformanceModel.withHold(Tier.VARIABLE, staged, hold.request()), 1e-9,
                "its small loss asks for less than easing down already gives");
    }

    @Test
    void noHoldWhenTheOutdoorsHelpsOrItIsIdle() {
        assertEquals(PerformanceModel.Hold.NONE, PerformanceModel.hold(ONE_HEAT_PUMP, OperatingMode.HEATING,
                OperatingProfile.NORMAL, 1.0, 0.0, SETTINGS));
        assertEquals(PerformanceModel.Hold.NONE, PerformanceModel.hold(ONE_HEAT_PUMP, OperatingMode.IDLE,
                OperatingProfile.NORMAL, 1.0, 5.0, SETTINGS));
    }

    @Test
    void flatOutStillLosingIsCantKeepUp() {
        PerformanceModel.Hold hold = PerformanceModel.hold(ONE_HEAT_PUMP, OperatingMode.HEATING,
                OperatingProfile.NORMAL, 0.5, 4.0, SETTINGS);
        assertEquals(1.0, hold.request(), 1e-9, "it runs flat out");
        assertFalse(hold.keepsUp(), "half a unit's worth makes 2.5 °F an hour against 4 lost");
        assertTrue(PerformanceModel.hold(ONE_HEAT_PUMP, OperatingMode.HEATING, OperatingProfile.NORMAL, 0.5, 2.0,
                SETTINGS).keepsUp(), "against 2 lost it holds, if slowly");
    }

    @Test
    void aTwoStageUnitGoesToItsHighStageWhenItsLowOneIsntEnough() {
        double low = SETTINGS.twoStageLow();
        assertEquals(low, PerformanceModel.withHold(Tier.TWO_STAGE, low, low - 0.1), 1e-9);
        assertEquals(1.0, PerformanceModel.withHold(Tier.TWO_STAGE, low, low + 0.05), 1e-9);
        assertEquals(1.0, PerformanceModel.withHold(Tier.SINGLE_STAGE, 0.0, 0.0), 1e-9, "single-stage is always flat out");
    }

    @Test
    void ecoMakesAVariableUnitCountForLessAndTurboForMore() {
        double eco = PerformanceModel.profileShare(OperatingProfile.ECO, SETTINGS);
        double turbo = PerformanceModel.profileShare(OperatingProfile.TURBO, SETTINGS);
        assertEquals(1.0, PerformanceModel.profileShare(OperatingProfile.NORMAL, SETTINGS), 1e-9);
        assertEquals(0.7, eco, 1e-9, "2,400 of 3,600 RPM: 70% of its capacity");
        assertTrue(turbo > 1.0, "Turbo: " + turbo);
    }

    @Test
    void theEtaFollowsTheHoldSoALeakyRoomGetsOne() {
        java.util.OptionalLong eta = EtaEstimator.estimateTicks(84.0, 85.0, 55.0, OperatingMode.HEATING,
                List.of(new EtaEstimator.UnitSnapshot(EquipmentType.HEAT_PUMP, 1200, Tier.VARIABLE)),
                OperatingProfile.NORMAL, SETTINGS, LEAKY, SMALL);
        assertTrue(eta.isPresent(), "it reaches the target, so the ETA isn't endless");
    }

    @Test
    void theOutdoorsLevelWithTheRoomStillWorksAgainstIt() {
        // Found live: a room that can't keep up is pulled to the outdoor temperature and held there, so a
        // strict "colder outside" saw no loss at all, and never said it couldn't keep up.
        assertTrue(PerformanceModel.against(OperatingMode.HEATING, 56.6, 56.6));
        assertTrue(PerformanceModel.against(OperatingMode.COOLING, 80.0, 80.0));
        assertFalse(PerformanceModel.against(OperatingMode.HEATING, 56.6, 60.0), "warmer outside helps heating");
        assertFalse(PerformanceModel.against(OperatingMode.COOLING, 80.0, 70.0), "cooler outside helps cooling");
        assertFalse(PerformanceModel.against(OperatingMode.IDLE, 70.0, 50.0));
    }
}
