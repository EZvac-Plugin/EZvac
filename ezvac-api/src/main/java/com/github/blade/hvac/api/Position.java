package com.github.blade.hvac.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

/**
 * A block in a world, the way EZvac identifies its devices and readings.
 *
 * @param world the world's UUID
 * @since 1.0
 */
public record Position(UUID world, int x, int y, int z) {
    public Position {
        Objects.requireNonNull(world, "world");
    }

    /** The block at a location. */
    public static Position of(Location location) {
        Objects.requireNonNull(location.getWorld(), "location has no world");
        return new Position(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(),
                location.getBlockZ());
    }

    /** The middle of this block, or null when its world isn't loaded. */
    public Location toLocation() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x + 0.5, y + 0.5, z + 0.5);
    }
}
