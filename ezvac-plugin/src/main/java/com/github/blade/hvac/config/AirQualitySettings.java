package com.github.blade.hvac.config;

import com.github.blade.hvac.simulation.AirQualityIndex;
import com.github.blade.hvac.simulation.BiomeClimate;
import com.github.blade.hvac.simulation.OutdoorAir;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Immutable, validated tuning for air quality and air purifiers (3.2), read
 * from {@code air-quality} in config.yml. Rates are per in-game hour.
 *
 * @param enabled        false hides every air reading, and purifiers sit idle
 * @param referenceCells the space size sources and purifiers are quoted for; larger spaces dilute both
 * @param settling       share of each kind of particle that settles out of still air per hour
 * @param systemFilter   share a system's own filter takes out per hour while its equipment runs
 * @param biomes         the server's own outdoor air per biome id, over EZvac's figures
 */
public record AirQualitySettings(boolean enabled, int referenceCells, Rates settling, Rates systemFilter,
                                 Sources sources, PollenLevels pollenLevels, Purifier purifier,
                                 Map<String, OutdoorAir.Baseline> biomes) {
    public AirQualitySettings {
        if (referenceCells < 1) throw new IllegalArgumentException("air-quality.reference-cells must be positive");
        biomes = Map.copyOf(biomes == null ? Map.of() : biomes);
    }

    /** One rate for each kind of particle. */
    public record Rates(double fine, double coarse, double pollen) {
        public Rates {
            requireNonNegative("fine", fine);
            requireNonNegative("coarse", coarse);
            requireNonNegative("pollen", pollen);
        }
    }

    /** What one burning block adds per hour to a space of the reference size, in µg/m³. */
    public record Source(double fine, double coarse) {
        public Source {
            requireNonNegative("fine", fine);
            requireNonNegative("coarse", coarse);
        }
    }

    /**
     * @param campfire a lit campfire or soul campfire
     * @param fire     fire or soul fire
     * @param lava     lava bordering the space
     * @param furnace  a lit furnace, smoker or blast furnace
     * @param candle   a lit candle, counted once per block
     */
    public record Sources(Source campfire, Source fire, Source lava, Source furnace, Source candle) {}

    /** Grains/m³ where pollen turns Moderate, High and Very high. */
    public record PollenLevels(double moderate, double high, double veryHigh) {
        public PollenLevels {
            if (!Double.isFinite(moderate) || !(moderate > 0.0) || !Double.isFinite(high) || !Double.isFinite(veryHigh)
                    || high <= moderate || veryHigh <= high)
                throw new IllegalArgumentException("air-quality.pollen-levels must rise: moderate < high < very-high");
        }
    }

    /**
     * @param range           airflow reach of a purifier's own zone, in blocks
     * @param maximumCells    cap on the cells one zone may hold
     * @param rate            share of each kind one purifier takes out per hour on High, in a space of the
     *                        reference size
     * @param speeds          how hard each fan speed cleans, against {@code rate}
     * @param auto            when Auto steps up a speed
     * @param filterCapacity  µg/m³ at the reference size a filter holds: what it captures, pollen weighted
     * @param maximumLifeDays in-game days a filter lasts running nonstop on High in perfectly clean air
     * @param pollenWeight    how much a grain/m³ of pollen fills the filter against a µg/m³ of particles
     */
    public record Purifier(int range, int maximumCells, Rates rate, Speeds speeds, Auto auto, double filterCapacity,
                           double maximumLifeDays, double pollenWeight) {
        public Purifier {
            if (range < 1 || range > 64) throw new IllegalArgumentException("air-quality.purifier.range must be 1-64");
            if (maximumCells < 1)
                throw new IllegalArgumentException("air-quality.purifier.maximum-cells must be positive");
            requirePositive("air-quality.purifier.filter-capacity", filterCapacity);
            requirePositive("air-quality.purifier.maximum-life-days", maximumLifeDays);
            requireNonNegative("air-quality.purifier.pollen-weight", pollenWeight);
        }
    }

    /** A purifier's fan speeds, as shares of its full rate: they must rise from Low to High. */
    public record Speeds(double low, double medium, double high) {
        public Speeds {
            if (!(low > 0.0) || !(medium >= low) || !(high >= medium) || !Double.isFinite(high) || high > 2.0)
                throw new IllegalArgumentException("air-quality.purifier.speeds must rise: 0 < low <= medium <= high <= 2");
        }
    }

    /**
     * When Auto steps up: past an AQI, or at a pollen level (null: never for
     * pollen). It steps down once the AQI is {@code hysteresis} under the step
     * and the pollen a tenth under its level.
     */
    public record Auto(int mediumAboveAqi, int highAboveAqi, AirQualityIndex.PollenLevel mediumPollen,
                       AirQualityIndex.PollenLevel highPollen, int hysteresis) {
        public Auto {
            if (mediumAboveAqi < 0 || highAboveAqi < mediumAboveAqi || highAboveAqi > AirQualityIndex.MAXIMUM)
                throw new IllegalArgumentException("air-quality.purifier.auto: 0 <= medium-above-aqi <= high-above-aqi"
                        + " <= 500");
            if (hysteresis < 0) throw new IllegalArgumentException("air-quality.purifier.auto.hysteresis must not be"
                    + " negative");
        }
    }

    /** A biome's outdoor air: the server's own, else EZvac's, else the default countryside. */
    public OutdoorAir.Baseline baseline(String key) {
        String normalized = BiomeClimate.normalize(key);
        OutdoorAir.Baseline own = biomes.get(normalized);
        if (own != null) return own;
        OutdoorAir.Baseline builtIn = OutdoorAir.builtIn(normalized);
        return builtIn != null ? builtIn : OutdoorAir.Baseline.DEFAULT;
    }

    public static AirQualitySettings defaults() {
        return new AirQualitySettings(true, 1_500,
                new Rates(0.1, 0.3, 0.5),
                new Rates(0.15, 0.5, 0.8),
                new Sources(new Source(20.0, 8.0), new Source(25.0, 10.0), new Source(6.0, 4.0),
                        new Source(4.0, 2.0), new Source(1.0, 0.0)),
                new PollenLevels(20.0, 80.0, 200.0),
                new Purifier(30, 50_000, new Rates(2.5, 2.5, 3.0), new Speeds(0.35, 0.65, 1.0),
                        new Auto(50, 100, AirQualityIndex.PollenLevel.MODERATE, AirQualityIndex.PollenLevel.HIGH, 20),
                        2_400.0, 60.0, 0.5),
                Map.of());
    }

    /**
     * Reads {@code air-quality}. As with the rest of the climate settings, a
     * value out of range falls back to its section's defaults with a warning.
     */
    public static AirQualitySettings load(FileConfiguration config, Consumer<String> warn) {
        AirQualitySettings d = defaults();
        String root = "air-quality.";
        Purifier p = d.purifier;
        boolean enabled = config.getBoolean(root + "enabled", d.enabled);
        int referenceCells = section("air-quality.reference-cells", d.referenceCells, warn, () -> {
            int value = config.getInt(root + "reference-cells", d.referenceCells);
            if (value < 1) throw new IllegalArgumentException("air-quality.reference-cells must be positive");
            return value;
        });
        return new AirQualitySettings(enabled, referenceCells,
                section("air-quality.settling", d.settling, warn, () -> rates(config, root + "settling", d.settling)),
                section("air-quality.system-filter", d.systemFilter, warn,
                        () -> rates(config, root + "system-filter", d.systemFilter)),
                section("air-quality.sources", d.sources, warn, () -> new Sources(
                        source(config, root + "sources.campfire", d.sources.campfire()),
                        source(config, root + "sources.fire", d.sources.fire()),
                        source(config, root + "sources.lava", d.sources.lava()),
                        source(config, root + "sources.furnace", d.sources.furnace()),
                        source(config, root + "sources.candle", d.sources.candle()))),
                section("air-quality.pollen-levels", d.pollenLevels, warn, () -> new PollenLevels(
                        config.getDouble(root + "pollen-levels.moderate", d.pollenLevels.moderate()),
                        config.getDouble(root + "pollen-levels.high", d.pollenLevels.high()),
                        config.getDouble(root + "pollen-levels.very-high", d.pollenLevels.veryHigh()))),
                section("air-quality.purifier", p, warn, () -> new Purifier(
                        config.getInt(root + "purifier.range", p.range()),
                        config.getInt(root + "purifier.maximum-cells", p.maximumCells()),
                        rates(config, root + "purifier.rate", p.rate()),
                        new Speeds(config.getDouble(root + "purifier.speeds.low", p.speeds().low()),
                                config.getDouble(root + "purifier.speeds.medium", p.speeds().medium()),
                                config.getDouble(root + "purifier.speeds.high", p.speeds().high())),
                        new Auto(config.getInt(root + "purifier.auto.medium-above-aqi", p.auto().mediumAboveAqi()),
                                config.getInt(root + "purifier.auto.high-above-aqi", p.auto().highAboveAqi()),
                                pollenLevel("medium-pollen", config.getString(root + "purifier.auto.medium-pollen"),
                                        p.auto().mediumPollen()),
                                pollenLevel("high-pollen", config.getString(root + "purifier.auto.high-pollen"),
                                        p.auto().highPollen()),
                                config.getInt(root + "purifier.auto.hysteresis", p.auto().hysteresis())),
                        config.getDouble(root + "purifier.filter-capacity", p.filterCapacity()),
                        config.getDouble(root + "purifier.maximum-life-days", p.maximumLifeDays()),
                        config.getDouble(root + "purifier.pollen-weight", p.pollenWeight()))),
                biomes(config.getConfigurationSection(root + "biomes"), warn));
    }

    private static Rates rates(FileConfiguration config, String path, Rates fallback) {
        try {
            return new Rates(config.getDouble(path + ".fine", fallback.fine()),
                    config.getDouble(path + ".coarse", fallback.coarse()),
                    config.getDouble(path + ".pollen", fallback.pollen()));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(path + "." + invalid.getMessage());
        }
    }

    private static Source source(FileConfiguration config, String path, Source fallback) {
        try {
            return new Source(config.getDouble(path + ".fine", fallback.fine()),
                    config.getDouble(path + ".coarse", fallback.coarse()));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(path + "." + invalid.getMessage());
        }
    }

    /** {@code high}, {@code very-high}, or {@code never}; missing keeps the default. */
    private static AirQualityIndex.PollenLevel pollenLevel(String key, String text,
                                                           AirQualityIndex.PollenLevel fallback) {
        if (text == null) return fallback;
        if (text.trim().equalsIgnoreCase("never") || text.trim().equalsIgnoreCase("false")) return null;
        AirQualityIndex.PollenLevel level = AirQualityIndex.PollenLevel.parse(text);
        if (level == null || level == AirQualityIndex.PollenLevel.LOW)
            throw new IllegalArgumentException("air-quality.purifier.auto." + key + " must be moderate, high,"
                    + " very-high or never");
        return level;
    }

    /**
     * The server's own outdoor air per biome. A value left out comes from
     * EZvac's figures, so {@code minecraft:plains: { pollen: 40 }} only calms its pollen.
     */
    private static Map<String, OutdoorAir.Baseline> biomes(ConfigurationSection section, Consumer<String> warn) {
        if (section == null) return Map.of();
        Map<String, OutdoorAir.Baseline> overrides = new LinkedHashMap<>();
        for (String name : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(name);
            if (entry == null) {
                warn.accept("air-quality.biomes." + name + " must be a section such as { fine: 10, coarse: 20,"
                        + " pollen: 50 }, so it keeps its built-in air until config.yml is fixed.");
                continue;
            }
            String key = BiomeClimate.normalize(name);
            OutdoorAir.Baseline base = OutdoorAir.builtIn(key);
            if (base == null) base = OutdoorAir.Baseline.DEFAULT;
            try {
                overrides.put(key, new OutdoorAir.Baseline(entry.getDouble("fine", base.fine()),
                        entry.getDouble("coarse", base.coarse()), entry.getDouble("pollen", base.pollen())));
            } catch (IllegalArgumentException invalid) {
                warn.accept("air-quality.biomes." + name + ": " + invalid.getMessage()
                        + ", so it keeps its built-in air until config.yml is fixed.");
            }
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

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0)
            throw new IllegalArgumentException(name + " must be positive and finite");
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0)
            throw new IllegalArgumentException(name + " must be non-negative and finite");
    }
}
