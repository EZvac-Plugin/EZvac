package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;

/**
 * Small, smooth differences between spots (2.0 Pilot 5), so a house isn't
 * one number from wall to wall. Readings only: what thermometers, sensors,
 * the HUD and placeholders show. Thermostats, equipment and the simulation
 * keep the room's own value, so no system ever chases the variance.
 *
 * <p>The variance is value noise: neighbouring blocks read almost the same
 * and the difference builds over a few blocks, so walking through a room the
 * reading changes gradually rather than flickering. It is fixed for a spot at
 * a moment, so a sign is never rewritten for nothing, and drifts slowly over
 * in-game hours, so no corner is warm forever. Pure.
 */
public final class Microvariance {
    /** Separate noise for temperature and moisture, so a warm spot isn't always a damp one. */
    public static final long TEMPERATURE = 0x5DEECE66DL;
    public static final long DEW_POINT = 0x9E3779B97F4A7C15L;

    private Microvariance() {}

    /**
     * How far air may stray here: the outdoor spread, narrowing to the indoor
     * spread as far as a system's air reaches the spot.
     */
    public static double airSpreadF(ClimateSettings.Variance variance, double influence) {
        return variance.outdoorF() + (variance.indoorF() - variance.outdoorF()) * clampFraction(influence);
    }

    /** How far water may stray here: a lake's spread, narrowing to a pool's as far as a pool system reaches it. */
    public static double waterSpreadF(ClimateSettings.Variance variance, double poolInfluence) {
        return variance.naturalWaterF() + (variance.poolF() - variance.naturalWaterF()) * clampFraction(poolInfluence);
    }

    /**
     * The offset at a block, at most {@code spreadF} either way; 0 when
     * variance is switched off.
     *
     * @param seed    the world's, so worlds differ
     * @param channel {@link #TEMPERATURE} or {@link #DEW_POINT}
     * @param hours   in-game hours since the world began
     */
    public static double offsetF(ClimateSettings.Variance variance, double spreadF, long seed, long channel,
                                 int x, int y, int z, double hours) {
        if (!variance.enabled() || !(spreadF > 0.0) || !Double.isFinite(hours)) return 0.0;
        return spreadF * noise(seed ^ channel, x / variance.spacingBlocks(), y / variance.spacingBlocks(),
                z / variance.spacingBlocks(), hours / variance.driftHours());
    }

    /**
     * Smooth noise in [-1, 1] over space and time: lattice values blended with
     * smoothstep, the space lattice from one moment of the time lattice eased
     * into the next.
     */
    static double noise(long seed, double x, double y, double z, double t) {
        long t0 = (long) Math.floor(t);
        double ft = smooth(t - t0);
        double now = spatial(seed, t0, x, y, z);
        double next = spatial(seed, t0 + 1, x, y, z);
        return now + (next - now) * ft;
    }

    private static double spatial(long seed, long t, double x, double y, double z) {
        long x0 = (long) Math.floor(x), y0 = (long) Math.floor(y), z0 = (long) Math.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double c000 = lattice(seed, x0, y0, z0, t), c100 = lattice(seed, x0 + 1, y0, z0, t);
        double c010 = lattice(seed, x0, y0 + 1, z0, t), c110 = lattice(seed, x0 + 1, y0 + 1, z0, t);
        double c001 = lattice(seed, x0, y0, z0 + 1, t), c101 = lattice(seed, x0 + 1, y0, z0 + 1, t);
        double c011 = lattice(seed, x0, y0 + 1, z0 + 1, t), c111 = lattice(seed, x0 + 1, y0 + 1, z0 + 1, t);
        double y00 = lerp(c000, c100, fx), y10 = lerp(c010, c110, fx);
        double y01 = lerp(c001, c101, fx), y11 = lerp(c011, c111, fx);
        return lerp(lerp(y00, y10, fy), lerp(y01, y11, fy), fz);
    }

    /** A fixed pseudo-random value in [-1, 1] for one lattice point. */
    private static double lattice(long seed, long x, long y, long z, long t) {
        long h = seed;
        h = mix(h ^ x * 0x632BE59BD9B4E019L);
        h = mix(h ^ y * 0x8CB92BA72F3D8DD7L);
        h = mix(h ^ z * 0xBF58476D1CE4E5B9L);
        h = mix(h ^ t * 0x94D049BB133111EBL);
        return (h >>> 11) * 0x1.0p-52 - 1.0;
    }

    /** SplitMix64's finaliser. */
    private static long mix(long h) {
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double smooth(double f) {
        return f * f * (3.0 - 2.0 * f);
    }

    private static double lerp(double a, double b, double f) {
        return a + (b - a) * f;
    }

    private static double clampFraction(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
