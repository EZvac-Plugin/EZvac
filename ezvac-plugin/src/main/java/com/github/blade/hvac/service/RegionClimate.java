package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.simulation.BiomeClimate;
import com.github.blade.hvac.simulation.OutdoorAir;
import com.github.blade.hvac.simulation.WindModel;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The climate of a world at a large scale (BETA 1.0 Pilot 3): which climate
 * belt each part of it lies in, for the wind's direction, and its outdoor air,
 * for maps. It reads biomes from the world's vanilla biome provider, which
 * works them out from the world seed, so it never loads a chunk and knows
 * places no one has been. Biomes are read on a {@value #CELL}-block grid and
 * blended between, so the belts change gradually. Kept in memory only. Main
 * thread only.
 */
public final class RegionClimate {
    /** Biomes are read this many blocks apart. */
    public static final int CELL = 256;

    /**
     * What one grid point reads.
     *
     * @param downfall how wet its ground is, 0 (desert) .. 1, roughly: rain biomes 0.6, snow 0.5, dry ones 0
     */
    record Cell(double meanF, OutdoorAir.Baseline air, double exposure, double downfall) {
        static final Cell TEMPERATE = new Cell(55.0, OutdoorAir.Baseline.DEFAULT, 1.0, 0.6);
    }

    private final ClimateSettings climate;
    private final Consumer<String> warn;
    private final Map<UUID, Map<Long, Cell>> cells = new HashMap<>();
    private final Map<UUID, Boolean> failed = new HashMap<>();

    public RegionClimate(ClimateSettings climate, Consumer<String> warn) {
        this.climate = Objects.requireNonNull(climate, "climate");
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    /** How much a spot lies in each climate belt. */
    public WindModel.Belts belts(World world, double x, double z) {
        return WindModel.belts(blended(world, x, z, Cell::meanF));
    }

    /** The outdoor air a spot's biomes have before season, rain and wind. */
    public OutdoorAir.Baseline air(World world, double x, double z) {
        return new OutdoorAir.Baseline(blended(world, x, z, cell -> cell.air().fine()),
                blended(world, x, z, cell -> cell.air().coarse()), blended(world, x, z, cell -> cell.air().pollen()));
    }

    /** How open the spot's biomes are to the wind, at sea level. */
    public double exposure(World world, double x, double z) {
        return blended(world, x, z, Cell::exposure);
    }

    /** Roughly how wet the spot's ground is, for the dust the wind lifts. */
    public double downfall(World world, double x, double z) {
        return blended(world, x, z, Cell::downfall);
    }

    /** Drops what is kept for a world, when it unloads. */
    public void forget(World world) {
        cells.remove(world.getUID());
        failed.remove(world.getUID());
    }

    private double blended(World world, double x, double z, java.util.function.ToDoubleFunction<Cell> value) {
        double u = x / CELL - 0.5, v = z / CELL - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fu = u - i, fv = v - j;
        double a = value.applyAsDouble(cell(world, i, j)), b = value.applyAsDouble(cell(world, i + 1, j));
        double c = value.applyAsDouble(cell(world, i, j + 1)), d = value.applyAsDouble(cell(world, i + 1, j + 1));
        double top = a + (b - a) * fu, bottom = c + (d - c) * fu;
        return top + (bottom - top) * fv;
    }

    private Cell cell(World world, int i, int j) {
        if (world == null) return Cell.TEMPERATE;
        Map<Long, Cell> known = cells.computeIfAbsent(world.getUID(), ignored -> new HashMap<>());
        long key = ((long) i << 32) ^ (j & 0xFFFF_FFFFL);
        Cell cell = known.get(key);
        if (cell == null) {
            cell = read(world, i * CELL + CELL / 2, j * CELL + CELL / 2);
            known.put(key, cell);
        }
        return cell;
    }

    private Cell read(World world, int x, int z) {
        if (Boolean.TRUE.equals(failed.get(world.getUID()))) return Cell.TEMPERATE;
        try {
            BiomeProvider provider = world.vanillaBiomeProvider();
            int sea = world.getSeaLevel();
            Biome biome = provider.getBiome(world, x, sea + 8, z);
            // Under a mountain the grid can land in a cave; its surface is above.
            if (underground(biome)) biome = provider.getBiome(world, x, sea + 64, z);
            return of(biome);
        } catch (RuntimeException | LinkageError unreadable) {
            failed.put(world.getUID(), true);
            warn.accept("Couldn't read the biomes of '" + world.getName() + "' from its seed (" + unreadable
                    + "), so its wind blows as in the westerlies everywhere.");
            return Cell.TEMPERATE;
        }
    }

    private static boolean underground(Biome biome) {
        if (biome == null || biome == Biome.CUSTOM) return false;
        String key = biome.getKey().getKey();
        return key.endsWith("_caves") || key.equals("deep_dark");
    }

    private Cell of(Biome biome) {
        if (biome == null || biome == Biome.CUSTOM) return Cell.TEMPERATE;
        String key = biome.getKey().toString();
        BiomeClimate.Climate found = climate.biomes().climate(key);
        double meanF = found == null ? Cell.TEMPERATE.meanF() : (found.noonF() + found.midnightF()) / 2.0;
        double downfall = found == null ? Cell.TEMPERATE.downfall() : switch (found.precipitation()) {
            case NONE -> 0.0;
            case SNOW -> 0.5;
            case RAIN -> 0.6;
        };
        return new Cell(meanF, climate.airQuality().baseline(key), climate.wind().exposure(key), downfall);
    }
}
