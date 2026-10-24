package com.github.blade.hvac.integration.placeholderapi;

import com.github.blade.hvac.hud.ClimateHudService;
import com.github.blade.hvac.hud.PlaceholderResolver;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/**
 * {@code %ezvac_<key>%}. PlaceholderAPI may call {@link #onRequest} from any
 * thread, so it only ever reads the player's latest immutable snapshot.
 */
final class EzvacExpansion extends PlaceholderExpansion {
    private final Plugin plugin;
    private final ClimateHudService hud;
    private final PlaceholderResolver resolver;

    EzvacExpansion(Plugin plugin, ClimateHudService hud) {
        this.plugin = plugin;
        this.hud = hud;
        this.resolver = new PlaceholderResolver(hud.format());
    }

    @Override public String getIdentifier() { return "ezvac"; }
    @Override public String getAuthor() { return "blade"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    /** Owned by EZvac, so it must survive /papi reload. */
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return hud.format().settings().noneText();
        return resolver.resolve(hud.snapshot(player.getUniqueId()), params);
    }
}
