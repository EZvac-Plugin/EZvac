package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.simulation.BiomeBlend;
import com.github.blade.hvac.simulation.BiomeClimate;
import com.github.blade.hvac.simulation.HumidityModel;
import com.github.blade.hvac.simulation.Precipitation;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.Seasons;
import com.github.blade.hvac.simulation.ThermalModel;
import com.github.blade.hvac.simulation.WeatherModel;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The outdoors at any spot, never force-loading chunks. From 2.1 the climate
 * at a spot is blended from the biomes around it (see {@link BiomeBlend}), so
 * walking from one biome into another ramps instead of jumping; from 2.1
 * Pilot 2 each biome has its own climate ({@link BiomeClimate}), it gets
 * colder with height, the ground holds deep spots steady, and the seasons move
 * it all up and down through the year.
 */
public final class OutdoorTemperatureService {
    /**
     * The outdoors at one spot, weather included.
     *
     * @param airF      outdoor air temperature
     * @param waterF    natural water temperature (lakes, the sea, unheated pools)
     * @param dewPointF outdoor moisture
     * @param windMs    outdoor wind, for apparent temperature
     * @param weather   Clear, Rain, Thunder or Snow; empty where weather never reaches
     */
    public record Outdoor(double airF, double waterF, double dewPointF, double windMs, String weather) {
        public double humidity() { return Psychrometrics.relativeHumidity(airF, dewPointF); }
        public double feelsLikeF() { return Psychrometrics.apparentTemperatureF(airF, humidity(), windMs); }
    }

    private final ClimateSettings climate;
    /** Null outside a running server (tests): every world then reads as clear. */
    private final WeatherService weather;
    /** Null outside a running server (tests): there are then no seasons. */
    private final SeasonService seasons;

    /**
     * A biome's climate only ever needs looking up once. Data-pack biomes all
     * share CUSTOM, so they are not cached.
     */
    private final Map<Biome, BiomeClimate.Climate> climateCache = new EnumMap<>(Biome.class);
    private final Map<Biome, Double> downfallCache = new EnumMap<>(Biome.class);
    /**
     * Blended climate per 4x4 column of biome cells, per world, then per
     * 4-block height cell. Biomes almost never change, so each cell is worked
     * out once; a chunk loading or unloading near it forgets it, because the
     * blend skips chunks that aren't loaded.
     */
    private final Map<UUID, Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<BiomeBlend.Blended>>> blended = new HashMap<>();
    private int blendedCells;
    /** The cache is dropped past this many cells, and simply refills. */
    private static final int MAXIMUM_BLENDED_CELLS = 400_000;
    /** Vanilla starts cooling biomes with height this far above sea level, which is where rain can turn to snow. */
    private static final int VANILLA_COOLING_ABOVE_SEA_LEVEL = 17;

    public OutdoorTemperatureService() {
        this(ClimateSettings.defaults(), null, null);
    }

    public OutdoorTemperatureService(ClimateSettings climate, WeatherService weather) {
        this(climate, weather, null);
    }

    public OutdoorTemperatureService(ClimateSettings climate, WeatherService weather, SeasonService seasons) {
        this.climate = Objects.requireNonNull(climate, "climate");
        this.weather = weather;
        this.seasons = seasons;
    }

    public ClimateSettings climate() { return climate; }

    /** The biome's climate: the server's own from config.yml, else the built-in one, else an estimate. */
    private BiomeClimate.Climate climateOf(Block block, Biome biome) {
        if (biome == Biome.CUSTOM) {
            BiomeClimate.Climate own = customKey(block)
                    .map(key -> climate.biomes().climate(key.toString())).orElse(null);
            return own != null ? own : BiomeClimate.estimate(block.getTemperature(), block.getHumidity());
        }
        BiomeClimate.Climate cached = climateCache.get(biome);
        if (cached != null) return cached;
        BiomeClimate.Climate found = climate.biomes().climate(biome.getKey().toString());
        if (found == null) found = BiomeClimate.estimate(block.getTemperature(), block.getHumidity());
        climateCache.put(biome, found);
        return found;
    }

    /** A data-pack biome's id, where Paper can tell it. */
    private static Optional<NamespacedKey> customKey(Block block) {
        try {
            return Optional.ofNullable(Bukkit.getUnsafe().getBiomeKey(block.getWorld(),
                    block.getX(), block.getY(), block.getZ()));
        } catch (RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    /**
     * Rain or snow at a block, as Minecraft makes it: a rainy biome snows
     * where vanilla cools it enough with height, high on a mountain.
     */
    private Precipitation precipitationAt(Block block, BiomeClimate.Climate biome) {
        if (biome.precipitation() != Precipitation.RAIN) return biome.precipitation();
        if (block.getY() <= block.getWorld().getSeaLevel() + VANILLA_COOLING_ABOVE_SEA_LEVEL)
            return Precipitation.RAIN;
        return block.getTemperature() < BiomeClimate.SNOW_BELOW ? Precipitation.SNOW : Precipitation.RAIN;
    }

    /** Vanilla's downfall, the biome value behind rain and grass colour. Data-pack biomes all share CUSTOM, so they are not cached. */
    private double downfall(Block block, Biome biome) {
        if (biome == Biome.CUSTOM) return block.getHumidity();
        Double cached = downfallCache.get(biome);
        if (cached != null) return cached;
        double value = block.getHumidity();
        downfallCache.put(biome, value);
        return value;
    }

    private WeatherModel.Effect effect(World world, BiomeBlend.Blended local) {
        WeatherModel.Conditions conditions = weather == null ? WeatherModel.Conditions.CLEAR : weather.conditions(world);
        return WeatherModel.effect(local, conditions, climate.weather());
    }

    /** The weather word for a spot: the kind of weather its surroundings mostly get. */
    private String weatherWord(World world, BiomeBlend.Blended local) {
        if (!climate.weather().enabled()) return "";
        WeatherModel.Conditions conditions = weather == null ? WeatherModel.Conditions.CLEAR : weather.conditions(world);
        return WeatherModel.word(local.dominant(), conditions);
    }

    /**
     * The outdoor climate at a block: blended from the biomes around it,
     * colder with height, and held steady deep underground. Readings between
     * sample cells are interpolated, so there are no 4-block steps either.
     * Never loads a chunk. The seasons are not in it: they change through the
     * year, so they are added when a temperature is read.
     */
    public BiomeBlend.Blended blendedAt(World world, int x, int y, int z) {
        BiomeBlend.Blended local = surroundingsAt(world, x, y, z);
        ClimateSettings.Altitude altitude = climate.altitude();
        local = local.warmer(-BiomeClimate.lapseF(y, altitude.fromY(), altitude.lapseFPerBlock()));
        return BiomeClimate.underground(local, groundFactor(world, x, y, z));
    }

    private BiomeBlend.Blended surroundingsAt(World world, int x, int y, int z) {
        int radius = climate.biomeBlend().radiusBlocks();
        if (radius <= 0) return exactAt(world, x, y, z);
        double u = (x - 1.5) / BiomeBlend.SAMPLE_SPACING;
        double v = (z - 1.5) / BiomeBlend.SAMPLE_SPACING;
        int cellX = (int) Math.floor(u), cellZ = (int) Math.floor(v), cellY = y >> 2;
        double tx = u - cellX, tz = v - cellZ;
        BiomeBlend.Blended near = BiomeBlend.lerp(cell(world, cellX, cellY, cellZ, radius),
                cell(world, cellX + 1, cellY, cellZ, radius), tx);
        BiomeBlend.Blended far = BiomeBlend.lerp(cell(world, cellX, cellY, cellZ + 1, radius),
                cell(world, cellX + 1, cellY, cellZ + 1, radius), tx);
        return BiomeBlend.lerp(near, far, tz);
    }

    /**
     * How much a spot takes on the ground's steady temperature: by how far it
     * is below the surface, or below sea level where the surface is higher.
     * Measuring from sea level keeps a tall house or a hill from counting as
     * underground. Only where the sun shines, and only in loaded chunks.
     */
    private double groundFactor(World world, int x, int y, int z) {
        ClimateSettings.Underground underground = climate.underground();
        if (!underground.enabled() || world.getEnvironment() != World.Environment.NORMAL) return 0.0;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return 0.0;
        int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        double depth = Math.min(world.getSeaLevel(), surface) - y;
        return BiomeClimate.groundFactor(depth, underground.startDepth(), underground.fullDepth());
    }

    /** The biome at exactly this block, as before 2.1 (and with blending off). */
    private BiomeBlend.Blended exactAt(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        Biome biome = block.getBiome();
        BiomeClimate.Climate own = climateOf(block, biome);
        return BiomeBlend.Blended.of(own.noonF(), own.midnightF(), downfall(block, biome),
                precipitationAt(block, own), own.seasonalF());
    }

    private BiomeBlend.Blended cell(World world, int cellX, int cellY, int cellZ, int radius) {
        Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<BiomeBlend.Blended>> columns =
                blended.computeIfAbsent(world.getUID(), ignored -> new Long2ObjectOpenHashMap<>());
        long column = HvacRegistry.cellKey(cellX, cellZ);
        Int2ObjectOpenHashMap<BiomeBlend.Blended> heights = columns.get(column);
        BiomeBlend.Blended cached = heights == null ? null : heights.get(cellY);
        if (cached != null) return cached;
        BiomeBlend.Blended computed = blendCell(world, cellX, cellY, cellZ, radius);
        if (blendedCells >= MAXIMUM_BLENDED_CELLS) {
            blended.clear();
            blendedCells = 0;
            columns = blended.computeIfAbsent(world.getUID(), ignored -> new Long2ObjectOpenHashMap<>());
            heights = null;
        }
        if (heights == null) {
            heights = new Int2ObjectOpenHashMap<>();
            columns.put(column, heights);
        }
        heights.put(cellY, computed);
        blendedCells++;
        return computed;
    }

    /** Samples every biome cell within the radius of this cell's centre, at its height. */
    private BiomeBlend.Blended blendCell(World world, int cellX, int cellY, int cellZ, int radius) {
        int centreX = cellX * BiomeBlend.SAMPLE_SPACING + 2;
        int centreZ = cellZ * BiomeBlend.SAMPLE_SPACING + 2;
        int y = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 1, cellY * BiomeBlend.SAMPLE_SPACING + 2));
        List<BiomeBlend.Sample> samples = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += BiomeBlend.SAMPLE_SPACING) {
            for (int dz = -radius; dz <= radius; dz += BiomeBlend.SAMPLE_SPACING) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius) continue;
                int x = centreX + dx, z = centreZ + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                Block block = world.getBlockAt(x, y, z);
                Biome biome = block.getBiome();
                BiomeClimate.Climate own = climateOf(block, biome);
                samples.add(new BiomeBlend.Sample(own.noonF(), own.midnightF(), downfall(block, biome),
                        precipitationAt(block, own), BiomeBlend.weight(distance, radius), own.seasonalF()));
            }
        }
        BiomeBlend.Blended result = BiomeBlend.blend(samples);
        return result != null ? result : exactAt(world, centreX, y, centreZ);
    }

    /**
     * Forgets blended cells a chunk could have taken part in, when it loads or
     * unloads: a cell blended while it was unloaded left it out.
     */
    public void forgetBlendAround(World world, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<BiomeBlend.Blended>> columns = blended.get(world.getUID());
        if (columns == null || columns.isEmpty()) return;
        int reach = climate.biomeBlend().radiusBlocks() + BiomeBlend.SAMPLE_SPACING;
        int fromX = Math.floorDiv(chunkX * 16 - reach, BiomeBlend.SAMPLE_SPACING);
        int toX = Math.floorDiv(chunkX * 16 + 15 + reach, BiomeBlend.SAMPLE_SPACING);
        int fromZ = Math.floorDiv(chunkZ * 16 - reach, BiomeBlend.SAMPLE_SPACING);
        int toZ = Math.floorDiv(chunkZ * 16 + 15 + reach, BiomeBlend.SAMPLE_SPACING);
        for (int cellX = fromX; cellX <= toX; cellX++)
            for (int cellZ = fromZ; cellZ <= toZ; cellZ++) {
                Int2ObjectOpenHashMap<BiomeBlend.Blended> removed = columns.remove(HvacRegistry.cellKey(cellX, cellZ));
                if (removed != null) blendedCells -= removed.size();
            }
    }

    /** Drops a world's blended cells, when it unloads. */
    public void forgetBlend(World world) {
        Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<BiomeBlend.Blended>> columns = blended.remove(world.getUID());
        if (columns != null) for (Int2ObjectOpenHashMap<BiomeBlend.Blended> heights : columns.values())
            blendedCells -= heights.size();
    }

    /**
     * What the sky is doing at a spot, for weather sensors: Clear, Rain,
     * Thunder or Snow, from the world's own weather. Unlike the HUD's weather
     * word it doesn't depend on EZvac's weather effects being switched on: a
     * sensor reports the sky, whatever the temperatures do. Clear where it
     * never rains, such as a desert.
     */
    public String skyAt(Location location) {
        if (location == null || location.getWorld() == null) return "Clear";
        World world = location.getWorld();
        Block block = location.getBlock();
        Precipitation precipitation = precipitationAt(block, climateOf(block, block.getBiome()));
        String word = WeatherModel.word(precipitation,
                WeatherModel.Conditions.settled(world.hasStorm(), world.isThundering()));
        return word.isEmpty() ? "Clear" : word;
    }

    /**
     * How far the seasons move this climate right now: up to its seasonal
     * swing, warmer in summer and colder in winter. Natural water lags the air
     * and moves less.
     */
    private double seasonShiftF(World world, double seasonalF, boolean water) {
        if (seasons == null || world == null || !(seasonalF > 0.0)) return 0.0;
        double strength = climate.seasons().effectiveStrength();
        if (strength <= 0.0) return 0.0;
        UUID id = world.getUID();
        double factor = water ? seasons.waterFactor(id) * Seasons.WATER_SHARE : seasons.airFactor(id);
        return seasonalF * strength * factor;
    }

    /** The climate as it is at this point of the year. */
    private BiomeBlend.Blended inSeason(World world, BiomeBlend.Blended local) {
        return local.warmer(seasonShiftF(world, local.seasonalF(), false));
    }

    /** How far the seasons move this spot's outdoor air right now, for /hvac season. */
    public double seasonShiftAt(Location location) {
        if (location == null || location.getWorld() == null) return 0.0;
        return seasonShiftF(location.getWorld(), blendedAt(location).seasonalF(), false);
    }

    /** Refreshes a thermostat's cached biome profile when its chunk is loaded; never loads it. */
    private void sample(Thermostat thermostat) {
        BlockKey position = thermostat.position();
        if (!position.isChunkLoaded()) return;
        World world = position.world();
        if (world == null) return;
        // Biomes don't change, so a thermostat's blend is refreshed every few seconds rather than every cycle;
        // that still picks up neighbouring chunks that weren't loaded the first time.
        long now = world.getGameTime();
        if (!thermostat.outdoorSampleDue(now, RESAMPLE_TICKS)) return;
        thermostat.setOutdoorClimate(blendedAt(world, position.x(), position.y(), position.z()));
        thermostat.markOutdoorSampled(now);
    }

    /** How often a thermostat's surroundings are sampled again. */
    private static final long RESAMPLE_TICKS = 200L;

    public double temperatureFor(Thermostat thermostat) {
        return temperatureFor(thermostat, true);
    }

    private double temperatureFor(Thermostat thermostat, boolean resample) {
        World world = thermostat.position().world();
        if (world == null) return thermostat.lastOutdoorF();
        if (resample) sample(thermostat);
        double value = air(world, thermostat.outdoorClimate());
        thermostat.setLastOutdoorF(value);
        return value;
    }

    /** The full outdoor picture at a thermostat, from its cached profile when its chunk is unloaded. */
    public Outdoor climateFor(Thermostat thermostat) {
        return climateFor(thermostat, true);
    }

    /**
     * The same, from the profile the thermostat already cached, without
     * sampling its biome again: the controller has just sampled it this cycle.
     */
    public Outdoor cachedClimateFor(Thermostat thermostat) {
        return climateFor(thermostat, false);
    }

    private Outdoor climateFor(Thermostat thermostat, boolean resample) {
        World world = thermostat.position().world();
        double air = temperatureFor(thermostat, resample);
        if (world == null) {
            double dew = Psychrometrics.dewPointF(air, 50.0);
            return new Outdoor(air, ThermalModel.naturalWaterTemperature(thermostat.outdoorNoonF(),
                    thermostat.outdoorMidnightF()), dew, climate.weather().windClearMs(), "");
        }
        return outdoor(world, thermostat.outdoorClimate(), air);
    }

    /**
     * What a thermostat's space drifts toward when nothing conditions it: the
     * outdoor air for a room, the biome's natural water for a pool. A heated
     * pool left alone cools toward lake temperature, not toward a hot noon.
     */
    public double driftBaselineFor(Thermostat thermostat) {
        return thermostat.pool() ? naturalWaterFor(thermostat) : temperatureFor(thermostat);
    }

    /** Natural water temperature at a thermostat's biome, refreshing its cached profile like temperatureFor. */
    public double naturalWaterFor(Thermostat thermostat) {
        temperatureFor(thermostat);
        World world = thermostat.position().world();
        BiomeBlend.Blended local = thermostat.outdoorClimate();
        WeatherModel.Effect effect = world == null ? WeatherModel.Effect.calm(climate.weather())
                : effect(world, local);
        return water(world, effect, local);
    }

    /** Natural water temperature at a location: steady, with no day/night swing. */
    public double naturalWaterAt(Location location) {
        if (location == null || location.getWorld() == null) return 60.0;
        BiomeBlend.Blended local = blendedAt(location);
        return water(location.getWorld(), effect(location.getWorld(), local), local);
    }

    public double temperatureAt(Location location) {
        if (location == null || location.getWorld() == null) return 70.0;
        return air(location.getWorld(), blendedAt(location));
    }

    private BiomeBlend.Blended blendedAt(Location location) {
        return blendedAt(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** The full outdoor picture at a location. */
    public Outdoor climateAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return new Outdoor(70.0, 60.0, Psychrometrics.dewPointF(70.0, 50.0), climate.weather().windClearMs(), "");
        }
        World world = location.getWorld();
        BiomeBlend.Blended local = blendedAt(location);
        return outdoor(world, local, air(world, local));
    }

    private Outdoor outdoor(World world, BiomeBlend.Blended local, double airF) {
        WeatherModel.Effect effect = effect(world, local);
        BiomeBlend.Blended now = inSeason(world, local);
        double dew = HumidityModel.outdoorDewPointF(now.noonF(), now.midnightF(), now.downfall(), effect, airF,
                climate.humidity());
        return new Outdoor(airF, water(world, effect, local), dew, effect.windMs(), weatherWord(world, local));
    }

    /** The outdoor air at this time of day and point of the year, weather included. */
    private double air(World world, BiomeBlend.Blended local) {
        WeatherModel.Effect effect = effect(world, local);
        BiomeBlend.Blended now = inSeason(world, local);
        return ThermalModel.outdoorTemperature(world.getTime(),
                effect.noonF(now.noonF(), now.midnightF()), effect.midnightF(now.noonF(), now.midnightF()));
    }

    /**
     * Natural water: it follows its own lagged share of the seasons, and only
     * a small share of the weather.
     */
    private double water(World world, WeatherModel.Effect effect, BiomeBlend.Blended local) {
        double season = seasonShiftF(world, local.seasonalF(), true);
        double value = ThermalModel.naturalWaterTemperature(local.noonF() + season, local.midnightF() + season)
                + effect.meanShiftF() * climate.weather().waterShare();
        return Math.max(ThermalModel.NATURAL_WATER_MINIMUM_F, Math.min(ThermalModel.NATURAL_WATER_MAXIMUM_F, value));
    }
}
