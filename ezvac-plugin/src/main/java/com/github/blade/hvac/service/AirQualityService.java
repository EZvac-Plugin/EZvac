package com.github.blade.hvac.service;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.model.AirflowTarget;
import com.github.blade.hvac.model.SmokeSource;
import com.github.blade.hvac.simulation.AirQualityModel;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What burns in each space (3.2). The airflow scan notes where campfires,
 * fire, lava, furnaces and candles are; this reads which of them burn, every
 * so often rather than every cycle, and turns them into particles per hour
 * for the space's size.
 */
public final class AirQualityService {
    /** Lighting a campfire changes no block, so each space's sources are read again this often. */
    private static final long REFRESH_TICKS = 200L;

    private record Reading(AirQualityModel.Air atReference, long measuredAt) {}

    private final AirflowService airflow;
    private final AirQualitySettings settings;
    private final Map<AirflowTarget, Reading> readings = new HashMap<>();

    public AirQualityService(AirflowService airflow, AirQualitySettings settings) {
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public AirQualitySettings settings() { return settings; }

    public boolean enabled() { return settings.enabled(); }

    /**
     * Particles per hour the space's burning sources add, diluted by its
     * size: in full at the reference size, less in a larger space, and at
     * most four times as much in a small one, so a closet with a campfire is
     * bad but not absurd. Nothing while the space is unmapped.
     */
    public AirQualityModel.Air sourcesFor(AirflowTarget target, UUID worldId, int cells) {
        if (cells <= 0) return AirQualityModel.Air.NONE;
        long[] smoke = airflow.smokeCells(target);
        if (smoke.length == 0) {
            readings.remove(target);
            return AirQualityModel.Air.NONE;
        }
        World world = Bukkit.getWorld(worldId);
        if (world == null) return AirQualityModel.Air.NONE;
        long now = world.getGameTime();
        Reading reading = readings.get(target);
        if (reading == null || now - reading.measuredAt() >= REFRESH_TICKS || now < reading.measuredAt()) {
            reading = new Reading(measure(world, smoke), now);
            readings.put(target, reading);
        }
        return reading.atReference().times(dilution(cells, settings.referenceCells()));
    }

    /** How strongly a source shows in a space this size, against the reference size. */
    static double dilution(int cells, int referenceCells) {
        return referenceCells / (double) Math.max(cells, Math.max(1, referenceCells / 4));
    }

    private AirQualityModel.Air measure(World world, long[] smoke) {
        double fine = 0.0, coarse = 0.0;
        for (long cell : smoke) {
            int x = AirflowService.unpackX(cell), y = AirflowService.unpackY(cell), z = AirflowService.unpackZ(cell);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block block = world.getBlockAt(x, y, z);
            // The block may have changed since the scan; it only counts while it is still a burning source.
            SmokeSource source = SmokeSource.of(block.getType());
            if (source == null || !source.burning(block.getBlockData())) continue;
            AirQualitySettings.Source emission = source.emission(settings.sources());
            fine += emission.fine();
            coarse += emission.coarse();
        }
        return new AirQualityModel.Air(fine, coarse, 0.0);
    }

    /** Drops a removed space's reading. */
    public void forget(AirflowTarget target) {
        readings.remove(target);
    }
}
