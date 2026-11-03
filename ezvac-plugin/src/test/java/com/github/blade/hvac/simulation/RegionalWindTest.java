package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.WindSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wind differs from region to region (BETA 1.0 Pilot 3), never by more
 * than about a level, and the regions drift downwind through the day.
 */
class RegionalWindTest {
    private static final WindSettings SETTINGS = WindSettings.defaults();
    private static final long SEED = 8_675_309L;
    private static final long DAY = WindModel.DAY_TICKS;

    private static WindSettings regions(WindSettings.Regions regions) {
        return new WindSettings(true, SETTINGS.chances(), SETTINGS.speeds(), SETTINGS.seasons(), 0.0, 0.05, 2.0,
                1.5, 0.03, 0.06, WindSettings.Units.AUTO, Map.of(), SETTINGS.direction(), regions);
    }

    @Test
    void regionsStayWithinALevelOfTheDay() {
        for (long day = 0; day < 300; day++) {
            WindModel.Day wind = WindModel.day(SEED, day, 1.0, SETTINGS);
            int level = wind.level().ordinal();
            for (int x = -20_000; x <= 20_000; x += 2_500)
                for (int z = -20_000; z <= 20_000; z += 2_500) {
                    WindModel.Day here = WindModel.dayAt(SEED, wind, x, z, WindModel.Belts.WESTERLIES, SETTINGS);
                    assertTrue(Math.abs(here.level().ordinal() - level) <= 1,
                            wind.level() + " day reads " + here.level() + " at " + x + ", " + z);
                }
        }
    }

    @Test
    void aHeldLevelIsThatLevelEverywhere() {
        // Found live: a held gale read Windy in a calmer region, which is no use for testing a build in a gale.
        WindModel.Day gale = WindModel.day(SEED, 4, WindModel.Level.GALE, SETTINGS).holding();
        for (int x = -30_000; x <= 30_000; x += 1_500) {
            WindModel.Day here = WindModel.dayAt(SEED, gale, x, -x / 2.0, WindModel.Belts.WESTERLIES, SETTINGS);
            assertEquals(WindModel.Level.GALE, here.level(), "at " + x);
            assertTrue(here.held());
        }
    }

    @Test
    void farApartTheWindDiffersAndNearbyItDoesNot() {
        WindModel.Day wind = WindModel.day(SEED, 12, WindModel.Level.WINDY, SETTINGS);
        double spread = 0.0;
        for (int x = 0; x < 30_000; x += 3_000)
            spread = Math.max(spread, Math.abs(WindModel.regionalMean(SEED, wind, x, 0, 6, SETTINGS)
                    - WindModel.regionalMean(SEED, wind, x + 3_000, 0, 6, SETTINGS)));
        assertTrue(spread > 1.5, "3,000 blocks apart it's windier in one place: " + spread + " m/s");
        for (int x = 0; x < 30_000; x += 1_000)
            assertEquals(WindModel.regionalMean(SEED, wind, x, 0, 6, SETTINGS),
                    WindModel.regionalMean(SEED, wind, x + 4, 0, 6, SETTINGS), 0.05, "no seams at " + x);
    }

    @Test
    void aWindyPatchDriftsDownwind() {
        WindSettings slow = regions(new WindSettings.Regions(true, 1500, 0.4, 150));
        WindModel.Day west = WindModel.day(SEED, 20, WindModel.Level.WINDY, slow).from(270.0);
        // From the west it blows toward +x: what was at x at hour 2 is at x + 150 * 8 by hour 10.
        for (int x = -5_000; x < 5_000; x += 700)
            assertEquals(WindModel.field(SEED, west, 7, x, 300, 2.0, slow.regions()),
                    WindModel.field(SEED, west, 7, x + 1_200, 300, 10.0, slow.regions()), 1e-9);
    }

    @Test
    void switchedOffTheWindIsTheSameEverywhere() {
        WindSettings off = regions(new WindSettings.Regions(false, 1500, 0.4, 150));
        WindModel.Day wind = WindModel.day(SEED, 9, WindModel.Level.BREEZY, off);
        for (int x = -10_000; x <= 10_000; x += 2_000)
            assertEquals(wind.meanMs(), WindModel.regionalMean(SEED, wind, x, -x, 3, off), 1e-12);
    }

    @Test
    void theWindEasesOverFromYesterdaysRegionsAtDawn() {
        WindModel.Day yesterday = WindModel.day(SEED, 30, 1.0, SETTINGS);
        WindModel.Day today = WindModel.day(SEED, 31, 1.0, SETTINGS);
        WindSettings still = regions(SETTINGS.regions());
        double end = WindModel.at(SEED, 31 * DAY - 1, 800, 800, yesterday, null, WindModel.Belts.WESTERLIES,
                WindModel.Front.NONE, still).speedMs();
        double dawn = WindModel.at(SEED, 31 * DAY, 800, 800, today, yesterday, WindModel.Belts.WESTERLIES,
                WindModel.Front.NONE, still).speedMs();
        assertEquals(end, dawn, 0.01, "dawn carries on from the night before");
    }

    @Test
    void theSameSeedGivesTheSameWind() {
        WindModel.Day wind = WindModel.day(SEED, 77, 1.0, SETTINGS);
        assertEquals(WindModel.at(SEED, 77 * DAY + 5_555, 1234, -987, wind, null, WindModel.belts(70.0),
                        WindModel.Front.NONE, SETTINGS),
                WindModel.at(SEED, 77 * DAY + 5_555, 1234, -987, wind, null, WindModel.belts(70.0),
                        WindModel.Front.NONE, SETTINGS));
    }

    @Test
    void settingsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> new WindSettings.Regions(true, 50, 0.4, 150));
        assertThrows(IllegalArgumentException.class, () -> new WindSettings.Regions(true, 1500, 0.9, 150));
        assertThrows(IllegalArgumentException.class, () -> new WindSettings.Direction(3.0, true));
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("wind.regions.size-blocks", 3000);
        yaml.set("wind.direction.fronts", false);
        WindSettings loaded = WindSettings.load(yaml, warning -> fail(warning));
        assertEquals(3000, loaded.regions().sizeBlocks());
        assertFalse(loaded.direction().fronts());
        yaml.set("wind.regions.strength", 5.0);
        java.util.List<String> warnings = new java.util.ArrayList<>();
        assertEquals(WindSettings.Regions.DEFAULT, WindSettings.load(yaml, warnings::add).regions());
        assertEquals(1, warnings.size(), "a broken section falls back with a warning");
    }
}
