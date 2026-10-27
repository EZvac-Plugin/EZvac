package com.github.blade.hvac.service;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.simulation.AirQualityModel.Air;
import com.github.blade.hvac.simulation.ThermalModel;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Standalone spaces share their air, purifiers share what they catch, and what burns makes smoke (3.2). */
class AirSpaceTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final long HOUR = (long) ThermalModel.MINECRAFT_TICKS_PER_HOUR;
    private static final AirQualitySettings SETTINGS = AirQualitySettings.defaults();
    private static final Air OUTDOOR = new Air(8, 12, 60);

    private static AirPurifier purifier(int x) {
        return new AirPurifier(new BlockKey(WORLD, x + 1, 65, 0), new BlockKey(WORLD, x, 65, 0),
                new BlockKey(WORLD, x, 64, 0), "air", AirPurifier.Mode.HIGH);
    }

    private static Dehumidifier dehumidifier(int x) {
        return new Dehumidifier(new BlockKey(WORLD, x + 1, 65, 0), new BlockKey(WORLD, x, 65, 0),
                new BlockKey(WORLD, x, 64, 0), "dry", 45);
    }

    private static Air step(List<ClimateUnit> space, Air sources, long ticks, List<AirPurifier> filled) {
        return ClimateUnitService.stepSharedAir(space, SETTINGS.referenceCells(), sources, OUTDOOR, ticks, 0.15,
                SETTINGS, filled);
    }

    @Test
    void unitsSharingASpaceShareItsAir() {
        Dehumidifier dry = dehumidifier(0);
        AirPurifier clean = purifier(10);
        Air air = step(List.of(dry, clean), new Air(20, 8, 0), 2 * HOUR, new ArrayList<>());
        assertEquals(air, dry.zoneAir());
        assertEquals(air, clean.zoneAir());
        assertTrue(air.fine() > OUTDOOR.fine(), "a campfire in a shed with nothing cleaning it");
    }

    @Test
    void runningPurifiersCleanTheSpaceAndShareWhatTheyCaught() {
        AirPurifier one = purifier(0);
        AirPurifier two = purifier(10);
        List<ClimateUnit> space = List.of(one, two);
        step(space, new Air(20, 8, 0), 24 * HOUR, new ArrayList<>());
        Air smoky = one.zoneAir();
        one.control(smoky, SETTINGS);
        two.control(smoky, SETTINGS);
        assertTrue(one.running() && two.running());
        Air cleaned = step(space, new Air(20, 8, 0), 2 * HOUR, new ArrayList<>());
        assertTrue(cleaned.fine() < smoky.fine() / 3, smoky + " -> " + cleaned);
        assertEquals(one.filter(), two.filter(), "they share what they caught");
        assertTrue(one.filter() < 1.0);
    }

    @Test
    void aFilterThatFillsIsReported() {
        AirPurifier purifier = purifier(0);
        purifier.setZoneAir(new Air(4_000, 4_000, 0));
        purifier.control(purifier.zoneAir(), SETTINGS);
        List<AirPurifier> filled = new ArrayList<>();
        // Hours of a bonfire: the filter fills.
        step(List.of(purifier), new Air(4_000, 4_000, 0), 48 * HOUR, filled);
        assertEquals(List.of(purifier), filled);
        assertEquals(ClimateUnit.State.FILTER_SPENT, purifier.state());
    }

    @Test
    void aFasterFanCatchesMoreAndFillsFaster() {
        AirPurifier high = purifier(0);
        AirPurifier low = purifier(10);
        low.setMode(AirPurifier.Mode.LOW);
        ClimateUnitService.wear(List.of(high, low), new Air(100, 0, 0), 1_500, HOUR, SETTINGS, new ArrayList<>());
        assertEquals((1.0 - high.filter()) / (1.0 - low.filter()), 1.0 / 0.35, 0.05,
                "each fills by its share of the cleaning");
    }

    @Test
    void largerSpacesWearFiltersFaster() {
        AirPurifier small = purifier(0);
        AirPurifier large = purifier(10);
        ClimateUnitService.wear(List.of(small), new Air(10, 0, 0), 1_500, HOUR, SETTINGS, new ArrayList<>());
        ClimateUnitService.wear(List.of(large), new Air(10, 0, 0), 6_000, HOUR, SETTINGS, new ArrayList<>());
        assertTrue(large.filter() < small.filter(), "the same air in four times the space is four times the dirt");
    }

    @Test
    void sourcesAreDilutedBySpaceSize() {
        assertEquals(1.0, AirQualityService.dilution(1_500, 1_500), 1e-12);
        assertEquals(0.5, AirQualityService.dilution(3_000, 1_500), 1e-12);
        assertEquals(4.0, AirQualityService.dilution(10, 1_500), 1e-12, "at most four times in a closet");
    }

    @Test
    void whatBurnsIsASource() {
        assertEquals(SmokeSource.CAMPFIRE, SmokeSource.of(Material.CAMPFIRE));
        assertEquals(SmokeSource.CAMPFIRE, SmokeSource.of(Material.SOUL_CAMPFIRE));
        assertEquals(SmokeSource.FIRE, SmokeSource.of(Material.SOUL_FIRE));
        assertEquals(SmokeSource.LAVA, SmokeSource.of(Material.LAVA));
        assertEquals(SmokeSource.FURNACE, SmokeSource.of(Material.BLAST_FURNACE));
        assertEquals(SmokeSource.CANDLE, SmokeSource.of(Material.CANDLE));
        assertEquals(SmokeSource.CANDLE, SmokeSource.of(Material.RED_CANDLE));
        assertEquals(SmokeSource.CANDLE, SmokeSource.of(Material.RED_CANDLE_CAKE));
        assertNull(SmokeSource.of(Material.TORCH), "torches would make every room smoky");
        assertNull(SmokeSource.of(Material.DISPENSER), "EZvac's own furnaces are sealed");
        assertNull(SmokeSource.of(Material.STONE));
        assertTrue(SmokeSource.FIRE.burning(null), "fire always burns");
        assertTrue(SmokeSource.CAMPFIRE.burning(lightable(true)));
        assertFalse(SmokeSource.CAMPFIRE.burning(lightable(false)), "a doused campfire makes no smoke");
        assertEquals(SETTINGS.sources().campfire(), SmokeSource.CAMPFIRE.emission(SETTINGS.sources()));
    }

    private static BlockData lightable(boolean lit) {
        return (BlockData) Proxy.newProxyInstance(AirSpaceTest.class.getClassLoader(),
                new Class<?>[]{Lightable.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isLit" -> lit;
                    case "hashCode" -> 0;
                    case "equals" -> false;
                    default -> null;
                });
    }

    @Test
    void settingsLoadAndFallBackSectionBySection() {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("air-quality.enabled", false);
        config.set("air-quality.settling.fine", -1);
        config.set("air-quality.purifier.auto.high-pollen", "never");
        config.set("air-quality.purifier.speeds.low", 0.5);
        config.set("air-quality.pollen-levels.high", 10);
        List<String> warnings = new ArrayList<>();
        AirQualitySettings settings = AirQualitySettings.load(config, warnings::add);
        assertFalse(settings.enabled());
        assertEquals(SETTINGS.settling(), settings.settling(), "a bad value falls back to its section's defaults");
        assertEquals(SETTINGS.pollenLevels(), settings.pollenLevels());
        assertNull(settings.purifier().auto().highPollen());
        assertEquals(0.5, settings.purifier().speeds().low());
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("air-quality.settling.fine"), warnings.get(0));
        config.set("air-quality.purifier.auto.high-pollen", "sometimes");
        warnings.clear();
        assertEquals(SETTINGS.purifier(), AirQualitySettings.load(config, warnings::add).purifier());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("high-pollen")), warnings.toString());
        assertEquals(Map.of(), settings.biomes());
    }
}
