package com.github.blade.hvac.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A weather station (EZvac BETA 1.0), as it was when this view was made: a
 * tower an admin built that measures the outdoor climate around it and
 * forecasts seven days. Not an {@link HvacDevice}, so addons that match on
 * every device kind keep working; find stations with
 * {@link EzvacApi#weatherStations(org.bukkit.World)} and friends.
 *
 * @param label            its label; labels may repeat
 * @param position         its sign
 * @param enabled          false when an admin switched it off
 * @param intact           false while its tower is damaged
 * @param lightningStrikes how many times lightning has struck its tower
 * @param outdoorF         the outdoor air of the area it measures, right now; NaN before it has measured it
 * @param forecast         today first, seven days; empty while it isn't forecasting (off, damaged, switched
 *                         off server-wide, or not measured yet)
 * @since 1.6
 */
public record WeatherStationView(String label, Position position, boolean enabled, boolean intact,
                                 long lightningStrikes, double outdoorF, List<ForecastDay> forecast) {
    public WeatherStationView {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(position, "position");
        forecast = forecast == null ? List.of() : List.copyOf(forecast);
    }

    /** Whether it is forecasting: on, standing, switched on server-wide, and measured. */
    public boolean working() { return !forecast.isEmpty(); }

    public double outdoorC() { return Temperatures.celsius(outdoorF); }

    /** A day's weather, as the station's screen and /hvac forecast show it. */
    public enum Sky { CLEAR, RAIN, SNOW, THUNDER }

    /**
     * One day of a forecast. Temperatures, wind and pollen are exact; rain and
     * thunder are chances, except what Minecraft's weather clocks already know
     * for today and tomorrow, which {@code note} says.
     *
     * @param daysAhead     0 for today
     * @param highF         the day's high
     * @param lowF          the day's low
     * @param sky           its weather: the likeliest, at an even chance or more
     * @param rainChance    0..1, rain (or snow where it's cold); 0 in a dry area
     * @param thunderChance 0..1
     * @param wind          the day's wind on open ground; empty with wind switched off
     * @param pollen        grains/m³; NaN with air quality or pollen switched off
     * @param note          what is certain about the day, such as {@code Rain from 15:00}, or empty
     */
    public record ForecastDay(int daysAhead, double highF, double lowF, Sky sky, double rainChance,
                              double thunderChance, Optional<Wind.Day> wind, double pollen, String note) {
        public ForecastDay {
            Objects.requireNonNull(sky, "sky");
            wind = wind == null ? Optional.empty() : wind;
            note = note == null ? "" : note;
        }

        public double highC() { return Temperatures.celsius(highF); }

        public double lowC() { return Temperatures.celsius(lowF); }
    }
}
