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
 *
 * <p>BETA 1.0 Pilot 3: the wind has a direction and differs from region to
 * region. A spot's direction comes from its climate belts, read across the map
 * by {@link RegionClimate}, and turns with rain passing through, read from the
 * world's weather clocks. When it last started and stopped raining is kept in
 * memory only, so after a restart a front simply begins again.
 */
public final class WindService {
    private final WindSettings settings;
    /** Null outside a running server (tests): the wind then has no seasons. */
    private final SeasonService seasons;

    /** Null outside a running server (tests): everywhere is then in the westerlies. */
    private final RegionClimate regions;

    private record Today(long day, WindModel.Day today, WindModel.Day yesterday, WindModel.Level floor) {}

    private PlannedWeather planned = PlannedWeather.none();

    /** An addon's weather plan (BETA 1.0 Pilot 4): a planned storm raises the day's wind. */
    public void setPlannedWeather(PlannedWeather planned) {
        this.planned = Objects.requireNonNull(planned, "planned");
        days.clear();
    }

    /** The least wind a planned storm brings now: a snowstorm a gale, a thunderstorm windy. */
    private WindModel.Level floor(World world) {
        return planned.eventNow(world).map(event -> switch (event) {
            case SNOWSTORM -> WindModel.Level.GALE;
            case THUNDERSTORM -> WindModel.Level.WINDY;
            default -> null;
        }).orElse(null);
    }
    /** A level an admin set for the day, and the direction, NaN to leave it as it would blow. */
    private record Held(long day, WindModel.Level level, double fromDegrees) {}
    private record Speed(long fullTime, long day, double speedMs) {}
    /** When it was last seen to start and stop raining, in game ticks; -1 when not seen. */
    private static final class Rain {
        boolean raining;
        boolean seen;
        long started = -1L;
        long ended = -1L;
        long checked = Long.MIN_VALUE;
        WindModel.Front front = WindModel.Front.NONE;
    }

    private final Map<UUID, Today> days = new HashMap<>();
    private final Map<UUID, Held> held = new HashMap<>();
    /** The last speed worked out per world: many readings ask in the same tick. */
    private final Map<UUID, Speed> speeds = new HashMap<>();
    private final Map<UUID, Rain> rain = new HashMap<>();

    public WindService(WindSettings settings, SeasonService seasons) {
        this(settings, seasons, null);
    }

    public WindService(WindSettings settings, SeasonService seasons, RegionClimate regions) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.seasons = seasons;
        this.regions = regions;
    }

    public WindSettings settings() { return settings; }

    /** Null outside a running server. */
    public RegionClimate regions() { return regions; }
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
     * The wind on open, level ground at a spot right now (BETA 1.0 Pilot 3):
     * its region's, from its climate belts' direction. No speed and no
     * direction (NaN) where there is no wind.
     */
    public WindModel.Spot at(World world, double x, double z) {
        if (!enabled() || !hasWind(world)) return new WindModel.Spot(0.0, Double.NaN);
        Today today = today(world);
        return WindModel.at(world.getSeed(), world.getFullTime(), x, z, today.today(), today.yesterday(),
                belts(world, x, z), front(world), settings);
    }

    /** Today and the days after it at a spot's region, {@code count} in all (BETA 1.0 Pilot 3). */
    public List<WindModel.Day> forecastAt(World world, double x, double z, int count) {
        WindModel.Belts belts = belts(world, x, z);
        return forecast(world, count).stream()
                .map(day -> WindModel.dayAt(world.getSeed(), day, x, z, belts, settings)).toList();
    }

    /** How much a spot lies in each climate belt. */
    public WindModel.Belts belts(World world, double x, double z) {
        return regions == null ? WindModel.Belts.WESTERLIES : regions.belts(world, x, z);
    }

    /** The rain passing through a world as the wind feels it, from its weather clocks. */
    public WindModel.Front front(World world) {
        Rain seen = rain.computeIfAbsent(world.getUID(), ignored -> new Rain());
        long now = world.getGameTime();
        if (seen.checked == now) return seen.front;
        boolean raining = world.hasStorm();
        if (seen.seen && raining != seen.raining) {
            if (raining) seen.started = now;
            else seen.ended = now;
        }
        seen.seen = true;
        seen.raining = raining;
        seen.checked = now;
        seen.front = WindModel.front(raining, world.getWeatherDuration(), world.getClearWeatherDuration(),
                seen.started < 0 ? -1L : now - seen.started, seen.ended < 0 ? -1L : now - seen.ended);
        return seen.front;
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
        hold(world, level, Double.NaN);
    }

    /**
     * Holds today's wind at a level, and from a direction unless that is NaN
     * (BETA 1.0 Pilot 3), until the day ends; a null level lets it blow as it would.
     */
    public void hold(World world, WindModel.Level level, double fromDegrees) {
        UUID id = world.getUID();
        if (level == null) held.remove(id);
        else held.put(id, new Held(dayOf(world), level, fromDegrees));
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
        rain.remove(id);
        if (regions != null) regions.forget(world);
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
        WindModel.Level floor = floor(world);
        Today cached = days.get(id);
        if (cached != null && cached.day() == day && cached.floor() == floor) return cached;
        Held level = held.get(id);
        if (level != null && level.day() != day) {
            held.remove(id);
            level = null;
        }
        WindModel.Day today = level != null ? WindModel.day(world.getSeed(), day, level.level(), settings).holding()
                : drawn(world, day, 0);
        if (level != null && Double.isFinite(level.fromDegrees())) today = today.from(level.fromDegrees());
        // A planned storm (API 1.9) blows at least so hard, unless an admin set the day.
        if (level == null && floor != null && today.level().ordinal() < floor.ordinal())
            today = WindModel.day(world.getSeed(), day, floor, settings);
        // Yesterday as it was drawn, for the dawn ease: a level held then has run out. A level held
        // now takes hold at once, with no ease from yesterday's wind.
        Today computed = new Today(day, today, level != null ? null : drawn(world, day - 1, -1), floor);
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
