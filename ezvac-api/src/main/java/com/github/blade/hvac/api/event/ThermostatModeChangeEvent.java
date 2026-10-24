package com.github.blade.hvac.api.event;

import com.github.blade.hvac.api.ThermostatView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * A system started cooling or heating, or went idle. Main thread.
 *
 * @since 1.0
 */
public final class ThermostatModeChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ThermostatView thermostat;
    private final ThermostatView.Mode from;

    public ThermostatModeChangeEvent(ThermostatView thermostat, ThermostatView.Mode from) {
        this.thermostat = Objects.requireNonNull(thermostat, "thermostat");
        this.from = Objects.requireNonNull(from, "from");
    }

    /** The thermostat, already in its new mode. */
    public ThermostatView getThermostat() { return thermostat; }

    public ThermostatView.Mode getFrom() { return from; }

    public ThermostatView.Mode getTo() { return thermostat.mode(); }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
