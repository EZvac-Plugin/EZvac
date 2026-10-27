package com.github.blade.hvac.simulation;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Each biome's own climate (2.1), replacing the seven name-matched
 * classes of earlier releases. Values are year-round averages at sea level:
 * altitude, the ground and the seasons are applied on top. Modelled on real
 * places: a desert swings widely between day and night, a jungle or the sea
 * barely moves, and the further from the tropics, the stronger the seasons.
 * Pure.
 */
public final class BiomeClimate {
    private BiomeClimate() {}

    /**
     * @param noonF      average noon temperature over the year, at sea level
     * @param midnightF  average midnight temperature over the year, at sea level
     * @param seasonalF  how far mid-summer and mid-winter move both away from those averages
     */
    public record Climate(double noonF, double midnightF, double seasonalF, Precipitation precipitation) {
        public Climate {
            if (!Double.isFinite(noonF) || !Double.isFinite(midnightF))
                throw new IllegalArgumentException("noon and midnight temperatures must be finite");
            if (midnightF > noonF) throw new IllegalArgumentException("midnight must not be warmer than noon");
            if (!Double.isFinite(seasonalF) || seasonalF < 0.0)
                throw new IllegalArgumentException("the seasonal swing must be non-negative");
            Objects.requireNonNull(precipitation, "precipitation");
        }
    }

    private static Climate rain(double noon, double midnight, double seasonal) {
        return new Climate(noon, midnight, seasonal, Precipitation.RAIN);
    }

    private static Climate snow(double noon, double midnight, double seasonal) {
        return new Climate(noon, midnight, seasonal, Precipitation.SNOW);
    }

    private static Climate dry(double noon, double midnight, double seasonal) {
        return new Climate(noon, midnight, seasonal, Precipitation.NONE);
    }

    /** Every vanilla 1.21.1 biome. Mountain biomes are given at sea level; altitude cools them where they stand. */
    private static final Map<String, Climate> BUILT_IN = Map.ofEntries(
            // Oceans and shores: water keeps the air mild, by day and through the year.
            Map.entry("ocean", rain(66, 60, 10)),
            Map.entry("deep_ocean", rain(66, 60, 10)),
            Map.entry("warm_ocean", rain(84, 80, 3)),
            Map.entry("lukewarm_ocean", rain(76, 70, 7)),
            Map.entry("deep_lukewarm_ocean", rain(76, 70, 7)),
            Map.entry("cold_ocean", rain(52, 46, 10)),
            Map.entry("deep_cold_ocean", rain(52, 46, 10)),
            Map.entry("frozen_ocean", snow(30, 24, 14)),
            Map.entry("deep_frozen_ocean", snow(30, 24, 14)),
            Map.entry("river", rain(68, 50, 16)),
            Map.entry("frozen_river", snow(30, 14, 22)),
            Map.entry("beach", rain(76, 60, 12)),
            Map.entry("snowy_beach", snow(32, 18, 20)),
            Map.entry("stony_shore", rain(62, 48, 14)),
            Map.entry("mushroom_fields", rain(66, 56, 8)),
            // Temperate lands.
            Map.entry("plains", rain(72, 50, 18)),
            Map.entry("sunflower_plains", rain(72, 50, 18)),
            Map.entry("meadow", rain(66, 44, 16)),
            Map.entry("cherry_grove", rain(68, 48, 16)),
            Map.entry("forest", rain(68, 48, 18)),
            Map.entry("flower_forest", rain(70, 50, 18)),
            Map.entry("birch_forest", rain(66, 46, 18)),
            Map.entry("old_growth_birch_forest", rain(66, 46, 18)),
            Map.entry("dark_forest", rain(64, 50, 16)),
            Map.entry("swamp", rain(80, 60, 14)),
            // Tropics: hot and humid, with almost no seasons.
            Map.entry("mangrove_swamp", rain(86, 74, 6)),
            Map.entry("jungle", rain(88, 74, 2)),
            Map.entry("sparse_jungle", rain(89, 72, 3)),
            Map.entry("bamboo_jungle", rain(87, 74, 2)),
            // Dry lands: clear skies let the day heat and the night cool hard.
            Map.entry("savanna", dry(92, 70, 4)),
            Map.entry("savanna_plateau", dry(92, 68, 4)),
            Map.entry("windswept_savanna", dry(90, 66, 5)),
            Map.entry("desert", dry(98, 64, 14)),
            Map.entry("badlands", dry(92, 60, 18)),
            Map.entry("wooded_badlands", dry(90, 60, 18)),
            Map.entry("eroded_badlands", dry(94, 60, 18)),
            // The north: cool summers, long cold winters.
            Map.entry("taiga", rain(52, 32, 24)),
            Map.entry("old_growth_pine_taiga", rain(54, 38, 18)),
            Map.entry("old_growth_spruce_taiga", rain(54, 38, 18)),
            Map.entry("snowy_taiga", snow(32, 12, 24)),
            Map.entry("snowy_plains", snow(28, 8, 26)),
            Map.entry("ice_spikes", snow(22, 2, 26)),
            // Mountains, before altitude.
            Map.entry("windswept_hills", rain(58, 38, 20)),
            Map.entry("windswept_gravelly_hills", rain(58, 38, 20)),
            Map.entry("windswept_forest", rain(58, 40, 20)),
            Map.entry("grove", snow(44, 26, 20)),
            Map.entry("snowy_slopes", snow(38, 20, 20)),
            Map.entry("frozen_peaks", snow(34, 16, 18)),
            Map.entry("jagged_peaks", snow(34, 16, 18)),
            Map.entry("stony_peaks", rain(58, 38, 16)),
            // Caves: steady all day and all year.
            Map.entry("dripstone_caves", rain(58, 58, 0)),
            Map.entry("lush_caves", rain(64, 64, 0)),
            Map.entry("deep_dark", rain(52, 52, 0)),
            // The Nether and the End have no sun and no seasons.
            Map.entry("nether_wastes", dry(112, 112, 0)),
            Map.entry("soul_sand_valley", dry(98, 98, 0)),
            Map.entry("crimson_forest", dry(104, 104, 0)),
            Map.entry("warped_forest", dry(96, 96, 0)),
            Map.entry("basalt_deltas", dry(120, 120, 0)),
            Map.entry("the_end", dry(30, 30, 0)),
            Map.entry("small_end_islands", dry(30, 30, 0)),
            Map.entry("end_midlands", dry(30, 30, 0)),
            Map.entry("end_highlands", dry(30, 30, 0)),
            Map.entry("end_barrens", dry(30, 30, 0)),
            Map.entry("the_void", dry(30, 30, 0)));

    /** {@code minecraft:plains}, {@code plains} and {@code PLAINS} all name the same biome. */
    public static String normalize(String key) {
        String value = key.trim().toLowerCase(Locale.ROOT);
        return value.indexOf(':') < 0 ? "minecraft:" + value : value;
    }

    /** The built-in climate of a vanilla biome, or null for one this table doesn't know. */
    public static Climate builtIn(String key) {
        String normalized = normalize(key);
        if (!normalized.startsWith("minecraft:")) return null;
        return BUILT_IN.get(normalized.substring("minecraft:".length()));
    }

    /** Below this vanilla biome temperature, Minecraft makes snow instead of rain. */
    public static final double SNOW_BELOW = 0.15;

    /**
     * A climate for a biome the table doesn't know (a data pack's), worked
     * out from what Minecraft says about it: its temperature (-0.7 for the
     * coldest peaks, 0.8 for plains, 2.0 for deserts) and its downfall.
     */
    public static Climate estimate(double vanillaTemperature, double downfall) {
        double t = Double.isFinite(vanillaTemperature) ? vanillaTemperature : 0.8;
        double wet = Double.isFinite(downfall) ? Math.max(0.0, Math.min(1.0, downfall)) : 0.4;
        double mean = interpolate(t, MEAN_BY_TEMPERATURE);
        // Dry air lets the day heat and the night cool: a desert swings 35 F, a jungle 13 F.
        double swing = 10.0 + 25.0 * (1.0 - wet);
        Precipitation precipitation = t < SNOW_BELOW ? Precipitation.SNOW
                : t > 1.0 && wet < 0.1 ? Precipitation.NONE : Precipitation.RAIN;
        return new Climate(mean + swing / 2.0, mean - swing / 2.0, estimateSeasonalF(mean), precipitation);
    }

    /**
     * The seasonal swing to expect for a daily mean, when nothing better is
     * known: strong in the cold north, weak in the tropics. Also used for
     * thermostats saved before 2.1, until they sample their biome again.
     */
    public static double estimateSeasonalF(double meanF) {
        if (!Double.isFinite(meanF)) return 0.0;
        return interpolate(meanF, SEASONAL_BY_MEAN);
    }

    private static final double[][] MEAN_BY_TEMPERATURE = {
            {-0.7, 9}, {0.0, 18}, {0.25, 42}, {0.5, 56}, {0.8, 61}, {0.95, 78}, {2.0, 81}};
    private static final double[][] SEASONAL_BY_MEAN = {
            {20, 26}, {45, 24}, {60, 18}, {75, 8}, {82, 3}};

    private static double interpolate(double x, double[][] points) {
        if (x <= points[0][0]) return points[0][1];
        for (int i = 1; i < points.length; i++) {
            if (x <= points[i][0]) {
                double t = (x - points[i - 1][0]) / (points[i][0] - points[i - 1][0]);
                return points[i - 1][1] + (points[i][1] - points[i - 1][1]) * t;
            }
        }
        return points[points.length - 1][1];
    }

    /** How much colder the air is at this height than at {@code fromY}: nothing below it. */
    public static double lapseF(double y, double fromY, double perBlockF) {
        return y > fromY ? (y - fromY) * perBlockF : 0.0;
    }

    /**
     * How much a spot this deep below ground takes on the ground's steady
     * temperature: 0 down to {@code startDepth}, 1 from {@code fullDepth}.
     */
    public static double groundFactor(double depth, double startDepth, double fullDepth) {
        if (!(depth > startDepth)) return 0.0;
        if (depth >= fullDepth) return 1.0;
        double t = (depth - startDepth) / (fullDepth - startDepth);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Underground: the day/night swing fades into the daily mean, the seasons
     * fade, and weather no longer reaches. Deep down it is the same all day
     * and all year, as in a real mine. Pollen and wind fade with the sky (3.2).
     */
    public static BiomeBlend.Blended underground(BiomeBlend.Blended surface, double factor) {
        if (!(factor > 0.0)) return surface;
        double g = Math.min(1.0, factor);
        double mean = (surface.noonF() + surface.midnightF()) / 2.0;
        return new BiomeBlend.Blended(surface.noonF() + (mean - surface.noonF()) * g,
                surface.midnightF() + (mean - surface.midnightF()) * g, surface.downfall(),
                surface.rainShare() * (1.0 - g), surface.snowShare() * (1.0 - g), surface.seasonalF() * (1.0 - g),
                surface.air().underground(g), surface.windExposure() * (1.0 - g));
    }
}
