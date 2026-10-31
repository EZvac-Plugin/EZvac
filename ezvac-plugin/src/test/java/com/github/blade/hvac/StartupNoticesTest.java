package com.github.blade.hvac;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StartupNoticesTest {
    private static String text(List<Component> lines) {
        return String.join("\n", lines.stream().map(line -> PlainTextComponentSerializer.plainText().serialize(line)).toList());
    }

    @Test
    void aHealthyStartSaysNothing() {
        assertTrue(StartupNotices.lines(null, null, 0, 0).isEmpty());
    }

    @Test
    void anUpgradeToANewerFormatIsToldAsGoodNews() {
        String said = text(StartupNotices.lines(null, null, 0, 7));
        assertTrue(said.contains("upgraded from format 7 to format 8 at this start: everything carried over"), said);
        assertTrue(said.contains("hvac.yml.format7.bak"), said);
        assertFalse(said.contains("--force"), "an upgrade never asks for anything");
    }

    @Test
    void aRecoveryAndEntriesKeptAsideAreToldWithTheWayOut() {
        String said = text(StartupNotices.lines("backups/hvac-2026-10-29_08-00-00-auto.yml",
                "hvac.yml.unreadable-1", 2, 0));
        assertTrue(said.contains("recovered from backups/hvac-2026-10-29_08-00-00-auto.yml"), said);
        assertTrue(said.contains("hvac.yml.unreadable-1"), said);
        assertTrue(said.contains("2 entries in hvac.yml couldn't be loaded"), said);
        assertTrue(said.contains("If this is intentional, run /hvac registry repair --force"), said);
    }

    @Test
    void stoppedItSaysWhyAndOffersForceOnlyForAnUnreadableFile() {
        String unreadable = text(StartupNotices.stoppedLines("hvac.yml is unreadable.", true));
        assertTrue(unreadable.contains("EZvac is stopped"), unreadable);
        assertTrue(unreadable.contains("If this is intentional, run /hvac registry repair --force"), unreadable);
        String newer = text(StartupNotices.stoppedLines("hvac.yml is format 9.", false));
        assertFalse(newer.contains("--force"), "a newer file is never forced");
    }
}
