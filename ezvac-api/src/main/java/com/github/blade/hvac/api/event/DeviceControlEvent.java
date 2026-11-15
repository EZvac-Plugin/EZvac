package com.github.blade.hvac.api.event;

import com.github.blade.hvac.api.HvacDevice;
import com.github.blade.hvac.api.control.ControlSource;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * A device is about to be changed through the API or by a button on its
 * screen (API 1.12): who asked, what, and to what. Cancel it and nothing
 * changes; the caller's result says {@code CANCELLED}, and a player at the
 * screen hears the dull note. A protection or claims plugin can keep
 * strangers off a thermostat this way. Main thread.
 *
 * @since 1.12
 */
public final class DeviceControlEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    /** What is being changed. New settings may come in later versions: a switch needs a default branch. */
    public enum Setting {
        /** A thermostat's target, in F ({@link Double}). */
        TARGET,
        /** A thermostat switched on or off ({@link Boolean}). */
        ENABLED,
        /** A system's profile ({@link com.github.blade.hvac.api.ThermostatView.Profile}). */
        PROFILE,
        /** A climate unit switched on or off ({@link Boolean}). */
        POWER,
        /** A climate unit's setpoint, % RH or F ({@link Integer}). */
        SETPOINT,
        /** A refrigerator's fridge compartment's setpoint, in F ({@link Integer}). */
        FRIDGE_SETPOINT,
        /** An air purifier's fan ({@link com.github.blade.hvac.api.ClimateUnitView.Fan}). */
        FAN,
        /** A defrost started ({@link Boolean#TRUE}). */
        DEFROST,
        /** A refrigerator's freezer's auto defrost ({@link Boolean}). */
        AUTO_DEFROST,
        /** A piece of equipment switched on or off ({@link Boolean}). */
        EQUIPMENT_ENABLED
    }

    private final HvacDevice device;
    private final Setting setting;
    private final Object value;
    private final ControlSource source;
    private boolean cancelled;

    public DeviceControlEvent(HvacDevice device, Setting setting, Object value, ControlSource source) {
        this.device = Objects.requireNonNull(device, "device");
        this.setting = Objects.requireNonNull(setting, "setting");
        this.value = Objects.requireNonNull(value, "value");
        this.source = Objects.requireNonNull(source, "source");
    }

    /** The device as it is before the change. */
    public HvacDevice getDevice() { return device; }

    public Setting getSetting() { return setting; }

    /** What it is being changed to, as asked (it may still be kept within the device's range). */
    public Object getValue() { return value; }

    public ControlSource getSource() { return source; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
