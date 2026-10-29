package com.github.blade.hvac.update;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Tells the console, and admins as they join, when a newer EZvac is on
 * GitHub (BETA 1.0). It reads GitHub's public release list in the
 * background, at start-up and every six hours; a failed check is logged once
 * and retried at the next one.
 */
public final class UpdateNotifier implements Listener {

    static final String RELEASES = "https://api.github.com/repos/EZvac-Plugin/EZvac/releases?per_page=30";
    private static final long FIRST_CHECK_TICKS = 20L * 10;
    private static final long PERIOD_TICKS = 20L * 60 * 60 * 6;
    private static final long JOIN_DELAY_TICKS = 40L;

    private final Plugin plugin;
    private final String runningName;
    private final ReleaseVersion running;
    private final boolean notifyAdmins;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile UpdateCheck.Release available;
    private boolean failureLogged;
    private BukkitTask task;

    public UpdateNotifier(Plugin plugin, String runningName, boolean notifyAdmins) {
        this.plugin = plugin;
        this.runningName = runningName;
        this.running = ReleaseVersion.parse(runningName);
        this.notifyAdmins = notifyAdmins;
    }

    public void start() {
        if (running == null) {
            plugin.getLogger().info("Update checks are off: " + runningName + " is not a release build.");
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::check,
                FIRST_CHECK_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        // A check still waiting on GitHub must not hold up the shutdown or outlive a reload.
        http.shutdownNow();
    }

    /** Off the main thread. */
    private void check() {
        List<UpdateCheck.Release> releases;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "EZvac/" + runningName)
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IOException("GitHub answered " + response.statusCode());
            releases = UpdateCheck.parse(response.body());
        } catch (IOException | RuntimeException failure) {
            failed(failure.getMessage());
            return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        failureLogged = false;
        UpdateCheck.Release newer = UpdateCheck.newer(running, releases);
        if (newer == null || (available != null && available.version().equals(newer.version()))) return;
        available = newer;
        onMainThread(() -> announce(newer));
    }

    private void failed(String reason) {
        if (failureLogged || !plugin.isEnabled()) return;
        failureLogged = true;
        plugin.getLogger().info("Could not check GitHub for a newer EZvac (" + reason + "); trying again later.");
    }

    private void announce(UpdateCheck.Release release) {
        plugin.getLogger().info("EZvac " + release.tag() + (release.pilot() ? ", a pilot," : "")
                + " is out; this server runs " + runningName + ". " + release.url());
        if (!notifyAdmins) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) tell(player, release);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UpdateCheck.Release release = available;
        if (release == null || !notifyAdmins) return;
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) tell(player, release);
        }, JOIN_DELAY_TICKS);
    }

    private void tell(Player player, UpdateCheck.Release release) {
        if (!player.hasPermission("ezvac.admin")) return;
        player.sendMessage(message(release, runningName));
    }

    static Component message(UpdateCheck.Release release, String runningName) {
        Component link = Component.text("Open the release", NamedTextColor.AQUA, TextDecoration.UNDERLINED);
        if (release.url() != null) link = link.clickEvent(ClickEvent.openUrl(release.url()));
        return Component.text("[EZvac] ", NamedTextColor.GOLD)
                .append(Component.text("EZvac " + release.tag() + (release.pilot() ? " (a pilot)" : "")
                        + " is out; this server runs " + runningName + ". ", NamedTextColor.YELLOW))
                .append(link);
    }

    private void onMainThread(Runnable action) {
        if (!plugin.isEnabled()) return;
        try {
            plugin.getServer().getScheduler().runTask(plugin, action);
        } catch (IllegalStateException | org.bukkit.plugin.IllegalPluginAccessException disabling) {
            // The plugin is shutting down; the next start checks again.
        }
    }
}
