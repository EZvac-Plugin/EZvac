package com.github.blade.hvac.model;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A remote sensor: a button that measures the spot it is on, shown on any
 * number of sensor hubs. The button says what it measures, so a sensor is
 * never told: stone reads temperature, polished blackstone humidity, mangrove
 * both, birch the weather, cherry the air quality (3.2) and bamboo the wind (3.2). It reads exactly as a
 * thermometer sign at the
 * same spot would; a weather sensor reads the sky above it, like a rain gauge.
 */
public record Sensor(BlockKey position, String label, Kind reads) {
    /** What a sensor measures. Saved by name, as thermometer signs save theirs. */
    public enum Kind {
        TEMPERATURE("temperature"), HUMIDITY("humidity"), BOTH("temperature and humidity"), WEATHER("weather"),
        AIR("air quality"), WIND("wind");

        private final String description;
        Kind(String description) { this.description = description; }
        /** For messages: "a weather sensor", "reads temperature and humidity". */
        public String description() { return description; }

        /** Missing means temperature, the stone button's. */
        static Kind parse(Object value) {
            if (!(value instanceof String text) || text.isBlank()) return TEMPERATURE;
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        }
    }

    public Sensor {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(reads, "reads");
        label = Thermostat.validateId(label);
    }

    /** What a button measures as a sensor, or null for anything that is not a sensor button. */
    public static Kind forButton(Material button) {
        if (button == null) return null;
        return switch (button) {
            case STONE_BUTTON -> Kind.TEMPERATURE;
            case POLISHED_BLACKSTONE_BUTTON -> Kind.HUMIDITY;
            case MANGROVE_BUTTON -> Kind.BOTH;
            case BIRCH_BUTTON -> Kind.WEATHER;
            case CHERRY_BUTTON -> Kind.AIR;
            case BAMBOO_BUTTON -> Kind.WIND;
            default -> null;
        };
    }

    /** The button a sensor of this kind is, so the registry can tell when it was broken. */
    public static Material button(Kind reads) {
        return switch (reads) {
            case TEMPERATURE -> Material.STONE_BUTTON;
            case HUMIDITY -> Material.POLISHED_BLACKSTONE_BUTTON;
            case BOTH -> Material.MANGROVE_BUTTON;
            case WEATHER -> Material.BIRCH_BUTTON;
            case AIR -> Material.CHERRY_BUTTON;
            case WIND -> Material.BAMBOO_BUTTON;
        };
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        map.put("label", label);
        map.put("reads", reads.name());
        return map;
    }

    public static Sensor fromMap(Map<String, Object> map) {
        Object label = map.get("label");
        return new Sensor(BlockKey.read(map, ""), label instanceof String text && !text.isBlank() ? text : "Sensor",
                Kind.parse(map.get("reads")));
    }
}
