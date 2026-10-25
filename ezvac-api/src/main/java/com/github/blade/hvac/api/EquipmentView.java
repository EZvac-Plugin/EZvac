package com.github.blade.hvac.api;

import com.github.blade.hvac.api.event.EquipmentStateChangeEvent;

import java.util.Objects;

/**
 * A heat pump, air conditioner, furnace, boiler or pool heat pump, as it was
 * when this view was made.
 *
 * @param kind     what it is; the same kinds as {@link EquipmentStateChangeEvent}
 * @param system   the system (group) it belongs to
 * @param position its dispenser
 * @param enabled  false when switched off on its own ({@code /hvac equipment disable})
 * @param running  true while it runs
 * @param mode     what it does while running, IDLE when it doesn't
 * @param rpm      its motor's simulated speed, as an RPM monitor shows it; 0 when stopped
 * @since 1.2
 */
public record EquipmentView(EquipmentStateChangeEvent.Kind kind, String system, Position position, boolean enabled,
                            boolean running, ThermostatView.Mode mode, double rpm) implements HvacDevice {
    public EquipmentView {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(mode, "mode");
    }
}
