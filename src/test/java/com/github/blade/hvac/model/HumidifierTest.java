package com.github.blade.hvac.model;

import com.github.blade.hvac.config.ClimateSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HumidifierTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private final ClimateSettings settings = ClimateSettings.defaults();

    private static Humidifier unit() {
        return new Humidifier(new BlockKey(WORLD, 1, 65, 0), new BlockKey(WORLD, 0, 65, 0),
                new BlockKey(WORLD, 0, 66, 0), "nursery", 40);
    }

    @Test
    void itStartsBelowTheBandAndRunsUpToTheSetpoint() {
        Humidifier humidifier = unit();
        assertFalse(humidifier.control(38.5, settings), "within the 2% band it waits");
        assertEquals(ClimateUnit.State.IDLE, humidifier.state());
        assertTrue(humidifier.control(37.5, settings));
        assertEquals(ClimateUnit.State.RUNNING, humidifier.state());
        assertFalse(humidifier.control(39.5, settings), "keeps going until the setpoint");
        assertTrue(humidifier.control(40.0, settings));
        assertFalse(humidifier.running());
        assertFalse(humidifier.control(Double.NaN, settings), "no reading, no running");
    }

    @Test
    void switchingOffStopsItAndItNeverRunsDry() {
        Humidifier humidifier = unit();
        humidifier.control(20, settings);
        humidifier.setPowered(false);
        assertEquals(ClimateUnit.State.OFF, humidifier.state());
        assertFalse(humidifier.control(10, settings), "off stays off");
        humidifier.setPowered(true);
        for (int cycle = 0; cycle < 10_000; cycle++) humidifier.control(10, settings);
        assertTrue(humidifier.running(), "plumbed: no tank to run out");
    }

    @Test
    void presetsCycleWithinTheHumidifierRange() {
        Humidifier humidifier = unit();
        assertEquals(45, humidifier.nextPreset(settings));
        humidifier.setSetpoint(50);
        assertEquals(30, humidifier.nextPreset(settings), "wraps to the lowest");
    }

    @Test
    void itRoundTripsThroughTheSaveFile() {
        Humidifier humidifier = unit();
        humidifier.setSetpoint(45);
        humidifier.setPowered(false);
        humidifier.setZoneDewPointF(41.25);
        Map<String, Object> saved = humidifier.toMap();
        assertTrue(saved.containsKey("slabX"), "its water block is saved as the slab");
        Humidifier loaded = Humidifier.fromMap(saved);
        assertEquals(saved, loaded.toMap());
        assertEquals(new BlockKey(WORLD, 0, 66, 0), loaded.slab());
        assertEquals(45, loaded.setpoint());
        assertFalse(loaded.powered());
        assertEquals(41.25, loaded.zoneDewPointF());
        assertEquals(ClimateUnit.Kind.HUMIDIFIER, loaded.kind());
    }
}
