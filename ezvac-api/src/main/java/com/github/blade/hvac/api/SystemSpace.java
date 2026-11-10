package com.github.blade.hvac.api;

import java.util.List;
import java.util.Objects;

/**
 * The space a system's air (or a pool system's water) fills, as EZvac last
 * mapped it (API 1.10): for showing where a room ends and where it leaks.
 *
 * @param system   the system's key (see {@link EzvacApi#thermostatOf})
 * @param edge     blocks of its space that touch something outside it: walls, floors, ceilings and openings
 *                 on the far side; at most a few thousand, spread evenly over a bigger space
 * @param openings blocks of its air beside open sky: doorways, windows and holes it leaks through, as
 *                 {@link SystemSize#openings()} counts them (none for a pool)
 * @param water    true for a pool system
 * @param capped   true when {@code edge} was thinned out to keep it small
 */
public record SystemSpace(String system, List<Position> edge, List<Position> openings, boolean water,
                          boolean capped) {
    public SystemSpace {
        Objects.requireNonNull(system, "system");
        edge = List.copyOf(edge);
        openings = List.copyOf(openings);
    }
}
