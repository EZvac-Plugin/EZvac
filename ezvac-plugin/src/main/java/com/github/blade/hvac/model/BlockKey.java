package com.github.blade.hvac.model;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable block identity that remains valid while a world is unloaded. */
public record BlockKey(UUID worldId, int x, int y, int z) implements Comparable<BlockKey> {
    public BlockKey {
        Objects.requireNonNull(worldId, "worldId");
    }

    public static BlockKey of(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new BlockKey(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockKey read(Map<String, Object> map, String prefix) {
        String uuidKey = key(prefix, "worldUuid");
        String worldKey = key(prefix, "world");
        UUID worldId = parseUuid(map.get(uuidKey));
        if (worldId == null) {
            String name = text(map.get(worldKey));
            Server server = Bukkit.getServer();
            World world = name == null || server == null ? null : server.getWorld(name);
            if (world != null) worldId = world.getUID();
        }
        if (worldId == null) throw new IllegalArgumentException("world identity is missing");
        return new BlockKey(worldId, integer(map.get(key(prefix, "x"))), integer(map.get(key(prefix, "y"))),
                integer(map.get(key(prefix, "z"))));
    }

    public void write(Map<String, Object> map, String prefix) {
        map.put(key(prefix, "worldUuid"), worldId.toString());
        World world = world();
        if (world != null) map.put(key(prefix, "world"), world.getName());
        map.put(key(prefix, "x"), x);
        map.put(key(prefix, "y"), y);
        map.put(key(prefix, "z"), z);
    }

    public World world() {
        Server server = Bukkit.getServer();
        return server == null ? null : server.getWorld(worldId);
    }

    public Location location() {
        World world = world();
        return world == null ? null : new Location(world, x, y, z);
    }

    public boolean isChunkLoaded() {
        World world = world();
        return world != null && world.isChunkLoaded(x >> 4, z >> 4);
    }

    public boolean isIn(Chunk chunk) {
        return chunk != null && worldId.equals(chunk.getWorld().getUID())
                && (x >> 4) == chunk.getX() && (z >> 4) == chunk.getZ();
    }

    @Override
    public int compareTo(BlockKey other) {
        int comparison = worldId.compareTo(other.worldId);
        if (comparison != 0) return comparison;
        comparison = Integer.compare(x, other.x);
        if (comparison != 0) return comparison;
        comparison = Integer.compare(y, other.y);
        return comparison != 0 ? comparison : Integer.compare(z, other.z);
    }

    private static UUID parseUuid(Object value) {
        if (!(value instanceof String text) || text.isBlank()) return null;
        try { return UUID.fromString(text); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        throw new IllegalArgumentException("block coordinate is missing");
    }

    private static String key(String prefix, String suffix) {
        if (prefix == null || prefix.isEmpty()) return suffix;
        return prefix + Character.toUpperCase(suffix.charAt(0)) + suffix.substring(1);
    }
}
