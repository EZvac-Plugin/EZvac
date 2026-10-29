package com.github.blade.hvac.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.function.Consumer;

/**
 * Size-based heating and cooling (BETA 1.0), read from {@code sizing} in
 * config.yml. A unit heats or cools the air its system reaches: one
 * variable-speed unit is sized for {@code cellsPerUnit} blocks of air, 5,000
 * sq ft of floor over 4-block-tall levels.
 *
 * @param enabled           false: the behaviour from before BETA 1.0, whatever a system's size
 * @param cellsPerUnit      blocks of air one variable-speed unit heats or cools at the normal pace
 * @param poolCellsPerUnit  blocks of water one variable-speed pool heater warms at the normal pace
 * @param maximumSpeedUp    the most a system smaller than that is sped up by
 * @param outdoorReach      blocks the air reaches past a roof's edge, into open sky
 * @param openingLeak       extra leak per opening block (a doorway, window or hole) per 1,000 blocks of air
 * @param maximumOpenness   the most openings can multiply a sealed room's leak by
 * @param neighbourExchange how fast systems whose air touches even out, per touching face per block of air
 */
public record SizingSettings(boolean enabled, int cellsPerUnit, int poolCellsPerUnit, double maximumSpeedUp,
                             int outdoorReach, double openingLeak, double maximumOpenness,
                             double neighbourExchange) {
    public SizingSettings {
        if (cellsPerUnit < 1) throw new IllegalArgumentException("sizing.cells-per-unit must be positive");
        if (poolCellsPerUnit < 1) throw new IllegalArgumentException("sizing.pool-cells-per-unit must be positive");
        if (!Double.isFinite(maximumSpeedUp) || maximumSpeedUp < 1.0 || maximumSpeedUp > 10.0)
            throw new IllegalArgumentException("sizing.maximum-speed-up must be 1-10");
        if (outdoorReach < 0 || outdoorReach > 30) throw new IllegalArgumentException("sizing.outdoor-reach must be 0-30");
        if (!Double.isFinite(openingLeak) || openingLeak < 0.0)
            throw new IllegalArgumentException("sizing.opening-leak must not be negative");
        if (!Double.isFinite(maximumOpenness) || maximumOpenness < 1.0 || maximumOpenness > 50.0)
            throw new IllegalArgumentException("sizing.maximum-openness must be 1-50");
        if (!Double.isFinite(neighbourExchange) || neighbourExchange < 0.0)
            throw new IllegalArgumentException("sizing.neighbour-exchange must not be negative");
    }

    /** On a running server, what /hvac quicksettings says; config.yml's value otherwise. */
    @Override
    public boolean enabled() { return ServerSwitches.sizing(enabled); }

    public static SizingSettings defaults() {
        return new SizingSettings(true, 1860, 1500, 2.0, 3, 1.1, 6.0, 10.0);
    }

    /** Reads {@code sizing}; a value out of range keeps the defaults, with a warning. */
    public static SizingSettings load(FileConfiguration config, Consumer<String> warn) {
        SizingSettings d = defaults();
        String root = "sizing.";
        boolean enabled = config.getBoolean(root + "enabled", d.enabled);
        try {
            return new SizingSettings(enabled,
                    config.getInt(root + "cells-per-unit", d.cellsPerUnit),
                    config.getInt(root + "pool-cells-per-unit", d.poolCellsPerUnit),
                    config.getDouble(root + "maximum-speed-up", d.maximumSpeedUp),
                    config.getInt(root + "outdoor-reach", d.outdoorReach),
                    config.getDouble(root + "opening-leak", d.openingLeak),
                    config.getDouble(root + "maximum-openness", d.maximumOpenness),
                    config.getDouble(root + "neighbour-exchange", d.neighbourExchange));
        } catch (IllegalArgumentException invalid) {
            warn.accept(invalid.getMessage() + ", so every sizing setting uses its default until config.yml is fixed.");
            return new SizingSettings(enabled, d.cellsPerUnit, d.poolCellsPerUnit, d.maximumSpeedUp, d.outdoorReach,
                    d.openingLeak, d.maximumOpenness, d.neighbourExchange);
        }
    }
}
