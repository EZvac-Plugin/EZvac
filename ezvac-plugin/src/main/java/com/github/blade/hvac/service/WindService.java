package com.github.blade.hvac.service;

import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.simulation.Seasons;
import com.github.blade.hvac.simulation.WindModel;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Each world's wind (3.2). The day's wind comes from the world seed and the
 * day number, so nothing is saved: it is worked out again after a restart and
 * comes out the same. The day is the season calendar's while it runs, so
 * {@code /hvac season set} brings that day's wind at once and setting the
 * clock back never replays an old day's; without seasons, or with the
 * calendar paused, it is the world's own day count. Only worlds with a sky
 * have wind. An admin can set today's level with {@code /hvac wind set}; that
 * holds until the day ends and is never saved.
 */
public final class WindService {
    private final WindSettings settings;
    /** Null outside a running server (tests): the wind then has no seasons. */
    private final SeasonService seasons;

    private record Today(long day, WindModel.Day today, WindModel.Day yesterday) {}
    private record Held(long day, WindModel.Level level) {}
    private record Speed(long fullTime, long day, double speedMs) {}

    private final Map<UUID, Today> days = new HashMap<>();
    private final Map<UUID, Held> held = new HashMap<>();
    /** The last speed worked out per world: many readings ask in the same tick. */
    private final Map<UUID, Speed> speeds = new HashMap<>();

    public WindService(WindSettings settings, SeasonService seasons) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.seasons = seasons;
    }

    public WindSettings settings() { return settings; }
    public boolean enabled() { return settings.enabled(); }

    /** Whether the wind blows in this world at all: only under a sky. */
    public static boolean hasWind(World world) {
        return world != null && world.getEnvironment() == World.Environment.NORMAL;
    }

    /** The wind on open, level ground in this world right now, in m/s. */
    public double speedMs(World world) {
        if (!enabled() || !hasWind(world)) return 0.0;
        long now = world.getFullTime();
        long day = dayOf(world);
        Speed last = speeds.get(world.getUID());
        if (last != null && last.fullTime() == now && last.day() == day) return last.speedMs();
        Today today = today(world);
        double speed = WindModel.speedMs(world.getSeed(), now, today.today(), today.yesterday(), settings);
        speeds.put(world.getUID(), new Speed(now, day, speed));
        return speed;
    }

    /** Today's wind in this world. */
    public WindModel.Day day(World world) {
        return today(world).today();
    }

    /**
     * Today and the days after it, {@code count} in all. A level set for
     * today shows for today only.
     */
    public List<WindModel.Day> forecast(World world, int count) {
        List<WindModel.Day> forecast = new ArrayList<>();
        forecast.add(day(world));
        long today = dayOf(world);
        for (int ahead = 1; ahead < count; ahead++) forecast.add(drawn(world, today + ahead, ahead));
        return forecast;
    }

    /** Holds today's wind at a level until the day ends; null lets it blow as it would. */
    public void hold(World world, WindModel.Level level) {
        UUID id = world.getUID();
        if (level == null) held.remove(id);
        else held.put(id, new Held(dayOf(world), level));
        days.remove(id);
        speeds.remove(id);
    }

    /** True while an admin's level holds today's wind. */
    public boolean held(World world) {
        Held level = held.get(world.getUID());
        return level != null && level.day() == dayOf(world);
    }

    /** Drops what is kept for a world, when it unloads. */
    public void forget(World world) {
        UUID id = world.getUID();
        days.remove(id);
        held.remove(id);
        speeds.remove(id);
    }

    /** The day the wind is drawn for: the season calendar's while it runs, else the world's own. */
    long dayOf(World world) {
        long calendar = seasons == null ? -1L : seasons.runningDay(world.getUID());
        return calendar >= 0L ? calendar : WindModel.dayOf(world.getFullTime());
    }

    private Today today(World world) {
        UUID id = world.getUID();
        // Keyed by the day, so a new season or day from /hvac season set redraws it at once.
        long day = dayOf(world);
        Today cached = days.get(id);
        if (cached != null && cached.day() == day) return cached;
        Held level = held.get(id);
        if (level != null && level.day() != day) {
            held.remove(id);
            level = null;
        }
        WindModel.Day today = level != null ? WindModel.day(world.getSeed(), day, level.level(), settings)
                : drawn(world, day, 0);
        // Yesterday as it was drawn, for the dawn ease: a level held then has run out. A level held
        // now takes hold at once, with no ease from yesterday's wind.
        Today computed = new Today(day, today, level != null ? null : drawn(world, day - 1, -1));
        days.put(id, computed);
        return computed;
    }

    private WindModel.Day drawn(World world, long day, int daysFromNow) {
        return WindModel.day(world.getSeed(), day, windiness(world, daysFromNow), settings);
    }

    /** The season's windiness that many days from now; 1 without seasons. */
    private double windiness(World world, int daysFromNow) {
        if (seasons == null) return 1.0;
        Seasons.Season season = seasons.seasonIn(world.getUID(), daysFromNow);
        return season == null ? 1.0 : settings.seasons().of(season);
    }
}
