package com.github.blade.hvac.api;

import com.github.blade.hvac.api.hud.EzvacHud;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * EZvac's public API: what EZvac knows about the climate and its systems,
 * for addons. Get it with {@link #get()} from a plugin that has
 * {@code depend: [EZvac]} (or {@code softdepend}) in its plugin.yml.
 *
 * <p>The API reads only: nothing here changes EZvac's climate or systems.
 * Every value handed out is an immutable snapshot. Since 1.1 addons can also
 * add their own sections to the climate HUD ({@link #hud()}), and since 1.2
 * every device is listed with its location ({@link #devices(World)}).
 *
 * <p>Systems (1.3, EZvac 3.0): devices are linked to a thermostat with
 * EZvac's link tool, and a system has no name of its own. The {@code system}
 * of a view or reading is an opaque key, stable while the system exists: a
 * system upgraded from EZvac 2.x keeps its old group name as its key, a new
 * one is {@code #} and a number. When a headless system (one whose
 * thermostat is gone) is linked to a thermostat, its devices take that
 * thermostat's key. Show players the thermostat's label instead
 * ({@link #thermostatOf}); labels may repeat.
 * Temperatures are always Fahrenheit;
 * {@link #units()} says what the server shows players.
 *
 * <p>Call the methods on the server's main thread, except {@link #latestFor},
 * which is safe from any thread. Events in {@code com.github.blade.hvac.api.event}
 * are fired on the main thread.
 *
 * @since 1.0
 */
public interface EzvacApi {
    /**
     * The API's version. Additions raise the minor number; anything that
     * would break an addon only comes with a new major number.
     */
    String API_VERSION = "1.4";

    /**
     * EZvac's API, from Paper's services manager.
     *
     * @throws IllegalStateException when EZvac isn't installed or enabled
     */
    static EzvacApi get() {
        EzvacApi api = Bukkit.getServicesManager().load(EzvacApi.class);
        if (api == null) throw new IllegalStateException("EZvac is not installed or not enabled; "
                + "add depend: [EZvac] to your plugin.yml");
        return api;
    }

    /** {@link #API_VERSION} of the EZvac that is running. */
    String apiVersion();

    /** The running EZvac's version, such as {@code ALPHA-3.1}. */
    String pluginVersion();

    /** The unit the server shows players: F or C. */
    TemperatureUnit units();

    /** The climate at a spot, exactly as {@code /hvac temperature} would read it there. */
    ClimateReading readingAt(Location location);

    /**
     * The climate a player is in, as their HUD reads it: at head height, or
     * at their feet where only the feet are in a conditioned space, and the
     * water's temperature when they are in water.
     */
    ClimateReading readingFor(Player player);

    /**
     * The reading the last player climate update gave this player (see
     * {@link com.github.blade.hvac.api.event.PlayerClimateUpdateEvent}), or
     * empty when there has been none yet. Safe from any thread.
     */
    Optional<ClimateReading> latestFor(UUID player);

    /** The outdoors at a spot, whatever any system does there. */
    OutdoorClimate outdoorAt(Location location);

    /** Every thermostat on the server. */
    Collection<ThermostatView> thermostats();

    /**
     * The thermostats in one world.
     *
     * @since 1.2
     */
    Collection<ThermostatView> thermostats(World world);

    /**
     * A thermostat by its label (case-insensitive), if one in this world has it.
     *
     * @deprecated since 1.3 labels may repeat, and this returns the one with
     *             the lowest position; use {@link #thermostatAt} or {@link #thermostatOf}
     */
    @Deprecated(since = "1.3")
    Optional<ThermostatView> thermostat(World world, String id);

    /**
     * The thermostat whose sign is at a position.
     *
     * @since 1.3
     */
    Optional<ThermostatView> thermostatAt(Position position);

    /**
     * The thermostat of a system, by the system's key from a view or a
     * reading; empty while the system is headless or gone.
     *
     * @since 1.3
     */
    Optional<ThermostatView> thermostatOf(World world, String system);

    /** Every dehumidifier and humidifier on the server. */
    Collection<ClimateUnitView> climateUnits();

    /**
     * The dehumidifiers and humidifiers in one world.
     *
     * @since 1.2
     */
    Collection<ClimateUnitView> climateUnits(World world);

    /**
     * Every heat pump, air conditioner, furnace, boiler and pool heat pump on the server.
     *
     * @since 1.2
     */
    Collection<EquipmentView> equipment();

    /**
     * The equipment in one world.
     *
     * @since 1.2
     */
    Collection<EquipmentView> equipment(World world);

    /**
     * Every vent on the server, air and water.
     *
     * @since 1.2
     */
    Collection<VentView> vents();

    /**
     * The vents in one world.
     *
     * @since 1.2
     */
    Collection<VentView> vents(World world);

    /**
     * Every thermometer, sensor, sensor hub, RPM monitor and settings panel on the server.
     *
     * @since 1.2
     */
    Collection<InstrumentView> instruments();

    /**
     * The instruments in one world.
     *
     * @since 1.2
     */
    Collection<InstrumentView> instruments(World world);

    /**
     * Every device in one world, of every kind: what a map addon needs to
     * draw them all. Keep it in step with
     * {@link com.github.blade.hvac.api.event.DeviceAddedEvent} and
     * {@link com.github.blade.hvac.api.event.DeviceRemovedEvent}.
     *
     * @since 1.2
     */
    Collection<HvacDevice> devices(World world);

    /** Where a world is in the year, or empty where it has no seasons (no sun, or seasons switched off). */
    Optional<SeasonView> season(World world);

    /**
     * Addon sections on the climate HUD. Check {@link #apiVersion()} first
     * when the addon should also run on API 1.0, which doesn't have it.
     *
     * @since 1.1
     */
    EzvacHud hud();

    /**
     * EZvac's link tool: whether an item is one, and what it has selected.
     * EZdebug+ uses it to show the system being linked.
     *
     * @since 1.3
     */
    EzvacLinkTool linkTool();
}
