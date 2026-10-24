package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.simulation.BiomeBlend;
import com.github.blade.hvac.simulation.HumidityModel;
import com.github.blade.hvac.simulation.Precipitation;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.ThermalModel;
import com.github.blade.hvac.simulation.WeatherModel;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Biome profile sampling that never force-loads chunks. From 2.1 the climate
 * at a spot is blended from the biomes around it (see {@link BiomeBlend}), so
 * walking from one biome into another ramps instead of jumping.
 */
public final class OutdoorTemperatureService {
    private enum Profile {
        SNOWY(25, 5, Precipitation.SNOW), COLD(45, 28, Precipitation.RAIN),
        TEMPERATE(72, 52, Precipitation.RAIN), WARM(85, 68, Precipitation.RAIN),
        HOT(100, 78, Precipitation.NONE), NETHER(112, 112, Precipitation.NONE), END(30, 30, Precipitation.NONE);
        final double noon;
        final double midnight;
        final Precipitation precipitation;
        Profile(double noon, double midnight, Precipitation precipitation) {
            this.noon = noon;
            this.midnight = midnight;
            this.precipitation = precipitation;
        }
    }

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

    /**
     * Classification is pure string matching over a biome's name, and it ran
     * per thermostat per cycle and per thermometer per refresh. Biome is an
     * enum, so each one only ever needs classifying once.
     */
    private final Map<Biome, Profile> profileCache = new EnumMap<>(Biome.class);
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

    public OutdoorTemperatureService() {
        this(ClimateSettings.defaults(), null);
    }

    public OutdoorTemperatureService(ClimateSettings climate, WeatherService weather) {
        this.climate = Objects.requireNonNull(climate, "climate");
        this.weather = weather;
    }

    public ClimateSettings climate() { return climate; }

    private Profile cachedProfile(Biome biome) {
        Profile cached = profileCache.get(biome);
        if (cached != null) return cached;
        Profile computed = profile(biome);
        profileCache.put(biome, computed);
        return computed;
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
     * The outdoor climate at a block, blended from the biomes around it.
     * Readings between sample cells are interpolated, so there are no 4-block
     * steps either. Never loads a chunk.
     */
    public BiomeBlend.Blended blendedAt(World world, int x, int y, int z) {
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

    /** The biome at exactly this block, as before 2.1 (and with blending off). */
    private BiomeBlend.Blended exactAt(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        Biome biome = block.getBiome();
        Profile profile = cachedProfile(biome);
        return BiomeBlend.Blended.of(profile.noon, profile.midnight, downfall(block, biome), profile.precipitation);
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
                Profile profile = cachedProfile(biome);
                samples.add(new BiomeBlend.Sample(profile.noon, profile.midnight, downfall(block, biome),
                        profile.precipitation, BiomeBlend.weight(distance, radius)));
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
        Precipitation precipitation = cachedProfile(location.getBlock().getBiome()).precipitation;
        String word = WeatherModel.word(precipitation,
                WeatherModel.Conditions.settled(world.hasStorm(), world.isThundering()));
        return word.isEmpty() ? "Clear" : word;
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
        WeatherModel.Effect effect = effect(world, thermostat.outdoorClimate());
        double value = air(world, effect, thermostat.outdoorNoonF(), thermostat.outdoorMidnightF());
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
        WeatherModel.Effect effect = world == null ? WeatherModel.Effect.calm(climate.weather())
                : effect(world, thermostat.outdoorClimate());
        return water(effect, thermostat.outdoorNoonF(), thermostat.outdoorMidnightF());
    }

    /** Natural water temperature at a location: steady, with no day/night swing. */
    public double naturalWaterAt(Location location) {
        if (location == null || location.getWorld() == null) return 60.0;
        BiomeBlend.Blended local = blendedAt(location);
        return water(effect(location.getWorld(), local), local.noonF(), local.midnightF());
    }

    public double temperatureAt(Location location) {
        if (location == null || location.getWorld() == null) return 70.0;
        BiomeBlend.Blended local = blendedAt(location);
        return air(location.getWorld(), effect(location.getWorld(), local), local.noonF(), local.midnightF());
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
        double air = air(world, effect(world, local), local.noonF(), local.midnightF());
        return outdoor(world, local, air);
    }

    private Outdoor outdoor(World world, BiomeBlend.Blended local, double airF) {
        WeatherModel.Effect effect = effect(world, local);
        double dew = HumidityModel.outdoorDewPointF(local.noonF(), local.midnightF(), local.downfall(), effect, airF,
                climate.humidity());
        return new Outdoor(airF, water(effect, local.noonF(), local.midnightF()), dew, effect.windMs(),
                weatherWord(world, local));
    }

    private static double air(World world, WeatherModel.Effect effect, double noonF, double midnightF) {
        return ThermalModel.outdoorTemperature(world.getTime(),
                effect.noonF(noonF, midnightF), effect.midnightF(noonF, midnightF));
    }

    /** Water barely reacts to weather: it follows only a small share of the air's shift. */
    private double water(WeatherModel.Effect effect, double noonF, double midnightF) {
        double value = ThermalModel.naturalWaterTemperature(noonF, midnightF)
                + effect.meanShiftF() * climate.weather().waterShare();
        return Math.max(ThermalModel.NATURAL_WATER_MINIMUM_F, Math.min(ThermalModel.NATURAL_WATER_MAXIMUM_F, value));
    }

    private static Profile profile(Biome biome) {
        String name = biome.name().toUpperCase(Locale.ROOT);
        if (name.contains("NETHER") || name.contains("BASALT") || name.contains("CRIMSON")
                || name.contains("WARPED") || name.contains("SOUL_SAND")) return Profile.NETHER;
        if (name.contains("END")) return Profile.END;
        if (name.contains("FROZEN") || name.contains("SNOWY") || name.contains("ICE")
                || name.contains("COLD_OCEAN")) return Profile.SNOWY;
        if (name.contains("DESERT") || name.contains("BADLANDS") || name.contains("SAVANNA"))
            return Profile.HOT;
        if (name.contains("JUNGLE") || name.contains("WARM_OCEAN") || name.contains("BAMBOO"))
            return Profile.WARM;
        if (name.contains("TAIGA") || name.contains("WINDSWEPT") || name.contains("GROVE")
                || name.contains("PEAKS") || name.contains("STONY_SHORE")) return Profile.COLD;
        return Profile.TEMPERATE;
    }
}
