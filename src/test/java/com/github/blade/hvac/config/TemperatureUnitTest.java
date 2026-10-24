package com.github.blade.hvac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The plugin-wide °F/°C switch (2.1). */
class TemperatureUnitTest {
    @Test
    void readingsShowInTheChosenUnit() {
        assertEquals("72.4 F", TemperatureUnit.F.format(72.4));
        assertEquals("22.4 C", TemperatureUnit.C.format(72.32));
        assertEquals("0.0 C", TemperatureUnit.C.format(32));
        assertEquals("22C", TemperatureUnit.C.whole(71.6));
        assertEquals("22.0C", TemperatureUnit.C.compact(71.6));
        assertEquals("72.4°F", TemperatureUnit.F.degrees(72.4));
        assertEquals("16 C", TemperatureUnit.C.format(60, 0));
    }

    @Test
    void aPlainNumberIsInTheServersUnitAndASuffixWins() {
        assertEquals(71.6, TemperatureUnit.C.parse("22"), 1e-9);
        assertEquals(72.0, TemperatureUnit.F.parse("72"), 1e-9);
        assertEquals(72.0, TemperatureUnit.C.parse("72F"), 1e-9, "72F means 72 F even on a Celsius server");
        assertEquals(71.6, TemperatureUnit.F.parse("22 c"), 1e-9);
        assertEquals(71.6, TemperatureUnit.F.parse("22°C"), 1e-9);
        assertEquals(23.0, TemperatureUnit.C.parse("-5"), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> TemperatureUnit.F.parse("warm"));
        assertThrows(IllegalArgumentException.class, () -> TemperatureUnit.F.parse("72K"));
        assertThrows(IllegalArgumentException.class, () -> TemperatureUnit.F.parse(""));
    }

    @Test
    void aSignLineIsSearchedForATemperature() {
        assertEquals(71.6, TemperatureUnit.C.find("Set 22").orElseThrow(), 1e-9);
        assertEquals(72.0, TemperatureUnit.C.find("Set 72F please").orElseThrow(), 1e-9);
        assertTrue(TemperatureUnit.F.find("no number").isEmpty());
    }

    @Test
    void aTargetRoundTripsAtDisplayPrecision() {
        double stored = TemperatureUnit.C.parse("22.5");
        assertEquals("22.5 C", TemperatureUnit.C.format(stored));
    }

    @Test
    void theSettingIsReadLeniently() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> warnings = new ArrayList<>();
        assertEquals(TemperatureUnit.F, TemperatureUnit.load(config, warnings::add), "missing means F");
        config.set("units", "c");
        assertEquals(TemperatureUnit.C, TemperatureUnit.load(config, warnings::add));
        config.set("units", "kelvin");
        assertEquals(TemperatureUnit.F, TemperatureUnit.load(config, warnings::add));
        assertEquals(1, warnings.size(), String.valueOf(warnings));
    }

    @Test
    void aCelsiusHudCarriesOverToThePluginWideSwitch() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString("hud:\n  units: C\n");
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.loadFromString("units: F\nhud:\n  enabled: true\n");
        List<String> added = ConfigUpdater.addMissing(old, bundled);
        TemperatureUnit.carryOver(old, added);
        assertEquals("C", old.getString("units"));
        assertEquals("C", old.getString("hud.units"), "the old setting stays, it is just no longer read");
        assertTrue(old.saveToString().contains("No longer read"));

        YamlConfiguration fahrenheit = new YamlConfiguration();
        fahrenheit.loadFromString("hud:\n  units: F\n");
        TemperatureUnit.carryOver(fahrenheit, ConfigUpdater.addMissing(fahrenheit, bundled));
        assertEquals("F", fahrenheit.getString("units"));
    }
}
