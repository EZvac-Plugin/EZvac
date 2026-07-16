package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.OperatingProfile;

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
