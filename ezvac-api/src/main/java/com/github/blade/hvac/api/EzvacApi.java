package com.github.blade.hvac.api;

import java.util.List;
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
    String API_VERSION = "1.9";

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
     * A world's wind for today and the days after it, {@code days} in all,
     * as {@code /hvac wind} shows it. Empty where there is no wind: outside
     * the overworld, or with wind switched off.
     *
     * @since 1.5
     */
    default List<Wind.Day> windForecast(World world, int days) {
        return List.of();
    }

    /**
     * A region's wind for today and the days after it, {@code days} in all:
     * the wind around {@code location}, which can be up to a level windier or
     * calmer than the world's, with where it blows from there. Empty where
     * there is no wind.
     *
     * @since 1.8
     */
    default List<Wind.Day> windForecast(Location location, int days) {
        return location == null ? List.of() : windForecast(location.getWorld(), days);
    }

    /**
     * The wind on open, level ground at a spot right now, with where it blows
     * from: as a weather vane in a field there would read it, before terrain
     * and buildings. Works anywhere, explored or not, and never loads a
     * chunk, so a map can draw it everywhere. Calm where there is no wind.
     * Main thread only.
     *
     * @since 1.8
     */
    default Wind windAt(World world, int x, int z) {
        return Wind.CALM;
    }

    /**
     * The outdoor air in the open at a spot right now: its biomes' air with
     * the season's pollen, the rain and the wind, as a weather station there
     * would read it; no indoor smoke, but since EZvac BETA 1.0 Pilot 4 the
     * smoke of fires burning in the open nearby, carried downwind. Works
     * anywhere, explored or not, and never loads a chunk. Empty with air
     * quality switched off or outside the overworld. Main thread only.
     *
     * @since 1.8
     */
    default Optional<AirQuality> outdoorAirAt(World world, int x, int z) {
        return Optional.empty();
    }

    /**
     * What is falling at a block right now: nothing while the world isn't
     * raining or where the rain doesn't reach, otherwise rain or snow by
     * EZvac's air at that block. An addon can show it in the world, as
     * EZclimates does. Never loads a chunk. Main thread only.
     *
     * @since 1.9
     */
    default Precipitation precipitationAt(World world, int x, int y, int z) {
        return Precipitation.NONE;
    }

    /**
     * The height above which rain would come down as snow right now at a
     * column, out in the open, whether or not it is raining: EZvac's air
     * cools with height, so above it snow would fall and below it rain.
     * {@link Double#NEGATIVE_INFINITY} where it would snow at every height,
     * {@link Double#POSITIVE_INFINITY} where it would rain at every height,
     * and NaN where rain doesn't reach now (a dry place outside a
     * thunderstorm, by default). An addon can have the right look ready
     * before the rain starts. One call answers for a whole chunk's column.
     * Never loads a chunk. Main thread only.
     *
     * @since 1.9
     */
    default double snowLineAt(World world, int x, int z) {
        return Double.NaN;
    }

    /**
     * How cold natural water (a lake, a river, the sea, an unheated pool) is
     * getting at a block: its temperature before EZvac holds liquid water at
     * 33 F, so at or below 32 F a lake would be freezing over. It lags the
     * air's seasons. {@link OutdoorClimate#waterF()} is the same held at
     * liquid water's range. Never loads a chunk. Main thread only.
     *
     * @since 1.9
     */
    default double freezingWaterF(World world, int x, int y, int z) {
        return Double.NaN;
    }

    /**
     * Lets an addon plan the weather (see {@link WeatherPlan}): stations
     * forecast from it, and the outdoor air takes on its heat waves, cold
     * snaps and storms. One plan at a time; registering again replaces it,
     * and it is dropped when its plugin disables. Null removes the plugin's
     * own plan.
     *
     * @since 1.9
     */
    default void setWeatherPlan(org.bukkit.plugin.Plugin plugin, WeatherPlan plan) {
    }

    /** The weather plan an addon registered, if any. @since 1.9 */
    default Optional<WeatherPlan> weatherPlan() {
        return Optional.empty();
    }

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

    /**
     * How big a system is and what equipment its size needs (EZvac BETA 1.0,
     * size-based heating and cooling). Empty for an unknown system, one whose
     * air isn't mapped yet (its chunk unloaded), or with building size
     * switched off.
     *
     * @param system a system's key (see {@link #thermostatOf})
     * @since 1.6
     */
    default Optional<SystemSize> systemSize(World world, String system) {
        return Optional.empty();
    }

    /**
     * What the owner has switched in /hvac quicksettings, as it is now.
     *
     * @since 1.6
     */
    default ServerSettingsView serverSettings() {
        return new ServerSettingsView(java.util.Set.of(), true, true, true, true, true);
    }

    /**
     * The weather stations in a world (EZvac BETA 1.0), working or not.
     * Weather stations are not {@link HvacDevice}s: they aren't in
     * {@link #devices(World)} and fire no device events.
     *
     * @since 1.6
     */
    default Collection<WeatherStationView> weatherStations(World world) {
        return List.of();
    }

    /**
     * The weather station whose sign is at a block.
     *
     * @since 1.6
     */
    default Optional<WeatherStationView> weatherStationAt(Position position) {
        return Optional.empty();
    }

    /**
     * The working stations whose forecast a player at this spot could see
     * with {@code /hvac forecast}: in the same world, within the server's
     * range (400 blocks by default), nearest first.
     *
     * @since 1.6
     */
    default List<WeatherStationView> weatherStationsNear(Location location) {
        return List.of();
    }
}
