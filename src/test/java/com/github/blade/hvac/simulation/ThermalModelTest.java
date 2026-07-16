package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.OperatingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThermalModelTest {
    private static final double EPSILON = 1.0e-9;
    private final HvacSettings settings = HvacSettings.defaults();

    @Test
    void deliversConfiguredCoolingAndHeatingWithActiveLeak() {
        var cooling = ThermalModel.step(80, 60, 80, OperatingMode.COOLING, 1, 1_000, settings);
        var heating = ThermalModel.step(60, 80, 60, OperatingMode.HEATING, 1, 1_000, settings);
        assertEquals(76.75, cooling.temperatureF(), EPSILON);
        assertEquals(-3.75, cooling.equipmentEffectF(), EPSILON);
        assertEquals(0.5, cooling.outdoorEffectF(), EPSILON);
        assertEquals(64.5, heating.temperatureF(), EPSILON);
        assertEquals(5.0, heating.equipmentEffectF(), EPSILON);
        assertEquals(-0.5, heating.outdoorEffectF(), EPSILON);
    }

    @Test
    void integrationIsIndependentOfTickPartitioning() {
        double combined = ThermalModel.step(90, 69.7, 110, OperatingMode.COOLING,
                0.8, 1_000, settings).temperatureF();
        double split = 90;
        for (int index = 0; index < 50; index++)
            split = ThermalModel.step(split, 69.7, 110, OperatingMode.COOLING,
                    0.8, 20, settings).temperatureF();
        assertEquals(combined, split, EPSILON);
    }

    @Test
    void controlBoundaryIsNotClampedToUserSetpointRange() {
        double cooling = ThermalModel.step(61, 59.7, 90, OperatingMode.COOLING,
                1, 10_000, settings).temperatureF();
        double heating = ThermalModel.step(84, 85.3, 30, OperatingMode.HEATING,
                1, 10_000, settings).temperatureF();
        assertEquals(59.7, cooling, EPSILON);
        assertEquals(85.3, heating, EPSILON);
    }

    @Test
    void activeEquipmentHoldsBoundaryAgainstAdverseOutdoorLoad() {
        assertEquals(69.7, ThermalModel.step(69.7, 69.7, 100,
                OperatingMode.COOLING, 0.4, 500, settings).temperatureF(), EPSILON);
        assertEquals(70.3, ThermalModel.step(70.3, 70.3, 20,
                OperatingMode.HEATING, 0.4, 500, settings).temperatureF(), EPSILON);
    }

    @Test
    void subMinimumStartupCapacityCannotMagicallyCancelLargerHeatLoad() {
        double next = ThermalModel.step(70, 70, 100, OperatingMode.COOLING,
                0.01, 100, settings).temperatureF();
        assertTrue(next > 70.0);
    }

    @Test
    void weakStartupAtOutdoorEquilibriumDoesNotChatterOrDiverge() {
        var result = ThermalModel.step(80, 70, 80, OperatingMode.COOLING,
                0.01, 1_000, settings);
        assertEquals(80.0, result.temperatureF(), EPSILON);
        assertEquals(0.0, result.outdoorEffectF() + result.equipmentEffectF(), EPSILON);
    }

    @Test
    void idleDriftStopsAtOutdoorEquilibrium() {
        assertEquals(72.5, ThermalModel.step(70, 70, 80, OperatingMode.IDLE,
                0, 2_000, settings).temperatureF(), EPSILON);
        assertEquals(80.0, ThermalModel.step(70, 70, 80, OperatingMode.IDLE,
                0, 20_000, settings).temperatureF(), EPSILON);
    }

    @Test
    void aggregateCapacityHasDiminishingReturnsAndCap() {
        assertEquals(0.4, ThermalModel.aggregateCapacity(0.4, 2), EPSILON);
        assertEquals(Math.sqrt(2), ThermalModel.aggregateCapacity(2, 2), EPSILON);
        assertEquals(2, ThermalModel.aggregateCapacity(100, 2), EPSILON);
    }

    @Test
    void fixedAndVariableEquipmentAreAddedBeforeDiminishingReturns() {
        assertEquals(1.0, ThermalModel.aggregateIndependentCapacity(1.0, 0.0, 2.0), EPSILON);
        assertEquals(Math.sqrt(1.4),
                ThermalModel.aggregateIndependentCapacity(1.0, 0.4, 2.0), EPSILON);
        assertEquals(Math.sqrt(1.2),
                ThermalModel.aggregateIndependentCapacity(0.0, 1.2, 2.0), EPSILON);
        assertThrows(IllegalArgumentException.class,
                () -> ThermalModel.aggregateIndependentCapacity(1.0, -0.1, 2.0));
    }

    @Test
    void outdoorProfileTracksNoonMidnightAndWraparound() {
        assertEquals(85, ThermalModel.outdoorTemperature(6_000, 85, 65), EPSILON);
        assertEquals(65, ThermalModel.outdoorTemperature(18_000, 85, 65), EPSILON);
        assertEquals(85, ThermalModel.outdoorTemperature(30_000, 85, 65), EPSILON);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> ThermalModel.step(Double.NaN,
                70, 80, OperatingMode.IDLE, 0, 20, settings));
        assertThrows(IllegalArgumentException.class, () -> ThermalModel.step(70,
                70, 80, OperatingMode.IDLE, -1, 20, settings));
        assertThrows(IllegalArgumentException.class, () -> ThermalModel.step(70,
                70, 80, OperatingMode.IDLE, 0, -1, settings));
    }
}
