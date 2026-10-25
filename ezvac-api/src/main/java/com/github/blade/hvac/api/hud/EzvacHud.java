package com.github.blade.hvac.api.hud;

import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Addon sections on EZvac's climate HUD. Get it with
 * {@link com.github.blade.hvac.api.EzvacApi#hud()}.
 *
 * <p>A section is a few lines of the addon's own, drawn with EZvac's HUD for
 * every player who has it switched on. On the sidebar a section goes where
 * the server's {@code hud.sidebar.lines} has {@code {addons}} (every section
 * in registration order), or {@code {section:<id>}} (that section alone).
 * The one-line HUDs, the action bar and the boss bar, show a section only
 * where their format has {@code {section:<id>}}, using
 * {@link HudSection#inline}.
 *
 * <p>Call these on the main thread. A section is removed by itself when the
 * plugin that registered it is disabled.
 *
 * @since 1.1
 */
public interface EzvacHud {
    /**
     * Adds a section, or replaces this plugin's section with the same key.
     *
     * @param owner   the plugin registering it
     * @param key     a short name, unique within the plugin: lowercase letters, digits, {@code _ - .}
     * @param section draws the section for one player at a time
     * @return the section's id, {@code <plugin name>:<key>} in lowercase, such as {@code liveexposure:body}
     * @throws IllegalArgumentException when the key is empty or has other characters
     */
    String register(Plugin owner, String key, HudSection section);

    /** Removes a section; nothing happens when there is none. */
    void unregister(Plugin owner, String key);

    /** The ids of every registered section, in registration order. */
    List<String> sections();
}
