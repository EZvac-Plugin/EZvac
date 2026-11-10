package com.github.blade.hvac.service;

import com.github.blade.hvac.model.Sensor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SensorLineTest {
    private static String line(String label, Sensor.Kind kind, double temperatureF, double humidity) {
        return SensorLine.format(label, kind, true, temperatureF, humidity, "", 90);
    }

    @Test
    void eachKindHasItsOwnShortLine() {
        assertEquals("Kitchen 72.4F", line("Kitchen", Sensor.Kind.TEMPERATURE, 72.42, 45));
        assertEquals("Cellar 58%", line("Cellar", Sensor.Kind.HUMIDITY, 60, 57.6));
        assertEquals("Kitchen 72F 45%", line("Kitchen", Sensor.Kind.BOTH, 72.4, 45.2));
        assertEquals("Garden Rain", SensorLine.format("Garden", Sensor.Kind.WEATHER, true, Double.NaN, Double.NaN, "Rain", 90));
        assertEquals("Porch Covered", SensorLine.format("Porch", Sensor.Kind.WEATHER, true, Double.NaN, Double.NaN,
                DisplayService.COVERED, 90));
        assertEquals("Garage --", SensorLine.format("Garage", Sensor.Kind.BOTH, false, Double.NaN, Double.NaN, "", 90));
        assertEquals("Garden --", SensorLine.format("Garden", Sensor.Kind.WEATHER, false, Double.NaN, Double.NaN, "", 90));
        assertEquals("Pool --%", line("Pool", Sensor.Kind.HUMIDITY, 80, Double.NaN), "no humidity to read in water");
    }

    @Test
    void everyLineFitsANarrowLineAndTheReadingIsNeverCut() {
        String longLabel = "LivingRoomUpstairs";
        for (Sensor.Kind kind : Sensor.Kind.values())
            for (double temperature : new double[] {-40.25, 5, 72.4, 104.9, 199.5}) {
                String line = SensorLine.format(longLabel, kind, true, temperature, 100, "Thunder", 90);
                assertTrue(TextWidth.width(line) <= 90, line);
                assertTrue(line.startsWith("L"), "the label shrinks, never vanishes: " + line);
            }
        assertEquals("LivingRo Thunder", SensorLine.format(longLabel, Sensor.Kind.WEATHER, true, 0, 0, "Thunder", 90));
        assertEquals("LivingRoo 72F 45%", line("LivingRoom", Sensor.Kind.BOTH, 72, 45),
                "measured in pixels, narrow letters leave room for more");
        assertEquals("MMMMMMM 72F 45%", line("MMMMMMMMMM", Sensor.Kind.BOTH, 72, 45));
    }

    @Test
    void aVeryNarrowLineKeepsTheReadingWhole() {
        for (Sensor.Kind kind : Sensor.Kind.values())
            for (double temperature : new double[] {-40.25, 72.4, 199.5}) {
                String line = SensorLine.format("Kitchen", kind, true, temperature, 100, "Covered",
                        60);
                assertTrue(TextWidth.width(line) <= 60, line);
            }
        assertEquals("Kit 72F 45%", SensorLine.format("Kitchen", Sensor.Kind.BOTH, true, 72, 45, "",
                60));
        assertEquals("-40F 100%", SensorLine.format("Kitchen", Sensor.Kind.BOTH, true, -40, 100, "",
                60), "no room for a letter: the reading alone");
    }
}
