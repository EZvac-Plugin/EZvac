package com.github.blade.hvac.model;

import java.util.Locale;

/** User-selected variable-speed policy for one HVAC group. */
public enum OperatingProfile {
    ECO,
    NORMAL,
    TURBO;

    public OperatingProfile next() {
        return switch (this) {
            case ECO -> NORMAL;
            case NORMAL -> TURBO;
            case TURBO -> ECO;
        };
    }

    public OperatingProfile previous() {
        return switch (this) {
            case ECO -> TURBO;
            case NORMAL -> ECO;
            case TURBO -> NORMAL;
        };
    }

    public static OperatingProfile parse(Object value) {
        if (value instanceof OperatingProfile profile) return profile;
        if (!(value instanceof String text)) return NORMAL;
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NORMAL;
        }
    }
}
