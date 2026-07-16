package com.github.blade.hvac.model;

import org.bukkit.Material;

import java.util.Locale;

public enum EquipmentType {
    HEAT_PUMP(true, true, Material.WATER, "Heat pump"),
    AIR_CONDITIONER(true, false, Material.WATER, "Air conditioner"),
    FURNACE(false, true, Material.LAVA, "Furnace");

    private final boolean cooling;
    private final boolean heating;
    private final Material outputMaterial;
    private final String displayName;

    EquipmentType(boolean cooling, boolean heating, Material outputMaterial, String displayName) {
        this.cooling = cooling;
        this.heating = heating;
        this.outputMaterial = outputMaterial;
        this.displayName = displayName;
    }

    public boolean supports(OperatingMode mode) {
        return mode == OperatingMode.COOLING ? cooling : mode == OperatingMode.HEATING && heating;
    }

    public Material outputMaterial() { return outputMaterial; }
    public String displayName() { return displayName; }

    public static EquipmentType parse(Object value) {
        if (!(value instanceof String text) || text.isBlank()) return HEAT_PUMP;
        String normalized = text.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("AC")) normalized = "AIR_CONDITIONER";
        try { return valueOf(normalized); }
        catch (IllegalArgumentException ignored) { return HEAT_PUMP; }
    }
}
