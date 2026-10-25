package com.github.blade.hvac.api.event;

import com.github.blade.hvac.api.HvacDevice;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * A device is gone. Main thread.
 *
 * @since 1.2
 */
public final class DeviceRemovedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    /** How it went. */
    public enum Cause {
        /** Its block was broken: by a player, an explosion, fire or a piston. */
        BROKEN,
        /** With {@code /hvac remove}. */
        COMMAND,
        /** Its block was found missing, gone without any event EZvac saw, such as a sensor's wall broken. */
        MISSING
    }

    private final HvacDevice device;
    private final Cause cause;

    public DeviceRemovedEvent(HvacDevice device, Cause cause) {
        this.device = Objects.requireNonNull(device, "device");
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    /** The device as it was just before it went. */
    public HvacDevice getDevice() { return device; }

    public Cause getCause() { return cause; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
