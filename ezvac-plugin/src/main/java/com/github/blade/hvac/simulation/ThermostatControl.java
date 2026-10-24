package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.OperatingMode;

import java.util.Objects;

/** Pure thermostat hysteresis and anti-short-cycle state machine. */
public final class ThermostatControl {
    private ThermostatControl() {}

    public record State(OperatingMode mode, long ticksInMode, boolean changed) {}

    public static State advance(OperatingMode current, long ticksInMode,
                                double roomF, double targetF, boolean enabled,
                                boolean coolingAvailable, boolean heatingAvailable,
                                long elapsedTicks, HvacSettings settings) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(settings, "settings");
        if (!Double.isFinite(roomF) || !Double.isFinite(targetF))
            throw new IllegalArgumentException("temperatures must be finite");
        if (ticksInMode < 0 || elapsedTicks < 0)
            throw new IllegalArgumentException("mode timers must be non-negative");

        long elapsed = saturatingAdd(ticksInMode, elapsedTicks);
        OperatingMode next = current;

        if (!enabled || (current == OperatingMode.COOLING && !coolingAvailable)
                || (current == OperatingMode.HEATING && !heatingAvailable)) {
            next = OperatingMode.IDLE;
        } else {
            switch (current) {
                case COOLING -> {
                    if (elapsed >= settings.minimumRunTicks()
                            && roomF <= targetF - settings.coolingStopBelowF()) {
                        next = OperatingMode.IDLE;
                    }
                }
                case HEATING -> {
                    if (elapsed >= settings.minimumRunTicks()
                            && roomF >= targetF + settings.heatingStopAboveF()) {
                        next = OperatingMode.IDLE;
                    }
                }
                case IDLE -> {
                    if (elapsed >= settings.minimumOffTicks()) {
                        if (coolingAvailable && roomF > targetF + settings.coolingStartAboveF())
                            next = OperatingMode.COOLING;
                        else if (heatingAvailable && roomF < targetF - settings.heatingStartBelowF())
                            next = OperatingMode.HEATING;
                    }
                }
            }
        }

        boolean changed = next != current;
        return new State(next, changed ? 0L : elapsed, changed);
    }

    private static long saturatingAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) return Long.MAX_VALUE;
        return first + second;
    }
}
