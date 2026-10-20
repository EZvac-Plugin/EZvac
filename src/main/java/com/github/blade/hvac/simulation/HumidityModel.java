package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;

/** Outdoor and indoor moisture, as dew points. Pure and stateless. */
public final class HumidityModel {
    /** Integration step: rates are re-evaluated at least this often, in in-game hours. */
    private static final double STEP_HOURS = 0.05;
    /** A wet fraction beyond this (a quarter of the space is water surface) evaporates no faster. */
    private static final double MAXIMUM_WET_FRACTION = 0.25;

    private HumidityModel() {}

    /**
     * What is adding or removing moisture in a space during one step.
     *
     * @param coolingCapacity  delivered cooling capacity (0 when not cooling)
     * @param wetFraction      water surface relative to the space's size
     * @param waterF           temperature of that water, NaN when unknown (taken as the room's)
     * @param dehumidifierScale sum of running dehumidifiers' strength in this space, 1 = one unit
     *                          at its reference size
     */
    public record Loads(double coolingCapacity, double wetFraction, double waterF, double dehumidifierScale) {
        public static final Loads NONE = new Loads(0.0, 0.0, Double.NaN, 0.0);
    }

    /**
     * @param dewPointF       the space's new dew point
     * @param dehumidifiedF   dew point drop the dehumidifiers achieved, for filling their tanks
     */
    public record Result(double dewPointF, double dehumidifiedF) {}

    /**
     * Advances a space's moisture. Infiltration closes a share of the gap to
     * the outdoors each hour; cooling and dehumidifiers pull toward their
     * coil's dew point, harder the further above it the air is; water
     * evaporates faster when it is warmer than the air, and never raises the
     * dew point past its own temperature. The dew point never exceeds the air
     * temperature: anything beyond 100% RH condenses out.
     */
    public static Result step(double dewPointF, double roomF, double outdoorDewPointF, Loads loads,
                              long elapsedTicks, ClimateSettings settings) {
        requireFinite("room temperature", roomF);
        requireFinite("outdoor dew point", outdoorDewPointF);
        double dew = Double.isFinite(dewPointF) ? Math.min(dewPointF, roomF) : Math.min(outdoorDewPointF, roomF);
        if (elapsedTicks <= 0) return new Result(dew, 0.0);
        ClimateSettings.Humidity humidity = settings.humidity();
        ClimateSettings.Dehumidifier dehumidifier = settings.dehumidifier();
        double waterF = Double.isFinite(loads.waterF()) ? loads.waterF() : roomF;
        double wet = Math.max(0.0, Math.min(MAXIMUM_WET_FRACTION, loads.wetFraction()));
        double evaporation = humidity.evaporationFPerHour() * wet * (1.0 + Math.max(0.0, waterF - roomF) / 20.0);
        double cooling = humidity.coolingDryFPerHour() * Math.max(0.0, loads.coolingCapacity());
        double drying = dehumidifier.dryFPerHour() * Math.max(0.0, loads.dehumidifierScale());

        double remaining = elapsedTicks / ThermalModel.MINECRAFT_TICKS_PER_HOUR;
        double dehumidified = 0.0;
        while (remaining > 1e-12) {
            double hours = Math.min(STEP_HOURS, remaining);
            double rate = (outdoorDewPointF - dew) * humidity.infiltrationPerHour()
                    + evaporation * fraction((waterF - dew) / 10.0)
                    - cooling * fraction((dew - humidity.coilDewPointF()) / 15.0);
            double removal = drying * fraction((dew - dehumidifier.dryDewPointF()) / 15.0);
            double next = Math.min(roomF, dew + (rate - removal) * hours);
            dehumidified += removal * hours;
            dew = next;
            remaining -= hours;
        }
        return new Result(Math.max(ThermalModel.MINIMUM_SIMULATED_F, dew), dehumidified);
    }

    private static double fraction(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    /**
     * The outdoor dew point in a biome. It is held steady through the day, so
     * RH rises at night as the air cools and falls at noon, the way real
     * humidity swings. Rain and snow pull it up toward saturation.
     *
     * @param downfall the vanilla biome downfall, 0 (desert) to 1 (jungle, swamp)
     * @param airNowF  the outdoor air temperature right now, weather included
     */
    public static double outdoorDewPointF(double noonF, double midnightF, double downfall,
                                          WeatherModel.Effect effect, double airNowF,
                                          ClimateSettings.Humidity settings) {
        double mean = (noonF + midnightF) / 2.0 + effect.meanShiftF();
        double wetness = Double.isFinite(downfall) ? Math.max(0.0, Math.min(1.0, downfall)) : 0.5;
        double baseRh = settings.outdoorRhDry() + (settings.outdoorRhWet() - settings.outdoorRhDry()) * wetness;
        double dew = Psychrometrics.dewPointF(mean, baseRh);
        if (effect.wetness() > 0.0) {
            double rainDew = Psychrometrics.dewPointF(airNowF, settings.rainRh());
            if (rainDew > dew) dew += (rainDew - dew) * effect.wetness();
        }
        return Math.min(dew, airNowF);
    }
}
