package com.github.blade.hvac.api.event;

import com.github.blade.hvac.api.HvacDevice;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * A device was created: with {@code /hvac create}, or a vent with the duct
 * tool. Not fired for the devices hvac.yml brings back at startup; read
 * those from the lists in {@link com.github.blade.hvac.api.EzvacApi}. Main
 * thread.
 *
 * @since 1.2
 */
public final class DeviceAddedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final HvacDevice device;

    public DeviceAddedEvent(HvacDevice device) {
        this.device = Objects.requireNonNull(device, "device");
    }

    /** The new device; match on its view for the details. */
    public HvacDevice getDevice() { return device; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
