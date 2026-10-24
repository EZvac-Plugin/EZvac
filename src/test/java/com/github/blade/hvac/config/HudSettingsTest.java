package com.github.blade.hvac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudSettingsTest {
    @Test
    void defaultsAreValidAndUseTheSidebar() {
        HudSettings value = HudSettings.defaults();
        assertTrue(value.enabled());
        assertEquals(HudSettings.Style.SIDEBAR, value.style());
        assertEquals(10, value.updateTicks());
        assertFalse(value.celsius());
        assertEquals(10, value.sidebarLines().size());
    }

    @Test
    void aConfigWithoutAHudSectionLoadsTheDefaults() {
        YamlConfiguration old = new YamlConfiguration();
        old.set("airflow.maximum-range", 30);   // a pre-1.8 config.yml
        assertEquals(HudSettings.defaults(), HudSettings.load(old));
    }

    @Test
    void theBundledConfigMatchesTheDefaults() throws Exception {
        YamlConfiguration bundled = new YamlConfiguration();
        try (var reader = new java.io.InputStreamReader(
                java.util.Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
                java.nio.charset.StandardCharsets.UTF_8)) {
            bundled.load(reader);
        }
        assertEquals(HudSettings.defaults(), HudSettings.load(bundled));
    }

    @Test
    void customValuesLoad() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("hud.style", "boss-bar");
        config.set("units", "c");
        config.set("hud.update-ticks", 20);
        config.set("hud.sidebar.lines", List.of("{temp}", "{group}"));
        config.set("hud.comfort.hot.above-f", 90.0);
        config.set("hud.comfort.hot.label", "Toasty");
        HudSettings value = HudSettings.load(config);
        assertEquals(HudSettings.Style.BOSSBAR, value.style());
        assertTrue(value.celsius());
        assertEquals(20, value.updateTicks());
        assertEquals(List.of("{temp}", "{group}"), value.sidebarLines());
        assertEquals(90.0, value.hot().limitF());
        assertEquals("Toasty", value.hot().label());
        assertEquals("#ff5c5c", value.hot().color(), "unspecified fields keep their default");
    }

    @Test
    void theWaterMediumHasItsOwnDefaultsAndLoadsOverrides() {
        HudSettings.Medium water = HudSettings.defaults().water();
        assertEquals("Hot tub", water.hot().label());
        assertEquals(95.0, water.hot().limitF());
        assertEquals(60.0, water.barColdF());
        assertTrue(water.sidebarLines().get(0).contains("Water"));

        YamlConfiguration config = new YamlConfiguration();
        config.set("hud.water.comfort.hot.above-f", 100.0);
        config.set("hud.water.sidebar.lines", List.of("{temp}"));
        HudSettings loaded = HudSettings.load(config);
        assertEquals(100.0, loaded.water().hot().limitF());
        assertEquals(List.of("{temp}"), loaded.water().sidebarLines());
        assertEquals(HudSettings.defaults().sidebarLines(), loaded.sidebarLines(), "air is untouched");
    }

    @Test
    void invalidValuesAreRejectedWithTheirKey() {
        assertRejected("hud.style", "sideways", "hud.style");
        assertRejected("hud.update-ticks", 0, "hud.update-ticks");
        assertRejected("hud.bar.length", 0, "hud.bar.length");
        assertRejected("hud.bar.hot-f", 40.0, "hud.bar.cold-f");
        assertRejected("hud.comfort.cold.color", "blue", "#RRGGBB");
        assertRejected("hud.comfort.warm.above-f", 50.0, "hud.comfort");
        assertRejected("hud.sidebar.lines", List.of(), "hud.sidebar.lines");
        assertRejected("hud.sidebar.lines", java.util.Collections.nCopies(16, "x"), "hud.sidebar.lines");
        assertRejected("hud.water.bar.hot-f", 10.0, "cold-f must be below hot-f");
        assertRejected("hud.water.comfort.warm.above-f", 20.0, "hud comfort limits");
    }

    private static void assertRejected(String key, Object value, String messagePart) {
        YamlConfiguration config = new YamlConfiguration();
        config.set(key, value);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> HudSettings.load(config));
        assertTrue(error.getMessage().contains(messagePart), () -> key + ": " + error.getMessage());
    }
}
