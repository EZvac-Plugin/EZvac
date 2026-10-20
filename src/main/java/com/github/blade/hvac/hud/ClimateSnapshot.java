package com.github.blade.hvac.hud;

/**
 * What one player's climate looked like at the last HUD update. Immutable,
 * so renderers and placeholder requests on any thread can read it safely
 * while the main thread builds the next one.
 *
 * @param localF       temperature where the player stands
 * @param outdoorF     outdoor temperature at the player's position
 * @param influence    0..1, how strongly a system conditions this spot
 * @param distance     airflow path length from the nearest vent, or -1
 * @param group        label of the conditioning system, or null when none
 * @param thermostat   id of that system's thermostat, or null
 * @param mode         thermostat mode (COOLING, HEATING, IDLE), or null
 * @param targetF      thermostat target, or NaN when there is none
 * @param core         true inside the full-strength core range
 * @param water        true when the player is in water; localF is then the water temperature
 */
public record ClimateSnapshot(double localF, double outdoorF, double influence, int distance,
                              String group, String thermostat, String mode, double targetF,
                              boolean core, boolean water) {
    public boolean conditioned() { return group != null && thermostat != null && influence > 0.0; }

    /** A spot no system reaches: the local temperature is simply the outdoor one. */
    public static ClimateSnapshot outdoors(double outdoorF) {
        return new ClimateSnapshot(outdoorF, outdoorF, 0.0, -1, null, null, null, Double.NaN, false, false);
    }
}
