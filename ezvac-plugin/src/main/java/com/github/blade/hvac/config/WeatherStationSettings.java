package com.github.blade.hvac.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * Weather stations (BETA 1.0), read from {@code weather-stations} in
 * config.yml.
 *
 * @param range      how near a player must be to a station to see its forecast, in blocks
 * @param areaRadius how far each way a station measures the outdoor climate, in blocks
 * @param areaHeight how far above the station it measures, in blocks
 * @param sparks     whether a station that is on gives off sparks at its rods
 */
public record WeatherStationSettings(int range, int areaRadius, int areaHeight, boolean sparks) {
    public WeatherStationSettings {
        if (range < 16 || range > 10_000) throw new IllegalArgumentException("weather-stations.range must be 16 to 10000");
        if (areaRadius < 0 || areaRadius > 256)
            throw new IllegalArgumentException("weather-stations.area-radius must be 0 to 256");
        if (areaHeight < 0 || areaHeight > 128)
            throw new IllegalArgumentException("weather-stations.area-height must be 0 to 128");
    }

    public static WeatherStationSettings defaults() {
        return new WeatherStationSettings(400, 100, 20, true);
    }

    public static WeatherStationSettings load(ConfigurationSection config, Consumer<String> warn) {
        WeatherStationSettings d = defaults();
        ConfigurationSection section = config.getConfigurationSection("weather-stations");
        if (section == null) return d;
        try {
            return new WeatherStationSettings(section.getInt("range", d.range), section.getInt("area-radius", d.areaRadius),
                    section.getInt("area-height", d.areaHeight), section.getBoolean("sparks", d.sparks));
        } catch (IllegalArgumentException exception) {
            warn.accept(exception.getMessage() + "; using the defaults for weather stations.");
            return d;
        }
    }
}
