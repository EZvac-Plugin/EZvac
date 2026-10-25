package com.github.blade.hvac.api.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import java.util.List;

/**
 * An addon's section of the climate HUD: see {@link EzvacHud}.
 *
 * <p>EZvac calls it on the main thread every HUD update ({@code hud.update-ticks},
 * twice a second by default) for each player who has the HUD on, so keep it
 * cheap: draw from state the addon already has. An exception is logged once
 * and that player's section is left out of that update.
 *
 * @since 1.1
 */
@FunctionalInterface
public interface HudSection {
    /**
     * The section's lines for one player, top to bottom. An empty list hides
     * the section for this player. The sidebar shows at most 15 lines in all,
     * so a section should keep to two or three.
     */
    List<Component> lines(HudContext context);

    /**
     * The section on one line, for the action bar and the boss bar. By
     * default its lines joined with spaces; empty to hide it.
     */
    default Component inline(HudContext context) {
        List<Component> lines = lines(context);
        if (lines.isEmpty()) return Component.empty();
        return Component.join(JoinConfiguration.separator(Component.space()), lines);
    }
}
