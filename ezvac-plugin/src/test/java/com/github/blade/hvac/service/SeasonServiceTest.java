package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.simulation.Seasons;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Each world's calendar, and seasons.yml (2.1). */
class SeasonServiceTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final UUID NETHER = UUID.fromString("7d1e2f3a-4b5c-4d6e-8f70-112233445566");
    private static final long DAY = Seasons.TICKS_PER_DAY;

    @TempDir Path folder;
    private final List<String> warnings = new ArrayList<>();

    private SeasonService service(ClimateSettings.Seasons settings) {
        SeasonService service = new SeasonService(folder.toFile(), settings, warnings::add);
        service.load();
        return service;
    }

    private SeasonService service() {
        return service(ClimateSettings.defaults().seasons());
    }

    @Test
    void aNewWorldStartsInEarlySpring() {
        SeasonService seasons = service();
        seasons.advance(WORLD, "world", 960 * DAY + 6_000);   // eight 120-day years
        assertTrue(seasons.tracks(WORLD));
        assertEquals(Seasons.Season.SPRING, seasons.season(WORLD));
        assertEquals(1, seasons.dayOfSeason(WORLD));
        assertTrue(seasons.airFactor(WORLD) < -0.6, "early spring is still cool");
        assertEquals(0.0, seasons.airFactor(NETHER), "a world without a calendar has no seasons");
        assertFalse(seasons.tracks(NETHER));
        assertThrows(IllegalArgumentException.class, () -> seasons.next(NETHER));
    }

    @Test
    void commandsMoveTheCalendar() {
        SeasonService seasons = service();
        seasons.advance(WORLD, "world", 6_000);
        seasons.set(WORLD, Seasons.Season.SUMMER, 16);
        assertEquals(Seasons.Season.SUMMER, seasons.season(WORLD));
        assertEquals(16, seasons.dayOfSeason(WORLD));
        assertEquals(1.0, seasons.airFactor(WORLD), 0.01, "mid-summer at noon");
        seasons.next(WORLD);
        assertEquals(Seasons.Season.AUTUMN, seasons.season(WORLD));
        assertEquals(1, seasons.dayOfSeason(WORLD));
        seasons.setPaused(WORLD, true);
        seasons.advance(WORLD, "world", DAY + 6_000);
        assertEquals(1, seasons.dayOfSeason(WORLD), "paused");
        assertTrue(seasons.paused(WORLD));
    }

    @Test
    void theCalendarSurvivesARestart() {
        SeasonService seasons = service();
        seasons.advance(WORLD, "world", 6_000);
        seasons.set(WORLD, Seasons.Season.WINTER, 4);
        seasons.setPaused(WORLD, true);
        seasons.save();
        assertTrue(Files.isRegularFile(folder.resolve("seasons.yml")));
        assertFalse(Files.exists(folder.resolve("seasons.yml.tmp")));

        SeasonService again = service();
        again.advance(WORLD, "world", 6_000);
        assertEquals(Seasons.Season.WINTER, again.season(WORLD));
        assertEquals(4, again.dayOfSeason(WORLD));
        assertTrue(again.paused(WORLD));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    /** The seasons.yml the released 2.1 Pilot 2 wrote, set to mid-summer, must load as it was. */
    @Test
    void aReleasedCalendarLoads() throws IOException {
        try (var input = getClass().getResourceAsStream("/compat/ALPHA-2.1-P2/seasons.yml")) {
            Files.copy(java.util.Objects.requireNonNull(input, "missing fixture"), folder.resolve("seasons.yml"));
        }
        UUID world = UUID.fromString("b9bff999-d8d4-4113-894b-9bd99c140665");
        // Counted in that release's 15-day seasons: since Pilot 4 it's summer day 8 of 30.
        SeasonService seasons = new SeasonService(folder.toFile(), ClimateSettings.defaults().seasons(), warnings::add);
        seasons.load(15);
        seasons.advance(world, "world", 6_000);
        assertEquals(Seasons.Season.SUMMER, seasons.season(world));
        assertEquals(8, seasons.dayOfSeason(world));
        assertFalse(seasons.paused(world));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void anUnreadableFileIsKeptAsideAndTheYearStartsAgain() throws IOException {
        Files.writeString(folder.resolve("seasons.yml"), "worlds: [unclosed");
        SeasonService seasons = service();
        assertEquals(1, warnings.size(), warnings::toString);
        assertTrue(Files.isRegularFile(folder.resolve("seasons.yml.unreadable")));
        seasons.advance(WORLD, "world", 0);
        assertEquals(Seasons.Season.SPRING, seasons.season(WORLD));
    }

    @Test
    void switchedOffThereAreNoSeasons() {
        SeasonService seasons = service(new ClimateSettings.Seasons(false, 15, 1.0, 5.0));
        seasons.advance(WORLD, "world", 6_000);
        seasons.set(WORLD, Seasons.Season.SUMMER, 8);
        assertEquals(0.0, seasons.airFactor(WORLD));
        assertEquals(0.0, seasons.waterFactor(WORLD));
    }

    @Test
    void aTurningSeasonIsReported() {
        SeasonService seasons = service();
        List<Seasons.Season> turns = new ArrayList<>();
        seasons.setListener((world, from) -> turns.add(from));
        seasons.advance(WORLD, "world", 6_000);                 // first sight: nothing turned
        seasons.advance(WORLD, "world", DAY + 6_000);           // spring day 2: same season
        assertTrue(turns.isEmpty(), turns::toString);
        seasons.set(WORLD, Seasons.Season.SPRING, 30);
        assertTrue(turns.isEmpty(), "still spring");
        seasons.advance(WORLD, "world", 2 * DAY + 6_000);       // the next morning is summer
        assertEquals(List.of(Seasons.Season.SPRING), turns);
        seasons.next(WORLD);
        assertEquals(List.of(Seasons.Season.SPRING, Seasons.Season.SUMMER), turns);
        seasons.set(WORLD, Seasons.Season.WINTER, 3);
        assertEquals(Seasons.Season.AUTUMN, turns.get(2));
    }

    @Test
    void anUnloadedWorldKeepsItsCalendar() {
        SeasonService seasons = service();
        seasons.advance(WORLD, "world", 6_000);
        seasons.set(WORLD, Seasons.Season.SUMMER, 2);
        seasons.forget(WORLD);
        assertFalse(seasons.tracks(WORLD));
        seasons.advance(WORLD, "world", 6_000);
        assertEquals(Seasons.Season.SUMMER, seasons.season(WORLD));
        assertEquals(2, seasons.dayOfSeason(WORLD));
    }

    @Test
    void aCalendarCountedInFifteenDaySeasonsKeepsItsSeasonAndDay() throws IOException {
        // BETA 1.0 Pilot 4: a Pilot 3 seasons.yml, without a days-per-season, on summer day 8.
        Files.writeString(folder.resolve("seasons.yml"), "worlds:\n  " + WORLD + ":\n    name: world\n"
                + "    day: 22\n    last-world-day: 22\n    paused: false\n");
        SeasonService seasons = new SeasonService(folder.toFile(), ClimateSettings.defaults().seasons(), warnings::add);
        seasons.load(15);
        assertEquals(1, seasons.converted());
        seasons.advance(WORLD, "world", 22 * DAY + 6_000);
        assertEquals(Seasons.Season.SUMMER, seasons.season(WORLD));
        assertEquals(8, seasons.dayOfSeason(WORLD), "of 30 now");
        assertTrue(Files.isRegularFile(folder.resolve("seasons.yml.before-30-day-seasons.bak")), "the old file kept");
        String saved = Files.readString(folder.resolve("seasons.yml"));
        assertTrue(saved.contains("days-per-season: 30"), saved);
        assertTrue(saved.contains("day: 37"), saved);

        SeasonService again = new SeasonService(folder.toFile(), ClimateSettings.defaults().seasons(), warnings::add);
        again.load(15);
        assertEquals(0, again.converted(), "the file says 30 now, so the next start changes nothing");

        SeasonService shorter = new SeasonService(folder.toFile(), new ClimateSettings.Seasons(true, 10, 1.0, 5.0),
                warnings::add);
        shorter.load(15);
        shorter.advance(WORLD, "world", 22 * DAY + 6_000);
        assertEquals(Seasons.Season.SUMMER, shorter.season(WORLD), "an admin's own change converts too");
        assertEquals(8, shorter.dayOfSeason(WORLD));
        assertEquals(List.of(), warnings);
    }

    @Test
    void aCalendarWithoutTheLengthCountsInTodaysWhenNothingChanged() throws IOException {
        Files.writeString(folder.resolve("seasons.yml"), "worlds:\n  " + WORLD + ":\n    name: world\n"
                + "    day: 22\n    last-world-day: 22\n    paused: false\n");
        SeasonService seasons = service();
        assertEquals(0, seasons.converted(), "a server already on 30 by its owner's choice");
        seasons.advance(WORLD, "world", 22 * DAY + 6_000);
        assertEquals(23, seasons.dayOfSeason(WORLD));
    }
}
