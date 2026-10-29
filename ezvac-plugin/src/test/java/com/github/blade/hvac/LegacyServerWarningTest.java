package com.github.blade.hvac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyServerWarningTest {

    @Test
    void a1211ServerIsWarnedThatBeta2DropsIt() {
        String warning = HvacPlugin.legacyServerWarning("1.21.1");
        assertNotNull(warning);
        assertTrue(warning.contains("1.21.1") && warning.contains("BETA 2.x"), warning);
        String later = HvacPlugin.legacyServerWarning("1.21.4");
        assertTrue(later.contains("Minecraft 1.21.4") && later.contains("all of 1.21.x")
                && later.contains("26.1 or newer"), "a later 1.21 is told its whole line goes: " + later);
    }

    @Test
    void the26LineIsNotWarned() {
        assertNull(HvacPlugin.legacyServerWarning("26.1"));
        assertNull(HvacPlugin.legacyServerWarning("26.1.2"));
        assertNull(HvacPlugin.legacyServerWarning("26.3"));
        assertNull(HvacPlugin.legacyServerWarning(null));
    }
}
