package com.github.blade.hvac.model;

import java.util.Locale;

/**
 * Equipment tiers (3.1), shown as stars: the three kinds of real HVAC
 * equipment. A single-stage unit runs flat out or not at all, a two-stage
 * unit has a lower stage for the last stretch to its target, and a
 * variable-speed compressor (or modulating burner) eases down smoothly. The
 * lower tiers are the budget models, so they also deliver a little less
 * (config.yml's performance.tiers). Every unit from before tiers is
 * Variable: a compressor exactly as it ran, a furnace or boiler at its full
 * heat, now easing off near its target.
 */
public enum Tier {
    SINGLE_STAGE(1, "Single-stage"), TWO_STAGE(2, "Two-stage"), VARIABLE(3, "Variable-speed");

    private final int number;
    private final String displayName;

    Tier(int number, String displayName) {
        this.number = number;
        this.displayName = displayName;
    }

    /** As saved in hvac.yml: 1, 2 or 3. */
    public int number() { return number; }

    /** "Variable-speed"; a variable furnace or boiler is "Modulating". */
    public String displayName(EquipmentType type) {
        return this == VARIABLE && type.fixedSpeed() ? "Modulating" : displayName;
    }

    /** "★★☆" for two-stage, with the server's own glyphs. */
    public String stars(String full, String empty) {
        return full.repeat(number) + empty.repeat(values().length - number);
    }

    /** The saved number; anything out of range is the nearest tier. */
    public static Tier of(int number) {
        return values()[Math.max(1, Math.min(values().length, number)) - 1];
    }

    /** What every unit from before tiers (3.1) is: variable, at full capacity. */
    public static final Tier LEGACY = VARIABLE;

    /** What a command may say: t1-t3, 1-3, or the kind. Null for anything else. */
    public static Tier parse(String text) {
        if (text == null) return null;
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "t1", "1", "single", "single-stage" -> SINGLE_STAGE;
            case "t2", "2", "two-stage", "dual", "dual-stage" -> TWO_STAGE;
            case "t3", "3", "variable", "variable-speed", "modulating" -> VARIABLE;
            default -> null;
        };
    }
}
