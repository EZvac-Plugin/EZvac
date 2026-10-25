package com.github.blade.hvac.hud;

import org.bukkit.entity.Player;

/** Draws the climate HUD for one player. Main thread only. */
public interface HudRenderer {
    /** @param sections the addon sections for this update (API 1.1), drawn only where the layout places them */
    void render(Player player, ClimateSnapshot snapshot, SectionFrame sections);

    /** Removes anything this renderer shows the player. Safe to call when nothing is shown. */
    void clear(Player player);
}
