package com.github.blade.hvac.service;

import com.github.blade.hvac.model.OperatingMode;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Where screens go on their signs, and what they say (2.1). */
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
        ScreenText screen = ScreenText.titled("main", "house").tone(ScreenText.Tone.COOLING).big("72.4°F")
                .line("Cooling to 70.0°F", NamedTextColor.AQUA)
                .line(ScreenText.bar(0.6, 10, NamedTextColor.AQUA));
        assertEquals(2, screen.used());
        assertEquals(ScreenText.BODY_LINES, screen.bodyLines());
        assertEquals("main  · house\n72.4°F\nCooling to 70.0°F\n▮▮▮▮▮▮▯▯▯▯ 60%", plain(screen));
        assertEquals("72.4°F", text(screen.bigText()));
    }

    private static String text(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Pixels a screen line takes, counting a · as narrow as a full stop. */
    private static int width(String line) {
        return SignText.width(line.replace('·', '.'));
    }

    @Test
    void theHeaderCarriesTheNameAndEndsInTheToneGlyph() {
        String cooling = text(ScreenText.titled("Living room", "").tone(ScreenText.Tone.COOLING).header());
        assertTrue(cooling.startsWith("Living room ") && cooling.endsWith("❄"), cooling);
        String plain = text(ScreenText.titled("Shed", "").header());
        assertTrue(plain.startsWith("Shed") && plain.endsWith(" "), "a neutral header has no glyph: " + plain);
        for (String header : java.util.List.of(cooling, plain)) {
            int width = width(header);
            assertTrue(width <= ScreenText.WIDTH && width > ScreenText.WIDTH - 2 * SignText.width(" "),
                    () -> "the strip reaches the full width: " + width);
        }
        String longName = text(ScreenText.titled("A very long thermostat name indeed", "Pool")
                .tone(ScreenText.Tone.HEATING).header());
        assertTrue(longName.endsWith("♨") && width(longName) <= ScreenText.WIDTH, longName);
    }

    @Test
    void everyScreenIsTheSameSizeAndNoLineIsPadded() {
        for (ScreenText screen : java.util.List.of(ScreenText.titled("main", "house").big("72.4°F"),
                ScreenText.titled("hub", "").line("Attic: 71.0 F", NamedTextColor.WHITE),
                ScreenText.titled("A much longer thermostat name", "").big("50%").line("Target 45%",
                        NamedTextColor.WHITE).line("x", NamedTextColor.WHITE).line("y", NamedTextColor.WHITE))) {
            assertEquals(ScreenText.BODY_LINES, screen.bodyLines());
            String[] lines = text(screen.body()).split("\n", -1);
            assertEquals(ScreenText.BODY_LINES, lines.length, "padded with blank lines");
            int widest = java.util.Arrays.stream(lines).mapToInt(ScreenTest::width).max().orElse(0);
            assertTrue(widest < ScreenText.WIDTH && widest > ScreenText.WIDTH - 3 * SignText.width(" "),
                    () -> "a blank line sets the panel just short of the wrap width: " + widest);
            for (String line : lines)
                assertTrue(line.isBlank() || line.equals(line.strip()), () -> "never padded: '" + line + "'");
        }
    }

    @Test
    void aThermostatWaitingOnUnitsFitsItsLines() {
        // A wrapped line pushes the body up over the big reading (BETA 1.0, seen on a live screen).
        for (String target : List.of("104.0°F", "-40.0°C", "100.0°F"))
            for (OperatingMode mode : List.of(OperatingMode.HEATING, OperatingMode.COOLING)) {
                String line = DisplayService.waitingLine(mode, target);
                assertTrue(SignText.width(line) <= ScreenText.WIDTH, line + " is " + SignText.width(line));
            }
        assertTrue(SignText.width(DisplayService.UNITS_OFF) <= ScreenText.WIDTH);
        // Size-based heating (BETA 1.0): the widest has/needs a system can show, up to 200,000 blocks of air.
        for (double[] sized : new double[][] {{0.85, 2.6}, {9.9, 9.9}, {12.5, 107.5}, {107.5, 107.5}}) {
            String line = DisplayService.undersizedLine(new com.github.blade.hvac.simulation.SizingModel.Assessment(
                    200_000, false, 0, sized[1], sized[0], 0.01, 1.0));
            assertTrue(SignText.width(line) <= ScreenText.WIDTH, line + " is " + SignText.width(line));
        }
        assertTrue(SignText.width(DisplayService.UNITS_OFF + " | 15/15") <= SignText.SIGN_WIDTH, "and the sign's footer");
    }

    @Test
    void aLineTooWideGivesUpItsLastPartsInsteadOfWrapping() {
        // BETA 1.0, seen on a live screen: on a 106.6 °F day this line wrapped and put Heating under the big reading.
        assertEquals(103, SignText.width("Out 106.6°F · Summer"), "the client's own widths: ° 5, · 2");
        assertEquals("Out 106.6°F", ScreenText.fitted("Out 106.6°F · Summer"));
        assertEquals("Out 96.6°F · Summer", ScreenText.fitted("Out 96.6°F · Summer"), "a line that fits is kept");
        assertEquals("ETA 0:31 · 28% RH", ScreenText.fitted("ETA 0:31 · 28% RH"));
        for (String season : List.of("Spring", "Summer", "Autumn", "Winter", ""))
            for (double f = -60; f <= 140; f += 0.1) {
                for (String out : List.of(String.format(java.util.Locale.US, "Out %.1f°F", f),
                        String.format(java.util.Locale.US, "Out %.1f°C", (f - 32) / 1.8))) {
                    String line = ScreenText.fitted(season.isEmpty() ? out : out + " · " + season);
                    assertTrue(SignText.width(line) <= ScreenText.WIDTH, line);
                    assertTrue(line.startsWith(out), "the temperature always stays: " + line);
                }
            }
        String screen = plain(ScreenText.titled("Home", "").big("106.6°F").line("Heating to 75.0°F", NamedTextColor.GOLD)
                .line("Out 106.6°F · Summer", NamedTextColor.GRAY));
        assertTrue(screen.endsWith("\nOut 106.6°F"), screen);
    }

    @Test
    void theBigReadingLeavesTheTopOfTheBodyBlank() {
        String[] lines = text(ScreenText.titled("main", "").big("72.4°F")
                .line("Holding 72.0°F", NamedTextColor.GREEN).body()).split("\n", -1);
        assertTrue(lines[0].isBlank() && lines[1].isBlank(), "the big reading is drawn over these");
        assertEquals("Holding 72.0°F", lines[2]);
    }

    @Test
    void aListAlwaysEndsInABlankLine() {
        ScreenText full = ScreenText.titled("hub", "");
        for (int i = 0; i < ScreenText.ROWS; i++) full.line("sensor " + i, NamedTextColor.WHITE);
        String[] lines = text(full.body()).split("\n", -1);
        assertEquals(ScreenText.BODY_LINES, lines.length);
        assertTrue(lines[lines.length - 1].isBlank());
    }

    @Test
    void aBusyScreenGrows() {
        ScreenText screen = ScreenText.titled("hub", "");
        for (int i = 0; i < ScreenText.ROWS + 2; i++) screen.line("sensor " + i, NamedTextColor.WHITE);
        assertEquals(ScreenText.ROWS + 3, screen.bodyLines(), "and keeps its blank line");
    }

    @Test
    void theStripSitsOnTopOfTheBodyAndBothAreCentredOnTheBoard() {
        ScreenPlacement.Layout layout = ScreenPlacement.layout(ScreenPlacement.Mount.WALL, 0, 1,
                ScreenText.BODY_LINES, true);
        double pixel = 0.4 / 40;
        double body = (ScreenText.BODY_LINES * 10 + 2) * pixel, header = 12 * pixel;
        assertEquals(layout.body().y() + body, layout.header().y(), 1e-9, "the header starts where the body ends");
        assertEquals(0.53, layout.body().y() + (body + header) / 2, 1e-9, "centred on the board");
        assertEquals(layout.body().z(), layout.header().z(), 1e-9);
        assertEquals(0.0f, layout.header().yaw(), 1e-4);
    }

    @Test
    void theBigReadingFloatsOverTheBodysBlankRows() {
        ScreenPlacement.Layout layout = ScreenPlacement.layout(ScreenPlacement.Mount.WALL, 0, 1,
                ScreenText.BODY_LINES, true);
        double pixel = 0.4 / 40, bodyTop = layout.body().y() + (ScreenText.BODY_LINES * 10 + 2) * pixel;
        double bigCentre = layout.big().y() + 12 * pixel;
        assertEquals(bodyTop - 11 * pixel, bigCentre, 1e-9, "centred on the two blank rows under the top");
        assertTrue(layout.big().z() > layout.body().z(), "just in front of the body, towards the reader");
        assertNull(ScreenPlacement.layout(ScreenPlacement.Mount.WALL, 0, 1, 6, false).big());
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
