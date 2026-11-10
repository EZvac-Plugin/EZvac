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
    /** The share of rain spells that reach a world's dry areas now: its season's (weather.dry-rain-chance). */
    private final java.util.function.ToDoubleFunction<World> dryRainShare;

    public WeatherService(ClimateSettings.Weather settings) {
        this(settings, world -> settings.dryRain().share(null));
    }

    public WeatherService(ClimateSettings.Weather settings, java.util.function.ToDoubleFunction<World> dryRainShare) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.dryRainShare = Objects.requireNonNull(dryRainShare, "dryRainShare");
    }

    /** Called once per controller cycle with the ticks since the last one. */
    public void advance(long elapsedTicks) {
        if (elapsedTicks <= 0) return;
        for (World world : Bukkit.getWorlds()) {
            WeatherModel.Conditions current = worlds.get(world.getUID());
            if (current == null) {
                worlds.put(world.getUID(), settled(world));
                continue;
            }
            // A rain spell rolls once, as it starts, whether it reaches dry areas; it keeps that until it stops.
            boolean raining = world.hasStorm();
            boolean reaches = raining && (current.raining() ? current.reachesDry() : rollsDry(world));
            worlds.put(world.getUID(), current.advance(raining, world.isThundering(), elapsedTicks,
                    settings.easeHours(), reaches));
        }
    }

    public WeatherModel.Conditions conditions(World world) {
        if (world == null) return WeatherModel.Conditions.CLEAR;
        return worlds.computeIfAbsent(world.getUID(), ignored -> settled(world));
    }

    public void forget(World world) {
        if (world != null) worlds.remove(world.getUID());
    }

    private WeatherModel.Conditions settled(World world) {
        return WeatherModel.Conditions.settled(world.hasStorm(), world.isThundering(),
                world.hasStorm() && rollsDry(world));
    }

    /** The roll for the spell that starts (or is first seen) today, by the world's seed and day. */
    private boolean rollsDry(World world) {
        return ClimateSettings.Weather.DryRain.reaches(world.getSeed(), Math.floorDiv(world.getFullTime(), 24_000L),
                dryRainShare.applyAsDouble(world));
    }
}
