package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.HvacDevice;
import com.github.blade.hvac.api.SeasonView;
import com.github.blade.hvac.api.event.DeviceAddedEvent;
import com.github.blade.hvac.api.event.DeviceRemovedEvent;
import com.github.blade.hvac.api.event.EquipmentStateChangeEvent;
import com.github.blade.hvac.api.event.SeasonChangeEvent;
import com.github.blade.hvac.api.event.ThermostatModeChangeEvent;
import com.github.blade.hvac.api.event.ThermostatTargetChangeEvent;
import com.github.blade.hvac.model.EquipmentUnit;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.HvacController;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.function.Supplier;

/**
 * Fires the public API's events (2.1). An event is only built when
 * some plugin listens to it, so EZvac costs nothing extra without addons.
 * Main thread.
 */
public final class ApiEvents {
    private ApiEvents() {}

    static boolean listened(HandlerList handlers) {
        return handlers.getRegisteredListeners().length > 0;
    }

    private static void fire(HandlerList handlers, Supplier<? extends Event> event) {
        if (listened(handlers)) Bukkit.getPluginManager().callEvent(event.get());
    }

    public static void modeChanged(Thermostat thermostat, OperatingMode from, HvacController controller) {
        if (thermostat.mode() == from) return;
        fire(ThermostatModeChangeEvent.getHandlerList(), () -> new ThermostatModeChangeEvent(
                ApiViews.thermostat(thermostat, controller.status(thermostat.group())), ApiViews.mode(from)));
    }

    public static void targetChanged(Thermostat thermostat, double previousF, ThermostatTargetChangeEvent.Cause cause,
                                     HvacController controller) {
        if (thermostat.targetF() == previousF) return;
        fire(ThermostatTargetChangeEvent.getHandlerList(), () -> new ThermostatTargetChangeEvent(
                ApiViews.thermostat(thermostat, controller.status(thermostat.group())), previousF, cause));
    }

    public static void equipmentChanged(EquipmentUnit unit, boolean running) {
        fire(EquipmentStateChangeEvent.getHandlerList(), () -> new EquipmentStateChangeEvent(unit.group().label(),
                ApiViews.equipment(unit.type()), ApiViews.position(unit.position()), running));
    }

    public static void seasonChanged(World world, SeasonView.Season from, SeasonView now) {
        fire(SeasonChangeEvent.getHandlerList(), () -> new SeasonChangeEvent(world, from, now));
    }

    public static void deviceAdded(Supplier<HvacDevice> device) {
        fire(DeviceAddedEvent.getHandlerList(), () -> new DeviceAddedEvent(device.get()));
    }

    public static void deviceRemoved(Supplier<HvacDevice> device, DeviceRemovedEvent.Cause cause) {
        fire(DeviceRemovedEvent.getHandlerList(), () -> new DeviceRemovedEvent(device.get(), cause));
    }
}
