package com.github.blade.hvac.model;

import com.github.blade.hvac.config.AirQualitySettings;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;

import java.util.Locale;

/**
 * Blocks that put smoke and soot into a space's air (3.2). The airflow scan
 * notes where they are; whether each burns is read again every so often,
 * because lighting a campfire changes no block. EZvac's own furnaces and
 * boilers are sealed and never count; torches don't either, or every room
 * would be smoky.
 */
public enum SmokeSource {
    CAMPFIRE, FIRE, LAVA, FURNACE, CANDLE;

    private static final SmokeSource[] BY_MATERIAL = new SmokeSource[Material.values().length];

    static {
        for (Material material : Material.values()) {
            if (material.isLegacy()) continue;
            String name = material.name();
            SmokeSource source = switch (material) {
                case CAMPFIRE, SOUL_CAMPFIRE -> CAMPFIRE;
                case FIRE, SOUL_FIRE -> FIRE;
                case LAVA -> LAVA;
                case FURNACE, SMOKER, BLAST_FURNACE -> FURNACE;
                default -> name.equals("CANDLE") || name.endsWith("_CANDLE") || name.endsWith("CANDLE_CAKE")
                        ? CANDLE : null;
            };
            BY_MATERIAL[material.ordinal()] = source;
        }
    }

    /** What this block is as a source, or null for anything that makes no smoke. */
    public static SmokeSource of(Material material) {
        return material == null ? null : BY_MATERIAL[material.ordinal()];
    }

    /** Fire and lava always burn; the rest only while lit. */
    public boolean burning(BlockData data) {
        return switch (this) {
            case FIRE, LAVA -> true;
            case CAMPFIRE, FURNACE, CANDLE -> data instanceof Lightable lightable && lightable.isLit();
        };
    }

    /** What one burning block of this kind adds, per hour, at the reference size. */
    public AirQualitySettings.Source emission(AirQualitySettings.Sources sources) {
        return switch (this) {
            case CAMPFIRE -> sources.campfire();
            case FIRE -> sources.fire();
            case LAVA -> sources.lava();
            case FURNACE -> sources.furnace();
            case CANDLE -> sources.candle();
        };
    }

    public String displayName() {
        String name = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
