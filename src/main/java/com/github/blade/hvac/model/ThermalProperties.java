package com.github.blade.hvac.model;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Thermal resistance properties for block materials. Higher resistance values
 * mean better insulation - conditioned air loses influence faster when passing
 * through or near high-resistance materials.
 *
 * <p>Resistance is measured in "effective blocks" - a material with resistance
 * 0.5 acts like an additional 0.5 blocks of distance for temperature influence.
 */
public final class ThermalProperties {
    private static final double DEFAULT_RESISTANCE = 0.0;
    private static final double MINIMUM_RESISTANCE = 0.0;
    private static final double MAXIMUM_RESISTANCE = 10.0;

    private final Map<Material, Double> resistanceByMaterial = new HashMap<>();
    private final double defaultResistance;
    private final boolean enabled;

    private ThermalProperties(Map<Material, Double> resistances, double defaultResistance, boolean enabled) {
        this.resistanceByMaterial.putAll(resistances);
        this.defaultResistance = clamp(defaultResistance);
        this.enabled = enabled;
    }

    public static ThermalProperties loadFrom(File configFile, Logger logger) {
        if (!configFile.exists()) {
            logger.info("Thermal properties file not found, creating default: " + configFile.getName());
            ThermalProperties defaults = createDefaults();
            defaults.saveTo(configFile, logger);
            return defaults;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            boolean enabled = config.getBoolean("enabled", true);
            double defaultResistance = config.getDouble("default-resistance", DEFAULT_RESISTANCE);

            Map<Material, Double> resistances = new HashMap<>();
            ConfigurationSection materials = config.getConfigurationSection("materials");
            if (materials != null) {
                for (String key : materials.getKeys(false)) {
                    try {
                        Material material = Material.valueOf(key.toUpperCase());
                        double resistance = materials.getDouble(key);
                        resistances.put(material, clamp(resistance));
                    } catch (IllegalArgumentException e) {
                        logger.warning("Unknown material in thermal properties: " + key);
                    }
                }
            }

            logger.info("Loaded thermal properties: " + resistances.size() + " materials configured, "
                    + (enabled ? "enabled" : "disabled"));
            return new ThermalProperties(resistances, defaultResistance, enabled);
        } catch (Exception e) {
            logger.severe("Failed to load thermal properties, using defaults: " + e.getMessage());
            return createDefaults();
        }
    }

    public void saveTo(File configFile, Logger logger) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.set("enabled", enabled);
            config.set("default-resistance", defaultResistance);

            ConfigurationSection materials = config.createSection("materials");
            for (Map.Entry<Material, Double> entry : resistanceByMaterial.entrySet()) {
                materials.set(entry.getKey().name().toLowerCase(), entry.getValue());
            }

            config.save(configFile);
            logger.info("Saved thermal properties to " + configFile.getName());
        } catch (Exception e) {
            logger.severe("Failed to save thermal properties: " + e.getMessage());
        }
    }

    private static ThermalProperties createDefaults() {
        Map<Material, Double> defaults = new HashMap<>();

        // Excellent insulators (0.8-1.0)
        defaults.put(Material.WHITE_WOOL, 0.9);
        defaults.put(Material.ORANGE_WOOL, 0.9);
        defaults.put(Material.MAGENTA_WOOL, 0.9);
        defaults.put(Material.LIGHT_BLUE_WOOL, 0.9);
        defaults.put(Material.YELLOW_WOOL, 0.9);
        defaults.put(Material.LIME_WOOL, 0.9);
        defaults.put(Material.PINK_WOOL, 0.9);
        defaults.put(Material.GRAY_WOOL, 0.9);
        defaults.put(Material.LIGHT_GRAY_WOOL, 0.9);
        defaults.put(Material.CYAN_WOOL, 0.9);
        defaults.put(Material.PURPLE_WOOL, 0.9);
        defaults.put(Material.BLUE_WOOL, 0.9);
        defaults.put(Material.BROWN_WOOL, 0.9);
        defaults.put(Material.GREEN_WOOL, 0.9);
        defaults.put(Material.RED_WOOL, 0.9);
        defaults.put(Material.BLACK_WOOL, 0.9);
        defaults.put(Material.NETHERITE_BLOCK, 1.0);

        // Good insulators (0.6-0.8) - Stone, brick, concrete
        defaults.put(Material.STONE, 0.7);
        defaults.put(Material.COBBLESTONE, 0.7);
        defaults.put(Material.STONE_BRICKS, 0.7);
        defaults.put(Material.BRICKS, 0.7);
        defaults.put(Material.WHITE_CONCRETE, 0.65);
        defaults.put(Material.ORANGE_CONCRETE, 0.65);
        defaults.put(Material.MAGENTA_CONCRETE, 0.65);
        defaults.put(Material.LIGHT_BLUE_CONCRETE, 0.65);
        defaults.put(Material.YELLOW_CONCRETE, 0.65);
        defaults.put(Material.LIME_CONCRETE, 0.65);
        defaults.put(Material.PINK_CONCRETE, 0.65);
        defaults.put(Material.GRAY_CONCRETE, 0.65);
        defaults.put(Material.LIGHT_GRAY_CONCRETE, 0.65);
        defaults.put(Material.CYAN_CONCRETE, 0.65);
        defaults.put(Material.PURPLE_CONCRETE, 0.65);
        defaults.put(Material.BLUE_CONCRETE, 0.65);
        defaults.put(Material.BROWN_CONCRETE, 0.65);
        defaults.put(Material.GREEN_CONCRETE, 0.65);
        defaults.put(Material.RED_CONCRETE, 0.65);
        defaults.put(Material.BLACK_CONCRETE, 0.65);

        // Moderate insulators (0.4-0.6) - Wood, terracotta
        defaults.put(Material.OAK_PLANKS, 0.5);
        defaults.put(Material.SPRUCE_PLANKS, 0.5);
        defaults.put(Material.BIRCH_PLANKS, 0.5);
        defaults.put(Material.JUNGLE_PLANKS, 0.5);
        defaults.put(Material.ACACIA_PLANKS, 0.5);
        defaults.put(Material.DARK_OAK_PLANKS, 0.5);
        defaults.put(Material.MANGROVE_PLANKS, 0.5);
        defaults.put(Material.CHERRY_PLANKS, 0.5);
        defaults.put(Material.TERRACOTTA, 0.55);
        defaults.put(Material.WHITE_TERRACOTTA, 0.55);
        defaults.put(Material.ICE, 0.45);
        defaults.put(Material.PACKED_ICE, 0.5);

        // Poor insulators (0.2-0.4) - Glass, thin materials
        defaults.put(Material.GLASS, 0.3);
        defaults.put(Material.WHITE_STAINED_GLASS, 0.3);
        defaults.put(Material.ORANGE_STAINED_GLASS, 0.3);
        defaults.put(Material.MAGENTA_STAINED_GLASS, 0.3);
        defaults.put(Material.LIGHT_BLUE_STAINED_GLASS, 0.3);
        defaults.put(Material.YELLOW_STAINED_GLASS, 0.3);
        defaults.put(Material.LIME_STAINED_GLASS, 0.3);
        defaults.put(Material.PINK_STAINED_GLASS, 0.3);
        defaults.put(Material.GRAY_STAINED_GLASS, 0.3);
        defaults.put(Material.LIGHT_GRAY_STAINED_GLASS, 0.3);
        defaults.put(Material.CYAN_STAINED_GLASS, 0.3);
        defaults.put(Material.PURPLE_STAINED_GLASS, 0.3);
        defaults.put(Material.BLUE_STAINED_GLASS, 0.3);
        defaults.put(Material.BROWN_STAINED_GLASS, 0.3);
        defaults.put(Material.GREEN_STAINED_GLASS, 0.3);
        defaults.put(Material.RED_STAINED_GLASS, 0.3);
        defaults.put(Material.BLACK_STAINED_GLASS, 0.3);
        defaults.put(Material.GLASS_PANE, 0.25);

        return new ThermalProperties(defaults, DEFAULT_RESISTANCE, true);
    }

    /**
     * Returns the thermal resistance value for a material. Higher values mean
     * better insulation. Air passing through or near this material will lose
     * temperature influence faster.
     *
     * @param material the block material
     * @return resistance value in range [0.0, 10.0], or default if not configured
     */
    public double getResistance(Material material) {
        if (!enabled || material == null) return 0.0;
        return resistanceByMaterial.getOrDefault(material, defaultResistance);
    }

    /**
     * Checks if a material is considered a thermal barrier (has non-zero resistance).
     */
    public boolean isBarrier(Material material) {
        return enabled && material != null && getResistance(material) > 0.0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getDefaultResistance() {
        return defaultResistance;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return DEFAULT_RESISTANCE;
        return Math.max(MINIMUM_RESISTANCE, Math.min(MAXIMUM_RESISTANCE, value));
    }
}
