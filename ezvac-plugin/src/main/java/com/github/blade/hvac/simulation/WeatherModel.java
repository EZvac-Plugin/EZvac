package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;

/**
 * How weather changes the outdoor air. Vanilla weather switches in an
 * instant; the air should not, so each world carries a {@link Conditions}
 * that eases toward the current weather over a few in-game hours.
 */
public final class WeatherModel {
    private WeatherModel() {}

    /**
     * Eased weather for one world.
     *
     * @param wetness  0 clear .. 1 raining (or snowing), eased
     * @param storm    0 .. 1 thundering, eased
     * @param raining  the world's weather right now, for labels
     * @param thundering the world's thunder right now, for labels
     */
    public record Conditions(double wetness, double storm, boolean raining, boolean thundering) {
        public static final Conditions CLEAR = new Conditions(0.0, 0.0, false, false);

        /** Weather that has been this way long enough to have fully set in. */
        public static Conditions settled(boolean raining, boolean thundering) {
            return new Conditions(raining ? 1.0 : 0.0, raining && thundering ? 1.0 : 0.0, raining, thundering);
        }

        /** These conditions after {@code elapsedTicks} of easing toward the given weather. */
        public Conditions advance(boolean nowRaining, boolean nowThundering, long elapsedTicks, double easeHours) {
            double step = elapsedTicks / ThermalModel.MINECRAFT_TICKS_PER_HOUR / easeHours;
            return new Conditions(approach(wetness, nowRaining ? 1.0 : 0.0, step),
                    approach(storm, nowRaining && nowThundering ? 1.0 : 0.0, step), nowRaining, nowThundering);
        }
    }

    /**
     * What the weather does to the air in one biome.
     *
     * @param meanShiftF  change to the daily mean temperature
     * @param swingFactor multiplier on the day/night swing
     * @param windMs      outdoor wind for apparent temperature
     * @param wetness     0..1, how strongly rain or snow is pulling RH up here
     */
    public record Effect(double meanShiftF, double swingFactor, double windMs, double wetness) {
        public static Effect calm(ClimateSettings.Weather settings) {
            return new Effect(0.0, 1.0, settings.windClearMs(), 0.0);
        }

        /** The noon/midnight pair that produces the shifted mean and narrowed swing. */
        public double noonF(double noonF, double midnightF) {
            return (noonF + midnightF) / 2.0 + meanShiftF + (noonF - midnightF) / 2.0 * swingFactor;
        }

        public double midnightF(double noonF, double midnightF) {
            return (noonF + midnightF) / 2.0 + meanShiftF - (noonF - midnightF) / 2.0 * swingFactor;
        }
    }

    public static Effect effect(Precipitation precipitation, Conditions conditions,
                                ClimateSettings.Weather settings) {
        if (!settings.enabled() || precipitation == Precipitation.NONE || conditions == null)
            return Effect.calm(settings);
        double wet = clamp(conditions.wetness());
        double storm = Math.min(wet, clamp(conditions.storm()));
        double cooling = precipitation == Precipitation.SNOW
                ? settings.snowCoolingF() * wet
                : settings.rainCoolingF() * wet
                + Math.max(0.0, settings.thunderCoolingF() - settings.rainCoolingF()) * storm;
        double wind = settings.windClearMs() + (settings.windRainMs() - settings.windClearMs()) * wet
                + (settings.windThunderMs() - settings.windRainMs()) * storm;
        return new Effect(-cooling, 1.0 - settings.swingReduction() * wet, Math.max(0.0, wind), wet);
    }

    /**
     * The effect on a spot whose surroundings are partly rain, partly snow and
     * partly dry, as at a biome border (2.1): each kind's effect, mixed by its
     * share. Rain at a desert's edge cools it partway. A single biome gets
     * exactly its own effect.
     */
    public static Effect effect(BiomeBlend.Blended climate, Conditions conditions, ClimateSettings.Weather settings) {
        double rain = clamp(climate.rainShare());
        double snow = Math.min(clamp(climate.snowShare()), 1.0 - rain);
        double dry = 1.0 - rain - snow;
        if (rain == 1.0) return effect(Precipitation.RAIN, conditions, settings);
        if (snow == 1.0) return effect(Precipitation.SNOW, conditions, settings);
        Effect calm = Effect.calm(settings);
        if (dry >= 1.0) return calm;
        Effect wet = effect(Precipitation.RAIN, conditions, settings);
        Effect cold = effect(Precipitation.SNOW, conditions, settings);
        return new Effect(
                wet.meanShiftF() * rain + cold.meanShiftF() * snow + calm.meanShiftF() * dry,
                wet.swingFactor() * rain + cold.swingFactor() * snow + calm.swingFactor() * dry,
                wet.windMs() * rain + cold.windMs() * snow + calm.windMs() * dry,
                wet.wetness() * rain + cold.wetness() * snow + calm.wetness() * dry);
    }

    /** A short word for the HUD: Clear, Rain, Thunder or Snow, or empty where weather never reaches. */
    public static String word(Precipitation precipitation, Conditions conditions) {
        if (precipitation == Precipitation.NONE || conditions == null) return "";
        if (!conditions.raining()) return "Clear";
        if (precipitation == Precipitation.SNOW) return "Snow";
        return conditions.thundering() ? "Thunder" : "Rain";
    }

    private static double approach(double current, double target, double step) {
        if (!Double.isFinite(current)) return target;
        if (current < target) return Math.min(target, current + step);
        return Math.max(target, current - step);
    }

    private static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }
}
