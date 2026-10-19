package com.github.blade.hvac.model;

import java.util.Objects;

/**
 * Identifies a zone within an HVAC group. A zone is a subdivision of a group
 * with its own thermostat and dedicated vents. Multiple zones can share the
 * same equipment pool.
 *
 * <p>Zone names are optional - a null or empty zone name represents the
 * "primary" or "default" zone for backward compatibility with single-zone systems.
 */
public record ZoneId(GroupId group, String zoneName) implements Comparable<ZoneId> {
    public static final String PRIMARY_ZONE = "";

    public ZoneId {
        Objects.requireNonNull(group, "group");
        // Normalize null to empty string for primary zone
        zoneName = zoneName == null ? PRIMARY_ZONE : zoneName.trim();
    }

    /**
     * Creates a zone ID for the primary/default zone (backward compatible).
     */
    public static ZoneId primary(GroupId group) {
        return new ZoneId(group, PRIMARY_ZONE);
    }

    /**
     * Creates a zone ID with a specific zone name.
     */
    public static ZoneId of(GroupId group, String zoneName) {
        return new ZoneId(group, zoneName);
    }

    /**
     * Returns true if this is the primary/default zone.
     */
    public boolean isPrimary() {
        return zoneName.isEmpty();
    }

    /**
     * Returns the display name for this zone.
     */
    public String displayName() {
        return isPrimary() ? group.label() : group.label() + "/" + zoneName;
    }

    @Override
    public int compareTo(ZoneId other) {
        int groupCompare = group.compareTo(other.group);
        if (groupCompare != 0) return groupCompare;
        // Primary zone sorts first
        if (isPrimary() && !other.isPrimary()) return -1;
        if (!isPrimary() && other.isPrimary()) return 1;
        return zoneName.compareTo(other.zoneName);
    }

    @Override
    public String toString() {
        return displayName();
    }

    /**
     * Validates a zone name according to the same rules as group labels.
     * Returns the validated name, or throws if invalid.
     */
    public static String validateZoneName(String name) {
        if (name == null || name.trim().isEmpty()) return PRIMARY_ZONE;
        String zone = name.trim();
        if (zone.length() > 32 || !zone.matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("Zone names must be 1-32 letters, numbers, '_' or '-'");
        return zone;
    }
}
