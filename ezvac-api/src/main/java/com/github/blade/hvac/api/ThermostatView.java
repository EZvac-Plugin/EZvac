package com.github.blade.hvac.api;

import java.util.Objects;
import java.util.Optional;

/**
 * A thermostat and its system, as it was when this view was made.
 *
 * @param id           the thermostat's label; since 1.3 (EZvac 3.0) labels may repeat
 * @param system       the key of the system it controls (see {@link EzvacApi#thermostatOf})
 * @param position     its sign
 * @param roomF        the temperature of the space it controls: a room, or a pool's water
 * @param targetF      its target
 * @param mode         what the system is doing
 * @param enabled      false when the system is switched off
 * @param pool         true for a pool thermostat
 * @param humidity     the room's relative humidity in percent; NaN for a pool or before it is first simulated
 * @param runningUnits equipment units running in its system
 * @param totalUnits   equipment units in its system
 * @param airQuality   what is in the room's air (1.5); empty for a pool, before it is first simulated,
 *                     or where the server has air quality switched off
 * @param unitsOff     true while it would heat or cool but every unit that could is switched off
 *                     server-wide in /hvac quicksettings (1.6, EZvac BETA 1.0): its screen says "Units off"
 * @since 1.0
 */
public record ThermostatView(String id, String system, Position position, double roomF, double targetF, Mode mode,
                             boolean enabled, boolean pool, double humidity, int runningUnits, int totalUnits,
                             Optional<AirQuality> airQuality, boolean unitsOff)
        implements HvacDevice {
    public ThermostatView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(mode, "mode");
        airQuality = airQuality == null ? Optional.empty() : airQuality;
    }

    /** A view as in API 1.5, before units could be switched off server-wide. */
    public ThermostatView(String id, String system, Position position, double roomF, double targetF, Mode mode,
                          boolean enabled, boolean pool, double humidity, int runningUnits, int totalUnits,
                          Optional<AirQuality> airQuality) {
        this(id, system, position, roomF, targetF, mode, enabled, pool, humidity, runningUnits, totalUnits,
                airQuality, false);
    }

    /** A view without air quality, as before API 1.5. */
    public ThermostatView(String id, String system, Position position, double roomF, double targetF, Mode mode,
                          boolean enabled, boolean pool, double humidity, int runningUnits, int totalUnits) {
        this(id, system, position, roomF, targetF, mode, enabled, pool, humidity, runningUnits, totalUnits,
                Optional.empty(), false);
    }

    /** What a system is doing. */
    public enum Mode { COOLING, HEATING, IDLE }

    public double roomC() { return Temperatures.celsius(roomF); }

    public double targetC() { return Temperatures.celsius(targetF); }
}
