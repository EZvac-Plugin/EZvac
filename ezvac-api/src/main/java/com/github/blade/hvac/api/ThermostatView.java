package com.github.blade.hvac.api;

import java.util.Objects;

/**
 * A thermostat and its system, as it was when this view was made.
 *
 * @param id           the thermostat's id, unique on the server (case-insensitive)
 * @param system       the system (group) it controls
 * @param position     its sign
 * @param roomF        the temperature of the space it controls: a room, or a pool's water
 * @param targetF      its target
 * @param mode         what the system is doing
 * @param enabled      false when the system is switched off
 * @param pool         true for a pool thermostat
 * @param humidity     the room's relative humidity in percent; NaN for a pool or before it is first simulated
 * @param runningUnits equipment units running in its system
 * @param totalUnits   equipment units in its system
 * @since 1.0
 */
public record ThermostatView(String id, String system, Position position, double roomF, double targetF, Mode mode,
                             boolean enabled, boolean pool, double humidity, int runningUnits, int totalUnits)
        implements HvacDevice {
    public ThermostatView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(mode, "mode");
    }

    /** What a system is doing. */
    public enum Mode { COOLING, HEATING, IDLE }

    public double roomC() { return Temperatures.celsius(roomF); }

    public double targetC() { return Temperatures.celsius(targetF); }
}
