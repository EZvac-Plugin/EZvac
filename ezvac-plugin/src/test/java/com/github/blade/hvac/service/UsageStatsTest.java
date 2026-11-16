package com.github.blade.hvac.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** What the anonymous statistics report (BETA 1.0 Pilot 9): bands, never exact numbers, and addons by version. */
class UsageStatsTest {
    @Test
    void systemsAreReportedInBands() {
        assertEquals("0", UsageStats.systemsBand(0));
        assertEquals("1-10", UsageStats.systemsBand(1));
        assertEquals("1-10", UsageStats.systemsBand(10));
        assertEquals("11-50", UsageStats.systemsBand(11));
        assertEquals("51-200", UsageStats.systemsBand(200));
        assertEquals("201-1000", UsageStats.systemsBand(1000));
        assertEquals("1000+", UsageStats.systemsBand(1001));
    }

    @Test
    void addonsAreReportedByVersion() {
        assertEquals(Map.of("None", Map.of("None", 1)), UsageStats.addonVersions(List.of()));
        assertEquals(Map.of("EZclimates", Map.of("0.3.1", 1), "MapPeek", Map.of("unknown", 1)),
                UsageStats.addonVersions(List.of(Map.entry("EZclimates", "0.3.1"), Map.entry("MapPeek", ""))));
    }

    @Test
    void itIsEZvacOnBstats() {
        assertEquals(35377, UsageStats.PLUGIN_ID);
    }
}
