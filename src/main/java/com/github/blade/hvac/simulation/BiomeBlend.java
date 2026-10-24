package com.github.blade.hvac.simulation;

import java.util.List;

/**
 * Transition zones between biomes (2.1). A spot's outdoor climate is the
 * average of the biomes around it, nearer ones counting more, the way
 * Minecraft's Biome Blend setting fades grass colours. Deep inside a biome
 * every sample agrees and nothing changes; near a border the average ramps
 * from one climate to the other instead of jumping. Pure.
 */
public final class BiomeBlend {
    /** Minecraft stores biomes in 4x4x4 cells, so samples closer together add nothing. */
    public static final int SAMPLE_SPACING = 4;

    private BiomeBlend() {}

    /** One biome sample: its climate, and how much it counts. */
    public record Sample(double noonF, double midnightF, double downfall, Precipitation precipitation, double weight) {}

    /**
     * The blended climate at a spot.
     *
     * @param rainShare share of the surroundings where rain falls
     * @param snowShare share where snow falls; the rest never gets weather
     */
    public record Blended(double noonF, double midnightF, double downfall, double rainShare, double snowShare) {
        /** A single biome's climate, unblended. */
        public static Blended of(double noonF, double midnightF, double downfall, Precipitation precipitation) {
            return new Blended(noonF, midnightF, downfall,
                    precipitation == Precipitation.RAIN ? 1.0 : 0.0, precipitation == Precipitation.SNOW ? 1.0 : 0.0);
        }

        /** Share of the surroundings that gets any weather. */
        public double wetShare() { return rainShare + snowShare; }

        /**
         * The kind of weather this spot mostly gets: none unless at least half
         * its surroundings get weather, then rain or snow, whichever is more.
         */
        public Precipitation dominant() {
            if (wetShare() < 0.5) return Precipitation.NONE;
            return snowShare > rainShare ? Precipitation.SNOW : Precipitation.RAIN;
        }

        /** True when the surroundings are all one kind of weather, as a single biome is. */
        public boolean pure() {
            return isWhole(rainShare) && isWhole(snowShare);
        }

        private static boolean isWhole(double share) {
            return share == 0.0 || share == 1.0;
        }
    }

    /**
     * How much a sample counts at this distance from the spot: fully at the
     * spot, falling in a straight line to nothing just past the radius.
     */
    public static double weight(double distance, int radius) {
        if (radius <= 0) return distance <= 0.0 ? 1.0 : 0.0;
        double reach = radius + SAMPLE_SPACING;
        return Math.max(0.0, 1.0 - distance / reach);
    }

    /**
     * The weighted average of the samples, or null when there are none
     * (every sample was in an unloaded chunk) so the caller can fall back.
     */
    public static Blended blend(List<Sample> samples) {
        double total = 0.0, noon = 0.0, midnight = 0.0, downfall = 0.0, rain = 0.0, snow = 0.0;
        Sample first = null;
        boolean uniform = true;
        for (Sample sample : samples) {
            double w = sample.weight();
            if (!(w > 0.0)) continue;
            if (first == null) first = sample;
            else uniform &= sample.noonF() == first.noonF() && sample.midnightF() == first.midnightF()
                    && sample.downfall() == first.downfall() && sample.precipitation() == first.precipitation();
            total += w;
            noon += sample.noonF() * w;
            midnight += sample.midnightF() * w;
            downfall += sample.downfall() * w;
            if (sample.precipitation() == Precipitation.RAIN) rain += w;
            else if (sample.precipitation() == Precipitation.SNOW) snow += w;
        }
        if (total <= 0.0) return null;
        // Deep inside one biome the average is that biome, exactly, not to the last rounding bit.
        if (uniform) return Blended.of(first.noonF(), first.midnightF(), first.downfall(), first.precipitation());
        return new Blended(noon / total, midnight / total, downfall / total, share(rain, total), share(snow, total));
    }

    /** Between two blended climates, for smooth readings between sample cells. */
    public static Blended lerp(Blended a, Blended b, double t) {
        if (t <= 0.0) return a;
        if (t >= 1.0) return b;
        return new Blended(lerp(a.noonF(), b.noonF(), t), lerp(a.midnightF(), b.midnightF(), t),
                lerp(a.downfall(), b.downfall(), t), lerp(a.rainShare(), b.rainShare(), t),
                lerp(a.snowShare(), b.snowShare(), t));
    }

    private static double lerp(double a, double b, double t) {
        return a == b ? a : a + (b - a) * t;
    }

    /** A share that comes out whole when it is whole, so a single biome stays exactly itself. */
    private static double share(double part, double total) {
        if (part <= 0.0) return 0.0;
        if (part >= total) return 1.0;
        return part / total;
    }
}
