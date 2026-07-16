package com.github.blade.hvac.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ClimateVent(BlockKey position, GroupId group) {
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
