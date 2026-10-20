package com.github.blade.hvac.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PsychrometricsTest {
    @Test
    void relativeHumidityMatchesReferenceValues() {
        assertEquals(49.4, Psychrometrics.relativeHumidity(72.0, 52.0), 0.5, "72 F air with a 52 F dew point");
        assertEquals(100.0, Psychrometrics.relativeHumidity(60.0, 60.0), "at the dew point the air is saturated");
        assertEquals(100.0, Psychrometrics.relativeHumidity(60.0, 65.0), "never above 100%");
    }

    @Test
    void dewPointAndHumidityRoundTrip() {
        for (double temperature : new double[] {10, 45, 72, 95, 110}) {
            for (double rh : new double[] {5, 30, 50, 80, 100}) {
                double dew = Psychrometrics.dewPointF(temperature, rh);
                assertTrue(dew <= temperature);
                assertEquals(rh, Psychrometrics.relativeHumidity(temperature, dew), 0.01,
                        temperature + " F at " + rh + "%");
            }
        }
    }

    @Test
    void heatingAirWithoutAddingMoistureLowersItsHumidity() {
        double dew = Psychrometrics.dewPointF(40.0, 80.0);   // a damp winter morning
        assertTrue(Psychrometrics.relativeHumidity(70.0, dew) < 30.0, "the same air heated indoors is dry");
    }

    @Test
    void humidHeatFeelsHotterAndWindFeelsColder() {
        double muggy = Psychrometrics.apparentTemperatureF(95.0, 60.0, 0.0);
        assertTrue(muggy > 104.0 && muggy < 112.0, "95 F at 60% feels well above 100 F: " + muggy);
        assertTrue(Psychrometrics.apparentTemperatureF(95.0, 20.0, 0.0) < muggy, "dry heat feels milder");
        double calm = Psychrometrics.apparentTemperatureF(50.0, 50.0, 0.0);
        assertEquals(calm - 6.3, Psychrometrics.apparentTemperatureF(50.0, 50.0, 5.0), 0.01, "5 m/s of wind");
    }

    @Test
    void aComfortableRoomFeelsLikeItsTemperature() {
        assertEquals(72.0, Psychrometrics.apparentTemperatureF(72.0, 50.0, 0.0), 1.5);
    }

    @Test
    void nonFiniteInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Psychrometrics.relativeHumidity(Double.NaN, 50));
        assertThrows(IllegalArgumentException.class, () -> Psychrometrics.dewPointF(70, Double.NaN));
    }
}
