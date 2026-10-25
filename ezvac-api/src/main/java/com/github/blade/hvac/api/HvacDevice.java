package com.github.blade.hvac.api;

/**
 * Any EZvac device: a thermostat, a piece of equipment, a vent, a
 * dehumidifier or humidifier, or one of the instruments (thermometers,
 * sensors, hubs, RPM monitors, settings panels). Match on the view for the
 * details:
 *
 * <pre>{@code
 * for (HvacDevice device : ezvac.devices(world)) {
 *     if (device instanceof EquipmentView equipment && equipment.running()) ...
 * }
 * }</pre>
 *
 * @since 1.2
 */
public sealed interface HvacDevice permits ThermostatView, EquipmentView, VentView, ClimateUnitView, InstrumentView {
    /** The block the device is: a sign, a dispenser, a vent, a button. */
    Position position();
}
