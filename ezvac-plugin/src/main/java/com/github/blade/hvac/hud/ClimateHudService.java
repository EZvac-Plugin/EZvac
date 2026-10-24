package com.github.blade.hvac.hud;

import com.github.blade.hvac.config.HudSettings;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.SeasonService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Keeps one {@link ClimateSnapshot} per player and draws the built-in HUD.
 *
 * <p>Snapshots are built on the main thread every {@code hud.update-ticks},
 * the only place airflow and registry data may be read. Everything else -
 * renderers here, placeholder requests from any thread - reads the latest
 * immutable snapshot, so a HUD plugin refreshing many players many times a
 * second costs EZvac nothing extra.
 */
public final class ClimateHudService implements Listener {
    private final Plugin plugin;
    private final AirflowService airflow;
    /** Null where there is no calendar (tests): the HUD then shows no season. */
    private final SeasonService seasons;
    private final HudFormatter format;
    private final HudRenderer renderer;
    /** The scoreboard plugin that pushed the HUD from the sidebar to the action bar, or null. */
    private final String sidebarYieldedTo;
    private final NamespacedKey toggleKey;
    private final Map<UUID, ClimateSnapshot> snapshots = new ConcurrentHashMap<>();
    /** Players with the HUD switched on. Main thread only; mirrors their persistent data. */
    private final Set<UUID> showing = new HashSet<>();
    private volatile boolean placeholdersActive;
    private BukkitTask task;

    public ClimateHudService(Plugin plugin, AirflowService airflow, HudSettings settings) {
        this(plugin, airflow, settings, null);
    }

    public ClimateHudService(Plugin plugin, AirflowService airflow, HudSettings settings, SeasonService seasons) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.seasons = seasons;
        this.format = new HudFormatter(settings, plugin.getPluginMeta().getVersion());
        // Scoreboard plugins like TAB draw their sidebar with raw packets, which
        // the server's scoreboard API cannot see, so the sidebar's own conflict
        // check would miss them and the two boards would fight. Their presence
        // is checked by name instead (loaded counts: they may enable after us).
        String yieldedTo = null;
        if (settings.style() == HudSettings.Style.SIDEBAR)
            for (String name : settings.sidebarYieldTo())
                if (plugin.getServer().getPluginManager().getPlugin(name) != null) { yieldedTo = name; break; }
        this.sidebarYieldedTo = yieldedTo;
        HudSettings.Style style = yieldedTo == null ? settings.style() : HudSettings.Style.ACTIONBAR;
        this.renderer = switch (style) {
            case SIDEBAR -> new SidebarRenderer(format);
            case ACTIONBAR -> new ActionBarRenderer(format);
            case BOSSBAR -> new BossBarRenderer(format);
        };
        this.toggleKey = new NamespacedKey(plugin, "hud");
    }

    public HudFormatter format() { return format; }

    /** The installed scoreboard plugin that moved the HUD to the action bar, or null. */
    public String sidebarYieldedTo() { return sidebarYieldedTo; }

    public boolean enabled() { return format.settings().enabled(); }

    /** Once placeholders are registered, every online player needs a snapshot, not just HUD users. */
    public void setPlaceholdersActive(boolean active) { this.placeholdersActive = active; }

    /** The latest snapshot for a player, or null. Safe from any thread. */
    public ClimateSnapshot snapshot(UUID player) {
        return snapshots.get(player);
    }

    public void start() {
        for (Player player : plugin.getServer().getOnlinePlayers()) restore(player);
        long period = format.settings().updateTicks();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::update, 1L, period);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player player : plugin.getServer().getOnlinePlayers())
            if (showing.contains(player.getUniqueId())) renderer.clear(player);
        showing.clear();
        snapshots.clear();
    }

    public boolean isShowing(Player player) {
        return showing.contains(player.getUniqueId());
    }

    /** Turns the HUD on or off for a player and remembers it in their own data. */
    public void setShowing(Player player, boolean show) {
        player.getPersistentDataContainer().set(toggleKey, PersistentDataType.BYTE, (byte) (show ? 1 : 0));
        if (show) {
            showing.add(player.getUniqueId());
            render(player);
        } else if (showing.remove(player.getUniqueId())) {
            renderer.clear(player);
        }
    }

    private void restore(Player player) {
        Byte stored = player.getPersistentDataContainer().get(toggleKey, PersistentDataType.BYTE);
        if (enabled() && stored != null && stored == 1) showing.add(player.getUniqueId());
    }

    /**
     * Something outside the HUD that wants each player's reading on the HUD's
     * cadence: the public API (2.1 Pilot 4), while an addon listens.
     */
    public interface ReadingObserver {
        /** Whether to read every player this time, HUD or not. */
        boolean wanted();

        void observe(Player player, AirflowService.TemperatureReading reading);
    }

    private ReadingObserver observer;

    public void setObserver(ReadingObserver observer) { this.observer = observer; }

    private void update() {
        boolean observed = observer != null && observer.wanted();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean shown = showing.contains(player.getUniqueId());
            if (!shown && !placeholdersActive && !observed) continue;
            try {
                AirflowService.TemperatureReading reading = read(player);
                ClimateSnapshot snapshot = capture(player, reading);
                snapshots.put(player.getUniqueId(), snapshot);
                if (shown) renderer.render(player, snapshot);
                if (observed) observer.observe(player, reading);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not update the climate HUD for "
                        + player.getName(), exception);
            }
        }
    }

    private void render(Player player) {
        ClimateSnapshot snapshot = capture(player);
        snapshots.put(player.getUniqueId(), snapshot);
        renderer.render(player, snapshot);
    }

    private AirflowService.TemperatureReading read(Player player) {
        return airflow.temperatureForBody(player.getLocation(), player.getEyeLocation());
    }

    private ClimateSnapshot capture(Player player) {
        return capture(player, read(player));
    }

    private ClimateSnapshot capture(Player player, AirflowService.TemperatureReading reading) {
        ClimateSnapshot snapshot = captureClimate(reading);
        UUID world = player.getWorld().getUID();
        if (seasons == null || !seasons.settings().enabled() || !seasons.tracks(world)) return snapshot;
        return snapshot.inSeason(seasons.season(world).displayName(), seasons.dayOfSeason(world));
    }

    private static ClimateSnapshot captureClimate(AirflowService.TemperatureReading reading) {
        Thermostat thermostat = reading.thermostat();
        if (reading.group() == null || thermostat == null)
            return new ClimateSnapshot(reading.localF(), reading.outdoorF(), 0.0, reading.airflowDistance(),
                    null, null, null, Double.NaN, false, reading.water(),
                    reading.humidity(), reading.feelsLikeF(), reading.weather());
        return new ClimateSnapshot(reading.localF(), reading.outdoorF(), reading.influence(),
                reading.airflowDistance(), reading.group().label(), thermostat.id(),
                thermostat.mode().name(), thermostat.targetF(),
                reading.influence() >= 1.0, reading.water(),
                reading.humidity(), reading.feelsLikeF(), reading.weather());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        restore(player);
        // Scoreboard plugins often draw on join, before the next update would
        // run; without this, placeholders read "none" for that first frame.
        if (placeholdersActive) {
            try {
                snapshots.put(player.getUniqueId(), capture(player));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read the climate for " + player.getName(), exception);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (showing.remove(player.getUniqueId())) renderer.clear(player);
        snapshots.remove(player.getUniqueId());
    }
}
