package com.github.blade.hvac;

import com.github.blade.hvac.command.HvacCommand;
import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.config.ConfigUpdater;
import com.github.blade.hvac.config.HudSettings;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.SoundSettings;
import com.github.blade.hvac.hud.ClimateHudService;
import com.github.blade.hvac.integration.placeholderapi.PlaceholderApiBridge;
import com.github.blade.hvac.listener.HvacListener;
import com.github.blade.hvac.service.*;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

/** Standalone EZvac bootstrap and lifecycle owner. */
public final class HvacPlugin extends JavaPlugin {
    private HvacSettings settings;
    private ClimateSettings climate;
    private WeatherService weather;
    private HvacRegistry registry;
    private OutdoorTemperatureService outdoor;
    private AirflowService airflow;
    private DisplayService displays;
    private HvacController controller;
    private HvacAudioService audio;
    private ClimateUnitService units;
    /** Units running after the last cycle, for the humidifier bubbles between cycles. */
    private List<com.github.blade.hvac.model.ClimateUnit> runningUnits = List.of();
    private BukkitTask bubbleTask;
    private ClimateHudService hud;
    /** Unregisters %ezvac_*%; null unless PlaceholderAPI is installed. */
    private Runnable placeholderUnregister;
    private BukkitTask controllerTask;
    private BukkitTask airflowTask;
    private boolean immediateSyncQueued;
    private long ticksSinceDisplay;
    private long ticksSinceSave;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            addNewSettingsToConfig();
            settings = HvacSettings.load(getConfig());
            climate = ClimateSettings.load(getConfig());
            registry = new HvacRegistry(this, settings);
            registry.load();
            weather = new WeatherService(climate.weather());
            outdoor = new OutdoorTemperatureService(climate, weather);
            airflow = new AirflowService(registry, outdoor, settings);
            displays = new DisplayService(this, registry, airflow, outdoor, settings);
            units = new ClimateUnitService(registry, airflow, outdoor);
            controller = new HvacController(this, registry, outdoor, airflow, settings, units);
            audio = new HvacAudioService(settings, loadSounds(), registry);
            hud = new ClimateHudService(this, airflow, HudSettings.load(getConfig()));

            HvacCommand commandHandler = new HvacCommand(this, registry, controller,
                    outdoor, airflow, displays, settings, hud, units);
            PluginCommand command = Objects.requireNonNull(getCommand("hvac"),
                    "Command 'hvac' is missing from plugin.yml");
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
            getServer().getPluginManager().registerEvents(new HvacListener(
                    this, registry, airflow, displays, commandHandler, units), this);
            getServer().getPluginManager().registerEvents(hud, this);
            hud.start();
            if (hud.sidebarYieldedTo() != null)
                getLogger().info(hud.sidebarYieldedTo() + " draws its own scoreboard, so the /hvac hud overlay uses"
                        + " the action bar instead of the sidebar. To show EZvac in that scoreboard, add"
                        + " %ezvac_temp%, %ezvac_bar% and friends to it (needs PlaceholderAPI).");
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) registerPlaceholders();
            else getLogger().info("PlaceholderAPI is not installed. The /hvac hud overlay works without it;"
                    + " install PlaceholderAPI to also show %ezvac_temp%, %ezvac_bar%, %ezvac_group% and"
                    + " more in scoreboard, TAB, and chat plugins.");

            controllerTask = getServer().getScheduler().runTaskTimer(this,
                    () -> runController(settings.controllerPeriodTicks()),
                    settings.controllerPeriodTicks(), settings.controllerPeriodTicks());
            airflowTask = getServer().getScheduler().runTaskTimer(this, airflow::tick, 1L, 1L);
            bubbleTask = getServer().getScheduler().runTaskTimer(this,
                    () -> audio.bubble(runningUnits), HvacAudioService.BUBBLE_PERIOD_TICKS,
                    HvacAudioService.BUBBLE_PERIOD_TICKS);
            displays.refreshAll(true);
            requestImmediateSync();
            getLogger().info("EZvac enabled: " + registry.thermostats().size()
                    + " thermostat(s), " + registry.equipment().size() + " equipment unit(s), "
                    + registry.vents().size() + " vent(s), "
                    + registry.settingsPanels().size() + " settings panel(s), "
                    + registry.dehumidifiers().size() + " dehumidifier(s), "
                    + registry.humidifiers().size() + " humidifier(s).");
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "EZvac could not start safely", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Writes settings added since the server's config.yml was made into it,
     * with their comments, without touching anything already there. Before
     * this, a server upgraded from an older release never saw new settings:
     * they applied with their defaults, but nobody knew they existed.
     */
    private void addNewSettingsToConfig() {
        File file = new File(getDataFolder(), "config.yml");
        YamlConfiguration current = new YamlConfiguration();
        try {
            current.load(file);
        } catch (IOException | InvalidConfigurationException failure) {
            // Never rewrite a file we could not read: that would lose the admin's edits.
            getLogger().warning("config.yml could not be read, so new settings were not added to it: "
                    + failure.getMessage());
            return;
        }
        YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream input = Objects.requireNonNull(getResource("config.yml"), "bundled config.yml")) {
            bundled.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException failure) {
            getLogger().log(Level.WARNING, "Could not read the bundled config.yml", failure);
            return;
        }
        List<String> added = ConfigUpdater.addMissing(current, bundled);
        if (added.isEmpty()) return;
        SoundSettings.carryOverHum(current, added);
        File backup = new File(getDataFolder(), "config.yml.before-" + getPluginMeta().getVersion() + ".bak");
        try {
            if (!backup.exists()) Files.copy(file.toPath(), backup.toPath());
            current.save(file);
        } catch (IOException failure) {
            getLogger().log(Level.WARNING, "Could not add new settings to config.yml; it was left as it was",
                    failure);
            return;
        }
        reloadConfig();
        getLogger().info("Added " + added.size() + " new setting(s) to config.yml ("
                + String.join(", ", ConfigUpdater.topLevel(added)) + "). Existing settings were not changed;"
                + " the previous file was kept as " + backup.getName() + ".");
    }

    /**
     * The sounds from config.yml. A key the server doesn't know is played
     * anyway, since a resource pack may add it, but is pointed out once.
     */
    private SoundSettings loadSounds() {
        SoundSettings sounds = SoundSettings.load(getConfig(), getLogger()::warning);
        Set<String> unknown = new TreeSet<>();
        for (SoundSettings.Event event : SoundSettings.Event.values())
            for (SoundSettings.Cue cue : sounds.get(event)) {
                NamespacedKey key = NamespacedKey.fromString(cue.sound());
                if (key == null || Registry.SOUNDS.get(key) == null) unknown.add(cue.sound());
            }
        if (!unknown.isEmpty())
            getLogger().warning("audio.sounds names sounds this server doesn't have: " + String.join(", ", unknown)
                    + ". They are only heard if a resource pack adds them.");
        return sounds;
    }

    /** Only reached when PlaceholderAPI is enabled, so its classes are never needed otherwise. */
    private void registerPlaceholders() {
        try {
            PlaceholderApiBridge bridge = PlaceholderApiBridge.register(this, hud);
            if (bridge == null) {
                getLogger().warning("PlaceholderAPI refused the %ezvac_*% placeholders.");
                return;
            }
            placeholderUnregister = bridge::unregister;
            hud.setPlaceholdersActive(true);
            getLogger().info("Registered %ezvac_*% placeholders with PlaceholderAPI.");
        } catch (LinkageError | RuntimeException failure) {
            getLogger().log(Level.WARNING, "Could not register EZvac placeholders with PlaceholderAPI;"
                    + " the /hvac hud overlay still works.", failure);
        }
    }

    @Override
    public void onDisable() {
        if (placeholderUnregister != null) placeholderUnregister.run();
        if (hud != null) hud.shutdown();
        if (controllerTask != null) controllerTask.cancel();
        if (airflowTask != null) airflowTask.cancel();
        if (bubbleTask != null) bubbleTask.cancel();
        if (registry != null) {
            registry.shutdownEquipment();
            registry.save(true); // shutdown must not hand work to the scheduler
        }
        if (registry != null && !registry.isWritable())
            getLogger().warning("EZvac disabled without saving: hvac.yml could not be loaded, so it was left untouched.");
        else
            getLogger().info("EZvac disabled; owned loaded outputs were retracted and state was saved.");
    }

    private void runController(long elapsedTicks) {
        if (!isEnabled() || controller == null) return;
        try {
            weather.advance(elapsedTicks);
            units.prepare();
            HvacController.CycleResult result = controller.cycle(elapsedTicks);
            ClimateUnitService.Changes unitChanges = units.afterCycle(elapsedTicks);
            runningUnits = unitChanges.running();
            audio.applyTransitions(result);
            audio.applyUnitTransitions(unitChanges);
            audio.tickHum(result.running(), unitChanges.running(), elapsedTicks);
            audio.tickVents(result.running(), elapsedTicks);
            if (result.prunedDevices() > 0)
                getLogger().info("Removed " + result.prunedDevices() + " missing HVAC device record(s).");

            if (elapsedTicks > 0) {
                displays.advanceAutoFlip(elapsedTicks);
                ticksSinceDisplay += elapsedTicks;
                ticksSinceSave += elapsedTicks;
                if (ticksSinceDisplay >= settings.displayPeriodTicks()) {
                    ticksSinceDisplay %= settings.displayPeriodTicks();
                    displays.refreshAll(true);
                } else {
                    displays.refreshAll(false);
                }
                if (ticksSinceSave >= settings.savePeriodTicks()) {
                    ticksSinceSave %= settings.savePeriodTicks();
                    registry.save();
                }
            } else {
                displays.refreshAll(false);
            }
        } catch (RuntimeException exception) {
            // Per-device failures are already isolated by HvacController. This
            // is the final lifecycle guard for truly systemic failures.
            getLogger().log(Level.SEVERE, "EZvac controller cycle failed", exception);
        }
    }

    /** Coalesces command/event reconciliation requests into one zero-time cycle. */
    public void requestImmediateSync() {
        if (!isEnabled() || controller == null || immediateSyncQueued) return;
        immediateSyncQueued = true;
        getServer().getScheduler().runTask(this, () -> {
            try {
                runController(0L);
            } finally {
                immediateSyncQueued = false;
            }
        });
    }

    public HvacSettings settings() { return settings; }
    public ClimateSettings climate() { return climate; }
}
