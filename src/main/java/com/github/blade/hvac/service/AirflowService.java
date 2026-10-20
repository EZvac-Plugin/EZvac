package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
     * @param localF   temperature at the spot: the air there, or the water when {@code water}
     * @param outdoorF the outdoor air temperature, whatever the spot's medium
     * @param water    true when the spot is in water; unconditioned water reads the
     *                 biome's natural water temperature rather than the air's
     */
    public record TemperatureReading(double localF, double outdoorF, double influence,
                                     int airflowDistance, GroupId group, Thermostat thermostat,
                                     boolean water) {
        public boolean conditioned() { return group != null && thermostat != null && influence > 0.0; }
    }

    private record Node(int x, int y, int z, int distance) {}

    private static final class Rebuild {
        final GroupId group;
        /** Water systems traverse water; air systems traverse open air. */
        final boolean water;
        final ArrayDeque<Node> queue = new ArrayDeque<>();
        final Long2ByteOpenHashMap distances = new Long2ByteOpenHashMap();
        boolean capped;
        // Chunk lookups dominate the walk, and a breadth-first frontier stays
        // within a chunk for long runs. The cache is cleared every tick so a
        // reference can never outlive the chunk's residency.
        private int cachedChunkX;
        private int cachedChunkZ;
        private Chunk cachedChunk;
        private boolean chunkCached;

        Rebuild(GroupId group, boolean water) {
            this.group = group;
            this.water = water;
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
    private final Map<GroupId, Long2ByteMap> completed = new HashMap<>();
    private final LinkedHashSet<GroupId> dirty = new LinkedHashSet<>();
    private final Map<GroupId, Long> dirtySince = new HashMap<>();
    private final Map<GroupId, Long> dirtyFirst = new HashMap<>();
    private long ticks;
    private Rebuild active;
    private long completedRebuilds;
    private long visitedCells;
    private long cappedRebuilds;


    public AirflowService(HvacRegistry registry, OutdoorTemperatureService outdoor, HvacSettings settings) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.settings = Objects.requireNonNull(settings, "settings");
        for (ClimateVent vent : registry.vents()) dirty.add(vent.group());
    }

    public void invalidate(GroupId group) {
        dirty.add(group);
        dirtySince.put(group, ticks);
        dirtyFirst.putIfAbsent(group, ticks);
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

        Set<GroupId> affected = null;
        for (int cellX = (blockX - maxRange) >> shift; cellX <= (blockX + maxRange) >> shift; cellX++) {
            for (int cellZ = (blockZ - maxRange) >> shift; cellZ <= (blockZ + maxRange) >> shift; cellZ++) {
                List<ClimateVent> bucket = cells.get(HvacRegistry.cellKey(cellX, cellZ));
                if (bucket == null) continue;
                for (ClimateVent vent : bucket) {
                    BlockKey pos = vent.position();
                    GroupId group = vent.group();
                    if (affected != null && affected.contains(group)) continue;

                    Long2ByteMap cache = completed.get(group);
                    if (cache != null && cache.containsKey(packed)) {
                        if (affected == null) affected = new HashSet<>();
                        affected.add(group);
                        continue;
                    }

                    int dx = pos.x() - blockX;
                    int dy = pos.y() - blockY;
                    int dz = pos.z() - blockZ;
                    int distSq = dx * dx + dy * dy + dz * dz;

                    if (distSq <= maxRangeSq) {
                        if (affected == null) affected = new HashSet<>();
                        affected.add(group);
                    }
                }
            }
        }

        if (affected != null) {
            for (GroupId group : affected) invalidate(group);
        }
    }

    public void invalidateWorld(World world) {
        if (world == null) return;
        UUID worldId = world.getUID();
        for (ClimateVent vent : registry.vents())
            if (vent.group().worldId().equals(worldId)) invalidate(vent.group());
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
                    if (dx + dz <= maxRange) invalidate(vent.group());
                }
            }
        }
    }

    /** Called each server tick; no invocation can inspect more than the configured node budget. */
    public void tick() {
        ticks++;
        int budget = settings.airflowNodesPerTick();
        int started = 0;
        while (budget > 0) {
            if (active == null) {
                if (started >= MAXIMUM_REBUILD_STARTS_PER_TICK) return;
                GroupId group = nextSettledGroup();
                if (group == null) return;
                started++;
                active = begin(group);
                if (active == null) {
                    completed.remove(group);
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
                completed.put(active.group, active.distances);
                visitedCells += active.distances.size();
                completedRebuilds++;
                if (active.capped) cappedRebuilds++;
                active = null;
            }
        }
    }

    /** Returns the oldest dirty system that has stopped changing, or null. */
    private GroupId nextSettledGroup() {
        for (Iterator<GroupId> iterator = dirty.iterator(); iterator.hasNext(); ) {
            GroupId group = iterator.next();
            Long since = dirtySince.get(group);
            Long first = dirtyFirst.get(group);
            boolean settled = since == null || ticks - since >= REBUILD_DEBOUNCE_TICKS;
            boolean overdue = first != null && ticks - first >= MAXIMUM_REBUILD_DEFERRAL_TICKS;
            if (!settled && !overdue) continue;
            iterator.remove();
            dirtySince.remove(group);
            dirtyFirst.remove(group);
            return group;
        }
        return null;
    }

    /**
     * Collects the group's loaded vents first, because the medium they imply
     * decides which blocks the walk may traverse. A single water vent makes the
     * whole system a water system, so a stray trapdoor cannot strand a pool.
     */
    private Rebuild begin(GroupId group) {
        World world = Bukkit.getWorld(group.worldId());
        if (world == null) return null;
        List<BlockKey> seeds = new ArrayList<>();
        boolean hasVent = false;
        boolean water = false;
        for (ClimateVent vent : registry.ventsFor(group)) {
            hasVent = true;
            BlockKey pos = vent.position();
            if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) continue;
            water |= ClimateVent.isWaterVent(world.getBlockAt(pos.x(), pos.y(), pos.z()).getType());
            seeds.add(pos);
        }
        if (!hasVent) return null;
        Rebuild rebuild = new Rebuild(group, water);
        rebuild.clearChunkCache();
        for (BlockKey pos : seeds) {
            int x = pos.x(), y = pos.y(), z = pos.z();
            for (int[] direction : DIRECTIONS)
                enqueue(rebuild, world, x + direction[0], y + direction[1], z + direction[2], 0);
        }
        return rebuild;
    }

    private int advance(Rebuild rebuild, int budget) {
        World world = Bukkit.getWorld(rebuild.group.worldId());
        if (world == null) { rebuild.queue.clear(); return 1; }
        int processed = 0;
        while (processed < budget && !rebuild.queue.isEmpty() && !rebuild.capped) {
            Node node = rebuild.queue.removeFirst();
            processed++;
            if (node.distance >= settings.airflowMaximumRange()) continue;
            int next = node.distance + 1;
            for (int[] direction : DIRECTIONS)
                enqueue(rebuild, world, node.x + direction[0], node.y + direction[1],
                        node.z + direction[2], next);
        }
        return processed;
    }

    private void enqueue(Rebuild rebuild, World world, int x, int y, int z, int distance) {
        if (rebuild.distances.size() >= settings.airflowMaximumCells()) {
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
        if (!(rebuild.water ? isWaterPath(block) : isAirPath(block))) return;
        rebuild.distances.put(key, (byte) distance);
        rebuild.queue.addLast(new Node(x, y, z, distance));
    }

    static boolean isAirPath(Block block) {
        Material type = block.getType();
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

    public TemperatureReading temperatureAt(Location location, GroupId requiredGroup) {
        double outside = outdoor.temperatureAt(location);
        if (location == null || location.getWorld() == null
                || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return new TemperatureReading(outside, outside, 0.0, -1, null, null, false);
        }
        // Water keeps its own steady temperature, so a spot in water blends
        // toward the biome's natural water rather than toward the air above it.
        boolean water = location.getBlock().getType() == Material.WATER;
        double baseline = water ? outdoor.naturalWaterAt(location) : outside;
        long key = pack(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Nearest nearest = nearestGroup(candidateGroups(location.getWorld().getUID(),
                location.getBlockX(), location.getBlockZ()), key, requiredGroup);
        GroupId best = nearest == null ? null : nearest.group();
        int bestDistance = nearest == null ? Integer.MAX_VALUE : nearest.distance();
        if (best == null) {
            if (requiredGroup != null && !hasVents(requiredGroup)) {
                Thermostat thermostat = registry.thermostatFor(requiredGroup);
                if (thermostat != null)
                    return new TemperatureReading(thermostat.roomF(), outside, 1.0, -1,
                            requiredGroup, thermostat, water);
            }
            return new TemperatureReading(baseline, outside, 0.0, -1, null, null, water);
        }
        Thermostat thermostat = registry.thermostatFor(best);
        if (thermostat == null)
            return new TemperatureReading(baseline, outside, 0.0, bestDistance, best, null, water);
        double influence = influenceForDistance(bestDistance);
        return new TemperatureReading(baseline + (thermostat.roomF() - baseline) * influence,
                outside, influence, bestDistance, best, thermostat, water);
    }

    record Nearest(GroupId group, int distance) {}

    /**
     * Groups whose airflow map could contain a block at x/z: those with a vent
     * in the grid cells around it. Every cell of a map lies within the maximum
     * range (+1 for the vent's own neighbour) of one of its group's vents, so
     * this finds every group a scan of all maps would, without visiting the
     * maps of systems elsewhere in the world. Before 1.8 every lookup scanned
     * them all, which a HUD refreshing many players would multiply.
     */
    Set<GroupId> candidateGroups(UUID worldId, int blockX, int blockZ) {
        Long2ObjectOpenHashMap<List<ClimateVent>> cells = registry.ventCells(worldId);
        if (cells == null) return Set.of();
        int reach = settings.airflowMaximumRange() + 2;
        int shift = registry.ventCellShift();
        Set<GroupId> groups = new HashSet<>();
        for (int cellX = (blockX - reach) >> shift; cellX <= (blockX + reach) >> shift; cellX++) {
            for (int cellZ = (blockZ - reach) >> shift; cellZ <= (blockZ + reach) >> shift; cellZ++) {
                List<ClimateVent> bucket = cells.get(HvacRegistry.cellKey(cellX, cellZ));
                if (bucket == null) continue;
                for (ClimateVent vent : bucket) groups.add(vent.group());
            }
        }
        return groups;
    }

    /** The candidate whose completed map reaches the block soonest; ties go to the lower GroupId. */
    Nearest nearestGroup(Iterable<GroupId> candidates, long key, GroupId requiredGroup) {
        GroupId best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (GroupId group : candidates) {
            if (requiredGroup != null && !requiredGroup.equals(group)) continue;
            Long2ByteMap map = completed.get(group);
            if (map == null) continue;
            byte rawDistance = map.get(key);
            if (rawDistance == ABSENT) continue;
            int distance = rawDistance;
            if (distance < bestDistance || (distance == bestDistance && (best == null || group.compareTo(best) < 0))) {
                best = group;
                bestDistance = distance;
            }
        }
        return best == null ? null : new Nearest(best, bestDistance);
    }

    /** Test support: installs a finished airflow map as if a rebuild had produced it. */
    void putCompletedMap(GroupId group, Long2ByteMap map) {
        completed.put(group, map);
    }

    /** Test support: the old full scan over every completed map in the world. */
    Nearest nearestGroupByFullScan(UUID worldId, long key, GroupId requiredGroup) {
        List<GroupId> all = new ArrayList<>();
        for (GroupId group : completed.keySet()) if (group.worldId().equals(worldId)) all.add(group);
        return nearestGroup(all, key, requiredGroup);
    }

    public double influenceForDistance(int distance) {
        if (distance < 0 || distance > settings.airflowMaximumRange()) return 0.0;
        if (distance <= settings.airflowCoreRange()) return 1.0;
        return (settings.airflowMaximumRange() - distance)
                / (double) (settings.airflowMaximumRange() - settings.airflowCoreRange());
    }

    private boolean hasVents(GroupId group) {
        return !registry.ventsFor(group).isEmpty();
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
