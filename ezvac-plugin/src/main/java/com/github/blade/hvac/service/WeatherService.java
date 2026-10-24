package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.simulation.WeatherModel;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Eased weather per world. Main thread only. Nothing here is saved: on
 * startup each world simply begins at whatever its weather is now.
 */
public final class WeatherService {
    private final ClimateSettings.Weather settings;
    private final Map<UUID, WeatherModel.Conditions> worlds = new HashMap<>();

    public WeatherService(ClimateSettings.Weather settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Called once per controller cycle with the ticks since the last one. */
    public void advance(long elapsedTicks) {
        if (elapsedTicks <= 0) return;
        for (World world : Bukkit.getWorlds()) {
            WeatherModel.Conditions current = worlds.get(world.getUID());
            worlds.put(world.getUID(), current == null ? settled(world)
                    : current.advance(world.hasStorm(), world.isThundering(), elapsedTicks, settings.easeHours()));
        }
    }

    public WeatherModel.Conditions conditions(World world) {
        if (world == null) return WeatherModel.Conditions.CLEAR;
        return worlds.computeIfAbsent(world.getUID(), ignored -> settled(world));
    }

    public void forget(World world) {
        if (world != null) worlds.remove(world.getUID());
    }

    private static WeatherModel.Conditions settled(World world) {
        return WeatherModel.Conditions.settled(world.hasStorm(), world.isThundering());
    }
}
