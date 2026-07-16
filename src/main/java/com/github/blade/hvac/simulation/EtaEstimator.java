package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.OperatingProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** Forward estimate using the same RPM, capacity, and thermal rules as the controller. */
public final class EtaEstimator {
    private static final long STEP_TICKS = 20L;
    private static final long MAXIMUM_ETA_TICKS = 36_000L; // 30 real-time minutes
    private static final double EPSILON = 1.0e-9;

    public record UnitSnapshot(EquipmentType type, double rpm) {
        public UnitSnapshot {
            Objects.requireNonNull(type, "type");
            if (!Double.isFinite(rpm) || rpm < 0.0)
                throw new IllegalArgumentException("RPM must be finite and non-negative");
        }
    }

    private EtaEstimator() {}

    public static OptionalLong estimateTicks(double roomF, double targetF, double outdoorF,
                                             OperatingMode mode, List<UnitSnapshot> units,
                                             HvacSettings settings) {
        return estimateTicks(roomF, targetF, outdoorF, mode, units,
                OperatingProfile.NORMAL, settings);
    }

    public static OptionalLong estimateTicks(double roomF, double targetF, double outdoorF,
                                             OperatingMode mode, List<UnitSnapshot> units,
                                             OperatingProfile profile, HvacSettings settings) {
        if (!Double.isFinite(roomF) || !Double.isFinite(targetF) || !Double.isFinite(outdoorF))
            throw new IllegalArgumentException("ETA temperatures must be finite");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(settings, "settings");
        if (mode == OperatingMode.IDLE) return OptionalLong.empty();
        if (reached(roomF, targetF, mode)) return OptionalLong.of(0L);

        int fixedUnits = 0;
        List<Double> variableRpms = new ArrayList<>();
        for (UnitSnapshot unit : units) {
            if (!unit.type().supports(mode)) continue;
            if (unit.type() == EquipmentType.FURNACE) fixedUnits++;
            else variableRpms.add(unit.rpm());
        }
        if (fixedUnits == 0 && variableRpms.isEmpty()) return OptionalLong.empty();

        double room = roomF;
        for (long elapsed = STEP_TICKS; elapsed <= MAXIMUM_ETA_TICKS; elapsed += STEP_TICKS) {
            double requested = PerformanceModel.requestedVariableCapacity(
                    Math.abs(room - targetF), settings);
            double targetRpm = PerformanceModel.variableTargetRpm(requested, profile, settings);
            double variableEquivalent = 0.0;
            for (int index = 0; index < variableRpms.size(); index++) {
                double rpm = PerformanceModel.approach(variableRpms.get(index), targetRpm,
                        STEP_TICKS / 20.0, settings.variableRampUpRpmPerSecond(),
                        settings.variableRampDownRpmPerSecond());
                variableRpms.set(index, rpm);
                variableEquivalent += PerformanceModel.variableCapacityFromRpm(rpm, settings);
            }
            double capacity = ThermalModel.aggregateIndependentCapacity(
                    fixedUnits, variableEquivalent, settings.maximumGroupCapacity());
            room = ThermalModel.step(room, targetF, outdoorF, mode, capacity,
                    STEP_TICKS, settings).temperatureF();
            if (reached(room, targetF, mode)) return OptionalLong.of(elapsed);
        }
        return OptionalLong.empty();
    }

    private static boolean reached(double roomF, double targetF, OperatingMode mode) {
        return mode == OperatingMode.COOLING ? roomF <= targetF + EPSILON
                : roomF >= targetF - EPSILON;
    }
}
