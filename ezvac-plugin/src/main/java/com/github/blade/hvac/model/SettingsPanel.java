package com.github.blade.hvac.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Persisted sign controller for one system's variable-speed operating
 * profile. A panel is created on its own and linked to a system with the
 * link tool; until then, or once unlinked, it has no system.
 */
public final class SettingsPanel {
    private final BlockKey position;
    private SystemId system;
    private String label;
    private OperatingProfile profile;

    /** A panel not linked to any system yet. */
    public SettingsPanel(BlockKey position, String label) {
        this(position, null, label, OperatingProfile.NORMAL);
    }

    public SettingsPanel(BlockKey position, String systemKey, String label) {
        this(position, SystemId.of(position, systemKey), label, OperatingProfile.NORMAL);
    }

    private SettingsPanel(BlockKey position, SystemId system, String label,
                          OperatingProfile profile) {
        this.position = Objects.requireNonNull(position, "position");
        this.label = Thermostat.validateId(label);
        this.profile = Objects.requireNonNull(profile, "profile");
        setSystem(system);
    }

    public BlockKey position() { return position; }
    /** The system it controls, or null while it is not linked. */
    public SystemId system() { return system; }
    public String label() { return label; }
    public OperatingProfile profile() { return profile; }

    public void setProfile(OperatingProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public void setLabel(String label) { this.label = Thermostat.validateId(label); }

    /** Only HvacRegistry may relink a registered panel: its index follows the system. Null unlinks. */
    public void setSystem(SystemId system) {
        if (system != null && !position.worldId().equals(system.worldId()))
            throw new IllegalArgumentException("settings panel and system worlds differ");
        this.system = system;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        if (system != null) map.put("system", system.key());
        map.put("label", label);
        map.put("profile", profile.name());
        return map;
    }

    public static SettingsPanel fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        // Before 3.0 every panel belonged to a group, whose name became its system's key.
        String system = text(map.get("system"), text(map.get("group"), null));
        String label = text(map.get("label"), system);
        return new SettingsPanel(position, system == null ? null : SystemId.of(position, system), label,
                OperatingProfile.parse(map.get("profile")));
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
