package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.simulation.AirQualityModel;
import com.github.blade.hvac.simulation.BiomeBlend;
import com.github.blade.hvac.simulation.BiomeClimate;
import com.github.blade.hvac.simulation.HumidityModel;
import com.github.blade.hvac.simulation.OutdoorAir;
import com.github.blade.hvac.simulation.Precipitation;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.Seasons;
import com.github.blade.hvac.simulation.SnowEdge;
import com.github.blade.hvac.simulation.ThermalModel;
import com.github.blade.hvac.simulation.WeatherModel;
import com.github.blade.hvac.simulation.WindModel;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The outdoors at any spot, never force-loading chunks. From 2.1 the climate
 * at a spot is blended from the biomes around it (see {@link BiomeBlend}), so
 * walking from one biome into another ramps instead of jumping, each biome
 * has its own climate ({@link BiomeClimate}), it gets
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
     * @param air       what is in the outdoor air right now (3.2)
     * @param windFromDegrees where the wind blows from (BETA 1.0 Pilot 3); NaN where there is none
     */
    public record Outdoor(double airF, double waterF, double dewPointF, double windMs, String weather,
                          AirQualityModel.Air air, double windFromDegrees) {
        public Outdoor {
            air = air == null || !air.known() ? OutdoorAir.now(OutdoorAir.Baseline.DEFAULT,
                    OutdoorAir.SUMMER_POLLEN, 0.0) : air;
        }

        /** As in 3.2, with no direction to the wind. */
        public Outdoor(double airF, double waterF, double dewPointF, double windMs, String weather,
                       AirQualityModel.Air air) {
            this(airF, waterF, dewPointF, windMs, weather, air, Double.NaN);
        }

        /** Without air: the default countryside's. */
        public Outdoor(double airF, double waterF, double dewPointF, double windMs, String weather) {
            this(airF, waterF, dewPointF, windMs, weather, null);
        }

        public double humidity() { return Psychrometrics.relativeHumidity(airF, dewPointF); }
        public double feelsLikeF() { return Psychrometrics.apparentTemperatureF(airF, humidity(), windMs); }
    }

    private final ClimateSettings climate;
    /** Null outside a running server (tests): every world then reads as clear. */
    private final WeatherService weather;
    /** Null outside a running server (tests): there are then no seasons. */
    private final SeasonService seasons;
    /** Null outside a running server (tests): the wind is then the fixed weather.wind-clear-ms. */
    private final WindService wind;

    /**
     * A biome's climate only ever needs looking up once. On 1.21.1 data-pack
     * biomes all share CUSTOM, so they are not cached; from 26.1 each is its
     * own Biome. Not EnumMaps: from 26.1 Biome is no longer an enum.
     */
    private final Map<Biome, BiomeClimate.Climate> climateCache = new HashMap<>();
    private final Map<Biome, Double> downfallCache = new HashMap<>();
    private final Map<Biome, OutdoorAir.Baseline> airCache = new HashMap<>();
    private final Map<Biome, Double> windCache = new HashMap<>();
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
        this(climate, weather, seasons, null);
    }

    public OutdoorTemperatureService(ClimateSettings climate, WeatherService weather, SeasonService seasons,
                                     WindService wind) {
        this.climate = Objects.requireNonNull(climate, "climate");
        this.weather = weather;
        this.seasons = seasons;
        this.wind = wind;
    }

    /** Null outside a running server. */
    public WindService wind() { return wind; }

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

    /** The biome's outdoor air (3.2): the server's own from config.yml, else EZvac's, else the default. */
    private OutdoorAir.Baseline airOf(Block block, Biome biome) {
        if (biome == Biome.CUSTOM)
            return customKey(block).map(key -> climate.airQuality().baseline(key.toString()))
                    .orElse(OutdoorAir.Baseline.DEFAULT);
        OutdoorAir.Baseline cached = airCache.get(biome);
        if (cached != null) return cached;
        OutdoorAir.Baseline found = climate.airQuality().baseline(biome.getKey().toString());
        airCache.put(biome, found);
        return found;
    }

    /** How much of the wind reaches the biome (3.2): the server's own figure, else EZvac's, else fully. */
    private double windOf(Block block, Biome biome) {
        if (biome == Biome.CUSTOM)
            return customKey(block).map(key -> climate.wind().exposure(key.toString())).orElse(1.0);
        Double cached = windCache.get(biome);
        if (cached != null) return cached;
        double found = climate.wind().exposure(biome.getKey().toString());
        windCache.put(biome, found);
        return found;
    }

    /**
     * A data-pack biome's id, where Paper can tell it. 1.21.1 only knows it
     * through its unsafe values. 26.1 removed those: there a data-pack biome
     * is its own Biome, with its own key, and never reaches this.
     */
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

    /** Vanilla's downfall, the biome value behind rain and grass colour. CUSTOM (1.21.1's data-pack biomes) is not cached. */
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
        if (wind == null || !wind.enabled()) return WeatherModel.effect(local, conditions, climate.weather());
        // The day's wind, rain and thunder adding to it, as much of it as reaches this spot (3.2).
        return WeatherModel.effect(local, conditions, climate.weather(), wind.speedMs(world))
                .windScaled(WindService.hasWind(world) ? local.windExposure() : 0.0);
    }

    /** The same with a spot's own wind (BETA 1.0 Pilot 3): its region's, rather than the world's day. */
    private WeatherModel.Effect effect(World world, BiomeBlend.Blended local, WindModel.Spot spot) {
        WeatherModel.Conditions conditions = weather == null ? WeatherModel.Conditions.CLEAR : weather.conditions(world);
        if (wind == null || !wind.enabled() || spot == null) return effect(world, local);
        return WeatherModel.effect(local, conditions, climate.weather(), spot.speedMs())
                .windScaled(WindService.hasWind(world) ? local.windExposure() : 0.0);
    }

    /** How wet the weather is in a world right now, 0 clear .. 1 raining, eased (BETA 1.0 Pilot 3, stations). */
    public double wetness(World world) {
        return weather == null || world == null ? 0.0 : weather.conditions(world).wetness();
    }

    /** The wind at a spot, or null outside a running server or with wind off. */
    private WindModel.Spot windAt(World world, double x, double z) {
        return wind == null || !wind.enabled() ? null : wind.at(world, x, z);
    }

    /**
     * The open air at a spot anywhere in a world, explored or not (BETA 1.0
     * Pilot 3, for maps): its biomes' air read from the seed, the season's
     * pollen, the rain and the wind there. Never loads a chunk. Null with air
     * quality switched off, outside a running server, or outside the overworld.
     */
    public AirQualityModel.Air openAirAt(World world, double x, double z) {
        if (world == null || !climate.airQuality().enabled() || wind == null || wind.regions() == null
                || world.getEnvironment() != World.Environment.NORMAL) return null;
        RegionClimate regions = wind.regions();
        double pollen = !climate.airQuality().pollen() ? 0.0
                : seasons == null ? OutdoorAir.SUMMER_POLLEN : seasons.pollenFactor(world.getUID());
        WindModel.Spot spot = windAt(world, x, z);
        double windMs = spot == null ? climate.weather().windClearMs()
                : spot.speedMs() * regions.exposure(world, x, z);
        double downfall = regions.downfall(world, x, z);
        // Rain only falls where the ground gets any.
        double wet = downfall > 0.0 ? wetness(world) : 0.0;
        AirQualityModel.Air open = OutdoorAir.now(regions.air(world, x, z), pollen, wet, windMs, downfall,
                climate.wind());
        return withSmoke(open, world, x, Double.NaN, z, windMs, spot == null ? Double.NaN : spot.fromDegrees(), wet);
    }

    private OutdoorSmokeService smoke;

    /** Smoke from fires in the open (BETA 1.0 Pilot 4); null outside a running server. */
    public void setSmoke(OutdoorSmokeService smoke) { this.smoke = smoke; }

    /** The outdoor air with the smoke of open fires within reach added (BETA 1.0 Pilot 4). */
    public AirQualityModel.Air withSmoke(AirQualityModel.Air air, World world, double x, double y, double z,
                                         double windMs, double fromDegrees, double wetness) {
        if (smoke == null || air == null || !air.known() || !climate.airQuality().outdoorSmokeOn()) return air;
        AirQualityModel.Air plume = smoke.at(world, x, y, z, windMs, fromDegrees, wetness);
        return plume == AirQualityModel.Air.NONE ? air : air.plus(plume);
    }

    /**
     * The weather word for a spot: what its weather brings, rain or snow by
     * the air there (BETA 1.0 Pilot 4; before, the kind its surroundings
     * mostly get in vanilla).
     */
    private String weatherWord(World world, double x, double y, double z) {
        if (!climate.weather().enabled() || world == null) return "";
        WeatherModel.Conditions conditions = conditions(world);
        String word = WeatherModel.word(kindAt(world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                conditions), conditions);
        // A planned event names itself where it shows (API 1.9): a snowstorm's snow, a heat wave's clear sky.
        return planned.eventNow(world).map(event -> switch (event) {
            case SNOWSTORM -> "Snow".equals(word) ? "Snowstorm" : word;
            case HEAT_WAVE, COLD_SNAP -> "Clear".equals(word) ? event.displayName() : word;
            default -> word;
        }).orElse(word);
    }

    private PlannedWeather planned = PlannedWeather.none();

    /** An addon's weather plan (BETA 1.0 Pilot 4): its heat waves and cold snaps move the air, its storms the wind. */
    public void setPlannedWeather(PlannedWeather planned) {
        this.planned = Objects.requireNonNull(planned, "planned");
        if (wind != null) wind.setPlannedWeather(planned);
    }

    public PlannedWeather plannedWeather() { return planned; }

    /** Areas that decide rain or snow together, in blocks (BETA 1.0 Pilot 4). */
    static final int AREA_BLOCKS = 64;
    /** Ground this far above its area's is a peak, which snows by its own air. */
    static final int PEAK_ABOVE_BLOCKS = 48;
    /** An area changes between rain and snow at most this often, in ticks: an in-game hour. */
    static final long HOLD_TICKS = 1_000L;
    /** How long an area's decision is trusted before it is looked at again, in ticks. */
    static final long FRESH_TICKS = 200L;

    /**
     * One area's rain or snow, decided at its centre: snowing or not (with a
     * storm's hold), its margin from the line, and its ground and air.
     */
    private static final class Area {
        Boolean snowing;
        long decidedAt;
        long checkedAt;
        int groundY;
        double airF;
        double lineF;
        double marginF;
    }

    private final Map<UUID, Long2ObjectOpenHashMap<Area>> areas = new HashMap<>();

    /** The line between rain and snow now: higher in winter (BETA 1.0 Pilot 4). */
    private double snowBelowF(World world) {
        ClimateSettings.Weather settings = climate.weather();
        boolean winter = seasons != null && climate.seasons().enabled() && seasons.tracks(world.getUID())
                && seasons.season(world.getUID()) == Seasons.Season.WINTER;
        return winter ? settings.snowInWinterBelowF() : settings.snowAtOrBelowF();
    }

    /**
     * An area, by its index, looked at again when its decision is old: its
     * ground (where loaded), the air there, and whether it snows, which only
     * changes once an in-game hour has passed (at once when the clock jumps
     * or the season's line changes).
     */
    private Area area(World world, int ax, int az) {
        Long2ObjectOpenHashMap<Area> map = areas.computeIfAbsent(world.getUID(), ignored -> new Long2ObjectOpenHashMap<>());
        long key = HvacRegistry.cellKey(ax, az);
        // The world's clock, so /time set and sleeping count as the jumps they are.
        long now = world.getFullTime();
        Area area = map.get(key);
        if (area != null && now >= area.checkedAt && now - area.checkedAt < FRESH_TICKS) return area;
        int cx = ax * AREA_BLOCKS + AREA_BLOCKS / 2, cz = az * AREA_BLOCKS + AREA_BLOCKS / 2;
        int ground = world.isChunkLoaded(cx >> 4, cz >> 4)
                ? world.getHighestBlockYAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1 : world.getSeaLevel() + 1;
        double airF = air(world, blendedAt(world, cx, ground, cz));
        double line = snowBelowF(world);
        if (area == null) {
            area = new Area();
            map.put(key, area);
            if (map.size() > 20_000) map.clear();
        }
        // A jump of the clock, or a new season's line (an admin's /hvac season set), applies at once.
        boolean jumped = area.snowing == null || now < area.checkedAt || now - area.checkedAt > HOLD_TICKS * 2
                || area.lineF != line;
        Boolean before = jumped ? null : area.snowing;
        boolean snows = SnowEdge.margin(airF, line, before, WeatherModel.KEEP_F, false) > 0.0;
        boolean held = !jumped && now - area.decidedAt < HOLD_TICKS;
        if (!held && (area.snowing == null || snows != area.snowing)) {
            area.snowing = snows;
            area.decidedAt = now;
        }
        area.marginF = SnowEdge.margin(airF, line, area.snowing, WeatherModel.KEEP_F, held);
        area.checkedAt = now;
        area.groundY = ground;
        area.airF = airF;
        area.lineF = line;
        return area;
    }

    private Area areaAt(World world, int x, int z) {
        return area(world, Math.floorDiv(x, AREA_BLOCKS), Math.floorDiv(z, AREA_BLOCKS));
    }

    /**
     * How much colder than the line a column is (BETA 1.0 Pilot 4; positive
     * snows): the margins of the four nearest area centres blended at the
     * column, plus a little noise, so rain and snow meet along a wavy line
     * that follows the cold rather than the areas' grid.
     */
    private double marginAt(World world, int x, int z) {
        double u = (x + 0.5 - AREA_BLOCKS / 2.0) / AREA_BLOCKS, v = (z + 0.5 - AREA_BLOCKS / 2.0) / AREA_BLOCKS;
        int ax = (int) Math.floor(u), az = (int) Math.floor(v);
        double blended = SnowEdge.blend(area(world, ax, az).marginF, area(world, ax + 1, az).marginF,
                area(world, ax, az + 1).marginF, area(world, ax + 1, az + 1).marginF, u - ax, v - az);
        return blended + SnowEdge.NOISE_F * SnowEdge.noise(world.getSeed(), x, z);
    }

    /** Whether rain reaches a column at all: weather.dry-areas, by the column's own surroundings. */
    private boolean reaches(World world, int x, int groundY, int z, WeatherModel.Conditions conditions) {
        BiomeBlend.Blended local = surroundingsAt(world, x, groundY, z);
        return WeatherModel.kind(local, 0.0, conditions, climate.weather()) != Precipitation.NONE;
    }

    /**
     * What weather brings to a block (BETA 1.0 Pilot 4): snow where its
     * column's blended margin is above the line, or, on ground well above its
     * area's (a peak), where its own air is cold enough; rain elsewhere;
     * nothing where rain doesn't reach.
     */
    private Precipitation kindAt(World world, int x, int y, int z, WeatherModel.Conditions conditions) {
        if (world.getEnvironment() != World.Environment.NORMAL) return Precipitation.NONE;
        Area own = areaAt(world, x, z);
        if (!reaches(world, x, own.groundY, z, conditions)) return Precipitation.NONE;
        if (marginAt(world, x, z) > 0.0) return Precipitation.SNOW;
        if (y < own.groundY + PEAK_ABOVE_BLOCKS) return Precipitation.RAIN;
        return air(world, blendedAt(world, x, y, z)) <= own.lineF ? Precipitation.SNOW : Precipitation.RAIN;
    }

    private WeatherModel.Conditions conditions(World world) {
        return weather == null ? WeatherModel.Conditions.CLEAR : weather.conditions(world);
    }

    /** Whether rain is falling now that reaches the world's dry areas (weather.dry-areas). */
    public boolean rainReachesDry(World world) {
        WeatherModel.Conditions conditions = conditions(world);
        return conditions.raining() && WeatherModel.reachesDry(conditions, climate.weather());
    }

    /**
     * The sunshine a person at this spot takes in, W/m² (BETA 1.0 Pilot 4),
     * for the feels-like: full where nothing is above it, a share of it by
     * the sky light in shade, none at night, indoors or where the chunk
     * isn't loaded.
     */
    public double sunshineAt(Location location) {
        double strength = climate.feelsLike().effectiveStrength();
        if (!(strength > 0.0) || location == null || location.getWorld() == null) return 0.0;
        World world = location.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return 0.0;
        int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return 0.0;
        int skyLight = location.getBlock().getLightFromSky();
        if (skyLight <= 0) return 0.0;
        boolean direct = y > world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        WeatherModel.Conditions conditions = conditions(world);
        double season = seasons == null ? 0.0 : seasons.airFactor(world.getUID());
        return com.github.blade.hvac.simulation.Sunshine.absorbedWm2(world.getTime(), season,
                conditions.wetness(), conditions.storm(), direct, skyLight, strength);
    }

    /**
     * What is falling at a block right now (BETA 1.0 Pilot 4): NONE when it
     * isn't raining or weather never reaches there, otherwise RAIN or SNOW by
     * the air at that block. Never loads a chunk.
     */
    public Precipitation fallingAt(World world, int x, int y, int z) {
        if (world == null || !climate.weather().enabled()) return Precipitation.NONE;
        WeatherModel.Conditions conditions = conditions(world);
        if (!conditions.raining()) return Precipitation.NONE;
        return kindAt(world, x, y, z, conditions);
    }

    /**
     * The height above which rain would come down as snow right now at a
     * column (BETA 1.0 Pilot 4), whether or not it is raining: negative
     * infinity where its area snows (at every height), and in a raining
     * area the height where a peak's own air is cold enough, never less than
     * 48 blocks above the area's ground (positive infinity if never). NaN
     * where rain doesn't reach now (a dry area outside a thunderstorm, the
     * Nether and the End, or weather off). Never loads a chunk.
     */
    public double snowLineAt(World world, int x, int z) {
        if (world == null || !climate.weather().enabled() || world.getEnvironment() != World.Environment.NORMAL)
            return Double.NaN;
        WeatherModel.Conditions conditions = conditions(world);
        Area own = areaAt(world, x, z);
        if (!reaches(world, x, own.groundY, z, conditions)) return Double.NaN;
        if (marginAt(world, x, z) > 0.0) return Double.NEGATIVE_INFINITY;
        // A raining column: only peaks well above its area's ground snow, where their own air is cold enough.
        ClimateSettings.Altitude altitude = climate.altitude();
        double byHeight = snowLine(own.airF, Math.max(altitude.fromY(), own.groundY), altitude.fromY(),
                altitude.lapseFPerBlock(), own.lineF);
        return Math.max(own.groundY + PEAK_ABOVE_BLOCKS, byHeight);
    }

    /**
     * Where the air reaches {@code snowF}, from {@code airF} measured at
     * height {@code y}: it cools by {@code lapseFPerBlock} above
     * {@code fromY} and stays the same below.
     */
    public static double snowLine(double airF, double y, double fromY, double lapseFPerBlock, double snowF) {
        double atFrom = airF + lapseFPerBlock * Math.max(0.0, y - fromY);
        if (atFrom <= snowF) return Double.NEGATIVE_INFINITY;
        if (!(lapseFPerBlock > 0.0)) return Double.POSITIVE_INFINITY;
        return fromY + (atFrom - snowF) / lapseFPerBlock;
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
        // Windier with height; underground takes the rest of it away below.
        local = local.withWindExposure(WindModel.exposure(local.windExposure(), y, world.getSeaLevel(), 0.0));
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
                precipitationAt(block, own), own.seasonalF(), airOf(block, biome), windOf(block, biome));
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
                        precipitationAt(block, own), BiomeBlend.weight(distance, radius), own.seasonalF(),
                        airOf(block, biome), windOf(block, biome)));
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
        // A planned heat wave or cold snap moves the air on top of the season (API 1.9).
        return local.warmer(seasonShiftF(world, local.seasonalF(), false) + planned.shiftNowF(world));
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
        // The world's clock, so /time set and sleeping count as the jumps they are.
        long now = world.getFullTime();
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
        return outdoor(world, thermostat.outdoorClimate(), air,
                windAt(world, thermostat.position().x() + 0.5, thermostat.position().z() + 0.5),
                thermostat.position().x() + 0.5, thermostat.position().y() + 0.5, thermostat.position().z() + 0.5);
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
        return outdoor(world, local, air(world, local), windAt(world, location.getX(), location.getZ()),
                location.getX(), location.getY(), location.getZ());
    }

    private Outdoor outdoor(World world, BiomeBlend.Blended local, double airF, WindModel.Spot spot,
                            double x, double y, double z) {
        WeatherModel.Effect effect = effect(world, local, spot);
        BiomeBlend.Blended now = inSeason(world, local);
        double dew = HumidityModel.outdoorDewPointF(now.noonF(), now.midnightF(), now.downfall(), effect, airF,
                climate.humidity());
        double from = spot == null || !(effect.windMs() > 0.0) ? Double.NaN : spot.fromDegrees();
        return new Outdoor(airF, water(world, effect, local), dew, effect.windMs(), weatherWord(world, x, y, z),
                withSmoke(outdoorAir(world, local, effect), world, x, y, z, effect.windMs(), from, effect.wetness()),
                from);
    }

    /** The outdoor air right now: the biome's, its season's pollen, and rain washing it out (3.2). */
    private AirQualityModel.Air outdoorAir(World world, BiomeBlend.Blended local, WeatherModel.Effect effect) {
        // Pollen switched off (BETA 1.0): none outdoors, so none comes in.
        double pollen = !climate.airQuality().pollen() ? 0.0 : seasons == null || world == null
                ? OutdoorAir.SUMMER_POLLEN : seasons.pollenFactor(world.getUID());
        return OutdoorAir.now(local.air(), pollen, effect.wetness(), effect.windMs(), local.downfall(),
                climate.wind());
    }

    /**
     * The outdoor air of a climate right now, weather and season included:
     * for a weather station's area (BETA 1.0), which is averaged from many
     * spots. Never reads a room's temperature.
     */
    public double airNow(World world, BiomeBlend.Blended local) {
        return air(world, local);
    }

    /** How far the seasons move a climate this many days from now, in degrees (BETA 1.0, forecasts). */
    public double seasonShiftIn(World world, double seasonalF, int days) {
        if (seasons == null || world == null || !(seasonalF > 0.0)) return 0.0;
        double strength = climate.seasons().effectiveStrength();
        return strength <= 0.0 ? 0.0 : seasonalF * strength * seasons.airFactorIn(world.getUID(), days);
    }

    /** Null outside a running server (tests). */
    public SeasonService seasons() { return seasons; }

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
        return Math.max(ThermalModel.NATURAL_WATER_MINIMUM_F, Math.min(ThermalModel.NATURAL_WATER_MAXIMUM_F,
                waterUnheld(world, effect, local)));
    }

    /** Natural water before it is held at liquid water's minimum and maximum. */
    private double waterUnheld(World world, WeatherModel.Effect effect, BiomeBlend.Blended local) {
        double season = seasonShiftF(world, local.seasonalF(), true) + planned.shiftNowF(world) * Seasons.WATER_SHARE;
        return ThermalModel.naturalWaterUnheld(local.noonF() + season, local.midnightF() + season)
                + effect.meanShiftF() * climate.weather().waterShare();
    }

    /**
     * How cold natural water at a block is getting (BETA 1.0 Pilot 4): its
     * temperature before EZvac holds liquid water at 33 F, so at or below 32
     * it would be freezing. Lakes lag the air's seasons. Never loads a chunk.
     */
    public double naturalWaterUnheldAt(World world, int x, int y, int z) {
        if (world == null) return 60.0;
        BiomeBlend.Blended local = blendedAt(world, x, y, z);
        return waterUnheld(world, effect(world, local), local);
    }
}
