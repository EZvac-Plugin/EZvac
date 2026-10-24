package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Each biome's own climate, altitude and the ground (2.1 Pilot 2). */
class BiomeClimateTest {
    private static BiomeClimate.Climate of(String id) {
        return BiomeClimate.builtIn("minecraft:" + id);
    }

    private static double swing(BiomeClimate.Climate climate) {
        return climate.noonF() - climate.midnightF();
    }

    @Test
    void everyVanillaBiomeHasItsOwnClimate() {
        for (Biome biome : Biome.values()) {
            if (biome == Biome.CUSTOM) continue;
            assertNotNull(BiomeClimate.builtIn(biome.getKey().toString()), biome.getKey().toString());
        }
    }

    @Test
    void dryLandsSwingWidelyAndWaterBarelyMoves() {
        assertTrue(swing(of("desert")) > 30, "a desert's clear nights get cold");
        assertTrue(swing(of("desert")) > 2 * swing(of("jungle")));
        assertTrue(swing(of("ocean")) < 10);
        assertEquals(Precipitation.NONE, of("desert").precipitation());
        assertEquals(Precipitation.SNOW, of("snowy_plains").precipitation());
    }

    @Test
    void theSeasonsAreStrongInTheNorthAndWeakInTheTropics() {
        assertTrue(of("taiga").seasonalF() > of("plains").seasonalF());
        assertTrue(of("plains").seasonalF() > 4 * of("jungle").seasonalF());
        assertEquals(0.0, of("deep_dark").seasonalF(), "caves are the same all year");
        assertEquals(0.0, of("nether_wastes").seasonalF());
        // Plains noon: 90 F in mid-summer, 54 F in mid-winter.
        assertEquals(90.0, of("plains").noonF() + of("plains").seasonalF());
        assertEquals(54.0, of("plains").noonF() - of("plains").seasonalF());
    }

    @Test
    void namesWithoutANamespaceAreVanillaOnes() {
        assertSame(of("plains"), BiomeClimate.builtIn("PLAINS"));
        assertNull(BiomeClimate.builtIn("mypack:volcano"));
    }

    @Test
    void aDataPackBiomeIsEstimatedFromWhatMinecraftSaysAboutIt() {
        BiomeClimate.Climate plainsLike = BiomeClimate.estimate(0.8, 0.4);
        assertEquals(61.0, (plainsLike.noonF() + plainsLike.midnightF()) / 2.0, 0.01);
        assertEquals(Precipitation.RAIN, plainsLike.precipitation());
        BiomeClimate.Climate dry = BiomeClimate.estimate(2.0, 0.0);
        assertEquals(Precipitation.NONE, dry.precipitation());
        assertTrue(swing(dry) > swing(BiomeClimate.estimate(0.95, 0.9)), "dry air swings more than humid air");
        assertEquals(Precipitation.SNOW, BiomeClimate.estimate(-0.5, 0.4).precipitation());
        assertTrue(BiomeClimate.estimate(-0.5, 0.4).seasonalF() > dry.seasonalF());
    }

    @Test
    void itGetsColderWithHeight() {
        assertEquals(0.0, BiomeClimate.lapseF(64, 64, 0.1));
        assertEquals(0.0, BiomeClimate.lapseF(20, 64, 0.1), "no warming below the reference");
        assertEquals(6.4, BiomeClimate.lapseF(128, 64, 0.1), 1e-9);
        assertEquals(13.6, BiomeClimate.lapseF(200, 64, 0.1), 1e-9);
    }

    @Test
    void theGroundTakesOverWithDepth() {
        assertEquals(0.0, BiomeClimate.groundFactor(-3, 4, 20), "above ground");
        assertEquals(0.0, BiomeClimate.groundFactor(4, 4, 20));
        assertEquals(1.0, BiomeClimate.groundFactor(20, 4, 20));
        assertEquals(1.0, BiomeClimate.groundFactor(80, 4, 20));
        double previous = 0.0;
        for (int depth = 5; depth < 20; depth++) {
            double factor = BiomeClimate.groundFactor(depth, 4, 20);
            assertTrue(factor > previous && factor < 1.0, "depth " + depth);
            previous = factor;
        }
    }

    @Test
    void deepDownThereIsNoSwingNoSeasonAndNoWeather() {
        BiomeBlend.Blended plains = BiomeBlend.Blended.of(72, 50, 0.4, Precipitation.RAIN, 18);
        BiomeBlend.Blended mine = BiomeClimate.underground(plains, 1.0);
        assertEquals(61.0, mine.noonF());
        assertEquals(61.0, mine.midnightF());
        assertEquals(0.0, mine.seasonalF());
        assertEquals(0.0, mine.wetShare());
        assertSame(plains, BiomeClimate.underground(plains, 0.0));
        BiomeBlend.Blended basement = BiomeClimate.underground(plains, 0.5);
        assertEquals(66.5, basement.noonF(), 1e-9);
        assertEquals(9.0, basement.seasonalF(), 1e-9);
        assertEquals(0.5, basement.rainShare(), 1e-9);
    }

    @Test
    void theServerCanGiveABiomeItsOwnClimate() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("biomes.minecraft:plains.seasonal-f", 10.0);
        config.set("biomes.mypack:volcano.noon-f", 120.0);
        config.set("biomes.mypack:volcano.midnight-f", 100.0);
        config.set("biomes.mypack:volcano.precipitation", "none");
        config.set("biomes.minecraft:desert.midnight-f", 200.0);     // warmer than its noon
        config.set("biomes.mypack:swamp.seasonal-f", 5.0);            // unknown, and no temperatures
        config.set("biomes.minecraft:taiga", 7);                      // not a section
        List<String> warnings = new ArrayList<>();
        ClimateSettings.Biomes biomes = ClimateSettings.load(config, warnings::add).biomes();

        BiomeClimate.Climate plains = biomes.climate("minecraft:plains");
        assertEquals(10.0, plains.seasonalF());
        assertEquals(72.0, plains.noonF(), "what was left out stays built in");
        assertEquals(new BiomeClimate.Climate(120, 100, 0, Precipitation.NONE).noonF(),
                biomes.climate("mypack:volcano").noonF());
        assertEquals(Precipitation.NONE, biomes.climate("mypack:volcano").precipitation());
        assertEquals(of("desert"), biomes.climate("minecraft:desert"), "a broken entry keeps the built-in climate");
        assertNull(biomes.climate("mypack:swamp"));
        assertEquals(of("taiga"), biomes.climate("taiga"));
        assertEquals(3, warnings.size(), warnings::toString);
        assertTrue(warnings.stream().allMatch(warning -> warning.startsWith("biomes.")), warnings::toString);
    }
}
