package com.github.blade.hvac.hud;

import com.github.blade.hvac.service.AirflowService;
import org.bukkit.entity.Player;

import java.util.List;

/** Where the HUD gets addon sections from (API 1.1). Main thread only. */
public interface SectionSource {
    /** True when any section is registered; the HUD skips the work otherwise. */
    boolean any();

    /** Every registered section's id, in registration order. */
    List<String> ids();

    /** The sections for one player's update, drawn lazily as the layout asks for them. */
    SectionFrame frame(Player player, AirflowService.TemperatureReading reading);
}
