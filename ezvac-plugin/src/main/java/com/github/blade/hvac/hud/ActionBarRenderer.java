package com.github.blade.hvac.hud;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** One line above the hotbar, resent every update because the client fades it out. */
public final class ActionBarRenderer implements HudRenderer {
    private final HudFormatter format;

    public ActionBarRenderer(HudFormatter format) {
        this.format = format;
    }

    @Override
    public void render(Player player, ClimateSnapshot snapshot, SectionFrame sections) {
        player.sendActionBar(format.render(format.medium(snapshot).actionBarFormat(), snapshot, sections));
    }

    @Override
    public void clear(Player player) {
        player.sendActionBar(Component.empty());
    }
}
