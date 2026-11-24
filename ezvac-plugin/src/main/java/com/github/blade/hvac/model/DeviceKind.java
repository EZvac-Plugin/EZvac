package com.github.blade.hvac.model;

import org.bukkit.Material;

import java.util.Locale;

/**
 * The kinds of device that change the climate, which /hvac quicksettings can
 * switch off server-wide (BETA 1.0), and weather stations. Thermostats,
 * sensors, monitors and panels only read or set it, so they aren't here.
 */
public enum DeviceKind {
    HEAT_PUMP("heat-pump", "Heat pump", "Heat pumps", Material.DISPENSER),
    AIR_CONDITIONER("air-conditioner", "Air conditioner", "Air conditioners", Material.BLUE_ICE),
    FURNACE("furnace", "Furnace", "Furnaces", Material.BLAST_FURNACE),
    BOILER("boiler", "Boiler", "Boilers", Material.CAULDRON),
    POOL_HEAT_PUMP("pool-heat-pump", "Pool heat pump", "Pool heat pumps", Material.PRISMARINE),
    DEHUMIDIFIER("dehumidifier", "Dehumidifier", "Dehumidifiers", Material.IRON_BARS),
    HUMIDIFIER("humidifier", "Humidifier", "Humidifiers", Material.CUT_COPPER_SLAB),
    AIR_PURIFIER("air-purifier", "Air purifier", "Air purifiers", Material.TUBE_CORAL_BLOCK),
    FRIDGE("fridge", "Fridge", "Fridges", Material.BARREL),
    FREEZER("deep-freezer", "Deep freezer", "Deep freezers", Material.PACKED_ICE),
    FRIDGE_FREEZER("refrigerator", "Refrigerator", "Refrigerators", Material.BLUE_ICE),
    MINI_FRIDGE("mini-fridge", "Mini fridge", "Mini fridges", Material.SNOW_BLOCK),
    WEATHER_STATION("weather-station", "Weather station", "Weather stations", Material.LIGHTNING_ROD);

    private final String configName;
    private final String displayName;
    private final String plural;
    private final Material icon;

    DeviceKind(String configName, String displayName, String plural, Material icon) {
        this.configName = configName;
        this.displayName = displayName;
        this.plural = plural;
        this.icon = icon;
    }

    /** As written in config.yml's {@code devices.disabled}. */
    public String configName() { return configName; }
    public String displayName() { return com.github.blade.hvac.lang.Lang.text(com.github.blade.hvac.lang.Lang.key("device.kind", name()) + ".one"); }
    public String plural() { return com.github.blade.hvac.lang.Lang.text(com.github.blade.hvac.lang.Lang.key("device.kind", name()) + ".many"); }
    public Material icon() { return icon; }

    public static DeviceKind of(EquipmentType type) {
        return switch (type) {
            case HEAT_PUMP -> HEAT_PUMP;
            case AIR_CONDITIONER -> AIR_CONDITIONER;
            case FURNACE -> FURNACE;
            case BOILER -> BOILER;
            case POOL_HEAT_PUMP -> POOL_HEAT_PUMP;
        };
    }

    public static DeviceKind of(ClimateUnit.Kind kind) {
        return switch (kind) {
            case DEHUMIDIFIER -> DEHUMIDIFIER;
            case HUMIDIFIER -> HUMIDIFIER;
            case AIR_PURIFIER -> AIR_PURIFIER;
            case FRIDGE -> FRIDGE;
            case FREEZER -> FREEZER;
            case FRIDGE_FREEZER -> FRIDGE_FREEZER;
            case MINI_FRIDGE -> MINI_FRIDGE;
        };
    }

    /** Null when the name is no kind's; "heat_pump" and "Heat-Pump" are read as heat-pump. */
    public static DeviceKind parse(String name) {
        if (name == null) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (DeviceKind kind : values()) if (kind.configName.equals(normalized)) return kind;
        // Their names while Pilot 7 was being made.
        if (normalized.equals("freezer")) return FREEZER;
        if (normalized.equals("fridge-freezer")) return FRIDGE_FREEZER;
        return null;
    }

    /** What /hvac create says while this kind is switched off. */
    public String refusal() {
        return com.github.blade.hvac.lang.Lang.text("device.refusal", com.github.blade.hvac.lang.Lang.arg("kinds", plural()));
    }
}
