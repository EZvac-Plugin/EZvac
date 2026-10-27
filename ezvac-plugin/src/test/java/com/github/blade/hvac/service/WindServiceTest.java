package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.simulation.Seasons;
import com.github.blade.hvac.simulation.WindModel;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Which day the wind is drawn for (3.2): the season calendar's while it runs, else the world's. */
class WindServiceTest {
    private static final UUID ID = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final long DAY = Seasons.TICKS_PER_DAY;
    private static final long SEED = 424_242L;
    private static final WindSettings SETTINGS = WindSettings.defaults();

    @TempDir Path folder;

    /** The clock a fake world reads, settable. */
    private long fullTime;

    private final World world = (World) Proxy.newProxyInstance(WindServiceTest.class.getClassLoader(),
            new Class<?>[]{World.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getUID" -> ID;
                case "getFullTime" -> fullTime;
                case "getSeed" -> SEED;
                case "getEnvironment" -> World.Environment.NORMAL;
                case "getName" -> "world";
                case "hashCode" -> 0;
                case "equals" -> proxy == args[0];
                default -> null;
            });

    private SeasonService seasons(boolean enabled) {
        ClimateSettings.Seasons settings = ClimateSettings.defaults().seasons();
        SeasonService seasons = new SeasonService(folder.toFile(), new ClimateSettings.Seasons(enabled,
                settings.daysPerSeason(), settings.strength(), settings.waterLagDays()), warning -> { });
        seasons.load();
        return seasons;
    }

    /** The world's clock moves on, and the calendar looks at it, as the controller does each cycle. */
    private void clock(SeasonService seasons, long time) {
        fullTime = time;
        seasons.advance(ID, "world", time);
    }

    @Test
    void theWindFollowsTheSeasonCalendarsDay() {
        SeasonService seasons = seasons(true);
        WindService wind = new WindService(SETTINGS, seasons);
        clock(seasons, 900 * DAY + 6_000);
        assertEquals(0, wind.day(world).day(), "a new calendar's first day, whatever the world's clock says");

        seasons.set(ID, Seasons.Season.WINTER, 5);
        long winterDay5 = 3L * seasons.settings().daysPerSeason() + 4;
        WindModel.Day winter = wind.day(world);
        assertEquals(winterDay5, winter.day(), "/hvac season set brings that day's wind at once");
        assertEquals(WindModel.day(SEED, winterDay5, SETTINGS.seasons().winter(), SETTINGS), winter,
                "drawn with winter's odds");
        assertEquals(winterDay5 + 1, wind.forecast(world, 2).get(1).day());

        clock(seasons, 900 * DAY + 20_000);
        assertEquals(winter, wind.day(world), "the same day, later on");
        clock(seasons, 1_000);
        assertEquals(winter, wind.day(world), "/time set day sends the world's clock back, not the wind");
        clock(seasons, 2 * DAY + 1_000);
        assertEquals(winterDay5 + 1, wind.day(world).day(), "a day passes: the next day's wind");
    }

    @Test
    void withTheCalendarPausedOrNoSeasonsTheWorldsDayCounts() {
        SeasonService seasons = seasons(true);
        WindService wind = new WindService(SETTINGS, seasons);
        clock(seasons, 900 * DAY + 6_000);
        seasons.setPaused(ID, true);
        assertEquals(900, wind.day(world).day(), "a paused calendar's days stand still; the wind's don't");
        clock(seasons, 901 * DAY + 6_000);
        assertEquals(901, wind.day(world).day());

        WindService noSeasons = new WindService(SETTINGS, seasons(false));
        assertEquals(901, noSeasons.day(world).day());
        assertEquals(901, new WindService(SETTINGS, null).day(world).day());
    }

    @Test
    void aHeldLevelLastsUntilTheCalendarMoves() {
        SeasonService seasons = seasons(true);
        WindService wind = new WindService(SETTINGS, seasons);
        clock(seasons, 900 * DAY + 6_000);
        wind.hold(world, WindModel.Level.GALE);
        assertTrue(wind.held(world));
        assertEquals(WindModel.Level.GALE, wind.day(world).level());
        assertTrue(wind.speedMs(world) >= SETTINGS.speeds().gale().from() * (1 - SETTINGS.gusts()),
                "a held gale blows at once, even at dawn");
        seasons.next(ID);
        assertFalse(wind.held(world), "a new day from /hvac season next ends it");
        wind.hold(world, null);
        assertFalse(wind.held(world));
    }
}
