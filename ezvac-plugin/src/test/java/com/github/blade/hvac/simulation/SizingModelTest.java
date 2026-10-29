package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.SizingSettings;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.OperatingProfile;
import com.github.blade.hvac.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Size-based heating and cooling (BETA 1.0): the maintainer's rule, and how it plays out. */
class SizingModelTest {
    private static final SizingSettings SIZING = SizingSettings.defaults();
    private static final HvacSettings SETTINGS = HvacSettings.defaults();
    /** One three-star unit's worth: a floor of about 465 blocks, 4 blocks high. */
    private static final int HOUSE = 1860;

    @Test
    void oneThreeStarUnitPer1860BlocksOfAir() {
        assertEquals(1.0, SizingModel.required(HOUSE, false, SIZING), 1e-9);
        assertEquals(6.0, SizingModel.required(6 * HOUSE, false, SIZING), 1e-9, "six times the air needs six");
        assertEquals(2.0, SizingModel.required(3000, true, SIZING), 1e-9, "pools: 1,500 water blocks a heater");
        assertEquals(1.0, SizingModel.factor(HOUSE, false, SIZING), 1e-9);
        assertEquals(0.5, SizingModel.factor(2 * HOUSE, false, SIZING), 1e-9);
        assertEquals(2.0, SizingModel.factor(100, false, SIZING), 1e-9, "a closet is quicker, up to the cap");
    }

    @Test
    void unitsAddUpInFullSoTheRightNumberPacesAsOneDidInAHouse() {
        double six = SizingModel.combine(0.0, 6.0, SizingModel.factor(6 * HOUSE, false, SIZING), SETTINGS);
        assertEquals(1.0, six, 1e-9, "six units in six houses' worth push like one in one");
        double stacked = SizingModel.combine(0.0, 6.0, SizingModel.factor(HOUSE, false, SIZING), SETTINGS);
        assertEquals(SETTINGS.maximumSystemCapacity(), stacked, 1e-9, "stacking units in a small house still caps");
        assertEquals(ThermalModel.aggregateIndependentCapacity(1.0, 3.0, SETTINGS.maximumSystemCapacity()),
                SizingModel.combine(1.0, 3.0, Double.NaN, SETTINGS), 1e-12, "sizing off: exactly as before");
        assertEquals(1.0, SizingModel.combine(0.0, 1.0, 1.0, SETTINGS),
                SizingModel.combine(0.0, 1.0, Double.NaN, SETTINGS), "one unit in a sized house: today's pace");
    }

    @Test
    void openingsLeakAndAreCapped() {
        assertEquals(1.0, SizingModel.openness(0, HOUSE, SIZING));
        double door = SizingModel.openness(2, HOUSE, SIZING);
        assertTrue(door > 1.8 && door < 2.6, "one open door in a house: " + door);
        assertEquals(SIZING.maximumOpenness(), SizingModel.openness(400, HOUSE, SIZING), "a barn with open walls");
        assertTrue(SizingModel.openness(2, 10 * HOUSE, SIZING) < door, "a door matters less to a bigger building");
    }

    private static long eta(int size, int openings, int units, double outdoorF, OperatingMode mode, double fromF,
                            double toF) {
        List<EtaEstimator.UnitSnapshot> snapshots = java.util.Collections.nCopies(units,
                new EtaEstimator.UnitSnapshot(mode == OperatingMode.COOLING ? EquipmentType.AIR_CONDITIONER
                        : EquipmentType.FURNACE, 0.0, Tier.VARIABLE));
        OptionalLong ticks = EtaEstimator.estimateTicks(fromF, toF, outdoorF, mode, snapshots,
                OperatingProfile.NORMAL, SETTINGS, SizingModel.openness(openings, size, SIZING),
                SizingModel.factor(size, false, SIZING));
        return ticks.orElse(Long.MAX_VALUE);
    }

    @Test
    void aSizedHouseReachesItsTargetAsOneUnitAlwaysHas() {
        long sized = eta(HOUSE, 0, 1, 95.0, OperatingMode.COOLING, 85.0, 72.0);
        long before = EtaEstimator.estimateTicks(85.0, 72.0, 95.0, OperatingMode.COOLING,
                List.of(new EtaEstimator.UnitSnapshot(EquipmentType.AIR_CONDITIONER, 0.0, Tier.VARIABLE)),
                OperatingProfile.NORMAL, SETTINGS, 1.0).orElseThrow();
        assertEquals(before, sized, "the reference house keeps today's ETA");
        assertTrue(sized < 6 * 20 * 60, "and it's reasonable on a hot day: " + sized / 1200.0 + " min");
        assertEquals(sized, eta(6 * HOUSE, 0, 6, 95.0, OperatingMode.COOLING, 85.0, 72.0),
                "six units in six times the air keep that pace");
        long heating = eta(HOUSE, 0, 1, 20.0, OperatingMode.HEATING, 60.0, 72.0);
        assertTrue(heating < 6 * 20 * 60, "a sized house heats in good time on a cold day: " + heating / 1200.0);
    }

    @Test
    void anUndersizedHouseIsSlowerAndATooBigOneBarelyHolds() {
        long sized = eta(HOUSE, 0, 1, 95.0, OperatingMode.COOLING, 85.0, 72.0);
        long triple = eta(3 * HOUSE, 0, 1, 95.0, OperatingMode.COOLING, 85.0, 72.0);
        assertTrue(triple > 3 * sized, "a third the push and the same leak: " + triple / (double) sized);
        assertEquals(Long.MAX_VALUE, eta(8 * HOUSE, 0, 1, 95.0, OperatingMode.COOLING, 85.0, 72.0),
                "one unit can't cool eight times its air against a hot day");
    }

    @Test
    void anOpenFrontDoorStretchesTheEtaByAThirdToAHalf() {
        long sealed = eta(HOUSE, 0, 1, 95.0, OperatingMode.COOLING, 85.0, 72.0);
        long open = eta(HOUSE, 2, 1, 95.0, OperatingMode.COOLING, 85.0, 72.0);
        double ratio = open / (double) sealed;
        assertTrue(ratio >= 1.3 && ratio <= 1.6, "one open door: x" + ratio);
    }

    @Test
    void neighboursPullHalfTheGapAtMost() {
        double pulled = SizingModel.pull(20, HOUSE, 70.0, 80.0, 1000L, SIZING);
        assertTrue(pulled > 0.0 && pulled < 5.0, "a doorway pulls a little: " + pulled);
        assertEquals(-pulled, SizingModel.pull(20, HOUSE, 80.0, 70.0, 1000L, SIZING), 1e-12, "both ways alike");
        assertEquals(5.0, SizingModel.pull(100_000, HOUSE, 70.0, 80.0, 1000L, SIZING), 1e-12, "never past halfway");
        assertEquals(0.0, SizingModel.pull(0, HOUSE, 70.0, 80.0, 1000L, SIZING), "no touching air, no pull");
    }

    /** Two rooms, one heating to 75 and one cooling to 68, stepped as the controller does. */
    private static double[] neighbours(int contact, int hours) {
        double a = 60.0, b = 85.0;
        double[] last = new double[2];
        for (long tick = 0; tick < hours * 1000L; tick += 20) {
            double startA = a, startB = b;
            a = ThermalModel.step(a, 75.0 + SETTINGS.heatingStopAboveF(), 50.0, OperatingMode.HEATING, 1.0, 20, SETTINGS)
                    .temperatureF() + SizingModel.pull(contact, HOUSE, startA, startB, 20, SIZING);
            b = ThermalModel.step(b, 68.0 - SETTINGS.coolingStopBelowF(), 90.0, OperatingMode.COOLING, 1.0, 20, SETTINGS)
                    .temperatureF() + SizingModel.pull(contact, HOUSE, startB, startA, 20, SIZING);
            last[0] = a;
            last[1] = b;
            assertTrue(a < 120 && a > 0 && b < 120 && b > 0, "never runs away");
        }
        return last;
    }

    @Test
    void aHeatedRoomBesideACooledOneThroughADoorwayBothHoldTheirTargets() {
        double[] settled = neighbours(20, 20);
        assertTrue(Math.abs(settled[0] - 75.0) < 2.0, "the heated room: " + settled[0]);
        assertTrue(Math.abs(settled[1] - 68.0) < 2.0, "the cooled room: " + settled[1]);
        double[] later = neighbours(20, 21);
        assertEquals(settled[0], later[0], 0.05, "and stays there, no back and forth");
    }

    @Test
    void anOpenPlanSharedByTwoSystemsEvensOutWithoutRunningAway() {
        double[] settled = neighbours(600, 20);
        assertTrue(settled[0] > settled[1] - 0.01, "they meet but never cross");
        assertTrue(settled[0] - settled[1] < 4.0, "an open plan mixes: " + settled[0] + " / " + settled[1]);
    }

    @Test
    void anAssessmentSaysWhatItHasOfWhatItNeeds() {
        SizingModel.Assessment small = new SizingModel.Assessment(3 * HOUSE, false, 0, 3.0, 1.0, 1 / 3.0, 1.0);
        assertTrue(small.undersized());
        assertEquals("1/3", small.ratio());
        assertEquals("0.9/2.6", new SizingModel.Assessment(4836, false, 0, 2.6, 0.85, 0.38, 1.0).ratio());
        assertEquals("13/108", new SizingModel.Assessment(200_000, false, 0, 107.5, 12.5, 0.01, 1.0).ratio());
        assertFalse(new SizingModel.Assessment(HOUSE, false, 0, 1.0, 1.0, 1.0, 1.0).undersized());
    }
}
