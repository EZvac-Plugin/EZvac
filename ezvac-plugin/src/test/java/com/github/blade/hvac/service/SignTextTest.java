package com.github.blade.hvac.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignTextTest {
    @Test
    void measuresLikeTheVanillaFont() {
        assertEquals(99, SignText.width("EZvac | Dehumidifier"), "the line the client cut to 'EZvac |'");
        assertEquals(94, SignText.width("Thermohygrometer"), "one letter too wide");
        assertEquals(70, SignText.width("T. Hygrometer"));
        assertEquals(0, SignText.width(""));
        assertTrue(SignText.width("é") >= 9, "anything outside ASCII is counted wide");
    }

    @Test
    void aHeaderDropsItsPrefixBeforeTheLabelIsCut() {
        assertEquals("EZvac | house", SignText.titled("house", SignText.SIGN_WIDTH));
        assertEquals("Dehumidifier", SignText.titled("Dehumidifier", SignText.SIGN_WIDTH));
        assertEquals("Dehumidifier", SignText.titled("Dehumidifier", SignText.HANGING_SIGN_WIDTH));
        String cut = SignText.titled("MainFloorThermostat", SignText.SIGN_WIDTH);
        assertTrue(SignText.width(cut) <= SignText.SIGN_WIDTH);
        assertEquals("MainFloorThermos", cut);
    }

    @Test
    void fitNeverGoesOverTheLine() {
        String[] samples = {"", "a", "Running", "72.4 F | COOLING", "Set 72.0 F | 100%", "ETA 12:34 | basement-north",
                "MMMMMMMMMMMMMMMMMMMMMMMMM", "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii", "ÄÖÜäöüßéèñ"};
        for (int width : new int[] {SignText.SIGN_WIDTH, SignText.HANGING_SIGN_WIDTH})
            for (String sample : samples) {
                String fitted = SignText.fit(sample, width);
                assertTrue(SignText.width(fitted) <= width, fitted);
                assertTrue(sample.startsWith(fitted));
                if (SignText.width(sample) <= width) assertEquals(sample, fitted, "text that fits is left alone");
            }
        assertEquals("MMMMMMMMMMMMMMM", SignText.fit("MMMMMMMMMMMMMMMMMMMM", 90), "fifteen 6 px letters");
        assertEquals("Out 61.2 F", SignText.fit("Out 61.2 F 78%", 60), "a reading is left off, never cut");
        assertEquals("In 61.2 F", SignText.fit("In 61.2 F 78%", 60));
        assertEquals("Feels 57.5", SignText.fit("Feels 57.5 F", 60), "no trailing space");
        assertEquals("T. Hygromet", SignText.fit("T. Hygrometer", 60), "a label is cut where the room ends");
        assertEquals("ETA 1:05 | baseme", SignText.fit("ETA 1:05 | basement", 90));
        assertEquals("ETA 12:34 | basem", SignText.fit("ETA 12:34 | basement2north", 90),
                "a label with a digit in it is a label, cut like any other");
        assertEquals("Set 72.0 F", SignText.fit("Set 72.0 F | 100%", 60), "no separator left dangling");
        assertEquals("Garden", SignText.fit("Garden -40F", 50), "a negative reading is a reading too");
        assertEquals(45, SignText.fit("i".repeat(60), 90).length(), "fifty-ish thin letters");
    }
}
