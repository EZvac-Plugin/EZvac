package com.github.blade.hvac.api;

/**
 * The unit a server shows and takes temperatures in ({@code units:} in
 * EZvac's config). The API itself always speaks Fahrenheit.
 *
 * @since 1.0
 */
public enum TemperatureUnit {
    F, C;

    /** A Fahrenheit value in this unit. */
    public double fromFahrenheit(double fahrenheit) {
        return this == F ? fahrenheit : Temperatures.celsius(fahrenheit);
    }
}
