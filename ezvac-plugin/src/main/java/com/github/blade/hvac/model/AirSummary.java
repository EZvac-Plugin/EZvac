package com.github.blade.hvac.model;

import java.util.Map;

/**
 * What a system's airflow map came to (BETA 1.0 Pilot 8), saved with its
 * thermostat so the system can run at its real size while its chunks are
 * unloaded, even after a restart.
 *
 * @param owned    blocks of air (or water) the system owns
 * @param openings openings to the sky it found
 * @param water    whether it is a water system
 * @param cells    cells in its map
 */
public record AirSummary(int owned, int openings, boolean water, int cells) {
    public AirSummary {
        owned = Math.max(0, owned);
        openings = Math.max(0, openings);
        cells = Math.max(0, cells);
    }

    public void write(Map<String, Object> map) {
        map.put("airOwned", owned);
        map.put("airOpenings", openings);
        if (water) map.put("airWater", true);
        map.put("airCells", cells);
    }

    /** The summary a save holds, or null when it has none. */
    public static AirSummary read(Map<String, Object> map) {
        if (!(map.get("airOwned") instanceof Number owned) || !(map.get("airCells") instanceof Number cells)) return null;
        int openings = map.get("airOpenings") instanceof Number number ? number.intValue() : 0;
        return new AirSummary(owned.intValue(), openings, Boolean.TRUE.equals(map.get("airWater")), cells.intValue());
    }
}
