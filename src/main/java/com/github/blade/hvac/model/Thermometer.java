package com.github.blade.hvac.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Independent point sensor; its reading is determined only by its block position. */
public record Thermometer(BlockKey position, String label) {
    public Thermometer {
        Objects.requireNonNull(position, "position");
        label = Thermostat.validateId(label);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        map.put("label", label);
        return map;
    }

    public static Thermometer fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        String legacyGroup = text(map.get("group"), "Thermometer");
        return new Thermometer(position, text(map.get("label"), legacyGroup));
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
