package com.github.blade.hvac.simulation;

/**
 * Where rain meets snow on the land (BETA 1.0 Pilot 4). Each 64-block area
 * decides how far its air is from the snow line (its margin: positive
 * snows), with a storm's hold; each spot blends the margins of the four
 * nearest area centres and adds a little noise, so the edge between rain
 * and snow is a wavy line following the cold, never the areas' grid.
 */
public final class SnowEdge {
    /** How far the noise moves the edge, in degrees F. */
    public static final double NOISE_F = 1.5;
    /** How quickly the noise varies, in blocks. */
    public static final double NOISE_BLOCKS = 16.0;
    /** A held decision never gets closer to the line than this, so it stays what it was. */
    static final double HELD_F = 0.25;

    private SnowEdge() {}

    /**
     * An area's margin: how much colder its air is than its line (the line
     * moved by {@code keepF} toward what it has, so a storm keeps its kind).
     *
     * @param snowing what it has now: true snow, false rain, null nothing yet
     * @param held    true while it may not change yet: the sign stays what it has
     */
    public static double margin(double airF, double lineF, Boolean snowing, double keepF, boolean held) {
        double line = snowing == null ? lineF : snowing ? lineF + keepF : lineF - keepF;
        double margin = line - airF;
        if (!Double.isFinite(margin)) return -HELD_F;
        if (held && snowing != null) return snowing ? Math.max(margin, HELD_F) : Math.min(margin, -HELD_F);
        return margin;
    }

    /** Blends four area margins at a spot between their centres, {@code tx} and {@code tz} from 0 to 1. */
    public static double blend(double m00, double m10, double m01, double m11, double tx, double tz) {
        double near = m00 + (m10 - m00) * tx, far = m01 + (m11 - m01) * tx;
        return near + (far - near) * tz;
    }

    /** Smooth noise from -1 to 1, the same for a world's seed and a spot every time. */
    public static double noise(long seed, double x, double z) {
        double u = x / NOISE_BLOCKS, v = z / NOISE_BLOCKS;
        int x0 = (int) Math.floor(u), z0 = (int) Math.floor(v);
        double tx = smooth(u - x0), tz = smooth(v - z0);
        return blend(lattice(seed, x0, z0), lattice(seed, x0 + 1, z0), lattice(seed, x0, z0 + 1),
                lattice(seed, x0 + 1, z0 + 1), tx, tz);
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lattice(long seed, int x, int z) {
        long h = seed ^ 0x5DEECE66DL;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }
}
