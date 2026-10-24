package com.github.blade.hvac.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SensorTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static BlockKey at(int x, int z) { return new BlockKey(WORLD, x, 64, z); }

    @Test
    void theButtonSaysWhatASensorMeasures() {
        assertEquals(Sensor.Kind.TEMPERATURE, Sensor.forButton(Material.STONE_BUTTON));
        assertEquals(Sensor.Kind.HUMIDITY, Sensor.forButton(Material.POLISHED_BLACKSTONE_BUTTON));
        assertEquals(Sensor.Kind.BOTH, Sensor.forButton(Material.MANGROVE_BUTTON));
        assertEquals(Sensor.Kind.WEATHER, Sensor.forButton(Material.BIRCH_BUTTON));
        assertNull(Sensor.forButton(Material.OAK_BUTTON), "only the four sensor buttons");
        assertNull(Sensor.forButton(Material.STONE));
        assertNull(Sensor.forButton(null));
        for (Sensor.Kind reads : Sensor.Kind.values())
            assertEquals(reads, Sensor.forButton(Sensor.button(reads)), "button and type map both ways");
    }

    @Test
    void aSensorRoundTripsThroughTheSaveFile() {
        Sensor sensor = new Sensor(at(1, 2), "kitchen", Sensor.Kind.BOTH);
        Map<String, Object> saved = sensor.toMap();
        assertEquals(sensor, Sensor.fromMap(saved));
        assertEquals("BOTH", saved.get("reads"));
        Sensor weather = new Sensor(at(3, 4), "garden", Sensor.Kind.WEATHER);
        assertEquals(weather, Sensor.fromMap(weather.toMap()));
        Map<String, Object> pilot4 = sensor.toMap();
        pilot4.remove("reads");
        assertEquals(Sensor.Kind.TEMPERATURE, Sensor.fromMap(pilot4).reads(), "no kind saved means temperature");
    }

    @Test
    void aHubLinksUpToNineSensorsInItsOwnWorld() {
        SensorHub hub = new SensorHub(at(0, 0), "home");
        for (int i = 0; i < SensorHub.MAX_SENSORS; i++) assertEquals(SensorHub.LinkResult.LINKED, hub.link(at(i, 5)));
        assertEquals(SensorHub.LinkResult.FULL, hub.link(at(20, 5)), "a 10th is refused");
        assertEquals(SensorHub.LinkResult.ALREADY_LINKED, hub.link(at(3, 5)));
        assertEquals(SensorHub.LinkResult.OTHER_WORLD, new SensorHub(at(0, 0), "x")
                .link(new BlockKey(UUID.randomUUID(), 1, 64, 1)));
        assertTrue(hub.unlink(at(3, 5)));
        assertFalse(hub.unlink(at(3, 5)));
        assertEquals(8, hub.sensors().size());
    }

    @Test
    void pagesHoldThreeSensorsAndWrapAround() {
        SensorHub hub = new SensorHub(at(0, 0), "home");
        assertEquals(1, hub.pages(), "an empty hub still has a page");
        assertTrue(hub.sensorsOnPage().isEmpty());
        for (int i = 0; i < 7; i++) hub.link(at(i, 5));
        assertEquals(3, hub.pages());
        assertEquals(List.of(at(0, 5), at(1, 5), at(2, 5)), hub.sensorsOnPage());
        hub.nextPage();
        hub.nextPage();
        assertEquals(List.of(at(6, 5)), hub.sensorsOnPage(), "the last page holds what is left");
        hub.nextPage();
        assertEquals(0, hub.page(), "and wraps back to the first");
        hub.nextPage(); hub.nextPage();
        hub.unlink(at(6, 5));
        assertEquals(1, hub.page(), "unlinking the only sensor on the last page moves back a page");
    }

    @Test
    void autoFlipTurnsThePageEveryFiveSeconds() {
        SensorHub hub = new SensorHub(at(0, 0), "home");
        for (int i = 0; i < 4; i++) hub.link(at(i, 5));
        assertFalse(hub.advanceAutoFlip(200), "off by default");
        hub.setAutoFlip(true);
        assertFalse(hub.advanceAutoFlip(80));
        assertTrue(hub.advanceAutoFlip(20));
        assertEquals(1, hub.page());
        SensorHub single = new SensorHub(at(1, 0), "one");
        single.link(at(9, 9));
        single.setAutoFlip(true);
        assertFalse(single.advanceAutoFlip(1_000), "nothing to flip to on one page");
    }

    @Test
    void autoFlipIsSavedButThePageIsNot() {
        SensorHub hub = new SensorHub(at(0, 0), "home");
        for (int i = 0; i < 5; i++) hub.link(at(i, 5));
        hub.setAutoFlip(true);
        hub.nextPage();
        SensorHub loaded = SensorHub.fromMap(hub.toMap());
        assertTrue(loaded.autoFlip());
        assertEquals(0, loaded.page(), "a restarted hub starts on its first page");
        assertEquals(hub.sensors(), loaded.sensors(), "in the order they were linked");
        assertEquals(List.of("0,64,5", "1,64,5", "2,64,5", "3,64,5", "4,64,5"), hub.toMap().get("sensors"));
    }

    @Test
    void malformedSavedLinksAreSkipped() {
        SensorHub hub = new SensorHub(at(0, 0), "home");
        Map<String, Object> saved = hub.toMap();
        saved.put("sensors", List.of("1,64,1", "not a block", "2,64"));
        assertEquals(List.of(at(1, 1)), SensorHub.fromMap(saved).sensors());
    }
}
