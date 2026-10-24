package com.github.blade.hvac.api.event;

import com.github.blade.hvac.api.ClimateReading;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;

/**
 * A player's climate, read again: fired for every online player on the
 * HUD's cadence ({@code hud.update-ticks}, half a second by default), but
 * only while some plugin listens. The reading is the one the HUD shows.
 * Main thread.
 *
 * @since 1.0
 */
public final class PlayerClimateUpdateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ClimateReading previous;
    private final ClimateReading current;

    public PlayerClimateUpdateEvent(Player player, ClimateReading previous, ClimateReading current) {
        this.player = Objects.requireNonNull(player, "player");
        this.previous = previous;
        this.current = Objects.requireNonNull(current, "current");
    }

    public Player getPlayer() { return player; }

    /** The reading before this one, empty for the first since the player joined. */
    public Optional<ClimateReading> getPrevious() { return Optional.ofNullable(previous); }

    public ClimateReading getReading() { return current; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
