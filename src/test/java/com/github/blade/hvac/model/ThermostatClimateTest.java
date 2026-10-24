package com.github.blade.hvac.model;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.simulation.BiomeBlend;
import com.github.blade.hvac.simulation.Precipitation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A thermostat's cached outdoor climate on and off a biome border (2.1). */
class ThermostatClimateTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private final HvacSettings settings = HvacSettings.defaults();

    private Thermostat thermostat() {
        return new Thermostat(new BlockKey(WORLD, 0, 64, 0), "main", "house", 72.0, 70.0, settings, false);
    }

    @Test
    void onABorderTheMixIsSavedAndComesBack() {
        Thermostat thermostat = thermostat();
        thermostat.setOutdoorClimate(new BiomeBlend.Blended(86, 65, 0.2, 0.45, 0.0));
        assertEquals(Precipitation.NONE, thermostat.outdoorPrecipitation(), "mostly dry, so its dominant weather is none");
        Map<String, Object> saved = thermostat.toMap();
        assertEquals(0.45, saved.get("outdoorRainShare"));
        Thermostat loaded = Thermostat.fromMap(saved, settings);
        assertEquals(thermostat.outdoorClimate(), loaded.outdoorClimate());
    }

    @Test
    void insideOneBiomeTheSaveIsExactlyAsBefore() {
        Thermostat thermostat = thermostat();
        thermostat.setOutdoorClimate(BiomeBlend.Blended.of(72, 52, 0.4, Precipitation.RAIN));
        Map<String, Object> saved = thermostat.toMap();
        assertFalse(saved.containsKey("outdoorRainShare"), "nothing new written for a thermostat inside one biome");
        assertFalse(saved.containsKey("outdoorSnowShare"));
        assertEquals("RAIN", saved.get("outdoorPrecipitation"));
    }

    @Test
    void aSaveFromBeforeTwoPointOneGetsItsSharesFromItsWeather() {
        Map<String, Object> saved = thermostat().toMap();
        saved.put("outdoorPrecipitation", "SNOW");
        Thermostat loaded = Thermostat.fromMap(saved, settings);
        assertEquals(0.0, loaded.outdoorClimate().rainShare());
        assertEquals(1.0, loaded.outdoorClimate().snowShare());
        assertEquals(saved, loaded.toMap(), "and saves back unchanged");
    }
}
