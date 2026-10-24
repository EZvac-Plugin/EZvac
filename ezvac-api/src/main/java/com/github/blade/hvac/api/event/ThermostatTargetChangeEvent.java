package com.github.blade.hvac.api.event;

import com.github.blade.hvac.api.ThermostatView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * A thermostat's target changed. Main thread.
 *
 * @since 1.0
 */
public final class ThermostatTargetChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    /** How the target was changed. */
    public enum Cause {
        /** From the control menu its sign opens. */
        MENU,
        /** With {@code /hvac thermostat <id> set}. */
        COMMAND
    }

    private final ThermostatView thermostat;
    private final double previousF;
    private final Cause cause;

    public ThermostatTargetChangeEvent(ThermostatView thermostat, double previousF, Cause cause) {
        this.thermostat = Objects.requireNonNull(thermostat, "thermostat");
        this.previousF = previousF;
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    /** The thermostat, already with its new target. */
    public ThermostatView getThermostat() { return thermostat; }

    public double getPreviousTargetF() { return previousF; }

    public double getTargetF() { return thermostat.targetF(); }

    public Cause getCause() { return cause; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
