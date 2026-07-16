package com.github.blade.hvac.config;

import org.bukkit.configuration.file.FileConfiguration;

/** Immutable, validated runtime tuning. */
public record HvacSettings(
        long controllerPeriodTicks,
        long savePeriodTicks,
        long displayPeriodTicks,
        double coolingStartAboveF,
        double heatingStartBelowF,
        double coolingStopBelowF,
        double heatingStopAboveF,
        long minimumRunTicks,
        long minimumOffTicks,
        double minimumTargetF,
        double maximumTargetF,
        double coolingRateFPerHour,
        double heatingRateFPerHour,
        double idleDriftFPerHour,
        double activeLeakFPerHour,
        double maximumGroupCapacity,
        double variableApproachBandF,
        double variableMinimumCapacity,
        double variableMinimumRpm,
        double variableMaximumRpm,
        double variableEcoMaximumRpm,
        double variableTurboMaximumRpm,
        double variableRampUpRpmPerSecond,
        double variableRampDownRpmPerSecond,
        int airflowCoreRange,
        int airflowMaximumRange,
        int airflowNodesPerTick,
        int airflowMaximumCells,
        boolean audioEnabled,
        double audioRadiusBlocks,
        long humRefreshTicks,
        float humVolume,
        float humPitch
) {
    public HvacSettings {
        requirePositive("controller.period-ticks", controllerPeriodTicks);
        requirePositive("controller.save-period-ticks", savePeriodTicks);
        requirePositive("controller.display-period-ticks", displayPeriodTicks);
        requireNonNegative("controller.cooling-start-above-f", coolingStartAboveF);
        requireNonNegative("controller.heating-start-below-f", heatingStartBelowF);
        requireNonNegative("controller.cooling-stop-below-f", coolingStopBelowF);
        requireNonNegative("controller.heating-stop-above-f", heatingStopAboveF);
        requireNonNegative("controller.minimum-run-ticks", minimumRunTicks);
        requireNonNegative("controller.minimum-off-ticks", minimumOffTicks);
        if (!Double.isFinite(minimumTargetF) || !Double.isFinite(maximumTargetF)
                || minimumTargetF >= maximumTargetF) {
            throw new IllegalArgumentException("temperature target bounds are invalid");
        }
        requirePositiveFinite("temperature.cooling-f-per-minecraft-hour", coolingRateFPerHour);
        requirePositiveFinite("temperature.heating-f-per-minecraft-hour", heatingRateFPerHour);
        requireNonNegative("temperature.idle-drift-f-per-minecraft-hour", idleDriftFPerHour);
        requireNonNegative("temperature.active-leak-f-per-minecraft-hour", activeLeakFPerHour);
        requirePositiveFinite("temperature.maximum-group-capacity", maximumGroupCapacity);
        requirePositiveFinite("performance.variable-speed.approach-band-f", variableApproachBandF);
        if (!Double.isFinite(variableMinimumCapacity) || variableMinimumCapacity <= 0.0
                || variableMinimumCapacity >= 1.0) {
            throw new IllegalArgumentException(
                    "performance.variable-speed.minimum-capacity must be between 0 and 1");
        }
        requirePositiveFinite("performance.variable-speed.minimum-rpm", variableMinimumRpm);
        requirePositiveFinite("performance.variable-speed.maximum-rpm", variableMaximumRpm);
        if (variableMaximumRpm <= variableMinimumRpm) {
            throw new IllegalArgumentException(
                    "performance.variable-speed.maximum-rpm must exceed minimum-rpm");
        }
        requirePositiveFinite("performance.variable-speed.eco-maximum-rpm",
                variableEcoMaximumRpm);
        requirePositiveFinite("performance.variable-speed.turbo-maximum-rpm",
                variableTurboMaximumRpm);
        if (variableEcoMaximumRpm <= variableMinimumRpm
                || variableEcoMaximumRpm >= variableMaximumRpm) {
            throw new IllegalArgumentException(
                    "eco-maximum-rpm must be between minimum-rpm and normal maximum-rpm");
        }
        if (variableTurboMaximumRpm <= variableMaximumRpm) {
            throw new IllegalArgumentException(
                    "turbo-maximum-rpm must exceed normal maximum-rpm");
        }
        requirePositiveFinite("performance.variable-speed.ramp-up-rpm-per-second",
                variableRampUpRpmPerSecond);
        requirePositiveFinite("performance.variable-speed.ramp-down-rpm-per-second",
                variableRampDownRpmPerSecond);
        if (airflowCoreRange < 0 || airflowMaximumRange <= airflowCoreRange) {
            throw new IllegalArgumentException("airflow ranges are invalid");
        }
        if (airflowMaximumRange > 127) {
            throw new IllegalArgumentException("airflow.maximum-range must be at most 127");
        }
        if (airflowNodesPerTick <= 0 || airflowMaximumCells <= 0) {
            throw new IllegalArgumentException("airflow budgets must be positive");
        }
        requirePositiveFinite("audio.radius-blocks", audioRadiusBlocks);
        requirePositive("audio.hum-refresh-ticks", humRefreshTicks);
        requirePositiveFinite("audio.hum-volume", humVolume);
        requirePositiveFinite("audio.hum-pitch", humPitch);
    }

    public static HvacSettings load(FileConfiguration config) {
        HvacSettings defaults = defaults();
        return new HvacSettings(
                config.getLong("controller.period-ticks", defaults.controllerPeriodTicks),
                config.getLong("controller.save-period-ticks", defaults.savePeriodTicks),
                config.getLong("controller.display-period-ticks", defaults.displayPeriodTicks),
                config.getDouble("controller.cooling-start-above-f", defaults.coolingStartAboveF),
                config.getDouble("controller.heating-start-below-f", defaults.heatingStartBelowF),
                config.getDouble("controller.cooling-stop-below-f", defaults.coolingStopBelowF),
                config.getDouble("controller.heating-stop-above-f", defaults.heatingStopAboveF),
                config.getLong("controller.minimum-run-ticks", defaults.minimumRunTicks),
                config.getLong("controller.minimum-off-ticks", defaults.minimumOffTicks),
                config.getDouble("temperature.minimum-target-f", defaults.minimumTargetF),
                config.getDouble("temperature.maximum-target-f", defaults.maximumTargetF),
                config.getDouble("temperature.cooling-f-per-minecraft-hour", defaults.coolingRateFPerHour),
                config.getDouble("temperature.heating-f-per-minecraft-hour", defaults.heatingRateFPerHour),
                config.getDouble("temperature.idle-drift-f-per-minecraft-hour", defaults.idleDriftFPerHour),
                config.getDouble("temperature.active-leak-f-per-minecraft-hour", defaults.activeLeakFPerHour),
                config.getDouble("temperature.maximum-group-capacity", defaults.maximumGroupCapacity),
                config.getDouble("performance.variable-speed.approach-band-f", defaults.variableApproachBandF),
                config.getDouble("performance.variable-speed.minimum-capacity", defaults.variableMinimumCapacity),
                config.getDouble("performance.variable-speed.minimum-rpm", defaults.variableMinimumRpm),
                config.getDouble("performance.variable-speed.maximum-rpm", defaults.variableMaximumRpm),
                config.getDouble("performance.variable-speed.eco-maximum-rpm",
                        defaults.variableEcoMaximumRpm),
                config.getDouble("performance.variable-speed.turbo-maximum-rpm",
                        defaults.variableTurboMaximumRpm),
                config.getDouble("performance.variable-speed.ramp-up-rpm-per-second",
                        defaults.variableRampUpRpmPerSecond),
                config.getDouble("performance.variable-speed.ramp-down-rpm-per-second",
                        defaults.variableRampDownRpmPerSecond),
                config.getInt("airflow.core-range", defaults.airflowCoreRange),
                config.getInt("airflow.maximum-range", defaults.airflowMaximumRange),
                config.getInt("airflow.nodes-per-tick", defaults.airflowNodesPerTick),
                config.getInt("airflow.maximum-cells-per-system", defaults.airflowMaximumCells),
                config.getBoolean("audio.enabled", defaults.audioEnabled),
                config.getDouble("audio.radius-blocks", defaults.audioRadiusBlocks),
                config.getLong("audio.hum-refresh-ticks", defaults.humRefreshTicks),
                (float) config.getDouble("audio.hum-volume", defaults.humVolume),
                (float) config.getDouble("audio.hum-pitch", defaults.humPitch)
        );
    }

    public static HvacSettings defaults() {
        return new HvacSettings(20, 600, 60,
                1.0, 1.0, 0.3, 0.3, 300, 200,
                60.0, 85.0, 3.75, 5.0, 1.25, 0.50, 2.0,
                3.0, 0.40, 1_200.0, 3_600.0, 2_400.0, 4_200.0, 900.0, 1_200.0,
                15, 30, 4_000, 200_000,
                true, 25.0, 60, 1.6f, 0.55f);
    }

    public double clampTarget(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("target temperature must be finite");
        return Math.max(minimumTargetF, Math.min(maximumTargetF, value));
    }

    private static void requirePositive(String name, long value) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    private static void requireNonNegative(String name, long value) {
        if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0)
            throw new IllegalArgumentException(name + " must be non-negative and finite");
    }

    private static void requirePositiveFinite(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0)
            throw new IllegalArgumentException(name + " must be positive and finite");
    }
}
