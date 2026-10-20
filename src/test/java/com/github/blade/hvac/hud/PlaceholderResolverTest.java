package com.github.blade.hvac.hud;

import com.github.blade.hvac.config.HudSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderResolverTest {
    private final PlaceholderResolver resolver = new PlaceholderResolver(new HudFormatter(HudSettings.defaults()));
    private final ClimateSnapshot cooled =
            new ClimateSnapshot(72.5, 88.0, 0.6, 21, "house", "main", "COOLING", 70.0, false, false);

    @Test
    void everyKeyResolves() {
        Map<String, String> expected = new TreeMap<>(Map.ofEntries(
                Map.entry("temp", "72.5°F"), Map.entry("temp_f", "72.5°F"), Map.entry("temp_c", "22.5°C"),
                Map.entry("outdoor", "88.0°F"), Map.entry("outdoor_f", "88.0°F"),
                Map.entry("outdoor_c", "31.1°C"), Map.entry("bar_plain", "▮▮▮▮▮▯▯▯▯▯"),
                Map.entry("comfort", "Comfortable"), Map.entry("group", "house"),
                Map.entry("thermostat", "main"), Map.entry("mode", "Cooling to 70.0°F"),
                Map.entry("target", "70.0°F"), Map.entry("influence", "60"),
                Map.entry("zone", "60% leakage"), Map.entry("conditioned", "true"), Map.entry("medium", "air")));
        for (String key : PlaceholderResolver.KEYS) {
            String value = resolver.resolve(cooled, key);
            assertNotNull(value, key);
            if (key.equals("bar")) assertTrue(value.contains("▮▮▮▮▮") && value.contains("§"), value);
            else assertEquals(expected.get(key), value, key);
        }
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
