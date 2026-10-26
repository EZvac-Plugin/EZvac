package com.github.blade.hvac.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persisted sign controller for one group's variable-speed operating profile. */
public final class SettingsPanel {
    private final BlockKey position;
    private final SystemId system;
    private final String label;
    private OperatingProfile profile;

    public SettingsPanel(BlockKey position, String systemLabel, String label) {
        this(position, SystemId.of(position, systemLabel), label, OperatingProfile.NORMAL);
    }

    private SettingsPanel(BlockKey position, SystemId system, String label,
                          OperatingProfile profile) {
        this.position = Objects.requireNonNull(position, "position");
        this.system = Objects.requireNonNull(system, "group");
        this.label = Thermostat.validateId(label);
        this.profile = Objects.requireNonNull(profile, "profile");
        if (!position.worldId().equals(system.worldId()))
            throw new IllegalArgumentException("settings panel and group worlds differ");
    }

    public BlockKey position() { return position; }
    public SystemId system() { return system; }
    public String label() { return label; }
    public OperatingProfile profile() { return profile; }
    public void setProfile(OperatingProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        map.put("group", system.key());
        map.put("label", label);
        map.put("profile", profile.name());
        return map;
    }

    public static SettingsPanel fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        String system = text(map.get("group"), null);
        String label = text(map.get("label"), system);
        return new SettingsPanel(position, SystemId.of(position, system), label,
                OperatingProfile.parse(map.get("profile")));
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
