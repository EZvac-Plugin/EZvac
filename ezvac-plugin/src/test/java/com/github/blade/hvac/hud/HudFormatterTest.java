package com.github.blade.hvac.hud;

import com.github.blade.hvac.config.HudSettings;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HudFormatterTest {
    private final HudFormatter format = new HudFormatter(HudSettings.defaults());

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static ClimateSnapshot cooled(double localF) {
        return new ClimateSnapshot(localF, 88.0, 0.6, 21, "house", "main", "COOLING", 70.0, false, false);
    }

    @Test
    void theSeasonLineShowsOnlyWhereThereAreSeasons() {
        List<String> lines = HudSettings.defaults().sidebarLines();
        String seasonLine = "<gray>Season: <white>{season} <gray>day {season_day}";
        assertTrue(lines.contains(seasonLine));
        assertFalse(format.shownLines(lines, cooled(72.0)).contains(seasonLine));
        assertEquals(lines.size() - 1, format.shownLines(lines, cooled(72.0)).size());
        ClimateSnapshot summer = cooled(72.0).inSeason("Summer", 3);
        assertSame(lines, format.shownLines(lines, summer));
        assertEquals("Season: Summer day 3", plain(format.render(seasonLine, summer)));
    }

    @Test
    void temperaturesFormatInEitherUnit() {
        assertEquals("72.4°F", format.temperature(72.4));
        assertEquals("22.0°C", HudFormatter.celsius(71.6));
        YamlConfiguration config = new YamlConfiguration();
        config.set("units", "C");
        assertEquals("0.0°C", new HudFormatter(HudSettings.load(config)).temperature(32.0));
    }

    @Test
    void theBarFillsFromColdToHotAndClampsAtBothEnds() {
        assertEquals("▯▯▯▯▯▯▯▯▯▯", format.barPlain(50.0));
        assertEquals("▯▯▯▯▯▯▯▯▯▯", format.barPlain(-40.0));
        assertEquals("▮▮▮▮▮▯▯▯▯▯", format.barPlain(72.5));
        assertEquals("▮▮▮▮▮▮▮▮▮▮", format.barPlain(95.0));
        assertEquals("▮▮▮▮▮▮▮▮▮▮", format.barPlain(200.0));
        assertEquals(0.0, format.fraction(Double.NaN));
    }

    @Test
    void comfortBandsFollowTheirLimits() {
        assertEquals("Cold", format.band(59.9).label());
        assertEquals("Cool", format.band(60.0).label());
        assertEquals("Cool", format.band(67.9).label());
        assertEquals("Comfortable", format.band(68.0).label());
        assertEquals("Comfortable", format.band(78.0).label());
        assertEquals("Warm", format.band(78.1).label());
        assertEquals("Warm", format.band(86.0).label());
        assertEquals("Hot", format.band(86.1).label());
        assertEquals(BossBar.Color.BLUE, format.bossBarColor(55));
        assertEquals(BossBar.Color.GREEN, format.bossBarColor(72));
        assertEquals(BossBar.Color.YELLOW, format.bossBarColor(80));
        assertEquals(BossBar.Color.RED, format.bossBarColor(100));
    }

    @Test
    void theColouredBarUsesTheComfortColourAndKeepsItsGlyphs() {
        assertTrue(format.barMini(72.5).startsWith("<color:#6ee07a>▮▮▮▮▮</color>"));
        assertTrue(format.barLegacy(72.5).contains("▮▮▮▮▮"));
        assertTrue(format.barLegacy(72.5).contains("§"), "legacy colours for placeholder consumers");
    }

    @Test
    void templatesFillEveryToken() {
        String line = plain(format.render(
                "{temp}|{bar}|{comfort}|{outdoor}|{group}|{thermostat}|{mode}|{target}|{zone}", cooled(72.5)));
        assertEquals("72.5°F|▮▮▮▮▮▯▯▯▯▯|Comfortable|88.0°F|house|main|Cooling to 70.0°F|70.0°F|60% leakage", line);
    }

    @Test
    void outdoorSpotsShowTheNoneText() {
        ClimateSnapshot outside = ClimateSnapshot.outdoors(91.0);
        assertEquals("91.0°F|none|none|none|Outside",
                plain(format.render("{temp}|{group}|{mode}|{target}|{zone}", outside)));
    }

    @Test
    void coreAndIdleRenderPlainly() {
        ClimateSnapshot idleCore = new ClimateSnapshot(70.0, 60.0, 1.0, 3, "house", "main", "IDLE", 70.0, true, false);
        assertEquals("Idle|Core", plain(format.render("{mode}|{zone}", idleCore)));
    }

    @Test
    void versionsShortenForTheTitle() {
        assertEquals("A1.8", HudFormatter.shortVersion("ALPHA-1.8"));
        assertEquals("A1.8_H1", HudFormatter.shortVersion("ALPHA-1.8-HOTFIX1"));
        assertEquals("A1.6_P1", HudFormatter.shortVersion("ALPHA-1.6-P1"));
        assertEquals("A2.0", HudFormatter.shortVersion("ALPHA-2.0"));
        assertEquals("A2.0_P5", HudFormatter.shortVersion("ALPHA-2.0-P5"));
        assertEquals("A1.10_H12", HudFormatter.shortVersion("ALPHA-1.10-HOTFIX12"));
        assertEquals("1.0-SNAPSHOT", HudFormatter.shortVersion("1.0-SNAPSHOT"), "unknown formats pass through");
        assertEquals("", HudFormatter.shortVersion(null));
    }

    @Test
    void theVersionHasItsOwnLineUnderAirflow() {
        HudFormatter versioned = new HudFormatter(HudSettings.defaults(), "ALPHA-1.8-HOTFIX1");
        assertEquals("Climate - EZvac", plain(versioned.title()));
        java.util.List<String> lines = HudSettings.defaults().sidebarLines();
        assertTrue(lines.get(lines.size() - 2).contains("{zone}"));
        assertEquals("Version: A1.8_H1", plain(versioned.render(lines.get(lines.size() - 1), cooled(70))));
    }

    @Test
    void waterUsesItsOwnScaleAndWording() {
        ClimateSnapshot pool = new ClimateSnapshot(84.0, 90.0, 0.4, 20, "pool", "spa", "HEATING", 84.0, false, true);
        assertEquals("Comfortable", format.band(pool).label(), "84 F is a pleasant pool");
        assertEquals("Warm", format.band(84.0).label(), "but a warm room");
        assertEquals("Water: 84.0°F|Air: 90.0°F|Pool: pool|Circulation: 40% mixing",
                plain(format.render("<gray>Water: {temp}|Air: {outdoor}|Pool: {group}|Circulation: {zone}", pool)));
        assertEquals(BossBar.Color.GREEN, format.bossBarColor(pool));
        ClimateSnapshot hotTub = new ClimateSnapshot(102.0, 70.0, 1.0, 2, "spa", "spa", "IDLE", 102.0, true, true);
        assertEquals("Hot tub", format.band(hotTub).label());
        ClimateSnapshot lake = new ClimateSnapshot(61.0, 72.0, 0.0, -1, null, null, null, Double.NaN, false, true);
        assertEquals("Natural water", format.zone(lake));
        assertEquals("Cold", format.band(lake).label());
        assertEquals(HudSettings.defaults().water().sidebarLines(), format.medium(pool).sidebarLines());
        assertEquals(HudSettings.defaults().sidebarLines(), format.medium(cooled(70)).sidebarLines());
    }

    @Test
    void theWaterBarSpansThePoolRange() {
        ClimateSnapshot cold = new ClimateSnapshot(60.0, 70.0, 0, -1, null, null, null, Double.NaN, false, true);
        ClimateSnapshot hot = new ClimateSnapshot(104.0, 70.0, 0, -1, null, null, null, Double.NaN, false, true);
        assertEquals("▯▯▯▯▯▯▯▯▯▯", format.barPlain(cold));
        assertEquals("▮▮▮▮▮▮▮▮▮▮", format.barPlain(hot));
    }

    @Test
    void airComfortFollowsHowItFeels() {
        ClimateSnapshot muggy = new ClimateSnapshot(84.0, 90.0, 1.0, 2, "house", "main", "IDLE", 72.0, true, false,
                75.0, 92.0, "Clear");
        assertEquals("Hot", format.band(muggy).label(), "84 F at 75% feels like 92 F");
        assertEquals("Warm", format.band(84.0).label(), "the thermometer alone says warm");
        assertEquals("Feels like: 92.0°F|Humidity: 75%|Outside: 90.0°F Clear",
                plain(format.render("Feels like: {feels}|Humidity: {humidity}|Outside: {outdoor} {weather}", muggy)));
    }

    @Test
    void waterShowsNoHumidity() {
        ClimateSnapshot pool = new ClimateSnapshot(84.0, 90.0, 0.4, 20, "pool", "spa", "HEATING", 84.0, false, true,
                Double.NaN, 84.0, "");
        assertEquals("none", format.humidity(pool));
        assertEquals("84.0°F", format.feelsLike(pool));
    }

    @Test
    void valuesCannotInjectFormatting() {
        ClimateSnapshot sneaky = new ClimateSnapshot(70.0, 60.0, 1.0, 3, "<red>evil", "main", "IDLE", 70.0, true, false);
        assertEquals("<red>evil", plain(format.render("{group}", sneaky)));
    }

    /** Sections by id, drawn as plain lines; counts how often each is drawn. */
    private static final class FakeSections implements SectionFrame {
        private final Map<String, List<String>> sections = new LinkedHashMap<>();
        final Map<String, Integer> drawn = new HashMap<>();

        FakeSections with(String id, String... lines) { sections.put(id, List.of(lines)); return this; }

        @Override public List<String> ids() { return List.copyOf(sections.keySet()); }

        @Override
        public List<Component> lines(String id) {
            drawn.merge(id, 1, Integer::sum);
            return sections.getOrDefault(id, List.of()).stream().map(line -> (Component) Component.text(line)).toList();
        }

        @Override
        public Component inline(String id) {
            drawn.merge(id, 1, Integer::sum);
            return Component.text(String.join(" ", sections.getOrDefault(id, List.of())));
        }
    }

    private static List<String> plainLines(List<Component> lines) {
        return lines.stream().map(HudFormatterTest::plain).toList();
    }

    @Test
    void theAddonsLineTakesEverySectionOrNothing() {
        List<String> templates = List.of("<gray>Here: <white>{temp}", "{addons}", "end");
        assertEquals(List.of("Here: 72.0°F", "end"),
                plainLines(format.lines(templates, cooled(72.0), SectionFrame.NONE)), "no sections, no lines");
        FakeSections two = new FakeSections().with("a:one", "A1", "A2").with("b:two", "B1");
        assertEquals(List.of("Here: 72.0°F", "A1", "A2", "B1", "end"),
                plainLines(format.lines(templates, cooled(72.0), two)), "in registration order");
        FakeSections hiding = new FakeSections().with("a:one").with("b:two", "B1");
        assertEquals(List.of("Here: 72.0°F", "B1", "end"),
                plainLines(format.lines(templates, cooled(72.0), hiding)), "an empty section takes no lines");
    }

    @Test
    void aPlacedSectionShowsOnlyWhereItIsPlaced() {
        FakeSections two = new FakeSections().with("a:one", "A1").with("b:two", "B1");
        List<String> templates = List.of("{section:B:Two}", "middle", "{addons}", "{section:nobody:here}");
        assertEquals(List.of("B1", "middle", "A1"), plainLines(format.lines(templates, cooled(72.0), two)),
                "placed once, ids in any case, unknown ids render nothing");
        assertEquals(1, two.drawn.get("b:two"), "drawn once");
    }

    @Test
    void seasonLinesAreStillLeftOutWithoutSeasons() {
        FakeSections one = new FakeSections().with("a:one", "A1");
        List<String> templates = List.of(HudSettings.SEASON_LINE, "{addons}");
        assertEquals(List.of("A1"), plainLines(format.lines(templates, cooled(72.0), one)));
    }

    @Test
    void hiddenSectionsNeverShow() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("hud.sections.hidden", List.of("a:one"));
        HudFormatter hiding = new HudFormatter(HudSettings.load(config));
        FakeSections two = new FakeSections().with("a:one", "A1").with("b:two", "B1");
        assertEquals(List.of("B1"), plainLines(hiding.lines(List.of("{addons}"), cooled(72.0), two)));
        assertEquals(List.of(), plainLines(hiding.lines(List.of("{section:a:one}"), cooled(72.0), two)));
        assertEquals("Here ", plain(hiding.render("Here {section:a:one}", cooled(72.0), two)));
        assertFalse(two.drawn.containsKey("a:one"), "a hidden section is never even drawn");
    }

    @Test
    void oneLineFormatsShowASectionInline() {
        FakeSections one = new FakeSections().with("a:one", "Body", "98.6");
        assertEquals("72.0°F | Body 98.6", plain(format.render("{temp} | {section:a:one}", cooled(72.0), one)));
        assertEquals("72.0°F | ", plain(format.render("{temp} | {section:x:y}", cooled(72.0), one)));
        assertEquals("72.0°F", plain(format.render("{temp}", cooled(72.0), one)));
    }

    @Test
    void addonBarsUseTheHudsGlyphsAndLength() {
        TextColor red = TextColor.color(0xFF5C5C);
        assertEquals("▮▮▮▮▮▯▯▯▯▯", plain(format.bar(0.5, red)));
        assertEquals("▯▯▯▯▯▯▯▯▯▯", plain(format.bar(-1, red)));
        assertEquals("▮▮▮▮▮▮▮▮▮▮", plain(format.bar(7, red)));
        assertEquals("▯▯▯▯▯▯▯▯▯▯", plain(format.bar(Double.NaN, red)));
    }
}
