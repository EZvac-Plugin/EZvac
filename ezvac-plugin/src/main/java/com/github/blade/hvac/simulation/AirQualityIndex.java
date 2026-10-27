package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.AirQualitySettings;

import java.util.Locale;

/**
 * The US EPA Air Quality Index (3.2), from PM2.5 and PM10: each gets its own
 * score and the AQI is the higher one. Pollen is not part of it; it has its
 * own level. The EPA averages over 24 hours; EZvac scores the air as it is now.
 */
public final class AirQualityIndex {
    /** The AQI never goes past this. */
    public static final int MAXIMUM = 500;

    private AirQualityIndex() {}

    /** One EPA breakpoint row: {@code low} to {@code high} µg/m³ scores {@code indexLow} to {@code indexHigh}. */
    private record Breakpoint(double low, double high, int indexLow, int indexHigh) {}

    /** PM2.5, µg/m³, 24-hour, as revised in 2024. */
    private static final Breakpoint[] PM25 = {
            new Breakpoint(0.0, 9.0, 0, 50),
            new Breakpoint(9.1, 35.4, 51, 100),
            new Breakpoint(35.5, 55.4, 101, 150),
            new Breakpoint(55.5, 125.4, 151, 200),
            new Breakpoint(125.5, 225.4, 201, 300),
            new Breakpoint(225.5, 325.4, 301, 500),
    };

    /** PM10, µg/m³, 24-hour. */
    private static final Breakpoint[] PM10 = {
            new Breakpoint(0, 54, 0, 50),
            new Breakpoint(55, 154, 51, 100),
            new Breakpoint(155, 254, 101, 150),
            new Breakpoint(255, 354, 151, 200),
            new Breakpoint(355, 424, 201, 300),
            new Breakpoint(425, 604, 301, 500),
    };

    /** What drives the score: fine particles read as smoke, coarse ones as dust. */
    public enum Pollutant {
        PM25("PM2.5", "Smoke"), PM10("PM10", "Dust");

        private final String code;
        private final String cause;
        Pollutant(String code, String cause) { this.code = code; this.cause = cause; }
        public String code() { return code; }
        /** The plain word players see: Smoke or Dust. */
        public String cause() { return cause; }
    }

    public enum Band {
        GOOD("Good", "Good"),
        MODERATE("Moderate", "Moderate"),
        SENSITIVE("Unhealthy for Sensitive Groups", "Sensitive"),
        UNHEALTHY("Unhealthy", "Unhealthy"),
        VERY_UNHEALTHY("Very Unhealthy", "Very bad"),
        HAZARDOUS("Hazardous", "Hazardous");

        private final String displayName;
        private final String shortName;
        Band(String displayName, String shortName) { this.displayName = displayName; this.shortName = shortName; }
        public String displayName() { return displayName; }
        /** For signs, where the EPA's longest name does not fit. */
        public String shortName() { return shortName; }

        public static Band of(int aqi) {
            if (aqi <= 50) return GOOD;
            if (aqi <= 100) return MODERATE;
            if (aqi <= 150) return SENSITIVE;
            if (aqi <= 200) return UNHEALTHY;
            if (aqi <= 300) return VERY_UNHEALTHY;
            return HAZARDOUS;
        }
    }

    public enum PollenLevel {
        LOW("Low"), MODERATE("Moderate"), HIGH("High"), VERY_HIGH("Very high");

        private final String displayName;
        PollenLevel(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }

        public static PollenLevel of(double grains, AirQualitySettings.PollenLevels levels) {
            if (!(grains >= levels.moderate())) return LOW;
            if (grains < levels.high()) return MODERATE;
            if (grains < levels.veryHigh()) return HIGH;
            return VERY_HIGH;
        }

        /** {@code high}, {@code very-high}, {@code Very high}; null when it names no level. */
        public static PollenLevel parse(String text) {
            if (text == null) return null;
            return switch (text.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-')) {
                case "low" -> LOW;
                case "moderate" -> MODERATE;
                case "high" -> HIGH;
                case "very-high" -> VERY_HIGH;
                default -> null;
            };
        }
    }

    /** The PM2.5 score; the EPA truncates to a tenth first. */
    public static int pm25(double ugM3) {
        // The nudge keeps a value like 9.1, a hair under in binary, from truncating to 9.0.
        return score(Math.floor(Math.max(0.0, ugM3) * 10.0 + 1e-9) / 10.0, PM25);
    }

    /** The PM10 score; the EPA truncates to a whole µg/m³ first. */
    public static int pm10(double ugM3) {
        return score(Math.floor(Math.max(0.0, ugM3) + 1e-9), PM10);
    }

    /** The AQI: the higher of the two scores; 0 for air not known. */
    public static int aqi(AirQualityModel.Air air) {
        if (!air.known()) return 0;
        return Math.max(pm25(air.pm25()), pm10(air.pm10()));
    }

    /** Whichever pollutant gives the AQI; PM2.5 on a tie. */
    public static Pollutant dominant(AirQualityModel.Air air) {
        if (!air.known()) return Pollutant.PM25;
        return pm10(air.pm10()) > pm25(air.pm25()) ? Pollutant.PM10 : Pollutant.PM25;
    }

    /**
     * The EPA's linear interpolation within the concentration's row. After
     * truncation a concentration always lands inside a row, never between two.
     */
    private static int score(double concentration, Breakpoint[] table) {
        for (Breakpoint row : table) {
            if (concentration > row.high()) continue;
            double value = (row.indexHigh() - row.indexLow()) / (row.high() - row.low())
                    * (concentration - row.low()) + row.indexLow();
            return (int) Math.round(Math.max(row.indexLow(), value));
        }
        return MAXIMUM;
    }
}
