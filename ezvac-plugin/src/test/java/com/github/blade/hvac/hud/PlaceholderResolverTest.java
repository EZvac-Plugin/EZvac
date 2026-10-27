package com.github.blade.hvac.hud;

import com.github.blade.hvac.config.HudSettings;
import com.github.blade.hvac.simulation.AirQualityModel;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderResolverTest {
    private final PlaceholderResolver resolver = new PlaceholderResolver(new HudFormatter(HudSettings.defaults()));
    private final ClimateSnapshot cooled =
            new ClimateSnapshot(72.5, 88.0, 0.6, 21, "house", "main", "COOLING", 70.0, false, false,
                    45.0, 73.4, "Rain", "Summer", 3);

    @Test
    void everyKeyResolves() {
        Map<String, String> expected = new TreeMap<>(Map.ofEntries(
                Map.entry("temp", "72.5°F"), Map.entry("temp_f", "72.5°F"), Map.entry("temp_c", "22.5°C"),
                Map.entry("outdoor", "88.0°F"), Map.entry("outdoor_f", "88.0°F"),
                Map.entry("outdoor_c", "31.1°C"), Map.entry("bar_plain", "▮▮▮▮▮▯▯▯▯▯"),
                Map.entry("comfort", "Comfortable"), Map.entry("system", "house"), Map.entry("group", "house"),
                Map.entry("thermostat", "main"), Map.entry("mode", "Cooling to 70.0°F"),
                Map.entry("target", "70.0°F"), Map.entry("influence", "60"),
                Map.entry("zone", "60% leakage"), Map.entry("conditioned", "true"), Map.entry("medium", "air"),
                Map.entry("feels_like", "73.4°F"), Map.entry("feels_like_f", "73.4°F"),
                Map.entry("feels_like_c", "23.0°C"), Map.entry("humidity", "45%"), Map.entry("weather", "Rain"),
                Map.entry("season", "Summer"), Map.entry("season_day", "3"),
                // No air was measured here: air quality off, or before its first cycle.
                Map.entry("aqi", "none"), Map.entry("aqi_colored", "none"), Map.entry("aqi_band", ""),
                Map.entry("pm25", "none"), Map.entry("pm10", "none"), Map.entry("pollen", "none"),
                Map.entry("pollen_count", "none")));
        for (String key : PlaceholderResolver.KEYS) {
            String value = resolver.resolve(cooled, key);
            assertNotNull(value, key);
            if (key.equals("bar")) assertTrue(value.contains("▮▮▮▮▮") && value.contains("§"), value);
            else assertEquals(expected.get(key), value, key);
        }
    }

    @Test
    void theAirKeysReadTheAir() {
        ClimateSnapshot aired = cooled.withAir(new AirQualityModel.Air(12.0, 20.0, 90.0), "High");
        // PM2.5 12.0 scores 56 and PM10 32 scores 30, so the AQI is 56: Moderate.
        assertEquals("56", resolver.resolve(aired, "aqi"));
        assertEquals("§e56§r", resolver.resolve(aired, "aqi_colored"));
        assertEquals("Moderate", resolver.resolve(aired, "aqi_band"));
        assertEquals("12.0", resolver.resolve(aired, "pm25"));
        assertEquals("32", resolver.resolve(aired, "pm10"));
        assertEquals("High", resolver.resolve(aired, "pollen"));
        assertEquals("90", resolver.resolve(aired, "pollen_count"));
        HudFormatter format = new HudFormatter(HudSettings.defaults());
        assertEquals("<gray>Air: <yellow>56</yellow> <white>Moderate <gray>· Pollen <white>High",
                format.fill(HudSettings.AIR_LINE, aired));
        assertEquals("<gray>Air: none <white> <gray>· Pollen <white>none", format.fill(HudSettings.AIR_LINE, cooled));
    }

    @Test
    void keysAreCaseInsensitive() {
        assertEquals("house", resolver.resolve(cooled, "GROUP"));
    }

    @Test
    void unknownKeysAreLeftForPlaceholderApi() {
        assertNull(resolver.resolve(cooled, "nonsense"));
        assertNull(resolver.resolve(cooled, null));
    }

    @Test
    void aPlayerWithoutASnapshotYetGetsTheNoneText() {
        assertEquals("none", resolver.resolve(null, "temp"));
        assertNull(resolver.resolve(null, "nonsense"));
    }

    @Test
    void waterReadingsReportTheirMedium() {
        ClimateSnapshot pool = new ClimateSnapshot(84.0, 90.0, 1.0, 2, "pool", "spa", "HEATING", 84.0, true, true);
        assertEquals("water", resolver.resolve(pool, "medium"));
        assertEquals("Comfortable", resolver.resolve(pool, "comfort"));
        assertEquals("Core", resolver.resolve(pool, "zone"));
    }

    @Test
    void outdoorSpotsAreNotConditioned() {
        ClimateSnapshot outside = ClimateSnapshot.outdoors(91.0);
        assertEquals("false", resolver.resolve(outside, "conditioned"));
        assertEquals("none", resolver.resolve(outside, "group"));
        assertEquals("0", resolver.resolve(outside, "influence"));
        assertEquals("Hot", resolver.resolve(outside, "comfort"));
    }
}
