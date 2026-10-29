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

    @Test
    void anythingButAListIsNoReleases() {
        assertTrue(UpdateCheck.parse("{\"message\": \"API rate limit exceeded\"}").isEmpty());
    }
}
