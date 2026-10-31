package com.github.blade.hvac;

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
    private boolean salvaged;
    private final Set<UUID> told = new HashSet<>();

    private StartupNotices(Plugin plugin, List<Component> lines, boolean stopped, boolean salvageable) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lines = List.copyOf(lines);
        this.stopped = stopped;
        this.salvageable = salvageable;
    }

    /**
     * The notices after a start that loaded, or null when there's nothing to
     * say.
     *
     * @param recoveredFrom what hvac.yml was recovered from, or null
     * @param setAside      where the unreadable file was kept, or null
     * @param keptAside     records kept aside
     * @param migratedFrom  the older format hvac.yml was upgraded from, or 0
     */
    public static StartupNotices loaded(Plugin plugin, String recoveredFrom, String setAside, int keptAside,
                                        int migratedFrom) {
        List<Component> lines = lines(recoveredFrom, setAside, keptAside, migratedFrom);
        return lines.isEmpty() ? null : new StartupNotices(plugin, lines, false, false);
    }

    static List<Component> lines(String recoveredFrom, String setAside, int keptAside, int migratedFrom) {
        List<Component> lines = new ArrayList<>();
        // Good news first: an upgrade to a newer save format went through (BETA 1.0).
        if (migratedFrom > 0)
            lines.add(prefix().append(Component.text("hvac.yml was upgraded from format " + migratedFrom
                    + " to format " + com.github.blade.hvac.service.HvacRegistry.FORMAT_VERSION + " at this start"
                    + (keptAside == 0 ? ": everything carried over." : "."), NamedTextColor.GREEN))
                    .append(Component.text(" The original is kept as hvac.yml.format" + migratedFrom + ".bak.",
                            NamedTextColor.GRAY)));
        if (recoveredFrom != null) {
            lines.add(prefix().append(Component.text("hvac.yml was unreadable at this start, so it was recovered from "
                    + recoveredFrom + ".", NamedTextColor.GOLD)));
            lines.add(Component.text("Changes made since that copy may be missing. The unreadable file is kept as "
                    + setAside + " in plugins/EZvac.", NamedTextColor.GRAY));
        }
        if (keptAside > 0)
            lines.add(prefix().append(Component.text(keptAside + (keptAside == 1 ? " entry" : " entries")
                    + " in hvac.yml couldn't be loaded and " + (keptAside == 1 ? "is" : "are")
                    + " kept aside, not running. /hvac registry lists " + (keptAside == 1 ? "it" : "them")
                    + "; fix " + (keptAside == 1 ? "it" : "them") + " in hvac.yml.", NamedTextColor.GOLD)));
        if (keptAside > 0)
            lines.add(Component.text("If this is intentional, run " + REPAIR + " to discard "
                    + (keptAside == 1 ? "it" : "them") + " for good.", NamedTextColor.GRAY));
        return lines;
    }

    /**
     * The notice when hvac.yml couldn't be loaded at all: EZvac runs nothing
     * and saves nothing.
     *
     * @param salvageable an unreadable file, which {@link #REPAIR} can salvage; false for one from a newer EZvac
     */
    public static StartupNotices stopped(Plugin plugin, String reason, boolean salvageable) {
        return new StartupNotices(plugin, stoppedLines(reason, salvageable), true, salvageable);
    }

    static List<Component> stoppedLines(String reason, boolean salvageable) {
        List<Component> lines = new ArrayList<>(List.of(
                prefix().append(Component.text("EZvac is stopped: hvac.yml couldn't be loaded, so nothing runs and"
                        + " nothing is saved.", NamedTextColor.RED)),
                Component.text(reason == null || reason.isBlank() ? "See the server log." : reason, NamedTextColor.GRAY),
                Component.text("The server log has the details. Fix or restore plugins/EZvac/hvac.yml, then restart.",
                        NamedTextColor.GRAY)));
        if (salvageable)
            lines.add(Component.text("If this is intentional, run " + REPAIR + " to keep what still reads and drop the"
                    + " broken parts, then restart.", NamedTextColor.GRAY));
        return lines;
    }

    private static Component prefix() {
        return Component.text("[EZvac] ", NamedTextColor.AQUA);
    }

    public boolean stopped() { return stopped; }

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
            sender.sendMessage(Component.text("EZvac isn't running right now. An admin has been told why.",
                    NamedTextColor.YELLOW));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("registry") && args[1].equalsIgnoreCase("repair")) {
            repair(sender, args.length >= 3 && args[2].equalsIgnoreCase("--force"));
            return true;
        }
        lines.forEach(sender::sendMessage);
        return true;
    }

    private void repair(CommandSender sender, boolean force) {
        if (!salvageable) {
            sender.sendMessage(Component.text("hvac.yml was written by a newer EZvac, and that is never forced: it"
                    + " would lose what the newer version stored. Update EZvac instead.", NamedTextColor.RED));
            return;
        }
        if (salvaged) {
            sender.sendMessage(Component.text("hvac.yml was already salvaged. Restart the server to start EZvac.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (!force) {
            sender.sendMessage(Component.text("This keeps every entry of hvac.yml that still reads and drops the"
                    + " broken ones for good. The file as it is now is kept beside it. If this is intentional, run "
                    + REPAIR + ".", NamedTextColor.YELLOW));
            return;
        }
        try {
            com.github.blade.hvac.service.RegistrySalvage.Saved saved =
                    com.github.blade.hvac.service.RegistrySalvage.salvageFile(plugin.getDataFolder());
            salvaged = true;
            List<String> dropped = saved.result().dropped();
            sender.sendMessage(Component.text("Salvaged hvac.yml: kept " + saved.result().kept() + " entries, dropped "
                    + dropped.size() + ". The file as it was is kept as " + saved.original()
                    + ". Restart the server to start EZvac.", NamedTextColor.GREEN));
            if (!dropped.isEmpty())
                sender.sendMessage(Component.text("Dropped: " + String.join(", ", dropped.subList(0,
                        Math.min(10, dropped.size()))) + (dropped.size() > 10 ? ", and more" : ""), NamedTextColor.GRAY));
            plugin.getLogger().warning(sender.getName() + " salvaged hvac.yml with " + REPAIR + ": kept "
                    + saved.result().kept() + " entries, dropped " + dropped + "; the original is "
                    + saved.original());
        } catch (java.io.IOException | RuntimeException failure) {
            sender.sendMessage(Component.text("Couldn't salvage hvac.yml: " + failure.getMessage()
                    + ". Nothing was changed.", NamedTextColor.RED));
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Couldn't salvage hvac.yml", failure);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN)) return List.of();
        if (args.length == 1) return List.of("registry");
        if (args.length == 2 && args[0].equalsIgnoreCase("registry")) return List.of("repair");
        if (args.length == 3 && args[1].equalsIgnoreCase("repair")) return List.of("--force");
        return List.of();
    }
}
