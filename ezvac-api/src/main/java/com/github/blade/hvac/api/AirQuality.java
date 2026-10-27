package com.github.blade.hvac.api;

import java.util.Objects;

/**
 * What is in the air at a spot or in a room (EZvac 3.2). The AQI is the US
 * EPA index: the higher of the PM2.5 and PM10 scores, 0 to 500. Pollen is
 * not part of it and has its own level.
 *
 * @param pm25        fine particles, µg/m³
 * @param pm10        all particles up to 10 µm, fine ones included, µg/m³
 * @param pollen      grains/m³
 * @param aqi         the Air Quality Index, 0 to 500
 * @param band        the AQI's band
 * @param dominant    the pollutant that gives the AQI
 * @param pollenLevel how bad the pollen is, by the server's thresholds
 * @since 1.5
 */
public record AirQuality(double pm25, double pm10, double pollen, int aqi, Band band, Pollutant dominant,
                         PollenLevel pollenLevel) {
    public AirQuality {
        Objects.requireNonNull(band, "band");
        Objects.requireNonNull(dominant, "dominant");
        Objects.requireNonNull(pollenLevel, "pollenLevel");
    }

    /** The EPA's bands, best first. */
    public enum Band { GOOD, MODERATE, UNHEALTHY_FOR_SENSITIVE_GROUPS, UNHEALTHY, VERY_UNHEALTHY, HAZARDOUS }

    /** PM2.5 reads as smoke, PM10 as dust. */
    public enum Pollutant { PM25, PM10 }

    public enum PollenLevel { LOW, MODERATE, HIGH, VERY_HIGH }
}
