package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutdoorHumidityTest {
    private final ClimateSettings settings = ClimateSettings.defaults();
    private final WeatherModel.Effect calm = WeatherModel.Effect.calm(settings.weather());

    private double rh(double noon, double midnight, double downfall, WeatherModel.Effect effect, double airNow) {
        double dew = HumidityModel.outdoorDewPointF(noon, midnight, downfall, effect, airNow, settings.humidity());
        return Psychrometrics.relativeHumidity(airNow, dew);
    }

    @Test
    void wetBiomesAreMoreHumidThanDryOnes() {
        double desert = rh(100, 78, 0.0, calm, 100);
        double plains = rh(72, 52, 0.4, calm, 72);
        double swamp = rh(72, 52, 0.9, calm, 72);
        assertTrue(desert < 25, "desert noon: " + desert);
        assertTrue(plains > desert && plains < swamp, "plains " + plains + " swamp " + swamp);
        assertEquals(54.0, rh(72, 52, 0.4, calm, 62), 0.5, "plains at the daily mean");
    }

    @Test
    void humidityRisesAtNightAsTheAirCools() {
        assertTrue(rh(72, 52, 0.4, calm, 52) > rh(72, 52, 0.4, calm, 72) + 30);
    }

    @Test
    void rainPushesHumidityTowardSaturation() {
        WeatherModel.Effect rain = WeatherModel.effect(Precipitation.RAIN,
                WeatherModel.Conditions.settled(true, false), settings.weather());
        assertTrue(rh(72, 52, 0.4, rain, 56.0) >= 96.0);
        WeatherModel.Effect easing = WeatherModel.effect(Precipitation.RAIN,
                new WeatherModel.Conditions(0.5, 0, true, false), settings.weather());
        double half = rh(72, 52, 0.4, easing, 59.0);
        assertTrue(half > rh(72, 52, 0.4, calm, 59.0) && half < 96.0, "half-set rain sits in between: " + half);
    }

    @Test
    void theDewPointNeverExceedsTheAir() {
        assertTrue(HumidityModel.outdoorDewPointF(85, 68, 0.9, calm, 60.0, settings.humidity()) <= 60.0);
    }
}
