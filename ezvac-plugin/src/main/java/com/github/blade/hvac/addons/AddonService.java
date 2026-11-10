package com.github.blade.hvac.addons;

import com.github.blade.hvac.api.addon.Addon;
import com.github.blade.hvac.api.addon.AddonSwitches;
import com.github.blade.hvac.update.UpdateCheck;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/**
 * EZvac's official addons on this server (BETA 1.0 Pilot 4): which are
 * installed, switching them and their parts on and off while the server
 * runs, the store's list of the rest, and whether GitHub has a newer release
 * of one. Nothing is ever downloaded or installed: the store only gives
 * links. Main thread, but for the fetches, which only hand results back.
 */
public final class AddonService implements Listener {
    /** The list of official addons, kept in the organisation's profile repository. */
    static final String CATALOGUE = "https://raw.githubusercontent.com/EZvac-Plugin/.github/main/addons.json";
    private static final long FIRST_CHECK_TICKS = 20L * 20;
    private static final long PERIOD_TICKS = 20L * 60 * 60 * 6;
    private static final long JOIN_DELAY_TICKS = 60L;
    private static final String OFF = "addons.disabled", PARTS_OFF = "addons.parts-disabled",
            PARTS_ON = "addons.parts-enabled";

    private record Registered(Plugin plugin, Addon addon) {}

    /** One of an addon's parts, and whether it is on. */
    public record PartView(String id, String name, String description, boolean on) {}

    /**
     * An official addon found on this server.
     *
     * @param version    as its plugin says
     * @param running    whether its plugin is enabled at all
     * @param switchable whether it registered with EZvac, so it can be switched here
     * @param active     switched on (always true for one that can't be switched)
     * @param update     the newer release GitHub has, or null
     */
    public record Installed(AddonCatalogue.Entry entry, String version, boolean running, boolean switchable,
                            boolean active, List<PartView> parts, String update) {}

    private final Plugin plugin;
    private final FileConfiguration config;
    private final Runnable saveConfig;
    private final String runningName;
    private final String minecraft;
    private final BooleanSupplier checking;
    private final BooleanSupplier notifyAdmins;
    private final BooleanSupplier pilots;
    private final Map<String, Registered> registered = new LinkedHashMap<>();
    private final TreeSet<String> off = new TreeSet<>();
    private final TreeSet<String> partsOff = new TreeSet<>();
    /** Parts a server switched on: what counts for a part that starts off. */
    private final TreeSet<String> partsOn = new TreeSet<>();
    private volatile List<AddonCatalogue.Entry> catalogue;
    /** The newer release found for each addon, by its id. */
    private final Map<String, AddonVersion.Release> newer = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private BukkitTask task;
    private volatile boolean failureLogged;

    /**
     * @param checking     whether background update checks are on (updates.check)
     * @param notifyAdmins whether admins are told on join (updates.notify-admins)
     * @param pilots       whether this server hears about prereleases (the Pilot channel)
     */
    public AddonService(Plugin plugin, FileConfiguration config, Runnable saveConfig, String runningName,
                        String minecraft, BooleanSupplier checking, BooleanSupplier notifyAdmins,
                        BooleanSupplier pilots) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.saveConfig = Objects.requireNonNull(saveConfig, "saveConfig");
        this.runningName = runningName;
        this.minecraft = minecraft;
        this.checking = checking;
        this.notifyAdmins = notifyAdmins;
        this.pilots = pilots;
        off.addAll(config.getStringList(OFF));
        partsOff.addAll(config.getStringList(PARTS_OFF));
        partsOn.addAll(config.getStringList(PARTS_ON));
        this.catalogue = builtIn();
    }

    /** The list EZvac ships with. */
    private List<AddonCatalogue.Entry> builtIn() {
        try (InputStream input = plugin.getResource("addons.json")) {
            if (input == null) return List.of();
            return List.copyOf(AddonCatalogue.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled list of addons", failure);
            return List.of();
        }
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (checking.getAsBoolean()) refresh(null);
        }, FIRST_CHECK_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        http.shutdownNow();
        registered.clear();
    }

    /** What an addon registering now should do: its own switch, and each part's. */
    public AddonSwitches register(Plugin owner, Addon addon) {
        Objects.requireNonNull(owner, "plugin");
        Objects.requireNonNull(addon, "addon");
        String name = owner.getName();
        // Only an official addon can be switched off here; anything else always runs.
        if (official(owner) == null) return AddonSwitches.ALL_ON;
        registered.put(name, new Registered(owner, addon));
        return new AddonSwitches() {
            @Override
            public boolean active() { return !off.contains(name); }

            @Override
            public boolean partOn(String id) {
                for (Addon.Part part : partsOf(registered.get(name)))
                    if (part.id().equals(id)) return on(name, part);
                return !partsOff.contains(name + "/" + id);
            }
        };
    }

    /** Whether a part is on: as the server switched it, or as it starts where it never did. */
    private boolean on(String pluginName, Addon.Part part) {
        String key = pluginName + "/" + part.id();
        return !partsOff.contains(key) && (part.startsOn() || partsOn.contains(key));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisable(PluginDisableEvent event) {
        registered.remove(event.getPlugin().getName());
    }

    /** The catalogue's entry for a plugin, when its name and main class are an official addon's. */
    private AddonCatalogue.Entry official(Plugin candidate) {
        for (AddonCatalogue.Entry entry : catalogue)
            if (entry.plugin().equals(candidate.getName()) && entry.mainClass().equals(candidate.getClass().getName()))
                return entry;
        return null;
    }

    /** The official addons installed here, in the catalogue's order. */
    public List<Installed> installed() {
        List<Installed> found = new ArrayList<>();
        for (AddonCatalogue.Entry entry : catalogue) {
            Plugin there = plugin.getServer().getPluginManager().getPlugin(entry.plugin());
            if (there == null || !entry.mainClass().equals(there.getClass().getName())) continue;
            Registered addon = there.isEnabled() ? registered.get(entry.plugin()) : null;
            List<PartView> parts = new ArrayList<>();
            if (addon != null)
                for (Addon.Part part : partsOf(addon))
                    parts.add(new PartView(part.id(), AddonCatalogue.clean(part.name(), 40),
                            AddonCatalogue.clean(part.description(), 120), on(entry.plugin(), part)));
            AddonVersion.Release release = newer.get(entry.id());
            String version = there.getPluginMeta().getVersion();
            // A release found before the addon was updated on this server is no longer news.
            AddonVersion running = AddonVersion.parse(version);
            String update = release == null || running == null || release.version().compareTo(running) <= 0
                    ? null : release.tag();
            found.add(new Installed(entry, version, there.isEnabled(), addon != null,
                    addon == null || !off.contains(entry.plugin()), parts, update));
        }
        return found;
    }

    private List<Addon.Part> partsOf(Registered addon) {
        if (addon == null) return List.of();
        try {
            List<Addon.Part> parts = addon.addon().parts();
            return parts == null ? List.of() : parts;
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, addon.plugin().getName() + " could not list its parts", failure);
            return List.of();
        }
    }

    /** The official addons that aren't installed here: the store. */
    public List<AddonCatalogue.Entry> store() {
        List<AddonCatalogue.Entry> missing = new ArrayList<>();
        for (AddonCatalogue.Entry entry : catalogue) {
            Plugin there = plugin.getServer().getPluginManager().getPlugin(entry.plugin());
            if (there == null || !entry.mainClass().equals(there.getClass().getName())) missing.add(entry);
        }
        return missing;
    }

    /** The catalogue's entry with this id, or null. */
    public AddonCatalogue.Entry entry(String id) {
        for (AddonCatalogue.Entry entry : catalogue) if (entry.id().equals(id)) return entry;
        return null;
    }

    /** Whether an addon runs on this server's Minecraft. */
    public boolean runsHere(AddonCatalogue.Entry entry) {
        return entry.minecraft().isEmpty() || UpdateCheck.compareMinecraft(minecraft, entry.minecraft()) >= 0;
    }

    /**
     * Switches an addon on or off and remembers it. False when it can't be
     * switched here (it isn't registered), or when it failed, which is logged.
     */
    public boolean setActive(String pluginName, boolean on) {
        Registered addon = registered.get(pluginName);
        if (addon == null) return false;
        try {
            addon.addon().setActive(on);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, pluginName + " could not be switched " + (on ? "on" : "off"), failure);
            return false;
        }
        if (on) off.remove(pluginName);
        else off.add(pluginName);
        save();
        return true;
    }

    /** Switches one part of an addon on or off and remembers it. */
    public boolean setPart(String pluginName, String partId, boolean on) {
        Registered addon = registered.get(pluginName);
        if (addon == null) return false;
        for (Addon.Part part : partsOf(addon)) {
            if (!part.id().equals(partId)) continue;
            try {
                part.switcher().accept(on);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, pluginName + "'s " + partId + " could not be switched "
                        + (on ? "on" : "off"), failure);
                return false;
            }
            // The choice is kept either way, so it holds whatever the part starts as.
            String key = pluginName + "/" + partId;
            (on ? partsOff : partsOn).remove(key);
            (on ? partsOn : partsOff).add(key);
            save();
            return true;
        }
        return false;
    }

    private void save() {
        config.set(OFF, new ArrayList<>(off));
        config.set(PARTS_OFF, new ArrayList<>(partsOff));
        config.set(PARTS_ON, new ArrayList<>(partsOn));
        saveConfig.run();
    }

    /** The addons with a newer release, as {@code EZclimates 0.2.0}. */
    public List<String> updates() {
        List<String> lines = new ArrayList<>();
        for (Installed addon : installed())
            if (addon.update() != null) lines.add(addon.entry().name() + " " + addon.update());
        return lines;
    }

    /**
     * Reads the list of addons and each installed one's releases from
     * GitHub, off the main thread; {@code whenDone} then runs on it, with
     * whether GitHub answered.
     */
    public void refresh(java.util.function.Consumer<Boolean> whenDone) {
        Map<AddonCatalogue.Entry, AddonVersion> running = new LinkedHashMap<>();
        for (Installed addon : installed()) {
            AddonVersion version = AddonVersion.parse(addon.version());
            if (version != null) running.put(addon.entry(), version);
        }
        boolean prereleases = pilots.getAsBoolean();
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean answered = fetch(running, prereleases);
                if (whenDone != null && plugin.isEnabled())
                    plugin.getServer().getScheduler().runTask(plugin, () -> whenDone.accept(answered));
            });
        } catch (IllegalStateException | org.bukkit.plugin.IllegalPluginAccessException disabling) {
            // Shutting down: the next start looks again.
        }
    }

    /** Off the main thread. */
    private boolean fetch(Map<AddonCatalogue.Entry, AddonVersion> running, boolean prereleases) {
        try {
            String list = get(CATALOGUE, "text/plain");
            if (list != null) {
                List<AddonCatalogue.Entry> read = AddonCatalogue.parse(list);
                if (!read.isEmpty()) catalogue = List.copyOf(read);
            }
            for (Map.Entry<AddonCatalogue.Entry, AddonVersion> addon : running.entrySet()) {
                String releases = get(addon.getKey().releasesApi(), "application/vnd.github+json");
                if (releases == null) continue;
                AddonVersion.Release release = AddonVersion.newer(releases, addon.getValue(), prereleases);
                if (release == null) newer.remove(addon.getKey().id());
                else newer.put(addon.getKey().id(), release);
            }
            failureLogged = false;
            return true;
        } catch (IOException | RuntimeException failure) {
            if (!failureLogged && plugin.isEnabled()) {
                failureLogged = true;
                plugin.getLogger().info("Could not check GitHub for addons (" + failure.getMessage()
                        + "); trying again later.");
            }
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** A page's text, or null where there is none (the list isn't published yet, an addon has no releases). */
    private String get(String url, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Accept", accept).header("User-Agent", "EZvac/" + runningName).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) throw new IOException("GitHub answered " + response.statusCode());
        return response.body();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!notifyAdmins.getAsBoolean() || newer.isEmpty()) return;
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !player.hasPermission("ezvac.admin")) return;
            List<String> updates = updates();
            if (updates.isEmpty()) return;
            player.sendMessage(Component.text("[EZvac] ", NamedTextColor.GOLD)
                    .append(Component.text((updates.size() == 1 ? "An addon has an update: " : "Addons have updates: ")
                            + String.join(", ", updates) + ". ", NamedTextColor.YELLOW))
                    .append(Component.text("/hvac addons", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.runCommand("/hvac addons"))));
        }, JOIN_DELAY_TICKS);
    }
}
