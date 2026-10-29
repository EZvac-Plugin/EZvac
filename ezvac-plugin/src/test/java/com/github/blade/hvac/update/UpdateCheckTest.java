package com.github.blade.hvac.update;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckTest {

    /** GitHub's release list, trimmed to the fields EZvac reads, newest first. */
    private static final String RELEASES = """
            [
              {"tag_name": "BETA-1.1-P1", "html_url": "https://github.com/x/releases/tag/BETA-1.1-P1",
               "draft": false, "prerelease": true},
              {"tag_name": "BETA-1.0-HOTFIX1", "html_url": "https://github.com/x/releases/tag/BETA-1.0-HOTFIX1",
               "draft": false, "prerelease": false},
              {"tag_name": "BETA-2.0", "html_url": "https://github.com/x/releases/tag/BETA-2.0",
               "draft": true, "prerelease": false},
              {"tag_name": "BETA-1.0", "html_url": "https://github.com/x/releases/tag/BETA-1.0",
               "draft": false, "prerelease": false},
              {"tag_name": "BETA-1.0-P2", "html_url": "https://github.com/x/releases/tag/BETA-1.0-P2",
               "draft": false, "prerelease": true},
              {"tag_name": "nightly", "html_url": "https://github.com/x/releases/tag/nightly",
               "draft": false, "prerelease": true},
              {"tag_name": "ALPHA-3.2-HOTFIX1", "html_url": "https://github.com/x/releases/tag/ALPHA-3.2-HOTFIX1",
               "draft": false, "prerelease": false}
            ]
            """;

    private static String newer(String running) {
        UpdateCheck.Release release = UpdateCheck.newer(ReleaseVersion.parse(running), UpdateCheck.parse(RELEASES));
        return release == null ? null : release.tag();
    }

    @Test
    void draftsAndOtherTagsAreSkipped() {
        List<UpdateCheck.Release> releases = UpdateCheck.parse(RELEASES);
        assertEquals(5, releases.size());
        assertTrue(releases.stream().noneMatch(release -> release.tag().equals("BETA-2.0")));
        assertTrue(releases.get(0).pilot());
        assertFalse(releases.get(1).pilot());
        assertEquals("https://github.com/x/releases/tag/BETA-1.1-P1", releases.get(0).url());
    }

    @Test
    void aPilotHearsOfNewerPilotsAndReleases() {
        assertEquals("BETA-1.1-P1", newer("BETA-1.0-P1"));
        assertEquals("BETA-1.1-P1", newer("BETA-1.0-P2"));
    }

    @Test
    void aReleaseOnlyHearsOfReleasesAndHotfixes() {
        assertEquals("BETA-1.0-HOTFIX1", newer("BETA-1.0"));
        assertEquals("BETA-1.0-HOTFIX1", newer("ALPHA-3.2-HOTFIX1"));
        assertNull(newer("BETA-1.0-HOTFIX1"));
    }

    @Test
    void theNewestIsNeverOlder() {
        assertNull(newer("BETA-1.1-P1"));
        assertNull(newer("BETA-1.1"));
    }

    @Test
    void aBuildThatIsNotAReleaseHearsNothing() {
        assertNull(UpdateCheck.newer(ReleaseVersion.parse("1.0-SNAPSHOT"), UpdateCheck.parse(RELEASES)));
    }

    /** After the cutoff: BETA 2.0 needs Minecraft 26.1, while BETA 1.x still gets hotfixes. */
    private static final String AFTER_CUTOFF = """
            [
              {"tag_name": "BETA-2.1-P1", "html_url": "u/BETA-2.1-P1", "draft": false, "prerelease": true},
              {"tag_name": "BETA-1.2-HOTFIX1", "html_url": "u/BETA-1.2-HOTFIX1", "draft": false, "prerelease": false},
              {"tag_name": "BETA-2.0", "html_url": "u/BETA-2.0", "draft": false, "prerelease": false},
              {"tag_name": "BETA-1.2", "html_url": "u/BETA-1.2", "draft": false, "prerelease": false},
              {"tag_name": "BETA-1.0", "html_url": "u/BETA-1.0", "draft": false, "prerelease": false}
            ]
            """;

    private static String check(String running, String minecraft) {
        UpdateCheck.Outcome outcome = UpdateCheck.check(ReleaseVersion.parse(running),
                UpdateCheck.parse(AFTER_CUTOFF), minecraft);
        return (outcome.newest() == null ? "-" : outcome.newest().tag()) + " / "
                + (outcome.runsHere() == null ? "-" : outcome.runsHere().tag());
    }

    @Test
    void aServerOn26HearsOfTheNewestForIt() {
        assertEquals("BETA-2.0 / BETA-2.0", check("BETA-1.0", "26.3"));
        assertEquals("BETA-2.0 / BETA-2.0", check("BETA-1.2-HOTFIX1", "26.1"));
        assertEquals("BETA-2.1-P1 / BETA-2.1-P1", check("BETA-1.0-P1", "26.1.2"));
        assertEquals("- / -", check("BETA-2.0", "26.3"));
    }

    @Test
    void aServerOn121HearsOfTheCutoffAndTheNewestThatRunsThere() {
        assertEquals("BETA-2.0 / BETA-1.2-HOTFIX1", check("BETA-1.0", "1.21.1"));
        assertEquals("BETA-2.0 / BETA-1.2-HOTFIX1", check("BETA-1.2", "1.21.11"));
        assertEquals("BETA-2.0 / -", check("BETA-1.2-HOTFIX1", "1.21.4"), "nothing newer runs on 1.21");
        assertTrue(UpdateCheck.check(ReleaseVersion.parse("BETA-1.2-HOTFIX1"), UpdateCheck.parse(AFTER_CUTOFF),
                "1.21.4").blocked());
        assertFalse(UpdateCheck.check(ReleaseVersion.parse("BETA-1.0"), UpdateCheck.parse(AFTER_CUTOFF),
                "26.3").blocked());
    }

    @Test
    void onlyBeta2AndLaterNeed26() {
        assertNull(UpdateCheck.requirement(ReleaseVersion.parse("ALPHA-3.2-HOTFIX1")));
        assertNull(UpdateCheck.requirement(ReleaseVersion.parse("BETA-1.9")));
        assertEquals("26.1", UpdateCheck.requirement(ReleaseVersion.parse("BETA-2.0-P1")).minecraft());
        assertEquals("26.1", UpdateCheck.requirement(ReleaseVersion.parse("BETA-3.1")).minecraft());
    }

    @Test
    void minecraftVersionsOrderByNumber() {
        assertTrue(UpdateCheck.compareMinecraft("1.21.11", "26.1") < 0);
        assertTrue(UpdateCheck.compareMinecraft("1.21.11", "1.21.4") > 0);
        assertEquals(0, UpdateCheck.compareMinecraft("26.1", "26.1.0"));
        assertTrue(UpdateCheck.compareMinecraft("26.1.2", "26.1") > 0);
        assertTrue(UpdateCheck.compareMinecraft("26.4-snapshot-2", "26.1") > 0);
        assertTrue(UpdateCheck.compareMinecraft("odd", "26.1") > 0, "an unreadable version is given the benefit");
    }

    @Test
    void anythingButAListIsNoReleases() {
        assertTrue(UpdateCheck.parse("{\"message\": \"API rate limit exceeded\"}").isEmpty());
    }
}
