package com.github.blade.hvac.gui;

import com.github.blade.hvac.config.HudSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.gui.SetupSteps.HudChoice;
import com.github.blade.hvac.gui.SetupSteps.Step;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The first-start setup's pages and choices (BETA 1.0 Pilot 10). */
class SetupStepsTest {
    @Test
    void itGoesFromWelcomeToDoneInOrder() {
        assertEquals(List.of(Step.WELCOME, Step.TEMPERATURES, Step.WIND, Step.HUD, Step.UPDATES, Step.BACKUPS,
                Step.STATISTICS, Step.ADDONS, Step.DONE), List.of(Step.values()));
        assertNull(Step.WELCOME.previous());
        assertNull(Step.DONE.next());
        assertEquals(Step.WIND, Step.TEMPERATURES.next());
        assertEquals(Step.TEMPERATURES, Step.WIND.previous());
        assertEquals("Setup 3/9 · Wind", Step.WIND.menuTitle());
        // A chest's title has about 160 pixels; "EZvac setup · 7 of 9: Anonymous statistics" ran off it.
        for (Step step : Step.values())
            assertTrue(com.github.blade.hvac.service.TextWidth.width(step.menuTitle()) <= 150,
                    step.menuTitle() + " is " + com.github.blade.hvac.service.TextWidth.width(step.menuTitle()) + " px");
    }

    @Test
    void theHudChoiceReadsTheSettings() {
        assertEquals(HudChoice.OFF, HudChoice.of(false, "bossbar"), "off while new players aren't shown it");
        assertEquals(HudChoice.BOSSBAR, HudChoice.of(true, "bossbar"));
        assertEquals(HudChoice.ACTIONBAR, HudChoice.of(true, "action_bar"));
        assertEquals(HudChoice.SIDEBAR, HudChoice.of(true, "nonsense"), "an unknown style reads as the sidebar");
        assertNull(HudChoice.OFF.style());
        assertEquals(HudSettings.Style.SIDEBAR, HudChoice.SIDEBAR.style());
    }

    @Test
    void aTypedNameIsTidiedAndTheTitleKeepsItsLook() {
        assertNull(SetupSteps.cleanName("   "));
        assertEquals("Soms", SetupSteps.cleanName("  Soms  "));
        assertEquals("My   Server".replaceAll("\\s+", " "), SetupSteps.cleanName("My   Server"));
        assertEquals(24, SetupSteps.cleanName("x".repeat(40)).length());
        assertTrue(SetupSteps.title("Soms").endsWith("<gray>Soms"));
        assertTrue(SetupSteps.title("Soms").startsWith("<gradient:#3999fb:#f69500><bold>Climate</bold></gradient>"),
                "the same look as the stock title");
    }

    @Test
    void theSummarySaysEveryChoice() {
        assertEquals(List.of("Temperatures: °C", "Wind speeds: km/h", "HUD for new players: Sidebar", "Updates: Pilot",
                        "Backups: Weekly", "Anonymous statistics: Off"),
                SetupSteps.summary(TemperatureUnit.C, WindSettings.Units.KMH, HudChoice.SIDEBAR, "pilot", 168, false));
        assertEquals("Every 24 hours", SetupSteps.backupLabel(24));
        assertEquals("Follow this build", SetupSteps.channelLabel("auto"));
    }
}
