package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.WindSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where the wind blows from (BETA 1.0 Pilot 3): by climate belt, day by day,
 * through the day, and as rain passes.
 */
class WindDirectionTest {
    private static final WindSettings SETTINGS = WindSettings.defaults();
    private static final long SEED = 8_675_309L;
    private static final long DAY = WindModel.DAY_TICKS;

    /** How far apart two directions are, the short way round. */
    private static double apart(double a, double b) {
        double d = Math.abs(WindModel.normalize(a) - WindModel.normalize(b));
        return Math.min(d, 360.0 - d);
    }

    /** The average direction of many days, as arrows. */
    private static double average(WindModel.Belts belts) {
        double x = 0.0, z = 0.0;
        for (long day = 0; day < 2_000; day++) {
            double from = Math.toRadians(WindModel.day(SEED, day, 1.0, SETTINGS).fromDegrees(belts));
            x += Math.sin(from);
            z += Math.cos(from);
        }
        return WindModel.normalize(Math.toDegrees(Math.atan2(x, z)));
    }

    @Test
    void eachClimateHasItsOwnBelt() {
        assertEquals(WindModel.Belts.WESTERLIES, WindModel.belts(55.0));
        assertEquals(1.0, WindModel.belts(90.0).trades(), 1e-12, "a desert lies in the trade winds");
        assertEquals(1.0, WindModel.belts(15.0).polar(), 1e-12, "snowy plains in the polar easterlies");
        WindModel.Belts edge = WindModel.belts(74.0);
        assertTrue(edge.trades() > 0.0 && edge.westerlies() > 0.0, "the belts blend at their edges: " + edge);
        for (double meanF = -20.0; meanF <= 120.0; meanF += 0.5) {
            WindModel.Belts belts = WindModel.belts(meanF);
            assertEquals(1.0, belts.trades() + belts.westerlies() + belts.polar(), 1e-9, "at " + meanF);
        }
        assertEquals(WindModel.Belts.WESTERLIES, WindModel.belts(Double.NaN));
    }

    @Test
    void overTheYearsEachBeltBlowsFromItsUsualQuarter() {
        double westerlies = average(WindModel.Belts.WESTERLIES);
        double trades = average(WindModel.belts(90.0));
        double polar = average(WindModel.belts(10.0));
        assertTrue(apart(westerlies, WindModel.WESTERLIES_FROM) < 10.0, "temperate: from the WSW, " + westerlies);
        assertTrue(apart(trades, WindModel.TRADES_FROM) < 5.0, "hot: from the ENE, " + trades);
        assertTrue(apart(polar, WindModel.POLAR_FROM) < 8.0, "cold: from the NE, " + polar);
    }

    @Test
    void theWesterliesWanderMoreThanTheTrades() {
        double westerlies = 0.0, trades = 0.0;
        int anywhere = 0;
        for (long day = 0; day < 2_000; day++) {
            WindModel.Day wind = WindModel.day(SEED, day, 1.0, SETTINGS);
            double off = apart(wind.directions().westerlies(), WindModel.WESTERLIES_FROM);
            westerlies += off;
            if (off > 135.0) anywhere++;
            trades += apart(wind.directions().trades(), WindModel.TRADES_FROM);
        }
        assertTrue(westerlies > trades * 1.6, westerlies / 2_000 + "° against " + trades / 2_000 + "°");
        assertTrue(anywhere > 20 && anywhere < 200, "now and then a day blows from anywhere: " + anywhere);
        WindSettings steady = new WindSettings(true, SETTINGS.chances(), SETTINGS.speeds(), SETTINGS.seasons(),
                SETTINGS.gusts(), 0.05, 2.0, 1.5, 0.03, 0.06, WindSettings.Units.AUTO, java.util.Map.of(),
                new WindSettings.Direction(0.0, true), SETTINGS.regions());
        assertEquals(WindModel.WESTERLIES_FROM, WindModel.day(SEED, 42, 1.0, steady).fromDegrees(), 1e-9,
                "with no variation it always blows from the belt's");
    }

    @Test
    void directionsBlendAsArrowsSoNorthHasNoSeam() {
        WindModel.Directions around = new WindModel.Directions(350.0, 10.0, 0.0);
        double half = around.blend(new WindModel.Belts(0.5, 0.5, 0.0));
        assertTrue(apart(half, 0.0) < 1e-6, "halfway between 350° and 10° is north, not south: " + half);
        assertEquals(10.0, WindModel.between(350.0, 30.0, 0.5), 1e-9, "the short way round");
        assertEquals(350.0, WindModel.between(10.0, 330.0, 0.5), 1e-9);
        assertEquals(270.0, WindModel.normalize(-90.0), 1e-9);
    }

    @Test
    void theDirectionTurnsSmoothlyThroughTheDayAndAtDawn() {
        WindModel.Day yesterday = WindModel.day(SEED, 40, 1.0, SETTINGS);
        WindModel.Day today = WindModel.day(SEED, 41, 1.0, SETTINGS);
        double previous = Double.NaN;
        for (long tick = 41 * DAY - 2_000; tick < 42 * DAY; tick += 10) {
            WindModel.Day now = tick < 41 * DAY ? yesterday : today;
            WindModel.Day before = tick < 41 * DAY ? WindModel.day(SEED, 39, 1.0, SETTINGS) : yesterday;
            double from = WindModel.at(SEED, tick, 100, 200, now, before, WindModel.Belts.WESTERLIES,
                    WindModel.Front.NONE, SETTINGS).fromDegrees();
            if (Double.isFinite(previous)) assertTrue(apart(previous, from) < 1.5, "no jump at tick " + tick);
            previous = from;
        }
    }

    @Test
    void rainPassingThroughTurnsTheWesterlies() {
        assertEquals(WindModel.Front.NONE, WindModel.front(false, 20_000, 0, -1, -1), "no rain for hours");
        assertEquals(WindModel.Front.NONE, WindModel.front(false, 2_000, 6_000, -1, -1), "/weather clear holds");
        WindModel.Front ahead = WindModel.front(false, 1_000, 0, -1, -1);
        assertEquals(WindModel.Front.Kind.AHEAD, ahead.kind());
        assertEquals(WindModel.AHEAD_FROM, ahead.fromDegrees(), 1e-9);
        assertTrue(ahead.strength() > 0.8, "an hour before rain it's nearly all the way round");
        WindModel.Front raining = WindModel.front(true, 9_000, 0, 5_000, -1);
        assertEquals(WindModel.RAIN_FROM, raining.fromDegrees(), 1e-9);
        WindModel.Front starting = WindModel.front(true, 9_000, 0, 0, -1);
        assertEquals(WindModel.AHEAD_FROM, starting.fromDegrees(), 1e-9, "it swings round as the rain begins");
        WindModel.Front behind = WindModel.front(false, 50_000, 0, -1, 2_000);
        assertEquals(WindModel.Front.Kind.BEHIND, behind.kind());
        assertEquals(WindModel.BEHIND_FROM, behind.fromDegrees(), 1e-9, "veered west-northwest once it cleared");
        assertEquals(WindModel.Front.NONE, WindModel.front(false, 50_000, 0, -1, 7_000), "gone after six hours");

        WindModel.Day calm = WindModel.day(SEED, 5, 1.0, SETTINGS).from(WindModel.WESTERLIES_FROM);
        WindSettings still = new WindSettings(true, SETTINGS.chances(), SETTINGS.speeds(), SETTINGS.seasons(), 0.0,
                0.05, 2.0, 1.5, 0.03, 0.06, WindSettings.Units.AUTO, java.util.Map.of(),
                new WindSettings.Direction(0.0, true), new WindSettings.Regions(false, 1500, 0.4, 150));
        double temperate = WindModel.at(SEED, 5 * DAY + 6_000, 0, 0, calm, null, WindModel.Belts.WESTERLIES,
                raining, still).fromDegrees();
        assertTrue(apart(temperate, WindModel.RAIN_FROM) < apart(WindModel.WESTERLIES_FROM, WindModel.RAIN_FROM),
                "it comes round toward the southwest: " + temperate);
        double tropical = WindModel.at(SEED, 5 * DAY + 6_000, 0, 0, calm, null, WindModel.belts(95.0), raining,
                still).fromDegrees();
        assertEquals(WindModel.WESTERLIES_FROM, tropical, 1e-9, "the trades don't feel fronts");
    }

    @Test
    void aHeldDirectionHolds() {
        WindModel.Day north = WindModel.day(SEED, 3, WindModel.Level.GALE, SETTINGS).from(0.0);
        WindSettings still = new WindSettings(true, SETTINGS.chances(), SETTINGS.speeds(), SETTINGS.seasons(), 0.0,
                0.05, 2.0, 1.5, 0.03, 0.06, WindSettings.Units.AUTO, java.util.Map.of(),
                new WindSettings.Direction(0.0, false), new WindSettings.Regions(false, 1500, 0.4, 150));
        for (WindModel.Belts belts : new WindModel.Belts[]{WindModel.Belts.WESTERLIES, WindModel.belts(95.0)})
            assertEquals(0.0, apart(0.0, WindModel.at(SEED, 3 * DAY + 4_000, 500, -900, north, null, belts,
                    WindModel.Front.NONE, still).fromDegrees()), 1e-9);
    }
}
