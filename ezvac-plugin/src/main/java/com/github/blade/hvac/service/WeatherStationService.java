package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.config.ServerSwitches;
import com.github.blade.hvac.config.WeatherStationSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.DeviceKind;
import com.github.blade.hvac.model.StationShape;
import com.github.blade.hvac.model.WeatherStation;
import com.github.blade.hvac.simulation.BiomeBlend;
import com.github.blade.hvac.simulation.Forecast;
import com.github.blade.hvac.simulation.OutdoorAir;
import com.github.blade.hvac.simulation.WindModel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weather stations at work (BETA 1.0): checking their towers, measuring the
 * outdoor climate around them, forecasting, finding the ones near a player,
 * their sparks, and the lightning they catch. A station only ever reads
 * EZvac's outdoor climate model, never a room's temperature, so a heated or
 * cooled house beside it can't skew it. Main thread only.
 */
public final class WeatherStationService implements Listener {
    /** The step between the spots a station samples: a blended climate already averages what lies between. */
    static final int SAMPLE_STEP = 16;
    /** Sparks only show to someone this near. */
    private static final double SPARK_RANGE = 32.0;

    private final Plugin plugin;
    private final HvacRegistry registry;
    private final OutdoorTemperatureService outdoor;
    private final ClimateSettings climate;
    private final WeatherStationSettings settings;
    private DisplayService displays;

    private final Map<BlockKey, List<Forecast.Day>> forecasts = new HashMap<>();
    /** Per world, the half-day (sunrise to sunset, or sunset to sunrise) last measured in. */
    private final Map<UUID, Long> measuredIn = new HashMap<>();
    /** Per world, the rain and thunder chances: every station in a world shares the sky's clocks. */
    private final Map<UUID, Forecast.Chances> chances = new HashMap<>();

    public WeatherStationService(Plugin plugin, HvacRegistry registry, OutdoorTemperatureService outdoor,
                                 ClimateSettings climate, WeatherStationSettings settings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.climate = Objects.requireNonNull(climate, "climate");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public void setDisplays(DisplayService displays) { this.displays = displays; }
    public WeatherStationSettings settings() { return settings; }

    /** False while weather stations are switched off in /hvac quicksettings. */
    public static boolean switchedOn() { return !ServerSwitches.deviceOff(DeviceKind.WEATHER_STATION); }

    /** On, standing, and switched on server-wide. */
    public static boolean working(WeatherStation station) {
        return switchedOn() && station.enabled() && station.intact();
    }

    // ---- the tower ----

    /**
     * Whether a wall sign hangs on a whole tower.
     *
     * @param missing what the tower still needs, for the builder, or null when it's whole
     */
    public record Check(boolean whole, String missing) {}

    public static Check check(Block sign) {
        if (!(sign.getBlockData() instanceof WallSign wall))
            return new Check(false, "Put a wall sign on the front of the waxed cut copper.");
        BlockFace facing = wall.getFacing();
        Check best = null;
        int bestFitting = -1;
        for (boolean mirrored : new boolean[]{false, true}) {
            List<StationShape.Block> blocks = StationShape.blocks(sign.getX(), sign.getY(), sign.getZ(),
                    facing.getModX(), facing.getModZ(), mirrored);
            int fitting = 0;
            StationShape.Block first = null;
            for (StationShape.Block block : blocks) {
                if (fits(sign.getWorld(), block)) fitting++;
                else if (first == null) first = block;
            }
            if (first == null) return new Check(true, null);
            if (fitting > bestFitting) {
                bestFitting = fitting;
                best = new Check(false, describe(first, sign));
            }
        }
        return best;
    }

    private static boolean fits(World world, StationShape.Block part) {
        Block block = world.getBlockAt(part.x(), part.y(), part.z());
        if (!StationShape.fits(block.getType(), part.part())) return false;
        if (part.part() != StationShape.Part.ROD) return true;
        return block.getBlockData() instanceof Directional rod && rod.getFacing().getModX() == part.faceX()
                && rod.getFacing().getModZ() == part.faceZ() && rod.getFacing().getModY() == 0;
    }

    private static String describe(StationShape.Block part, Block sign) {
        String what = part.part() == StationShape.Part.ROD && !StationShape.waxedRods()
                ? "a lightning rod" : part.part().description();
        String where = part.part() == StationShape.Part.ROD ? ", pointing out sideways," : "";
        return "The tower needs " + what + where + " at " + part.x() + ", " + part.y() + ", " + part.z()
                + " (" + (part.y() - sign.getY() >= 0 ? "+" : "") + (part.y() - sign.getY())
                + " from the sign).";
    }

    /** Checks a station's tower again, where its chunk is loaded; redraws it if that changed. */
    public void recheck(WeatherStation station) {
        BlockKey position = station.position();
        if (!position.isChunkLoaded()) return;
        Location location = position.location();
        if (location == null) return;
        boolean whole = check(location.getBlock()).whole();
        if (whole == station.intact()) return;
        station.setIntact(whole);
        if (whole && station.area() == null) measure(station);
        if (displays != null) displays.refreshWeatherStation(station, true);
    }

    // ---- measuring ----

    /**
     * Measures the outdoor climate around a station: EZvac's blended climate
     * at spots every {@link #SAMPLE_STEP} blocks out to the area's radius, at
     * the station's height, halfway up and at the top of the area. Only
     * loaded chunks are sampled, and none is loaded for it.
     */
    public void measure(WeatherStation station) {
        BlockKey position = station.position();
        if (!position.isChunkLoaded()) return;
        World world = position.world();
        if (world == null) return;
        int radius = settings.areaRadius() / SAMPLE_STEP * SAMPLE_STEP;
        int[] heights = {0, settings.areaHeight() / 2, settings.areaHeight()};
        List<BiomeBlend.Blended> samples = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += SAMPLE_STEP)
            for (int dz = -radius; dz <= radius; dz += SAMPLE_STEP) {
                int x = position.x() + dx, z = position.z() + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int height : heights) samples.add(outdoor.blendedAt(world, x, position.y() + height, z));
            }
        if (samples.isEmpty()) return;
        station.setArea(average(samples));
        registry.markDirty();
        forecasts.remove(position);
    }

    static WeatherStation.Area average(List<BiomeBlend.Blended> samples) {
        double noon = 0, midnight = 0, downfall = 0, rain = 0, snow = 0, seasonal = 0, fine = 0, coarse = 0,
                pollen = 0, exposure = 0;
        for (BiomeBlend.Blended sample : samples) {
            noon += sample.noonF(); midnight += sample.midnightF(); downfall += sample.downfall();
            rain += sample.rainShare(); snow += sample.snowShare(); seasonal += sample.seasonalF();
            fine += sample.air().fine(); coarse += sample.air().coarse(); pollen += sample.air().pollen();
            exposure += sample.windExposure();
        }
        int n = samples.size();
        return new WeatherStation.Area(noon / n, midnight / n, downfall / n, rain / n, snow / n, seasonal / n,
                fine / n, coarse / n, pollen / n, exposure / n);
    }

    static BiomeBlend.Blended blended(WeatherStation.Area area) {
        return new BiomeBlend.Blended(area.noonF(), area.midnightF(), area.downfall(), area.rainShare(),
                area.snowShare(), area.seasonalF(), new OutdoorAir.Baseline(area.fine(), area.coarse(), area.pollen()),
                area.windExposure());
    }

    /** The outdoor air of a station's area right now, or NaN before it has measured it. */
    public double nowF(WeatherStation station) {
        World world = station.position().world();
        if (world == null || station.area() == null) return Double.NaN;
        return outdoor.airNow(world, blended(station.area()));
    }

    // ---- forecasting ----

    /** A station's seven days, today first; empty before it has measured its area. */
    public List<Forecast.Day> forecast(WeatherStation station) {
        List<Forecast.Day> cached = forecasts.get(station.position());
        if (cached != null) return cached;
        List<Forecast.Day> made = make(station);
        if (!made.isEmpty()) forecasts.put(station.position(), made);
        return made;
    }

    private List<Forecast.Day> make(WeatherStation station) {
        World world = station.position().world();
        WeatherStation.Area area = station.area();
        if (world == null || area == null) return List.of();
        Forecast.Clocks clocks = clocks(world);
        Forecast.Chances odds = chances.computeIfAbsent(world.getUID(),
                ignored -> Forecast.chances(clocks, world.getSeed() ^ Math.floorDiv(world.getFullTime(), 12_000L)));
        WindService wind = outdoor.wind();
        List<WindModel.Day> winds = wind != null && wind.enabled() ? wind.forecast(world, Forecast.DAYS) : null;
        SeasonService seasons = outdoor.seasons();
        boolean snow = area.snowShare() > area.rainShare();
        boolean dry = area.rainShare() + area.snowShare() < 0.1;
        OutdoorAir.Baseline baseline = new OutdoorAir.Baseline(area.fine(), area.coarse(), area.pollen());
        List<Forecast.Day> days = new ArrayList<>(Forecast.DAYS);
        for (int ahead = 0; ahead < Forecast.DAYS; ahead++) {
            WindModel.Day windDay = winds == null ? null : winds.get(ahead);
            double windMs = windDay == null ? climate.weather().windClearMs() : windDay.meanMs() * area.windExposure();
            double pollen = Double.NaN;
            if (climate.airQuality().enabled() && climate.airQuality().pollen()) {
                double factor = seasons == null ? OutdoorAir.SUMMER_POLLEN : seasons.pollenFactorIn(world.getUID(), ahead);
                pollen = OutdoorAir.now(baseline, factor, dry ? 0.0 : odds.rain()[ahead], windMs, area.downfall(),
                        climate.wind()).pollen();
            }
            days.add(Forecast.day(ahead, area, outdoor.seasonShiftIn(world, area.seasonalF(), ahead),
                    odds.rain()[ahead], odds.thunder()[ahead], windDay, pollen,
                    Forecast.note(clocks, ahead, snow, dry), climate.weather()));
        }
        return days;
    }

    static Forecast.Clocks clocks(World world) {
        return new Forecast.Clocks(world.hasStorm(), world.getWeatherDuration(), world.isThundering(),
                world.getThunderDuration(), world.getClearWeatherDuration(), world.getTime());
    }

    // ---- finding ----

    /** The working stations within range of a spot, nearest first, at most {@code limit}. */
    public List<WeatherStation> nearby(Location at, int limit) {
        if (at.getWorld() == null || !switchedOn()) return List.of();
        UUID world = at.getWorld().getUID();
        double range = (double) settings.range() * settings.range();
        return registry.weatherStations().stream()
                .filter(station -> station.position().worldId().equals(world) && working(station)
                        && station.area() != null && distanceSquared(station, at) <= range)
                .sorted(Comparator.comparingDouble(station -> distanceSquared(station, at)))
                .limit(limit).toList();
    }

    public static double distanceSquared(WeatherStation station, Location at) {
        double dx = station.position().x() + 0.5 - at.getX(), dy = station.position().y() + 0.5 - at.getY(),
                dz = station.position().z() + 0.5 - at.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    // ---- the day's rhythm ----

    /**
     * Measures and forecasts again in every world whose half-day has turned
     * (at sunrise and at sunset), and redraws its stations. Cheap the rest of
     * the time: one comparison per world.
     */
    public void tick() {
        Map<UUID, World> worlds = new HashMap<>();
        for (WeatherStation station : registry.weatherStations()) {
            World world = station.position().world();
            if (world != null) worlds.putIfAbsent(world.getUID(), world);
        }
        for (World world : worlds.values()) {
            long half = Math.floorDiv(world.getFullTime(), 12_000L);
            Long last = measuredIn.put(world.getUID(), half);
            if (last != null && last == half) continue;
            chances.remove(world.getUID());
            for (WeatherStation station : registry.weatherStations()) {
                if (!station.position().worldId().equals(world.getUID())) continue;
                recheck(station);
                measure(station);
                forecasts.remove(station.position());
                if (displays != null) displays.refreshWeatherStation(station, true);
            }
        }
    }

    /** Forgets a world's forecasts: its weather clocks changed. */
    private void skyChanged(World world) {
        chances.remove(world.getUID());
        // A tick later: the event comes before the world's clocks are set.
        Bukkit.getScheduler().runTask(plugin, () -> {
            chances.remove(world.getUID());
            for (WeatherStation station : registry.weatherStations())
                if (station.position().worldId().equals(world.getUID())) {
                    forecasts.remove(station.position());
                    if (displays != null) displays.refreshWeatherStation(station, true);
                }
        });
    }

    /**
     * Every second, for the stations someone is near: checks the tower again,
     * since /fill, WorldEdit and pistons change blocks without the events
     * that would say so, measures one that hasn't yet, then gives off sparks
     * at the rods of a working one.
     * Thirteen block reads a station, and none where nobody is.
     */
    public void watch() {
        if (!switchedOn()) return;
        for (WeatherStation station : registry.weatherStations()) {
            if (!station.enabled() || !station.position().isChunkLoaded()) continue;
            Location sign = station.position().location();
            if (sign == null || sign.getNearbyPlayers(SPARK_RANGE).isEmpty()) continue;
            recheck(station);
            // Not measured yet, or its chunk wasn't loaded when it last tried (just after a start).
            if (station.area() == null && station.intact()) {
                measure(station);
                if (displays != null && station.area() != null) displays.refreshWeatherStation(station, true);
            }
            if (!settings.sparks() || !station.intact()
                    || !(sign.getBlock().getBlockData() instanceof WallSign wall)) continue;
            BlockFace facing = wall.getFacing();
            boolean mirrored = mirrored(sign.getBlock(), facing);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (StationShape.Block part : StationShape.blocks(sign.getBlockX(), sign.getBlockY(), sign.getBlockZ(),
                    facing.getModX(), facing.getModZ(), mirrored)) {
                if (part.part() != StationShape.Part.ROD || random.nextInt(3) != 0) continue;
                // The rod's tip: the far half of its block.
                Location tip = new Location(sign.getWorld(), part.x() + 0.5 + part.faceX() * 0.35, part.y() + 0.5,
                        part.z() + 0.5 + part.faceZ() * 0.35);
                sign.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, tip, 2 + random.nextInt(3), 0.12, 0.12, 0.12, 0.02);
            }
        }
    }

    /** Whether the tower is the mirror image: its first rod on the left. */
    private static boolean mirrored(Block sign, BlockFace facing) {
        for (StationShape.Block part : StationShape.blocks(sign.getX(), sign.getY(), sign.getZ(),
                facing.getModX(), facing.getModZ(), false))
            if (part.part() == StationShape.Part.ROD) return !fits(sign.getWorld(), part);
        return false;
    }

    // ---- events ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) { skyChanged(event.getWorld()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) { skyChanged(event.getWorld()); }

    /** Lightning that hits a station's tower, rods and all, is counted. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent event) {
        Location strike = event.getLightning().getLocation();
        for (WeatherStation station : registry.weatherStations()) {
            if (!onTower(station, strike)) continue;
            station.struck();
            registry.markDirty();
            if (displays != null) displays.refreshWeatherStation(station, true);
            return;
        }
    }

    /** Within the tower's footprint (the rods reach one block out) from its foot to just over its top. */
    static boolean onTower(WeatherStation station, Location at) {
        BlockKey sign = station.position();
        if (at.getWorld() == null || !at.getWorld().getUID().equals(sign.worldId())) return false;
        return Math.abs(at.getBlockX() - sign.x()) <= 2 && Math.abs(at.getBlockZ() - sign.z()) <= 2
                && at.getBlockY() >= sign.y() - 2 && at.getBlockY() <= sign.y() + 5;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { changedNear(event.getBlock().getLocation()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { changedNear(event.getBlock().getLocation()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) { event.blockList().forEach(block -> changedNear(block.getLocation())); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { event.blockList().forEach(block -> changedNear(block.getLocation())); }

    /** A block changed by a tower: check it again a tick later, once the change has happened. */
    private void changedNear(Location at) {
        for (WeatherStation station : registry.weatherStations())
            if (onTower(station, at)) Bukkit.getScheduler().runTask(plugin, () -> recheck(station));
    }

    /** Checks every station whose chunk is loaded, for /hvac sync. */
    public void recheckAll() {
        for (WeatherStation station : registry.weatherStations()) recheck(station);
    }

    /** Forgets what a removed station had. */
    public void forget(BlockKey position) {
        forecasts.remove(position);
    }
}
