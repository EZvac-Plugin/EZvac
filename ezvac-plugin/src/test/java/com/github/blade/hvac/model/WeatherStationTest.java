package com.github.blade.hvac.model;

import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.HvacController;
import com.github.blade.hvac.service.WeatherStationService;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WeatherStationTest {
    private static final BlockKey SIGN = new BlockKey(UUID.randomUUID(), 10, 64, 20);

    @Test
    void itSavesAndLoadsWhatItKnows() {
        WeatherStation station = new WeatherStation(SIGN, "Harbour");
        assertTrue(station.enabled());
        Map<String, Object> fresh = station.toMap();
        assertFalse(fresh.containsKey("enabled") || fresh.containsKey("strikes") || fresh.containsKey("area"),
                "a new station saves only where it is and its label");
        station.setEnabled(false);
        station.struck();
        station.struck();
        station.setArea(new WeatherStation.Area(80.0, 60.0, 0.4, 1.0, 0.0, 10.0, 6.0, 10.0, 60.0, 0.8));
        WeatherStation loaded = WeatherStation.fromMap(station.toMap());
        assertEquals(SIGN, loaded.position());
        assertEquals("Harbour", loaded.label());
        assertFalse(loaded.enabled());
        assertEquals(2, loaded.strikes());
        assertEquals(station.area(), loaded.area());
        assertEquals("Pier", loaded.renamed("Pier").label());
        assertEquals(2, loaded.renamed("Pier").strikes());
    }

    @Test
    void itsAreaSurvivesHvacYml() throws Exception {
        // The registry reads each device back as its section's values: a nested map is then a section.
        WeatherStation station = new WeatherStation(SIGN, "Harbour");
        station.setArea(new WeatherStation.Area(80.0, 60.0, 0.4, 1.0, 0.0, 10.0, 6.0, 10.0, 60.0, 0.8));
        org.bukkit.configuration.file.YamlConfiguration saved = new org.bukkit.configuration.file.YamlConfiguration();
        saved.set("weatherStations.item0", station.toMap());
        org.bukkit.configuration.file.YamlConfiguration loaded = new org.bukkit.configuration.file.YamlConfiguration();
        loaded.loadFromString(saved.saveToString());
        Map<String, Object> values = new java.util.LinkedHashMap<>(
                loaded.getConfigurationSection("weatherStations.item0").getValues(false));
        assertEquals(station.area(), WeatherStation.fromMap(values).area());
    }

    @Test
    void aBrokenAreaIsMeasuredAgainRatherThanFailingTheLoad() {
        Map<String, Object> map = new java.util.LinkedHashMap<>(new WeatherStation(SIGN, "Harbour").toMap());
        map.put("area", Map.of("noonF", "warm"));
        assertNull(WeatherStation.fromMap(map).area());
    }

    @Test
    void theTowerStandsBehindTheSignWithRodsRightLeftRight() {
        // A sign facing south (+z): its cut copper is just north of it, and someone reading it looks north.
        List<StationShape.Block> blocks = StationShape.blocks(0, 64, 0, 0, 1, false);
        assertEquals(13, blocks.size());
        assertTrue(blocks.contains(new StationShape.Block(0, 64, -1, StationShape.Part.CUT_COPPER, 0, 0)));
        assertTrue(blocks.contains(new StationShape.Block(0, 62, -1, StationShape.Part.IRON, 0, 0)), "the cross's centre");
        assertEquals(4, blocks.stream().filter(b -> b.part() == StationShape.Part.COPPER && b.y() == 62).count());
        assertEquals(3, blocks.stream().filter(b -> b.part() == StationShape.Part.IRON && b.y() > 64).count());
        List<StationShape.Block> rods = blocks.stream().filter(b -> b.part() == StationShape.Part.ROD).toList();
        assertEquals(List.of(65, 66, 67), rods.stream().map(StationShape.Block::y).toList());
        // Looking north, east (+x) is on the right.
        assertEquals(1, rods.get(0).x(), "the lowest rod on the right: east, for someone looking north");
        assertEquals(-1, rods.get(1).x(), "the middle one on the left");
        assertEquals(1, rods.get(2).x());
        assertEquals(1, rods.get(0).faceX(), "pointing out from the iron");
        List<StationShape.Block> mirrored = StationShape.blocks(0, 64, 0, 0, 1, true).stream()
                .filter(b -> b.part() == StationShape.Part.ROD).toList();
        assertEquals(-1, mirrored.get(0).x(), "the mirror image counts too");
        assertThrows(IllegalArgumentException.class, () -> StationShape.blocks(0, 64, 0, 1, 1, false));
    }

    @Test
    void anyWaxedCopperFitsButNotPlain() {
        assertTrue(StationShape.fits(Material.WAXED_COPPER_BLOCK, StationShape.Part.COPPER));
        assertTrue(StationShape.fits(Material.WAXED_OXIDIZED_COPPER, StationShape.Part.COPPER));
        assertFalse(StationShape.fits(Material.COPPER_BLOCK, StationShape.Part.COPPER), "it would oxidise");
        assertFalse(StationShape.fits(Material.WAXED_CUT_COPPER, StationShape.Part.COPPER));
        assertTrue(StationShape.fits(Material.WAXED_CUT_COPPER, StationShape.Part.CUT_COPPER));
        assertTrue(StationShape.fits(Material.WAXED_WEATHERED_CUT_COPPER, StationShape.Part.CUT_COPPER));
        assertFalse(StationShape.fits(Material.CUT_COPPER, StationShape.Part.CUT_COPPER));
        assertTrue(StationShape.fits(Material.IRON_BLOCK, StationShape.Part.IRON));
        // Built against 1.21.1, where lightning rods have no waxed kind: a plain one does there.
        assertEquals(!StationShape.waxedRods(), StationShape.fits(Material.LIGHTNING_ROD, StationShape.Part.ROD));
    }

    @Test
    void aStationOnlyEverReadsTheOutdoors() {
        // The rule (maintainer, BETA 1.0): a station measures the outdoor climate, never a room's
        // temperature. It has no way to: it holds neither the airflow nor the controller.
        for (Field field : WeatherStationService.class.getDeclaredFields()) {
            assertNotEquals(AirflowService.class, field.getType(), field.getName());
            assertNotEquals(HvacController.class, field.getType(), field.getName());
        }
    }
}
