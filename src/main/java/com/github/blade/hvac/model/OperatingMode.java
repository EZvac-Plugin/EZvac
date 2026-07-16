package com.github.blade.hvac.model;

public enum OperatingMode {
    IDLE,
    COOLING,
    HEATING;

    public static OperatingMode parse(Object value) {
        if (!(value instanceof String text)) return IDLE;
        try { return valueOf(text.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return IDLE; }
    }
}
