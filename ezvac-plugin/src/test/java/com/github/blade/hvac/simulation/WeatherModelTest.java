package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherModelTest {
    private final ClimateSettings.Weather settings = ClimateSettings.defaults().weather();

    @Test
    void weatherEasesInOverTheConfiguredHours() {
        WeatherModel.Conditions clear = WeatherModel.Conditions.CLEAR;
        WeatherModel.Conditions oneHour = clear.advance(true, false, 1_000, settings.easeHours());
        assertEquals(0.5, oneHour.wetness(), 1e-9, "halfway after one of two hours");
        assertTrue(oneHour.raining(), "the label follows the world at once");
        WeatherModel.Conditions settled = oneHour.advance(true, false, 5_000, settings.easeHours());
        assertEquals(1.0, settled.wetness(), 1e-9, "never overshoots");
        assertEquals(0.0, settled.advance(false, false, 2_000, settings.easeHours()).wetness(), 1e-9,
                "and clears the same way");
    }

    @Test
    void thunderNeedsRain() {
        assertEquals(1.0, WeatherModel.Conditions.settled(true, true).storm());
        assertEquals(0.0, WeatherModel.Conditions.settled(false, true).storm());
    }

    @Test
    void rainCoolsTheDayAndNarrowsTheSwing() {
        WeatherModel.Effect rain = WeatherModel.effect(Precipitation.RAIN,
                WeatherModel.Conditions.settled(true, false), settings);
        assertEquals(-6.0, rain.meanShiftF(), 1e-9);
        assertEquals(0.4, rain.swingFactor(), 1e-9);
        assertEquals(60.0, rain.noonF(72, 52), 1e-9, "a 72 F noon becomes 62 - 6 + 10 * 0.4");
        assertEquals(52.0, rain.midnightF(72, 52), 1e-9);
        WeatherModel.Effect thunder = WeatherModel.effect(Precipitation.RAIN,
                WeatherModel.Conditions.settled(true, true), settings);
        assertEquals(-9.0, thunder.meanShiftF(), 1e-9);
        assertTrue(thunder.windMs() > rain.windMs());
    }

    @Test
    void dryBiomesIgnoreTheWeatherAndSnowCoolsLess() {
        WeatherModel.Conditions storm = WeatherModel.Conditions.settled(true, true);
        WeatherModel.Effect desert = WeatherModel.effect(Precipitation.NONE, storm, settings);
        assertEquals(0.0, desert.meanShiftF());
        assertEquals(1.0, desert.swingFactor());
        assertEquals(0.0, desert.wetness());
        assertEquals(-3.0, WeatherModel.effect(Precipitation.SNOW,
                WeatherModel.Conditions.settled(true, false), settings).meanShiftF(), 1e-9);
    }

    @Test
    void disablingWeatherLeavesTheAirAlone() {
        ClimateSettings.Weather off = new ClimateSettings.Weather(false, 2, 6, 9, 3, 0.6, 0.1, 1.5, 3, 5);
        assertEquals(0.0, WeatherModel.effect(Precipitation.RAIN,
                WeatherModel.Conditions.settled(true, true), off).meanShiftF());
    }

    @Test
    void wordsDescribeWhatFalls() {
        WeatherModel.Conditions rain = WeatherModel.Conditions.settled(true, false);
        assertEquals("Rain", WeatherModel.word(Precipitation.RAIN, rain));
        assertEquals("Snow", WeatherModel.word(Precipitation.SNOW, rain));
        assertEquals("Thunder", WeatherModel.word(Precipitation.RAIN, WeatherModel.Conditions.settled(true, true)));
        assertEquals("Clear", WeatherModel.word(Precipitation.RAIN, WeatherModel.Conditions.CLEAR));
        assertEquals("", WeatherModel.word(Precipitation.NONE, rain), "deserts never show weather");
    }

    @Test
    void theSkysWordDoesNotDependOnTheWeatherEffects() {
        ClimateSettings.Weather off = new ClimateSettings.Weather(false, 2.0, 6.0, 9.0, 3.0, 0.6, 0.1, 1.5, 3.0, 5.0);
        WeatherModel.Conditions rain = WeatherModel.Conditions.settled(true, false);
        assertEquals(WeatherModel.Effect.calm(off), WeatherModel.effect(Precipitation.RAIN, rain, off),
                "with the effects off, rain changes no temperature");
        assertEquals("Rain", WeatherModel.word(Precipitation.RAIN, rain), "but a weather sensor still sees it");
    }
}
