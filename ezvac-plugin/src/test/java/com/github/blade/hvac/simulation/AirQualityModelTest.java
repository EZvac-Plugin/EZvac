package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.simulation.AirQualityModel.Air;
import com.github.blade.hvac.simulation.AirQualityModel.Loads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The air in a space (3.2): fine, coarse and pollen, solved exactly over each step. */
class AirQualityModelTest {
    private static final AirQualitySettings SETTINGS = AirQualitySettings.defaults();
    private static final double INFILTRATION = 0.15;
    private static final long HOUR = (long) ThermalModel.MINECRAFT_TICKS_PER_HOUR;

    private static Air step(Air start, Air outdoor, Loads loads, long ticks) {
        return AirQualityModel.step(start, outdoor, loads, ticks, INFILTRATION, SETTINGS).air();
    }

    @Test
    void oneLongStepIsManyShortOnes() {
        Air start = new Air(80, 40, 120);
        Air outdoor = new Air(8, 12, 60);
        Loads loads = new Loads(new Air(20, 8, 0), 1.0, 1.0);
        Air once = step(start, outdoor, loads, 10 * HOUR);
        Air often = start;
        for (int tick = 0; tick < 10 * HOUR; tick++) often = step(often, outdoor, loads, 1);
        assertEquals(once.fine(), often.fine(), 1e-6);
        assertEquals(once.coarse(), often.coarse(), 1e-6);
        assertEquals(once.pollen(), often.pollen(), 1e-6);
    }

    @Test
    void itSettlesWhereWhatComesInMatchesWhatGoesOut() {
        Air outdoor = new Air(8, 12, 60);
        // A campfire in a room of the reference size, with nothing cleaning it.
        Air air = step(Air.UNKNOWN, outdoor, new Loads(new Air(20, 8, 0), 0.0, 0.0), 500 * HOUR);
        assertEquals((INFILTRATION * 8 + 20) / (INFILTRATION + 0.1), air.fine(), 1e-6);
        assertEquals((INFILTRATION * 12 + 8) / (INFILTRATION + 0.3), air.coarse(), 1e-6);
        assertEquals(INFILTRATION * 60 / (INFILTRATION + 0.5), air.pollen(), 1e-6);
        assertTrue(AirQualityIndex.aqi(air) > 150, "a campfire indoors is unhealthy: " + AirQualityIndex.aqi(air));
        Air purified = step(Air.UNKNOWN, outdoor, new Loads(new Air(20, 8, 0), 0.0, 1.0), 500 * HOUR);
        assertTrue(AirQualityIndex.aqi(purified) <= 50, "one purifier keeps up: " + AirQualityIndex.aqi(purified));
    }

    @Test
    void smokeLingersWhileDustAndPollenSettle() {
        Air after = step(new Air(100, 100, 100), Air.NONE, Loads.NONE, 3 * HOUR);
        assertTrue(after.fine() > after.coarse() && after.coarse() > after.pollen(), after.toString());
        assertTrue(after.fine() > 40, "smoke hangs about: " + after.fine());
    }

    @Test
    void aSystemsFilterCatchesPollenBestAndSmokeWorst() {
        Air start = new Air(100, 100, 100);
        Air still = step(start, Air.NONE, Loads.NONE, HOUR);
        Air filtered = step(start, Air.NONE, new Loads(Air.NONE, 1.0, 0.0), HOUR);
        double fine = filtered.fine() / still.fine();
        double coarse = filtered.coarse() / still.coarse();
        double pollen = filtered.pollen() / still.pollen();
        assertTrue(pollen < coarse && coarse < fine, fine + " " + coarse + " " + pollen);
        Air off = step(start, Air.NONE, new Loads(Air.NONE, 0.0, 0.0), HOUR);
        assertEquals(still, off, "no filtering while the equipment is off");
    }

    @Test
    void whatThePurifiersCatchIsWhatTheyTookOut() {
        Air start = new Air(60, 30, 90);
        Air outdoor = new Air(8, 12, 60);
        Loads loads = new Loads(new Air(10, 5, 0), 0.0, 2.0);
        AirQualityModel.Result result = AirQualityModel.step(start, outdoor, loads, 4 * HOUR, INFILTRATION, SETTINGS);
        // The same integral, summed tick by tick.
        double fine = 0, coarse = 0, pollen = 0;
        Air air = start;
        for (int tick = 0; tick < 4 * HOUR; tick++) {
            AirQualityModel.Result one = AirQualityModel.step(air, outdoor, loads, 1, INFILTRATION, SETTINGS);
            fine += one.captured().fine();
            coarse += one.captured().coarse();
            pollen += one.captured().pollen();
            air = one.air();
        }
        assertEquals(fine, result.captured().fine(), 1e-6);
        assertEquals(coarse, result.captured().coarse(), 1e-6);
        assertEquals(pollen, result.captured().pollen(), 1e-6);
        assertEquals(Air.NONE, AirQualityModel.step(start, outdoor, new Loads(Air.NONE, 1.0, 0.0), HOUR,
                INFILTRATION, SETTINGS).captured(), "no purifiers, nothing caught");
    }

    @Test
    void anUnknownSpaceStartsAtTheOutdoorAir() {
        Air outdoor = new Air(8, 12, 60);
        assertEquals(outdoor, step(Air.UNKNOWN, outdoor, Loads.NONE, 0));
        Air start = new Air(30, 30, 30);
        assertEquals(start, step(start, outdoor, Loads.NONE, 0), "no time, no change");
        assertThrows(IllegalArgumentException.class, () -> step(start, Air.UNKNOWN, Loads.NONE, HOUR));
    }

    @Test
    void itNeverGoesNegativeOrPastTheCap() {
        Air air = step(new Air(1, 1, 1), Air.NONE, new Loads(Air.NONE, 1.0, 50.0), 10_000 * HOUR);
        assertTrue(air.known() && air.fine() >= 0 && air.coarse() >= 0 && air.pollen() >= 0, air.toString());
        Air bonfire = step(Air.NONE, Air.NONE, new Loads(new Air(1e9, 1e9, 0), 0.0, 0.0), 1_000 * HOUR);
        assertEquals(AirQualityModel.MAXIMUM, bonfire.fine());
        assertEquals(AirQualityIndex.MAXIMUM, AirQualityIndex.aqi(bonfire));
    }

    @Test
    void airSavesAndBlends() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        new Air(1.5, 2.5, 3.5).write(map, "room");
        assertEquals(java.util.Map.of("roomFine", 1.5, "roomCoarse", 2.5, "roomPollen", 3.5), map);
        assertEquals(new Air(1.5, 2.5, 3.5), Air.read(map, "room"));
        assertFalse(Air.read(java.util.Map.of(), "room").known(), "a save from before 3.2");
        java.util.Map<String, Object> none = new java.util.LinkedHashMap<>();
        Air.UNKNOWN.write(none, "room");
        assertTrue(none.isEmpty());
        assertEquals(new Air(5, 10, 15), new Air(0, 0, 0).toward(new Air(10, 20, 30), 0.5));
        assertEquals(30.0, new Air(10, 20, 30).pm10());
    }
}
