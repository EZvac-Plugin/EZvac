package com.github.blade.hvac.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry backups (BETA 1.0): their names, the automatic schedule and what is pruned. */
class RegistryBackupsTest {
    private static final long HOUR = 60L * 60 * 1000;

    @Test
    void theIntervalStepsThroughEachAndBackToOff() {
        int hours = 0;
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < RegistryBackups.INTERVALS.size(); i++) {
            hours = RegistryBackups.nextInterval(hours);
            seen.append(hours).append(' ');
        }
        assertEquals("4 8 12 24 48 168 0 ", seen.toString());
        assertEquals(4, RegistryBackups.nextInterval(5), "an odd value starts the cycle over");
        assertEquals("Off", RegistryBackups.describe(0));
        assertEquals("Every 8 hours", RegistryBackups.describe(8));
        assertEquals("Every week", RegistryBackups.describe(168));
    }

    @Test
    void namesSortInTimeOrder() {
        assertEquals("hvac-2026-10-29_14-05-00-auto.yml", RegistryBackups.name("hvac", "2026-10-29_14-05-00", true));
        assertEquals("seasons-2026-10-29_14-05-00-manual.yml",
                RegistryBackups.name("seasons", "2026-10-29_14-05-00", false));
        List<String> folder = List.of("hvac-2026-10-30_02-00-00-auto.yml", "seasons-2026-10-29_14-00-00-auto.yml",
                "hvac-2026-10-29_14-00-00-auto.yml", "hvac-2026-10-29_20-00-00-manual.yml", "notes.txt",
                "hvac-2026-10-30_02-00-00-auto.yml.tmp");
        assertEquals(List.of("hvac-2026-10-29_14-00-00-auto.yml", "hvac-2026-10-30_02-00-00-auto.yml"),
                RegistryBackups.backups(folder, true));
        assertEquals(List.of("hvac-2026-10-29_20-00-00-manual.yml"), RegistryBackups.backups(folder, false));
    }

    @Test
    void onlyTheOldestAutomaticOnesArePruned() {
        List<String> folder = List.of("hvac-2026-10-01_00-00-00-manual.yml", "hvac-2026-10-02_00-00-00-auto.yml",
                "hvac-2026-10-03_00-00-00-auto.yml", "hvac-2026-10-04_00-00-00-auto.yml",
                "hvac-2026-10-05_00-00-00-auto.yml");
        assertEquals(List.of("hvac-2026-10-02_00-00-00-auto.yml", "hvac-2026-10-03_00-00-00-auto.yml"),
                RegistryBackups.toPrune(folder, 2));
        assertEquals(List.of(), RegistryBackups.toPrune(folder, 4));
        assertEquals(List.of(), RegistryBackups.toPrune(folder, 10));
    }

    @Test
    void aBackupIsDueOnceTheNewestIsTheIntervalOld() {
        long now = 1_000 * HOUR;
        assertTrue(RegistryBackups.due(0L, now, 24), "none yet");
        assertFalse(RegistryBackups.due(now - 23 * HOUR, now, 24));
        assertTrue(RegistryBackups.due(now - 24 * HOUR, now, 24));
        assertTrue(RegistryBackups.due(now - 200 * HOUR, now, 168), "after a long stop, at once");
        assertFalse(RegistryBackups.due(0L, now, 0), "off is never due");
    }
}
