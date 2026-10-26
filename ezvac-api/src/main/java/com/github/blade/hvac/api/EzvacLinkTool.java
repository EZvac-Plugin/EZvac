package com.github.blade.hvac.api;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * EZvac's link tool, the stick admins link devices with (EZvac 3.0). It
 * also recognises the duct and sensor tools it replaced.
 *
 * @since 1.3
 */
public interface EzvacLinkTool {
    /** True when the item is a link tool, or a duct or sensor tool from before EZvac 3.0. */
    boolean isLinkTool(ItemStack item);

    /** What the tool has selected, or empty when nothing (or the item is no link tool). */
    Optional<LinkSelection> selection(ItemStack item);

    /**
     * A link tool's selection: a thermostat, whose system devices are being
     * linked to, or a sensor hub, whose sensors are.
     *
     * @param system     the selected thermostat's system key, empty when a sensor hub is selected
     * @param thermostat the selected thermostat's sign, empty when a sensor hub is selected
     * @param sensorHub  the selected sensor hub's sign, empty when a thermostat is selected
     */
    record LinkSelection(Optional<String> system, Optional<Position> thermostat, Optional<Position> sensorHub) {
        public LinkSelection {
            system = system == null ? Optional.empty() : system;
            thermostat = thermostat == null ? Optional.empty() : thermostat;
            sensorHub = sensorHub == null ? Optional.empty() : sensorHub;
        }
    }
}
