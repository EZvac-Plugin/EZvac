package com.github.blade.hvac.service;

import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** How the wind's direction reads (BETA 1.0 Pilot 3). */
class WindTextTest {
    private static final WindSettings SETTINGS = WindSettings.defaults();

    @Test
    void directionsReadAsEightCompassPoints() {
        assertEquals("N", WindText.compass(0.0));
        assertEquals("N", WindText.compass(359.0));
        assertEquals("NE", WindText.compass(60.0));
        assertEquals("E", WindText.compass(70.0), "the trades' 70° is nearer east than northeast");
        assertEquals("W", WindText.compass(255.0), "the westerlies' WSW rounds to W");
        assertEquals("SW", WindText.compass(225.0));
        assertEquals("NW", WindText.compass(300.0));
        assertEquals("S", WindText.compass(-180.0));
    }

    @Test
    void aCommandTakesCompassPointsOrDegrees() {
        assertEquals(315.0, WindText.parseDirection("nw"), 1e-9);
        assertEquals(247.5, WindText.parseDirection("WSW"), 1e-9);
        assertEquals(270.0, WindText.parseDirection("270"), 1e-9);
        assertEquals(0.0, WindText.parseDirection("360"), 1e-9);
        assertTrue(Double.isNaN(WindText.parseDirection("up")));
        assertTrue(Double.isNaN(WindText.parseDirection("400")));
    }

    @Test
    void calmHasNoDirectionAndTheRestFitASign() {
        assertEquals("Calm", WindText.sign(0.5, 270.0, SETTINGS, TemperatureUnit.F));
        assertEquals("Calm", WindText.describe(0.5, 270.0, SETTINGS, TemperatureUnit.C));
        assertEquals("Windy 29 km/h from the W", WindText.describe(8.0, 270.0, SETTINGS, TemperatureUnit.C));
        String gale = WindText.sign(18.0, 315.0, SETTINGS, TemperatureUnit.C);
        assertEquals("Gale 65km/h NW", gale);
        assertTrue(SignText.width(gale) < SignText.SIGN_WIDTH, "it leaves room for a label");
        assertEquals("Windy 29km/h", WindText.sign(8.0, Double.NaN, SETTINGS, TemperatureUnit.C), "no direction known");
    }
}
