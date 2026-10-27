package com.github.blade.hvac.api;

import java.util.Objects;
import java.util.Optional;

/**
 * The climate at one spot, as players see it on the HUD and in
 * {@code /hvac temperature}: spot-to-spot variance included. Temperatures
 * are Fahrenheit.
 *
 * @param position       where it was read
 * @param temperatureF   the air temperature, or the water's when {@code inWater}
 * @param feelsLikeF     the apparent temperature (humidity and wind); the water temperature in water
 * @param humidity       relative humidity in percent, NaN in water
 * @param outdoorF       the outdoor air temperature at this spot
 * @param inWater        true when the spot is in water
 * @param weather        Clear, Rain, Thunder or Snow; empty where weather never reaches
 * @param season         the season here, empty where there are none
 * @param thermostat     the label of the thermostat whose system conditions this spot, if any:
 *                       the name to show players
 * @param system         that system's key, if any (see {@link EzvacApi#thermostatOf})
 * @param influence      how strongly that system conditions the spot, 0 to 1
 * @param airQuality     what is in the air at the spot (1.5); empty in water, or where the server
 *                       has air quality switched off
 * @param wind           the wind felt at the spot (1.5): calm indoors, in water and where a system
 *                       conditions it
 * @since 1.0
 */
public record ClimateReading(Position position, double temperatureF, double feelsLikeF, double humidity,
                             double outdoorF, boolean inWater, String weather, Optional<SeasonView.Season> season,
                             Optional<String> thermostat, Optional<String> system, double influence,
                             Optional<AirQuality> airQuality, Wind wind) {
    public ClimateReading {
        Objects.requireNonNull(position, "position");
        weather = weather == null ? "" : weather;
        season = season == null ? Optional.empty() : season;
        thermostat = thermostat == null ? Optional.empty() : thermostat;
        system = system == null ? Optional.empty() : system;
        airQuality = airQuality == null ? Optional.empty() : airQuality;
        wind = wind == null ? Wind.CALM : wind;
    }

    /** A reading without wind. */
    public ClimateReading(Position position, double temperatureF, double feelsLikeF, double humidity,
                          double outdoorF, boolean inWater, String weather, Optional<SeasonView.Season> season,
                          Optional<String> thermostat, Optional<String> system, double influence,
                          Optional<AirQuality> airQuality) {
        this(position, temperatureF, feelsLikeF, humidity, outdoorF, inWater, weather, season, thermostat, system,
                influence, airQuality, Wind.CALM);
    }

    /** A reading without air quality, as before API 1.5. */
    public ClimateReading(Position position, double temperatureF, double feelsLikeF, double humidity,
                          double outdoorF, boolean inWater, String weather, Optional<SeasonView.Season> season,
                          Optional<String> thermostat, Optional<String> system, double influence) {
        this(position, temperatureF, feelsLikeF, humidity, outdoorF, inWater, weather, season, thermostat, system,
                influence, Optional.empty(), Wind.CALM);
    }

    /** True where an EZvac system conditions the spot. */
    public boolean conditioned() { return thermostat.isPresent() && influence > 0.0; }

    public double temperatureC() { return Temperatures.celsius(temperatureF); }

    public double feelsLikeC() { return Temperatures.celsius(feelsLikeF); }

    public double outdoorC() { return Temperatures.celsius(outdoorF); }
}
