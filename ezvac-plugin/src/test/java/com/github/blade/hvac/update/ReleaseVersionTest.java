package com.github.blade.hvac.update;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseVersionTest {

    private static ReleaseVersion v(String name) { return ReleaseVersion.parse(name); }

    @Test
    void releasesOrderAsTheyCameOut() {
        List<String> inOrder = List.of("ALPHA-1.1", "ALPHA-1.1-HOTFIX", "ALPHA-1.6-P1", "ALPHA-1.6",
                "ALPHA-1.6-HOTFIX1", "ALPHA-1.10", "ALPHA-2.0-P5", "ALPHA-2.0", "ALPHA-3.2",
                "ALPHA-3.2-HOTFIX1", "ALPHA-3.2-HOTFIX2", "BETA-1.0-P1", "BETA-1.0-P2", "BETA-1.0",
                "BETA-1.0-HOTFIX1", "BETA-1.1-P1", "BETA-2.0");
        List<ReleaseVersion> shuffled = new ArrayList<>(inOrder.stream().map(ReleaseVersionTest::v).toList());
        Collections.shuffle(shuffled, new java.util.Random(7));
        Collections.sort(shuffled);
        assertEquals(inOrder.stream().map(ReleaseVersionTest::v).toList(), shuffled);
    }

    @Test
    void namesParseWhateverTheCaseAndShorten() {
        assertEquals("B1.0_P1", v("beta-1.0-p1").shortName());
        assertEquals("A1.1_H1", v("ALPHA-1.1-HOTFIX").shortName(), "the one hotfix without a number");
        assertEquals(v("BETA-1.0"), v("BETA-1.0.0"));
        assertEquals(v("BETA-1.0").hashCode(), v("BETA-1.0.0").hashCode());
        assertTrue(v("BETA-1.0-P3").isPilot());
    }

    @Test
    void otherNamesAreNotReleases() {
        assertNull(v("1.0-SNAPSHOT"));
        assertNull(v("BETA-1.0-SNAPSHOT"));
        assertNull(v("v1.0"));
        assertNull(v(null));
    }
}
