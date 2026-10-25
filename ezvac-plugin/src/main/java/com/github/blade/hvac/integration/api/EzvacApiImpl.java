package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.ClimateUnitView;
import com.github.blade.hvac.api.EzvacApi;
import com.github.blade.hvac.api.OutdoorClimate;
import com.github.blade.hvac.api.Position;
import com.github.blade.hvac.api.SeasonView;
import com.github.blade.hvac.api.TemperatureUnit;
import com.github.blade.hvac.api.ThermostatView;
import com.github.blade.hvac.api.event.PlayerClimateUpdateEvent;
import com.github.blade.hvac.api.hud.EzvacHud;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.hud.ClimateHudService;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.HvacController;
import com.github.blade.hvac.service.HvacRegistry;
import com.github.blade.hvac.service.OutdoorTemperatureService;
import com.github.blade.hvac.service.SeasonService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * EZvac's public API, read-only (and addon HUD sections since 1.1): it adapts what EZvac already
 * works out for the HUD, signs and commands, and hands out immutable views.
 * Registered with Paper's services manager while EZvac is enabled.
 */
public final class EzvacApiImpl implements EzvacApi, Listener, ClimateHudService.ReadingObserver {
    private final String pluginVersion;
    private final HvacSettings settings;
    private final HvacRegistry registry;
    private final HvacController controller;
    private final AirflowService airflow;
    private final OutdoorTemperatureService outdoor;
    private final SeasonService seasons;
    private final HudSections hud;
    /** The last update per player, for latestFor from any thread. */
    private final Map<UUID, ClimateReading> latest = new ConcurrentHashMap<>();

    public EzvacApiImpl(String pluginVersion, HvacSettings settings, HvacRegistry registry, HvacController controller,
                        AirflowService airflow, OutdoorTemperatureService outdoor, SeasonService seasons,
                        ClimateHudService hudService, Logger logger) {
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.hud = new HudSections(logger, hudService.format(), (player, reading) ->
                ApiViews.reading(Position.of(player.getLocation()), reading, seasonOf(player.getWorld())));
    }

    /** The addon sections, for the HUD to draw. */
    public HudSections sections() { return hud; }

    @Override
    public EzvacHud hud() { return hud; }

    @Override
    public String apiVersion() { return API_VERSION; }

    @Override
    public String pluginVersion() { return pluginVersion; }

    @Override
    public TemperatureUnit units() {
        return settings.units() == com.github.blade.hvac.config.TemperatureUnit.C ? TemperatureUnit.C : TemperatureUnit.F;
    }

    @Override
    public ClimateReading readingAt(Location location) {
        return ApiViews.reading(Position.of(location), airflow.temperatureAt(location), seasonOf(location.getWorld()));
    }

    @Override
    public ClimateReading readingFor(Player player) {
        return ApiViews.reading(Position.of(player.getLocation()),
                airflow.temperatureForBody(player.getLocation(), player.getEyeLocation()), seasonOf(player.getWorld()));
    }

    @Override
    public Optional<ClimateReading> latestFor(UUID player) {
        return Optional.ofNullable(latest.get(player));
    }

    @Override
    public OutdoorClimate outdoorAt(Location location) {
        OutdoorTemperatureService.Outdoor out = outdoor.climateAt(location);
        return new OutdoorClimate(out.airF(), out.waterF(), out.humidity(), out.dewPointF(), out.feelsLikeF(),
                out.weather(), outdoor.seasonShiftAt(location));
    }

    @Override
    public Collection<ThermostatView> thermostats() {
        return registry.thermostats().stream().map(this::view).toList();
    }

    @Override
    public Optional<ThermostatView> thermostat(World world, String id) {
        Thermostat thermostat = id == null ? null : registry.thermostatById(id);
        if (thermostat == null || world == null || !thermostat.position().worldId().equals(world.getUID()))
            return Optional.empty();
        return Optional.of(view(thermostat));
    }

    @Override
    public Collection<ClimateUnitView> climateUnits() {
        return registry.climateUnits().stream().map(ApiViews::climateUnit).toList();
    }

    @Override
    public Optional<SeasonView> season(World world) {
        if (world == null) return Optional.empty();
        UUID id = world.getUID();
        if (!seasons.settings().enabled() || !seasons.tracks(id)) return Optional.empty();
        return Optional.of(new SeasonView(ApiViews.season(seasons.season(id)), seasons.dayOfSeason(id),
                seasons.settings().daysPerSeason(), seasons.paused(id)));
    }

    private Optional<SeasonView.Season> seasonOf(World world) {
        return season(world).map(SeasonView::season);
    }

    private ThermostatView view(Thermostat thermostat) {
        return ApiViews.thermostat(thermostat, controller.status(thermostat.group()));
    }

    // ---- per-player updates, on the HUD's cadence ----

    @Override
    public boolean wanted() {
        return ApiEvents.listened(PlayerClimateUpdateEvent.getHandlerList());
    }

    @Override
    public void observe(Player player, AirflowService.TemperatureReading reading) {
        ClimateReading current = ApiViews.reading(Position.of(player.getLocation()), reading, seasonOf(player.getWorld()));
        ClimateReading previous = latest.put(player.getUniqueId(), current);
        Bukkit.getPluginManager().callEvent(new PlayerClimateUpdateEvent(player, previous, current));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        latest.remove(event.getPlayer().getUniqueId());
    }

    /** Forgets every player, when EZvac stops. */
    public void clear() {
        latest.clear();
        hud.clear();
    }
}
