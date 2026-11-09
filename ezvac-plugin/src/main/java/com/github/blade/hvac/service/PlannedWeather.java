package com.github.blade.hvac.service;

import com.github.blade.hvac.api.WeatherEvent;
import com.github.blade.hvac.api.WeatherPlan;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The weather plan an addon registered (BETA 1.0 Pilot 4, API 1.9), as the
 * services read it. An addon's plan that throws is treated as no plan for
 * that call and said once, so it can never stop EZvac. Main thread only.
 */
public final class PlannedWeather {
    private final Consumer<String> warn;
    private Plugin owner;
    private WeatherPlan plan = WeatherPlan.NONE;
    private boolean warned;

    public PlannedWeather(Consumer<String> warn) {
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    /** No plan: the services read Minecraft's own weather. For tests and a server without addons. */
    public static PlannedWeather none() {
        return new PlannedWeather(ignored -> {});
    }

    public void set(Plugin plugin, WeatherPlan plan) {
        if (plan == null) {
            if (owner == null || owner.equals(plugin)) clear();
            return;
        }
        this.owner = plugin;
        this.plan = plan;
        warned = false;
    }

    /** Drops a plugin's plan, as it disables. */
    public void dropFrom(Plugin plugin) {
        if (owner != null && owner.equals(plugin)) clear();
    }

    private void clear() {
        owner = null;
        plan = WeatherPlan.NONE;
    }

    public boolean active() { return plan != WeatherPlan.NONE; }

    public Optional<WeatherPlan> plan() { return active() ? Optional.of(plan) : Optional.empty(); }

    public double shiftNowF(World world) {
        if (!active() || world == null) return 0.0;
        try {
            double shift = plan.shiftNowF(world);
            return Double.isFinite(shift) ? Math.max(-60.0, Math.min(60.0, shift)) : 0.0;
        } catch (RuntimeException failure) {
            failed(failure);
            return 0.0;
        }
    }

    public Optional<WeatherEvent> eventNow(World world) {
        if (!active() || world == null) return Optional.empty();
        try {
            Optional<WeatherEvent> event = plan.eventNow(world);
            return event == null ? Optional.empty() : event;
        } catch (RuntimeException failure) {
            failed(failure);
            return Optional.empty();
        }
    }

    /** The plan's days from today; empty without a plan. */
    public List<WeatherPlan.Outlook> outlook(World world, int days) {
        if (!active() || world == null) return List.of();
        try {
            List<WeatherPlan.Outlook> outlook = plan.outlook(world, days);
            return outlook == null ? List.of() : outlook;
        } catch (RuntimeException failure) {
            failed(failure);
            return List.of();
        }
    }

    private void failed(RuntimeException failure) {
        if (warned) return;
        warned = true;
        warn.accept((owner == null ? "An addon" : owner.getName()) + "'s weather plan failed (" + failure
                + "); EZvac uses Minecraft's own weather while it does. This is said once.");
    }
}
