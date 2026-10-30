package com.github.blade.hvac.api;

import java.util.Map;
import java.util.Objects;

/**
 * How big a system is and what equipment its size needs (EZvac BETA 1.0,
 * size-based heating and cooling). Units are counted in three-star units'
 * worth: a two-star unit is 0.85 of one, a one-star 0.70.
 *
 * @param system           the system's key (see {@link EzvacApi#thermostatOf})
 * @param size             blocks of air it owns (water blocks for a pool)
 * @param water            true for a pool system
 * @param openings         faces of its air open to the sky: doorways, windows, holes
 * @param leakFactor       how much faster than a sealed room it leaks: 1 when sealed
 * @param requiredCooling  three-star units' worth its size needs to cool
 * @param installedCooling what it has that cools
 * @param requiredHeating  the same to heat
 * @param installedHeating what it has that heats
 * @param touching         the systems its air touches, by key, with how many faces touch
 * @since 1.6
 */
public record SystemSize(String system, int size, boolean water, int openings, double leakFactor,
                         double requiredCooling, double installedCooling, double requiredHeating,
                         double installedHeating, Map<String, Integer> touching) {
    /** Below this share of what it needs, a system is undersized, as its screen says. */
    public static final double UNDERSIZED_BELOW = 0.95;

    public SystemSize {
        Objects.requireNonNull(system, "system");
        touching = touching == null ? Map.of() : Map.copyOf(touching);
    }

    /** Whether it has too little to heat or to cool at the normal pace. */
    public boolean undersized(ThermostatView.Mode mode) {
        return switch (mode) {
            case COOLING -> installedCooling < requiredCooling * UNDERSIZED_BELOW;
            case HEATING -> installedHeating < requiredHeating * UNDERSIZED_BELOW;
            case IDLE -> false;
        };
    }
}
