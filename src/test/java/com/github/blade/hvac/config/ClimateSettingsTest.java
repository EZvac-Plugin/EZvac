package com.github.blade.hvac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ClimateSettingsTest {
    @Test
    void anOlderConfigWithoutTheNewSectionsGetsDefaults() {
        YamlConfiguration old = new YamlConfiguration();
        old.set("airflow.maximum-range", 30);   // a 1.9 config.yml
        assertEquals(ClimateSettings.defaults(), ClimateSettings.load(old));
    }

    @Test
    void theBundledConfigMatchesTheDefaults() throws Exception {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.load(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
                StandardCharsets.UTF_8));
        assertEquals(ClimateSettings.defaults(), ClimateSettings.load(bundled));
    }

    @Test
    void presetsCycleAndWrap() {
        ClimateSettings.Dehumidifier settings = ClimateSettings.defaults().dehumidifier();
        assertEquals(50, settings.nextPreset(45));
        assertEquals(35, settings.nextPreset(60), "wraps to the lowest");
        assertEquals(45, settings.nextPreset(42), "an odd value moves to the next preset up");
    }

    @Test
    void invalidValuesAreRejected() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("weather.swing-reduction", 1.5);
        assertThrows(IllegalArgumentException.class, () -> ClimateSettings.load(config));
        YamlConfiguration empty = new YamlConfiguration();
        empty.set("dehumidifier.presets", List.of());
        assertThrows(IllegalArgumentException.class, () -> ClimateSettings.load(empty));
    }

    @Test
    void onStartupABadValueOnlyCostsItsOwnSection() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("variance.indoor-f", -0.5);
        config.set("dehumidifier.presets", List.of());
        config.set("humidifier.range", 12);
        config.set("weather.ease-hours", 4.0);
        List<String> warnings = new ArrayList<>();
        ClimateSettings loaded = ClimateSettings.load(config, warnings::add);
        assertEquals(ClimateSettings.defaults().variance(), loaded.variance(), "a bad section falls back");
        assertEquals(ClimateSettings.defaults().dehumidifier(), loaded.dehumidifier());
        assertEquals(12, loaded.humidifier().range(), "every other section loads as written");
        assertEquals(4.0, loaded.weather().easeHours());
        assertEquals(2, warnings.size(), String.valueOf(warnings));
        assertTrue(warnings.get(0).contains("dehumidifier.presets") && warnings.get(0).contains("default"),
                warnings.get(0));
        assertTrue(warnings.get(1).contains("variance.indoor-f"), warnings.get(1));
    }
}
