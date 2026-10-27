package com.github.blade.hvac.config;

import com.github.blade.hvac.simulation.BiomeClimate;
import com.github.blade.hvac.simulation.Seasons;
import com.github.blade.hvac.simulation.WindModel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Immutable, validated tuning for the daily wind (3.2), read from
 * {@code wind} in config.yml. Speeds are in m/s on open, level ground.
 *
 * @param enabled            false: the fixed {@code weather.wind-clear-ms} of 3.1, and wind changes nothing else
 * @param chances            how often each level comes up, as weights
 * @param speeds             each level's range of daily mean speeds
 * @param seasons            how much each season scales the chance of a Windy or Gale day
 * @param gusts              how far the wind swings around the day's mean, as a share of it
 * @param infiltrationPerMs  extra air exchange per m/s of wind outside a room
 * @param maximumInfiltration the most wind can multiply a room's air exchange by
 * @param dust               how much dust a strong wind lifts in dry biomes
 * @param smokeDispersal     share of the outdoor fine particles the wind thins out per m/s
 * @param pollenSpread       share the wind adds to the outdoor pollen per m/s
 * @param units              how speeds are shown
 * @param biomes             the server's own exposure per biome id, over EZvac's figures
 */
public record WindSettings(boolean enabled, Chances chances, Speeds speeds, SeasonWindiness seasons, double gusts,
                           double infiltrationPerMs, double maximumInfiltration, double dust, double smokeDispersal,
                           double pollenSpread, Units units, Map<String, Double> biomes) {
    public WindSettings {
        if (!Double.isFinite(gusts) || gusts < 0.0 || gusts > 0.9)
            throw new IllegalArgumentException("wind.gusts must be 0-0.9");
        requireNonNegative("wind.infiltration-per-ms", infiltrationPerMs);
        if (!Double.isFinite(maximumInfiltration) || maximumInfiltration < 1.0 || maximumInfiltration > 10.0)
            throw new IllegalArgumentException("wind.maximum-infiltration must be 1-10");
        requireNonNegative("wind.dust", dust);
        if (!Double.isFinite(smokeDispersal) || smokeDispersal < 0.0 || smokeDispersal > 1.0)
            throw new IllegalArgumentException("wind.smoke-dispersal must be 0-1");
        requireNonNegative("wind.pollen-spread", pollenSpread);
        units = units == null ? Units.AUTO : units;
        biomes = Map.copyOf(biomes == null ? Map.of() : biomes);
    }

    /** How speeds are shown: AUTO follows the temperature unit, km/h with °C and mph with °F. */
    public enum Units {
        AUTO, MS, KMH, MPH;

        static Units parse(String text) {
            if (text == null) return AUTO;
            return switch (text.trim().toLowerCase(Locale.ROOT).replace("/", "")) {
                case "auto" -> AUTO;
                case "ms", "m-s" -> MS;
                case "kmh", "km-h", "kph" -> KMH;
                case "mph" -> MPH;
                default -> throw new IllegalArgumentException("wind.units must be auto, ms, kmh or mph");
            };
        }
    }

    /** Weights for each level; they needn't add up to 100. */
    public record Chances(double calm, double breezy, double windy, double gale) {
        public Chances {
            requireNonNegative("wind.chances.calm", calm);
            requireNonNegative("wind.chances.breezy", breezy);
            requireNonNegative("wind.chances.windy", windy);
            requireNonNegative("wind.chances.gale", gale);
            if (!(calm + breezy + windy + gale > 0.0))
                throw new IllegalArgumentException("wind.chances must not all be 0");
        }

        public double of(WindModel.Level level) {
            return switch (level) {
                case CALM -> calm;
                case BREEZY -> breezy;
                case WINDY -> windy;
                case GALE -> gale;
            };
        }
    }

    /** A level's daily mean speeds, in m/s. */
    public record Range(double from, double to) {
        public Range {
            if (!Double.isFinite(from) || !Double.isFinite(to) || from < 0.0 || to < from || to > 60.0)
                throw new IllegalArgumentException("must be [from, to] with 0 <= from <= to <= 60");
        }
    }

    public record Speeds(Range calm, Range breezy, Range windy, Range gale) {
        public Range of(WindModel.Level level) {
            return switch (level) {
                case CALM -> calm;
                case BREEZY -> breezy;
                case WINDY -> windy;
                case GALE -> gale;
            };
        }
    }

    /** Multipliers on the chance of a Windy or Gale day; Calm takes up the difference. */
    public record SeasonWindiness(double spring, double summer, double autumn, double winter) {
        public SeasonWindiness {
            requireNonNegative("wind.seasons.spring", spring);
            requireNonNegative("wind.seasons.summer", summer);
            requireNonNegative("wind.seasons.autumn", autumn);
            requireNonNegative("wind.seasons.winter", winter);
        }

        public double of(Seasons.Season season) {
            if (season == null) return 1.0;
            return switch (season) {
                case SPRING -> spring;
                case SUMMER -> summer;
                case AUTUMN -> autumn;
                case WINTER -> winter;
            };
        }
    }

    /** A biome's exposure: the server's own, else EZvac's, else 1. */
    public double exposure(String key) {
        String normalized = BiomeClimate.normalize(key);
        Double own = biomes.get(normalized);
        if (own != null) return own;
        Double builtIn = WindModel.builtInExposure(normalized);
        return builtIn != null ? builtIn : 1.0;
    }

    /** The extra air exchange a room gets from this much wind outside it. */
    public double infiltrationFactor(double windMs) {
        if (!enabled || !(windMs > 0.0)) return 1.0;
        return Math.min(maximumInfiltration, 1.0 + infiltrationPerMs * windMs);
    }

    public static WindSettings defaults() {
        return new WindSettings(true, new Chances(40, 35, 18, 7),
                new Speeds(new Range(0, 2), new Range(3, 6), new Range(7, 12), new Range(14, 20)),
                new SeasonWindiness(1.3, 0.7, 1.3, 1.0), 0.25, 0.05, 2.0, 1.5, 0.03, 0.06, Units.AUTO, Map.of());
    }

    /**
     * Reads {@code wind}. As with the rest of the climate settings, a value
     * out of range falls back to its section's defaults with a warning.
     */
    public static WindSettings load(FileConfiguration config, Consumer<String> warn) {
        WindSettings d = defaults();
        String root = "wind.";
        Chances chances = section("wind.chances", d.chances, warn, () -> new Chances(
                config.getDouble(root + "chances.calm", d.chances.calm()),
                config.getDouble(root + "chances.breezy", d.chances.breezy()),
                config.getDouble(root + "chances.windy", d.chances.windy()),
                config.getDouble(root + "chances.gale", d.chances.gale())));
        Speeds speeds = section("wind.speeds-ms", d.speeds, warn, () -> new Speeds(
                range(config, root + "speeds-ms.calm", d.speeds.calm()),
                range(config, root + "speeds-ms.breezy", d.speeds.breezy()),
                range(config, root + "speeds-ms.windy", d.speeds.windy()),
                range(config, root + "speeds-ms.gale", d.speeds.gale())));
        SeasonWindiness seasons = section("wind.seasons", d.seasons, warn, () -> new SeasonWindiness(
                config.getDouble(root + "seasons.spring", d.seasons.spring()),
                config.getDouble(root + "seasons.summer", d.seasons.summer()),
                config.getDouble(root + "seasons.autumn", d.seasons.autumn()),
                config.getDouble(root + "seasons.winter", d.seasons.winter())));
        Units units = section("wind.units", d.units, warn, () -> Units.parse(config.getString(root + "units")));
        boolean enabled = config.getBoolean(root + "enabled", d.enabled);
        Map<String, Double> biomes = biomes(config.getConfigurationSection(root + "biomes"), warn);
        return section("wind", new WindSettings(enabled, chances, speeds, seasons, d.gusts, d.infiltrationPerMs,
                d.maximumInfiltration, d.dust, d.smokeDispersal, d.pollenSpread, units, biomes), warn,
                () -> new WindSettings(enabled, chances, speeds, seasons,
                        config.getDouble(root + "gusts", d.gusts),
                        config.getDouble(root + "infiltration-per-ms", d.infiltrationPerMs),
                        config.getDouble(root + "maximum-infiltration", d.maximumInfiltration),
                        config.getDouble(root + "dust", d.dust),
                        config.getDouble(root + "smoke-dispersal", d.smokeDispersal),
                        config.getDouble(root + "pollen-spread", d.pollenSpread),
                        units, biomes));
    }

    /** {@code [3, 6]}; missing keeps the default. */
    private static Range range(FileConfiguration config, String path, Range fallback) {
        if (!config.isSet(path)) return fallback;
        List<Double> values = config.getDoubleList(path);
        if (values.size() != 2) throw new IllegalArgumentException(path + " must be [from, to], such as [3, 6]");
        try {
            return new Range(values.get(0), values.get(1));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(path + " " + invalid.getMessage());
        }
    }

    /** The server's own exposure per biome: {@code minecraft:plains: 1.2}. A broken entry is skipped. */
    private static Map<String, Double> biomes(ConfigurationSection section, Consumer<String> warn) {
        if (section == null) return Map.of();
        Map<String, Double> overrides = new LinkedHashMap<>();
        for (String name : section.getKeys(false)) {
            double value = section.getDouble(name, Double.NaN);
            if (!section.isDouble(name) && !section.isInt(name) || !Double.isFinite(value) || value < 0.0
                    || value > 3.0) {
                warn.accept("wind.biomes." + name + " must be a number from 0 to 3, so it keeps its built-in"
                        + " exposure until config.yml is fixed.");
                continue;
            }
            overrides.put(BiomeClimate.normalize(name), value);
        }
        return overrides;
    }

    private static <T> T section(String name, T fallback, Consumer<String> warn, Supplier<T> reader) {
        try {
            return reader.get();
        } catch (IllegalArgumentException invalid) {
            warn.accept(invalid.getMessage() + ", so " + name + " uses its defaults until config.yml is fixed.");
            return fallback;
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0)
            throw new IllegalArgumentException(name + " must be non-negative and finite");
    }
}
