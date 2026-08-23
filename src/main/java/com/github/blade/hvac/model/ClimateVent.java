package com.github.blade.hvac.model;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ClimateVent(BlockKey position, GroupId group) {
    /** Distributes conditioned air into a room. */
    public static final Material AIR_VENT = Material.IRON_TRAPDOOR;
    /** Distributes conditioned water into a body of water. */
    public static final Material WATER_VENT = Material.COPPER_GRATE;

    public static boolean isVentBlock(Material type) {
        return type == AIR_VENT || type == WATER_VENT;
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
        return map;
    }

    public static ClimateVent fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        Object value = map.get("group");
        if (!(value instanceof String label)) throw new IllegalArgumentException("vent group is missing");
        return new ClimateVent(position, GroupId.of(position, label));
    }
}
