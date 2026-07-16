package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;

import java.util.*;

/** Event-invalidated, tick-budgeted airflow maps for registered vent systems. */
public final class AirflowService {
    public record TemperatureReading(double localF, double outdoorF, double influence,
                                     int airflowDistance, GroupId group, Thermostat thermostat) {
        public boolean conditioned() { return group != null && thermostat != null && influence > 0.0; }
    }

    private record Node(int x, int y, int z, int distance) {}

    private static final class Rebuild {
        final GroupId group;
        final ArrayDeque<Node> queue = new ArrayDeque<>();
        final Map<Long, Byte> distances = new HashMap<>();
        boolean capped;
        Rebuild(GroupId group) { this.group = group; }
    }

    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final HvacRegistry registry;
    private final OutdoorTemperatureService outdoor;
    private final HvacSettings settings;
    private final Map<GroupId, Map<Long, Byte>> completed = new HashMap<>();
    private final LinkedHashSet<GroupId> dirty = new LinkedHashSet<>();
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
        if (active != null && active.group.equals(group)) active = null;
    }

    public void invalidateNear(Location location) {
        if (location == null || location.getWorld() == null) return;
        UUID worldId = location.getWorld().getUID();
        long packed = pack(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        double radiusSquared = Math.pow(settings.airflowMaximumRange() + 2.0, 2.0);
        Set<GroupId> groups = new HashSet<>();
        for (ClimateVent vent : registry.vents()) groups.add(vent.group());
        for (GroupId group : groups) {
            if (!group.worldId().equals(worldId)) continue;
            Map<Long, Byte> cache = completed.get(group);
            boolean affected = cache != null && cache.containsKey(packed);
            if (!affected) {
                for (ClimateVent vent : registry.vents()) {
                    if (!vent.group().equals(group)) continue;
                    Location ventLocation = vent.position().location();
                    if (ventLocation != null && ventLocation.getWorld().equals(location.getWorld())
                            && ventLocation.distanceSquared(location) <= radiusSquared) {
                        affected = true;
                        break;
                    }
                }
            }
            if (affected) invalidate(group);
        }
    }

    public void invalidateWorld(World world) {
        if (world == null) return;
        for (ClimateVent vent : registry.vents())
            if (vent.group().worldId().equals(world.getUID())) invalidate(vent.group());
    }

    /** Invalidates only systems whose maximum possible path can intersect this chunk. */
    public void invalidateChunk(Chunk chunk) {
        if (chunk == null) return;
        int minimumX = chunk.getX() << 4;
        int minimumZ = chunk.getZ() << 4;
        int maximumX = minimumX + 15;
        int maximumZ = minimumZ + 15;
        for (ClimateVent vent : registry.vents()) {
            if (!vent.group().worldId().equals(chunk.getWorld().getUID())) continue;
            int dx = vent.position().x() < minimumX ? minimumX - vent.position().x()
                    : Math.max(0, vent.position().x() - maximumX);
            int dz = vent.position().z() < minimumZ ? minimumZ - vent.position().z()
                    : Math.max(0, vent.position().z() - maximumZ);
            if (dx + dz <= settings.airflowMaximumRange()) invalidate(vent.group());
        }
    }

    /** Called each server tick; no invocation can inspect more than the configured node budget. */
    public void tick() {
        int budget = settings.airflowNodesPerTick();
        while (budget > 0) {
            if (active == null) {
                Iterator<GroupId> iterator = dirty.iterator();
                if (!iterator.hasNext()) return;
                GroupId group = iterator.next();
                iterator.remove();
                active = begin(group);
                if (active == null) {
                    completed.remove(group);
                    continue;
                }
            }
            int processed = advance(active, budget);
            budget -= Math.max(1, processed);
            if (active.queue.isEmpty() || active.capped) {
                completed.put(active.group, Map.copyOf(active.distances));
                visitedCells += active.distances.size();
                completedRebuilds++;
                if (active.capped) cappedRebuilds++;
                active = null;
            }
        }
    }

    private Rebuild begin(GroupId group) {
        World world = Bukkit.getWorld(group.worldId());
        if (world == null) return null;
        Rebuild rebuild = new Rebuild(group);
        boolean hasVent = false;
        for (ClimateVent vent : registry.vents()) {
            if (!vent.group().equals(group)) continue;
            hasVent = true;
            if (!vent.position().isChunkLoaded()) continue;
            int x = vent.position().x(), y = vent.position().y(), z = vent.position().z();
            for (int[] direction : DIRECTIONS)
                enqueue(rebuild, world, x + direction[0], y + direction[1], z + direction[2], 0);
        }
        return hasVent ? rebuild : null;
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
        if (y < world.getMinHeight() || y >= world.getMaxHeight()
                || !world.isChunkLoaded(x >> 4, z >> 4)) return;
        long key = pack(x, y, z);
        Byte previous = rebuild.distances.get(key);
        if (previous != null && Byte.toUnsignedInt(previous) <= distance) return;
        Block block = world.getBlockAt(x, y, z);
        if (!isAirPath(block)) return;
        rebuild.distances.put(key, (byte) distance);
        rebuild.queue.addLast(new Node(x, y, z, distance));
    }

    static boolean isAirPath(Block block) {
        if (block.isLiquid()) return false;
        if (block.getBlockData() instanceof Openable openable) return openable.isOpen();
        return block.isPassable();
    }

    public TemperatureReading temperatureAt(Location location) {
        return temperatureAt(location, null);
    }

    public TemperatureReading temperatureAt(Location location, GroupId requiredGroup) {
        double outside = outdoor.temperatureAt(location);
        if (location == null || location.getWorld() == null
                || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return new TemperatureReading(outside, outside, 0.0, -1, null, null);
        }
        long key = pack(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        GroupId best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<GroupId, Map<Long, Byte>> entry : completed.entrySet()) {
            GroupId group = entry.getKey();
            if (!group.worldId().equals(location.getWorld().getUID())) continue;
            if (requiredGroup != null && !requiredGroup.equals(group)) continue;
            Byte rawDistance = entry.getValue().get(key);
            if (rawDistance == null) continue;
            int distance = Byte.toUnsignedInt(rawDistance);
            if (distance < bestDistance || (distance == bestDistance && (best == null || group.compareTo(best) < 0))) {
                best = group;
                bestDistance = distance;
            }
        }
        if (best == null) {
            if (requiredGroup != null && !hasVents(requiredGroup)) {
                Thermostat thermostat = registry.thermostatFor(requiredGroup);
                if (thermostat != null)
                    return new TemperatureReading(thermostat.roomF(), outside, 1.0, -1,
                            requiredGroup, thermostat);
            }
            return new TemperatureReading(outside, outside, 0.0, -1, null, null);
        }
        Thermostat thermostat = registry.thermostatFor(best);
        if (thermostat == null)
            return new TemperatureReading(outside, outside, 0.0, bestDistance, best, null);
        double influence = influenceForDistance(bestDistance);
        return new TemperatureReading(outside + (thermostat.roomF() - outside) * influence,
                outside, influence, bestDistance, best, thermostat);
    }

    public double influenceForDistance(int distance) {
        if (distance < 0 || distance > settings.airflowMaximumRange()) return 0.0;
        if (distance <= settings.airflowCoreRange()) return 1.0;
        return (settings.airflowMaximumRange() - distance)
                / (double) (settings.airflowMaximumRange() - settings.airflowCoreRange());
    }

    private boolean hasVents(GroupId group) {
        for (ClimateVent vent : registry.vents()) if (vent.group().equals(group)) return true;
        return false;
    }

    public String stats() {
        int cells = completed.values().stream().mapToInt(Map::size).sum();
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
