package com.github.blade.hvac.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignTextTest {
    @Test
    void measuresLikeTheVanillaFont() {
        assertEquals(99, TextWidth.width("EZvac | Dehumidifier"), "the line the client cut to 'EZvac |'");
        assertEquals(94, TextWidth.width("Thermohygrometer"), "one letter too wide");
        assertEquals(70, TextWidth.width("T. Hygrometer"));
        assertEquals(0, TextWidth.width(""));
        assertTrue(TextWidth.width("é") >= 9, "anything outside ASCII is counted wide");
    }

    @Test
    void fitNeverGoesOverTheLine() {
        String[] samples = {"", "a", "Running", "72.4 F | COOLING", "Set 72.0 F | 100%", "ETA 12:34 | basement-north",
                "MMMMMMMMMMMMMMMMMMMMMMMMM", "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii", "ÄÖÜäöüßéèñ"};
        for (int width : new int[] {90, 60})
            for (String sample : samples) {
                String fitted = TextWidth.fit(sample, width);
                assertTrue(TextWidth.width(fitted) <= width, fitted);
                assertTrue(sample.startsWith(fitted));
                if (TextWidth.width(sample) <= width) assertEquals(sample, fitted, "text that fits is left alone");
            }
        assertEquals("MMMMMMMMMMMMMMM", TextWidth.fit("MMMMMMMMMMMMMMMMMMMM", 90), "fifteen 6 px letters");
        assertEquals("Out 61.2 F", TextWidth.fit("Out 61.2 F 78%", 60), "a reading is left off, never cut");
        assertEquals("In 61.2 F", TextWidth.fit("In 61.2 F 78%", 60));
        assertEquals("Feels 57.5", TextWidth.fit("Feels 57.5 F", 60), "no trailing space");
        assertEquals("T. Hygromet", TextWidth.fit("T. Hygrometer", 60), "a label is cut where the room ends");
        assertEquals("ETA 1:05 | baseme", TextWidth.fit("ETA 1:05 | basement", 90));
        assertEquals("ETA 12:34 | basem", TextWidth.fit("ETA 12:34 | basement2north", 90),
                "a label with a digit in it is a label, cut like any other");
        assertEquals("Set 72.0 F", TextWidth.fit("Set 72.0 F | 100%", 60), "no separator left dangling");
        assertEquals("Garden", TextWidth.fit("Garden -40F", 50), "a negative reading is a reading too");
        assertEquals(45, TextWidth.fit("i".repeat(60), 90).length(), "fifty-ish thin letters");
    }
}
