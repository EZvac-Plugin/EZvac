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
 * boilers are sealed and never count. Since BETA 1.0 Pilot 4 smokers and
 * blast furnaces have rates of their own, and torches and lanterns add a
 * little each; redstone torches don't burn, so they never count.
 */
public enum SmokeSource {
    CAMPFIRE, FIRE, LAVA, FURNACE, CANDLE, SMOKER, BLAST_FURNACE, TORCH;

    private static final SmokeSource[] BY_MATERIAL = new SmokeSource[Material.values().length];

    static {
        for (Material material : Material.values()) {
            if (material.isLegacy()) continue;
            BY_MATERIAL[material.ordinal()] = classify(material.name());
        }
    }

    /** What a block of this name is as a source, or null. By name, so copper torches count where they exist. */
    public static SmokeSource classify(String name) {
        return switch (name) {
            case "CAMPFIRE", "SOUL_CAMPFIRE" -> CAMPFIRE;
            case "FIRE", "SOUL_FIRE" -> FIRE;
            case "LAVA" -> LAVA;
            case "FURNACE" -> FURNACE;
            case "SMOKER" -> SMOKER;
            case "BLAST_FURNACE" -> BLAST_FURNACE;
            case "SEA_LANTERN" -> null;
            default -> name.equals("CANDLE") || name.endsWith("_CANDLE") || name.endsWith("CANDLE_CAKE") ? CANDLE
                    : (name.endsWith("TORCH") && !name.startsWith("REDSTONE")) || name.endsWith("LANTERN") ? TORCH
                    : null;
        };
    }

    /** What this block is as a source, or null for anything that makes no smoke. */
    public static SmokeSource of(Material material) {
        return material == null ? null : BY_MATERIAL[material.ordinal()];
    }

    /** Fire, lava, torches and lanterns always burn; the rest only while lit. */
    public boolean burning(BlockData data) {
        return switch (this) {
            case FIRE, LAVA, TORCH -> true;
            case CAMPFIRE, FURNACE, SMOKER, BLAST_FURNACE, CANDLE -> data instanceof Lightable lightable && lightable.isLit();
        };
    }

    /** What one burning block of this kind adds, per hour, at the reference size. */
    public AirQualitySettings.Source emission(AirQualitySettings.Sources sources) {
        return switch (this) {
            case CAMPFIRE -> sources.campfire();
            case FIRE -> sources.fire();
            case LAVA -> sources.lava();
            case FURNACE -> sources.furnace();
            case SMOKER -> sources.smoker();
            case BLAST_FURNACE -> sources.blastFurnace();
            case CANDLE -> sources.candle();
            case TORCH -> sources.torch();
        };
    }

    public String displayName() {
        String name = name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
