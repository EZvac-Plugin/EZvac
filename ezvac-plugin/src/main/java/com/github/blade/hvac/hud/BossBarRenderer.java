package com.github.blade.hvac.hud;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A bar along the top of the screen: fill is the temperature, colour the comfort band. */
public final class BossBarRenderer implements HudRenderer {
    private final HudFormatter format;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public BossBarRenderer(HudFormatter format) {
        this.format = format;
    }

    @Override
    public void render(Player player, ClimateSnapshot snapshot, SectionFrame sections) {
        float progress = (float) format.fraction(snapshot);
        BossBar.Color color = format.bossBarColor(snapshot);
        var name = format.render(format.medium(snapshot).bossBarFormat(), snapshot, sections);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(name, progress, color, BossBar.Overlay.NOTCHED_10);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
            return;
        }
        bar.name(name);
        bar.progress(progress);
        bar.color(color);
    }

    @Override
    public void clear(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }
}
