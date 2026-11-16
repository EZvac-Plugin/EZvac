package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.ThermostatView;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** When a thermostat went into its mode and its last run (API 1.13, EZvac BETA 1.0 Pilot 8). */
class ModeTimesTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final HvacSettings SETTINGS = HvacSettings.defaults();

    private static Thermostat thermostat() {
        return new Thermostat(new BlockKey(WORLD, 0, 64, 0), "Home", new SystemId(WORLD, "#1"),
                Thermostat.ROOM_DEFAULT_F, 70, SETTINGS, false);
    }

    @Test
    void aModeChangeIsTimedAndAFinishedRunKept() throws Exception {
        Thermostat thermostat = thermostat();
        long before = System.currentTimeMillis();
        thermostat.setControlState(OperatingMode.HEATING, 0);
        long heatingSince = thermostat.modeSinceMillis();
        assertTrue(heatingSince >= before);
        assertEquals(OperatingMode.IDLE, thermostat.lastRunMode(), "no run has ended yet");

        thermostat.setControlState(OperatingMode.HEATING, 400);
        assertEquals(heatingSince, thermostat.modeSinceMillis(), "the same mode keeps its start");

        Thread.sleep(5);
        thermostat.setControlState(OperatingMode.IDLE, 0);
        assertEquals(OperatingMode.HEATING, thermostat.lastRunMode());
        assertEquals(heatingSince, thermostat.lastRunStartMillis());
        assertTrue(thermostat.lastRunEndMillis() > heatingSince);
        assertEquals(thermostat.lastRunEndMillis(), thermostat.modeSinceMillis(), "idle since the run ended");

        thermostat.setControlState(OperatingMode.COOLING, 0);
        assertEquals(OperatingMode.HEATING, thermostat.lastRunMode(), "idle isn't a run");
    }

    @Test
    void theyAreSavedAndShownThroughTheApi() {
        Thermostat thermostat = thermostat();
        thermostat.setControlState(OperatingMode.COOLING, 0);
        thermostat.setControlState(OperatingMode.IDLE, 0);
        Thermostat loaded = Thermostat.fromMap(thermostat.toMap(), SETTINGS);
        assertEquals(thermostat.modeSinceMillis(), loaded.modeSinceMillis());
        assertEquals(OperatingMode.COOLING, loaded.lastRunMode());
        assertEquals(thermostat.lastRunStartMillis(), loaded.lastRunStartMillis());
        assertEquals(thermostat.lastRunEndMillis(), loaded.lastRunEndMillis());

        ThermostatView view = ApiViews.thermostat(loaded, null);
        assertEquals(Instant.ofEpochMilli(loaded.modeSinceMillis()), view.modeSince().orElseThrow());
        ThermostatView.Run run = view.lastRun().orElseThrow();
        assertEquals(ThermostatView.Mode.COOLING, run.mode());
        assertFalse(run.duration().isNegative());
        assertFalse(view.timeInMode().orElseThrow().isNegative());
        assertFalse(view.loaded(), "no server: its chunk reads as unloaded");
    }

    @Test
    void anOlderSaveCountsBackFromHowLongItHasBeenInItsMode() {
        Map<String, Object> map = thermostat().toMap();
        map.remove("modeSince");
        map.put("mode", "HEATING");
        map.put("ticksInMode", 20L * 60 * 12); // 12 minutes
        Thermostat loaded = Thermostat.fromMap(map, SETTINGS);
        long ago = System.currentTimeMillis() - loaded.modeSinceMillis();
        assertEquals(Duration.ofMinutes(12).toMillis(), ago, 2_000, "about 12 minutes ago");
        assertTrue(ApiViews.thermostat(loaded, null).lastRun().isEmpty());
    }

    @Test
    void anOlderAddonsViewStillBuilds() {
        ThermostatView old = new ThermostatView("Old", "#1", new com.github.blade.hvac.api.Position(WORLD, 0, 64, 0),
                70, 72, ThermostatView.Mode.IDLE, true, false, 45, 0, 1, java.util.Optional.empty(), false,
                ThermostatView.Profile.NORMAL, false, java.util.List.of());
        assertTrue(old.modeSince().isEmpty() && old.lastRun().isEmpty() && old.loaded());
        assertThrows(IllegalArgumentException.class, () -> new ThermostatView.Run(ThermostatView.Mode.HEATING,
                Instant.ofEpochMilli(10), Instant.ofEpochMilli(5)));
    }
}
