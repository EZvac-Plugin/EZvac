package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.SeasonCalendar;
import com.github.blade.hvac.simulation.OutdoorAir;
import com.github.blade.hvac.simulation.Seasons;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Each world's own calendar (2.1), kept in {@code seasons.yml} next
 * to hvac.yml so the device save never changes because of it. Only worlds
 * with a sun get one: the Nether and the End have no seasons. Main thread only.
 */
public final class SeasonService {
    private static final class World {
        final SeasonCalendar calendar;
        String name;
        long fullTime;
        boolean clockSeen;
        double air;
        double water;

        World(SeasonCalendar calendar, String name) {
            this.calendar = calendar;
            this.name = name;
        }
    }

    private final File file;
    private final ClimateSettings.Seasons settings;
    private final Consumer<String> warn;
    private final Map<UUID, World> worlds = new LinkedHashMap<>();
    private boolean dirty;

    /** Told when a world's season turns (2.1: the API's SeasonChangeEvent). */
    public interface SeasonListener {
        void seasonChanged(UUID world, Seasons.Season from);
    }

    private SeasonListener listener;

    public void setListener(SeasonListener listener) { this.listener = listener; }

    /** Runs a change to a world's calendar, and says so when its season turned. */
    private void watching(UUID worldId, World world, Runnable change) {
        Seasons.Season before = world.clockSeen ? season(worldId) : null;
        change.run();
        refresh(world);
        if (before != null && listener != null && settings.enabled()) {
            Seasons.Season after = season(worldId);
            if (after != before) listener.seasonChanged(worldId, before);
        }
    }

    public SeasonService(File dataFolder, ClimateSettings.Seasons settings, Consumer<String> warn) {
        this.file = new File(Objects.requireNonNull(dataFolder, "dataFolder"), "seasons.yml");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    public ClimateSettings.Seasons settings() { return settings; }

    /** Reads seasons.yml. A file that can't be read is kept aside and every world starts on the first day of spring. */
    public void load() {
        worlds.clear();
        if (!file.isFile()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException failure) {
            File aside = new File(file.getParentFile(), "seasons.yml.unreadable");
            try {
                Files.copy(file.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Still start: the calendar is not worth refusing to run over.
            }
            warn.accept("seasons.yml could not be read (" + failure.getMessage() + "), so every world's calendar"
                    + " starts again on the first day of spring. The old file was kept as " + aside.getName() + ".");
            return;
        }
        ConfigurationSection section = yaml.getConfigurationSection("worlds");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            UUID world;
            try {
                world = UUID.fromString(id);
            } catch (IllegalArgumentException invalid) {
                warn.accept("seasons.yml names a world '" + id + "' that is not a world id; it was skipped.");
                continue;
            }
            if (entry == null) continue;
            worlds.put(world, new World(new SeasonCalendar(entry.getLong("day", 0L),
                    entry.getLong("last-world-day", 0L), entry.getBoolean("paused", false)),
                    entry.getString("name", "")));
        }
    }

    /**
     * Looks at a world's clock, once per controller cycle: moves its calendar
     * on when a day has passed and works out where in the seasons it is now.
     */
    public void advance(UUID worldId, String name, long fullTime) {
        World world = worlds.computeIfAbsent(worldId, ignored -> {
            dirty = true;
            return new World(new SeasonCalendar(), name);
        });
        if (!Objects.equals(world.name, name)) {
            world.name = name;
            dirty = true;
        }
        boolean seen = world.clockSeen;
        world.clockSeen = true;
        if (!seen) {
            world.fullTime = fullTime;
            if (world.calendar.advance(fullTime)) dirty = true;
            refresh(world);
            return;
        }
        watching(worldId, world, () -> {
            world.fullTime = fullTime;
            if (world.calendar.advance(fullTime)) dirty = true;
        });
    }

    private void refresh(World world) {
        long ticks = world.calendar.ticks(world.fullTime);
        world.air = Seasons.factor(ticks, settings.daysPerSeason());
        world.water = Seasons.waterFactor(ticks, settings.daysPerSeason(), settings.waterLagDays());
    }

    /** True for a world that has seasons: one this service has seen with a sun. */
    public boolean tracks(UUID worldId) {
        World world = worlds.get(worldId);
        return world != null && world.clockSeen;
    }

    /** +1 in mid-summer, -1 in mid-winter; 0 where there are no seasons or they are switched off. */
    public double airFactor(UUID worldId) {
        World world = worlds.get(worldId);
        return world == null || !world.clockSeen || !settings.enabled() ? 0.0 : world.air;
    }

    /**
     * Pollen through the year as a share of its spring peak (3.2). Where
     * there are no seasons, or they are switched off, it stays at summer's.
     */
    public double pollenFactor(UUID worldId) {
        World world = worlds.get(worldId);
        if (world == null || !world.clockSeen || !settings.enabled()) return OutdoorAir.SUMMER_POLLEN;
        return OutdoorAir.pollenSeasonFactor(world.calendar.ticks(world.fullTime), settings.daysPerSeason());
    }

    /** The same for natural water, a few days behind the air. */
    public double waterFactor(UUID worldId) {
        World world = worlds.get(worldId);
        return world == null || !world.clockSeen || !settings.enabled() ? 0.0 : world.water;
    }

    public Seasons.Season season(UUID worldId) {
        return Seasons.seasonAt(ticks(worldId), settings.daysPerSeason());
    }

    public int dayOfSeason(UUID worldId) {
        return Seasons.dayOfSeason(ticks(worldId), settings.daysPerSeason());
    }

    public boolean paused(UUID worldId) {
        World world = worlds.get(worldId);
        return world != null && world.calendar.paused();
    }

    private long ticks(UUID worldId) {
        World world = worlds.get(worldId);
        return world == null ? 0L : world.calendar.ticks(world.fullTime);
    }

    /** Jumps a world's calendar to this day of this season, this year; the time of day stays the world's. */
    public void set(UUID worldId, Seasons.Season season, int day) {
        World world = require(worldId);
        watching(worldId, world, () -> world.calendar.setDay(
                Seasons.dayOf(season, day, world.calendar.day(), settings.daysPerSeason())));
        dirty = true;
    }

    /** Jumps to the first day of the next season. */
    public void next(UUID worldId) {
        World world = require(worldId);
        watching(worldId, world, () -> world.calendar.setDay(
                Seasons.firstDayOfNext(world.calendar.day(), settings.daysPerSeason())));
        dirty = true;
    }

    /** Stops or restarts the calendar: while paused, the days pass but the season stays. */
    public void setPaused(UUID worldId, boolean paused) {
        World world = require(worldId);
        if (world.calendar.paused() == paused) return;
        world.calendar.setPaused(paused);
        dirty = true;
    }

    private World require(UUID worldId) {
        World world = worlds.get(worldId);
        if (world == null || !world.clockSeen) throw new IllegalArgumentException("That world has no seasons.");
        return world;
    }

    /** Writes seasons.yml when anything changed, atomically, like hvac.yml. */
    public void save() {
        if (!dirty) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(java.util.List.of(
                "EZvac's own calendar for each world (2.1). Use /hvac season to change it;",
                "day 0 is the first day of spring. Edit only while the server is stopped."));
        for (Map.Entry<UUID, World> entry : worlds.entrySet()) {
            String path = "worlds." + entry.getKey();
            World world = entry.getValue();
            yaml.set(path + ".name", world.name);
            yaml.set(path + ".day", world.calendar.day());
            yaml.set(path + ".last-world-day", world.calendar.lastWorldDay());
            yaml.set(path + ".paused", world.calendar.paused());
        }
        File temporary = new File(file.getParentFile(), "seasons.yml.tmp");
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.writeString(temporary.toPath(), yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException failure) {
            warn.accept("Could not save seasons.yml (" + failure.getMessage() + "); it will be tried again.");
        }
    }

    /** Saves when a world unloads; its calendar carries on if the world loads again. */
    public void forget(UUID worldId) {
        save();
        World world = worlds.get(worldId);
        if (world != null) world.clockSeen = false;
    }
}
