package com.github.blade.hvac.model;

import org.bukkit.Material;

import java.util.Locale;

/**
 * The kinds of device that change the climate, which /hvac quicksettings can
 * switch off server-wide (BETA 1.0). Thermostats, sensors, monitors and
 * panels only read or set it, so they aren't here.
 */
public enum DeviceKind {
    HEAT_PUMP("heat-pump", "Heat pump", "Heat pumps", Material.DISPENSER),
    AIR_CONDITIONER("air-conditioner", "Air conditioner", "Air conditioners", Material.BLUE_ICE),
    FURNACE("furnace", "Furnace", "Furnaces", Material.BLAST_FURNACE),
    BOILER("boiler", "Boiler", "Boilers", Material.CAULDRON),
    POOL_HEAT_PUMP("pool-heat-pump", "Pool heat pump", "Pool heat pumps", Material.PRISMARINE),
    DEHUMIDIFIER("dehumidifier", "Dehumidifier", "Dehumidifiers", Material.IRON_BARS),
    HUMIDIFIER("humidifier", "Humidifier", "Humidifiers", Material.CUT_COPPER_SLAB),
    AIR_PURIFIER("air-purifier", "Air purifier", "Air purifiers", Material.TUBE_CORAL_BLOCK);

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
    public String displayName() { return displayName; }
    public String plural() { return plural; }
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
        };
    }

    /** Null when the name is no kind's; "heat_pump" and "Heat-Pump" are read as heat-pump. */
    public static DeviceKind parse(String name) {
        if (name == null) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (DeviceKind kind : values()) if (kind.configName.equals(normalized)) return kind;
        return null;
    }

    /** What /hvac create says while this kind is switched off. */
    public String refusal() {
        return plural + " are switched off on this server, so no new one can be made. An admin can switch"
                + " them back on in /hvac quicksettings.";
    }
}
