package com.github.blade.hvac.api;

import java.util.Objects;

/**
 * A vent, where a system's air or water comes out.
 *
 * @param system   the key of the system it belongs to (see {@link EzvacApi#thermostatOf})
 * @param position the vent block
 * @param medium   air for a room system, water for a pool system
 * @since 1.2
 */
public record VentView(String system, Position position, Medium medium) implements HvacDevice {
    public VentView {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(medium, "medium");
    }

    /** What comes out of it: AIR from an air vent, WATER from a pool system's grate. */
    public enum Medium { AIR, WATER }
}
