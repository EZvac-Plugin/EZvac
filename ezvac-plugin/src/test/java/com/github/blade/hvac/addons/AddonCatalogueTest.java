package com.github.blade.hvac.addons;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The official addons list and addon versions (BETA 1.0 Pilot 4). */
class AddonCatalogueTest {

    @Test
    void theBundledListHasTheFourOfficialAddons() throws IOException {
        String json;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("addons.json")) {
            assertNotNull(input, "addons.json ships in the jar");
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<AddonCatalogue.Entry> entries = AddonCatalogue.parse(json);
        assertEquals(List.of("ezclimates", "liveexposure", "ezdebugplus", "mappeek"),
                entries.stream().map(AddonCatalogue.Entry::id).toList());
        AddonCatalogue.Entry climates = entries.get(0);
        assertEquals("EZclimates", climates.plugin());
        assertEquals("com.github.blade.ezclimates.EzClimatesPlugin", climates.mainClass());
        assertEquals("https://github.com/EZvac-Plugin/EZclimates", climates.url());
        assertEquals("https://api.github.com/repos/EZvac-Plugin/EZclimates/releases?per_page=30", climates.releasesApi());
        assertEquals("26.1", climates.minecraft());
        assertEquals("SNOWBALL", climates.icon());
        assertFalse(climates.folded());
        assertEquals("ezclimates", entries.get(1).foldedInto(), "LiveExposure went into EZclimates");
        assertEquals("EZdebug+", entries.get(2).name());
        for (AddonCatalogue.Entry entry : entries) {
            assertTrue(entry.url().startsWith(AddonCatalogue.ORGANISATION), entry.url());
            assertFalse(entry.description().isEmpty(), entry.id());
        }
    }

    private static String one(String fields) {
        return "{\"addons\":[{" + fields + "}]}";
    }

    private static final String GOOD = "\"id\":\"x\",\"plugin\":\"X\",\"main\":\"a.b.X\",\"repository\":\"X\"";

    @Test
    void onlyCleanEntriesAreKeptAndLinksStayInTheOrganisation() {
        assertEquals(1, AddonCatalogue.parse(one(GOOD)).size());
        AddonCatalogue.Entry plain = AddonCatalogue.parse(one(GOOD)).get(0);
        assertEquals("X", plain.name(), "the plugin's name where none is given");
        assertEquals("PAPER", plain.icon());
        assertEquals("", plain.minecraft());
        // A repository can't point anywhere else.
        for (String repository : List.of("../../evil", "evil/repo", "https://evil.example", "", "..", "a b", "x?y=1"))
            assertTrue(AddonCatalogue.parse(one("\"id\":\"x\",\"plugin\":\"X\",\"main\":\"a.b.X\",\"repository\":\""
                    + repository + "\"")).isEmpty(), repository);
        assertTrue(AddonCatalogue.parse(one("\"id\":\"Upper\",\"plugin\":\"X\",\"main\":\"a.b.X\",\"repository\":\"X\""))
                .isEmpty(), "ids are lower case");
        assertTrue(AddonCatalogue.parse(one("\"id\":\"x\",\"plugin\":\"X\",\"main\":\"not a class\",\"repository\":\"X\""))
                .isEmpty());
        // Text is cleaned and cut; odd values fall back.
        AddonCatalogue.Entry odd = AddonCatalogue.parse(one(GOOD + ",\"name\":\"§cRed\\nName\",\"description\":\""
                + "d".repeat(500) + "\",\"minecraft\":\"latest\",\"icon\":\"not an icon\",\"folded-into\":\"a b\"")).get(0);
        assertEquals("cRedName", odd.name());
        assertEquals(200, odd.description().length());
        assertEquals("", odd.minecraft());
        assertEquals("PAPER", odd.icon());
        assertEquals("", odd.foldedInto());
        // Not a list at all.
        for (String junk : new String[] {null, "", "[]", "{}", "{\"addons\":5}", "not json {", "<html>404</html>"})
            assertTrue(AddonCatalogue.parse(junk).isEmpty(), String.valueOf(junk));
        // Repeats and floods.
        assertEquals(1, AddonCatalogue.parse("{\"addons\":[{" + GOOD + "},{" + GOOD + "}]}").size());
        StringBuilder many = new StringBuilder("{\"addons\":[");
        for (int i = 0; i < 100; i++)
            many.append(i == 0 ? "" : ",").append("{\"id\":\"a").append(i).append("\",\"plugin\":\"P").append(i)
                    .append("\",\"main\":\"a.B\",\"repository\":\"R\"}");
        assertEquals(AddonCatalogue.MOST, AddonCatalogue.parse(many + "]}").size());
    }

    @Test
    void aSentenceWrapsAtSpaces() {
        assertEquals(List.of("Seasons you can", "see, and more"), AddonCatalogue.wrap("Seasons you can see, and more", 15));
        assertEquals(List.of(), AddonCatalogue.wrap("", 20));
        assertEquals(List.of("Supercalifragilistic"), AddonCatalogue.wrap("Supercalifragilistic", 5), "a long word stays whole");
    }

    @Test
    void versionsCompareByTheirNumbers() {
        assertEquals("0.2.1", AddonVersion.parse("0.2.1").toString());
        assertEquals(AddonVersion.parse("0.3.0"), AddonVersion.parse("v0.3"));
        assertEquals(AddonVersion.parse("0.1.0"), AddonVersion.parse("0.1.0-SNAPSHOT"), "a build's suffix doesn't count");
        assertTrue(AddonVersion.parse("0.10.0").compareTo(AddonVersion.parse("0.9.9")) > 0);
        assertTrue(AddonVersion.parse("1.0").compareTo(AddonVersion.parse("0.99.99")) > 0);
        assertNull(AddonVersion.parse("BETA-1.0-P4"));
        assertNull(AddonVersion.parse(""));
        assertNull(AddonVersion.parse(null));
    }

    @Test
    void theNewestNewerReleaseIsFound() {
        String releases = "[{\"tag_name\":\"0.2.0\"},{\"tag_name\":\"v0.3.0\"},{\"tag_name\":\"0.2.1\"},"
                + "{\"tag_name\":\"0.4.0\",\"prerelease\":true},{\"tag_name\":\"0.5.0\",\"draft\":true},"
                + "{\"tag_name\":\"nightly\"},{\"name\":\"no tag\"},7]";
        AddonVersion running = AddonVersion.parse("0.2.0");
        assertEquals("v0.3.0", AddonVersion.newer(releases, running, false).tag());
        assertEquals("0.4.0", AddonVersion.newer(releases, running, true).tag(), "pilots hear about prereleases");
        assertTrue(AddonVersion.newer(releases, running, true).prerelease());
        assertNull(AddonVersion.newer(releases, AddonVersion.parse("0.3.0"), false), "up to date");
        assertNull(AddonVersion.newer(releases, AddonVersion.parse("0.3.0-SNAPSHOT"), false));
        assertNull(AddonVersion.newer(releases, null, false), "a version that can't be read is never nagged");
        for (String junk : new String[] {null, "", "{}", "not json ["})
            assertNull(AddonVersion.newer(junk, running, true));
    }
}
