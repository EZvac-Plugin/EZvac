package com.github.blade.hvac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ConfigUpdaterTest {
    private YamlConfiguration resource(String name) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(name)),
                StandardCharsets.UTF_8));
        return config;
    }

    private static YamlConfiguration parse(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config;
    }

    /** A real config.yml written by 1.7 and carried through upgrades to 2.0 Pilot 1. */
    @Test
    void aPre18ConfigGainsEverySettingAddedSinceWithoutChangingItsOwn() throws Exception {
        YamlConfiguration old = resource("/config/pre-1.8-config.yml");
        Map<String, Object> before = old.getValues(true);
        YamlConfiguration bundled = resource("/config.yml");

        List<String> added = ConfigUpdater.addMissing(old, bundled);

        assertTrue(added.contains("temperature.pool-minimum-target-f"), "a missing setting in an existing section");
        assertTrue(added.contains("dehumidifier.range"));
        assertEquals(List.of("temperature", "weather", "humidity", "dehumidifier", "hud"),
                ConfigUpdater.topLevel(added).stream().filter(s -> !s.equals("humidifier")).toList());
        for (Map.Entry<String, Object> entry : before.entrySet())
            if (!old.isConfigurationSection(entry.getKey()))
                assertEquals(entry.getValue(), old.get(entry.getKey()), entry.getKey() + " must not change");
        // Every setting the bundled file has, the updated one now has, so it loads exactly as defaults.
        for (String path : bundled.getKeys(true)) assertTrue(old.contains(path, true), path);
        assertEquals(ClimateSettings.defaults(), ClimateSettings.load(old));
        assertEquals(HudSettings.defaults(), HudSettings.load(old));

        String written = old.saveToString();
        assertTrue(written.contains("# Weather changes the outdoor air"), "new sections bring their comments");
        assertTrue(written.contains("# Heat pumps and air conditioners slow down"), "existing comments stay");
        assertTrue(ConfigUpdater.addMissing(parse(written), bundled).isEmpty(), "a second run adds nothing");
    }

    @Test
    void customValuesAndListsAreNeverTouched() throws Exception {
        YamlConfiguration custom = parse("""
                dehumidifier:
                  range: 12
                hud:
                  sidebar:
                    lines:
                      - "Just one line"
                """);
        List<String> added = ConfigUpdater.addMissing(custom, resource("/config.yml"));
        assertFalse(added.contains("dehumidifier.range"));
        assertEquals(12, custom.getInt("dehumidifier.range"), "a changed default does not overwrite the server's value");
        assertEquals(List.of("Just one line"), custom.getStringList("hud.sidebar.lines"));
        assertTrue(added.contains("dehumidifier.presets"));
    }

    @Test
    void aValueIsNeverReplacedByASection() throws Exception {
        YamlConfiguration odd = parse("weather: false\n");
        List<String> added = ConfigUpdater.addMissing(odd, resource("/config.yml"));
        assertFalse(odd.getBoolean("weather", true));
        assertTrue(added.stream().noneMatch(path -> path.startsWith("weather.")));
    }

    @Test
    void anUpToDateConfigIsLeftAlone() throws Exception {
        assertTrue(ConfigUpdater.addMissing(resource("/config.yml"), resource("/config.yml")).isEmpty());
    }
}
