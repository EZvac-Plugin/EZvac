package com.github.blade.hvac.hud;

import org.bukkit.entity.Player;

/** Draws the climate HUD for one player. Main thread only. */
public interface HudRenderer {
    void render(Player player, ClimateSnapshot snapshot);

    /** Removes anything this renderer shows the player. Safe to call when nothing is shown. */
    void clear(Player player);
}
