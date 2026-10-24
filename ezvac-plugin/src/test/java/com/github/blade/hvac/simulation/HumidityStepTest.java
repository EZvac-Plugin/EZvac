package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HumidityStepTest {
    private static final long HOUR = 1_000L;
    private final ClimateSettings settings = ClimateSettings.defaults();

    private double step(double dew, double room, double outdoorDew, HumidityModel.Loads loads, long ticks) {
        return HumidityModel.step(dew, room, outdoorDew, loads, ticks, settings).dewPointF();
    }

    @Test
    void aRoomStartsAtTheOutdoorMoistureAndDriftsTowardIt() {
        assertEquals(55.0, step(Double.NaN, 72, 55, HumidityModel.Loads.NONE, 0), 1e-9, "a pre-2.0 room");
        double drifted = step(40, 72, 60, HumidityModel.Loads.NONE, HOUR);
        assertTrue(drifted > 42.5 && drifted < 43.5, "about 15% of the gap per hour: " + drifted);
        assertEquals(60.0, step(40, 72, 60, HumidityModel.Loads.NONE, 100 * HOUR), 0.01, "and settles there");
    }

    @Test
    void theDewPointNeverExceedsTheRoom() {
        assertEquals(65.0, step(70, 65, 70, HumidityModel.Loads.NONE, HOUR), 1e-9, "the excess condenses");
    }

    @Test
    void coolingDriesTheAir() {
        HumidityModel.Loads cooling = new HumidityModel.Loads(1.0, 0, Double.NaN, 0);
        double dew = step(60, 72, 60, cooling, 100 * HOUR);
        assertTrue(dew > 48 && dew < 53, "an AC room in humid weather settles near 50 F: " + dew);
        assertTrue(Psychrometrics.relativeHumidity(72, dew) < 55);
        assertTrue(step(60, 72, 60, new HumidityModel.Loads(2.0, 0, Double.NaN, 0), 100 * HOUR) < dew,
                "more capacity, drier air");
    }

    @Test
    void waterInTheRoomAddsMoistureAndWarmWaterAddsMore() {
        HumidityModel.Loads pond = new HumidityModel.Loads(0, 0.05, 75, 0);
        HumidityModel.Loads heatedPool = new HumidityModel.Loads(0, 0.05, 88, 0);
        double still = step(50, 75, 50, pond, 100 * HOUR);
        double heated = step(50, 75, 50, heatedPool, 100 * HOUR);
        assertTrue(still > 55, "a pool hall gets humid: " + still);
        assertTrue(heated > still, "a heated pool more so: " + heated);
        assertTrue(Psychrometrics.relativeHumidity(75, heated) > 65, "muggy");
    }

    @Test
    void aDehumidifierDriesTheSpaceAndReportsWhatItRemoved() {
        HumidityModel.Result result = HumidityModel.step(62, 72, 62,
                new HumidityModel.Loads(0, 0, Double.NaN, 1.0), HOUR, settings);
        assertTrue(result.dewPointF() < 62);
        assertTrue(result.dehumidifiedF() > 2.0 && result.dehumidifiedF() <= 3.0, "about its rated 3 F per hour");
        assertEquals(0.0, HumidityModel.step(62, 72, 62, HumidityModel.Loads.NONE, HOUR, settings).dehumidifiedF());
    }

    @Test
    void aHumidifierAddsMoistureButNeverSaturatesTheRoom() {
        HumidityModel.Loads humidifier = new HumidityModel.Loads(0, 0, Double.NaN, 0, 1.0);
        double dryWinterRoom = Psychrometrics.dewPointF(72, 25);
        double hour = step(dryWinterRoom, 72, dryWinterRoom, humidifier, HOUR);
        assertTrue(hour > dryWinterRoom + 2.0 && hour <= dryWinterRoom + 3.0, "about its rated 3 F per hour");
        double settled = step(dryWinterRoom, 72, dryWinterRoom, humidifier, 200 * HOUR);
        assertTrue(Psychrometrics.relativeHumidity(72, settled) < 95, "stops short of saturation: " + settled);
        assertTrue(Psychrometrics.relativeHumidity(72, settled) > 45, "but makes a dry room comfortable");
    }

    @Test
    void aHumidifierAndADehumidifierInOneRoomPullAgainstEachOther() {
        double alone = step(50, 72, 50, new HumidityModel.Loads(0, 0, Double.NaN, 0, 1.0), 20 * HOUR);
        double both = step(50, 72, 50, new HumidityModel.Loads(0, 0, Double.NaN, 1.0, 1.0), 20 * HOUR);
        assertTrue(both < alone);
    }

    @Test
    void splittingAnIntervalGivesTheSameResult() {
        HumidityModel.Loads loads = new HumidityModel.Loads(0.7, 0.02, 80, 0.5);
        double whole = step(58, 74, 61, loads, 400);
        double split = 58;
        for (int i = 0; i < 20; i++) split = step(split, 74, 61, loads, 20);
        assertEquals(whole, split, 0.01);
    }
}
