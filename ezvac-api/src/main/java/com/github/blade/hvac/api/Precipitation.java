package com.github.blade.hvac.api;

/**
 * What is falling at a spot (API 1.9): nothing, rain or snow. EZvac decides
 * it by its own air temperature there, season, height and time of day
 * included: snow at or below the server's snow line temperature (33 F by
 * default), rain above. Vanilla's dry places (deserts, badlands, savannas)
 * get rain as the server's {@code weather.dry-areas} allows: by default only
 * in thunderstorms.
 *
 * @since 1.9
 */
public enum Precipitation {
    /** Not raining here, or a place the rain doesn't reach. */
    NONE,
    RAIN,
    SNOW
}
