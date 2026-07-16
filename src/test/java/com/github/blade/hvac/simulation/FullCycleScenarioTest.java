package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.OperatingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Integration-style pure simulation of controller ordering across many cycles. */
class FullCycleScenarioTest {
    @Test
    void coolingCycleStartsRampsCrossesTargetAndStopsWithoutShortCycling() {
        HvacSettings settings = HvacSettings.defaults();
        double room = 78.0;
        double target = 72.0;
        double rpm = 0.0;
        OperatingMode mode = OperatingMode.IDLE;
        long ticksInMode = settings.minimumOffTicks();
        int starts = 0;
        int stops = 0;

        for (int cycle = 0; cycle < 500; cycle++) {
            boolean running = mode == OperatingMode.COOLING;
            double requested = PerformanceModel.requestedVariableCapacity(
                    Math.abs(room - target), settings);
            rpm = PerformanceModel.approach(rpm,
                    running ? PerformanceModel.variableTargetRpm(requested, settings) : 0.0,
                    1.0, settings.variableRampUpRpmPerSecond(),
                    settings.variableRampDownRpmPerSecond());
            double capacity = running
                    ? PerformanceModel.variableCapacityFromRpm(rpm, settings) : 0.0;
            room = ThermalModel.step(room, target - settings.coolingStopBelowF(), 85,
                    running ? OperatingMode.COOLING : OperatingMode.IDLE,
                    capacity, 20, settings).temperatureF();
            var next = ThermostatControl.advance(mode, ticksInMode, room, target,
                    true, true, true, 20, settings);
            if (mode == OperatingMode.IDLE && next.mode() == OperatingMode.COOLING) starts++;
            if (mode == OperatingMode.COOLING && next.mode() == OperatingMode.IDLE) stops++;
            mode = next.mode();
            ticksInMode = next.ticksInMode();
            if (stops > 0) break;
        }

        assertEquals(1, starts);
        assertEquals(1, stops);
        assertTrue(room <= target - settings.coolingStopBelowF() + 1e-9);
        assertEquals(OperatingMode.IDLE, mode);
    }

    @Test
    void repeatedCoolingCyclesNeverRestartEveryTwoSeconds() {
        HvacSettings settings = HvacSettings.defaults();
        double room = 73.2;
        double target = 72.0;
        double rpm = 0.0;
        OperatingMode mode = OperatingMode.IDLE;
        long ticksInMode = settings.minimumOffTicks();
        int starts = 0;
        int lastStartCycle = Integer.MIN_VALUE / 2;
        int shortestStartGap = Integer.MAX_VALUE;

        for (int cycle = 0; cycle < 3_000; cycle++) {
            boolean running = mode == OperatingMode.COOLING;
            double requested = PerformanceModel.requestedVariableCapacity(
                    Math.abs(room - target), settings);
            rpm = PerformanceModel.approach(rpm,
                    running ? PerformanceModel.variableTargetRpm(requested, settings) : 0.0,
                    1.0, settings.variableRampUpRpmPerSecond(),
                    settings.variableRampDownRpmPerSecond());
            double capacity = running
                    ? PerformanceModel.variableCapacityFromRpm(rpm, settings) : 0.0;
            room = ThermalModel.step(room, target - settings.coolingStopBelowF(), 85.0,
                    running ? OperatingMode.COOLING : OperatingMode.IDLE,
                    capacity, 20, settings).temperatureF();
            var next = ThermostatControl.advance(mode, ticksInMode, room, target,
                    true, true, true, 20, settings);
            if (mode == OperatingMode.IDLE && next.mode() == OperatingMode.COOLING) {
                starts++;
                if (lastStartCycle >= 0)
                    shortestStartGap = Math.min(shortestStartGap, cycle - lastStartCycle);
                lastStartCycle = cycle;
            }
            mode = next.mode();
            ticksInMode = next.ticksInMode();
        }

        assertTrue(starts > 2, "scenario must exercise repeated starts");
        assertTrue(shortestStartGap * 20 >= settings.minimumRunTicks() + settings.minimumOffTicks());
        assertTrue(shortestStartGap > 2, "a restart gap must be much longer than two seconds");
    }
}
