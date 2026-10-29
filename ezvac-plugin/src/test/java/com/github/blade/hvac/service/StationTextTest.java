package com.github.blade.hvac.service;

import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.simulation.Forecast;
import com.github.blade.hvac.simulation.WindModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StationTextTest {
    private static Forecast.Day day(Forecast.Sky sky, double rain, double thunder, double highF, double lowF, String note) {
        return new Forecast.Day(2, highF, lowF, rain, thunder, sky, WindModel.Level.BREEZY, 5.0, 60.0, note);
    }

    @Test
    void temperaturesAreWholeDegrees() {
        assertEquals("71°F", StationText.temperature(71.4, TemperatureUnit.F));
        assertEquals("22°C", StationText.temperature(71.6, TemperatureUnit.C));
        assertEquals("--°F", StationText.temperature(Double.NaN, TemperatureUnit.F));
        assertEquals("Hi 74° Lo 55°", StationText.highLow(day(Forecast.Sky.CLEAR, 0, 0, 73.6, 55.2, ""), TemperatureUnit.F));
    }

    @Test
    void theSkySaysItsChance() {
        assertEquals("Clear", StationText.sky(day(Forecast.Sky.CLEAR, 0.1, 0, 70, 50, ""), false));
        assertEquals("Clear, rain 30%", StationText.sky(day(Forecast.Sky.CLEAR, 0.3, 0, 70, 50, ""), false));
        assertEquals("Snow 80%", StationText.sky(day(Forecast.Sky.SNOW, 0.8, 0, 30, 20, ""), true));
        assertEquals("Thunder 60%", StationText.sky(day(Forecast.Sky.THUNDER, 0.9, 0.6, 70, 50, ""), false));
        assertEquals("Tmrw: Clear", StationText.tomorrow(day(Forecast.Sky.CLEAR, 0.1, 0, 70, 50, ""), false));
        assertEquals("Tmrw: Rain 40%", StationText.tomorrow(day(Forecast.Sky.CLEAR, 0.4, 0, 70, 50, ""), false));
        assertEquals("Today", StationText.dayName(0, "Autumn", 3));
        assertEquals("Tomorrow", StationText.dayName(1, "Autumn", 4));
        assertEquals("Autumn, day 5", StationText.dayName(2, "Autumn", 5));
        assertEquals("In 3 days", StationText.dayName(3, null, 0));
    }

    @Test
    void everyScreenLineFits() {
        List<String> lines = List.of(
                StationText.sky(day(Forecast.Sky.CLEAR, 1.0, 0, 70, 50, ""), true),
                StationText.sky(day(Forecast.Sky.THUNDER, 1.0, 1.0, 70, 50, ""), false),
                StationText.highLow(day(Forecast.Sky.CLEAR, 0, 0, 120, -40, ""), TemperatureUnit.F),
                StationText.tomorrow(day(Forecast.Sky.CLEAR, 0.49, 0, 70, 50, ""), true),
                StationText.tomorrow(day(Forecast.Sky.THUNDER, 1.0, 1.0, 70, 50, ""), false),
                StationText.tomorrow(day(Forecast.Sky.SNOW, 1.0, 0, 30, 20, ""), true),
                "Snow until 23:00", "Rain from 23:00", "Clear all day", "Gale 100km/h", "Rebuild its tower",
                "Off server-wide");
        for (String line : lines)
            assertTrue(SignText.width(line) <= ScreenText.WIDTH, line + ": " + SignText.width(line));
    }
}
