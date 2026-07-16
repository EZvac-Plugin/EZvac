package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.EquipmentUnit;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.OperatingProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceModelTest {
    private static final double EPSILON = 1.0e-9;
    private final HvacSettings settings = HvacSettings.defaults();

    @Test
    void variableCapacityMapsFortyToOneHundredPercent() {
        assertEquals(0.4, PerformanceModel.requestedVariableCapacity(0, settings), EPSILON);
        assertEquals(0.6, PerformanceModel.requestedVariableCapacity(1, settings), EPSILON);
        assertEquals(0.8, PerformanceModel.requestedVariableCapacity(2, settings), EPSILON);
        assertEquals(1.0, PerformanceModel.requestedVariableCapacity(3, settings), EPSILON);
        assertEquals(1.0, PerformanceModel.requestedVariableCapacity(30, settings), EPSILON);
    }

    @Test
    void rpmCapacityRoundTripIsStable() {
        for (double capacity : new double[]{0.4, 0.6, 0.8, 1.0})
            assertEquals(capacity, PerformanceModel.variableCapacityFromRpm(
                    PerformanceModel.variableTargetRpm(capacity, settings), settings), EPSILON);
    }

    @Test
    void lowerRpmActuallyReducesTheThermalRateInsideApproachBand() {
        double nearRequested = PerformanceModel.requestedVariableCapacity(1.0, settings);
        double farRequested = PerformanceModel.requestedVariableCapacity(3.0, settings);
        double nearRpm = PerformanceModel.variableTargetRpm(nearRequested, settings);
        double farRpm = PerformanceModel.variableTargetRpm(farRequested, settings);
        double nearDelivered = PerformanceModel.variableCapacityFromRpm(nearRpm, settings);
        double farDelivered = PerformanceModel.variableCapacityFromRpm(farRpm, settings);

        var near = ThermalModel.step(75, 60, 75, OperatingMode.COOLING,
                nearDelivered, 100, settings);
        var far = ThermalModel.step(75, 60, 75, OperatingMode.COOLING,
                farDelivered, 100, settings);

        assertEquals(2_000.0, nearRpm, EPSILON);
        assertEquals(3_600.0, farRpm, EPSILON);
        assertEquals(0.60, nearDelivered, EPSILON);
        assertEquals(1.0, farDelivered, EPSILON);
        assertTrue(Math.abs(near.equipmentEffectF()) < Math.abs(far.equipmentEffectF()));
    }

    @Test
    void variableUnitsSynchronizeWhileFixedFurnaceCapacityStaysWhole() {
        double requested = PerformanceModel.requestedVariableCapacity(1.0, settings);
        double synchronizedRpm = PerformanceModel.variableTargetRpm(requested, settings);
        EquipmentUnit heatPump = runningUnit(EquipmentType.HEAT_PUMP, synchronizedRpm);
        EquipmentUnit airConditioner = runningUnit(EquipmentType.AIR_CONDITIONER, synchronizedRpm);
        EquipmentUnit furnace = runningUnit(EquipmentType.FURNACE, 200.0);

        assertEquals(0.60, heatPump.operatingCapacity(settings), EPSILON);
        assertEquals(heatPump.operatingCapacity(settings),
                airConditioner.operatingCapacity(settings), EPSILON);
        assertEquals(1.0, furnace.operatingCapacity(settings), EPSILON);

        double mixedHeating = ThermalModel.aggregateIndependentCapacity(
                furnace.operatingCapacity(settings), heatPump.operatingCapacity(settings), 2.0);
        double synchronizedCooling = ThermalModel.aggregateIndependentCapacity(0.0,
                heatPump.operatingCapacity(settings) + airConditioner.operatingCapacity(settings), 2.0);
        assertEquals(Math.sqrt(1.60), mixedHeating, EPSILON);
        assertEquals(Math.sqrt(1.20), synchronizedCooling, EPSILON);
        assertTrue(mixedHeating > 1.0, "the slowed heat pump must add to, not average down, the furnace");
    }

    @Test
    void operatingProfilesChangeOnlyTheVariableSpeedCap() {
        assertEquals(2_400.0, PerformanceModel.variableTargetRpm(
                1.0, OperatingProfile.ECO, settings), EPSILON);
        assertEquals(3_600.0, PerformanceModel.variableTargetRpm(
                1.0, OperatingProfile.NORMAL, settings), EPSILON);
        assertEquals(4_200.0, PerformanceModel.variableTargetRpm(
                1.0, OperatingProfile.TURBO, settings), EPSILON);
        assertEquals(PerformanceModel.variableTargetRpm(0.73, settings),
                PerformanceModel.variableTargetRpm(0.73, OperatingProfile.NORMAL, settings), EPSILON);

        assertEquals(0.70, PerformanceModel.variableCapacityFromRpm(2_400.0, settings), EPSILON);
        assertEquals(1.00, PerformanceModel.variableCapacityFromRpm(3_600.0, settings), EPSILON);
        assertEquals(1.15, PerformanceModel.variableCapacityFromRpm(4_200.0, settings), EPSILON);

        EquipmentUnit furnace = runningUnit(EquipmentType.FURNACE, 0.0);
        furnace.advanceMotor(0.1, 200, settings, OperatingProfile.ECO);
        assertEquals(1_100.0, furnace.motorRpm(), EPSILON);
        assertEquals(1.0, furnace.operatingCapacity(settings), EPSILON);

        for (OperatingProfile profile : OperatingProfile.values()) {
            EquipmentUnit heatPump = runningUnit(EquipmentType.HEAT_PUMP, 0.0);
            EquipmentUnit airConditioner = runningUnit(EquipmentType.AIR_CONDITIONER, 0.0);
            heatPump.advanceMotor(1.0, 200, settings, profile);
            airConditioner.advanceMotor(1.0, 200, settings, profile);
            assertEquals(heatPump.motorRpm(), airConditioner.motorRpm(), EPSILON);
        }
    }

    @Test
    void reducedHeatPumpNeverBogsDownFixedFurnaceContribution() {
        double ecoHeatPump = PerformanceModel.variableCapacityFromRpm(
                PerformanceModel.variableTargetRpm(1.0, OperatingProfile.ECO, settings), settings);
        double normalHeatPump = PerformanceModel.variableCapacityFromRpm(
                PerformanceModel.variableTargetRpm(1.0, OperatingProfile.NORMAL, settings), settings);
        double ecoMixed = ThermalModel.aggregateIndependentCapacity(1.0, ecoHeatPump, 2.0);
        double normalMixed = ThermalModel.aggregateIndependentCapacity(1.0, normalHeatPump, 2.0);
        assertEquals(Math.sqrt(1.70), ecoMixed, EPSILON);
        assertEquals(Math.sqrt(2.0), normalMixed, EPSILON);
        assertTrue(ecoMixed > 1.0);
        assertTrue(normalMixed > ecoMixed);
    }

    @Test
    void rampsNeverOvershootAndSpinDownFaster() {
        assertEquals(900, PerformanceModel.approach(0, 3600, 1, 900, 1200), EPSILON);
        assertEquals(3600, PerformanceModel.approach(3500, 3600, 1, 900, 1200), EPSILON);
        assertEquals(1200, PerformanceModel.approach(2400, 0, 1, 900, 1200), EPSILON);
        assertEquals(0, PerformanceModel.approach(500, 0, 1, 900, 1200), EPSILON);
    }

    @Test
    void validatesNonFiniteInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> PerformanceModel.requestedVariableCapacity(Double.NaN, settings));
        assertThrows(IllegalArgumentException.class,
                () -> PerformanceModel.approach(0, 1, -1, 1, 1));
    }

    private static EquipmentUnit runningUnit(EquipmentType type, double rpm) {
        BlockKey position = new BlockKey(UUID.randomUUID(), 1, 64, 1);
        Map<String, Object> values = new LinkedHashMap<>();
        position.write(values, "");
        values.put("group", "test");
        values.put("equipmentType", type.name());
        values.put("enabled", true);
        values.put("running", true);
        values.put("operatingMode", type == EquipmentType.AIR_CONDITIONER
                ? OperatingMode.COOLING.name() : OperatingMode.HEATING.name());
        values.put("motorRpm", rpm);
        return EquipmentUnit.fromMap(values);
    }
}
