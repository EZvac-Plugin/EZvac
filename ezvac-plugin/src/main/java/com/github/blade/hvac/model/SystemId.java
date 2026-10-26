package com.github.blade.hvac.model;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A climate system: the devices linked together, in one world. Players never
 * see or type its key. A system upgraded from 2.x keeps its old group name as
 * its key, so anything that stored one still matches; a new system gets
 * {@code #n}, which no group name could ever be.
 */
public record SystemId(UUID worldId, String key) implements Comparable<SystemId>, AirflowTarget {
    public SystemId {
        Objects.requireNonNull(worldId, "worldId");
        key = normalizeKey(key);
    }

    public static SystemId of(BlockKey block, String key) {
        return new SystemId(block.worldId(), key);
    }

    /** The system numbered {@code number} in a world: {@code #12}. */
    public static SystemId numbered(UUID worldId, long number) {
        if (number < 1) throw new IllegalArgumentException("system numbers start at 1");
        return new SystemId(worldId, "#" + number);
    }

    /** The number of a {@code #n} key, or 0 for a key kept from an old group name. */
    public long number() {
        return key.startsWith("#") ? Long.parseLong(key.substring(1)) : 0L;
    }

    public static String normalizeKey(String key) {
        if (key == null) throw new IllegalArgumentException("system key is required");
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("#[1-9][0-9]{0,17}")) return normalized;
        if (normalized.isEmpty() || normalized.length() > 32
                || !normalized.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException("'" + key + "' is not a system key");
        }
        return normalized;
    }

    @Override
    public int compareTo(SystemId other) {
        int comparison = worldId.compareTo(other.worldId);
        return comparison != 0 ? comparison : key.compareTo(other.key);
    }
}
