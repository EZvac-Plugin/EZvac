package com.github.blade.hvac.model;

import com.github.blade.hvac.config.ClimateSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DehumidifierTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private final ClimateSettings.Dehumidifier settings = ClimateSettings.defaults().dehumidifier();

    private static Dehumidifier unit() {
        return new Dehumidifier(new BlockKey(WORLD, 1, 65, 0), new BlockKey(WORLD, 0, 65, 0),
                new BlockKey(WORLD, 0, 64, 0), "basement", 45);
    }

    @Test
    void itStartsAboveTheBandAndRunsDownToTheSetpoint() {
        Dehumidifier dehumidifier = unit();
        assertFalse(dehumidifier.control(46.5, settings), "within the 2% band it waits");
        assertEquals(Dehumidifier.State.IDLE, dehumidifier.state());
        assertTrue(dehumidifier.control(47.5, settings));
        assertEquals(Dehumidifier.State.RUNNING, dehumidifier.state());
        assertFalse(dehumidifier.control(45.5, settings), "keeps going until the setpoint");
        assertTrue(dehumidifier.control(45.0, settings));
        assertFalse(dehumidifier.running());
        assertFalse(dehumidifier.control(Double.NaN, settings), "no reading, no running");
    }

    @Test
    void aFullTankEmptiesAndThenItCarriesOn() {
        Dehumidifier dehumidifier = unit();
        dehumidifier.control(60, settings);
        assertFalse(dehumidifier.fill(0.6, settings));
        assertTrue(dehumidifier.fill(0.6, settings), "full");
        assertEquals(Dehumidifier.State.EMPTYING, dehumidifier.state());
        assertFalse(dehumidifier.running());
        assertFalse(dehumidifier.control(60, settings), "never runs while emptying");
        assertFalse(dehumidifier.fill(0.5, settings), "and takes no water");
        assertFalse(dehumidifier.advanceEmptying(20 * 20));
        assertTrue(dehumidifier.advanceEmptying(10 * 20), "30 seconds later");
        assertEquals(0.0, dehumidifier.tank());
        assertTrue(dehumidifier.control(60, settings));
    }

    @Test
    void switchingOffFreezesTheTankAndTheEmptying() {
        Dehumidifier dehumidifier = unit();
        dehumidifier.control(60, settings);
        dehumidifier.fill(1.0, settings);
        long left = dehumidifier.emptyingTicks();
        dehumidifier.setPowered(false);
        assertEquals(Dehumidifier.State.OFF, dehumidifier.state());
        assertFalse(dehumidifier.advanceEmptying(10_000));
        assertEquals(left, dehumidifier.emptyingTicks());
        assertEquals(1.0, dehumidifier.tank());
        assertFalse(dehumidifier.control(80, settings), "off stays off");
        dehumidifier.setPowered(true);
        assertEquals(Dehumidifier.State.EMPTYING, dehumidifier.state(), "and picks up where it left off");
    }

    @Test
    void itRoundTripsThroughTheSaveFile() {
        Dehumidifier dehumidifier = unit();
        dehumidifier.setSetpoint(55);
        dehumidifier.setPowered(false);
        dehumidifier.fill(0.25, settings);
        dehumidifier.setZoneDewPointF(51.5);
        Map<String, Object> saved = dehumidifier.toMap();
        Dehumidifier loaded = Dehumidifier.fromMap(saved);
        assertEquals(saved, loaded.toMap());
        assertEquals(new BlockKey(WORLD, 0, 64, 0), loaded.grate());
        assertEquals(55, loaded.setpoint());
        assertFalse(loaded.powered());
        assertEquals(0.25, loaded.tank());
        assertEquals(51.5, loaded.zoneDewPointF());
    }

    @Test
    void partsMustShareAWorld() {
        UUID other = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Dehumidifier(new BlockKey(WORLD, 1, 65, 0),
                new BlockKey(other, 0, 65, 0), new BlockKey(WORLD, 0, 64, 0), "x", 45));
    }
}
