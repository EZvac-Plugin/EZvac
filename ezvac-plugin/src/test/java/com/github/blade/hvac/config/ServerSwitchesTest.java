package com.github.blade.hvac.config;

import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.DeviceKind;
import com.github.blade.hvac.model.Humidifier;
import com.github.blade.hvac.service.AirText;
import com.github.blade.hvac.simulation.AirQualityModel;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /hvac quicksettings' switches (BETA 1.0): read from config.yml, written back, and followed at once. */
class ServerSwitchesTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private final AtomicInteger saves = new AtomicInteger();
    private ServerSwitches switches;

    @AfterEach
    void uninstall() {
        if (switches != null) switches.uninstall();
    }

    private ServerSwitches load(String yaml, List<String> warnings) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        switches = ServerSwitches.load(config, saves::incrementAndGet, warnings::add);
        return switches;
    }

    @Test
    void theyStartAsConfigSays() throws Exception {
        List<String> warnings = new ArrayList<>();
        ServerSwitches loaded = load("""
                devices:
                  disabled: [heat_pump, Humidifier, toaster]
                air-quality:
                  pollen: false
                wind:
                  enabled: false
                """, warnings);
        assertTrue(loaded.isOff(DeviceKind.HEAT_PUMP));
        assertTrue(loaded.isOff(DeviceKind.HUMIDIFIER));
        assertFalse(loaded.isOff(DeviceKind.FURNACE));
        assertEquals(1, warnings.size(), "the unknown kind is pointed out");
        assertTrue(warnings.get(0).contains("toaster") && warnings.get(0).contains("air-purifier"), warnings.get(0));
        assertTrue(loaded.airQuality());
        assertFalse(loaded.pollen());
        assertFalse(loaded.wind());
        assertTrue(loaded.seasons());
    }

    @Test
    void theSettingsFollowTheSwitchesOnlyWhileInstalled() throws Exception {
        ServerSwitches loaded = load("air-quality:\n  enabled: true\n", new ArrayList<>());
        AirQualitySettings air = AirQualitySettings.defaults();
        WindSettings wind = WindSettings.defaults();
        ClimateSettings.Seasons seasons = ClimateSettings.defaults().seasons();
        loaded.setAirQuality(false);
        loaded.setWind(false);
        loaded.setSeasons(false);
        assertTrue(air.enabled() && wind.enabled() && seasons.enabled(), "not installed: config.yml's values");

        loaded.install();
        assertFalse(air.enabled(), "air quality follows the switch");
        assertFalse(air.pollen(), "pollen goes with air quality");
        assertFalse(wind.enabled());
        assertEquals(1.0, wind.infiltrationFactor(10.0), "no wind, no drafts");
        assertFalse(seasons.enabled());
        assertEquals(0.0, seasons.effectiveStrength());

        loaded.setAirQuality(true);
        loaded.setWind(true);
        loaded.setSeasons(true);
        assertTrue(air.enabled() && air.pollen() && wind.enabled() && seasons.enabled(), "and back on, at once");

        loaded.uninstall();
        switches = null;
        assertTrue(air.enabled());
    }

    @Test
    void aChangeIsWrittenToConfigYml() throws Exception {
        ServerSwitches loaded = load("devices:\n  disabled: []\n", new ArrayList<>());
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("devices:\n  disabled: []\n");
        ServerSwitches written = ServerSwitches.load(config, saves::incrementAndGet, message -> { });
        written.setOff(DeviceKind.FURNACE, true);
        written.setOff(DeviceKind.HEAT_PUMP, true);
        written.setPollen(false);
        written.setWind(false);
        assertEquals(List.of("heat-pump", "furnace"), config.getStringList("devices.disabled"), "in the kinds' order");
        assertFalse(config.getBoolean("air-quality.pollen"));
        assertFalse(config.getBoolean("wind.enabled"));
        assertEquals(4, saves.get(), "each change is saved");
        written.setOff(DeviceKind.FURNACE, false);
        assertEquals(List.of("heat-pump"), config.getStringList("devices.disabled"));
        assertFalse(loaded.isOff(DeviceKind.HEAT_PUMP), "another server's switches are its own");
    }

    @Test
    void aKindSwitchedOffStopsItsUnitsAndKeepsTheirOwnSwitch() throws Exception {
        ServerSwitches loaded = load("devices:\n  disabled: []\n", new ArrayList<>());
        loaded.install();
        ClimateSettings settings = ClimateSettings.defaults();
        Humidifier humidifier = new Humidifier(new BlockKey(WORLD, 1, 65, 0), new BlockKey(WORLD, 0, 65, 0),
                new BlockKey(WORLD, 0, 66, 0), "nursery", 40);
        assertTrue(humidifier.control(20, settings));
        assertEquals(ClimateUnit.State.RUNNING, humidifier.state());

        loaded.setOff(DeviceKind.HUMIDIFIER, true);
        assertTrue(ServerSwitches.deviceOff(DeviceKind.HUMIDIFIER));
        assertTrue(humidifier.offServerWide());
        assertTrue(humidifier.control(20, settings), "it stops");
        assertFalse(humidifier.running());
        assertEquals(ClimateUnit.State.OFF, humidifier.state());
        assertTrue(humidifier.powered(), "its own switch is untouched");

        loaded.setOff(DeviceKind.HUMIDIFIER, false);
        assertTrue(humidifier.control(20, settings), "and it runs again");
        assertEquals(ClimateUnit.State.RUNNING, humidifier.state());
    }

    @Test
    void pollenOffClearsItAndSaysSo() throws Exception {
        ServerSwitches loaded = load("air-quality:\n  pollen: false\n", new ArrayList<>());
        loaded.install();
        AirQualitySettings settings = AirQualitySettings.defaults();
        AirQualityModel.Air pollenSeason = new AirQualityModel.Air(5.0, 10.0, 150.0);
        AirQualityModel.Air after = AirQualityModel.step(pollenSeason, pollenSeason, AirQualityModel.Loads.NONE,
                200L, 0.5, settings).air();
        assertEquals(0.0, after.pollen(), "a space's pollen is gone at once");
        assertEquals(5.0, after.fine(), 1.0, "the particles carry on");
        assertEquals("Off", AirText.pollenName(pollenSeason, settings));
        assertEquals("Off", AirText.pollenHudName(pollenSeason, settings));
        assertTrue(AirText.summary(pollenSeason, settings).endsWith("Pollen Off"));

        loaded.setPollen(true);
        assertEquals("High", AirText.pollenName(pollenSeason, settings));
    }
}
