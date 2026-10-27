package com.github.blade.hvac.model;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.AirPurifier.Mode;
import com.github.blade.hvac.simulation.AirQualityIndex;
import com.github.blade.hvac.simulation.AirQualityModel.Air;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The air purifier (3.2): its fan speeds and Auto, its coral filter, and its save. */
class AirPurifierTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final AirQualitySettings SETTINGS = AirQualitySettings.defaults();
    private static final Air CLEAN = new Air(2, 2, 0);

    private static AirPurifier purifier(Mode mode) {
        return new AirPurifier(new BlockKey(WORLD, 1, 65, 0), new BlockKey(WORLD, 0, 65, 0),
                new BlockKey(WORLD, 0, 64, 0), "Hall", mode);
    }

    /** Air whose AQI is this, from PM2.5 alone, with no pollen. */
    private static Air aqi(int target) {
        for (double pm = 0; pm < 400; pm += 0.1)
            if (AirQualityIndex.pm25(pm) >= target) return new Air(pm, 0, 0);
        throw new AssertionError();
    }

    @Test
    void itRunsWheneverItIsOnAndAFixedSpeedStaysPut() {
        AirPurifier purifier = purifier(Mode.MEDIUM);
        assertTrue(purifier.control(CLEAN, SETTINGS), "it starts");
        assertTrue(purifier.running());
        assertEquals(ClimateUnit.State.RUNNING, purifier.state());
        purifier.control(aqi(250), SETTINGS);
        assertEquals(Mode.MEDIUM, purifier.speed(), "a fixed speed doesn't change with the air");
        assertEquals("Medium", purifier.modeText());
        assertEquals(0.65, purifier.speedFactor(SETTINGS.purifier()));
        assertEquals(1.0, purifier(Mode.HIGH).speedFactor(SETTINGS.purifier()), "High is the full rate");
    }

    @Test
    void autoIdlesOnLowAndRampsUpWithTheAir() {
        AirPurifier purifier = purifier(Mode.AUTO);
        purifier.control(CLEAN, SETTINGS);
        assertTrue(purifier.running(), "it never switches off in clean air");
        assertEquals(Mode.LOW, purifier.speed());
        assertEquals("Auto · Low", purifier.modeText());
        purifier.control(aqi(60), SETTINGS);
        assertEquals(Mode.MEDIUM, purifier.speed(), "Moderate air");
        purifier.control(aqi(120), SETTINGS);
        assertEquals(Mode.HIGH, purifier.speed(), "past AQI 100");
        assertEquals("Auto · High", purifier.modeText());
        purifier.control(new Air(2, 2, 25), SETTINGS);
        assertEquals(Mode.MEDIUM, purifier.speed(), "clean air with moderate pollen");
        purifier.control(new Air(2, 2, 90), SETTINGS);
        assertEquals(Mode.HIGH, purifier.speed(), "high pollen, whatever the AQI");
    }

    @Test
    void autoStepsDownOnlyOnceTheAirIsClearlyBetter() {
        AirPurifier purifier = purifier(Mode.AUTO);
        purifier.control(aqi(120), SETTINGS);
        assertEquals(Mode.HIGH, purifier.speed());
        purifier.control(aqi(90), SETTINGS);
        assertEquals(Mode.HIGH, purifier.speed(), "within the hysteresis of the High step");
        purifier.control(aqi(75), SETTINGS);
        assertEquals(Mode.MEDIUM, purifier.speed());
        purifier.control(aqi(40), SETTINGS);
        assertEquals(Mode.MEDIUM, purifier.speed(), "within the hysteresis of the Medium step");
        purifier.control(aqi(25), SETTINGS);
        assertEquals(Mode.LOW, purifier.speed());
        purifier.control(new Air(2, 2, 85), SETTINGS);
        assertEquals(Mode.HIGH, purifier.speed());
        purifier.control(new Air(2, 2, 75), SETTINGS);
        assertEquals(Mode.HIGH, purifier.speed(), "pollen a little under High still counts");
        purifier.control(new Air(2, 2, 60), SETTINGS);
        assertEquals(Mode.MEDIUM, purifier.speed());
    }

    @Test
    void autoCanIgnorePollen() {
        AirQualitySettings.Purifier p = SETTINGS.purifier();
        AirQualitySettings.Purifier noPollen = new AirQualitySettings.Purifier(p.range(), p.maximumCells(), p.rate(),
                p.speeds(), new AirQualitySettings.Auto(50, 100, null, null, 20), p.filterCapacity(),
                p.maximumLifeDays(), p.pollenWeight());
        AirQualitySettings settings = new AirQualitySettings(true, 1_500, SETTINGS.settling(), SETTINGS.systemFilter(),
                SETTINGS.sources(), SETTINGS.pollenLevels(), noPollen, Map.of());
        AirPurifier purifier = purifier(Mode.AUTO);
        purifier.control(new Air(2, 2, 1_000), settings);
        assertEquals(Mode.LOW, purifier.speed(), "pollen: never");
    }

    @Test
    void itNeverRunsWithoutAFilterPowerOrAir() {
        AirPurifier purifier = purifier(Mode.HIGH);
        purifier.control(Air.UNKNOWN, SETTINGS);
        assertFalse(purifier.running(), "air it can't read");
        purifier.setPowered(false);
        purifier.control(aqi(200), SETTINGS);
        assertFalse(purifier.running());
        assertEquals(ClimateUnit.State.OFF, purifier.state());
        purifier.setPowered(true);
        purifier.readFilter(Material.AIR);
        assertEquals(AirPurifier.Filter.MISSING, purifier.installed());
        assertEquals(ClimateUnit.State.NO_FILTER, purifier.state());
        purifier.control(aqi(200), SETTINGS);
        assertFalse(purifier.running());
        AirQualitySettings disabled = new AirQualitySettings(false, 1_500, SETTINGS.settling(),
                SETTINGS.systemFilter(), SETTINGS.sources(), SETTINGS.pollenLevels(), SETTINGS.purifier(), Map.of());
        AirPurifier other = purifier(Mode.HIGH);
        other.control(aqi(200), disabled);
        assertFalse(other.running(), "air quality switched off");
    }

    @Test
    void dirtierAirAndFasterFansFillTheFilterFaster() {
        AirPurifier clean = purifier(Mode.HIGH);
        AirPurifier smoky = purifier(Mode.HIGH);
        clean.wear(new Air(1, 1, 0), 1.0, SETTINGS.purifier());
        smoky.wear(new Air(20, 8, 0), 1.0, SETTINGS.purifier());
        assertTrue(smoky.filter() < clean.filter(), smoky.filter() + " " + clean.filter());
        AirPurifier pollen = purifier(Mode.HIGH);
        pollen.wear(new Air(0, 0, 20), 1.0, SETTINGS.purifier());
        assertEquals(1.0 - 10.0 / 2_400 - 1.0 / (60 * 24), pollen.filter(), 1e-12, "pollen counts at its weight");
        AirPurifier idle = purifier(Mode.HIGH);
        for (int day = 0; day < 59; day++) idle.wear(Air.NONE, 24.0, SETTINGS.purifier());
        assertTrue(idle.filterUsable(), "on High in perfectly clean air it lasts nearly its maximum life");
        idle.wear(Air.NONE, 24.0, SETTINGS.purifier());
        assertFalse(idle.filterUsable(), "but no longer");
        AirPurifier slow = purifier(Mode.LOW);
        for (int day = 0; day < 100; day++) slow.wear(Air.NONE, 24.0, SETTINGS.purifier());
        assertTrue(slow.filterUsable(), "on Low it lasts longer");
    }

    @Test
    void aFullFilterStopsItAndOnlyAFreshCoralReplacesIt() {
        AirPurifier purifier = purifier(Mode.AUTO);
        purifier.control(aqi(200), SETTINGS);
        assertTrue(purifier.running());
        assertTrue(purifier.wear(new Air(3_000, 0, 0), 1.0, SETTINGS.purifier()));
        assertFalse(purifier.running());
        assertEquals(0.0, purifier.filter());
        assertEquals(ClimateUnit.State.FILTER_SPENT, purifier.state());
        assertFalse(purifier.wear(new Air(10, 0, 0), 1.0, SETTINGS.purifier()), "a full filter fills no more");

        purifier.readFilter(AirPurifier.SPENT_FILTER);
        assertEquals(ClimateUnit.State.FILTER_SPENT, purifier.state());
        purifier.readFilter(Material.AIR);
        assertEquals(ClimateUnit.State.NO_FILTER, purifier.state(), "the dead coral was broken");
        assertTrue(purifier.readFilter(AirPurifier.FILTER), "fresh coral is a new filter");
        assertEquals(1.0, purifier.filter());
        assertEquals(ClimateUnit.State.IDLE, purifier.state());

        purifier.wear(new Air(1_200, 0, 0), 1.0, SETTINGS.purifier());
        double left = purifier.filter();
        purifier.readFilter(Material.AIR);
        assertFalse(purifier.readFilter(AirPurifier.FILTER), "a part-used filter put back is not new");
        assertEquals(left, purifier.filter(), "and keeps what it had left");
    }

    @Test
    void itIsKeptWithoutItsFilterAndHasNoSetpoint() {
        AirPurifier purifier = purifier(Mode.AUTO);
        assertFalse(purifier.partRequired());
        assertEquals(Material.TUBE_CORAL_BLOCK, purifier.partMaterial());
        purifier.setSetpoint(50);
        assertEquals(0, purifier.setpoint());
        purifier.setMode(Mode.HIGH);
        assertEquals(Mode.HIGH, purifier.speed(), "a fixed speed takes effect at once");
        purifier.setMode(null);
        assertEquals(Mode.AUTO, purifier.mode());
        assertEquals(Mode.MEDIUM, Mode.parse(" med "));
        assertNull(Mode.parse("turbo"));
        assertTrue(new Dehumidifier(new BlockKey(WORLD, 1, 65, 0), new BlockKey(WORLD, 0, 65, 0),
                new BlockKey(WORLD, 0, 64, 0), "dry", 45).partRequired());
    }

    @Test
    void itSavesItsModeFilterAndItsZonesAir() {
        AirPurifier purifier = purifier(Mode.MEDIUM);
        purifier.wear(new Air(600, 0, 0), 1.0, SETTINGS.purifier());
        purifier.setZoneAir(new Air(12, 20, 30));
        purifier.setZoneDewPointF(50);
        Map<String, Object> map = purifier.toMap();
        assertEquals("MEDIUM", map.get("mode"));
        assertFalse(map.containsKey("setpoint"), "it has no setpoint to save");
        assertEquals(0, map.get("filterX"), "the filter block is saved as its part");
        AirPurifier loaded = AirPurifier.fromMap(map);
        assertEquals(purifier.filter(), loaded.filter());
        assertEquals(Mode.MEDIUM, loaded.mode());
        assertEquals(new Air(12, 20, 30), loaded.zoneAir());
        assertEquals(50, loaded.zoneDewPointF());
        assertEquals(purifier.filterBlock(), loaded.filterBlock());
        map.put("filterLife", 0.0);
        assertEquals(ClimateUnit.State.FILTER_SPENT, AirPurifier.fromMap(map).state());
        map.remove("mode");
        assertEquals(Mode.AUTO, AirPurifier.fromMap(map).mode(), "no mode reads as Auto");

        Dehumidifier dry = new Dehumidifier(new BlockKey(WORLD, 1, 65, 0), new BlockKey(WORLD, 0, 65, 0),
                new BlockKey(WORLD, 0, 64, 0), "dry", 45);
        dry.setZoneAir(new Air(5, 6, 7));
        assertEquals(new Air(5, 6, 7), Dehumidifier.fromMap(dry.toMap()).zoneAir(), "every unit keeps its zone's air");
        Map<String, Object> old = dry.toMap();
        old.keySet().removeIf(key -> key.startsWith("zone") && !key.equals("zoneDewPointF"));
        assertFalse(Dehumidifier.fromMap(old).zoneAir().known(), "a save from before 3.2");
    }

    @Test
    void theOldControlCallUsesTheLastAirItRead() {
        AirPurifier purifier = purifier(Mode.AUTO);
        purifier.setLastAir(aqi(150));
        purifier.control(Double.NaN, ClimateSettings.defaults());
        assertTrue(purifier.running());
        assertEquals(Mode.HIGH, purifier.speed());
    }
}
