package com.github.blade.hvac.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A device that shows or sets something rather than conditioning air: a
 * thermometer or hygrometer sign, a sensor button, a sensor hub, an RPM
 * monitor or a settings panel. Its readings aren't part of the view; read a
 * spot with {@link EzvacApi#readingAt}.
 *
 * @param kind     what it is
 * @param label    its name
 * @param position its sign or button
 * @param system   the key of the system it belongs to: set for RPM monitors and settings panels
 *                 that are linked to one (since 1.3 they can be unlinked)
 * @param links    the sensors a hub shows, in order; since 1.3 the equipment an RPM monitor
 *                 shows; empty for everything else
 * @since 1.2
 */
public record InstrumentView(Kind kind, String label, Position position, Optional<String> system,
                             List<Position> links) implements HvacDevice {
    public InstrumentView {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(position, "position");
        system = system == null ? Optional.empty() : system;
        links = links == null ? List.of() : List.copyOf(links);
    }

    public enum Kind {
        THERMOMETER, HYGROMETER, THERMO_HYGROMETER,
        TEMPERATURE_SENSOR, HUMIDITY_SENSOR, TEMPERATURE_HUMIDITY_SENSOR, WEATHER_SENSOR,
        SENSOR_HUB, RPM_MONITOR, SETTINGS_PANEL,
        /** An air monitor sign and a cherry-button air sensor (1.5). */
        AIR_MONITOR, AIR_SENSOR,
        /** A lightning-rod wind sensor (1.5). */
        WIND_SENSOR;

        /** True for the sensors: the buttons, and the lightning-rod wind sensor. */
        public boolean sensor() {
            return this == TEMPERATURE_SENSOR || this == HUMIDITY_SENSOR || this == TEMPERATURE_HUMIDITY_SENSOR
                    || this == WEATHER_SENSOR || this == AIR_SENSOR || this == WIND_SENSOR;
        }
    }
}
