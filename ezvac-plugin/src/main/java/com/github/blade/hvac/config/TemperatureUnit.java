package com.github.blade.hvac.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The unit players see and type temperatures in, plugin wide (2.1): signs,
 * chat, the HUD and placeholders. Everything inside EZvac, config.yml
 * included, stays in Fahrenheit; this only converts at the edges. Pure.
 */
public enum TemperatureUnit {
    F, C;

    /** A typed temperature: a number, optionally followed by a unit ({@code 72F}, {@code 22 c}, {@code 22°C}). */
    private static final Pattern TYPED = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*°?\\s*([fFcC])?(?![a-zA-Z])");

    /** A Fahrenheit value in this unit. */
    public double fromF(double fahrenheit) {
        return this == F ? fahrenheit : (fahrenheit - 32.0) * 5.0 / 9.0;
    }

    /** A value in this unit, in Fahrenheit. */
    public double toF(double value) {
        return this == F ? value : value * 9.0 / 5.0 + 32.0;
    }

    /** {@code 72.4 F} or {@code 22.4 C}: signs and chat. */
    public String format(double fahrenheit) {
        return format(fahrenheit, 1);
    }

    public String format(double fahrenheit, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f %s", fromF(fahrenheit), name());
    }

    /** {@code 72.4F}: sensor hub lines, where every pixel counts. */
    public String compact(double fahrenheit) {
        return String.format(Locale.US, "%.1f%s", fromF(fahrenheit), name());
    }

    /** {@code 72F}: whole degrees, for the tightest lines. */
    public String whole(double fahrenheit) {
        return String.format(Locale.US, "%.0f%s", fromF(fahrenheit), name());
    }

    /** {@code 72.4°F}: the HUD and placeholders. */
    public String degrees(double fahrenheit) {
        return String.format(Locale.US, "%.1f°%s", fromF(fahrenheit), name());
    }

    /**
     * Reads a typed temperature. A plain number is in this unit; a number
     * with F or C after it is in that unit, so someone used to the other
     * scale can't set 72 C by habit.
     *
     * @return the temperature in Fahrenheit
     * @throws IllegalArgumentException when the text is not a temperature
     */
    public double parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        Matcher match = TYPED.matcher(trimmed);
        if (!match.matches()) throw new IllegalArgumentException("'" + trimmed + "' is not a temperature (try 72F or 22C)");
        return read(match);
    }

    /** The first temperature in a line of free text, such as a sign line; empty when there is none. */
    public OptionalDouble find(String text) {
        if (text == null) return OptionalDouble.empty();
        Matcher match = TYPED.matcher(text);
        return match.find() ? OptionalDouble.of(read(match)) : OptionalDouble.empty();
    }

    private double read(Matcher match) {
        double value = Double.parseDouble(match.group(1));
        String suffix = match.group(2);
        TemperatureUnit unit = suffix == null ? this : valueOf(suffix.toUpperCase(Locale.ROOT));
        double fahrenheit = unit.toF(value);
        if (!Double.isFinite(fahrenheit)) throw new IllegalArgumentException("temperature must be finite");
        return fahrenheit;
    }

    /** The {@code units} setting: F unless it says C. Anything else is pointed out and treated as F. */
    public static TemperatureUnit load(ConfigurationSection config, Consumer<String> warn) {
        String value = String.valueOf(config.get("units", "F")).trim().toUpperCase(Locale.ROOT);
        if (value.equals("F") || value.equals("C")) return valueOf(value);
        warn.accept("units must be F or C, not '" + value + "'; using F.");
        return F;
    }

    /**
     * Before 2.1 only the HUD could show Celsius, through {@code hud.units}.
     * When the updater has just added the plugin-wide {@code units} to such
     * a config, it starts out the same, so a Celsius server stays Celsius.
     * Nothing the admin wrote is changed.
     */
    public static void carryOver(YamlConfiguration config, List<String> added) {
        if (!added.contains("units")) return;
        if (!config.contains("hud.units")) return;
        if (String.valueOf(config.get("hud.units")).trim().equalsIgnoreCase("C")) config.set("units", "C");
        config.setComments("hud.units", List.of("No longer read: the plugin-wide units setting replaced it in 2.1."));
    }
}
