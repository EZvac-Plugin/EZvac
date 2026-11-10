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
        // seasonal (the default): only a spell that rolled it reaches a desert, thunder or not.
        assertEquals(ClimateSettings.Weather.DryAreas.SEASONAL, w.dryAreas());
        WeatherModel.Conditions reaching = WeatherModel.Conditions.settled(true, false, true);
        assertEquals(Precipitation.NONE, WeatherModel.falling(desert, 90.0, rain, w), "most rain skips a desert");
        assertEquals(Precipitation.NONE, WeatherModel.falling(desert, 90.0, thunder, w), "a thunderstorm too");
        assertEquals(Precipitation.RAIN, WeatherModel.falling(desert, 90.0, reaching, w), "a spell that reaches it");
        assertEquals(Precipitation.SNOW, WeatherModel.falling(desert, 30.0, reaching, w), "as snow on a freezing night");
        assertEquals("Rain", WeatherModel.word(WeatherModel.kind(desert, 90.0, reaching, w), reaching));
        assertEquals("", WeatherModel.word(WeatherModel.kind(desert, 90.0, rain, w), rain));
        // The air there feels the rain that reaches it, and nothing of the rain that doesn't.
        assertEquals(0.0, WeatherModel.effect(desert, rain, w).meanShiftF(), 1e-9);
        assertEquals(-w.rainCoolingF(), WeatherModel.effect(desert, reaching, w).meanShiftF(), 1e-9);
        assertEquals(WeatherModel.effect(plains, rain, w), WeatherModel.effect(plains, reaching, w),
                "wet ground is the same either way");
        // thunder: rain only in thunderstorms; never and always as they say.
        ClimateSettings.Weather byThunder = withDryAreas(w, ClimateSettings.Weather.DryAreas.THUNDER);
        assertEquals(Precipitation.NONE, WeatherModel.falling(desert, 90.0, reaching, byThunder));
        assertEquals(Precipitation.RAIN, WeatherModel.falling(desert, 90.0, thunder, byThunder));
        assertEquals("Thunder", WeatherModel.word(WeatherModel.kind(desert, 90.0, thunder, byThunder), thunder));
        assertEquals(Precipitation.NONE, WeatherModel.falling(desert, 90.0, reaching,
                withDryAreas(w, ClimateSettings.Weather.DryAreas.NEVER)));
        assertEquals(Precipitation.RAIN, WeatherModel.falling(desert, 90.0, rain,
                withDryAreas(w, ClimateSettings.Weather.DryAreas.ALWAYS)));
    }

    private static ClimateSettings.Weather withDryAreas(ClimateSettings.Weather w, ClimateSettings.Weather.DryAreas rule) {
        return new ClimateSettings.Weather(w.enabled(), w.easeHours(), w.rainCoolingF(), w.thunderCoolingF(),
                w.snowCoolingF(), w.swingReduction(), w.waterShare(), w.windClearMs(), w.windRainMs(),
                w.windThunderMs(), w.snowAtOrBelowF(), rule, w.snowInWinterBelowF());
    }

    @Test
    void aRainSpellRollsOnceWhetherItReachesDryAreas() {
        // BETA 1.0 Pilot 4: mostly winter's spells, rarely the other seasons'.
        ClimateSettings.Weather.DryRain shares = ClimateSettings.defaults().weather().dryRain();
        assertEquals(0.6, shares.share(Seasons.Season.WINTER));
        assertEquals(0.05, shares.share(Seasons.Season.SUMMER));
        assertEquals(0.1, shares.share(null), "spring's without seasons");
        int winter = 0, summer = 0;
        for (long day = 0; day < 20_000; day++) {
            boolean reaches = ClimateSettings.Weather.DryRain.reaches(42L, day, shares.winter());
            assertEquals(reaches, ClimateSettings.Weather.DryRain.reaches(42L, day, shares.winter()), "the same every time");
            if (reaches) winter++;
            if (ClimateSettings.Weather.DryRain.reaches(42L, day, shares.summer())) summer++;
        }
        assertEquals(0.6, winter / 20_000.0, 0.02);
        assertEquals(0.05, summer / 20_000.0, 0.01);
        assertFalse(ClimateSettings.Weather.DryRain.reaches(7L, 3L, 0.0));
        assertTrue(ClimateSettings.Weather.DryRain.reaches(7L, 3L, 1.0));
        // A spell keeps its reach while it rains, and loses it when the rain stops.
        WeatherModel.Conditions spell = WeatherModel.Conditions.settled(true, false, true);
        assertTrue(spell.advance(true, true, 100, 2.0).reachesDry(), "thunder joining doesn't change it");
        assertFalse(spell.advance(false, false, 100, 2.0).reachesDry());
        assertFalse(WeatherModel.Conditions.settled(false, false, true).reachesDry(), "nothing reaches without rain");
        assertTrue(WeatherModel.Conditions.CLEAR.advance(true, false, 100, 2.0, true).reachesDry());
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
