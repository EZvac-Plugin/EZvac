package com.github.blade.hvac.simulation;

import java.util.HashMap;
import java.util.Map;

/**
 * The outdoor air (3.2): each biome's particles and pollen, the seasons'
 * pollen, and rain washing the air clean. Pure.
 */
public final class OutdoorAir {
    private OutdoorAir() {}

    /** Rain or snow at full strength takes this share of the particles out of the air. */
    public static final double RAIN_WASHES_PARTICLES = 0.6;
    /** And this share of the pollen. */
    public static final double RAIN_WASHES_POLLEN = 0.85;

    /** Pollen at the middle of each season, as a share of the biome's spring peak. */
    public static final double SPRING_POLLEN = 1.0;
    public static final double SUMMER_POLLEN = 0.6;
    public static final double AUTUMN_POLLEN = 0.25;
    public static final double WINTER_POLLEN = 0.0;

    /**
     * A biome's air on a dry day: particles in µg/m³, and pollen in grains/m³
     * at its spring peak.
     */
    public record Baseline(double fine, double coarse, double pollen) {
        /** Somewhere EZvac has no figures for: a quiet, green countryside. */
        public static final Baseline DEFAULT = new Baseline(6.0, 10.0, 60.0);

        public Baseline {
            if (!Double.isFinite(fine) || fine < 0.0 || !Double.isFinite(coarse) || coarse < 0.0
                    || !Double.isFinite(pollen) || pollen < 0.0)
                throw new IllegalArgumentException("fine, coarse and pollen must be finite and not negative");
        }

        /** Underground the pollen fades with the sky; particles stay. */
        public Baseline underground(double factor) {
            if (!(factor > 0.0)) return this;
            return new Baseline(fine, coarse, pollen * (1.0 - Math.min(1.0, factor)));
        }
    }

    private static final Map<String, Baseline> BUILT_IN = new HashMap<>();

    private static void put(Baseline baseline, String... biomes) {
        for (String biome : biomes) BUILT_IN.put("minecraft:" + biome, baseline);
    }

    static {
        // Flowers and blossom: the worst pollen of all.
        put(new Baseline(5, 8, 300), "flower_forest");
        put(new Baseline(4, 6, 320), "meadow", "cherry_grove");
        put(new Baseline(6, 12, 250), "sunflower_plains");
        // Grassland and woodland.
        put(new Baseline(6, 12, 120), "plains");
        put(new Baseline(5, 8, 150), "forest");
        put(new Baseline(5, 8, 180), "birch_forest", "old_growth_birch_forest");
        put(new Baseline(5, 7, 120), "dark_forest");
        put(new Baseline(4, 6, 90), "taiga", "windswept_forest");
        put(new Baseline(4, 6, 110), "old_growth_pine_taiga", "old_growth_spruce_taiga");
        put(new Baseline(8, 30, 100), "savanna", "savanna_plateau");
        put(new Baseline(8, 35, 90), "windswept_savanna");
        put(new Baseline(6, 8, 70), "jungle");
        put(new Baseline(6, 8, 80), "sparse_jungle");
        put(new Baseline(6, 8, 90), "bamboo_jungle");
        put(new Baseline(7, 8, 90), "swamp");
        put(new Baseline(8, 8, 80), "mangrove_swamp");
        put(new Baseline(5, 8, 70), "river");
        // Dry and dusty.
        put(new Baseline(12, 60, 8), "desert");
        put(new Baseline(14, 70, 10), "badlands");
        put(new Baseline(15, 80, 8), "eroded_badlands");
        put(new Baseline(12, 50, 40), "wooded_badlands");
        // Hills and mountains.
        put(new Baseline(4, 8, 40), "windswept_hills");
        put(new Baseline(4, 12, 30), "windswept_gravelly_hills");
        put(new Baseline(3, 4, 40), "grove");
        put(new Baseline(3, 5, 10), "stony_peaks");
        put(new Baseline(2, 3, 5), "snowy_slopes");
        put(new Baseline(2, 2, 0), "frozen_peaks", "jagged_peaks");
        // Snow and ice.
        put(new Baseline(3, 4, 5), "snowy_plains", "snowy_beach", "frozen_river");
        put(new Baseline(3, 4, 30), "snowy_taiga");
        put(new Baseline(2, 3, 0), "ice_spikes");
        // Coasts and the sea: sea salt is coarse.
        put(new Baseline(5, 12, 30), "beach");
        put(new Baseline(4, 8, 20), "stony_shore");
        put(new Baseline(4, 10, 5), "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "ocean", "deep_ocean");
        put(new Baseline(3, 8, 3), "cold_ocean", "deep_cold_ocean");
        put(new Baseline(2, 4, 0), "frozen_ocean", "deep_frozen_ocean");
        put(new Baseline(3, 8, 0), "mushroom_fields");
        // Caves.
        put(new Baseline(5, 15, 0), "dripstone_caves");
        put(new Baseline(4, 5, 40), "lush_caves");
        put(new Baseline(4, 8, 0), "deep_dark");
        // The Nether: smoke, ash and spores.
        put(new Baseline(70, 110, 0), "nether_wastes");
        put(new Baseline(45, 90, 0), "crimson_forest");
        put(new Baseline(40, 85, 0), "warped_forest");
        put(new Baseline(120, 200, 0), "soul_sand_valley");
        put(new Baseline(250, 300, 0), "basalt_deltas");
        // The End: nothing in the air at all.
        put(new Baseline(1, 2, 0), "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens");
        put(new Baseline(0, 0, 0), "the_void");
    }

    /** EZvac's own figures for a biome id ({@code minecraft:desert}), or null. */
    public static Baseline builtIn(String key) {
        return BUILT_IN.get(BiomeClimate.normalize(key));
    }

    /**
     * Pollen through the year, as a share of the spring peak: highest in
     * mid-spring, then summer's grasses, autumn's weeds, none in mid-winter.
     * It moves in a straight line from one mid-season to the next, so it
     * eases across the seasons' boundaries instead of jumping.
     */
    public static double pollenSeasonFactor(long calendarTicks, int daysPerSeason) {
        long year = Seasons.yearTicks(daysPerSeason);
        double position = Math.floorMod(calendarTicks, year) / (double) year * 4.0; // 0 = first day of spring
        double[] middles = {SPRING_POLLEN, SUMMER_POLLEN, AUTUMN_POLLEN, WINTER_POLLEN};
        double fromMiddle = position - 0.5;
        int season = (int) Math.floor(fromMiddle);
        double t = fromMiddle - season;
        double from = middles[Math.floorMod(season, 4)];
        double to = middles[Math.floorMod(season + 1, 4)];
        return from + (to - from) * t;
    }

    /**
     * The air outdoors right now.
     *
     * @param pollenFactor the season's share of the spring peak
     * @param wetness      how hard it rains or snows here, 0 to 1
     */
    public static AirQualityModel.Air now(Baseline baseline, double pollenFactor, double wetness) {
        double wet = Double.isFinite(wetness) ? Math.max(0.0, Math.min(1.0, wetness)) : 0.0;
        double season = Double.isFinite(pollenFactor) ? Math.max(0.0, pollenFactor) : SUMMER_POLLEN;
        double particles = 1.0 - RAIN_WASHES_PARTICLES * wet;
        return new AirQualityModel.Air(baseline.fine() * particles, baseline.coarse() * particles,
                baseline.pollen() * season * (1.0 - RAIN_WASHES_POLLEN * wet));
    }
}
