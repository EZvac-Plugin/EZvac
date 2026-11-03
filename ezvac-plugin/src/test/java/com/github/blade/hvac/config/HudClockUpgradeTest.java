package com.github.blade.hvac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The stock sidebar's season line gains the time of day (BETA 1.0 Pilot 3). */
class HudClockUpgradeTest {
    @Test
    void aStockBetaSidebarGainsTheClockAndAServersOwnDoesNot() {
        YamlConfiguration stock = new YamlConfiguration();
        stock.set("hud.sidebar.lines", HudSettings.SECTIONED_BEFORE_CLOCK);
        List<String> added = new ArrayList<>();
        HudSettings.adoptAccents(stock, added);
        assertTrue(stock.getStringList("hud.sidebar.lines").contains(HudSettings.SEASON_CLOCK_LINE));
        assertEquals(List.of("hud.sidebar.lines"), added);

        YamlConfiguration own = new YamlConfiguration();
        List<String> mine = new ArrayList<>(HudSettings.SECTIONED_BEFORE_CLOCK);
        mine.add("<gray>My line");
        own.set("hud.sidebar.lines", mine);
        HudSettings.adoptAccents(own, new ArrayList<>());
        assertEquals(mine, own.getStringList("hud.sidebar.lines"), "left as it is");
    }
}
