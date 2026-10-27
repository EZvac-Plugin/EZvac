package com.github.blade.hvac.hud;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Answers {@code %ezvac_<key>%} from a snapshot. Pure and thread-safe: it
 * reads nothing but the immutable snapshot it is given, because placeholder
 * plugins often ask from background threads.
 */
public final class PlaceholderResolver {
    /** Every supported key, in the order the docs list them. */
    public static final List<String> KEYS = List.of(
            "temp", "temp_f", "temp_c", "outdoor", "outdoor_f", "outdoor_c",
            "bar", "bar_plain", "comfort", "system", "group", "thermostat", "mode", "target",
            "influence", "zone", "conditioned", "medium",
            "feels_like", "feels_like_f", "feels_like_c", "humidity", "weather", "season", "season_day",
            "aqi", "aqi_colored", "aqi_band", "pm25", "pm10", "pollen", "pollen_count");

    private final HudFormatter format;

    public PlaceholderResolver(HudFormatter format) {
        this.format = Objects.requireNonNull(format, "format");
    }

    /**
     * @param snapshot the player's latest snapshot, or null when there is none yet
     * @return the value, or null for an unknown key (PlaceholderAPI then leaves it as typed)
     */
    public String resolve(ClimateSnapshot snapshot, String key) {
        if (key == null) return null;
        String normalized = key.toLowerCase(Locale.ROOT);
        if (!KEYS.contains(normalized)) return null;
        if (snapshot == null) return format.settings().noneText();
        return switch (normalized) {
            case "temp" -> format.temperature(snapshot.localF());
            case "temp_f" -> HudFormatter.fahrenheit(snapshot.localF());
            case "temp_c" -> HudFormatter.celsius(snapshot.localF());
            case "outdoor" -> format.temperature(snapshot.outdoorF());
            case "outdoor_f" -> HudFormatter.fahrenheit(snapshot.outdoorF());
            case "outdoor_c" -> HudFormatter.celsius(snapshot.outdoorF());
            case "bar" -> format.barLegacy(snapshot);
            case "bar_plain" -> format.barPlain(snapshot);
            case "comfort" -> format.band(snapshot).label();
            // "group" is the name it had before 3.0; it means the same.
            case "system", "group" -> format.system(snapshot);
            case "thermostat" -> format.thermostat(snapshot);
            case "mode" -> format.mode(snapshot);
            case "target" -> format.target(snapshot);
            case "influence" -> Long.toString(Math.round(snapshot.influence() * 100));
            case "zone" -> format.zone(snapshot);
            case "conditioned" -> Boolean.toString(snapshot.conditioned());
            case "medium" -> snapshot.water() ? "water" : "air";
            case "feels_like" -> format.feelsLike(snapshot);
            case "feels_like_f" -> HudFormatter.fahrenheit(snapshot.feelsLikeF());
            case "feels_like_c" -> HudFormatter.celsius(snapshot.feelsLikeF());
            case "humidity" -> format.humidity(snapshot);
            case "weather" -> snapshot.weather();
            case "season" -> snapshot.season();
            case "season_day" -> snapshot.seasonDay() > 0 ? Integer.toString(snapshot.seasonDay()) : "";
            case "aqi" -> format.aqi(snapshot);
            case "aqi_colored" -> format.aqiLegacy(snapshot);
            case "aqi_band" -> format.aqiBand(snapshot);
            case "pm25" -> snapshot.air().known()
                    ? String.format(Locale.US, "%.1f", snapshot.air().pm25()) : format.settings().noneText();
            case "pm10" -> snapshot.air().known()
                    ? Long.toString(Math.round(snapshot.air().pm10())) : format.settings().noneText();
            case "pollen" -> format.pollen(snapshot);
            case "pollen_count" -> snapshot.air().known()
                    ? Long.toString(Math.round(snapshot.air().pollen())) : format.settings().noneText();
            default -> null;
        };
    }
}
