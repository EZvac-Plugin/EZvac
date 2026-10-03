package com.github.blade.hvac.model;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ClimateVent(BlockKey position, GroupId group, UUID owner) {
    /** Distributes conditioned air into a room. */
    public static final Material AIR_VENT = Material.IRON_TRAPDOOR;

    /**
     * Distributes conditioned water into a body of water. Only the unweathered
     * waxed grate qualifies: waxed copper never oxidises, so a registered vent
     * cannot change material on its own and quietly unregister itself, and
     * every vent in a build keeps the same appearance.
     */
    public static final Material WATER_VENT = Material.WAXED_COPPER_GRATE;

    public static boolean isVentBlock(Material type) {
        return type == AIR_VENT || isWaterVent(type);
    }

    /** A system's medium follows its vent blocks, not the equipment feeding it. */
    public static boolean isWaterVent(Material type) {
        return type == WATER_VENT;
    }

    public ClimateVent {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(group, "group");
        if (!position.worldId().equals(group.worldId()))
            throw new IllegalArgumentException("vent and group worlds differ");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        map.put("group", group.label());
        Ownership.write(map, owner);
        return map;
    }

    public static ClimateVent fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        Object value = map.get("group");
        if (!(value instanceof String label)) throw new IllegalArgumentException("vent group is missing");
        return new ClimateVent(position, GroupId.of(position, label),
                Ownership.read(map.get("owner")));
    }
}
