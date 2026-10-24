package com.github.blade.hvac.service;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where screens go on their signs, and what they say (2.1 Pilot 3). */
class ScreenTest {
    private static String plain(ScreenText screen) {
        return screen.plain();
    }

    @Test
    void aWallSignsScreenSitsJustInFrontOfItsBoard() {
        // A wall sign facing south hangs on the block to its north; its board is at the north edge.
        ScreenPlacement.Spot south = ScreenPlacement.of(ScreenPlacement.Mount.WALL, 0, 1, 6);
        assertEquals(0.5, south.x(), 1e-9);
        assertEquals(0.14, south.z(), 1e-9);
        assertEquals(0.0f, south.yaw(), 1e-4);
        ScreenPlacement.Spot east = ScreenPlacement.of(ScreenPlacement.Mount.WALL, 1, 0, 6);
        assertEquals(0.14, east.x(), 1e-9);
        assertEquals(-90.0f, east.yaw(), 1e-4);
        assertEquals(180.0f, Math.abs(ScreenPlacement.of(ScreenPlacement.Mount.WALL, 0, -1, 6).yaw()), 1e-4);
    }

    @Test
    void aTallerScreenStaysCentredOnTheBoard() {
        ScreenPlacement.Spot four = ScreenPlacement.of(ScreenPlacement.Mount.WALL, 0, 1, 4);
        ScreenPlacement.Spot eight = ScreenPlacement.of(ScreenPlacement.Mount.WALL, 0, 1, 8);
        double fourHeight = (4 * 10 + 2) * 0.4 / 40, eightHeight = (8 * 10 + 2) * 0.4 / 40;
        assertEquals(0.53, four.y() + fourHeight / 2, 1e-9);
        assertEquals(0.53, eight.y() + eightHeight / 2, 1e-9);
        assertTrue(eight.y() < four.y());
    }

    @Test
    void aStandingSignTurnedHalfwayFacesHalfway() {
        double d = Math.sqrt(0.5);
        ScreenPlacement.Spot southWest = ScreenPlacement.of(ScreenPlacement.Mount.STANDING, -d, d, 5);
        assertEquals(45.0f, southWest.yaw(), 1e-4);
        assertTrue(southWest.x() < 0.5 && southWest.z() > 0.5, "in front of the board, towards the reader");
    }

    @Test
    void aScreenReadsTitleBigReadingAndLines() {
        ScreenText screen = ScreenText.titled("main", "house").big("72.4°F")
                .line("❄ Cooling to 70.0°F", NamedTextColor.AQUA)
                .line(ScreenText.bar(0.6, 10, NamedTextColor.AQUA));
        assertEquals(4, screen.used());
        assertEquals(ScreenText.LINES, screen.lines());
        assertEquals("main  · house\n72.4°F\n❄ Cooling to 70.0°F\n▮▮▮▮▮▮▯▯▯▯ 60%", plain(screen));
    }

    @Test
    void everyScreenIsTheSameSize() {
        for (ScreenText screen : java.util.List.of(ScreenText.titled("main", "house").big("72.4°F"),
                ScreenText.titled("A much longer thermostat name", "").big("50%").line("Target 45%",
                        NamedTextColor.WHITE).line("x", NamedTextColor.WHITE).line("y", NamedTextColor.WHITE))) {
            assertEquals(ScreenText.LINES, screen.lines());
            String[] lines = PlainTextComponentSerializer.plainText().serialize(screen.text()).split("\n", -1);
            assertEquals(ScreenText.LINES, lines.length, "padded with blank lines");
            int title = SignText.width(lines[0].replace('·', '.'));
            assertTrue(title <= ScreenText.WIDTH && title > ScreenText.WIDTH - SignText.width(" "),
                    () -> "the title reaches the full width: " + title);
        }
    }

    @Test
    void barsFillAndSayWhenTheyPassFull() {
        assertEquals("▯▯▯▯▯", ScreenText.bar(-1, 5));
        assertEquals("▮▮▮▯▯", ScreenText.bar(0.55, 5));
        assertEquals("▮▮▮▮▮", ScreenText.bar(3, 5));
        assertEquals("▮▮▮▮▮ 140%", PlainTextComponentSerializer.plainText()
                .serialize(ScreenText.bar(1.4, 5, NamedTextColor.GREEN)));
        assertEquals("main", plain(ScreenText.titled("main", " ")));
    }
}
