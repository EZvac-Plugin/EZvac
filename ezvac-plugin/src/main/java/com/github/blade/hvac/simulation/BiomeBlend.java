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
    public record Sample(double noonF, double midnightF, double downfall, Precipitation precipitation, double weight,
                         double seasonalF, OutdoorAir.Baseline air, double windExposure) {
        public Sample {
            air = air == null ? OutdoorAir.Baseline.DEFAULT : air;
            windExposure = Double.isFinite(windExposure) ? Math.max(0.0, windExposure) : 1.0;
        }

        /** A sample fully open to the wind. */
        public Sample(double noonF, double midnightF, double downfall, Precipitation precipitation, double weight,
                      double seasonalF, OutdoorAir.Baseline air) {
            this(noonF, midnightF, downfall, precipitation, weight, seasonalF, air, 1.0);
        }

        /** A sample with the default outdoor air. */
        public Sample(double noonF, double midnightF, double downfall, Precipitation precipitation, double weight,
                      double seasonalF) {
            this(noonF, midnightF, downfall, precipitation, weight, seasonalF, OutdoorAir.Baseline.DEFAULT);
        }

        /** A sample with no seasons. */
        public Sample(double noonF, double midnightF, double downfall, Precipitation precipitation, double weight) {
            this(noonF, midnightF, downfall, precipitation, weight, 0.0);
        }
    }

    /**
     * The blended climate at a spot.
     *
     * @param rainShare share of the surroundings where rain falls
     * @param snowShare share where snow falls; the rest never gets weather
     * @param seasonalF how far mid-summer and mid-winter move away from noonF and midnightF (2.1)
     * @param air       the outdoor air on a dry day, pollen at its spring peak (3.2)
     * @param windExposure how much of the open-ground wind reaches the spot (3.2)
     */
    public record Blended(double noonF, double midnightF, double downfall, double rainShare, double snowShare,
                          double seasonalF, OutdoorAir.Baseline air, double windExposure) {
        public Blended {
            air = air == null ? OutdoorAir.Baseline.DEFAULT : air;
            windExposure = Double.isFinite(windExposure) ? Math.max(0.0, windExposure) : 1.0;
        }

        /** A climate fully open to the wind. */
        public Blended(double noonF, double midnightF, double downfall, double rainShare, double snowShare,
                       double seasonalF, OutdoorAir.Baseline air) {
            this(noonF, midnightF, downfall, rainShare, snowShare, seasonalF, air, 1.0);
        }

        /** A climate with the default outdoor air. */
        public Blended(double noonF, double midnightF, double downfall, double rainShare, double snowShare,
                       double seasonalF) {
            this(noonF, midnightF, downfall, rainShare, snowShare, seasonalF, OutdoorAir.Baseline.DEFAULT);
        }

        /** A climate with no seasons. */
        public Blended(double noonF, double midnightF, double downfall, double rainShare, double snowShare) {
            this(noonF, midnightF, downfall, rainShare, snowShare, 0.0);
        }

        /** A single biome's climate, unblended. */
        public static Blended of(double noonF, double midnightF, double downfall, Precipitation precipitation) {
            return of(noonF, midnightF, downfall, precipitation, 0.0);
        }

        /** A single biome's climate, unblended, with its seasons. */
        public static Blended of(double noonF, double midnightF, double downfall, Precipitation precipitation,
                                 double seasonalF) {
            return of(noonF, midnightF, downfall, precipitation, seasonalF, OutdoorAir.Baseline.DEFAULT);
        }

        /** A single biome's climate and air, unblended. */
        public static Blended of(double noonF, double midnightF, double downfall, Precipitation precipitation,
                                 double seasonalF, OutdoorAir.Baseline air) {
            return of(noonF, midnightF, downfall, precipitation, seasonalF, air, 1.0);
        }

        /** A single biome's climate, air and exposure to the wind, unblended. */
        public static Blended of(double noonF, double midnightF, double downfall, Precipitation precipitation,
                                 double seasonalF, OutdoorAir.Baseline air, double windExposure) {
            return new Blended(noonF, midnightF, downfall,
                    precipitation == Precipitation.RAIN ? 1.0 : 0.0, precipitation == Precipitation.SNOW ? 1.0 : 0.0,
                    seasonalF, air, windExposure);
        }

        /** The same climate with this outdoor air. */
        public Blended withAir(OutdoorAir.Baseline air) {
            return new Blended(noonF, midnightF, downfall, rainShare, snowShare, seasonalF, air, windExposure);
        }

        /** The same climate with this much of the wind reaching it. */
        public Blended withWindExposure(double exposure) {
            if (exposure == windExposure) return this;
            return new Blended(noonF, midnightF, downfall, rainShare, snowShare, seasonalF, air, exposure);
        }

        /** The same climate, this much warmer at noon and at midnight alike (negative: colder). */
        public Blended warmer(double shiftF) {
            if (shiftF == 0.0) return this;
            return new Blended(noonF + shiftF, midnightF + shiftF, downfall, rainShare, snowShare, seasonalF, air,
                    windExposure);
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
        double total = 0.0, noon = 0.0, midnight = 0.0, downfall = 0.0, rain = 0.0, snow = 0.0, seasonal = 0.0;
        double fine = 0.0, coarse = 0.0, pollen = 0.0, wind = 0.0;
        Sample first = null;
        boolean uniform = true;
        for (Sample sample : samples) {
            double w = sample.weight();
            if (!(w > 0.0)) continue;
            if (first == null) first = sample;
            else uniform &= sample.noonF() == first.noonF() && sample.midnightF() == first.midnightF()
                    && sample.downfall() == first.downfall() && sample.precipitation() == first.precipitation()
                    && sample.seasonalF() == first.seasonalF() && sample.air().equals(first.air())
                    && sample.windExposure() == first.windExposure();
            total += w;
            noon += sample.noonF() * w;
            midnight += sample.midnightF() * w;
            downfall += sample.downfall() * w;
            seasonal += sample.seasonalF() * w;
            fine += sample.air().fine() * w;
            coarse += sample.air().coarse() * w;
            pollen += sample.air().pollen() * w;
            wind += sample.windExposure() * w;
            if (sample.precipitation() == Precipitation.RAIN) rain += w;
            else if (sample.precipitation() == Precipitation.SNOW) snow += w;
        }
        if (total <= 0.0) return null;
        // Deep inside one biome the average is that biome, exactly, not to the last rounding bit.
        if (uniform) return Blended.of(first.noonF(), first.midnightF(), first.downfall(), first.precipitation(),
                first.seasonalF(), first.air(), first.windExposure());
        return new Blended(noon / total, midnight / total, downfall / total, share(rain, total), share(snow, total),
                seasonal / total, new OutdoorAir.Baseline(fine / total, coarse / total, pollen / total), wind / total);
    }

    /** Between two blended climates, for smooth readings between sample cells. */
    public static Blended lerp(Blended a, Blended b, double t) {
        if (t <= 0.0) return a;
        if (t >= 1.0) return b;
        return new Blended(lerp(a.noonF(), b.noonF(), t), lerp(a.midnightF(), b.midnightF(), t),
                lerp(a.downfall(), b.downfall(), t), lerp(a.rainShare(), b.rainShare(), t),
                lerp(a.snowShare(), b.snowShare(), t), lerp(a.seasonalF(), b.seasonalF(), t),
                a.air().equals(b.air()) ? a.air() : new OutdoorAir.Baseline(lerp(a.air().fine(), b.air().fine(), t),
                        lerp(a.air().coarse(), b.air().coarse(), t), lerp(a.air().pollen(), b.air().pollen(), t)),
                lerp(a.windExposure(), b.windExposure(), t));
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
