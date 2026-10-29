package com.github.blade.hvac.command;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HvacCommandHelpTest {

    /** What a chat window shows without scrolling. */
    private static final int ONE_SCREEN = 12;

    private static List<String> page(String page, boolean hud, boolean admin) {
        return HvacCommand.helpPage(page, hud, admin).stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line)).toList();
    }

    @Test
    void aPlayerSeesAShortIndexOfTheirOwnCommands() {
        List<String> index = page("", true, false);
        assertTrue(index.size() <= ONE_SCREEN, index.toString());
        assertTrue(index.stream().anyMatch(line -> line.startsWith("/hvac temperature")));
        assertTrue(index.stream().anyMatch(line -> line.startsWith("/hvac hud")));
        assertFalse(index.stream().anyMatch(line -> line.contains("help build") || line.contains("create")),
                "no admin commands or pages");
        assertFalse(page("", false, false).stream().anyMatch(line -> line.startsWith("/hvac hud")),
                "the HUD only with its permission");
        assertEquals(index, page("server", true, false), "a player asking for an admin page gets the index");
    }

    @Test
    void anAdminGetsPointersToTwoPagesThatEachFitOneScreen() {
        List<String> index = page("", true, true);
        assertTrue(index.stream().anyMatch(line -> line.startsWith("/hvac help build")));
        assertTrue(index.stream().anyMatch(line -> line.startsWith("/hvac help server")));
        for (String name : List.of("build", "server", "BUILD")) {
            List<String> lines = page(name, true, true);
            assertTrue(lines.get(0).startsWith("EZvac commands: "), name + ": " + lines);
            assertTrue(lines.size() <= ONE_SCREEN, name + ": " + lines.size() + " lines");
        }
        assertEquals(index, page("nonsense", true, true), "an unknown page gives the index");
    }

    @Test
    void everyCommandIsOnAPage() {
        List<String> all = Stream.of("", "build", "server").flatMap(name -> page(name, true, true).stream()).toList();
        for (String word : HvacCommand.subcommands(true, true)) {
            if (word.equals("help")) continue;
            assertTrue(all.stream().anyMatch(line -> line.startsWith("/hvac " + word)), word + " is on no page");
        }
    }

    @Test
    void tabCompletionOffersOnlyWhatTheSenderCanRun() {
        List<String> player = HvacCommand.subcommands(true, false);
        assertTrue(player.containsAll(List.of("help", "temperature", "hud", "list", "info", "season", "wind")));
        for (String admin : List.of("create", "link-tool", "remove", "stats", "sync", "update", "quicksettings"))
            assertFalse(player.contains(admin), admin);
        assertFalse(HvacCommand.subcommands(false, false).contains("hud"));
        assertTrue(HvacCommand.subcommands(true, true).containsAll(List.of("create", "quicksettings", "update")));
    }
}
