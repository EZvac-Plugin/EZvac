package com.github.blade.hvac.simulation;

/**
 * How the smoke of a fire burning in the open spreads through the outdoor
 * air (BETA 1.0 Pilot 4). A simple plume shaped by the wind: strongest at the
 * source, stretching downwind and widening and thinning as it goes, barely
 * upwind. A stronger wind thins it; a calm day leaves it pooled round the
 * source. It never reaches past {@code reach} blocks.
 */
public final class SmokePlume {
    /** Close round the source, smoke rises and drifts every way. */
    static final double NEAR_BLOCKS = 4.0;
    /** In calm air it pools: half as thick this far out. */
    static final double POOL_BLOCKS = 16.0;
    /** The plume's half-width at the source, and how much it widens per block downwind. */
    static final double WIDTH_BLOCKS = 4.0;
    static final double SPREAD = 0.25;
    /** A wind of this many m/s thins the smoke to half what it is in still air. */
    static final double THINNING_MS = 3.0;
    /** How far up or down from the source the smoke still reaches, roughly. */
    static final double HEIGHT_BLOCKS = 24.0;
    /** The outdoor air a source's hourly emission makes right beside it, against a room of the reference size. */
    public static final double STRENGTH = 3.0;

    private SmokePlume() {}

    /**
     * The share of a source's smoke found at an offset from it, 0 to 1.
     *
     * @param dx          east of the source, blocks
     * @param dy          above it; NaN to ignore height (a map's layer)
     * @param dz          south of it
     * @param windMs      the wind there
     * @param fromDegrees where the wind comes from, 0 north and clockwise; NaN for no direction
     * @param reach       the furthest it goes
     */
    public static double share(double dx, double dy, double dz, double windMs, double fromDegrees, double reach) {
        double r2 = dx * dx + dz * dz;
        if (!(reach > 0.0) || r2 >= reach * reach) return 0.0;
        double r = Math.sqrt(r2);
        double vertical = Double.isNaN(dy) ? 1.0 : Math.exp(-(dy / HEIGHT_BLOCKS) * (dy / HEIGHT_BLOCKS));
        double speed = Double.isFinite(windMs) ? Math.max(0.0, windMs) : 0.0;
        // From calm (pooled) to a steady wind (a plume) between 0.5 and 2.5 m/s.
        double windy = Double.isFinite(fromDegrees) ? clamp((speed - 0.5) / 2.0) : 0.0;
        double pooled = 1.0 / (1.0 + (r / POOL_BLOCKS) * (r / POOL_BLOCKS));
        double plume = 0.0;
        if (windy > 0.0) {
            double toward = Math.toRadians(fromDegrees + 180.0);
            double ux = Math.sin(toward), uz = -Math.cos(toward);
            double down = dx * ux + dz * uz;
            if (down > 0.0) {
                double across = dz * ux - dx * uz;
                double width = WIDTH_BLOCKS + SPREAD * down;
                plume = WIDTH_BLOCKS / width * Math.exp(-across * across / (2.0 * width * width));
            }
        }
        double near = Math.exp(-(r / NEAR_BLOCKS) * (r / NEAR_BLOCKS));
        double spread = Math.max(near, (1.0 - windy) * pooled + windy * plume);
        double thinned = THINNING_MS / (speed + THINNING_MS);
        double taper = 1.0 - r / reach;
        return spread * thinned * taper * vertical;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
