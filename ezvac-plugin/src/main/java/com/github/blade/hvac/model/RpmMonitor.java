package com.github.blade.hvac.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RpmMonitor(BlockKey position, SystemId system, String label, EquipmentType equipmentType) {
    public RpmMonitor {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(system, "group");
        label = Thermostat.validateId(label);
        Objects.requireNonNull(equipmentType, "equipmentType");
        if (!position.worldId().equals(system.worldId()))
            throw new IllegalArgumentException("RPM monitor and group worlds differ");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        map.put("group", system.key());
        map.put("label", label);
        map.put("equipmentType", equipmentType.name());
        return map;
    }

    public static RpmMonitor fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        String system = text(map.get("group"), null);
        return new RpmMonitor(position, SystemId.of(position, system),
                text(map.get("label"), system), EquipmentType.parse(map.get("equipmentType")));
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
