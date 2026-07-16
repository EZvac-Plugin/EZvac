package com.github.blade.hvac.model;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.simulation.ThermalModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persistent state for one sign-based thermostat/controller. */
public final class Thermostat {
    private final BlockKey position;
    private String id;
    private GroupId group;
    private double targetF;
    private double roomF;
    private boolean enabled;
    private OperatingMode mode;
    private long ticksInMode;
    private double outdoorNoonF;
    private double outdoorMidnightF;
    private double lastOutdoorF;

    public Thermostat(BlockKey position, String id, String groupLabel,
                      double targetF, double roomF, HvacSettings settings) {
        this(position, id, GroupId.of(position, groupLabel), settings.clampTarget(targetF),
                ThermalModel.clampTemperature(roomF), true, OperatingMode.IDLE,
                settings.minimumOffTicks(), 72.0, 52.0, 62.0);
    }

    private Thermostat(BlockKey position, String id, GroupId group, double targetF,
                       double roomF, boolean enabled, OperatingMode mode, long ticksInMode,
                       double outdoorNoonF, double outdoorMidnightF, double lastOutdoorF) {
        this.position = Objects.requireNonNull(position, "position");
        this.id = validateId(id);
        this.group = Objects.requireNonNull(group, "group");
        if (!position.worldId().equals(group.worldId()))
            throw new IllegalArgumentException("thermostat and group worlds differ");
        this.targetF = targetF;
        this.roomF = roomF;
        this.enabled = enabled;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.ticksInMode = Math.max(0L, ticksInMode);
        this.outdoorNoonF = ThermalModel.clampTemperature(outdoorNoonF);
        this.outdoorMidnightF = ThermalModel.clampTemperature(outdoorMidnightF);
        this.lastOutdoorF = ThermalModel.clampTemperature(lastOutdoorF);
    }

    public BlockKey position() { return position; }
    public String id() { return id; }
    public GroupId group() { return group; }
    public double targetF() { return targetF; }
    public double roomF() { return roomF; }
    public boolean enabled() { return enabled; }
    public OperatingMode mode() { return mode; }
    public long ticksInMode() { return ticksInMode; }
    public double outdoorNoonF() { return outdoorNoonF; }
    public double outdoorMidnightF() { return outdoorMidnightF; }
    public double lastOutdoorF() { return lastOutdoorF; }

    public void setId(String id) { this.id = validateId(id); }
    public void setGroup(String label) { this.group = GroupId.of(position, label); }
    public void setTargetF(double targetF, HvacSettings settings) { this.targetF = settings.clampTarget(targetF); }
    public void setRoomF(double roomF) { this.roomF = ThermalModel.clampTemperature(roomF); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setControlState(OperatingMode mode, long ticksInMode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.ticksInMode = Math.max(0L, ticksInMode);
    }
    public void setOutdoorProfile(double noonF, double midnightF) {
        this.outdoorNoonF = ThermalModel.clampTemperature(noonF);
        this.outdoorMidnightF = ThermalModel.clampTemperature(midnightF);
    }
    public void setLastOutdoorF(double outdoorF) { this.lastOutdoorF = ThermalModel.clampTemperature(outdoorF); }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("group", group.label());
        position.write(map, "");
        map.put("targetF", targetF);
        map.put("roomF", roomF);
        map.put("enabled", enabled);
        map.put("mode", mode.name());
        map.put("ticksInMode", ticksInMode);
        map.put("outdoorNoonF", outdoorNoonF);
        map.put("outdoorMidnightF", outdoorMidnightF);
        map.put("lastOutdoorF", lastOutdoorF);
        return map;
    }

    public static Thermostat fromMap(Map<String, Object> map, HvacSettings settings) {
        BlockKey position = BlockKey.read(map, "");
        String id = string(map, "id", string(map, "label", null));
        String groupLabel = string(map, "group", string(map, "hvacGroupLabel", null));
        double target = number(map, "targetF", number(map, "targetTempF", 72.0));
        double room = number(map, "roomF", number(map, "roomTempF", 72.0));
        long ticks = whole(map, "ticksInMode", -1L);
        if (ticks < 0L) ticks = Math.max(0L, Math.round(number(map,
                "modeElapsedMinecraftHours", settings.minimumOffTicks() / 1_000.0) * 1_000.0));
        return new Thermostat(position, id, GroupId.of(position, groupLabel),
                settings.clampTarget(target), ThermalModel.clampTemperature(room),
                bool(map, "enabled", true), OperatingMode.parse(map.get("mode")), ticks,
                number(map, "outdoorNoonF", 72.0), number(map, "outdoorMidnightF", 52.0),
                number(map, "lastOutdoorF", number(map, "lastOutdoorTempF", 62.0)));
    }

    public static String validateId(String value) {
        if (value == null) throw new IllegalArgumentException("thermostat id is required");
        String id = value.trim();
        if (id.isEmpty() || id.length() > 32 || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("thermostat ids must be 1-32 letters, numbers, '_' or '-'");
        return id;
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue() : fallback;
    }

    private static long whole(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }
}
