package com.github.blade.hvac.service;

import com.github.blade.hvac.HvacPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anonymous usage statistics through bStats (BETA 1.0 Pilot 9): how many
 * servers and players run EZvac, shown on bstats.org and in the README.
 * bStats sends a random server id, counts and versions, never anything that
 * names a server or a player. Off with {@code metrics.enabled: false} in
 * EZvac's config, or for every plugin in {@code plugins/bStats/config.yml}.
 * Each of EZvac's own charts has its own switch too.
 */
public final class UsageStats {
    /** EZvac on bstats.org: https://bstats.org/plugin/bukkit/EZvac/35377 */
    public static final int PLUGIN_ID = 35377;

    private final Metrics metrics;

    private UsageStats(Metrics metrics) {
        this.metrics = metrics;
    }

    /** Starts sending, unless {@code metrics.enabled} is false; null then. */
    public static UsageStats start(HvacPlugin plugin) {
        if (!plugin.getConfig().getBoolean("metrics.enabled", true)) return null;
        Metrics metrics = new Metrics(plugin, PLUGIN_ID);
        // A few settings worth knowing which way servers go; all of them asked of bStats' own schedule.
        // Each chart has its own switch (metrics.<chart>, quick settings › Anonymous statistics); one switched
        // off answers nothing, which bStats leaves out.
        metrics.addCustomChart(new SimplePie("temperature_unit",
                () -> plugin.statOn("temperature-unit") ? plugin.settings().units().name() : null));
        metrics.addCustomChart(new SimplePie("unloaded_devices",
                () -> !plugin.statOn("unloaded-devices") ? null : plugin.settings().unloadedDevices() ? "On" : "Off"));
        metrics.addCustomChart(new SimplePie("systems",
                () -> plugin.statOn("systems") ? systemsBand(plugin.registry().thermostats().size()) : null));
        // The official addons installed, each with its version.
        metrics.addCustomChart(new DrilldownPie("official_addons", () -> !plugin.statOn("addons")
                ? null : addonVersions(plugin.addons().installed().stream()
                        .map(addon -> Map.entry(addon.entry().name(), addon.version() == null ? "" : addon.version())).toList())));
        return new UsageStats(metrics);
    }

    /** A server's systems, in bands: never the exact number. */
    static String systemsBand(int systems) {
        if (systems <= 0) return "0";
        if (systems <= 10) return "1-10";
        if (systems <= 50) return "11-50";
        if (systems <= 200) return "51-200";
        if (systems <= 1000) return "201-1000";
        return "1000+";
    }

    /** Each addon by its versions, as bStats' drilldown wants it; "None" when no official addon runs. */
    static Map<String, Map<String, Integer>> addonVersions(List<Map.Entry<String, String>> installed) {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> addon : installed)
            out.computeIfAbsent(addon.getKey(), ignored -> new LinkedHashMap<>())
                    .merge(addon.getValue() == null || addon.getValue().isBlank() ? "unknown" : addon.getValue(), 1, Integer::sum);
        if (out.isEmpty()) out.put("None", Map.of("None", 1));
        return out;
    }

    public void stop() {
        metrics.shutdown();
    }
}
