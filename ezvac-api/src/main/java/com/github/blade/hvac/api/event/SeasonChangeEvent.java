package com.github.blade.hvac.api.event;

import com.github.blade.hvac.api.SeasonView;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * A world's season turned, because the days passed or an admin set it.
 * Main thread.
 *
 * @since 1.0
 */
public final class SeasonChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final SeasonView.Season from;
    private final SeasonView season;

    public SeasonChangeEvent(World world, SeasonView.Season from, SeasonView season) {
        this.world = Objects.requireNonNull(world, "world");
        this.from = Objects.requireNonNull(from, "from");
        this.season = Objects.requireNonNull(season, "season");
    }

    public World getWorld() { return world; }

    public SeasonView.Season getFrom() { return from; }

    /** Where the world is now in the calendar. */
    public SeasonView getSeason() { return season; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
