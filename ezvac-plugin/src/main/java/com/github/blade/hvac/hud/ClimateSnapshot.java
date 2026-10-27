package com.github.blade.hvac.hud;

import com.github.blade.hvac.simulation.AirQualityModel;

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
 * @param humidity     relative humidity of the air (%), NaN in water or when unknown
 * @param feelsLikeF   apparent temperature; the water temperature in water
 * @param weather      outdoor weather word (Clear, Rain, Thunder, Snow), empty where there is none
 * @param season       the season (Spring, Summer, Autumn, Winter), empty where there are none (2.1)
 * @param seasonDay    the day of that season from 1, or 0 where there are no seasons
 * @param air          what is in the air (3.2); unknown in water or with air quality off
 * @param pollen       the pollen level's name (Low .. Very high), empty where the air is unknown
 */
public record ClimateSnapshot(double localF, double outdoorF, double influence, int distance,
                              String system, String thermostat, String mode, double targetF,
                              boolean core, boolean water, double humidity, double feelsLikeF,
                              String weather, String season, int seasonDay, AirQualityModel.Air air,
                              String pollen) {
    public ClimateSnapshot {
        if (!Double.isFinite(feelsLikeF)) feelsLikeF = localF;
        if (weather == null) weather = "";
        if (season == null) season = "";
        if (season.isEmpty()) seasonDay = 0;
        if (air == null) air = AirQualityModel.Air.UNKNOWN;
        if (pollen == null || !air.known()) pollen = "";
    }

    /** A snapshot without air. */
    public ClimateSnapshot(double localF, double outdoorF, double influence, int distance,
                           String system, String thermostat, String mode, double targetF,
                           boolean core, boolean water, double humidity, double feelsLikeF,
                           String weather, String season, int seasonDay) {
        this(localF, outdoorF, influence, distance, system, thermostat, mode, targetF, core, water, humidity,
                feelsLikeF, weather, season, seasonDay, AirQualityModel.Air.UNKNOWN, "");
    }

    /** A snapshot where there are no seasons. */
    public ClimateSnapshot(double localF, double outdoorF, double influence, int distance,
                           String system, String thermostat, String mode, double targetF,
                           boolean core, boolean water, double humidity, double feelsLikeF,
                           String weather) {
        this(localF, outdoorF, influence, distance, system, thermostat, mode, targetF, core, water,
                humidity, feelsLikeF, weather, "", 0);
    }

    /** The same snapshot, in this season. */
    public ClimateSnapshot inSeason(String season, int day) {
        return new ClimateSnapshot(localF, outdoorF, influence, distance, system, thermostat, mode, targetF, core,
                water, humidity, feelsLikeF, weather, season, day, air, pollen);
    }

    /** The same snapshot, with the air and its pollen level (3.2). */
    public ClimateSnapshot withAir(AirQualityModel.Air air, String pollen) {
        return new ClimateSnapshot(localF, outdoorF, influence, distance, system, thermostat, mode, targetF, core,
                water, humidity, feelsLikeF, weather, season, seasonDay, air, pollen);
    }

    /** A snapshot without humidity: it feels like its temperature. */
    public ClimateSnapshot(double localF, double outdoorF, double influence, int distance,
                           String system, String thermostat, String mode, double targetF,
                           boolean core, boolean water) {
        this(localF, outdoorF, influence, distance, system, thermostat, mode, targetF, core, water,
                Double.NaN, localF, "");
    }

    public boolean conditioned() { return system != null && thermostat != null && influence > 0.0; }

    /** The temperature comfort is judged by: how air feels, or simply the water's temperature. */
    public double comfortF() { return water ? localF : feelsLikeF; }

    /** A spot no system reaches: the local temperature is simply the outdoor one. */
    public static ClimateSnapshot outdoors(double outdoorF) {
        return new ClimateSnapshot(outdoorF, outdoorF, 0.0, -1, null, null, null, Double.NaN, false, false);
    }
}
