package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.WeatherStation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForecastTest {
    private static final ClimateSettings.Weather WEATHER = ClimateSettings.defaults().weather();
    /** A plains-like area: 80 F at noon, 60 F at midnight, rain falls here. */
    private static final WeatherStation.Area PLAINS =
            new WeatherStation.Area(80.0, 60.0, 0.4, 1.0, 0.0, 10.0, 6.0, 10.0, 60.0, 1.0);
    private static final WeatherStation.Area DESERT =
            new WeatherStation.Area(100.0, 70.0, 0.0, 0.0, 0.0, 10.0, 6.0, 10.0, 20.0, 1.0);

    @Test
    void daysChangeAtSunrise() {
        assertEquals(24_000, Forecast.leftToday(0));
        assertEquals(12_000, Forecast.leftToday(12_000));
        assertEquals(1, Forecast.leftToday(23_999));
        assertEquals("06:00", Forecast.clock(0));
        assertEquals("12:00", Forecast.clock(6_000));
        assertEquals("18:00", Forecast.clock(12_000));
        assertEquals("00:00", Forecast.clock(18_000));
    }

    @Test
    void chancesAreSteadyForTheSameClocksAndSeed() {
        Forecast.Clocks clocks = new Forecast.Clocks(false, 30_000, false, 50_000, 0, 1_000);
        Forecast.Chances once = Forecast.chances(clocks, 42L), again = Forecast.chances(clocks, 42L);
        assertArrayEquals(once.rain(), again.rain());
        for (int day = 0; day < Forecast.DAYS; day++) {
            assertTrue(once.rain()[day] >= 0.0 && once.rain()[day] <= 1.0);
            assertTrue(once.thunder()[day] <= once.rain()[day] + 1e-9, "thunder only storms while it rains");
        }
    }

    @Test
    void whatTheClocksAlreadyKnowIsCertain() {
        // Raining now for another two days: today and tomorrow are wet for sure.
        Forecast.Chances raining = Forecast.chances(new Forecast.Clocks(true, 48_000, false, 100_000, 0, 0), 1L);
        assertEquals(1.0, raining.rain()[0]);
        assertEquals(1.0, raining.rain()[1]);
        // A /weather clear for three days: no rain until it ends.
        Forecast.Chances cleared = Forecast.chances(new Forecast.Clocks(false, 0, false, 0, 72_000, 0), 1L);
        assertEquals(0.0, cleared.rain()[0]);
        assertEquals(0.0, cleared.rain()[1]);
        assertEquals(0.0, cleared.rain()[2]);
        assertTrue(cleared.rain()[6] > 0.0, "later, it may rain again");
        // Clear for sure until rain starts in a day and a half: today is dry, the day after is wet.
        Forecast.Chances later = Forecast.chances(new Forecast.Clocks(false, 36_000, false, 100_000, 0, 0), 1L);
        assertEquals(0.0, later.rain()[0]);
        assertEquals(1.0, later.rain()[1]);
    }

    @Test
    void theNoteSaysWhatIsKnownToday() {
        assertEquals("Rain from 15:00", Forecast.note(new Forecast.Clocks(false, 9_000, false, 0, 0, 0), 0, false, false));
        assertEquals("Snow until 12:00", Forecast.note(new Forecast.Clocks(true, 6_000, false, 0, 0, 0), 0, true, false));
        assertEquals("Rain all day", Forecast.note(new Forecast.Clocks(true, 30_000, false, 0, 0, 0), 0, false, false));
        assertEquals("Rain from 12:00", Forecast.note(new Forecast.Clocks(false, 30_000, false, 0, 0, 0), 1, false, false),
                "tomorrow, when the change falls then");
        assertEquals("", Forecast.note(new Forecast.Clocks(false, 9_000, false, 0, 0, 0), 2, false, false),
                "past tomorrow, nothing is certain");
        assertEquals("", Forecast.note(new Forecast.Clocks(false, 9_000, false, 0, 0, 0), 0, false, true),
                "a desert never rains");
        assertEquals("Clear all day", Forecast.note(new Forecast.Clocks(false, 0, false, 0, 30_000, 0), 0, false, false));
    }

    @Test
    void whereNobodyShowsEzvacsRainAndSnowTheForecastFollowsTheBiomes() {
        WindModel.Day calm = new WindModel.Day(0, WindModel.Level.CALM, 1.0);
        // A plains area on a freezing winter day: EZvac's air says snow, but Minecraft's plains rain.
        WeatherStation.Area coldPlains = new WeatherStation.Area(25.0, 10.0, 0.4, 1.0, 0.0, 10.0, 6.0, 10.0, 60.0, 1.0);
        assertEquals(Forecast.Sky.SNOW, Forecast.day(1, coldPlains, 0.0, 0.9, 0.0, calm, 5.0, "", WEATHER,
                Seasons.Season.WINTER, true).sky(), "shown by an addon: by the air");
        assertEquals(Forecast.Sky.RAIN, Forecast.day(1, coldPlains, 0.0, 0.9, 0.0, calm, 5.0, "", WEATHER,
                Seasons.Season.WINTER, false).sky(), "not shown: as players see it");
        // A snowy area on a mild day still snows in Minecraft.
        WeatherStation.Area mildTundra = new WeatherStation.Area(50.0, 40.0, 0.5, 0.0, 1.0, 10.0, 6.0, 10.0, 5.0, 1.0);
        assertEquals(Forecast.Sky.SNOW, Forecast.day(1, mildTundra, 0.0, 0.9, 0.0, calm, 5.0, "", WEATHER,
                Seasons.Season.SPRING, false).sky());
        // Deserts stay dry there: the service passes its rules with dry areas never reached.
        ClimateSettings.Weather unshown = WEATHER.withDryAreas(ClimateSettings.Weather.DryAreas.NEVER);
        Forecast.Day desert = Forecast.day(1, DESERT, 0.0, 0.9, 0.9, calm, 20.0, "", unshown, Seasons.Season.WINTER, false);
        assertEquals(Forecast.Sky.CLEAR, desert.sky());
        assertEquals(0.0, desert.rainChance());
        assertSame(WEATHER, WEATHER.withDryAreas(WEATHER.dryAreas()), "the same rule, the same settings");
        assertEquals(WEATHER.snowAtOrBelowF(), unshown.snowAtOrBelowF(), "everything else kept");
    }

    @Test
    void aDaysTemperaturesFollowItsSeasonAndItsRain() {
        WindModel.Day calm = new WindModel.Day(0, WindModel.Level.CALM, 1.0);
        Forecast.Day dry = Forecast.day(0, PLAINS, 0.0, 0.0, 0.0, calm, 60.0, "", WEATHER);
        assertEquals(80.0, dry.highF(), 1e-9);
        assertEquals(60.0, dry.lowF(), 1e-9);
        assertEquals(Forecast.Sky.CLEAR, dry.sky());
        Forecast.Day summer = Forecast.day(3, PLAINS, 8.0, 0.0, 0.0, calm, 60.0, "", WEATHER);
        assertEquals(88.0, summer.highF(), 1e-9, "a warmer season, a warmer day");
        Forecast.Day wet = Forecast.day(1, PLAINS, 0.0, 0.9, 0.0, calm, 60.0, "", WEATHER);
        assertEquals(Forecast.Sky.RAIN, wet.sky());
        assertTrue(wet.highF() < dry.highF(), "rain cools the day: " + wet.highF());
        assertEquals(Forecast.Sky.THUNDER, Forecast.day(1, PLAINS, 0.0, 0.9, 0.7, calm, 60.0, "", WEATHER).sky());
        assertEquals(Forecast.Sky.CLEAR, Forecast.day(1, PLAINS, 0.0, 0.3, 0.0, calm, 60.0, "", WEATHER).sky(),
                "a 30% chance is still a clear day");
        // BETA 1.0 Pilot 4: a desert gets the season's share of rain spells (weather.dry-areas: seasonal).
        Forecast.Day winterRain = Forecast.day(1, DESERT, 0.0, 0.9, 0.0, calm, 20.0, "", WEATHER, Seasons.Season.WINTER);
        assertEquals(0.54, winterRain.rainChance(), 1e-9, "60% of winter's rain reaches it");
        assertEquals(Forecast.Sky.RAIN, winterRain.sky());
        Forecast.Day summerRain = Forecast.day(1, DESERT, 0.0, 0.9, 0.9, calm, 20.0, "", WEATHER, Seasons.Season.SUMMER);
        assertEquals(0.045, summerRain.rainChance(), 1e-9, "and 5% of summer's");
        assertEquals(Forecast.Sky.CLEAR, summerRain.sky(), "a summer storm most likely passes it by");
        assertEquals(0.09, Forecast.day(1, DESERT, 0.0, 0.9, 0.0, calm, 20.0, "", WEATHER).rainChance(), 1e-9,
                "without seasons, spring's share");
        // thunder: rain only in thunderstorms.
        ClimateSettings.Weather thunder = withDryAreas(ClimateSettings.Weather.DryAreas.THUNDER);
        Forecast.Day storm = Forecast.day(1, DESERT, 0.0, 0.9, 0.9, calm, 20.0, "", thunder);
        assertEquals(Forecast.Sky.THUNDER, storm.sky(), "a desert thunderstorm");
        Forecast.Day showers = Forecast.day(1, DESERT, 0.0, 0.9, 0.0, calm, 20.0, "", thunder);
        assertEquals(Forecast.Sky.CLEAR, showers.sky(), "but ordinary rain passes it by");
        assertEquals(0.0, showers.rainChance());
        ClimateSettings.Weather never = withDryAreas(ClimateSettings.Weather.DryAreas.NEVER);
        assertEquals(Forecast.Sky.CLEAR, Forecast.day(1, DESERT, 0.0, 0.9, 0.9, calm, 20.0, "", never).sky(),
                "never: as vanilla");
        ClimateSettings.Weather always = withDryAreas(ClimateSettings.Weather.DryAreas.ALWAYS);
        assertEquals(Forecast.Sky.RAIN, Forecast.day(1, DESERT, 0.0, 0.9, 0.0, calm, 20.0, "", always).sky());
        WeatherStation.Area tundra = new WeatherStation.Area(30.0, 15.0, 0.5, 0.0, 1.0, 10.0, 6.0, 10.0, 5.0, 1.0);
        assertEquals(Forecast.Sky.SNOW, Forecast.day(1, tundra, 0.0, 0.8, 0.0, calm, 5.0, "", WEATHER).sky());
        assertEquals(Forecast.Sky.RAIN, Forecast.day(1, tundra, 30.0, 0.8, 0.0, calm, 5.0, "", WEATHER).sky(),
                "a warm spell on the tundra rains");
        assertEquals(Forecast.Sky.SNOW, Forecast.day(1, PLAINS, -60.0, 0.8, 0.0, calm, 5.0, "", WEATHER).sky(),
                "a cold winter's day on the plains snows");
        // A winter day's line is higher: 36 F rains in autumn but snows in winter.
        assertTrue(Forecast.snows(40.0, 32.0, WEATHER, true));
        assertFalse(Forecast.snows(40.0, 32.0, WEATHER, false));
        assertFalse(Forecast.snows(50.0, 40.0, WEATHER, true), "a mild winter day still rains");
    }

    private static ClimateSettings.Weather withDryAreas(ClimateSettings.Weather.DryAreas rule) {
        ClimateSettings.Weather w = WEATHER;
        return new ClimateSettings.Weather(w.enabled(), w.easeHours(), w.rainCoolingF(), w.thunderCoolingF(),
                w.snowCoolingF(), w.swingReduction(), w.waterShare(), w.windClearMs(), w.windRainMs(),
                w.windThunderMs(), w.snowAtOrBelowF(), rule, w.snowInWinterBelowF());
    }

    @Test
    void spellsAndOverlapsAddUp() {
        List<int[]> wet = List.of(new int[]{0, 100}, new int[]{200, 300});
        List<int[]> storm = List.of(new int[]{50, 250});
        assertEquals(100, Forecast.overlap(wet, 50, 250));
        List<int[]> both = Forecast.both(wet, storm);
        assertEquals(2, both.size());
        assertEquals(100, Forecast.overlap(both, 0, 1000));
    }
}
