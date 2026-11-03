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

    /**
     * One day's wind: its level, its mean speed on open, level ground, and
     * where it blows from in each climate belt (BETA 1.0 Pilot 3).
     */
    public record Day(long day, Level level, double meanMs, Directions directions) {
        public Day {
            directions = directions == null ? Directions.TYPICAL : directions;
        }

        /** A day blowing from each belt's usual direction. */
        public Day(long day, Level level, double meanMs) {
            this(day, level, meanMs, Directions.TYPICAL);
        }

        /** Where it blows from in the westerlies, as a whole world's day reads. */
        public double fromDegrees() { return directions.westerlies(); }

        /** Where it blows from for a spot in these belts. */
        public double fromDegrees(Belts belts) { return directions.blend(belts); }

        /** The same day blowing from one direction everywhere, as {@code /hvac wind set} holds it. */
        public Day from(double degrees) {
            double from = normalize(degrees);
            return new Day(day, level, meanMs, new Directions(from, from, from));
        }
    }

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
        return new Day(day, level, range.from() + (range.to() - range.from()) * t,
                directions(seed, day, settings.direction().variation()));
    }

    // ---- direction (BETA 1.0 Pilot 3) ----

    /**
     * Where the wind blows from in each climate belt that day, in degrees
     * (0 north, 90 east: Minecraft's -Z is north).
     */
    public record Directions(double trades, double westerlies, double polar) {
        /** Each belt's usual direction. */
        public static final Directions TYPICAL = new Directions(TRADES_FROM, WESTERLIES_FROM, POLAR_FROM);

        public Directions {
            trades = normalize(trades);
            westerlies = normalize(westerlies);
            polar = normalize(polar);
        }

        /** The direction for a spot in these belts, blended as arrows so there is no jump at north. */
        public double blend(Belts belts) {
            double x = belts.trades() * east(trades) + belts.westerlies() * east(westerlies)
                    + belts.polar() * east(polar);
            double z = belts.trades() * south(trades) + belts.westerlies() * south(westerlies)
                    + belts.polar() * south(polar);
            return x * x + z * z < 1e-12 ? westerlies : degrees(x, z);
        }
    }

    /** The trade winds of hot climates come from the east-northeast... */
    public static final double TRADES_FROM = 70.0;
    /** ...temperate ones lie in the westerlies, from the west-southwest... */
    public static final double WESTERLIES_FROM = 255.0;
    /** ...and cold ones in the polar easterlies, from the northeast. */
    public static final double POLAR_FROM = 60.0;

    /**
     * How much a spot lies in each climate belt, adding up to 1: the trade
     * winds where the year averages 77 °F and up, the polar easterlies at 32 °F
     * and below, the westerlies between, blended across the edges.
     */
    public record Belts(double trades, double westerlies, double polar) {
        public static final Belts WESTERLIES = new Belts(0.0, 1.0, 0.0);
    }

    /** The belts for a climate whose year averages {@code meanF}. */
    public static Belts belts(double meanF) {
        if (!Double.isFinite(meanF)) return Belts.WESTERLIES;
        double trades = smoothstep(68.0, 80.0, meanF);
        double polar = 1.0 - smoothstep(28.0, 40.0, meanF);
        return new Belts(trades, Math.max(0.0, 1.0 - trades - polar), polar);
    }

    /**
     * The day's direction in each belt: the trades keep close to theirs, the
     * westerlies wander most and now and then blow from anywhere, the polar
     * easterlies between. {@code variation} scales how far.
     */
    static Directions directions(long seed, long day, double variation) {
        SplittableRandom random = random(seed, day, 0xD1EC7L);
        double trades = TRADES_FROM + 25.0 * variation * normal(random);
        double westerlies = random.nextDouble() < 0.10 * Math.min(1.0, variation)
                ? random.nextDouble() * 360.0 : WESTERLIES_FROM + 50.0 * variation * normal(random);
        double polar = POLAR_FROM + 40.0 * variation * normal(random);
        return new Directions(trades, westerlies, polar);
    }

    /** A bell-curve draw, kept within 2.5 of the middle. */
    private static double normal(SplittableRandom random) {
        return Math.max(-2.5, Math.min(2.5, random.nextGaussian()));
    }

    // ---- weather fronts (BETA 1.0 Pilot 3) ----

    /** Where the wind turns from ahead of rain... */
    public static final double AHEAD_FROM = 190.0;
    /** ...while it rains... */
    public static final double RAIN_FROM = 225.0;
    /** ...and once it has cleared. */
    public static final double BEHIND_FROM = 300.0;
    /** A front is felt this many in-game hours ahead and behind... */
    public static final double FRONT_HOURS = 6.0;
    /** ...and turns the westerlies this much of the way to it. */
    public static final double FRONT_WEIGHT = 0.7;

    /**
     * Rain passing through, as the westerlies feel it: the wind backs to the
     * south ahead of it, blows from the southwest while it rains and veers to
     * the west-northwest once it clears.
     *
     * @param fromDegrees where the front turns the wind toward
     * @param strength    how far, 0 (not at all) .. 1
     */
    public record Front(Kind kind, double fromDegrees, double strength) {
        public static final Front NONE = new Front(Kind.NONE, WESTERLIES_FROM, 0.0);

        public enum Kind { NONE, AHEAD, RAIN, BEHIND }
    }

    /**
     * The front from the world's weather clocks.
     *
     * @param rainTicks        while clear, the ticks until rain; while raining, until it stops
     * @param clearTicks       above 0 while {@code /weather clear} holds the sky clear
     * @param sinceRainStarted ticks since it began raining, or -1 when not known
     * @param sinceRainEnded   ticks since it stopped raining, or -1 when not known
     */
    public static Front front(boolean raining, int rainTicks, int clearTicks, long sinceRainStarted,
                              long sinceRainEnded) {
        double hour = ThermalModel.MINECRAFT_TICKS_PER_HOUR;
        double span = FRONT_HOURS * hour;
        if (raining) {
            // Swinging round from where it blew ahead of the rain over its first hour.
            double t = sinceRainStarted < 0 ? 1.0 : Math.min(1.0, sinceRainStarted / hour);
            return new Front(Front.Kind.RAIN, between(AHEAD_FROM, RAIN_FROM, t), 1.0);
        }
        if (sinceRainEnded >= 0 && sinceRainEnded < span) {
            double t = Math.min(1.0, sinceRainEnded / hour);
            return new Front(Front.Kind.BEHIND, between(RAIN_FROM, BEHIND_FROM, t), 1.0 - sinceRainEnded / span);
        }
        if (clearTicks <= 0 && rainTicks >= 0 && rainTicks < span)
            return new Front(Front.Kind.AHEAD, AHEAD_FROM, 1.0 - rainTicks / span);
        return Front.NONE;
    }

    // ---- regions (BETA 1.0 Pilot 3) ----

    /** Regions bend the direction up to this many degrees either way... */
    public static final double REGION_BEND = 20.0;
    /** ...and through the day it swings this much. */
    public static final double HOURLY_SWING = 15.0;

    /** The wind at one spot on open, level ground. */
    public record Spot(double speedMs, double fromDegrees) {}

    /**
     * The wind at a spot on open, level ground at this moment: the day's
     * mean, made windier or calmer by the region it lies in (never more than
     * about a level either way), eased over from yesterday's after dawn and
     * gusting; from the direction of its climate belts, bent by the region,
     * swinging through the day, and turned by any front passing.
     */
    public static Spot at(long seed, long fullTime, double x, double z, Day today, Day yesterday, Belts belts,
                          Front front, WindSettings settings) {
        double hours = Math.floorMod(fullTime, DAY_TICKS) / ThermalModel.MINECRAFT_TICKS_PER_HOUR;
        Belts in = belts == null ? Belts.WESTERLIES : belts;
        double mean = regionalMean(seed, today, x, z, hours, settings);
        double from = today.fromDegrees(in) + bend(seed, today, x, z, hours, settings);
        if (yesterday != null && hours < EASE_HOURS) {
            double t = hours / EASE_HOURS;
            double smooth = t * t * (3.0 - 2.0 * t);
            double before = regionalMean(seed, yesterday, x, z, hours + 24.0, settings);
            mean = before + (mean - before) * smooth;
            from = between(yesterday.fromDegrees(in) + bend(seed, yesterday, x, z, hours + 24.0, settings), from,
                    smooth);
        }
        double absolute = fullTime / ThermalModel.MINECRAFT_TICKS_PER_HOUR;
        double speed = Math.max(0.0, mean * (1.0 + settings.gusts() * gust(seed, absolute)));
        from += HOURLY_SWING * Math.min(1.0, settings.direction().variation()) * gust(seed ^ 0x5714_6L, absolute);
        if (front != null && settings.direction().fronts() && front.strength() > 0.0)
            from = between(from, front.fromDegrees(),
                    FRONT_WEIGHT * Math.min(1.0, front.strength()) * in.westerlies());
        return new Spot(speed, normalize(from));
    }

    /**
     * A region's day, as a forecast gives it: its mean at midday, the level
     * that reads as, and where it blows from then.
     */
    public static Day dayAt(long seed, Day day, double x, double z, Belts belts, WindSettings settings) {
        double noon = 6.0;
        double mean = regionalMean(seed, day, x, z, noon, settings);
        Belts in = belts == null ? Belts.WESTERLIES : belts;
        double from = day.fromDegrees(in) + bend(seed, day, x, z, noon, settings);
        return new Day(day.day(), Level.of(mean, settings.speeds()), mean, new Directions(from, from, from));
    }

    /** The day's mean where the region makes it windier or calmer, kept within a level of the day's. */
    static double regionalMean(long seed, Day day, double x, double z, double hours, WindSettings settings) {
        WindSettings.Regions regions = settings.regions();
        if (!regions.enabled() || regions.strength() <= 0.0) return day.meanMs();
        double scaled = day.meanMs() * (1.0 + regions.strength() * field(seed, day, 0x2E610L, x, z, hours, regions));
        Level[] levels = Level.values();
        int at = day.level().ordinal();
        double lowest = settings.speeds().of(levels[Math.max(0, at - 1)]).from();
        double highest = settings.speeds().of(levels[Math.min(levels.length - 1, at + 1)]).to();
        return Math.max(lowest, Math.min(highest, scaled));
    }

    /** How far the region turns the wind, in degrees. */
    static double bend(long seed, Day day, double x, double z, double hours, WindSettings settings) {
        WindSettings.Regions regions = settings.regions();
        if (!regions.enabled()) return 0.0;
        return REGION_BEND * field(seed, day, 0xBE4DL, x, z, hours, regions);
    }

    /**
     * A day's regions: smooth noise between -1 and 1 about
     * {@code size-blocks} across, drifting downwind as the hours go by so a
     * windy patch passes over. Each day has its own.
     */
    static double field(long seed, Day day, long salt, double x, double z, double hours,
                        WindSettings.Regions regions) {
        double drift = regions.driftBlocksPerHour() * hours;
        double toward = Math.toRadians(day.fromDegrees() + 180.0);
        double u = (x - Math.sin(toward) * drift) / regions.sizeBlocks();
        double v = (z + Math.cos(toward) * drift) / regions.sizeBlocks();
        long daySeed = mix(seed ^ mix(day.day() * 0x9E3779B97F4A7C15L + salt));
        double noise = 0.75 * value(daySeed, u, v) + 0.25 * value(daySeed ^ 0x0C7A5EL, u * 2.3 + 17.0, v * 2.3 - 5.0);
        // Smooth noise keeps to the middle; spread it so some regions reach the edges.
        return Math.max(-1.0, Math.min(1.0, 1.6 * noise));
    }

    /** Value noise: a seeded value at each whole point, eased between them. */
    private static double value(long seed, double u, double v) {
        long iu = (long) Math.floor(u), iv = (long) Math.floor(v);
        double fu = u - iu, fv = v - iv;
        double su = fu * fu * (3.0 - 2.0 * fu), sv = fv * fv * (3.0 - 2.0 * fv);
        double a = lattice(seed, iu, iv), b = lattice(seed, iu + 1, iv);
        double c = lattice(seed, iu, iv + 1), d = lattice(seed, iu + 1, iv + 1);
        double top = a + (b - a) * su, bottom = c + (d - c) * su;
        return top + (bottom - top) * sv;
    }

    private static double lattice(long seed, long i, long j) {
        long hash = mix(seed ^ mix(i * 0xBF58476D1CE4E5B9L) ^ mix(j * 0x94D049BB133111EBL + 0x632BE5ABL));
        return (hash >>> 11) * 0x1.0p-53 * 2.0 - 1.0;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // ---- angles ----

    /** Degrees in 0 .. 360. */
    public static double normalize(double degrees) {
        if (!Double.isFinite(degrees)) return WESTERLIES_FROM;
        double d = degrees % 360.0;
        return d < 0.0 ? d + 360.0 : d;
    }

    /** The way from one direction to another the short way round, {@code t} of it. */
    public static double between(double from, double to, double t) {
        double turn = ((to - from) % 360.0 + 540.0) % 360.0 - 180.0;
        return normalize(from + turn * Math.max(0.0, Math.min(1.0, t)));
    }

    private static double east(double degrees) { return Math.sin(Math.toRadians(degrees)); }

    private static double south(double degrees) { return -Math.cos(Math.toRadians(degrees)); }

    private static double degrees(double east, double south) {
        return normalize(Math.toDegrees(Math.atan2(east, -south)));
    }

    private static double smoothstep(double from, double to, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - from) / (to - from)));
        return t * t * (3.0 - 2.0 * t);
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
        put(0.75, "forest", "birch_forest", "taiga", "snowy_taiga", "flower_forest", "cherry_grove", "grove",
                "dappled_forest");
        put(0.7, "swamp", "mangrove_swamp");
        // Dense woods.
        put(0.5, "dark_forest", "jungle", "sparse_jungle", "bamboo_jungle", "old_growth_pine_taiga",
                "old_growth_spruce_taiga", "old_growth_birch_forest", "pale_garden");
        // Caves, the Nether and the End have no wind.
        put(0.0, "dripstone_caves", "lush_caves", "deep_dark", "sulfur_caves", "nether_wastes", "crimson_forest",
                "warped_forest", "soul_sand_valley", "basalt_deltas", "the_end", "end_highlands", "end_midlands",
                "small_end_islands", "end_barrens", "the_void");
    }

    /** EZvac's own exposure for a biome id ({@code minecraft:jungle}), or null. */
    public static Double builtInExposure(String key) {
        return BUILT_IN.get(BiomeClimate.normalize(key));
    }
}
