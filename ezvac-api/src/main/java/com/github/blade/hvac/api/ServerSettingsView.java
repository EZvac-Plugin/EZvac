package com.github.blade.hvac.api;

import java.util.Objects;
import java.util.Set;

/**
 * What the server owner has switched in /hvac quicksettings (EZvac BETA 1.0),
 * as it is now.
 *
 * @param devicesOff   the kinds of device switched off server-wide, as config.yml's
 *                     {@code devices.disabled} names them: {@code heat-pump}, {@code air-conditioner},
 *                     {@code furnace}, {@code boiler}, {@code pool-heat-pump}, {@code dehumidifier},
 *                     {@code humidifier}, {@code air-purifier}, {@code weather-station}; more may come
 * @param airQuality   air quality (the AQI, smoke, purifiers)
 * @param pollen       pollen
 * @param wind         the daily wind
 * @param seasons      the seasons
 * @param buildingSize size-based heating and cooling
 * @since 1.6
 */
public record ServerSettingsView(Set<String> devicesOff, boolean airQuality, boolean pollen, boolean wind,
                                 boolean seasons, boolean buildingSize) {
    public ServerSettingsView {
        devicesOff = devicesOff == null ? Set.of() : Set.copyOf(devicesOff);
    }

    /** Whether a kind of device, by its config.yml name, is switched off server-wide. */
    public boolean deviceOff(String kind) {
        return devicesOff.contains(Objects.requireNonNull(kind, "kind"));
    }
}
