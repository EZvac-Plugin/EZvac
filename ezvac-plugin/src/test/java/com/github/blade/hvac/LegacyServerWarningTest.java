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
    }

    @Test
    void the26LineIsNotWarned() {
        assertNull(HvacPlugin.legacyServerWarning("26.1"));
        assertNull(HvacPlugin.legacyServerWarning("26.1.2"));
        assertNull(HvacPlugin.legacyServerWarning("26.3"));
        assertNull(HvacPlugin.legacyServerWarning(null));
    }
}
