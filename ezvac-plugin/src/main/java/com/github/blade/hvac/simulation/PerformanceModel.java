package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.OperatingProfile;
import com.github.blade.hvac.model.Tier;

/** Pure compressor/blower capacity and ramp calculations. */
public final class PerformanceModel {
    public static final double FURNACE_MAXIMUM_RPM = 1_100.0;
    public static final double FURNACE_RAMP_UP_PER_SECOND = 1_000.0;
    public static final double FURNACE_RAMP_DOWN_PER_SECOND = 1_300.0;

    private PerformanceModel() {}

    public static double requestedVariableCapacity(double absoluteErrorF, HvacSettings settings) {
        finite("temperature error", absoluteErrorF);
        if (settings == null) throw new IllegalArgumentException("settings are required");
        double error = Math.max(0.0, absoluteErrorF);
        return clamp(settings.variableMinimumCapacity()
                + (1.0 - settings.variableMinimumCapacity())
                * error / settings.variableApproachBandF(),
                settings.variableMinimumCapacity(), 1.0);
    }

    public static double variableTargetRpm(double capacity, HvacSettings settings) {
        return variableTargetRpm(capacity, OperatingProfile.NORMAL, settings);
    }

    public static double variableTargetRpm(double capacity, OperatingProfile profile,
                                           HvacSettings settings) {
        finite("capacity", capacity);
        if (profile == null) throw new IllegalArgumentException("operating profile is required");
        if (settings == null) throw new IllegalArgumentException("settings are required");
        double normalized = (clamp(capacity, settings.variableMinimumCapacity(), 1.0)
                - settings.variableMinimumCapacity()) / (1.0 - settings.variableMinimumCapacity());
        double maximumRpm = switch (profile) {
            case ECO -> settings.variableEcoMaximumRpm();
            case NORMAL -> settings.variableMaximumRpm();
            case TURBO -> settings.variableTurboMaximumRpm();
        };
        return settings.variableMinimumRpm()
                + normalized * (maximumRpm - settings.variableMinimumRpm());
    }

    public static double variableCapacityFromRpm(double rpm, HvacSettings settings) {
        finite("rpm", rpm);
        if (settings == null) throw new IllegalArgumentException("settings are required");
        if (rpm <= 0.0) return 0.0;
        if (rpm < settings.variableMinimumRpm())
            return settings.variableMinimumCapacity() * rpm / settings.variableMinimumRpm();
        double normalized = (rpm - settings.variableMinimumRpm())
                / (settings.variableMaximumRpm() - settings.variableMinimumRpm());
        double maximumCapacity = settings.variableMinimumCapacity()
                + (settings.variableTurboMaximumRpm() - settings.variableMinimumRpm())
                / (settings.variableMaximumRpm() - settings.variableMinimumRpm())
                * (1.0 - settings.variableMinimumCapacity());
        return clamp(settings.variableMinimumCapacity()
                + normalized * (1.0 - settings.variableMinimumCapacity()), 0.0, maximumCapacity);
    }

    /**
     * What a unit of a tier is asked for, as a share of flat out (3.1). A
     * single-stage unit only runs flat out; a two-stage one drops to its low
     * stage within the approach band of the target; a variable one eases down
     * on the variable-speed curve, as every compressor always did.
     */
    public static double stagedRequest(Tier tier, double absoluteErrorF, HvacSettings settings) {
        if (tier == null || settings == null) throw new IllegalArgumentException("tier and settings are required");
        return switch (tier) {
            case SINGLE_STAGE -> 1.0;
            case TWO_STAGE -> Math.abs(absoluteErrorF) <= settings.variableApproachBandF() ? settings.twoStageLow() : 1.0;
            case VARIABLE -> requestedVariableCapacity(absoluteErrorF, settings);
        };
    }

    /**
     * What a unit delivers flat out: a furnace or boiler its full heat, a
     * compressor at its operating profile's top speed, each as its tier allows.
     */
    public static double fullCapacity(EquipmentType type, Tier tier, OperatingProfile profile, HvacSettings settings) {
        if (type == null || tier == null || profile == null || settings == null)
            throw new IllegalArgumentException("type, tier, profile and settings are required");
        if (type.fixedSpeed()) return settings.tierCapacity(tier);
        return settings.tierCapacity(tier) * variableCapacityFromRpm(variableTargetRpm(1.0, profile, settings), settings);
    }

    /**
     * How hard a system is working, 0 to 1: what it delivers now as a share of
     * what its units could deliver flat out, both after the multi-unit
     * diminishing returns. 1 means everything that can run is at full speed,
     * whatever the number or tiers of its units.
     */
    public static double load(double deliveredFixed, double deliveredVariable, double fullFixed, double fullVariable,
                              HvacSettings settings) {
        double full = ThermalModel.aggregateIndependentCapacity(fullFixed, fullVariable, settings.maximumSystemCapacity());
        if (full <= 0.0) return 0.0;
        double delivered = ThermalModel.aggregateIndependentCapacity(deliveredFixed, deliveredVariable,
                settings.maximumSystemCapacity());
        return clamp(delivered / full, 0.0, 1.0);
    }

    public static double approach(double current, double target, double elapsedSeconds,
                                  double upPerSecond, double downPerSecond) {
        finite("current rpm", current);
        finite("target rpm", target);
        finite("elapsed seconds", elapsedSeconds);
        finite("ramp up", upPerSecond);
        finite("ramp down", downPerSecond);
        if (elapsedSeconds < 0.0 || upPerSecond <= 0.0 || downPerSecond <= 0.0)
            throw new IllegalArgumentException("RPM ramp inputs are invalid");
        double difference = target - current;
        double limit = (difference >= 0.0 ? upPerSecond : downPerSecond) * elapsedSeconds;
        if (Math.abs(difference) <= limit) return Math.max(0.0, target);
        return Math.max(0.0, current + Math.copySign(limit, difference));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void finite(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
