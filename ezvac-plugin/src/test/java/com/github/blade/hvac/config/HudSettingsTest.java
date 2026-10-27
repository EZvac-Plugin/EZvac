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
        assertEquals(13, value.sidebarLines().size());
        assertTrue(value.sidebarLines().contains(HudSettings.AIR_LINE), "the air line (3.2)");
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
    @Test
    void aStockSidebarGainsTheSeasonLineWhenSeasonsArrive() {
        List<String> stock = new java.util.ArrayList<>(HudSettings.defaults().sidebarLines());
        stock.remove(HudSettings.SEASON_LINE);
        stock.remove(HudSettings.ADDONS_LINE);
        stock.remove(HudSettings.AIR_LINE);
        YamlConfiguration config = new YamlConfiguration();
        config.set("hud.sidebar.lines", stock);
        List<String> added = new java.util.ArrayList<>(List.of("seasons.enabled"));
        HudSettings.addSeasonLine(config, added);
        List<String> withSeason = new java.util.ArrayList<>(HudSettings.defaults().sidebarLines());
        withSeason.remove(HudSettings.ADDONS_LINE);
        withSeason.remove(HudSettings.AIR_LINE);
        assertEquals(withSeason, config.getStringList("hud.sidebar.lines"));
        assertTrue(added.contains("hud.sidebar.lines"));

        // A sidebar the server made its own is left alone, and so is one that already has seasons.
        YamlConfiguration own = new YamlConfiguration();
        own.set("hud.sidebar.lines", List.of("<gray>Here: <white>{temp}"));
        HudSettings.addSeasonLine(own, new java.util.ArrayList<>(List.of("seasons.enabled")));
        assertEquals(List.of("<gray>Here: <white>{temp}"), own.getStringList("hud.sidebar.lines"));
        YamlConfiguration later = new YamlConfiguration();
        later.set("hud.sidebar.lines", stock);
        HudSettings.addSeasonLine(later, new java.util.ArrayList<>(List.of("biomes")));
        assertEquals(stock, later.getStringList("hud.sidebar.lines"), "only when the seasons are new");
    }

    @Test
    void aStockSidebarGainsTheAirLineWhenAirQualityArrives() {
        // A 3.1 config: everything but the air line.
        List<String> stock = new java.util.ArrayList<>(HudSettings.defaults().sidebarLines());
        stock.remove(HudSettings.AIR_LINE);
        YamlConfiguration config = new YamlConfiguration();
        config.set("hud.sidebar.lines", stock);
        List<String> added = new java.util.ArrayList<>(List.of("air-quality.enabled"));
        HudSettings.addAirLine(config, added);
        List<String> lines = config.getStringList("hud.sidebar.lines");
        assertEquals(HudSettings.defaults().sidebarLines(), lines);
        assertEquals(lines.indexOf("<gray>Humidity: <white>{humidity}") + 1, lines.indexOf(HudSettings.AIR_LINE),
                "right under the humidity line");
        assertTrue(added.contains("hud.sidebar.lines"));

        YamlConfiguration own = new YamlConfiguration();
        own.set("hud.sidebar.lines", List.of("<gray>Here: <white>{temp}"));
        HudSettings.addAirLine(own, new java.util.ArrayList<>(List.of("air-quality.enabled")));
        assertEquals(List.of("<gray>Here: <white>{temp}"), own.getStringList("hud.sidebar.lines"),
                "a sidebar the server made its own is left alone");
        YamlConfiguration later = new YamlConfiguration();
        later.set("hud.sidebar.lines", stock);
        HudSettings.addAirLine(later, new java.util.ArrayList<>(List.of("seasons.enabled")));
        assertEquals(stock, later.getStringList("hud.sidebar.lines"), "only when air quality is new");
    }

    @Test
    void stockSidebarsGainTheAddonsLineWhenSectionsArrive() {
        // An ALPHA 2.0 config: no season line, no addons line, no air line, in air or water.
        List<String> air = new java.util.ArrayList<>(HudSettings.defaults().sidebarLines());
        air.remove(HudSettings.SEASON_LINE);
        air.remove(HudSettings.ADDONS_LINE);
        air.remove(HudSettings.AIR_LINE);
        List<String> water = new java.util.ArrayList<>(HudSettings.defaults().water().sidebarLines());
        water.remove(HudSettings.ADDONS_LINE);
        YamlConfiguration config = new YamlConfiguration();
        config.set("hud.sidebar.lines", air);
        config.set("hud.water.sidebar.lines", water);
        List<String> added = new java.util.ArrayList<>(List.of("seasons.enabled", "hud.sections.hidden",
                "air-quality.enabled"));
        HudSettings.addSeasonLine(config, added);
        HudSettings.addAddonsLine(config, added);
        HudSettings.addAirLine(config, added);
        assertEquals(HudSettings.defaults().sidebarLines(), config.getStringList("hud.sidebar.lines"));
        assertEquals(HudSettings.defaults().water().sidebarLines(), config.getStringList("hud.water.sidebar.lines"));
        List<String> lines = HudSettings.defaults().sidebarLines();
        assertEquals(lines.indexOf(HudSettings.SEASON_LINE) + 1, lines.indexOf(HudSettings.ADDONS_LINE),
                "right under the season line");
        assertTrue(added.contains("hud.water.sidebar.lines"));

        // An ALPHA 2.1 pilot config already has its season line: only the addons line is new.
        List<String> pilot = new java.util.ArrayList<>(HudSettings.defaults().sidebarLines());
        pilot.remove(HudSettings.ADDONS_LINE);
        pilot.remove(HudSettings.AIR_LINE);
        YamlConfiguration p4 = new YamlConfiguration();
        p4.set("hud.sidebar.lines", pilot);
        HudSettings.addAddonsLine(p4, new java.util.ArrayList<>(List.of("hud.sections.hidden")));
        List<String> withAddons = new java.util.ArrayList<>(HudSettings.defaults().sidebarLines());
        withAddons.remove(HudSettings.AIR_LINE);
        assertEquals(withAddons, p4.getStringList("hud.sidebar.lines"));

        // A server's own layout is left alone, and nothing happens on later upgrades.
        YamlConfiguration own = new YamlConfiguration();
        own.set("hud.sidebar.lines", List.of("<gray>Here: <white>{temp}"));
        HudSettings.addAddonsLine(own, new java.util.ArrayList<>(List.of("hud.sections.hidden")));
        assertEquals(List.of("<gray>Here: <white>{temp}"), own.getStringList("hud.sidebar.lines"));
        YamlConfiguration later = new YamlConfiguration();
        later.set("hud.sidebar.lines", pilot);
        HudSettings.addAddonsLine(later, new java.util.ArrayList<>(List.of("biomes")));
        assertEquals(pilot, later.getStringList("hud.sidebar.lines"), "only when sections are new");
    }

    @Test
    void hiddenSectionsAreReadInLowercase() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("hud.sections.hidden", List.of(" LiveExposure:Body ", ""));
        assertEquals(List.of("liveexposure:body"), HudSettings.load(config).hiddenSections());
        assertEquals(List.of(), HudSettings.defaults().hiddenSections());
    }
}
