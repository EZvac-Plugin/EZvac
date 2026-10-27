package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.simulation.AirQualityModel.Air;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The outdoor air (3.2): biomes, the seasons' pollen, rain and blending. */
class OutdoorAirTest {
    private static final int DAYS = 15;
    private static final long DAY = Seasons.TICKS_PER_DAY;

    @Test
    void biomesHaveTheirOwnAir() {
        OutdoorAir.Baseline desert = OutdoorAir.builtIn("minecraft:desert");
        assertTrue(desert.coarse() > desert.fine() && desert.pollen() < 20, "a desert is dusty, not pollen-heavy");
        OutdoorAir.Baseline flowers = OutdoorAir.builtIn("flower_forest");
        assertTrue(flowers.pollen() > OutdoorAir.builtIn("plains").pollen());
        assertEquals(0.0, OutdoorAir.builtIn("basalt_deltas").pollen());
        assertTrue(AirQualityIndex.aqi(OutdoorAir.now(OutdoorAir.builtIn("basalt_deltas"), 0, 0)) > 300,
                "the basalt deltas are hazardous");
        assertTrue(AirQualityIndex.aqi(OutdoorAir.now(OutdoorAir.builtIn("the_end"), 0, 0)) < 10);
        assertNull(OutdoorAir.builtIn("my_pack:ash_wastes"));
        assertEquals(OutdoorAir.Baseline.DEFAULT, AirQualitySettings.defaults().baseline("my_pack:ash_wastes"));
        for (String biome : List.of("plains", "forest", "desert", "ocean", "meadow", "cherry_grove"))
            assertTrue(AirQualityIndex.aqi(OutdoorAir.now(OutdoorAir.builtIn(biome), 1.0, 0.0)) <= 100,
                    "the overworld's air is fine to breathe: " + biome);
    }

    @Test
    void pollenPeaksInSpringAndEasesAcrossTheSeasons() {
        long year = Seasons.yearTicks(DAYS);
        long middle = DAYS * DAY / 2;
        assertEquals(1.0, OutdoorAir.pollenSeasonFactor(middle, DAYS), 1e-9, "mid-spring");
        assertEquals(0.6, OutdoorAir.pollenSeasonFactor(DAYS * DAY + middle, DAYS), 1e-9, "mid-summer");
        assertEquals(0.25, OutdoorAir.pollenSeasonFactor(2 * DAYS * DAY + middle, DAYS), 1e-9, "mid-autumn");
        assertEquals(0.0, OutdoorAir.pollenSeasonFactor(3 * DAYS * DAY + middle, DAYS), 1e-9, "mid-winter");
        assertEquals(0.8, OutdoorAir.pollenSeasonFactor(DAYS * DAY, DAYS), 1e-9, "summer's first day is between");
        assertEquals(0.5, OutdoorAir.pollenSeasonFactor(year, DAYS), 1e-9, "spring's first day, a year on");
        double previous = OutdoorAir.pollenSeasonFactor(0, DAYS);
        for (long tick = DAY / 10; tick < year; tick += DAY / 10) {
            double now = OutdoorAir.pollenSeasonFactor(tick, DAYS);
            assertTrue(Math.abs(now - previous) < 0.02, "no jumps at " + tick);
            previous = now;
        }
    }

    @Test
    void rainWashesTheAirClean() {
        OutdoorAir.Baseline plains = new OutdoorAir.Baseline(10, 20, 100);
        assertEquals(new Air(10, 20, 100), OutdoorAir.now(plains, 1.0, 0.0));
        Air rain = OutdoorAir.now(plains, 1.0, 1.0);
        assertEquals(4.0, rain.fine(), 1e-9);
        assertEquals(8.0, rain.coarse(), 1e-9);
        assertEquals(15.0, rain.pollen(), 1e-9);
        assertEquals(0.0, OutdoorAir.now(plains, 0.0, 0.0).pollen(), "no pollen in mid-winter");
    }

    @Test
    void undergroundThePollenFades() {
        OutdoorAir.Baseline plains = new OutdoorAir.Baseline(10, 20, 100);
        assertEquals(new OutdoorAir.Baseline(10, 20, 50), plains.underground(0.5));
        BiomeBlend.Blended deep = BiomeClimate.underground(BiomeBlend.Blended.of(80, 60, 0.4,
                Precipitation.RAIN, 10, plains), 1.0);
        assertEquals(new OutdoorAir.Baseline(10, 20, 0), deep.air());
    }

    @Test
    void bordersBlendTheAirLikeTheTemperature() {
        OutdoorAir.Baseline desert = OutdoorAir.builtIn("desert");
        OutdoorAir.Baseline plains = OutdoorAir.builtIn("plains");
        BiomeBlend.Blended border = BiomeBlend.blend(List.of(
                new BiomeBlend.Sample(90, 60, 0, Precipitation.NONE, 1.0, 10, desert),
                new BiomeBlend.Sample(75, 55, 0.4, Precipitation.RAIN, 1.0, 10, plains)));
        assertEquals((desert.coarse() + plains.coarse()) / 2, border.air().coarse(), 1e-9);
        assertEquals((desert.pollen() + plains.pollen()) / 2, border.air().pollen(), 1e-9);
        BiomeBlend.Blended inside = BiomeBlend.blend(List.of(
                new BiomeBlend.Sample(90, 60, 0, Precipitation.NONE, 1.0, 10, desert),
                new BiomeBlend.Sample(90, 60, 0, Precipitation.NONE, 0.5, 10, desert)));
        assertEquals(desert, inside.air(), "inside one biome it is that biome's, exactly");
        BiomeBlend.Blended halfway = BiomeBlend.lerp(border, inside, 0.5);
        assertEquals((border.air().fine() + desert.fine()) / 2, halfway.air().fine(), 1e-9);
        assertEquals(OutdoorAir.Baseline.DEFAULT, BiomeBlend.Blended.of(70, 50, 0.4, Precipitation.RAIN).air(),
                "a climate from before 3.2 has the default air");
        assertEquals(desert, BiomeBlend.Blended.of(90, 60, 0, Precipitation.NONE, 10, desert).warmer(5).air());
    }

    @Test
    void serversCanSetTheirOwnBiomeAir() {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("air-quality.biomes.minecraft:plains.pollen", 40);
        config.set("air-quality.biomes.my_pack:ash.fine", 150);
        config.set("air-quality.biomes.bad.fine", -1);
        List<String> warnings = new java.util.ArrayList<>();
        AirQualitySettings settings = AirQualitySettings.load(config, warnings::add);
        OutdoorAir.Baseline plains = settings.baseline("plains");
        assertEquals(40, plains.pollen());
        assertEquals(OutdoorAir.builtIn("plains").coarse(), plains.coarse(), "what is left out stays EZvac's");
        assertEquals(150, settings.baseline("my_pack:ash").fine());
        assertEquals(OutdoorAir.Baseline.DEFAULT.coarse(), settings.baseline("my_pack:ash").coarse());
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("air-quality.biomes.bad"), warnings.get(0));
        assertEquals(Map.of(), AirQualitySettings.defaults().biomes());
    }
}
