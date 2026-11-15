package com.github.blade.hvac.api.control;

import com.github.blade.hvac.api.HvacDevice;

import java.util.Objects;
import java.util.Optional;

/**
 * What came of a control call (API 1.12).
 *
 * @param status  what happened
 * @param message a short sentence saying so, fit to show a player
 * @param device  the device as it is now, when there is one: what a web panel shows next
 * @since 1.12
 */
public record ControlResult(Status status, String message, Optional<HvacDevice> device) {
    public ControlResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
        device = device == null ? Optional.empty() : device;
    }

    /** What happened. New statuses may come in later versions: a switch needs a default branch. */
    public enum Status {
        /** Done, as asked. */
        DONE,
        /** Done, but kept within the device's range or rounded to its presets: the device says to what. */
        ADJUSTED,
        /** It already was so: nothing changed. */
        UNCHANGED,
        /** No device there, or EZvac is stopping. */
        NOT_FOUND,
        /** The device there can't do this (a humidifier has no fan; a fridge doesn't defrost). */
        NOT_SUPPORTED,
        /** A plugin cancelled the {@link com.github.blade.hvac.api.event.DeviceControlEvent}. */
        CANCELLED
    }

    /** True when the device is now as asked (or as near as its range allows), changed or not. */
    public boolean ok() {
        return status == Status.DONE || status == Status.ADJUSTED || status == Status.UNCHANGED;
    }
}
