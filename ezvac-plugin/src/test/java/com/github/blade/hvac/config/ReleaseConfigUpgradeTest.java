package com.github.blade.hvac.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every released config.yml, as a fresh server of that release had it,
 * upgraded the way a start of this build upgrades it (BETA 1.0). The files
 * under configs/ are the ones bundled in each release's jar from GitHub, so a
 * server that skipped every release in between still ends up current.
 */
class ReleaseConfigUpgradeTest {
    /** Lists the stock-layout steps move on to today's; every other value a server had must stay. */
    private static final Set<String> STOCK_LAYOUTS = Set.of("hud.sidebar.lines", "hud.water.sidebar.lines");

    static Stream<String> releases() throws IOException, URISyntaxException {
        Path folder = Path.of(Objects.requireNonNull(ReleaseConfigUpgradeTest.class.getResource("/configs")).toURI());
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".yml")).map(name -> name.substring(0, name.length() - 4))
                    .sorted().toList().stream();
        }
    }

    private static YamlConfiguration load(String resource) throws IOException, InvalidConfigurationException {
        try (InputStream input = Objects.requireNonNull(ReleaseConfigUpgradeTest.class.getResourceAsStream(resource),
                resource)) {
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return config;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releases")
    void aReleasesConfigUpgradesToTodays(String release) throws Exception {
        YamlConfiguration old = load("/configs/" + release + ".yml");
        YamlConfiguration config = load("/configs/" + release + ".yml");
        YamlConfiguration bundled = load("/config.yml");

        ConfigUpdater.upgrade(config, bundled);

        List<String> missing = bundled.getKeys(true).stream()
                .filter(path -> !bundled.isConfigurationSection(path) && !config.contains(path, true)).toList();
        assertEquals(List.of(), missing, "every setting of today's config.yml is there");

        for (String path : old.getKeys(true)) {
            if (old.isConfigurationSection(path) || STOCK_LAYOUTS.contains(path)) continue;
            assertEquals(old.get(path), config.get(path), "the release's own " + path + " is kept");
        }

        List<String> warnings = new ArrayList<>();
        HvacSettings.load(config, warnings::add);
        ClimateSettings.load(config, warnings::add);
        SoundSettings.load(config, warnings::add);
        TemperatureUnit.load(config, warnings::add);
        HudSettings hud = HudSettings.load(config);
        assertEquals(List.of(), warnings, "it loads without a warning");

        if (old.contains("hud.sidebar.lines"))
            assertEquals(HudSettings.defaults().sidebarLines(), hud.sidebarLines(), "its stock sidebar is today's");
        if (old.contains("hud.water.sidebar.lines"))
            assertEquals(HudSettings.defaults().water().sidebarLines(), hud.water().sidebarLines(),
                    "its stock water sidebar is today's");

        YamlConfiguration saved = new YamlConfiguration();
        saved.loadFromString(config.saveToString());
        assertTrue(ConfigUpdater.upgrade(saved, bundled).isEmpty(), "the next start finds nothing to add");
    }
}
