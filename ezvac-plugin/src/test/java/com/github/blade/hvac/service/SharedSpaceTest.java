package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.Dehumidifier;
import com.github.blade.hvac.model.Humidifier;
import com.github.blade.hvac.simulation.HumidityModel;
import com.github.blade.hvac.simulation.ThermalModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Standalone units in one space share its humidity (2.0 Pilot 5). */
class SharedSpaceTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final long HOUR = (long) ThermalModel.MINECRAFT_TICKS_PER_HOUR;
    private final ClimateSettings climate = ClimateSettings.defaults();
    private final int cells = climate.dehumidifier().referenceCells();

    private static Dehumidifier dehumidifier(int x) {
        return new Dehumidifier(new BlockKey(WORLD, x + 1, 65, 0), new BlockKey(WORLD, x, 65, 0),
                new BlockKey(WORLD, x, 64, 0), "dry", 30);
    }

    private static Humidifier humidifier(int x) {
        return new Humidifier(new BlockKey(WORLD, x + 1, 65, 0), new BlockKey(WORLD, x, 65, 0),
                new BlockKey(WORLD, x, 66, 0), "wet", 60);
    }

    private static <T extends ClimateUnit> T running(T unit, double humidity) {
        unit.control(humidity, ClimateSettings.defaults());
        assertTrue(unit.running());
        return unit;
    }

    @Test
    void everyUnitInTheSpaceEndsUpWithOneDewPoint() {
        Dehumidifier dry = running(dehumidifier(0), 80);
        Humidifier wet = running(humidifier(4), 20);
        dry.setZoneDewPointF(50);
        wet.setZoneDewPointF(60);
        double rh = step(List.of(dry, wet), HOUR);
        assertEquals(dry.zoneDewPointF(), wet.zoneDewPointF(), "one space, one humidity");
        assertTrue(Double.isFinite(rh));
    }

    @Test
    void aLoneUnitStepsExactlyAsItAlwaysDid() {
        Dehumidifier dry = running(dehumidifier(0), 80);
        dry.setZoneDewPointF(62);
        step(List.of(dry), HOUR);
        HumidityModel.Result alone = HumidityModel.step(62, 72, 62,
                new HumidityModel.Loads(0.0, 0.0, Double.NaN, 1.0, 0.0), HOUR, climate);
        assertEquals(alone.dewPointF(), dry.zoneDewPointF(), 1e-9);
        assertTrue(dry.tank() > 0.0, "and fills its tank");
    }

    @Test
    void whatTheUnitsDoAddsUpAndTheWaterIsSharedBetweenTanks() {
        Dehumidifier first = running(dehumidifier(0), 80);
        Dehumidifier second = running(dehumidifier(4), 80);
        first.setZoneDewPointF(62);
        second.setZoneDewPointF(62);
        step(List.of(first, second), HOUR);
        Dehumidifier lone = running(dehumidifier(8), 80);
        lone.setZoneDewPointF(62);
        step(List.of(lone), HOUR);
        assertTrue(first.zoneDewPointF() < lone.zoneDewPointF(), "two dry faster than one");
        assertEquals(first.tank(), second.tank(), 1e-12, "the water is shared equally");
        assertEquals(2 * lone.tank(), first.tank() + second.tank(), 1e-9,
                "in humid air each pulls its rated amount, so two collect twice the water");
    }

    @Test
    void aDehumidifierAndAHumidifierPullAgainstEachOther() {
        Humidifier wet = running(humidifier(0), 20);
        wet.setZoneDewPointF(50);
        step(List.of(wet), 20 * HOUR);
        Humidifier fought = running(humidifier(4), 20);
        Dehumidifier dry = running(dehumidifier(8), 80);
        fought.setZoneDewPointF(50);
        dry.setZoneDewPointF(50);
        step(List.of(fought, dry), 20 * HOUR);
        assertTrue(fought.zoneDewPointF() < wet.zoneDewPointF());
    }

    @Test
    void idleUnitsJustDriftTowardTheOutdoors() {
        Dehumidifier dry = dehumidifier(0);
        Humidifier wet = humidifier(4);
        step(List.of(dry, wet), HOUR);
        assertEquals(62.0, dry.zoneDewPointF(), 1e-9, "an unknown space starts at the outdoor moisture");
        assertEquals(0.0, dry.tank());
    }

    /** One step at 72 F outdoors with a 62 F outdoor dew point and no water. */
    private double step(List<ClimateUnit> space, long ticks) {
        return ClimateUnitService.stepSharedSpace(space, cells, 0.0, 72, 62, ticks, climate);
    }
}
