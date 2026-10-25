package com.github.blade.hvac.api;

import java.util.Objects;

/**
 * A dehumidifier or humidifier, as it was when this view was made.
 *
 * @param label    its name
 * @param kind     which it is
 * @param position its sign
 * @param humidity the relative humidity of the space it works on, NaN before it first measured
 * @param setpoint the humidity it aims for, in percent
 * @param powered  false when switched off
 * @param state    what it is doing
 * @param tank     a dehumidifier's tank, 0 to 1; 0 for a humidifier, which is plumbed
 * @since 1.0
 */
public record ClimateUnitView(String label, Kind kind, Position position, double humidity, int setpoint,
                              boolean powered, State state, double tank) implements HvacDevice {
    public ClimateUnitView {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
    }

    public enum Kind { DEHUMIDIFIER, HUMIDIFIER }

    /** {@code EMPTYING} is a full dehumidifier tank draining. */
    public enum State { OFF, IDLE, RUNNING, EMPTYING }
}
