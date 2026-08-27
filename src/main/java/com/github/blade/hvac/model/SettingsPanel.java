package com.github.blade.hvac.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persisted sign controller for one group's variable-speed operating profile. */
public final class SettingsPanel {
    private final BlockKey position;
    private final GroupId group;
    private final String label;
    private OperatingProfile profile;
    private UUID owner;

    public SettingsPanel(BlockKey position, String groupLabel, String label) {
        this(position, groupLabel, label, null);
    }

    public SettingsPanel(BlockKey position, String groupLabel, String label, UUID owner) {
        this(position, GroupId.of(position, groupLabel), label, OperatingProfile.NORMAL, owner);
    }

    private SettingsPanel(BlockKey position, GroupId group, String label,
                          OperatingProfile profile, UUID owner) {
        this.owner = owner;
        this.position = Objects.requireNonNull(position, "position");
        this.group = Objects.requireNonNull(group, "group");
        this.label = Thermostat.validateId(label);
        this.profile = Objects.requireNonNull(profile, "profile");
        if (!position.worldId().equals(group.worldId()))
            throw new IllegalArgumentException("settings panel and group worlds differ");
    }

    public BlockKey position() { return position; }
    public GroupId group() { return group; }
    public String label() { return label; }
    public OperatingProfile profile() { return profile; }
    public UUID owner() { return owner; }
    /** Reassigned by an administrator; see Ownership. */
    public void setOwner(UUID owner) { this.owner = owner; }
    public void setProfile(OperatingProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        map.put("group", group.label());
        map.put("label", label);
        map.put("profile", profile.name());
        Ownership.write(map, owner);
        return map;
    }

    public static SettingsPanel fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        String group = text(map.get("group"), null);
        String label = text(map.get("label"), group);
        return new SettingsPanel(position, GroupId.of(position, group), label,
                OperatingProfile.parse(map.get("profile")), Ownership.read(map.get("owner")));
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
