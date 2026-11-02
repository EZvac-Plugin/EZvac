package com.github.blade.hvac.update;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What /hvac update, and the notifier, say about what a check found. */
class UpdateReportTest {

    private static final UpdateCheck.Release BETA_2 = release("BETA-2.0", false);
    private static final UpdateCheck.Release BETA_1_2 = release("BETA-1.2", false);

    private static UpdateCheck.Release release(String tag, boolean pilot) {
        return new UpdateCheck.Release(ReleaseVersion.parse(tag), tag, "https://github.com/x/releases/tag/" + tag,
                pilot);
    }

    private static List<String> said(UpdateCheck.Outcome outcome, String running, String minecraft,
                                     boolean clickable) {
        return UpdateNotifier.report(outcome, running, minecraft, clickable).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }

    @Test
    void upToDate() {
        assertEquals(List.of("[EZvac] You're on the latest EZvac, BETA-1.0-P1, on the Pilot channel."),
                said(new UpdateCheck.Outcome(null, null), "BETA-1.0-P1", "26.3", true), "a pilot is on Pilot by itself");
        assertEquals(List.of("[EZvac] You're on the latest stable release, BETA-1.0."),
                said(new UpdateCheck.Outcome(null, null), "BETA-1.0", "26.3", true));
    }

    private static List<String> said(String running, UpdateCheck.Channel channel) {
        return UpdateNotifier.report(new UpdateCheck.Outcome(null, null), running, "26.3", true, channel).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }

    @Test
    void upToDateSaysWhatTheChannelWaitsFor() {
        // BETA 1.0 Pilot 2: a text for Stable is never shown on Pilot, nor the other way round.
        assertEquals(List.of("[EZvac] This server runs BETA-1.0-P2, a pilot, on the Stable channel: you'll hear"
                + " when the next full release is out."), said("BETA-1.0-P2", UpdateCheck.Channel.STABLE));
        assertEquals(List.of("[EZvac] You're on the latest EZvac, BETA-1.0, on the Pilot channel."),
                said("BETA-1.0", UpdateCheck.Channel.PILOT));
        assertEquals(List.of("[EZvac] You're on the latest stable release, BETA-1.0."),
                said("BETA-1.0", UpdateCheck.Channel.STABLE));
    }

    @Test
    void aNewerOneThatRunsHere() {
        List<String> lines = said(new UpdateCheck.Outcome(BETA_2, BETA_2), "BETA-1.0", "26.3", true);
        assertEquals(1, lines.size());
        assertEquals("[EZvac] EZvac BETA-2.0 is out (this server runs BETA-1.0), and it works on this server's"
                + " Minecraft 26.3. Open the release", lines.get(0));
    }

    @Test
    void aNewerOneThatNeedsANewerMinecraft() {
        List<String> lines = said(new UpdateCheck.Outcome(BETA_2, BETA_1_2), "BETA-1.0", "1.21.4", true);
        assertEquals("[EZvac] EZvac BETA-2.0 is out, but it needs Minecraft 26.1 or newer, and this server runs"
                + " 1.21.4. Update the server first: How to move", lines.get(0));
        assertEquals("[EZvac] Until then, BETA-1.2 is the newest that runs here; this server runs BETA-1.0."
                + " Open the release", lines.get(1));

        List<String> newest = said(new UpdateCheck.Outcome(BETA_2, null), "BETA-1.2", "1.21.4", true);
        assertEquals("[EZvac] Until then, BETA-1.2 is the newest EZvac this server can run.", newest.get(1));
    }

    @Test
    void aPilotIsSaidToBeOne() {
        UpdateCheck.Release pilot = release("BETA-1.1-P1", true);
        assertTrue(said(new UpdateCheck.Outcome(pilot, pilot), "BETA-1.0-P1", "26.3", true).get(0)
                .startsWith("[EZvac] EZvac BETA-1.1-P1 (a pilot) is out"));
    }

    @Test
    void linksClickInChatAndAreSpelledOutInTheConsole() {
        UpdateCheck.Outcome outcome = new UpdateCheck.Outcome(BETA_2, BETA_1_2);
        List<Component> chat = UpdateNotifier.report(outcome, "BETA-1.0", "1.21.4", true);
        ClickEvent guide = chat.get(0).children().get(0).children().get(0).clickEvent();
        assertEquals(UpdateCheck.requirement(BETA_2.version()).guide(), guide.value());

        List<String> console = said(outcome, "BETA-1.0", "1.21.4", false);
        assertTrue(console.get(0).endsWith("Update the server first: " + UpdateCheck.requirement(BETA_2.version()).guide()),
                console.get(0));
        assertTrue(console.get(1).endsWith("https://github.com/x/releases/tag/BETA-1.2"), console.get(1));
    }
}
