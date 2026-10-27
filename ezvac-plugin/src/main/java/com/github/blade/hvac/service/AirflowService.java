package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.simulation.AirQualityModel;
import com.github.blade.hvac.simulation.Microvariance;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.ThermalModel;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;

import java.util.*;

/** Event-invalidated, tick-budgeted airflow maps for registered vent systems. */
public final class AirflowService {
    /**
     * @param localF     temperature at the spot: the air there, or the water when {@code water}
     * @param outdoorF   the outdoor air temperature, whatever the spot's medium
     * @param water      true when the spot is in water; unconditioned water reads the
     *                   biome's natural water temperature rather than the air's
     * @param humidity   relative humidity of the air at the spot (%), NaN in water
     * @param feelsLikeF apparent temperature; in water, the water temperature
     * @param weather    the outdoor weather word, empty where weather never reaches
     * @param air        what is in the air at the spot (3.2); unknown in water
     */
    public record TemperatureReading(double localF, double outdoorF, double influence,
                                     int airflowDistance, SystemId system, Thermostat thermostat,
                                     boolean water, double humidity, double feelsLikeF, String weather,
                                     AirQualityModel.Air air) {
        public TemperatureReading {
            air = air == null ? AirQualityModel.Air.UNKNOWN : air;
        }

        /** A reading without air. */
        public TemperatureReading(double localF, double outdoorF, double influence, int airflowDistance,
                                  SystemId system, Thermostat thermostat, boolean water, double humidity,
                                  double feelsLikeF, String weather) {
            this(localF, outdoorF, influence, airflowDistance, system, thermostat, water, humidity, feelsLikeF,
                    weather, AirQualityModel.Air.UNKNOWN);
        }

        public boolean conditioned() { return system != null && thermostat != null && influence > 0.0; }
    }

    /**
     * How much water an air system's space holds open to its air, for
     * evaporation: the water blocks bordering its airflow map.
     *
     * @param airCells   cells in the airflow map
     * @param waterCells packed positions of bordering water blocks (capped)
     */
    public record WaterExposure(int airCells, long[] waterCells) {
        public static final WaterExposure NONE = new WaterExposure(0, new long[0]);
        public double wetFraction() { return airCells <= 0 ? 0.0 : waterCells.length / (double) airCells; }

        /**
         * Without the given blocks. Every heat pump and AC puts out a water
         * block at the edge of the room it serves; that is equipment, not a
         * pool, and counting it made every such room humidify itself.
         *
         * @param blocks this space's world's blocks, packed by {@link #packByWorld}; null for none
         */
        public WaterExposure excluding(LongOpenHashSet blocks) {
            if (waterCells.length == 0 || blocks == null || blocks.isEmpty()) return this;
            long[] kept = new long[waterCells.length];
            int count = 0;
            for (long cell : waterCells)
                if (!blocks.contains(cell)) kept[count++] = cell;
            return count == waterCells.length ? this : new WaterExposure(airCells, Arrays.copyOf(kept, count));
        }
    }

    /**
     * Blocks packed the way the airflow maps pack them, per world, so a
     * cycle's owned outputs are packed once and looked up without allocating.
     */
    public static Map<UUID, LongOpenHashSet> packByWorld(Collection<BlockKey> blocks) {
        Map<UUID, LongOpenHashSet> packed = new HashMap<>();
        for (BlockKey block : blocks)
            packed.computeIfAbsent(block.worldId(), ignored -> new LongOpenHashSet())
                    .add(pack(block.x(), block.y(), block.z()));
        return packed;
    }

    /** Bordering water blocks a single map records; beyond this a space is simply "very wet". */
    private static final int MAXIMUM_WATER_CELLS = 4_096;
    /** Smoke sources one space notes at most (3.2); a room past this is a bonfire either way. */
    private static final int MAXIMUM_SMOKE_CELLS = 256;
    private static final long[] NO_CELLS = new long[0];

    /**
     * A standalone unit's zone reaching a spot.
     *
     * @param influence 0..1, full near the unit and fading to its zone's edge
     */
    public record ZoneReading(ClimateUnit unit, int distance, double influence) {}

    private record Node(int x, int y, int z, int distance) {}

    private static final class Rebuild {
        final AirflowTarget target;
        /** Water systems traverse water; air systems and unit zones traverse open air. */
        final boolean water;
        /** Path length and cell cap: a system's, or a standalone unit's zone's. */
        final int range;
        final int maximumCells;
        final ArrayDeque<Node> queue = new ArrayDeque<>();
        final Long2ByteOpenHashMap distances = new Long2ByteOpenHashMap();
        /** Water blocks the air walk bumped into: pools, fountains, a lake outside an open door. */
        final LongOpenHashSet waterCells = new LongOpenHashSet();
        /** Campfires, fire, lava and the like in or around the air (3.2); burning or not is read later. */
        final LongOpenHashSet smokeCells = new LongOpenHashSet();
        boolean capped;
        // Chunk lookups dominate the walk, and a breadth-first frontier stays
        // within a chunk for long runs. The cache is cleared every tick so a
        // reference can never outlive the chunk's residency.
        private int cachedChunkX;
        private int cachedChunkZ;
        private Chunk cachedChunk;
        private boolean chunkCached;

        Rebuild(AirflowTarget target, boolean water, int range, int maximumCells) {
            this.target = target;
            this.water = water;
            this.range = range;
            this.maximumCells = maximumCells;
            distances.defaultReturnValue(ABSENT);
        }

        void clearChunkCache() {
            chunkCached = false;
            cachedChunk = null;
        }

        Chunk chunkAt(World world, int chunkX, int chunkZ) {
            if (chunkCached && cachedChunkX == chunkX && cachedChunkZ == chunkZ) return cachedChunk;
            // Never getChunkAt an unloaded chunk; that would force-load it.
            Chunk chunk = world.isChunkLoaded(chunkX, chunkZ) ? world.getChunkAt(chunkX, chunkZ) : null;
            cachedChunkX = chunkX;
            cachedChunkZ = chunkZ;
            cachedChunk = chunk;
            chunkCached = true;
            return chunk;
        }
    }

    /** Distance sentinel for "not reached"; real distances are 0..127. */
    private static final byte ABSENT = -1;

    /** A rebuild waits this long after its last invalidation before starting. */
    private static final long REBUILD_DEBOUNCE_TICKS = 20L;

    /**
     * Ceiling on debounced deferral. Without it a system invalidated faster
     * than the debounce window - a door on a redstone clock - would keep
     * pushing its own rebuild back and never refresh at all.
     */
    private static final long MAXIMUM_REBUILD_DEFERRAL_TICKS = 200L;

    /** Bounds begin() work after a mass invalidation such as a world load. */
    private static final int MAXIMUM_REBUILD_STARTS_PER_TICK = 8;

    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final HvacRegistry registry;
    private final OutdoorTemperatureService outdoor;
    private final HvacSettings settings;
    private final Map<AirflowTarget, Long2ByteMap> completed = new HashMap<>();
    private final Map<AirflowTarget, WaterExposure> exposures = new HashMap<>();
    private final Map<AirflowTarget, long[]> smoke = new HashMap<>();
    private final LinkedHashSet<AirflowTarget> dirty = new LinkedHashSet<>();
    private final Map<AirflowTarget, Long> dirtySince = new HashMap<>();
    private final Map<AirflowTarget, Long> dirtyFirst = new HashMap<>();
    private long ticks;
    private Rebuild active;
    private long completedRebuilds;
    private long visitedCells;
    private long cappedRebuilds;


    public AirflowService(HvacRegistry registry, OutdoorTemperatureService outdoor, HvacSettings settings) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.settings = Objects.requireNonNull(settings, "settings");
        for (ClimateVent vent : registry.vents()) dirty.add(vent.system());
        for (ClimateUnit unit : registry.climateUnits()) dirty.add(ZoneId.of(unit));
    }

    public void invalidate(AirflowTarget target) {
        dirty.add(target);
        dirtySince.put(target, ticks);
        dirtyFirst.putIfAbsent(target, ticks);
        // An in-flight rebuild is deliberately allowed to finish. Discarding it
        // meant a repeatedly invalidated system - a door on a redstone clock -
        // burned the whole node budget every tick and never produced a map at
        // all. A slightly stale map now stands until the debounced redo lands.
    }

    public void invalidateNear(Location location) {
        if (location == null || location.getWorld() == null) return;
        Long2ObjectOpenHashMap<List<ClimateVent>> cells = registry.ventCells(location.getWorld().getUID());
        if (cells == null) return;
        int shift = registry.ventCellShift();

        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        long packed = pack(blockX, blockY, blockZ);

        int maxRange = settings.airflowMaximumRange() + 2;
        int maxRangeSq = maxRange * maxRange;

        Set<SystemId> affected = null;
        for (int cellX = (blockX - maxRange) >> shift; cellX <= (blockX + maxRange) >> shift; cellX++) {
            for (int cellZ = (blockZ - maxRange) >> shift; cellZ <= (blockZ + maxRange) >> shift; cellZ++) {
                List<ClimateVent> bucket = cells.get(HvacRegistry.cellKey(cellX, cellZ));
                if (bucket == null) continue;
                for (ClimateVent vent : bucket) {
                    BlockKey pos = vent.position();
                    SystemId system = vent.system();
                    if (affected != null && affected.contains(system)) continue;

                    Long2ByteMap cache = completed.get(system);
                    if (cache != null && cache.containsKey(packed)) {
                        if (affected == null) affected = new HashSet<>();
                        affected.add(system);
                        continue;
                    }

                    int dx = pos.x() - blockX;
                    int dy = pos.y() - blockY;
                    int dz = pos.z() - blockZ;
                    int distSq = dx * dx + dy * dy + dz * dz;

                    if (distSq <= maxRangeSq) {
                        if (affected == null) affected = new HashSet<>();
                        affected.add(system);
                    }
                }
            }
        }

        if (affected != null) {
            for (SystemId system : affected) invalidate(system);
        }
        invalidateZonesNear(location.getWorld().getUID(), blockX, blockY, blockZ, packed);
    }

    /** Unit zones that hold this block, or whose unit is close enough that they could now. */
    private void invalidateZonesNear(UUID worldId, int blockX, int blockY, int blockZ, long packed) {
        Long2ObjectOpenHashMap<List<ClimateUnit>> cells = registry.climateUnitCells(worldId);
        if (cells == null) return;
        int shift = registry.ventCellShift();
        int reach = zoneRange() + 2;
        for (int cellX = (blockX - reach) >> shift; cellX <= (blockX + reach) >> shift; cellX++) {
            for (int cellZ = (blockZ - reach) >> shift; cellZ <= (blockZ + reach) >> shift; cellZ++) {
                List<ClimateUnit> bucket = cells.get(HvacRegistry.cellKey(cellX, cellZ));
                if (bucket == null) continue;
                for (ClimateUnit climateUnit : bucket) {
                    ZoneId zone = ZoneId.of(climateUnit);
                    Long2ByteMap map = completed.get(zone);
                    BlockKey unit = climateUnit.unit();
                    if ((map != null && map.containsKey(packed)) || Math.abs(unit.x() - blockX)
                            + Math.abs(unit.y() - blockY) + Math.abs(unit.z() - blockZ) <= reach)
                        invalidate(zone);
                }
            }
        }
    }

    /** The farthest any unit's zone reaches, for spatial searches. */
    private int zoneRange() {
        return outdoor.climate().unitRange();
    }

    private int rangeOf(ClimateUnit unit) {
        return switch (unit) {
            case Humidifier ignored -> outdoor.climate().humidifier().range();
            case Dehumidifier ignored -> outdoor.climate().dehumidifier().range();
            case AirPurifier ignored -> outdoor.climate().airQuality().purifier().range();
        };
    }

    public void invalidateWorld(World world) {
        if (world == null) return;
        UUID worldId = world.getUID();
        for (ClimateVent vent : registry.vents())
            if (vent.system().worldId().equals(worldId)) invalidate(vent.system());
        for (ClimateUnit unit : registry.climateUnits())
            if (unit.unit().worldId().equals(worldId)) invalidate(ZoneId.of(unit));
    }

    /** Invalidates only systems whose maximum possible path can intersect this chunk. */
    public void invalidateChunk(Chunk chunk) {
        if (chunk == null) return;
        Long2ObjectOpenHashMap<List<ClimateVent>> cells = registry.ventCells(chunk.getWorld().getUID());
        if (cells == null) return;
        int shift = registry.ventCellShift();
        int minimumX = chunk.getX() << 4;
        int minimumZ = chunk.getZ() << 4;
        int maximumX = minimumX + 15;
        int maximumZ = minimumZ + 15;
        int maxRange = settings.airflowMaximumRange();
        for (int cellX = (minimumX - maxRange) >> shift; cellX <= (maximumX + maxRange) >> shift; cellX++) {
            for (int cellZ = (minimumZ - maxRange) >> shift; cellZ <= (maximumZ + maxRange) >> shift; cellZ++) {
                List<ClimateVent> bucket = cells.get(HvacRegistry.cellKey(cellX, cellZ));
                if (bucket == null) continue;
                for (ClimateVent vent : bucket) {
                    int dx = vent.position().x() < minimumX ? minimumX - vent.position().x()
                            : Math.max(0, vent.position().x() - maximumX);
                    int dz = vent.position().z() < minimumZ ? minimumZ - vent.position().z()
                            : Math.max(0, vent.position().z() - maximumZ);
                    if (dx + dz <= maxRange) invalidate(vent.system());
                }
            }
        }
        Long2ObjectOpenHashMap<List<ClimateUnit>> zones = registry.climateUnitCells(chunk.getWorld().getUID());
        if (zones == null) return;
        int zoneRange = zoneRange();
        for (int cellX = (minimumX - zoneRange) >> shift; cellX <= (maximumX + zoneRange) >> shift; cellX++) {
            for (int cellZ = (minimumZ - zoneRange) >> shift; cellZ <= (maximumZ + zoneRange) >> shift; cellZ++) {
                List<ClimateUnit> bucket = zones.get(HvacRegistry.cellKey(cellX, cellZ));
                if (bucket == null) continue;
                for (ClimateUnit climateUnit : bucket) {
                    BlockKey unit = climateUnit.unit();
                    int dx = unit.x() < minimumX ? minimumX - unit.x() : Math.max(0, unit.x() - maximumX);
                    int dz = unit.z() < minimumZ ? minimumZ - unit.z() : Math.max(0, unit.z() - maximumZ);
                    if (dx + dz <= zoneRange) invalidate(ZoneId.of(climateUnit));
                }
            }
        }
    }

    /** Drops a removed unit's zone. */
    public void forget(AirflowTarget target) {
        dirty.remove(target);
        dirtySince.remove(target);
        dirtyFirst.remove(target);
        completed.remove(target);
        exposures.remove(target);
        smoke.remove(target);
        if (active != null && active.target.equals(target)) active = null;
    }

    /** Called each server tick; no invocation can inspect more than the configured node budget. */
    public void tick() {
        ticks++;
        int budget = settings.airflowNodesPerTick();
        int started = 0;
        while (budget > 0) {
            if (active == null) {
                if (started >= MAXIMUM_REBUILD_STARTS_PER_TICK) return;
                AirflowTarget target = nextSettledTarget();
                if (target == null) return;
                started++;
                active = begin(target);
                if (active == null) {
                    completed.remove(target);
                    exposures.remove(target);
                    smoke.remove(target);
                    continue;
                }
            }
            active.clearChunkCache();
            int processed = advance(active, budget);
            budget -= Math.max(1, processed);
            if (active.queue.isEmpty() || active.capped) {
                // The finished Rebuild is discarded below, so its map can be
                // handed over directly. Copying it cost ~42 ms in a single
                // tick at the configured cell cap.
                completed.put(active.target, active.distances);
                if (active.water || active.waterCells.isEmpty()) exposures.remove(active.target);
                else exposures.put(active.target, new WaterExposure(active.distances.size(),
                        active.waterCells.toLongArray()));
                if (active.smokeCells.isEmpty()) smoke.remove(active.target);
                else smoke.put(active.target, active.smokeCells.toLongArray());
                visitedCells += active.distances.size();
                completedRebuilds++;
                if (active.capped) cappedRebuilds++;
                active = null;
            }
        }
    }

    /** Returns the oldest dirty system or zone that has stopped changing, or null. */
    private AirflowTarget nextSettledTarget() {
        for (Iterator<AirflowTarget> iterator = dirty.iterator(); iterator.hasNext(); ) {
            AirflowTarget target = iterator.next();
            Long since = dirtySince.get(target);
            Long first = dirtyFirst.get(target);
            boolean settled = since == null || ticks - since >= REBUILD_DEBOUNCE_TICKS;
            boolean overdue = first != null && ticks - first >= MAXIMUM_REBUILD_DEFERRAL_TICKS;
            if (!settled && !overdue) continue;
            iterator.remove();
            dirtySince.remove(target);
            dirtyFirst.remove(target);
            return target;
        }
        return null;
    }

    /**
     * Collects the group's loaded vents first, because the medium they imply
     * decides which blocks the walk may traverse. A single water vent makes the
     * whole system a water system, so a stray trapdoor cannot strand a pool.
     */
    private Rebuild begin(AirflowTarget target) {
        return switch (target) {
            case SystemId system -> begin(system);
            case ZoneId zone -> begin(zone);
        };
    }

    /** A unit's zone starts from the air around its iron block. */
    private Rebuild begin(ZoneId zone) {
        BlockKey unit = zone.unit();
        World world = Bukkit.getWorld(unit.worldId());
        ClimateUnit climateUnit = registry.climateUnitAt(unit);
        if (world == null || climateUnit == null || !world.isChunkLoaded(unit.x() >> 4, unit.z() >> 4)) return null;
        int maximumCells = switch (climateUnit) {
            case Humidifier ignored -> outdoor.climate().humidifier().maximumCells();
            case Dehumidifier ignored -> outdoor.climate().dehumidifier().maximumCells();
            case AirPurifier ignored -> outdoor.climate().airQuality().purifier().maximumCells();
        };
        Rebuild rebuild = new Rebuild(zone, false, rangeOf(climateUnit), maximumCells);
        rebuild.clearChunkCache();
        for (int[] direction : DIRECTIONS)
            enqueue(rebuild, world, unit.x() + direction[0], unit.y() + direction[1], unit.z() + direction[2], 0);
        return rebuild;
    }

    private Rebuild begin(SystemId system) {
        World world = Bukkit.getWorld(system.worldId());
        if (world == null) return null;
        List<BlockKey> seeds = new ArrayList<>();
        boolean hasVent = false;
        boolean water = false;
        for (ClimateVent vent : registry.ventsFor(system)) {
            hasVent = true;
            BlockKey pos = vent.position();
            if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) continue;
            water |= ClimateVent.isWaterVent(world.getBlockAt(pos.x(), pos.y(), pos.z()).getType());
            seeds.add(pos);
        }
        if (!hasVent) return null;
        Rebuild rebuild = new Rebuild(system, water, settings.airflowMaximumRange(), settings.airflowMaximumCells());
        rebuild.clearChunkCache();
        for (BlockKey pos : seeds) {
            int x = pos.x(), y = pos.y(), z = pos.z();
            for (int[] direction : DIRECTIONS)
                enqueue(rebuild, world, x + direction[0], y + direction[1], z + direction[2], 0);
        }
        return rebuild;
    }

    private int advance(Rebuild rebuild, int budget) {
        World world = Bukkit.getWorld(switch (rebuild.target) {
            case SystemId system -> system.worldId();
            case ZoneId zone -> zone.unit().worldId();
        });
        if (world == null) { rebuild.queue.clear(); return 1; }
        int processed = 0;
        while (processed < budget && !rebuild.queue.isEmpty() && !rebuild.capped) {
            Node node = rebuild.queue.removeFirst();
            processed++;
            if (node.distance >= rebuild.range) continue;
            int next = node.distance + 1;
            for (int[] direction : DIRECTIONS)
                enqueue(rebuild, world, node.x + direction[0], node.y + direction[1],
                        node.z + direction[2], next);
        }
        return processed;
    }

    private void enqueue(Rebuild rebuild, World world, int x, int y, int z, int distance) {
        if (rebuild.distances.size() >= rebuild.maximumCells) {
            rebuild.capped = true;
            return;
        }
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return;
        long key = pack(x, y, z);
        byte previous = rebuild.distances.get(key);
        if (previous != ABSENT && previous <= distance) return;
        Chunk chunk = rebuild.chunkAt(world, x >> 4, z >> 4);
        if (chunk == null) return;
        Block block = chunk.getBlock(x & 15, y, z & 15);
        // A branch rather than a predicate field: this runs thousands of times
        // per tick and stays monomorphic this way.
        if (rebuild.water) {
            if (!isWaterPath(block)) return;
        } else {
            // Read once: the air test, water and smoke sources all need it.
            Material type = block.getType();
            if (!isAirPath(block, type)) {
                if (type == Material.WATER) {
                    if (rebuild.waterCells.size() < MAXIMUM_WATER_CELLS) rebuild.waterCells.add(key);
                } else if (rebuild.smokeCells.size() < MAXIMUM_SMOKE_CELLS && SmokeSource.of(type) != null) {
                    rebuild.smokeCells.add(key);
                }
                return;
            }
            // Fire is open air the walk passes through, and smoke where it burns.
            if ((type == Material.FIRE || type == Material.SOUL_FIRE)
                    && rebuild.smokeCells.size() < MAXIMUM_SMOKE_CELLS) rebuild.smokeCells.add(key);
        }
        rebuild.distances.put(key, (byte) distance);
        rebuild.queue.addLast(new Node(x, y, z, distance));
    }

    static boolean isAirPath(Block block) {
        return isAirPath(block, block.getType());
    }

    private static boolean isAirPath(Block block, Material type) {
        // Most cells in a ducted volume are plain air, and getBlockData()
        // allocates a fresh copy on every call. Answer air without it.
        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) return true;
        if (block.isLiquid()) return false;
        if (isOpenable(type) && block.getBlockData() instanceof Openable openable)
            return openable.isOpen();
        // A carpet is a sixteenth of a block of cloth on the floor, but the game
        // gives it collision, so it counted as solid: a carpeted floor left a
        // hole in the airflow exactly where players stand.
        if (isCarpet(type)) return true;
        return block.isPassable();
    }

    static boolean isCarpet(Material type) {
        return Tag.WOOL_CARPETS.isTagged(type) || type == Material.MOSS_CARPET;
    }

    /**
     * Materials whose block data can be Openable. Barrels are included because
     * Bukkit models their lid as Openable, and the original walk treated an
     * open barrel as a path; keeping it preserves that behaviour exactly.
     */
    /**
     * Water systems traverse water and nothing else. Checking the material
     * alone keeps this cheaper than the air walk: no block data is allocated,
     * so source and flowing water both conduct without a Levelled lookup.
     */
    static boolean isWaterPath(Block block) {
        return block.getType() == Material.WATER;
    }

    private static boolean isOpenable(Material type) {
        return Tag.TRAPDOORS.isTagged(type) || Tag.DOORS.isTagged(type)
                || Tag.FENCE_GATES.isTagged(type) || type == Material.BARREL;
    }

    public TemperatureReading temperatureAt(Location location) {
        return temperatureAt(location, null);
    }

    /**
     * The climate a player feels, from where their feet and eyes are. In water:
     * the eyes when submerged, else the feet (wading, swimming at the surface).
     * In air: head height first. A player standing on a carpet, slab, stairs,
     * or snow has their feet inside that block, which air does not fill, so a
     * feet-only reading used to show the outdoor temperature while their head
     * was in a conditioned room. The feet are the fallback.
     */
    public TemperatureReading temperatureForBody(Location feet, Location eyes) {
        if (eyes.getBlock().getType() == Material.WATER) return temperatureAt(eyes);
        if (feet.getBlock().getType() == Material.WATER) return temperatureAt(feet);
        TemperatureReading head = temperatureAt(eyes);
        if (head.conditioned()) return head;
        TemperatureReading foot = temperatureAt(feet);
        return foot.conditioned() ? foot : head;
    }

    public TemperatureReading temperatureAt(Location location, SystemId requiredSystem) {
        OutdoorTemperatureService.Outdoor out = outdoor.climateAt(location);
        double outside = out.airF();
        if (location == null || location.getWorld() == null
                || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return reading(null, outside, out, 0.0, -1, null, null, false);
        }
        // Water keeps its own steady temperature, so a spot in water blends
        // toward the biome's natural water rather than toward the air above it.
        boolean water = location.getBlock().getType() == Material.WATER;
        double baseline = water ? out.waterF() : outside;
        long key = pack(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Nearest nearest = nearestSystem(candidateSystems(location.getWorld().getUID(),
                location.getBlockX(), location.getBlockZ()), key, requiredSystem);
        SystemId best = nearest == null ? null : nearest.system();
        int bestDistance = nearest == null ? Integer.MAX_VALUE : nearest.distance();
        if (best == null) {
            if (requiredSystem != null && !hasVents(requiredSystem)) {
                Thermostat thermostat = registry.thermostatFor(requiredSystem);
                if (thermostat != null)
                    return reading(location, thermostat.roomF(), out, 1.0, -1, requiredSystem, thermostat, water);
            }
            return reading(location, baseline, out, 0.0, -1, null, null, water);
        }
        Thermostat thermostat = registry.thermostatFor(best);
        if (thermostat == null)
            return reading(location, baseline, out, 0.0, bestDistance, best, null, water);
        double influence = influenceForDistance(bestDistance);
        return reading(location, baseline + (thermostat.roomF() - baseline) * influence,
                out, influence, bestDistance, best, thermostat, water);
    }

    /**
     * Completes a reading with humidity and feels-like. Moisture blends from
     * the outdoors toward the system's air by the same influence temperature
     * does; the RH then follows from the blended temperature. Wind only
     * reaches the parts of a spot the system does not condition. The spot's
     * own small variance is added last, to the reading only. The outdoor air
     * at the spot varies with the same pattern, at the outdoor spread, so a
     * thermometer standing outside shows one temperature on both lines.
     */
    private TemperatureReading reading(Location location, double localF, OutdoorTemperatureService.Outdoor out,
                                       double influence, int distance, SystemId system, Thermostat thermostat,
                                       boolean water) {
        ClimateSettings.Variance variance = outdoor.climate().variance();
        boolean varies = location != null && location.getWorld() != null && variance.enabled();
        double hours = varies ? location.getWorld().getGameTime() / ThermalModel.MINECRAFT_TICKS_PER_HOUR : 0.0;
        long seed = varies ? location.getWorld().getUID().getMostSignificantBits()
                ^ location.getWorld().getUID().getLeastSignificantBits() : 0L;
        // The spot's temperature pattern at a spread of 1 F, scaled to each medium's spread below.
        double pattern = varies ? Microvariance.offsetF(variance, 1.0, seed, Microvariance.TEMPERATURE,
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), hours) : 0.0;
        double outdoorF = out.airF() + variance.outdoorF() * pattern;
        if (water) {
            double poolInfluence = thermostat != null && thermostat.pool() ? influence : 0.0;
            localF += Microvariance.waterSpreadF(variance, poolInfluence) * pattern;
            return new TemperatureReading(localF, outdoorF, influence, distance, system, thermostat,
                    true, Double.NaN, localF, out.weather());
        }
        double dew = out.dewPointF();
        // The air blends from the outdoors toward the room's, or a standalone unit's zone's, like moisture (3.2).
        AirQualityModel.Air air = out.air();
        boolean room = thermostat != null && !thermostat.pool() && influence > 0.0;
        if (room) air = air.toward(thermostat.roomAir().known() ? thermostat.roomAir() : air, influence);
        if (room && thermostat.hasRoomDewPoint()) {
            dew += (thermostat.roomDewPointF() - dew) * influence;
        } else if (location != null) {
            // No system conditions this air, but a standalone unit may be drying or humidifying it.
            ZoneReading zone = zoneAt(location.getWorld().getUID(),
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
            if (zone != null && zone.unit().servedSystem() == null) {
                if (Double.isFinite(zone.unit().zoneDewPointF()))
                    dew += (zone.unit().zoneDewPointF() - dew) * zone.influence();
                if (!room && zone.unit().zoneAir().known())
                    air = air.toward(zone.unit().zoneAir(), zone.influence());
            }
        }
        if (varies) {
            double airInfluence = thermostat != null && !thermostat.pool() ? influence : 0.0;
            localF += Microvariance.airSpreadF(variance, airInfluence) * pattern;
            dew = Math.min(localF, dew + Microvariance.offsetF(variance, variance.dewPointF(), seed,
                    Microvariance.DEW_POINT, location.getBlockX(), location.getBlockY(), location.getBlockZ(), hours));
        }
        double humidity = Psychrometrics.relativeHumidity(localF, dew);
        double feelsLike = Psychrometrics.apparentTemperatureF(localF, humidity, out.windMs() * (1.0 - influence));
        return new TemperatureReading(localF, outdoorF, influence, distance, system, thermostat,
                false, humidity, feelsLike, out.weather(), air);
    }

    /**
     * The temperature of the water at a block: a pool system's where one
     * reaches it, blended toward natural water by influence, else natural
     * water. Only what evaporation needs, without the humidity and feels-like
     * work of a full reading.
     */
    public double waterTemperatureAt(World world, int x, int y, int z) {
        double natural = outdoor.naturalWaterAt(new Location(world, x, y, z));
        Nearest nearest = nearestSystem(candidateSystems(world.getUID(), x, z), pack(x, y, z), null);
        if (nearest == null) return natural;
        Thermostat thermostat = registry.thermostatFor(nearest.system());
        if (thermostat == null) return natural;
        return natural + (thermostat.roomF() - natural) * influenceForDistance(nearest.distance());
    }

    /** The water a system's air (or a unit's zone) touches, from its last completed map. */
    public WaterExposure waterExposure(AirflowTarget target) {
        return exposures.getOrDefault(target, WaterExposure.NONE);
    }

    /** Packed positions of the smoke sources a space's last scan found (3.2), burning or not. */
    public long[] smokeCells(AirflowTarget target) {
        return smoke.getOrDefault(target, NO_CELLS);
    }

    /** Cells in a target's last completed map; 0 when it has none yet. */
    public int mapSize(AirflowTarget target) {
        Long2ByteMap map = completed.get(target);
        return map == null ? 0 : map.size();
    }

    /** The system whose completed map reaches this block soonest, or null. */
    public SystemId systemAt(UUID worldId, int x, int y, int z) {
        Nearest nearest = nearestSystem(candidateSystems(worldId, x, z), pack(x, y, z), null);
        return nearest == null ? null : nearest.system();
    }

    /**
     * The unit zone that reaches this block soonest, or null. Influence is
     * full over the first half of that unit's range and fades to its edge.
     */
    public ZoneReading zoneAt(UUID worldId, int x, int y, int z) {
        Long2ObjectOpenHashMap<List<ClimateUnit>> cells = registry.climateUnitCells(worldId);
        if (cells == null) return null;
        int shift = registry.ventCellShift();
        int reach = zoneRange() + 2;
        long key = pack(x, y, z);
        ClimateUnit best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int cellX = (x - reach) >> shift; cellX <= (x + reach) >> shift; cellX++) {
            for (int cellZ = (z - reach) >> shift; cellZ <= (z + reach) >> shift; cellZ++) {
                List<ClimateUnit> bucket = cells.get(HvacRegistry.cellKey(cellX, cellZ));
                if (bucket == null) continue;
                for (ClimateUnit unit : bucket) {
                    Long2ByteMap map = completed.get(ZoneId.of(unit));
                    if (map == null) continue;
                    byte distance = map.get(key);
                    if (distance == ABSENT) continue;
                    if (distance < bestDistance || (distance == bestDistance
                            && unit.position().compareTo(best.position()) < 0)) {
                        best = unit;
                        bestDistance = distance;
                    }
                }
            }
        }
        if (best == null) return null;
        int range = rangeOf(best);
        double core = range / 2.0;
        double influence = bestDistance <= core ? 1.0 : Math.max(0.0, (range - bestDistance) / (range - core));
        return new ZoneReading(best, bestDistance, influence);
    }

    /**
     * The other units this unit shares air with: its zone reaches the air
     * beside their iron block, or theirs reaches the air beside its own.
     * From completed maps only.
     */
    public List<ClimateUnit> unitsSharingAir(ClimateUnit unit) {
        BlockKey block = unit.unit();
        Long2ObjectOpenHashMap<List<ClimateUnit>> cells = registry.climateUnitCells(block.worldId());
        if (cells == null) return List.of();
        Long2ByteMap own = completed.get(ZoneId.of(unit));
        int shift = registry.ventCellShift();
        int reach = zoneRange() + 2;
        List<ClimateUnit> sharing = new ArrayList<>();
        for (int cellX = (block.x() - reach) >> shift; cellX <= (block.x() + reach) >> shift; cellX++) {
            for (int cellZ = (block.z() - reach) >> shift; cellZ <= (block.z() + reach) >> shift; cellZ++) {
                List<ClimateUnit> bucket = cells.get(HvacRegistry.cellKey(cellX, cellZ));
                if (bucket == null) continue;
                for (ClimateUnit other : bucket) {
                    if (other == unit) continue;
                    if (reachesBeside(own, other.unit()) || reachesBeside(completed.get(ZoneId.of(other)), block))
                        sharing.add(other);
                }
            }
        }
        return sharing;
    }

    private static boolean reachesBeside(Long2ByteMap map, BlockKey block) {
        if (map == null) return false;
        return map.containsKey(pack(block.x() + 1, block.y(), block.z()))
                || map.containsKey(pack(block.x() - 1, block.y(), block.z()))
                || map.containsKey(pack(block.x(), block.y() + 1, block.z()))
                || map.containsKey(pack(block.x(), block.y() - 1, block.z()))
                || map.containsKey(pack(block.x(), block.y(), block.z() + 1))
                || map.containsKey(pack(block.x(), block.y(), block.z() - 1));
    }

    /** Unpacks a position packed by the airflow maps, for callers outside this class. */
    public static int unpackX(long key) { return (int) (key >> 38); }
    public static int unpackY(long key) { return (int) (key << 52 >> 52); }
    public static int unpackZ(long key) { return (int) (key << 26 >> 38); }

    record Nearest(SystemId system, int distance) {}

    /**
     * Groups whose airflow map could contain a block at x/z: those with a vent
     * in the grid cells around it. Every cell of a map lies within the maximum
     * range (+1 for the vent's own neighbour) of one of its group's vents, so
     * this finds every group a scan of all maps would, without visiting the
     * maps of systems elsewhere in the world. Before 1.8 every lookup scanned
     * them all, which a HUD refreshing many players would multiply.
     */
    Set<SystemId> candidateSystems(UUID worldId, int blockX, int blockZ) {
        Long2ObjectOpenHashMap<List<ClimateVent>> cells = registry.ventCells(worldId);
        if (cells == null) return Set.of();
        int reach = settings.airflowMaximumRange() + 2;
        int shift = registry.ventCellShift();
        Set<SystemId> systems = new HashSet<>();
        for (int cellX = (blockX - reach) >> shift; cellX <= (blockX + reach) >> shift; cellX++) {
            for (int cellZ = (blockZ - reach) >> shift; cellZ <= (blockZ + reach) >> shift; cellZ++) {
                List<ClimateVent> bucket = cells.get(HvacRegistry.cellKey(cellX, cellZ));
                if (bucket == null) continue;
                for (ClimateVent vent : bucket) systems.add(vent.system());
            }
        }
        return systems;
    }

    /** The candidate whose completed map reaches the block soonest; ties go to the lower GroupId. */
    Nearest nearestSystem(Iterable<SystemId> candidates, long key, SystemId requiredSystem) {
        SystemId best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (SystemId system : candidates) {
            if (requiredSystem != null && !requiredSystem.equals(system)) continue;
            Long2ByteMap map = completed.get(system);
            if (map == null) continue;
            byte rawDistance = map.get(key);
            if (rawDistance == ABSENT) continue;
            int distance = rawDistance;
            if (distance < bestDistance || (distance == bestDistance && (best == null || system.compareTo(best) < 0))) {
                best = system;
                bestDistance = distance;
            }
        }
        return best == null ? null : new Nearest(best, bestDistance);
    }

    /** Test support: installs a finished airflow map as if a rebuild had produced it. */
    void putCompletedMap(SystemId system, Long2ByteMap map) {
        completed.put(system, map);
    }

    /** Test support: the old full scan over every completed map in the world. */
    Nearest nearestSystemByFullScan(UUID worldId, long key, SystemId requiredSystem) {
        List<SystemId> all = new ArrayList<>();
        for (AirflowTarget target : completed.keySet())
            if (target instanceof SystemId system && system.worldId().equals(worldId)) all.add(system);
        return nearestSystem(all, key, requiredSystem);
    }

    public double influenceForDistance(int distance) {
        if (distance < 0 || distance > settings.airflowMaximumRange()) return 0.0;
        if (distance <= settings.airflowCoreRange()) return 1.0;
        return (settings.airflowMaximumRange() - distance)
                / (double) (settings.airflowMaximumRange() - settings.airflowCoreRange());
    }

    private boolean hasVents(SystemId system) {
        return !registry.ventsFor(system).isEmpty();
    }

    public String stats() {
        int cells = completed.values().stream().mapToInt(Long2ByteMap::size).sum();
        return "vents=" + registry.vents().size() + ", cachedCells=" + cells
                + ", dirty=" + dirty.size() + ", rebuilding=" + (active != null)
                + ", completed=" + completedRebuilds + ", visited=" + visitedCells
                + ", capped=" + cappedRebuilds;
    }

    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | ((long) y & 0xFFFL);
    }
}
