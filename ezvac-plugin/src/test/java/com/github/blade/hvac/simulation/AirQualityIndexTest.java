package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.simulation.AirQualityIndex.Band;
import com.github.blade.hvac.simulation.AirQualityIndex.PollenLevel;
import com.github.blade.hvac.simulation.AirQualityIndex.Pollutant;
import com.github.blade.hvac.simulation.AirQualityModel.Air;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The US EPA Air Quality Index (3.2), at every breakpoint. */
class AirQualityIndexTest {
    @Test
    void pm25FollowsThe2024Breakpoints() {
        double[][] cases = {
                {0, 0}, {9.0, 50}, {9.1, 51}, {35.4, 100}, {35.5, 101}, {55.4, 150}, {55.5, 151},
                {125.4, 200}, {125.5, 201}, {225.4, 300}, {225.5, 301}, {325.4, 500}, {400, 500},
                {9.05, 50}, {12.0, 56}
        };
        for (double[] row : cases) assertEquals((int) row[1], AirQualityIndex.pm25(row[0]), "PM2.5 " + row[0]);
    }

    @Test
    void pm10FollowsTheBreakpoints() {
        double[][] cases = {
                {0, 0}, {54, 50}, {55, 51}, {154, 100}, {155, 101}, {254, 150}, {255, 151},
                {354, 200}, {355, 201}, {424, 300}, {425, 301}, {604, 500}, {700, 500}, {54.9, 50}
        };
        for (double[] row : cases) assertEquals((int) row[1], AirQualityIndex.pm10(row[0]), "PM10 " + row[0]);
    }

    @Test
    void theHigherScoreIsTheAqiAndNamesTheCause() {
        Air dust = new Air(5, 100, 0);   // PM10 105 scores 76, PM2.5 5 scores 28
        assertEquals(76, AirQualityIndex.aqi(dust));
        assertEquals(Pollutant.PM10, AirQualityIndex.dominant(dust));
        assertEquals("Dust", AirQualityIndex.dominant(dust).cause());
        Air smoke = new Air(60, 10, 0);
        assertEquals(AirQualityIndex.pm25(60), AirQualityIndex.aqi(smoke));
        assertEquals("Smoke", AirQualityIndex.dominant(smoke).cause());
        assertEquals(0, AirQualityIndex.aqi(Air.UNKNOWN));
        assertEquals(0, AirQualityIndex.aqi(new Air(0, 0, 10_000)), "pollen is not part of the AQI");
    }

    @Test
    void bandsFollowTheEpa() {
        assertEquals(Band.GOOD, Band.of(50));
        assertEquals(Band.MODERATE, Band.of(51));
        assertEquals(Band.MODERATE, Band.of(100));
        assertEquals(Band.SENSITIVE, Band.of(101));
        assertEquals(Band.UNHEALTHY, Band.of(151));
        assertEquals(Band.VERY_UNHEALTHY, Band.of(201));
        assertEquals(Band.VERY_UNHEALTHY, Band.of(300));
        assertEquals(Band.HAZARDOUS, Band.of(301));
        assertEquals("Unhealthy for Sensitive Groups", Band.SENSITIVE.displayName());
    }

    @Test
    void pollenLevelsFollowTheServersThresholds() {
        AirQualitySettings.PollenLevels levels = AirQualitySettings.defaults().pollenLevels();
        assertEquals(PollenLevel.LOW, PollenLevel.of(0, levels));
        assertEquals(PollenLevel.LOW, PollenLevel.of(19.9, levels));
        assertEquals(PollenLevel.MODERATE, PollenLevel.of(20, levels));
        assertEquals(PollenLevel.HIGH, PollenLevel.of(80, levels));
        assertEquals(PollenLevel.VERY_HIGH, PollenLevel.of(200, levels));
        assertEquals(PollenLevel.LOW, PollenLevel.of(Double.NaN, levels));
        assertEquals(PollenLevel.VERY_HIGH, PollenLevel.parse("Very high"));
        assertEquals(PollenLevel.HIGH, PollenLevel.parse(" HIGH "));
        assertNull(PollenLevel.parse("never"));
    }
}
