package com.github.blade.hvac.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A weather station (BETA 1.0): a sign on a tower of copper and iron that
 * measures the outdoor climate of the area around it and forecasts the next
 * seven days. Labels may repeat. It keeps what it last measured of its area,
 * so it can forecast while its chunk is unloaded, and counts the lightning
 * its rods have caught.
 */
public final class WeatherStation {
    /**
     * The outdoor climate around a station, averaged over the blocks it
     * measures: the same figures a biome blend gives, plus its air.
     */
    public record Area(double noonF, double midnightF, double downfall, double rainShare, double snowShare,
                       double seasonalF, double fine, double coarse, double pollen, double windExposure) {
        public Area {
            for (double value : new double[]{noonF, midnightF, downfall, rainShare, snowShare, seasonalF, fine,
                    coarse, pollen, windExposure})
                if (!Double.isFinite(value)) throw new IllegalArgumentException("a station's area must be finite");
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("noonF", noonF);
            map.put("midnightF", midnightF);
            map.put("downfall", downfall);
            map.put("rainShare", rainShare);
            map.put("snowShare", snowShare);
            map.put("seasonalF", seasonalF);
            map.put("fine", fine);
            map.put("coarse", coarse);
            map.put("pollen", pollen);
            map.put("windExposure", windExposure);
            return map;
        }

        static Area fromMap(Object value) {
            if (!(value instanceof Map<?, ?> map)) return null;
            try {
                return new Area(number(map, "noonF"), number(map, "midnightF"), number(map, "downfall"),
                        number(map, "rainShare"), number(map, "snowShare"), number(map, "seasonalF"),
                        number(map, "fine"), number(map, "coarse"), number(map, "pollen"),
                        number(map, "windExposure"));
            } catch (IllegalArgumentException | NullPointerException | ClassCastException exception) {
                return null;   // measured again when its chunk next loads
            }
        }

        private static double number(Map<?, ?> map, String key) {
            return ((Number) map.get(key)).doubleValue();
        }
    }

    private final BlockKey position;
    private final String label;
    private boolean enabled;
    private long strikes;
    private Area area;
    /** Whether its tower stood when last checked; not saved, as it's checked again when its chunk loads. */
    private boolean intact = true;

    public WeatherStation(BlockKey position, String label) {
        this(position, label, true, 0L, null);
    }

    public WeatherStation(BlockKey position, String label, boolean enabled, long strikes, Area area) {
        this.position = Objects.requireNonNull(position, "position");
        this.label = Thermostat.validateId(label);
        this.enabled = enabled;
        this.strikes = Math.max(0L, strikes);
        this.area = area;
    }

    public BlockKey position() { return position; }
    public String label() { return label; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** How many times lightning has struck its tower. */
    public long strikes() { return strikes; }
    public void struck() { strikes++; }
    /** What it last measured of its area; null until it first has. */
    public Area area() { return area; }
    public void setArea(Area area) { this.area = area; }
    public boolean intact() { return intact; }
    public void setIntact(boolean intact) { this.intact = intact; }

    /** The same station under another label. */
    public WeatherStation renamed(String label) {
        WeatherStation renamed = new WeatherStation(position, label, enabled, strikes, area);
        renamed.intact = intact;
        return renamed;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        map.put("label", label);
        if (!enabled) map.put("enabled", false);
        if (strikes > 0) map.put("strikes", strikes);
        if (area != null) map.put("area", area.toMap());
        return map;
    }

    public static WeatherStation fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        Object label = map.get("label");
        Object enabled = map.get("enabled");
        Object strikes = map.get("strikes");
        return new WeatherStation(position, label instanceof String text && !text.isBlank() ? text : "Station",
                !(enabled instanceof Boolean on) || on, strikes instanceof Number count ? count.longValue() : 0L,
                Area.fromMap(map.get("area")));
    }
}
