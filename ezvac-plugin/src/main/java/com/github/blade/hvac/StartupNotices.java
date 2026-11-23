package com.github.blade.hvac;

import com.github.blade.hvac.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.github.blade.hvac.lang.Lang.arg;

/**
 * What went wrong with hvac.yml at this start, told to admins in game (BETA
 * 1.0), not only in the server log: a recovery from a backup, records kept
 * aside, or EZvac stopped because nothing readable was left. Each admin hears
 * it once a run, a moment after joining. Stopped, it also answers /hvac, so
 * EZvac never just looks uninstalled.
 */
public final class StartupNotices implements Listener, CommandExecutor, TabCompleter {
    private static final long JOIN_DELAY_TICKS = 40L;
    private static final String ADMIN = "ezvac.admin";

    /** What admins run to accept losing what's broken, said with every warning that has a way out. */
    public static final String REPAIR = "/hvac registry repair --force";

    private final Plugin plugin;
    private final List<Component> lines;
    private final boolean stopped;
    /** Stopped on an unreadable file, which --force can salvage; never one from a newer EZvac. */
    private final boolean salvageable;
    /** Salvaged or restored this run: the next start runs from it. */
    private boolean fixed;
    private final String reason;
    /** /hvac quicksettings while stopped: the recovery menu; set by the plugin. */
    private java.util.function.Consumer<Player> recoveryMenu;
    private final Set<UUID> told = new HashSet<>();

    private StartupNotices(Plugin plugin, List<Component> lines, boolean stopped, boolean salvageable, String reason) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lines = List.copyOf(lines);
        this.stopped = stopped;
        this.salvageable = salvageable;
        this.reason = reason == null ? "" : reason;
    }

    /**
     * The notices after a start that loaded, or null when there's nothing to
     * say.
     *
     * @param recoveredFrom what hvac.yml was recovered from, or null
     * @param setAside      where the unreadable file was kept, or null
     * @param keptAside     records kept aside
     * @param migratedFrom  the older format hvac.yml was upgraded from, or 0
     * @param sizingOff     building size started off on this upgrade
     * @param backupsOn     automatic backups went on at this upgrade (BETA 1.0 Pilot 2)
     * @param needVents     thermostats with units but no vent, which no longer start (BETA 1.0 Pilot 2)
     */
    public static StartupNotices loaded(Plugin plugin, String recoveredFrom, String setAside, int keptAside,
                                        int migratedFrom, boolean sizingOff, boolean backupsOn, int needVents) {
        List<Component> lines = lines(recoveredFrom, setAside, keptAside, migratedFrom);
        if (sizingOff) lines = withSizingOff(lines);
        lines = withPilotTwo(lines, backupsOn, needVents);
        return lines.isEmpty() ? null : new StartupNotices(plugin, lines, false, false, null);
    }

    /** Building size is new and starts off on this upgraded server (BETA 1.0): told once, as news, not a warning. */
    static List<Component> withSizingOff(List<Component> lines) {
        List<Component> out = new ArrayList<>(lines);
        out.add(prefix().append(Lang.message("notice.sizing-off")));
        out.add(Lang.message("notice.sizing-off-how"));
        return out;
    }

    /**
     * What changed for this server with Pilot 2, told once: automatic backups
     * are on, and thermostats with units but no vent no longer start.
     */
    static List<Component> withPilotTwo(List<Component> lines, boolean backupsOn, int needVents) {
        List<Component> out = new ArrayList<>(lines);
        if (backupsOn)
            out.add(prefix().append(Lang.message("notice.backups-on")));
        if (needVents > 0)
            out.add(prefix().append(Lang.message(needVents == 1 ? "notice.need-vents.one" : "notice.need-vents.many",
                    arg("count", needVents))));
        return out;
    }

    static List<Component> lines(String recoveredFrom, String setAside, int keptAside, int migratedFrom) {
        List<Component> lines = new ArrayList<>();
        // Good news first: an upgrade to a newer save format went through (BETA 1.0).
        if (migratedFrom > 0)
            lines.add(prefix().append(Lang.message(keptAside == 0 ? "notice.upgraded-all" : "notice.upgraded",
                    arg("from", migratedFrom), arg("to", com.github.blade.hvac.service.HvacRegistry.FORMAT_VERSION))));
        if (recoveredFrom != null) {
            lines.add(prefix().append(Lang.message("notice.recovered", arg("from", recoveredFrom))));
            lines.add(Lang.message("notice.recovered-detail", arg("aside", setAside)));
        }
        if (keptAside > 0)
            lines.add(prefix().append(Lang.message(keptAside == 1 ? "notice.kept-aside.one" : "notice.kept-aside.many",
                    arg("count", keptAside))));
        if (keptAside > 0)
            lines.add(Lang.message(keptAside == 1 ? "notice.kept-aside.repair-one" : "notice.kept-aside.repair-many",
                    arg("command", REPAIR)));
        return lines;
    }

    /**
     * The notice when hvac.yml couldn't be loaded at all: EZvac runs nothing
     * and saves nothing.
     *
     * @param salvageable an unreadable file, which {@link #REPAIR} can salvage; false for one from a newer EZvac
     */
    public static StartupNotices stopped(Plugin plugin, String reason, boolean salvageable) {
        return new StartupNotices(plugin, stoppedLines(reason, salvageable), true, salvageable, reason);
    }

    static List<Component> stoppedLines(String reason, boolean salvageable) {
        List<Component> lines = new ArrayList<>(List.of(
                prefix().append(Lang.message("notice.stopped")),
                reason == null || reason.isBlank() ? Lang.message("notice.stopped-see-log")
                        : Component.text(reason, NamedTextColor.GRAY),
                Lang.message("notice.stopped-fix")));
        if (salvageable)
            lines.add(Lang.message("notice.stopped-repair", arg("command", REPAIR)));
        lines.add(Lang.message("notice.stopped-recovery"));
        return lines;
    }

    private static Component prefix() {
        return Lang.message("notice.prefix");
    }

    public boolean stopped() { return stopped; }
    public boolean salvageable() { return salvageable; }
    /** Salvaged or restored this run; the next start runs from it. */
    public boolean fixed() { return fixed; }
    /** Why EZvac stopped, as the registry said. */
    public String reason() { return reason; }
    public void setRecoveryMenu(java.util.function.Consumer<Player> menu) { this.recoveryMenu = menu; }

    /** Tells the admins already online, as after a /reload. */
    public void tellOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) tell(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) tell(player);
        }, JOIN_DELAY_TICKS);
    }

    private void tell(Player player) {
        if (!player.hasPermission(ADMIN) || !told.add(player.getUniqueId())) return;
        lines.forEach(player::sendMessage);
    }

    /**
     * Stopped, every /hvac says why: in full to admins and the console,
     * briefly to anyone else. {@link #REPAIR} salvages an unreadable file.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage(Lang.message("notice.not-running"));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("registry") && args[1].equalsIgnoreCase("repair")) {
            repair(sender, args.length >= 3 && args[2].equalsIgnoreCase("--force"));
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("quicksettings") || args[0].equalsIgnoreCase("qs"))
                && sender instanceof Player player && recoveryMenu != null) {
            recoveryMenu.accept(player);
            return true;
        }
        lines.forEach(sender::sendMessage);
        return true;
    }

    private void repair(CommandSender sender, boolean force) {
        if (!salvageable) {
            sender.sendMessage(Lang.message("notice.repair.newer"));
            return;
        }
        if (fixed) {
            sender.sendMessage(Lang.message("notice.repair.already-fixed"));
            return;
        }
        if (!force) {
            sender.sendMessage(Lang.message("notice.repair.confirm", arg("command", REPAIR)));
            return;
        }
        salvage(sender);
    }

    /**
     * Salvages hvac.yml now: keeps every entry that still reads. For
     * {@link #REPAIR} and the recovery menu.
     *
     * @return whether it worked
     */
    public boolean salvage(CommandSender sender) {
        if (!salvageable || fixed) return false;
        try {
            com.github.blade.hvac.service.RegistrySalvage.Saved saved =
                    com.github.blade.hvac.service.RegistrySalvage.salvageFile(plugin.getDataFolder());
            fixed = true;
            List<String> dropped = saved.result().dropped();
            sender.sendMessage(Lang.message("notice.salvaged", arg("kept", saved.result().kept()),
                    arg("dropped", dropped.size()), arg("original", saved.original())));
            if (!dropped.isEmpty())
                sender.sendMessage(Lang.message(dropped.size() > 10 ? "notice.dropped-more" : "notice.dropped",
                        arg("entries", String.join(", ", dropped.subList(0, Math.min(10, dropped.size()))))));
            plugin.getLogger().warning(sender.getName() + " salvaged hvac.yml: kept "
                    + saved.result().kept() + " entries, dropped " + dropped + "; the original is "
                    + saved.original());
            return true;
        } catch (java.io.IOException | RuntimeException failure) {
            sender.sendMessage(Lang.message("notice.salvage-failed", arg("reason", String.valueOf(failure.getMessage()))));
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Couldn't salvage hvac.yml", failure);
            return false;
        }
    }

    /**
     * Restores a backup from plugins/EZvac/backups over hvac.yml, for the
     * recovery menu. Only one this EZvac can read.
     *
     * @return whether it worked
     */
    public boolean restore(CommandSender sender, String name) {
        if (fixed) return false;
        try {
            String kept = com.github.blade.hvac.service.RegistrySalvage.restore(plugin.getDataFolder(), name);
            fixed = true;
            sender.sendMessage(Lang.message(kept == null ? "notice.restored" : "notice.restored-kept",
                    arg("name", name), arg("kept", String.valueOf(kept))));
            plugin.getLogger().warning(sender.getName() + " restored hvac.yml from backups/" + name
                    + (kept == null ? "" : "; the file before is " + kept));
            return true;
        } catch (java.io.IOException | RuntimeException failure) {
            sender.sendMessage(Lang.message("notice.restore-failed", arg("reason", String.valueOf(failure.getMessage()))));
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Couldn't restore hvac.yml from " + name, failure);
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN)) return List.of();
        if (args.length == 1) return List.of("quicksettings", "registry");
        if (args.length == 2 && args[0].equalsIgnoreCase("registry")) return List.of("repair");
        if (args.length == 3 && args[1].equalsIgnoreCase("repair")) return List.of("--force");
        return List.of();
    }
}
