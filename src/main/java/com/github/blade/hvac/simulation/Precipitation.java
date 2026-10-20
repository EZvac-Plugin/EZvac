package com.github.blade.hvac.simulation;

import java.util.Locale;

/** What falls in a biome when the world's weather turns. */
public enum Precipitation {
    /** Deserts, savannas, badlands, the Nether and the End: weather passes them by. */
    NONE,
    RAIN,
    SNOW;

    /** Unknown or missing values read as rain, the common case. */
    public static Precipitation parse(Object value) {
        if (!(value instanceof String text) || text.isBlank()) return RAIN;
        try { return valueOf(text.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException unknown) { return RAIN; }
    }
}
