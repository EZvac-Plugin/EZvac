package com.github.blade.hvac.update;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Tells the console, and admins as they join, when a newer EZvac is on
 * GitHub (BETA 1.0), and answers /hvac update. It reads GitHub's public
 * release list in the background, at start-up and every six hours; a failed
 * check is logged once and retried at the next one. A release that needs a
 * newer Minecraft than the server runs is named with what it needs, beside
 * the newest one that runs here.
 */
public final class UpdateNotifier implements Listener {

    static final String RELEASES = "https://api.github.com/repos/EZvac-Plugin/EZvac/releases?per_page=100";
    private static final long FIRST_CHECK_TICKS = 20L * 10;
    private static final long PERIOD_TICKS = 20L * 60 * 60 * 6;
    private static final long JOIN_DELAY_TICKS = 40L;
    /** /hvac update reuses a list this fresh, so repeating it doesn't spend GitHub's 60 requests an hour. */
    private static final long REUSE_MILLIS = 60_000L;

    private final Plugin plugin;
    private final String runningName;
    private final ReleaseVersion running;
    private final String minecraft;
    private volatile boolean notifyAdmins;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile UpdateCheck.Outcome available;
    private volatile Fetched recent;
    private boolean failureLogged;
    private BukkitTask task;

    private record Fetched(List<UpdateCheck.Release> releases, long at) {}

    public UpdateNotifier(Plugin plugin, String runningName, String minecraft, boolean notifyAdmins) {
        this.plugin = plugin;
        this.runningName = runningName;
        this.running = ReleaseVersion.parse(runningName);
        this.minecraft = minecraft;
        this.notifyAdmins = notifyAdmins;
    }

    /** The background checks; without them (updates.check: false) only /hvac update checks. */
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

    /** Whether the background checks run. */
    public boolean checking() { return task != null; }

    /** Starts or stops the background checks while the server runs (/hvac quicksettings). */
    public void setChecking(boolean on) {
        if (on == checking()) return;
        if (on) start();
        else {
            task.cancel();
            task = null;
            available = null;
        }
    }

    public boolean notifyAdmins() { return notifyAdmins; }

    public void setNotifyAdmins(boolean on) { notifyAdmins = on; }

    /** This server's EZvac and Minecraft, for the menu. */
    public String runningName() { return runningName; }
    public String minecraft() { return minecraft; }

    /** /hvac update: checks GitHub now and tells the sender what it found. */
    public void checkNow(CommandSender sender) {
        if (running == null) {
            sender.sendMessage(prefixed(Component.text("This EZvac (" + runningName + ") is a development build,"
                    + " not a release, so there is nothing to check it against.", NamedTextColor.YELLOW)));
            return;
        }
        Fetched cached = recent;
        if (cached != null && System.currentTimeMillis() - cached.at() < REUSE_MILLIS) {
            reply(sender, cached.releases());
            return;
        }
        sender.sendMessage(prefixed(Component.text("Checking GitHub for a newer EZvac...", NamedTextColor.GRAY)));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<UpdateCheck.Release> releases = fetch();
                onMainThread(() -> reply(sender, releases));
            } catch (IOException | RuntimeException failure) {
                onMainThread(() -> {
                    if (stillThere(sender)) sender.sendMessage(prefixed(Component.text("Could not reach GitHub ("
                            + failure.getMessage() + "). Try again later.", NamedTextColor.RED)));
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void reply(CommandSender sender, List<UpdateCheck.Release> releases) {
        if (!stillThere(sender)) return;
        for (Component line : report(UpdateCheck.check(running, releases, minecraft), runningName, minecraft,
                sender instanceof Player)) sender.sendMessage(line);
    }

    private static boolean stillThere(CommandSender sender) {
        return !(sender instanceof Player player) || player.isOnline();
    }

    /** Off the main thread. */
    private void check() {
        List<UpdateCheck.Release> releases;
        try {
            releases = fetch();
        } catch (IOException | RuntimeException failure) {
            failed(failure.getMessage());
            return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        failureLogged = false;
        UpdateCheck.Outcome outcome = UpdateCheck.check(running, releases, minecraft);
        if (outcome.upToDate() || sameAs(available, outcome)) return;
        available = outcome;
        onMainThread(() -> announce(outcome));
    }

    /** Off the main thread. */
    private List<UpdateCheck.Release> fetch() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "EZvac/" + runningName)
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new IOException("GitHub answered " + response.statusCode());
        List<UpdateCheck.Release> releases = UpdateCheck.parse(response.body());
        recent = new Fetched(releases, System.currentTimeMillis());
        return releases;
    }

    private static boolean sameAs(UpdateCheck.Outcome told, UpdateCheck.Outcome found) {
        return told != null && version(told.newest()).equals(version(found.newest()))
                && version(told.runsHere()).equals(version(found.runsHere()));
    }

    private static String version(UpdateCheck.Release release) {
        return release == null ? "" : release.tag();
    }

    private void failed(String reason) {
        if (failureLogged || !plugin.isEnabled()) return;
        failureLogged = true;
        plugin.getLogger().info("Could not check GitHub for a newer EZvac (" + reason + "); trying again later.");
    }

    private void announce(UpdateCheck.Outcome outcome) {
        for (Component line : report(outcome, runningName, minecraft, false))
            plugin.getLogger().info(PlainTextComponentSerializer.plainText().serialize(line)
                    .replaceFirst("^\\[EZvac] ", ""));
        if (!notifyAdmins) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) tell(player, outcome);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UpdateCheck.Outcome outcome = available;
        if (outcome == null || !notifyAdmins) return;
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) tell(player, outcome);
        }, JOIN_DELAY_TICKS);
    }

    private void tell(Player player, UpdateCheck.Outcome outcome) {
        if (!player.hasPermission("ezvac.admin")) return;
        for (Component line : report(outcome, runningName, minecraft, true)) player.sendMessage(line);
    }

    /**
     * What a check found, as chat lines. Links are clickable for a player;
     * the console gets the address instead.
     */
    static List<Component> report(UpdateCheck.Outcome outcome, String runningName, String minecraft,
                                  boolean clickable) {
        List<Component> lines = new ArrayList<>();
        if (outcome.upToDate()) {
            lines.add(prefixed(Component.text("You're on the latest EZvac, " + runningName + ".",
                    NamedTextColor.GREEN)));
            return lines;
        }
        UpdateCheck.Release newest = outcome.newest();
        if (!outcome.blocked()) {
            lines.add(prefixed(Component.text("EZvac " + named(newest) + " is out (this server runs " + runningName
                            + "), and it works on this server's Minecraft " + minecraft + ". ", NamedTextColor.YELLOW)
                    .append(link("Open the release", newest.url(), clickable))));
            return lines;
        }
        UpdateCheck.Requirement needs = UpdateCheck.requirement(newest.version());
        lines.add(prefixed(Component.text("EZvac " + named(newest) + " is out, but it needs Minecraft "
                        + needs.minecraft() + " or newer, and this server runs " + minecraft
                        + ". Update the server first: ", NamedTextColor.YELLOW)
                .append(link("How to move", needs.guide(), clickable))));
        UpdateCheck.Release here = outcome.runsHere();
        if (here == null)
            lines.add(prefixed(Component.text("Until then, " + runningName
                    + " is the newest EZvac this server can run.", NamedTextColor.GRAY)));
        else
            lines.add(prefixed(Component.text("Until then, " + named(here) + " is the newest that runs here;"
                            + " this server runs " + runningName + ". ", NamedTextColor.GRAY)
                    .append(link("Open the release", here.url(), clickable))));
        return lines;
    }

    private static String named(UpdateCheck.Release release) {
        return release.tag() + (release.pilot() ? " (a pilot)" : "");
    }

    private static Component prefixed(Component message) {
        return Component.text("[EZvac] ", NamedTextColor.GOLD).append(message);
    }

    private static Component link(String label, String url, boolean clickable) {
        if (url == null) return Component.empty();
        if (!clickable) return Component.text(url, NamedTextColor.AQUA);
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url));
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
