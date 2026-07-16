package com.github.blade.hvac.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RpmMonitor(BlockKey position, GroupId group, String label, EquipmentType equipmentType) {
    public RpmMonitor {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(group, "group");
        label = Thermostat.validateId(label);
        Objects.requireNonNull(equipmentType, "equipmentType");
        if (!position.worldId().equals(group.worldId()))
            throw new IllegalArgumentException("RPM monitor and group worlds differ");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        map.put("group", group.label());
        map.put("label", label);
        map.put("equipmentType", equipmentType.name());
        return map;
    }

    public static RpmMonitor fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        String group = text(map.get("group"), null);
        return new RpmMonitor(position, GroupId.of(position, group),
                text(map.get("label"), group), EquipmentType.parse(map.get("equipmentType")));
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
