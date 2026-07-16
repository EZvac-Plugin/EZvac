package com.github.blade.hvac.model;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** A climate system is scoped to exactly one world and normalized label. */
public record GroupId(UUID worldId, String label) implements Comparable<GroupId> {
    public GroupId {
        Objects.requireNonNull(worldId, "worldId");
        label = normalizeLabel(label);
    }

    public static GroupId of(BlockKey block, String label) {
        return new GroupId(block.worldId(), label);
    }

    public static String normalizeLabel(String label) {
        if (label == null) throw new IllegalArgumentException("group label is required");
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 32
                || !normalized.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException("group labels must be 1-32 letters, numbers, '_' or '-'");
        }
        return normalized;
    }

    @Override
    public int compareTo(GroupId other) {
        int comparison = worldId.compareTo(other.worldId);
        return comparison != 0 ? comparison : label.compareTo(other.label);
    }
}
