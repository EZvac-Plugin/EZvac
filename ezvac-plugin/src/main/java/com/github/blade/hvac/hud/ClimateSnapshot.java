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
 * @param windMs       the wind felt here (3.2), m/s; 0 where there is none
 * @param windLevel    how it reads (Calm .. Gale), empty in water, outside the overworld or with wind off
 * @param windSpeed    its speed in the server's units ({@code 22 km/h}), empty with windLevel
 */
public record ClimateSnapshot(double localF, double outdoorF, double influence, int distance,
                              String system, String thermostat, String mode, double targetF,
                              boolean core, boolean water, double humidity, double feelsLikeF,
                              String weather, String season, int seasonDay, AirQualityModel.Air air,
                              String pollen, double windMs, String windLevel, String windSpeed) {
    public ClimateSnapshot {
        if (!Double.isFinite(feelsLikeF)) feelsLikeF = localF;
        if (weather == null) weather = "";
        if (season == null) season = "";
        if (season.isEmpty()) seasonDay = 0;
        if (air == null) air = AirQualityModel.Air.UNKNOWN;
        if (pollen == null || !air.known()) pollen = "";
        if (windLevel == null) windLevel = "";
        if (windSpeed == null || windLevel.isEmpty()) windSpeed = "";
        windMs = Double.isFinite(windMs) && !windLevel.isEmpty() ? Math.max(0.0, windMs) : 0.0;
    }

    /** A snapshot without wind. */
    public ClimateSnapshot(double localF, double outdoorF, double influence, int distance,
                           String system, String thermostat, String mode, double targetF,
                           boolean core, boolean water, double humidity, double feelsLikeF,
                           String weather, String season, int seasonDay, AirQualityModel.Air air, String pollen) {
        this(localF, outdoorF, influence, distance, system, thermostat, mode, targetF, core, water, humidity,
                feelsLikeF, weather, season, seasonDay, air, pollen, 0.0, "", "");
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
                water, humidity, feelsLikeF, weather, season, day, air, pollen, windMs, windLevel, windSpeed);
    }

    /** The same snapshot, with the air and its pollen level (3.2). */
    public ClimateSnapshot withAir(AirQualityModel.Air air, String pollen) {
        return new ClimateSnapshot(localF, outdoorF, influence, distance, system, thermostat, mode, targetF, core,
                water, humidity, feelsLikeF, weather, season, seasonDay, air, pollen, windMs, windLevel, windSpeed);
    }

    /** The same snapshot, with the wind (3.2). */
    public ClimateSnapshot withWind(double windMs, String level, String speed) {
        return new ClimateSnapshot(localF, outdoorF, influence, distance, system, thermostat, mode, targetF, core,
                water, humidity, feelsLikeF, weather, season, seasonDay, air, pollen, windMs, level, speed);
    }

    /** {@code Calm}, or {@code Windy 22 km/h}; empty where there is no wind to read. */
    public String wind() {
        if (windLevel.isEmpty() || windLevel.equals("Calm")) return windLevel;
        return windLevel + " " + windSpeed;
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
