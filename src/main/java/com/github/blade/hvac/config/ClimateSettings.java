package com.github.blade.hvac.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Immutable, validated tuning for weather, humidity and dehumidifiers (2.0).
 * Kept apart from {@link HvacSettings} so the established controller tuning
 * and every caller of it stay untouched.
 */
public record ClimateSettings(Weather weather, Humidity humidity, Dehumidifier dehumidifier) {
    /**
     * @param easeHours     in-game hours for a full change of weather to take effect
     * @param swingReduction share of the day/night swing that full rain removes
     * @param waterShare    share of the air's weather shift that natural water follows
     */
    public record Weather(boolean enabled, double easeHours, double rainCoolingF, double thunderCoolingF,
                          double snowCoolingF, double swingReduction, double waterShare,
                          double windClearMs, double windRainMs, double windThunderMs) {
        public Weather {
            requirePositive("weather.ease-hours", easeHours);
            requireNonNegative("weather.rain-cooling-f", rainCoolingF);
            requireNonNegative("weather.thunder-cooling-f", thunderCoolingF);
            requireNonNegative("weather.snow-cooling-f", snowCoolingF);
            requireFraction("weather.swing-reduction", swingReduction);
            requireFraction("weather.water-share", waterShare);
            requireNonNegative("weather.wind-clear-ms", windClearMs);
            requireNonNegative("weather.wind-rain-ms", windRainMs);
            requireNonNegative("weather.wind-thunder-ms", windThunderMs);
        }
    }

    /**
     * @param outdoorRhDry          outdoor RH at the daily mean in a biome with no downfall (desert)
     * @param outdoorRhWet          outdoor RH at the daily mean in the wettest biome (jungle, swamp)
     * @param rainRh                outdoor RH that full rain or snow pulls toward
     * @param infiltrationPerHour   share of the indoor-outdoor dew point gap closed per in-game hour
     * @param coilDewPointF         the dew point cooling equipment dries air toward
     * @param coolingDryFPerHour    dew point drop per hour from cooling at full capacity
     * @param evaporationFPerHour   dew point rise per hour for a room that is all water surface
     */
    public record Humidity(double outdoorRhDry, double outdoorRhWet, double rainRh,
                           double infiltrationPerHour, double coilDewPointF, double coolingDryFPerHour,
                           double evaporationFPerHour) {
        public Humidity {
            requirePercent("humidity.outdoor-rh-dry", outdoorRhDry);
            requirePercent("humidity.outdoor-rh-wet", outdoorRhWet);
            requirePercent("humidity.rain-rh", rainRh);
            if (outdoorRhWet < outdoorRhDry)
                throw new IllegalArgumentException("humidity.outdoor-rh-wet must be at least outdoor-rh-dry");
            requirePositive("humidity.infiltration-per-hour", infiltrationPerHour);
            requireFinite("humidity.coil-dew-point-f", coilDewPointF);
            requireNonNegative("humidity.cooling-dry-f-per-minecraft-hour", coolingDryFPerHour);
            requireNonNegative("humidity.evaporation-f-per-minecraft-hour", evaporationFPerHour);
        }
    }

    /**
     * @param range          airflow reach of a dehumidifier's own zone, in blocks
     * @param maximumCells   cap on the cells one zone may hold
     * @param dryFPerHour    dew point drop per hour in a space of {@code referenceCells}
     * @param dryDewPointF   the dew point a dehumidifier dries toward
     * @param referenceCells space size the drying rate is quoted for; larger spaces dry slower
     * @param tankFillHours  hours of full-rate drying that fill the tank
     * @param emptySeconds   how long a full tank takes to empty
     * @param presets        setpoints a click on the sign cycles through, ascending
     * @param hysteresis     RH above the setpoint at which it starts again
     */
    public record Dehumidifier(int range, int maximumCells, double dryFPerHour, double dryDewPointF,
                               int referenceCells, double tankFillHours, int emptySeconds,
                               int defaultSetpoint, List<Integer> presets, double hysteresis) {
        public Dehumidifier {
            if (range < 1 || range > 64) throw new IllegalArgumentException("dehumidifier.range must be 1-64");
            if (maximumCells < 1) throw new IllegalArgumentException("dehumidifier.maximum-cells must be positive");
            requirePositive("dehumidifier.dry-f-per-minecraft-hour", dryFPerHour);
            requireFinite("dehumidifier.dry-dew-point-f", dryDewPointF);
            if (referenceCells < 1) throw new IllegalArgumentException("dehumidifier.reference-cells must be positive");
            requirePositive("dehumidifier.tank-fill-hours", tankFillHours);
            if (emptySeconds < 1) throw new IllegalArgumentException("dehumidifier.empty-seconds must be positive");
            presets = presets == null ? List.of() : List.copyOf(presets.stream().sorted().distinct().toList());
            if (presets.isEmpty()) throw new IllegalArgumentException("dehumidifier.presets must not be empty");
            for (int preset : presets)
                if (preset < 1 || preset > 99) throw new IllegalArgumentException("dehumidifier.presets must be 1-99%");
            if (defaultSetpoint < 1 || defaultSetpoint > 99)
                throw new IllegalArgumentException("dehumidifier.default-setpoint must be 1-99%");
            requireNonNegative("dehumidifier.hysteresis", hysteresis);
        }

        /** The preset after {@code current}, wrapping around; a value between presets moves to the next one up. */
        public int nextPreset(int current) {
            for (int preset : presets) if (preset > current) return preset;
            return presets.get(0);
        }
    }

    public static ClimateSettings defaults() {
        return new ClimateSettings(
                new Weather(true, 2.0, 6.0, 9.0, 3.0, 0.6, 0.1, 1.5, 3.0, 5.0),
                new Humidity(30.0, 90.0, 97.0, 0.15, 45.0, 4.0, 30.0),
                new Dehumidifier(12, 20_000, 3.0, 35.0, 1_500, 8.0, 30, 45,
                        List.of(35, 40, 45, 50, 55, 60), 2.0));
    }

    public static ClimateSettings load(FileConfiguration config) {
        ClimateSettings defaults = defaults();
        Weather w = defaults.weather;
        Humidity h = defaults.humidity;
        Dehumidifier d = defaults.dehumidifier;
        List<Integer> presets = config.isList("dehumidifier.presets")
                ? config.getIntegerList("dehumidifier.presets") : d.presets();
        return new ClimateSettings(
                new Weather(config.getBoolean("weather.enabled", w.enabled()),
                        config.getDouble("weather.ease-hours", w.easeHours()),
                        config.getDouble("weather.rain-cooling-f", w.rainCoolingF()),
                        config.getDouble("weather.thunder-cooling-f", w.thunderCoolingF()),
                        config.getDouble("weather.snow-cooling-f", w.snowCoolingF()),
                        config.getDouble("weather.swing-reduction", w.swingReduction()),
                        config.getDouble("weather.water-share", w.waterShare()),
                        config.getDouble("weather.wind-clear-ms", w.windClearMs()),
                        config.getDouble("weather.wind-rain-ms", w.windRainMs()),
                        config.getDouble("weather.wind-thunder-ms", w.windThunderMs())),
                new Humidity(config.getDouble("humidity.outdoor-rh-dry", h.outdoorRhDry()),
                        config.getDouble("humidity.outdoor-rh-wet", h.outdoorRhWet()),
                        config.getDouble("humidity.rain-rh", h.rainRh()),
                        config.getDouble("humidity.infiltration-per-hour", h.infiltrationPerHour()),
                        config.getDouble("humidity.coil-dew-point-f", h.coilDewPointF()),
                        config.getDouble("humidity.cooling-dry-f-per-minecraft-hour", h.coolingDryFPerHour()),
                        config.getDouble("humidity.evaporation-f-per-minecraft-hour", h.evaporationFPerHour())),
                new Dehumidifier(config.getInt("dehumidifier.range", d.range()),
                        config.getInt("dehumidifier.maximum-cells", d.maximumCells()),
                        config.getDouble("dehumidifier.dry-f-per-minecraft-hour", d.dryFPerHour()),
                        config.getDouble("dehumidifier.dry-dew-point-f", d.dryDewPointF()),
                        config.getInt("dehumidifier.reference-cells", d.referenceCells()),
                        config.getDouble("dehumidifier.tank-fill-hours", d.tankFillHours()),
                        config.getInt("dehumidifier.empty-seconds", d.emptySeconds()),
                        config.getInt("dehumidifier.default-setpoint", d.defaultSetpoint()),
                        presets,
                        config.getDouble("dehumidifier.hysteresis", d.hysteresis())));
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0)
            throw new IllegalArgumentException(name + " must be positive and finite");
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0)
            throw new IllegalArgumentException(name + " must be non-negative and finite");
    }

    private static void requireFraction(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0)
            throw new IllegalArgumentException(name + " must be between 0 and 1");
    }

    private static void requirePercent(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 100.0)
            throw new IllegalArgumentException(name + " must be above 0 and at most 100");
    }
}
