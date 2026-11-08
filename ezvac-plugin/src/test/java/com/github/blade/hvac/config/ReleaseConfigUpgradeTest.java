package com.github.blade.hvac.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    /**
     * Stock values Pilot 2 moves on a server's first Pilot 2 start, checked on
     * their own below: the humidifier's deeper bubbles, and backups going on.
     */
    private static final Set<String> PILOT_2_MOVES = Set.of("audio.sounds.humidifier-bubbles",
            "backups.auto-every-hours",
            // Pilot 4: the stock 15-day seasons move to 30, and water's stock lag with them.
            "seasons.days-per-season", "seasons.water-lag-days");

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
            if (old.isConfigurationSection(path) || STOCK_LAYOUTS.contains(path) || PILOT_2_MOVES.contains(path))
                continue;
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

        // Pilot 2: the stock humidifier bubbles go deeper, and automatic backups go on (Pilot 1's 0 included).
        assertEquals(List.of("block.bubble_column.upwards_ambient 0.35 0.5"),
                config.getStringList("audio.sounds.humidifier-bubbles"), "the humidifier's bubbles are deeper");
        assertEquals(24, config.getInt("backups.auto-every-hours"), "automatic backups are on");
        assertEquals("auto", config.getString("updates.channel"));

        // Pilot 4: stock 15-day seasons move to 30, natural water's stock 5-day lag to 10.
        assertEquals(30, config.getInt("seasons.days-per-season"), "seasons are 30 days");
        assertEquals(10.0, config.getDouble("seasons.water-lag-days"), "water lags 10 days");

        // Building size changes how existing builds heat and cool: it starts off on every server upgrading
        // from ALPHA. A BETA config already has the setting, and keeps its own.
        boolean sized = old.contains("sizing.enabled");
        boolean expected = sized && old.getBoolean("sizing.enabled");
        assertEquals(expected, config.getBoolean("sizing.enabled", true),
                sized ? "a release with building size keeps its own" : "building size starts off on an upgrade");

        YamlConfiguration saved = new YamlConfiguration();
        saved.loadFromString(config.saveToString());
        assertTrue(ConfigUpdater.upgrade(saved, bundled).isEmpty(), "the next start finds nothing to add");
        assertEquals(expected, saved.getBoolean("sizing.enabled", true), "and keeps it until the owner switches it");
    }

    @Test
    void aNewInstallHasBuildingSizeOn() throws Exception {
        assertTrue(load("/config.yml").getBoolean("sizing.enabled"), "the bundled config.yml");
        YamlConfiguration chosen = load("/config.yml");
        chosen.set("sizing.enabled", true);
        assertTrue(ConfigUpdater.upgrade(chosen, load("/config.yml")).isEmpty());
        assertTrue(chosen.getBoolean("sizing.enabled"), "a server that has the setting keeps its own");
    }

    @Test
    void anOwnersOwnSoundAndBackupsAreKept() throws Exception {
        YamlConfiguration chosen = load("/configs/BETA-1.0-P1.yml");
        chosen.set("audio.sounds.humidifier-bubbles", List.of("block.bubble_column.upwards_ambient 0.2 1.4"));
        chosen.set("backups.auto-every-hours", 48);
        ConfigUpdater.upgrade(chosen, load("/config.yml"));
        assertEquals(List.of("block.bubble_column.upwards_ambient 0.2 1.4"),
                chosen.getStringList("audio.sounds.humidifier-bubbles"));
        assertEquals(48, chosen.getInt("backups.auto-every-hours"));
    }

    @Test
    void anOwnersOwnSeasonLengthIsKept() throws Exception {
        YamlConfiguration chosen = load("/configs/BETA-1.0-P3.yml");
        chosen.set("seasons.days-per-season", 20);
        chosen.set("seasons.water-lag-days", 5.0);
        ConfigUpdater.upgrade(chosen, load("/config.yml"));
        assertEquals(20, chosen.getInt("seasons.days-per-season"), "a length the owner chose");
        assertEquals(5.0, chosen.getDouble("seasons.water-lag-days"), "and the lag with it");
        YamlConfiguration lagged = load("/configs/BETA-1.0-P3.yml");
        lagged.set("seasons.water-lag-days", 3.0);
        ConfigUpdater.upgrade(lagged, load("/config.yml"));
        assertEquals(30, lagged.getInt("seasons.days-per-season"));
        assertEquals(3.0, lagged.getDouble("seasons.water-lag-days"), "an owner's own lag is kept");
    }

    @Test
    void theStepsRunOnlyOnTheFirstPilotFourStart() throws Exception {
        YamlConfiguration current = load("/config.yml");
        current.set("seasons.days-per-season", 15);
        assertTrue(ConfigUpdater.upgrade(current, load("/config.yml")).isEmpty());
        assertEquals(15, current.getInt("seasons.days-per-season"), "set back to 15 after Pilot 4: it stays");
    }

    @Test
    void theStepsRunOnlyOnTheFirstPilotTwoStart() throws Exception {
        YamlConfiguration current = load("/config.yml");
        current.set("backups.auto-every-hours", 0);
        assertTrue(ConfigUpdater.upgrade(current, load("/config.yml")).isEmpty());
        assertEquals(0, current.getInt("backups.auto-every-hours"), "switched off after Pilot 2: it stays off");
    }
}
