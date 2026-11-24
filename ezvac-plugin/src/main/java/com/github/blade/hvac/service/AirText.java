package com.github.blade.hvac.service;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.simulation.AirQualityIndex;
import com.github.blade.hvac.simulation.AirQualityModel;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

/** How air quality reads on signs, screens, the HUD and in chat (3.2). Pure. */
public final class AirText {
    private AirText() {}

    /** {@code AQI 42}, or {@code AQI --} where the air can't be read. */
    public static String aqi(AirQualityModel.Air air) {
        return air.known() ? com.github.blade.hvac.lang.Lang.text("air.aqi", com.github.blade.hvac.lang.Lang.arg("aqi", AirQualityIndex.aqi(air)))
                : com.github.blade.hvac.lang.Lang.text("air.aqi", com.github.blade.hvac.lang.Lang.arg("aqi", "--"));
    }

    /** {@code 42}, or empty where the air can't be read. */
    public static String aqiNumber(AirQualityModel.Air air) {
        return air.known() ? Integer.toString(AirQualityIndex.aqi(air)) : "";
    }

    public static AirQualityIndex.Band band(AirQualityModel.Air air) {
        return AirQualityIndex.Band.of(AirQualityIndex.aqi(air));
    }

    /** The EPA's colours, as near as sign text has them. */
    public static NamedTextColor color(AirQualityIndex.Band band) {
        return switch (band) {
            case GOOD -> NamedTextColor.GREEN;
            case MODERATE -> NamedTextColor.YELLOW;
            case SENSITIVE -> NamedTextColor.GOLD;
            case UNHEALTHY -> NamedTextColor.RED;
            case VERY_UNHEALTHY -> NamedTextColor.DARK_PURPLE;
            case HAZARDOUS -> NamedTextColor.DARK_RED;
        };
    }

    public static NamedTextColor color(AirQualityModel.Air air) {
        return air.known() ? color(band(air)) : NamedTextColor.GRAY;
    }

    /** The same colours as legacy codes, for PlaceholderAPI and other plain-text places. */
    public static String legacyColor(AirQualityModel.Air air) {
        if (!air.known()) return "§7";
        return switch (band(air)) {
            case GOOD -> "§a";
            case MODERATE -> "§e";
            case SENSITIVE -> "§6";
            case UNHEALTHY -> "§c";
            case VERY_UNHEALTHY -> "§5";
            case HAZARDOUS -> "§4";
        };
    }

    public static AirQualityIndex.PollenLevel pollen(AirQualityModel.Air air, AirQualitySettings settings) {
        return AirQualityIndex.PollenLevel.of(air.pollen(), settings.pollenLevels());
    }

    /** The pollen level's name for a sign or chat ("Moderate"), or "Off" while pollen is switched off (BETA 1.0). */
    public static String pollenName(AirQualityModel.Air air, AirQualitySettings settings) {
        return settings.pollen() ? pollen(air, settings).displayName() : POLLEN_OFF;
    }

    /** The same, short for the HUD ("Med"). */
    public static String pollenHudName(AirQualityModel.Air air, AirQualitySettings settings) {
        return settings.pollen() ? pollen(air, settings).hudName() : com.github.blade.hvac.lang.Lang.text("air.pollen-off");
    }

    static final String POLLEN_OFF = "Off";

    /**
     * For a sign line: {@code AQI 42 Good}, or what drives it when it is not
     * good: {@code AQI 160 Smoke}.
     */
    public static String signLine(AirQualityModel.Air air) {
        if (!air.known()) return "AQI --";
        AirQualityIndex.Band band = band(air);
        return aqi(air) + " " + (band == AirQualityIndex.Band.GOOD ? band.shortName()
                : AirQualityIndex.dominant(air).cause());
    }

    /**
     * For a screen or chat: {@code AQI 42 Good · Pollen High}, or
     * {@code AQI 160 Unhealthy · Smoke · Pollen Low}.
     */
    public static String summary(AirQualityModel.Air air, AirQualitySettings settings) {
        if (!air.known()) return "AQI --";
        AirQualityIndex.Band band = band(air);
        String cause = band == AirQualityIndex.Band.GOOD ? "" : " · " + AirQualityIndex.dominant(air).cause();
        return com.github.blade.hvac.lang.Lang.text("air.summary", com.github.blade.hvac.lang.Lang.arg("aqi", aqi(air)),
                com.github.blade.hvac.lang.Lang.arg("band", band.displayName() + cause),
                com.github.blade.hvac.lang.Lang.arg("pollen", pollenName(air, settings)));
    }

    /** PM2.5 and PM10 in µg/m³ and the pollen count, for /hvac info. */
    public static String details(AirQualityModel.Air air) {
        if (!air.known()) return com.github.blade.hvac.lang.Lang.text("command.fridge.unmeasured");
        return com.github.blade.hvac.lang.Lang.text("air.details", com.github.blade.hvac.lang.Lang.arg("pm25", String.format(Locale.US, "%.1f", air.pm25())),
                com.github.blade.hvac.lang.Lang.arg("pm10", String.format(Locale.US, "%.0f", air.pm10())),
                com.github.blade.hvac.lang.Lang.arg("pollen", String.format(Locale.US, "%.0f", air.pollen())));
    }
}
