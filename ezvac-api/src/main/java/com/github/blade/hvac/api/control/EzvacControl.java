package com.github.blade.hvac.api.control;

import com.github.blade.hvac.api.ClimateUnitView;
import com.github.blade.hvac.api.Position;
import com.github.blade.hvac.api.ThermostatView;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Controlling EZvac's devices from another plugin (API 1.12, EZvac BETA 1.0
 * Pilot 7): what a player could do at a device's sign or screen, done from a
 * web panel, a Discord bot, an automation, or anything else.
 *
 * <p><b>EZvac checks nothing.</b> It does whatever a controller asks, on any
 * device: deciding who may control what, and logging people in, is the
 * calling plugin's job. A controller acting for a player ({@link #as(UUID)})
 * only records who asked, for {@link com.github.blade.hvac.api.event.DeviceControlEvent}
 * and {@code /hvac info}.
 *
 * <p><b>Any thread.</b> Every call returns at once with a future; the change
 * itself is made on the server's main thread (straight away when the caller is
 * already on it) and the future completes there. A web server's threads can
 * call it directly. {@link #read(Supplier)} does the same for reading views.
 *
 * <p>A device is named by its {@link Position}: the block its view gives (a
 * sign, a button, a dispenser), or any other block of it, such as a fridge's
 * barrel. Temperatures are in Fahrenheit, as everywhere in the API.
 *
 * <pre>{@code
 * EzvacControl control = EzvacApi.get().control(myPlugin).as(player.getUniqueId());
 * control.setTarget(thermostat.position(), 70.0).thenAccept(result -> {
 *     if (result.ok()) web.send(result.device().orElseThrow());
 *     else web.error(result.message());
 * });
 * }</pre>
 *
 * @since 1.12
 */
public interface EzvacControl {
    /** The plugin this controller belongs to. */
    Plugin plugin();

    /** The player it acts for, if any. */
    Optional<UUID> player();

    /** A controller for the same plugin acting for this player: recorded, never checked. */
    EzvacControl as(UUID player);

    // ---- Thermostats ----

    /** A thermostat's target, in F; kept within its range (a pool's is wider), which the result says. */
    CompletableFuture<ControlResult> setTarget(Position thermostat, double targetF);

    /** Switches a thermostat, and so its whole system, on or off. */
    CompletableFuture<ControlResult> setEnabled(Position thermostat, boolean enabled);

    /** The profile of the system a thermostat (or a settings panel) belongs to. */
    CompletableFuture<ControlResult> setProfile(Position thermostatOrPanel, ThermostatView.Profile profile);

    // ---- Dehumidifiers, humidifiers, air purifiers, fridges and freezers ----

    /** Switches a climate unit (a dehumidifier, humidifier, air purifier or refrigeration appliance) on or off. */
    CompletableFuture<ControlResult> setPowered(Position unit, boolean powered);

    /**
     * A climate unit's setpoint: a dehumidifier's or humidifier's in % RH (to
     * the nearest of its presets); a fridge's, mini fridge's or deep freezer's
     * in F; a refrigerator's freezer's in F (its fridge: {@link #setFridgeSetpoint}).
     */
    CompletableFuture<ControlResult> setSetpoint(Position unit, int setpoint);

    /** A refrigerator's fridge compartment's setpoint, in F. */
    CompletableFuture<ControlResult> setFridgeSetpoint(Position refrigerator, int setpointF);

    /** An air purifier's fan: a speed, or Auto. */
    CompletableFuture<ControlResult> setFan(Position purifier, ClimateUnitView.Fan fan);

    /** Starts a defrost in a deep freezer or a refrigerator's freezer; it works switched off too. */
    CompletableFuture<ControlResult> startDefrost(Position freezer);

    /** Whether a refrigerator's freezer defrosts itself (a deep freezer never does). */
    CompletableFuture<ControlResult> setAutoDefrost(Position refrigerator, boolean on);

    // ---- Equipment ----

    /** Switches a heat pump, AC, furnace, boiler or pool heat pump on or off, as its menu does. */
    CompletableFuture<ControlResult> setEquipmentEnabled(Position equipment, boolean enabled);

    // ---- Reading safely from any thread ----

    /**
     * Runs a read on the main thread and hands back what it returns: the API's
     * views are only consistent there.
     *
     * <pre>{@code control.read(() -> api.thermostats()).thenAccept(web::show);}</pre>
     */
    <T> CompletableFuture<T> read(Supplier<T> read);
}
