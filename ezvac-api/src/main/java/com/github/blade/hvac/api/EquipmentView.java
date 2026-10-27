package com.github.blade.hvac.api;

import com.github.blade.hvac.api.event.EquipmentStateChangeEvent;

import java.util.Objects;

/**
 * A heat pump, air conditioner, furnace, boiler or pool heat pump, as it was
 * when this view was made.
 *
 * @param kind     what it is; the same kinds as {@link EquipmentStateChangeEvent}
 * @param system   the key of the system it belongs to (see {@link EzvacApi#thermostatOf})
 * @param position its dispenser
 * @param enabled  false when switched off on its own ({@code /hvac equipment disable})
 * @param running  true while it runs
 * @param mode     what it does while running, IDLE when it doesn't
 * @param rpm      its motor's simulated speed, as an RPM monitor shows it; 0 when stopped
 * @param tier     its tier, shown as stars (1.4, EZvac 3.1): 1 single-stage, 2 two-stage, 3 variable-speed
 *                 (a modulating furnace or boiler). Every unit from before 3.1 is 3
 * @since 1.2
 */
public record EquipmentView(EquipmentStateChangeEvent.Kind kind, String system, Position position, boolean enabled,
                            boolean running, ThermostatView.Mode mode, double rpm, int tier) implements HvacDevice {
    public EquipmentView {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(mode, "mode");
        if (tier < 1 || tier > 3) throw new IllegalArgumentException("tier must be 1 to 3");
    }

    /** A variable unit (tier 3), as every unit from before API 1.4 is. */
    public EquipmentView(EquipmentStateChangeEvent.Kind kind, String system, Position position, boolean enabled,
                         boolean running, ThermostatView.Mode mode, double rpm) {
        this(kind, system, position, enabled, running, mode, rpm, 3);
    }
}
