package com.github.blade.hvac.listener;

import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.FridgeFreezer;
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
    private final com.github.blade.hvac.service.FreezerFrost frost;

    public RefrigeratorListener(HvacRegistry registry, ClimateUnitService units, DisplayService displays,
                                com.github.blade.hvac.service.FreezerFrost frost) {
        this.registry = registry;
        this.units = units;
        this.displays = displays;
        this.frost = frost;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Refrigerator refrigerator = of(event.getInventory());
        if (refrigerator == null) return;
        // A fridge-freezer's fridge barrel: a fridge's door, which never frosts.
        if (refrigerator instanceof FridgeFreezer both && isFridge(both, event.getInventory())) {
            both.fridgeOpened(units.airAround(both), units.climate().refrigeration());
            registry.markDirty();
            displays.refreshClimateUnit(both, true);
            return;
        }
        // A mini fridge's walls are in place before anyone sees inside.
        if (refrigerator instanceof com.github.blade.hvac.model.MiniFridge mini) frost.showWalls(mini);
        double letIn = refrigerator.opened(units.airAround(refrigerator), units.climate().refrigeration());
        // A freezer's frost: the air that came in leaves some behind, more when it's humid; seen at once.
        if (refrigerator instanceof com.github.blade.hvac.model.Freezer freezer) {
            freezer.frostFromOpening(units.humidityAround(freezer), letIn, units.climate().refrigeration());
            frost.show(freezer);
        }
        registry.markDirty();
        displays.refreshClimateUnit(refrigerator, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Refrigerator refrigerator = of(event.getInventory());
        if (refrigerator == null) return;
        if (refrigerator instanceof FridgeFreezer both && isFridge(both, event.getInventory())) both.fridgeClosed();
        else refrigerator.closed();
        displays.refreshClimateUnit(refrigerator, true);
    }

    /** The fridge or freezer whose barrel this inventory is, or null. */
    private Refrigerator of(Inventory inventory) {
        if (!(inventory.getHolder(false) instanceof Barrel barrel) || !registry.hasClimateUnits()) return null;
        BlockKey key = BlockKey.of(barrel.getLocation());
        Refrigerator refrigerator = registry.refrigeratorAt(key);
        return refrigerator != null && refrigerator.keepsCold(key) ? refrigerator : null;
    }

    private static boolean isFridge(FridgeFreezer both, Inventory inventory) {
        return inventory.getHolder(false) instanceof Barrel barrel
                && both.fridgeBarrel().equals(BlockKey.of(barrel.getLocation()));
    }
}
