package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.Sensor;
import com.github.blade.hvac.simulation.AirQualityModel.Air;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The daily wind (3.2): its levels, gusts, terrain, and what it does to rooms and the outdoor air. */
class WindModelTest {
    private static final WindSettings SETTINGS = WindSettings.defaults();
    private static final long SEED = 8_675_309L;
    private static final long DAY = WindModel.DAY_TICKS;

    @Test
    void theSameSeedAndDayAlwaysGiveTheSameWind() {
        for (long day = 0; day < 50; day++) {
            assertEquals(WindModel.day(SEED, day, 1.0, SETTINGS), WindModel.day(SEED, day, 1.0, SETTINGS));
            assertEquals(WindModel.speedMs(SEED, day * DAY + 7_000, WindModel.day(SEED, day, 1.0, SETTINGS),
                            WindModel.day(SEED, day - 1, 1.0, SETTINGS), SETTINGS),
                    WindModel.speedMs(SEED, day * DAY + 7_000, WindModel.day(SEED, day, 1.0, SETTINGS),
                            WindModel.day(SEED, day - 1, 1.0, SETTINGS), SETTINGS));
        }
        boolean differs = false;
        for (long day = 0; day < 50 && !differs; day++)
            differs = WindModel.level(SEED, day, 1.0, SETTINGS) != WindModel.level(SEED + 1, day, 1.0, SETTINGS);
        assertTrue(differs, "another world has other weather");
    }

    @Test
    void levelsComeUpAsOftenAsTheirChances() {
        Map<WindModel.Level, Integer> counts = new EnumMap<>(WindModel.Level.class);
        int days = 100_000;
        for (long day = 0; day < days; day++) counts.merge(WindModel.level(SEED, day, 1.0, SETTINGS), 1, Integer::sum);
        assertEquals(0.40, counts.get(WindModel.Level.CALM) / (double) days, 0.01);
        assertEquals(0.35, counts.get(WindModel.Level.BREEZY) / (double) days, 0.01);
        assertEquals(0.18, counts.get(WindModel.Level.WINDY) / (double) days, 0.01);
        assertEquals(0.07, counts.get(WindModel.Level.GALE) / (double) days, 0.01);
    }

    @Test
    void springAndAutumnAreWindierThanSummer() {
        int springGales = 0, summerGales = 0;
        for (long day = 0; day < 20_000; day++) {
            if (WindModel.level(SEED, day, SETTINGS.seasons().spring(), SETTINGS) == WindModel.Level.GALE) springGales++;
            if (WindModel.level(SEED, day, SETTINGS.seasons().summer(), SETTINGS) == WindModel.Level.GALE) summerGales++;
        }
        assertTrue(springGales > summerGales * 1.5, springGales + " spring gales, " + summerGales + " in summer");
        assertEquals(WindModel.Level.CALM, WindModel.level(SEED, 3, 0.0, new WindSettings(true,
                new WindSettings.Chances(1, 0, 1, 1), SETTINGS.speeds(), SETTINGS.seasons(), 0.25, 0.05, 2.0,
                1.5, 0.03, 0.06, WindSettings.Units.AUTO, Map.of())), "no windiness, no wind");
    }

    @Test
    void eachDaysSpeedIsInsideItsLevelsRange() {
        for (long day = 0; day < 2_000; day++) {
            WindModel.Day wind = WindModel.day(SEED, day, 1.0, SETTINGS);
            WindSettings.Range range = SETTINGS.speeds().of(wind.level());
            assertTrue(wind.meanMs() >= range.from() && wind.meanMs() <= range.to(), wind.toString());
            assertEquals(wind.level(), WindModel.Level.of(wind.meanMs(), SETTINGS.speeds()),
                    "a day's mean speed reads as its level");
        }
        WindModel.Day gale = WindModel.day(SEED, 9, WindModel.Level.GALE, SETTINGS);
        assertEquals(WindModel.Level.GALE, gale.level());
        assertTrue(gale.meanMs() >= 14 && gale.meanMs() <= 20);
    }

    @Test
    void gustsRiseAndFallWithoutStepsAndNeverGoBelowNothing() {
        WindModel.Day today = new WindModel.Day(5, WindModel.Level.WINDY, 10.0);
        double low = Double.MAX_VALUE, high = 0.0, previous = Double.NaN;
        for (long tick = 5 * DAY + 2_000; tick < 6 * DAY; tick += 10) {
            double speed = WindModel.speedMs(SEED, tick, today, null, SETTINGS);
            assertTrue(speed >= 0.0);
            if (Double.isFinite(previous)) assertEquals(previous, speed, 0.1, "no jump at tick " + tick);
            previous = speed;
            low = Math.min(low, speed);
            high = Math.max(high, speed);
        }
        assertTrue(low >= 10.0 * 0.75 - 1e-9 && high <= 10.0 * 1.25 + 1e-9, low + " .. " + high);
        assertTrue(high - low > 1.0, "it gusts");
        for (double hours = 0.0; hours < 48.0; hours += 0.25)
            assertTrue(Math.abs(WindModel.gust(SEED, hours)) <= 1.0);
    }

    @Test
    void aNewDaysWindEasesOverFromYesterdaysAtDawn() {
        WindSettings still = new WindSettings(true, SETTINGS.chances(), SETTINGS.speeds(), SETTINGS.seasons(), 0.0,
                0.05, 2.0, 1.5, 0.03, 0.06, WindSettings.Units.AUTO, Map.of());
        WindModel.Day yesterday = new WindModel.Day(6, WindModel.Level.CALM, 1.0);
        WindModel.Day today = new WindModel.Day(7, WindModel.Level.GALE, 17.0);
        assertEquals(1.0, WindModel.speedMs(SEED, 7 * DAY - 1, yesterday,
                new WindModel.Day(5, WindModel.Level.CALM, 1.0), still), 1e-9, "the night before");
        assertEquals(1.0, WindModel.speedMs(SEED, 7 * DAY, today, yesterday, still), 1e-9, "dawn: still yesterday's");
        assertEquals(9.0, WindModel.speedMs(SEED, 7 * DAY + 1_000, today, yesterday, still), 1e-9, "halfway");
        assertEquals(17.0, WindModel.speedMs(SEED, 7 * DAY + 2_000, today, yesterday, still), 1e-9, "today's");
    }

    @Test
    void terrainChangesTheWind() {
        assertEquals(1.0, WindModel.exposure(1.0, 63, 63, 0.0), 1e-12, "open ground at sea level");
        assertEquals(1.5, WindModel.exposure(1.0, 63 + 60, 63, 0.0), 1e-12, "60 blocks up");
        assertEquals(1.5, WindModel.exposure(1.0, 300, 63, 0.0), 1e-12, "capped with height");
        assertEquals(WindModel.MAXIMUM_EXPOSURE, WindModel.exposure(1.4, 300, 63, 0.0), 1e-12, "a mountain top");
        assertEquals(0.5, WindModel.exposure(0.5, 40, 63, 0.0), 1e-12, "a jungle floor, no stronger down low");
        assertEquals(0.0, WindModel.exposure(1.0, 20, 63, 1.0), 1e-12, "underground");
        assertEquals(1.4, WindModel.builtInExposure("minecraft:jagged_peaks"));
        assertEquals(0.5, WindModel.builtInExposure("dark_forest"));
        assertEquals(0.0, WindModel.builtInExposure("minecraft:nether_wastes"));
        assertNull(WindModel.builtInExposure("my_pack:ash_wastes"));
        assertEquals(1.0, SETTINGS.exposure("my_pack:ash_wastes"));
    }

    @Test
    void blendingCarriesTheExposure() {
        BiomeBlend.Blended open = BiomeBlend.blend(List.of(
                new BiomeBlend.Sample(70, 50, 0.4, Precipitation.RAIN, 1.0, 10, null, 1.15),
                new BiomeBlend.Sample(70, 50, 0.4, Precipitation.RAIN, 1.0, 10, null, 0.5)));
        assertEquals(0.825, open.windExposure(), 1e-12, "half plains, half jungle");
        assertEquals(0.825, open.warmer(5).windExposure(), 1e-12);
        assertEquals(0.0, BiomeClimate.underground(open, 1.0).windExposure(), 1e-12);
        assertEquals(1.0, new BiomeBlend.Blended(70, 50, 0.4, 1, 0, 10).windExposure(), "older callers: fully open");
    }

    @Test
    void stormsAddToTheDaysWind() {
        ClimateSettings.Weather weather = ClimateSettings.defaults().weather();
        WeatherModel.Conditions storm = WeatherModel.Conditions.settled(true, true);
        assertEquals(weather.windThunderMs(), WeatherModel.effect(Precipitation.RAIN, storm, weather).windMs(), 1e-12,
                "without daily wind, as in 3.1");
        assertEquals(12.0 + weather.windThunderMs() - weather.windClearMs(),
                WeatherModel.effect(Precipitation.RAIN, storm, weather, 12.0).windMs(), 1e-12);
        assertEquals(12.0, WeatherModel.effect(Precipitation.NONE, storm, weather, 12.0).windMs(), 1e-12,
                "a desert has wind, even where it never rains");
        assertEquals(6.0, WeatherModel.effect(Precipitation.NONE, storm, weather, 12.0).windScaled(0.5).windMs(),
                1e-12);
    }

    @Test
    void windMakesRoomsLeakier() {
        HvacSettings settings = HvacSettings.defaults();
        double still = ThermalModel.step(70, 70, 30, OperatingMode.IDLE, 0, 1_000, settings).temperatureF();
        assertEquals(still, ThermalModel.step(70, 70, 30, OperatingMode.IDLE, 0, 1_000, settings, 1.0)
                .temperatureF(), 1e-12, "still air is as before");
        double gale = ThermalModel.step(70, 70, 30, OperatingMode.IDLE, 0, 1_000, settings, 2.0).temperatureF();
        assertEquals(2.0 * (70 - still), 70 - gale, 1e-9, "twice as leaky, twice as fast toward the outdoors");
        double split = 70;
        for (int i = 0; i < 4; i++)
            split = ThermalModel.step(split, 70, 30, OperatingMode.IDLE, 0, 250, settings, 2.0).temperatureF();
        assertEquals(gale, split, 1e-9, "splitting a step changes nothing");

        ClimateSettings climate = ClimateSettings.defaults();
        double calmDew = HumidityModel.step(60, 70, 30, HumidityModel.Loads.NONE, 1_000, climate).dewPointF();
        double windyDew = HumidityModel.step(60, 70, 30, HumidityModel.Loads.NONE, 1_000, climate, 2.0).dewPointF();
        assertTrue(60 - windyDew > 60 - calmDew, "the outdoor air's moisture comes in faster");

        assertEquals(1.0, SETTINGS.infiltrationFactor(0.0));
        assertEquals(1.5, SETTINGS.infiltrationFactor(10.0), 1e-12);
        assertEquals(2.0, SETTINGS.infiltrationFactor(40.0), 1e-12, "capped");
        WindSettings off = new WindSettings(false, SETTINGS.chances(), SETTINGS.speeds(), SETTINGS.seasons(), 0.25,
                0.05, 2.0, 1.5, 0.03, 0.06, WindSettings.Units.AUTO, Map.of());
        assertEquals(1.0, off.infiltrationFactor(20.0), "wind off changes nothing");
    }

    @Test
    void aDesertGaleIsADustStorm() {
        OutdoorAir.Baseline desert = OutdoorAir.builtIn("desert");
        Air still = OutdoorAir.now(desert, 0.6, 0.0, 0.0, 0.0, SETTINGS);
        assertEquals(OutdoorAir.now(desert, 0.6, 0.0), still);
        int windy = AirQualityIndex.aqi(OutdoorAir.now(desert, 0.6, 0.0, 11.5, 0.0, SETTINGS));
        int gale = AirQualityIndex.aqi(OutdoorAir.now(desert, 0.6, 0.0, 20.0, 0.0, SETTINGS));
        assertTrue(windy <= 100, "a windy desert day is Moderate: " + windy);
        assertTrue(gale > 100 && gale <= 200, "a desert gale: " + gale);
        assertEquals(AirQualityIndex.Pollutant.PM10, AirQualityIndex.dominant(
                OutdoorAir.now(desert, 0.6, 0.0, 20.0, 0.0, SETTINGS)), "dust");
        assertTrue(AirQualityIndex.aqi(OutdoorAir.now(desert, 0.6, 0.0, 40.0, 0.0, SETTINGS)) <= 200,
                "even a hurricane has its limit");
        assertTrue(AirQualityIndex.aqi(OutdoorAir.now(desert, 0.6, 1.0, 20.0, 0.0, SETTINGS)) < gale,
                "rain lays the dust");

        OutdoorAir.Baseline jungle = OutdoorAir.builtIn("jungle");
        Air jungleGale = OutdoorAir.now(jungle, 0.6, 0.0, 20.0, 0.9, SETTINGS);
        assertTrue(jungleGale.coarse() < jungle.coarse() * 1.5, "wet ground gives up little dust");
        assertTrue(jungleGale.fine() < jungle.fine(), "the wind thins the smoke");
        assertEquals(jungle.fine() * OutdoorAir.MINIMUM_SMOKE_LEFT,
                OutdoorAir.now(jungle, 0.6, 0.0, 40.0, 0.9, SETTINGS).fine(), 1e-9);
    }

    @Test
    void windSpreadsPollenButMakesNoneInWinter() {
        OutdoorAir.Baseline plains = OutdoorAir.builtIn("plains");
        double still = OutdoorAir.now(plains, 1.0, 0.0, 0.0, 0.4, SETTINGS).pollen();
        assertEquals(still * 1.6, OutdoorAir.now(plains, 1.0, 0.0, 10.0, 0.4, SETTINGS).pollen(), 1e-9);
        assertEquals(still * OutdoorAir.MAXIMUM_POLLEN_SPREAD,
                OutdoorAir.now(plains, 1.0, 0.0, 30.0, 0.4, SETTINGS).pollen(), 1e-9);
        assertEquals(0.0, OutdoorAir.now(plains, 0.0, 0.0, 20.0, 0.4, SETTINGS).pollen(), "winter has none to spread");
    }

    @Test
    void aBambooButtonIsAWindSensor() {
        assertEquals(Sensor.Kind.WIND, Sensor.forButton(Material.BAMBOO_BUTTON));
        assertEquals(Material.BAMBOO_BUTTON, Sensor.button(Sensor.Kind.WIND));
        assertNull(Sensor.forButton(Material.LIGHTNING_ROD), "every sensor is a button");
    }

    @Test
    void settingsLoadAndFallBackSectionBySection() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("wind.enabled", false);
        config.set("wind.chances.gale", -1);
        config.set("wind.speeds-ms.breezy", List.of(4, 8));
        config.set("wind.units", "kmh");
        config.set("wind.biomes.minecraft:plains", 1.2);
        config.set("wind.biomes.minecraft:desert", "lots");
        List<String> warnings = new ArrayList<>();
        WindSettings settings = WindSettings.load(config, warnings::add);
        assertFalse(settings.enabled());
        assertEquals(SETTINGS.chances(), settings.chances(), "a bad value falls back to its section's defaults");
        assertEquals(new WindSettings.Range(4, 8), settings.speeds().breezy());
        assertEquals(WindSettings.Units.KMH, settings.units());
        assertEquals(1.2, settings.exposure("minecraft:plains"));
        assertEquals(1.15, settings.exposure("minecraft:desert"), "a broken override keeps EZvac's");
        assertEquals(2, warnings.size(), warnings.toString());
        config.set("wind.speeds-ms.gale", List.of(20, 10));
        config.set("wind.gusts", 5);
        warnings.clear();
        WindSettings broken = WindSettings.load(config, warnings::add);
        assertEquals(SETTINGS.speeds(), broken.speeds());
        assertEquals(SETTINGS.gusts(), broken.gusts());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("wind.gusts")), warnings.toString());
        assertEquals(SETTINGS, WindSettings.load(new YamlConfiguration(), warning -> fail(warning)));
    }
}
