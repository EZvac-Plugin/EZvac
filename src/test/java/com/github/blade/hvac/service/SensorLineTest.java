package com.github.blade.hvac.service;

import com.github.blade.hvac.model.Thermometer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensorLineTest {
    @Test
    void eachKindHasItsOwnShortLine() {
        assertEquals("Kitchen 72.4F", SensorLine.format("Kitchen", Thermometer.Reads.TEMPERATURE, true, 72.42, 45));
        assertEquals("Cellar 58%", SensorLine.format("Cellar", Thermometer.Reads.HUMIDITY, true, 60, 57.6));
        assertEquals("Kitchen 72F 45%", SensorLine.format("Kitchen", Thermometer.Reads.BOTH, true, 72.4, 45.2));
        assertEquals("Garage --", SensorLine.format("Garage", Thermometer.Reads.BOTH, false, Double.NaN, Double.NaN));
        assertEquals("Pool --%", SensorLine.format("Pool", Thermometer.Reads.HUMIDITY, true, 80, Double.NaN),
                "no humidity to read in water");
    }

    @Test
    void everyLineFitsOnASignAndTheReadingIsNeverCut() {
        String longLabel = "LivingRoomUpstairs";
        for (Thermometer.Reads reads : Thermometer.Reads.values())
            for (double temperature : new double[] {-40.25, 5, 72.4, 104.9, 199.5}) {
                String line = SensorLine.format(longLabel, reads, true, temperature, 100);
                assertTrue(line.length() <= SensorLine.WIDTH, line);
                assertTrue(line.startsWith("L"), "the label shrinks, never vanishes: " + line);
            }
        assertEquals("LivingR 72F 45%", SensorLine.format("LivingRoom", Thermometer.Reads.BOTH, true, 72, 45));
    }
}
