package com.github.blade.hvac.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Whether a player sees the HUD (BETA 1.0 Pilot 10): their own choice first, else the server's default. */
class HudDefaultTest {
    @Test
    void aPlayersOwnChoiceAlwaysWins() {
        assertTrue(ClimateHudService.shows((byte) 1, false), "turned on, whatever the default");
        assertFalse(ClimateHudService.shows((byte) 0, true), "turned off, whatever the default");
    }

    @Test
    void aPlayerWhoNeverChoseFollowsTheDefault() {
        assertTrue(ClimateHudService.shows(null, true));
        assertFalse(ClimateHudService.shows(null, false), "as before Pilot 10");
    }
}
