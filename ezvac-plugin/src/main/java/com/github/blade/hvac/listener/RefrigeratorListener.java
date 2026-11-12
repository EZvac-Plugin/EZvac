package com.github.blade.hvac.listener;

import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.Refrigerator;
import com.github.blade.hvac.service.ClimateUnitService;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import org.bukkit.block.Barrel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

/**
 * A fridge's or freezer's door (BETA 1.0 Pilot 7): opening its barrel lets
 * the room's air in, and it leaks faster until it is shut again. Its screen
 * says so while it stands open.
 */
public final class RefrigeratorListener implements Listener {
    private final HvacRegistry registry;
    private final ClimateUnitService units;
    private final DisplayService displays;

    public RefrigeratorListener(HvacRegistry registry, ClimateUnitService units, DisplayService displays) {
        this.registry = registry;
        this.units = units;
        this.displays = displays;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Refrigerator refrigerator = of(event.getInventory());
        if (refrigerator == null) return;
        refrigerator.opened(units.airAround(refrigerator), units.climate().refrigeration());
        registry.markDirty();
        displays.refreshClimateUnit(refrigerator, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Refrigerator refrigerator = of(event.getInventory());
        if (refrigerator == null) return;
        refrigerator.closed();
        displays.refreshClimateUnit(refrigerator, true);
    }

    /** The fridge or freezer whose barrel this inventory is, or null. */
    private Refrigerator of(Inventory inventory) {
        if (!(inventory.getHolder(false) instanceof Barrel barrel) || !registry.hasClimateUnits()) return null;
        BlockKey key = BlockKey.of(barrel.getLocation());
        Refrigerator refrigerator = registry.refrigeratorAt(key);
        return refrigerator != null && refrigerator.barrel().equals(key) ? refrigerator : null;
    }
}
