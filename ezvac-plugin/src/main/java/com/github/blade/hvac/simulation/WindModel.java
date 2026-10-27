package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.WindSettings;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The daily wind (3.2). Each day of a world draws a level, Calm, Breezy,
 * Windy or Gale, and a mean speed from the world seed and the day, so the
 * wind is the same for everyone, survives a restart and needs nothing saved.
 * Within the day it gusts around that mean, and over the first hours of a
 * day it eases over from the day before. Terrain then changes it: high,
 * open ground and the sea are windier, forests sheltered. Pure.
 */
public final class WindModel {
    private WindModel() {}

    public static final long DAY_TICKS = 24_000L;
    /** A new day's wind takes over from the last one's across this many in-game hours, from dawn. */
    public static final double EASE_HOURS = 2.0;
    /** Above sea level the wind rises by this share per block... */
    public static final double ALTITUDE_PER_BLOCK = 1.0 / 120.0;
    /** ...up to this much. */
    public static final double MAXIMUM_ALTITUDE = 1.5;
    /** No spot gets more than this times the open-ground wind, biome and height together. */
    public static final double MAXIMUM_EXPOSURE = 1.8;

    public enum Level {
        CALM, BREEZY, WINDY, GALE;

        public String displayName() {
            String name = name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        /** {@code gale}, {@code Windy}; null when it names no level. */
        public static Level parse(String text) {
            if (text == null) return null;
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "calm" -> CALM;
                case "breezy", "breeze" -> BREEZY;
                case "windy" -> WINDY;
                case "gale", "gales" -> GALE;
                default -> null;
            };
        }

        /** The level a speed reads as: what a wind sensor shows, gusts included. */
        public static Level of(double speedMs, WindSettings.Speeds speeds) {
            if (!(speedMs > 0.0)) return CALM;
            if (speedMs >= (speeds.windy().to() + speeds.gale().from()) / 2.0) return GALE;
            if (speedMs >= (speeds.breezy().to() + speeds.windy().from()) / 2.0) return WINDY;
            if (speedMs >= (speeds.calm().to() + speeds.breezy().from()) / 2.0) return BREEZY;
            return CALM;
        }
    }

    /** One day's wind: its level, and its mean speed on open, level ground. */
    public record Day(long day, Level level, double meanMs) {}

    /**
     * A day's wind.
     *
     * @param windiness the season's multiplier on Windy and Gale days; 1 without seasons
     */
    public static Day day(long seed, long day, double windiness, WindSettings settings) {
        return day(seed, day, level(seed, day, windiness, settings), settings);
    }

    /** A day's wind at a level chosen for it, as {@code /hvac wind set} does. */
    public static Day day(long seed, long day, Level level, WindSettings settings) {
        WindSettings.Range range = settings.speeds().of(level);
        double t = random(seed, day, 0x5EED_5BEEDL).nextDouble();
        return new Day(day, level, range.from() + (range.to() - range.from()) * t);
    }

    /**
     * The day's level: one roll against the chances, with Windy and Gale
     * scaled by the season's windiness and Calm taking up the difference.
     */
    public static Level level(long seed, long day, double windiness, WindSettings settings) {
        WindSettings.Chances chances = settings.chances();
        double scale = Double.isFinite(windiness) ? Math.max(0.0, windiness) : 1.0;
        double total = chances.calm() + chances.breezy() + chances.windy() + chances.gale();
        double windy = chances.windy() * scale;
        double gale = chances.gale() * scale;
        double breezy = chances.breezy();
        // Calm gives way to a windier season, but never goes below nothing.
        double calm = Math.max(0.0, total - breezy - windy - gale);
        double roll = random(seed, day, 0x1E7E1L).nextDouble() * (calm + breezy + windy + gale);
        if ((roll -= gale) < 0.0) return Level.GALE;
        if ((roll -= windy) < 0.0) return Level.WINDY;
        if ((roll -= breezy) < 0.0) return Level.BREEZY;
        return Level.CALM;
    }

    /**
     * The wind on open, level ground at this moment: the day's mean, eased
     * over from yesterday's after dawn, gusting around it. Never negative.
     */
    public static double speedMs(long seed, long fullTime, Day today, Day yesterday, WindSettings settings) {
        long intoDay = Math.floorMod(fullTime, DAY_TICKS);
        double hours = intoDay / ThermalModel.MINECRAFT_TICKS_PER_HOUR;
        double mean = today.meanMs();
        if (yesterday != null && hours < EASE_HOURS) {
            double t = hours / EASE_HOURS;
            double smooth = t * t * (3.0 - 2.0 * t);
            mean = yesterday.meanMs() + (today.meanMs() - yesterday.meanMs()) * smooth;
        }
        double gust = gust(seed, fullTime / ThermalModel.MINECRAFT_TICKS_PER_HOUR);
        return Math.max(0.0, mean * (1.0 + settings.gusts() * gust));
    }

    /**
     * Smooth noise between -1 and 1 for the gusts: a seeded value every
     * in-game hour, eased from one to the next, so the wind rises and falls
     * without steps.
     */
    static double gust(long seed, double hours) {
        double floor = Math.floor(hours);
        double t = hours - floor;
        double a = random(seed, (long) floor, 0x6057L).nextDouble() * 2.0 - 1.0;
        double b = random(seed, (long) floor + 1, 0x6057L).nextDouble() * 2.0 - 1.0;
        double smooth = (1.0 - Math.cos(Math.PI * t)) / 2.0;
        return a + (b - a) * smooth;
    }

    /**
     * How much of the open-ground wind reaches a spot: its biome's exposure,
     * more with height above the sea, none underground.
     *
     * @param ground how far underground the spot is, 0 under the sky .. 1 deep down
     */
    public static double exposure(double biomeExposure, int y, int seaLevel, double ground) {
        double biome = Double.isFinite(biomeExposure) ? Math.max(0.0, biomeExposure) : 1.0;
        double altitude = Math.min(MAXIMUM_ALTITUDE, 1.0 + Math.max(0, y - seaLevel) * ALTITUDE_PER_BLOCK);
        double sky = Double.isFinite(ground) ? 1.0 - Math.max(0.0, Math.min(1.0, ground)) : 1.0;
        return Math.min(MAXIMUM_EXPOSURE, biome * altitude) * sky;
    }

    /** The world's day number, which the wind is drawn for. */
    public static long dayOf(long fullTime) {
        return Math.floorDiv(fullTime, DAY_TICKS);
    }

    private static SplittableRandom random(long seed, long day, long salt) {
        return new SplittableRandom(seed * 0x9E3779B97F4A7C15L ^ (day + salt) * 0xBF58476D1CE4E5B9L ^ salt);
    }

    private static final Map<String, Double> BUILT_IN = new HashMap<>();

    private static void put(double exposure, String... biomes) {
        for (String biome : biomes) BUILT_IN.put("minecraft:" + biome, exposure);
    }

    static {
        // High, bare ground.
        put(1.4, "jagged_peaks", "frozen_peaks", "stony_peaks", "snowy_slopes", "windswept_hills",
                "windswept_gravelly_hills", "windswept_forest", "windswept_savanna");
        // The sea and its shores.
        put(1.3, "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "cold_ocean",
                "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean", "beach", "stony_shore", "snowy_beach",
                "mushroom_fields");
        // Open country.
        put(1.15, "plains", "sunflower_plains", "savanna", "savanna_plateau", "desert", "badlands",
                "eroded_badlands", "wooded_badlands", "snowy_plains", "ice_spikes", "meadow");
        // Woods.
        put(0.75, "forest", "birch_forest", "taiga", "snowy_taiga", "flower_forest", "cherry_grove", "grove");
        put(0.7, "swamp", "mangrove_swamp");
        // Dense woods.
        put(0.5, "dark_forest", "jungle", "sparse_jungle", "bamboo_jungle", "old_growth_pine_taiga",
                "old_growth_spruce_taiga", "old_growth_birch_forest");
        // Caves, the Nether and the End have no wind.
        put(0.0, "dripstone_caves", "lush_caves", "deep_dark", "nether_wastes", "crimson_forest", "warped_forest",
                "soul_sand_valley", "basalt_deltas", "the_end", "end_highlands", "end_midlands", "small_end_islands",
                "end_barrens", "the_void");
    }

    /** EZvac's own exposure for a biome id ({@code minecraft:jungle}), or null. */
    public static Double builtInExposure(String key) {
        return BUILT_IN.get(BiomeClimate.normalize(key));
    }
}
