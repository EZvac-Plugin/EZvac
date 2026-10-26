package com.github.blade.hvac.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Independent point sensor; its reading is determined only by its block
 * position. One device in three flavours: a thermometer, a hygrometer
 * (humidity), or a thermo-hygrometer (both).
 */
public record Thermometer(BlockKey position, String label, Reads reads) {
    public enum Reads {
        TEMPERATURE("Thermometer"), HUMIDITY("Hygrometer"), BOTH("Thermo-hygrometer");

        private final String displayName;
        Reads(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }

        /** Missing means a thermometer: every sensor before 2.0 was one. */
        static Reads parse(Object value) {
            if (!(value instanceof String text) || text.isBlank()) return TEMPERATURE;
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        }
    }

    public Thermometer {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(reads, "reads");
        label = Thermostat.validateId(label);
    }

    public Thermometer(BlockKey position, String label) {
        this(position, label, Reads.TEMPERATURE);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        map.put("label", label);
        // Thermometers save exactly as they always have; only the new kinds say what they read.
        if (reads != Reads.TEMPERATURE) map.put("reads", reads.name());
        return map;
    }

    public static Thermometer fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        String legacySystem = text(map.get("group"), "Thermometer");
        return new Thermometer(position, text(map.get("label"), legacySystem), Reads.parse(map.get("reads")));
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
