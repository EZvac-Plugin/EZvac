package com.github.blade.hvac.model;

import org.bukkit.Material;

import java.util.Locale;

public enum EquipmentType {
    HEAT_PUMP(true, true, Material.WATER, "Heat pump", false),
    AIR_CONDITIONER(true, false, Material.WATER, "Air conditioner", false),
    FURNACE(false, true, Material.LAVA, "Furnace", false),
    /**
     * Water-loop heat source: the counterpart of the furnace, feeding vents
     * that sit in water rather than in air. Like every source it is unaware of
     * what it is heating; the vents decide that.
     */
    BOILER(false, true, Material.WATER, "Boiler", true);

    private final boolean cooling;
    private final boolean heating;
    private final Material outputMaterial;
    private final String displayName;
    private final boolean waterLoop;

    EquipmentType(boolean cooling, boolean heating, Material outputMaterial, String displayName,
                  boolean waterLoop) {
        this.cooling = cooling;
        this.heating = heating;
        this.outputMaterial = outputMaterial;
        this.displayName = displayName;
        this.waterLoop = waterLoop;
    }

    /**
     * Which loop this source belongs to. Water-loop sources serve vents placed
     * in water; air-loop sources serve vents placed in a room. A system draws
     * on one loop only, so the two never mix.
     */
    public boolean waterLoop() { return waterLoop; }

    public boolean supports(OperatingMode mode) {
        return mode == OperatingMode.COOLING ? cooling : mode == OperatingMode.HEATING && heating;
    }

    public Material outputMaterial() { return outputMaterial; }
    public String displayName() { return displayName; }

    /**
     * A missing type still defaults to a heat pump, but an unrecognised one is
     * rejected rather than reinterpreted. Defaulting an unknown type would
     * silently change what a device does - a heating-only unit written by a
     * newer build would come back as a heat pump and start cooling - whereas
     * throwing lets the registry preserve the record untouched instead.
     */
    public static EquipmentType parse(Object value) {
        if (!(value instanceof String text) || text.isBlank()) return HEAT_PUMP;
        String normalized = text.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("AC")) normalized = "AIR_CONDITIONER";
        try { return valueOf(normalized); }
        catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown equipment type '" + text + "'", unknown);
        }
    }
}
