package com.github.blade.hvac.api;

/**
 * The outdoors at a spot, whatever any system does: biome, height, the
 * ground, the season and the weather all included. Fahrenheit.
 *
 * @param airF          the outdoor air
 * @param waterF        natural water: lakes, rivers, the sea, an unheated pool
 * @param humidity      relative humidity in percent
 * @param dewPointF     the outdoor moisture
 * @param feelsLikeF    the apparent temperature, wind included
 * @param weather       Clear, Rain, Thunder or Snow; empty where weather never reaches
 * @param seasonShiftF  how much the season warms (positive) or cools (negative) the air here
 * @since 1.0
 */
public record OutdoorClimate(double airF, double waterF, double humidity, double dewPointF, double feelsLikeF,
                             String weather, double seasonShiftF) {
    public OutdoorClimate {
        weather = weather == null ? "" : weather;
    }

    public double airC() { return Temperatures.celsius(airF); }

    public double waterC() { return Temperatures.celsius(waterF); }
}
