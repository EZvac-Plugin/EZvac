package com.github.blade.hvac.api;

/**
 * Conversions for the API's temperatures, which are always Fahrenheit.
 *
 * @since 1.0
 */
public final class Temperatures {
    private Temperatures() {}

    public static double celsius(double fahrenheit) {
        return (fahrenheit - 32.0) * 5.0 / 9.0;
    }

    public static double fahrenheit(double celsius) {
        return celsius * 9.0 / 5.0 + 32.0;
    }
}
