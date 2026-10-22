package com.github.blade.hvac.model;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A remote sensor: a button that measures the spot it is on, shown on any
 * number of sensor hubs. The button says what it measures, so a sensor is
 * never told: stone reads temperature, polished blackstone humidity, mangrove both.
 * It reads exactly as a thermometer sign at the same spot would.
 */
public record Sensor(BlockKey position, String label, Thermometer.Reads reads) {
    public Sensor {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(reads, "reads");
        label = Thermostat.validateId(label);
    }

    /** What a button measures as a sensor, or null for anything that is not a sensor button. */
    public static Thermometer.Reads forButton(Material button) {
        if (button == null) return null;
        return switch (button) {
            case STONE_BUTTON -> Thermometer.Reads.TEMPERATURE;
            case POLISHED_BLACKSTONE_BUTTON -> Thermometer.Reads.HUMIDITY;
            case MANGROVE_BUTTON -> Thermometer.Reads.BOTH;
            default -> null;
        };
    }

    /** The button a sensor of this kind is, so the registry can tell when it was broken. */
    public static Material button(Thermometer.Reads reads) {
        return switch (reads) {
            case TEMPERATURE -> Material.STONE_BUTTON;
            case HUMIDITY -> Material.POLISHED_BLACKSTONE_BUTTON;
            case BOTH -> Material.MANGROVE_BUTTON;
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
                Thermometer.Reads.parse(map.get("reads")));
    }
}
