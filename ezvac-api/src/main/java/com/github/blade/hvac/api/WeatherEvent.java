package com.github.blade.hvac.api;

import java.util.Locale;

/**
 * Weather out of the ordinary, from an addon's {@link WeatherPlan} (API 1.9).
 * More may be added in later versions, so a switch over these should have a
 * default.
 *
 * @since 1.9
 */
public enum WeatherEvent {
    /** Heavy snow and a gale where it snows; a heavy rainstorm where it's too warm. */
    SNOWSTORM,
    /** A front bringing thunder. */
    THUNDERSTORM,
    /** Days well above the season's usual air. */
    HEAT_WAVE,
    /** Days well below it. */
    COLD_SNAP;

    /** As players read it: {@code Heat wave}. */
    public String displayName() {
        String name = name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
