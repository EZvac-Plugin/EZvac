package com.github.blade.hvac.command;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The mode-time line in /hvac info (BETA 1.0 Pilot 8). */
class ModeTimesTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");

    @Test
    void durationsReadNaturally() {
        assertEquals("45 s", ModeTimes.duration(45_000));
        assertEquals("12 min", ModeTimes.duration(12 * 60_000L));
        assertEquals("3 h 05 min", ModeTimes.duration((3 * 60 + 5) * 60_000L));
        assertEquals("2 days", ModeTimes.duration(50 * 3_600_000L));
    }

    @Test
    void theLineSaysHowLongAndTheLastRun() {
        Thermostat thermostat = new Thermostat(new BlockKey(WORLD, 0, 64, 0), "Home", new SystemId(WORLD, "#1"),
                72, 70, HvacSettings.defaults(), false);
        assertEquals("", ModeTimes.line(thermostat, System.currentTimeMillis(), ZoneOffset.UTC), "nothing known yet");
        thermostat.setControlState(OperatingMode.COOLING, 0);
        thermostat.setControlState(OperatingMode.IDLE, 0);
        long now = thermostat.modeSinceMillis() + 12 * 60_000L;
        String line = ModeTimes.line(thermostat, now, ZoneOffset.UTC);
        assertTrue(line.startsWith("Idle for 12 min · last cooled "), line);
        assertTrue(line.matches(".* \\d\\d:\\d\\d to \\d\\d:\\d\\d"), line);
    }
}
