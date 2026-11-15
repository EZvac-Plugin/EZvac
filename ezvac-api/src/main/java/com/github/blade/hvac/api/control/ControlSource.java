package com.github.blade.hvac.api.control;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Who changed a device (API 1.12): a plugin through {@link EzvacControl}, or a
 * player at a device's screen.
 *
 * @param origin how
 * @param plugin the plugin's name, for {@link Origin#API}; empty otherwise
 * @param player the player, when known: the one a plugin acted for, or the one who pressed the button
 * @since 1.12
 */
public record ControlSource(Origin origin, Optional<String> plugin, Optional<UUID> player) {
    public ControlSource {
        Objects.requireNonNull(origin, "origin");
        plugin = plugin == null ? Optional.empty() : plugin;
        player = player == null ? Optional.empty() : player;
    }

    /** How a change was made. New origins may come in later versions. */
    public enum Origin {
        /** By a plugin, through {@link EzvacControl}. */
        API,
        /** By a player pressing a button on a device's screen. */
        SCREEN
    }
}
