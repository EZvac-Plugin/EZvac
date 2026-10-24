package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.OperatingProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EtaEstimatorTest {
    private final HvacSettings settings = HvacSettings.defaults();

    @Test
    void estimatesCoolingWithCompressorRampAndApproachModulation() {
        var eta = EtaEstimator.estimateTicks(78.0, 72.0, 85.0, OperatingMode.COOLING,
                List.of(new EtaEstimator.UnitSnapshot(EquipmentType.HEAT_PUMP, 0.0)), settings);
        assertTrue(eta.isPresent());
        assertTrue(eta.getAsLong() > settings.minimumRunTicks());
    }

    @Test
    void mixedHeatingKeepsFurnaceWholeAndHeatPumpAddsCapacity() {
        var furnaceOnly = EtaEstimator.estimateTicks(64.0, 72.0, 30.0, OperatingMode.HEATING,
                List.of(new EtaEstimator.UnitSnapshot(EquipmentType.FURNACE, 0.0)), settings);
        var mixed = EtaEstimator.estimateTicks(64.0, 72.0, 30.0, OperatingMode.HEATING,
                List.of(new EtaEstimator.UnitSnapshot(EquipmentType.FURNACE, 0.0),
                        new EtaEstimator.UnitSnapshot(EquipmentType.HEAT_PUMP, 0.0)), settings);
        assertTrue(furnaceOnly.isPresent());
        assertTrue(mixed.isPresent());
        assertTrue(mixed.getAsLong() < furnaceOnly.getAsLong());
    }

    @Test
    void incompatibleOrIdleEquipmentDoesNotProduceFakeEta() {
        assertTrue(EtaEstimator.estimateTicks(65.0, 72.0, 30.0, OperatingMode.HEATING,
                List.of(new EtaEstimator.UnitSnapshot(EquipmentType.AIR_CONDITIONER, 3_600.0)),
                settings).isEmpty());
        assertTrue(EtaEstimator.estimateTicks(75.0, 72.0, 85.0, OperatingMode.IDLE,
                List.of(new EtaEstimator.UnitSnapshot(EquipmentType.HEAT_PUMP, 3_600.0)),
                settings).isEmpty());
    }

    @Test
    void unreachableTargetReturnsNoEtaInsteadOfOverflowing() {
        assertTrue(EtaEstimator.estimateTicks(-90.0, 190.0, -100.0, OperatingMode.HEATING,
                List.of(new EtaEstimator.UnitSnapshot(EquipmentType.FURNACE, 1_100.0)),
                settings).isEmpty());
    }

    @Test
    void profileEtaOrdersEcoNormalAndTurboForVariableEquipment() {
        List<EtaEstimator.UnitSnapshot> units = List.of(
                new EtaEstimator.UnitSnapshot(EquipmentType.HEAT_PUMP, 0.0));
        long eco = EtaEstimator.estimateTicks(78.0, 72.0, 85.0, OperatingMode.COOLING,
                units, OperatingProfile.ECO, settings).orElseThrow();
        long normal = EtaEstimator.estimateTicks(78.0, 72.0, 85.0, OperatingMode.COOLING,
                units, OperatingProfile.NORMAL, settings).orElseThrow();
        long turbo = EtaEstimator.estimateTicks(78.0, 72.0, 85.0, OperatingMode.COOLING,
                units, OperatingProfile.TURBO, settings).orElseThrow();
        assertTrue(eco > normal);
        assertTrue(normal > turbo);
    }

    @Test
    void furnaceEtaIsIdenticalAcrossProfiles() {
        List<EtaEstimator.UnitSnapshot> furnace = List.of(
                new EtaEstimator.UnitSnapshot(EquipmentType.FURNACE, 0.0));
        long eco = EtaEstimator.estimateTicks(64.0, 72.0, 30.0, OperatingMode.HEATING,
                furnace, OperatingProfile.ECO, settings).orElseThrow();
        long turbo = EtaEstimator.estimateTicks(64.0, 72.0, 30.0, OperatingMode.HEATING,
                furnace, OperatingProfile.TURBO, settings).orElseThrow();
        assertEquals(eco, turbo);
    }
}
