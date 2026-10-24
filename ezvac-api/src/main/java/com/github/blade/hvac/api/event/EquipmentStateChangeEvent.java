package com.github.blade.hvac.api.event;

import com.github.blade.hvac.api.Position;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * A piece of equipment started or stopped. Main thread.
 *
 * @since 1.0
 */
public final class EquipmentStateChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    /** The kinds of equipment. */
    public enum Kind { HEAT_PUMP, AIR_CONDITIONER, FURNACE, BOILER, POOL_HEAT_PUMP }

    private final String system;
    private final Kind kind;
    private final Position position;
    private final boolean running;

    public EquipmentStateChangeEvent(String system, Kind kind, Position position, boolean running) {
        this.system = Objects.requireNonNull(system, "system");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.position = Objects.requireNonNull(position, "position");
        this.running = running;
    }

    /** The system (group) it belongs to. */
    public String getSystem() { return system; }

    public Kind getKind() { return kind; }

    /** Its dispenser. */
    public Position getPosition() { return position; }

    /** True when it started, false when it stopped. */
    public boolean isRunning() { return running; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
