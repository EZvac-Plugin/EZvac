package com.github.blade.hvac;

import com.github.blade.hvac.command.HvacCommand;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.listener.HvacListener;
import com.github.blade.hvac.service.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.logging.Level;

/** Standalone EZvac bootstrap and lifecycle owner. */
public final class HvacPlugin extends JavaPlugin {
    private HvacSettings settings;
    private HvacRegistry registry;
    private OutdoorTemperatureService outdoor;
    private AirflowService airflow;
    private DisplayService displays;
    private HvacController controller;
    private HvacAudioService audio;
    private BukkitTask controllerTask;
    private BukkitTask airflowTask;
    private boolean immediateSyncQueued;
    private long ticksSinceDisplay;
    private long ticksSinceSave;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            settings = HvacSettings.load(getConfig());
            registry = new HvacRegistry(this, settings);
            registry.load();
            outdoor = new OutdoorTemperatureService();
            airflow = new AirflowService(registry, outdoor, settings);
            displays = new DisplayService(this, registry, airflow, outdoor, settings);
            controller = new HvacController(this, registry, outdoor, airflow, settings);
            audio = new HvacAudioService(settings);

            HvacCommand commandHandler = new HvacCommand(this, registry, controller,
                    outdoor, airflow, displays, settings);
            PluginCommand command = Objects.requireNonNull(getCommand("hvac"),
                    "Command 'hvac' is missing from plugin.yml");
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
            getServer().getPluginManager().registerEvents(new HvacListener(
                    this, registry, airflow, displays, commandHandler), this);

            controllerTask = getServer().getScheduler().runTaskTimer(this,
                    () -> runController(settings.controllerPeriodTicks()),
                    settings.controllerPeriodTicks(), settings.controllerPeriodTicks());
            airflowTask = getServer().getScheduler().runTaskTimer(this, airflow::tick, 1L, 1L);
            displays.refreshAll(true);
            requestImmediateSync();
            getLogger().info("EZvac enabled: " + registry.thermostats().size()
                    + " thermostat(s), " + registry.equipment().size() + " equipment unit(s), "
                    + registry.vents().size() + " vent(s), "
                    + registry.settingsPanels().size() + " settings panel(s).");
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "EZvac could not start safely", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (controllerTask != null) controllerTask.cancel();
        if (airflowTask != null) airflowTask.cancel();
        if (registry != null) {
            registry.shutdownEquipment();
            registry.save();
        }
        getLogger().info("EZvac disabled; owned loaded outputs were retracted and state was saved.");
    }

    private void runController(long elapsedTicks) {
        if (!isEnabled() || controller == null) return;
        try {
            HvacController.CycleResult result = controller.cycle(elapsedTicks);
            audio.applyTransitions(result);
            audio.tickHum(result.running(), elapsedTicks);
            if (result.prunedDevices() > 0)
                getLogger().info("Removed " + result.prunedDevices() + " missing HVAC device record(s).");

            if (elapsedTicks > 0) {
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
}
