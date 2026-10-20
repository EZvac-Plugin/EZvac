package com.github.blade.hvac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
}
