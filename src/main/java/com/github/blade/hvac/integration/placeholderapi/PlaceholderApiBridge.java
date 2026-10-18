package com.github.blade.hvac.integration.placeholderapi;

import com.github.blade.hvac.hud.ClimateHudService;
import org.bukkit.plugin.Plugin;

/**
 * The only class EZvac calls into for PlaceholderAPI. HvacPlugin calls it
 * only after confirming PlaceholderAPI is enabled, so the JVM never has to
 * resolve PlaceholderAPI's classes on a server without it.
 */
public final class PlaceholderApiBridge {
    private final EzvacExpansion expansion;

    private PlaceholderApiBridge(EzvacExpansion expansion) {
        this.expansion = expansion;
    }

    /** Registers {@code %ezvac_*%}; returns null if PlaceholderAPI refused the registration. */
    public static PlaceholderApiBridge register(Plugin plugin, ClimateHudService hud) {
        EzvacExpansion expansion = new EzvacExpansion(plugin, hud);
        return expansion.register() ? new PlaceholderApiBridge(expansion) : null;
    }

    public void unregister() {
        expansion.unregister();
    }
}
