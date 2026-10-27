package com.github.blade.hvac.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A dehumidifier, humidifier or, since 1.5, air purifier, as it was when this view was made.
 *
 * @param label    its name
 * @param kind     which it is
 * @param position its sign
 * @param humidity the relative humidity of the space it works on, NaN before it first measured
 * @param setpoint the humidity it aims for, in percent; 0 for an air purifier, which has a fan mode
 * @param powered  false when switched off
 * @param state    what it is doing
 * @param tank     a dehumidifier's tank, 0 to 1; 0 for the others
 * @param filter   an air purifier's filter (1.5), 1 fresh to 0 full; empty for the others
 * @param airQuality what is in the air of the space it works on (1.5), empty before it first measured
 * @param fanMode  an air purifier's mode (1.5): a fixed speed, or {@code AUTO}; empty for the others
 * @param fanSpeed the speed an air purifier runs at (1.5), never {@code AUTO}; empty for the others
 * @since 1.0
 */
public record ClimateUnitView(String label, Kind kind, Position position, double humidity, int setpoint,
                              boolean powered, State state, double tank, OptionalDouble filter,
                              Optional<AirQuality> airQuality, Optional<Fan> fanMode, Optional<Fan> fanSpeed)
        implements HvacDevice {
    public ClimateUnitView {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        filter = filter == null ? OptionalDouble.empty() : filter;
        airQuality = airQuality == null ? Optional.empty() : airQuality;
        fanMode = fanMode == null ? Optional.empty() : fanMode;
        fanSpeed = fanSpeed == null ? Optional.empty() : fanSpeed;
    }

    /** A dehumidifier or humidifier, as before API 1.5. */
    public ClimateUnitView(String label, Kind kind, Position position, double humidity, int setpoint,
                           boolean powered, State state, double tank) {
        this(label, kind, position, humidity, setpoint, powered, state, tank, OptionalDouble.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** An air purifier's fan (1.5). */
    public enum Fan { LOW, MEDIUM, HIGH, AUTO }

    /** {@code AIR_PURIFIER} since 1.5: a switch over this needs a default branch to stay safe as kinds are added. */
    public enum Kind { DEHUMIDIFIER, HUMIDIFIER, AIR_PURIFIER }

    /**
     * {@code EMPTYING} is a full dehumidifier tank draining; {@code NO_FILTER} and
     * {@code FILTER_SPENT} (1.5) are an air purifier without a usable filter.
     */
    public enum State { OFF, IDLE, RUNNING, EMPTYING, NO_FILTER, FILTER_SPENT }
}
