package com.github.blade.hvac.simulation;

import com.github.blade.hvac.service.OutdoorTemperatureService;

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

    @Test
    void rainTurnsToSnowByTheAirAndDryAreasFollowTheirRule() {
        // BETA 1.0 Pilot 4.
        ClimateSettings.Weather w = ClimateSettings.defaults().weather();
        BiomeBlend.Blended plains = BiomeBlend.Blended.of(80, 60, 0.4, Precipitation.RAIN, 15, null);
        BiomeBlend.Blended desert = BiomeBlend.Blended.of(100, 70, 0.0, Precipitation.NONE, 10, null);
        WeatherModel.Conditions rain = WeatherModel.Conditions.settled(true, false);
        WeatherModel.Conditions thunder = WeatherModel.Conditions.settled(true, true);
        assertEquals(Precipitation.RAIN, WeatherModel.falling(plains, 34.0, rain, w));
        assertEquals(Precipitation.SNOW, WeatherModel.falling(plains, 33.0, rain, w), "33 F and below snows");
        assertEquals(Precipitation.NONE, WeatherModel.falling(plains, 20.0, WeatherModel.Conditions.CLEAR, w),
                "nothing falls without rain");
        assertEquals("Snow", WeatherModel.word(WeatherModel.kind(plains, 20.0, rain, w), rain));
        assertEquals(Precipitation.NONE, WeatherModel.falling(desert, 90.0, rain, w), "ordinary rain skips a desert");
        assertEquals(Precipitation.RAIN, WeatherModel.falling(desert, 90.0, thunder, w), "a thunderstorm doesn't");
        assertEquals("Thunder", WeatherModel.word(WeatherModel.kind(desert, 90.0, thunder, w), thunder));
        assertEquals("", WeatherModel.word(WeatherModel.kind(desert, 90.0, rain, w), rain));
    }

    @Test
    void theSnowLineIsWhereTheAirCoolsToIt() {
        // 43 F at 64, cooling 0.1 F a block above 64: 33 F at 64 + 100 = 164.
        assertEquals(164.0, OutdoorTemperatureService.snowLine(43.0, 64, 64, 0.1, 33.0), 1e-9);
        assertEquals(164.0, OutdoorTemperatureService.snowLine(35.0, 144, 64, 0.1, 33.0), 1e-9,
                "the same air measured higher up");
        assertEquals(Double.NEGATIVE_INFINITY, OutdoorTemperatureService.snowLine(30.0, 64, 64, 0.1, 33.0),
                "snow at every height");
        assertEquals(Double.POSITIVE_INFINITY, OutdoorTemperatureService.snowLine(50.0, 64, 64, 0.0, 33.0),
                "air that never cools never snows");
    }

    @Test
    void anAreaKeepsItsSnowOrRainUntilTheAirIsWellPastTheLine() {
        // BETA 1.0 Pilot 4: against a line of 40 F (winter), with 2 F to spare either way.
        ClimateSettings.Weather w = ClimateSettings.defaults().weather();
        BiomeBlend.Blended plains = BiomeBlend.Blended.of(80, 60, 0.4, Precipitation.RAIN, 15, null);
        WeatherModel.Conditions rain = WeatherModel.Conditions.settled(true, false);
        assertEquals(40.0, w.snowInWinterBelowF());
        assertEquals(Precipitation.SNOW, WeatherModel.kind(plains, 39.0, rain, w, 40.0, null));
        assertEquals(Precipitation.RAIN, WeatherModel.kind(plains, 41.0, rain, w, 40.0, null));
        assertEquals(Precipitation.SNOW, WeatherModel.kind(plains, 41.5, rain, w, 40.0, Precipitation.SNOW),
                "snowing at 41.5: it keeps snowing");
        assertEquals(Precipitation.RAIN, WeatherModel.kind(plains, 42.5, rain, w, 40.0, Precipitation.SNOW));
        assertEquals(Precipitation.RAIN, WeatherModel.kind(plains, 38.5, rain, w, 40.0, Precipitation.RAIN),
                "raining at 38.5: it keeps raining");
        assertEquals(Precipitation.SNOW, WeatherModel.kind(plains, 37.5, rain, w, 40.0, Precipitation.RAIN));
        BiomeBlend.Blended desert = BiomeBlend.Blended.of(100, 70, 0.0, Precipitation.NONE, 10, null);
        assertEquals(Precipitation.NONE, WeatherModel.kind(desert, 20.0, rain, w, 40.0, Precipitation.SNOW),
                "a dry area stays dry whatever it had");
    }
}
