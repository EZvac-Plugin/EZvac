package com.github.blade.hvac.gui;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.addons.AddonCatalogue;
import com.github.blade.hvac.addons.AddonService;
import com.github.blade.hvac.config.HudSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.gui.SetupSteps.HudChoice;
import com.github.blade.hvac.gui.SetupSteps.Step;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The first-start setup (BETA 1.0 Pilot 10). On a new install, an admin who
 * joins is asked (in chat, never a menu thrown open) to choose EZvac's key
 * settings: temperatures, wind speeds, the HUD for new players and its
 * title, updates, backups and anonymous statistics, then meets the official
 * addons and can go straight to building a first system. Every choice is
 * applied and saved the moment it's clicked; all of them stay in quick
 * settings. {@code /hvac setup} runs it again any time.
 */
public final class SetupWizard implements Listener {
    private static final String ADMIN = "ezvac.admin";
    static final String GETTING_STARTED = "https://github.com/EZvac-Plugin/EZvac/blob/main/docs/getting-started.md";
    private static final long JOIN_DELAY_TICKS = 60L;
    static final int INFO = 4, BACK = 27, SKIP = 31, NEXT = 35, RENAME = 22, GUIDE = 13, FIRST_SYSTEM = 12, FINISH = 14;
    private static final int[][] CHOICE_SLOTS = {{}, {13}, {12, 14}, {11, 13, 15}, {10, 12, 14, 16}};
    private static final int[] ADDON_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final HvacPlugin plugin;
    /** Admins typing a name for the HUD's title, and the page to come back to. */
    private final Map<UUID, Step> naming = new ConcurrentHashMap<>();

    public SetupWizard(HvacPlugin plugin) {
        this.plugin = plugin;
    }

    /** The open setup menu: which page it shows. */
    private static final class Menu implements InventoryHolder {
        final Step step;
        Inventory inventory;

        Menu(Step step) {
            this.step = step;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    // ---- the prompt ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.setupDone() || !player.hasPermission(ADMIN)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !plugin.setupDone()) prompt(player);
        }, JOIN_DELAY_TICKS);
    }

    /** "EZvac is new on this server. Set it up in a minute? [Set up] [Later] [Don't ask again]" */
    public void prompt(Player player) {
        boolean partway = plugin.getConfig().getBoolean("setup.started", false);
        player.sendMessage(Component.text(partway ? "You left EZvac's setup partway. " : "EZvac is new on this server. ",
                        NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                .append(Component.text(partway ? "Carry on? Every answer so far is kept." : "Set it up in a minute?",
                        NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)));
        player.sendMessage(button("[Set up]", NamedTextColor.GREEN, "/hvac setup", "Opens a short menu")
                .append(Component.text("  "))
                .append(Component.text("[Later]", NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(Component.text("Asks again when you next join"))))
                .append(Component.text("  "))
                .append(button("[Don't ask again]", NamedTextColor.RED, "/hvac setup skip",
                        "Every setting stays in /hvac quicksettings")));
    }

    private static Component button(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    // ---- the menu ----

    public void open(Player player) {
        open(player, Step.WELCOME);
    }

    public void open(Player player, Step step) {
        if (!plugin.getConfig().getBoolean("setup.started", false) && !plugin.setupDone()) {
            plugin.getConfig().set("setup.started", true);
            plugin.saveConfig();
        }
        Menu menu = new Menu(step);
        menu.inventory = Bukkit.createInventory(menu, 36, Component.text(step.menuTitle() + ": " + step.title()));
        draw(menu);
        player.openInventory(menu.inventory);
    }

    /** Marks the setup done without changing anything (/hvac setup skip, or Skip setup). */
    public void skip(Player player) {
        plugin.setSetupDone(true);
        player.sendMessage(Component.text("Setup skipped. Every setting is in /hvac quicksettings, and /hvac setup"
                + " runs it again any time.", NamedTextColor.GRAY));
    }

    private void finish(Player player) {
        plugin.setSetupDone(true);
        player.closeInventory();
        player.sendMessage(Component.text("EZvac is set up. ", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .append(Component.text("Change anything later in /hvac quicksettings, or run /hvac setup again.",
                        NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)));
        plugin.getLogger().info(player.getName() + " finished the setup.");
    }

    private void draw(Menu menu) {
        Inventory inventory = menu.inventory;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        if (menu.step.previous() != null)
            inventory.setItem(BACK, item(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW),
                    List.of(gray(menu.step.previous().title())), false));
        if (menu.step != Step.DONE)
            inventory.setItem(SKIP, item(Material.BARRIER, Component.text("Skip setup", NamedTextColor.RED),
                    List.of(gray("Keeps everything as it is now"), gray("/hvac setup runs it again")), false));
        inventory.setItem(NEXT, menu.step == Step.DONE
                ? item(Material.LIME_CONCRETE, Component.text("Finish", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(gray("Close the setup")), false)
                : item(Material.LIME_DYE, Component.text("Next", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(gray(menu.step.next().title())), false));
        switch (menu.step) {
            case WELCOME -> drawWelcome(inventory);
            case TEMPERATURES -> drawTemperatures(inventory);
            case WIND -> drawWind(inventory);
            case HUD -> drawHud(inventory);
            case UPDATES -> drawUpdates(inventory);
            case BACKUPS -> drawBackups(inventory);
            case STATISTICS -> drawStatistics(inventory);
            case ADDONS -> drawAddons(inventory);
            case DONE -> drawDone(inventory);
        }
    }

    private void info(Inventory inventory, Material icon, String title, String... lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) lore.add(gray(line));
        inventory.setItem(INFO, item(icon, Component.text(title, NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore, false));
    }

    private void choices(Inventory inventory, List<ItemStack> items) {
        int[] slots = CHOICE_SLOTS[items.size()];
        for (int i = 0; i < items.size(); i++) inventory.setItem(slots[i], items.get(i));
    }

    private static ItemStack choice(Material icon, String label, String what, boolean chosen) {
        List<Component> lore = new ArrayList<>();
        if (what != null) lore.add(gray(what));
        lore.add(chosen ? Component.text("Chosen", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                : Component.text("Click to choose", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        return item(icon, Component.text((chosen ? "● " : "") + label, chosen ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                lore, chosen);
    }

    private void drawWelcome(Inventory inventory) {
        info(inventory, Material.NETHER_STAR, "Welcome to EZvac",
                "Heating, cooling and the climate,", "built from ordinary blocks.",
                "A few questions set it up for", "this server; each can be changed",
                "later in /hvac quicksettings.");
        inventory.setItem(GUIDE, item(Material.WRITTEN_BOOK, Component.text("Getting started", NamedTextColor.GOLD),
                List.of(gray("A first working system, step by step"),
                        Component.text("Click for the link in chat", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)), false));
    }

    private void drawTemperatures(Inventory inventory) {
        TemperatureUnit now = plugin.settings().units();
        info(inventory, Material.REDSTONE_TORCH, "Temperatures",
                "How every temperature shows:", "screens, the HUD, menus and chat.");
        choices(inventory, List.of(
                choice(Material.ORANGE_DYE, "Fahrenheit (°F)", "72°F is a comfortable room", now == TemperatureUnit.F),
                choice(Material.LIGHT_BLUE_DYE, "Celsius (°C)", "22°C is a comfortable room", now == TemperatureUnit.C)));
    }

    private WindSettings.Units windNow() {
        var wind = plugin.outdoor().wind();
        return wind == null ? WindSettings.Units.AUTO : wind.settings().units();
    }

    private void drawWind(Inventory inventory) {
        WindSettings.Units now = windNow();
        info(inventory, Material.FEATHER, "Wind speeds", "How the HUD, sensors and forecasts", "give the wind's speed.");
        choices(inventory, List.of(
                choice(Material.COMPASS, SetupSteps.windLabel(WindSettings.Units.AUTO), "mph with °F, km/h with °C",
                        now == WindSettings.Units.AUTO),
                choice(Material.WHITE_DYE, "m/s", "Metres a second", now == WindSettings.Units.MS),
                choice(Material.WHITE_DYE, "km/h", "Kilometres an hour", now == WindSettings.Units.KMH),
                choice(Material.WHITE_DYE, "mph", "Miles an hour", now == WindSettings.Units.MPH)));
    }

    private HudChoice hudNow() {
        return HudChoice.of(plugin.getConfig().getBoolean("hud.show-to-new-players", false),
                plugin.getConfig().getString("hud.style", "sidebar"));
    }

    private void drawHud(Inventory inventory) {
        HudChoice now = hudNow();
        info(inventory, Material.ITEM_FRAME, "The HUD for new players",
                "The climate where a player stands:", "temperature, feels-like, humidity,",
                "the air and the weather.", "A player's own /hvac hud always wins.");
        Material[] icons = {Material.GRAY_DYE, Material.OAK_SIGN, Material.NAME_TAG, Material.END_CRYSTAL};
        List<ItemStack> items = new ArrayList<>();
        for (HudChoice choice : HudChoice.values())
            items.add(choice(icons[choice.ordinal()], choice.label(), choice.what(), now == choice));
        choices(inventory, items);
        String title = PlainTextComponentSerializer.plainText().serialize(plugin.hud().format().title());
        inventory.setItem(RENAME, item(Material.ANVIL, Component.text("Rename the title", NamedTextColor.GOLD),
                List.of(gray("Now: " + title), gray("Climate - your server's name"),
                        Component.text("Click, then type it in chat", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)), false));
    }

    private void drawUpdates(Inventory inventory) {
        String now = plugin.updates().channelSetting();
        info(inventory, Material.CLOCK, "Updates",
                "EZvac checks GitHub for a newer", "release and tells admins. Which", "releases should it tell you about?");
        choices(inventory, List.of(
                choice(Material.EMERALD, "Stable", "Full releases and their hotfixes", now.equals("stable")),
                choice(Material.AMETHYST_SHARD, "Pilot", "Pilots too: test builds, earlier", now.equals("pilot")),
                choice(Material.CLOCK, SetupSteps.channelLabel("auto"), "Pilot on a pilot, else Stable", now.equals("auto"))));
    }

    private void drawBackups(Inventory inventory) {
        int now = plugin.backups().intervalHours();
        info(inventory, Material.CHEST, "Backups",
                "A copy of every device, kept in", "plugins/EZvac/backups, the newest", "10 kept.");
        List<ItemStack> items = new ArrayList<>();
        for (int hours : SetupSteps.BACKUP_HOURS)
            items.add(choice(hours == 0 ? Material.GRAY_DYE : Material.CHEST, SetupSteps.backupLabel(hours),
                    hours == 24 ? "Recommended" : null, now == hours));
        choices(inventory, items);
    }

    private void drawStatistics(Inventory inventory) {
        boolean on = plugin.usageStatsOn();
        info(inventory, Material.COMPASS, "Anonymous statistics",
                "How many servers run EZvac, and on", "which versions, tells us what to", "test and support, and what to keep.",
                "Sent to bstats.org: a random id,", "players online, server, Java and",
                "EZvac versions, a few settings.", "Nothing names a server or a player.",
                "Each part: /hvac quicksettings.");
        choices(inventory, List.of(
                choice(Material.LIME_DYE, "On", "Help EZvac grow", on),
                choice(Material.GRAY_DYE, "Off", "Send nothing", !on)));
    }

    private void drawAddons(Inventory inventory) {
        info(inventory, Material.BOOKSHELF, "Official addons",
                "Plugins made alongside EZvac.", "Click one for its link in chat.", "EZvac never downloads anything.");
        AddonService addons = plugin.addons();
        List<AddonCatalogue.Entry> installed = new ArrayList<>();
        for (AddonService.Installed one : addons.installed()) installed.add(one.entry());
        List<AddonCatalogue.Entry> all = new ArrayList<>(installed);
        all.addAll(addons.store());
        for (int i = 0; i < all.size() && i < ADDON_SLOTS.length; i++) {
            AddonCatalogue.Entry entry = all.get(i);
            Material icon = Material.matchMaterial(entry.icon());
            List<Component> lore = new ArrayList<>(wrap(entry.description(), 34, 4));
            lore.add(installed.contains(entry) ? Component.text("Installed", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                    : Component.text("Click for the link", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            inventory.setItem(ADDON_SLOTS[i], item(icon == null ? Material.PAPER : icon,
                    Component.text(entry.name(), NamedTextColor.AQUA), lore, false));
        }
    }

    private void drawDone(Inventory inventory) {
        var wind = windNow();
        List<String> lines = new ArrayList<>(SetupSteps.summary(plugin.settings().units(), wind, hudNow(),
                plugin.updates().channelSetting(), plugin.backups().intervalHours(), plugin.usageStatsOn()));
        lines.add("All in /hvac quicksettings.");
        info(inventory, Material.NETHER_STAR, "EZvac is set up", lines.toArray(String[]::new));
        inventory.setItem(FIRST_SYSTEM, item(Material.STICK, Component.text("Build your first system", NamedTextColor.GOLD),
                List.of(gray("Gives you the link tool and the"), gray("step-by-step guide's link")), false));
        inventory.setItem(FINISH, item(Material.LIME_CONCRETE, Component.text("Finish", NamedTextColor.GREEN),
                List.of(gray("Close the setup")), false));
    }

    // ---- clicks ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;
        if (!player.hasPermission(ADMIN)) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK && menu.step.previous() != null) {
            sound(player, true);
            open(player, menu.step.previous());
            return;
        }
        if (slot == SKIP && menu.step != Step.DONE) {
            sound(player, true);
            player.closeInventory();
            skip(player);
            return;
        }
        if (slot == NEXT) {
            sound(player, true);
            if (menu.step == Step.DONE) finish(player);
            else open(player, menu.step.next());
            return;
        }
        if (clickPage(player, menu, slot)) {
            sound(player, true);
            if (player.getOpenInventory().getTopInventory() == menu.inventory) draw(menu);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    /** True when the click changed something on this page. */
    private boolean clickPage(Player player, Menu menu, int slot) {
        int index = indexOf(CHOICE_SLOTS[choiceCount(menu.step)], slot);
        switch (menu.step) {
            case WELCOME -> {
                if (slot != GUIDE) return false;
                link(player, "Getting started", GETTING_STARTED);
                return true;
            }
            case TEMPERATURES -> {
                if (index < 0) return false;
                TemperatureUnit unit = index == 0 ? TemperatureUnit.F : TemperatureUnit.C;
                plugin.switches().setUnits(unit);
                changed(player, "set temperatures to °" + unit.name());
                plugin.requestImmediateSync();
                return true;
            }
            case WIND -> {
                if (index < 0 || plugin.outdoor().wind() == null) return false;
                WindSettings.Units units = WindSettings.Units.values()[index];
                plugin.switches().setWindUnits(units);
                changed(player, "set wind speeds to " + SetupSteps.windLabel(units));
                return true;
            }
            case HUD -> {
                if (slot == RENAME) {
                    naming.put(player.getUniqueId(), Step.HUD);
                    player.closeInventory();
                    player.sendMessage(Component.text("Type the name for the HUD's title in chat", NamedTextColor.AQUA)
                            .append(Component.text(" (Climate - name; up to 24 characters, \"cancel\" to keep it).",
                                    NamedTextColor.GRAY)));
                    return false;
                }
                if (index < 0) return false;
                HudChoice choice = HudChoice.values()[index];
                setHud(choice);
                changed(player, "set the HUD for new players to " + choice.label());
                return true;
            }
            case UPDATES -> {
                if (index < 0) return false;
                String setting = new String[]{"stable", "pilot", "auto"}[index];
                plugin.updates().setChannel(setting);
                plugin.getConfig().set("updates.channel", setting);
                plugin.saveConfig();
                changed(player, "set the update channel to " + setting);
                return true;
            }
            case BACKUPS -> {
                if (index < 0) return false;
                int hours = SetupSteps.BACKUP_HOURS.get(index);
                plugin.backups().setIntervalHours(hours);
                changed(player, "set automatic backups to " + SetupSteps.backupLabel(hours).toLowerCase());
                return true;
            }
            case STATISTICS -> {
                if (index < 0) return false;
                boolean on = index == 0;
                plugin.setUsageStats(on);
                changed(player, "switched anonymous statistics " + (on ? "on" : "off"));
                return true;
            }
            case ADDONS -> {
                int at = indexOf(ADDON_SLOTS, slot);
                if (at < 0) return false;
                List<AddonCatalogue.Entry> all = new ArrayList<>();
                for (AddonService.Installed one : plugin.addons().installed()) all.add(one.entry());
                all.addAll(plugin.addons().store());
                if (at >= all.size()) return false;
                link(player, all.get(at).name(), all.get(at).url());
                return true;
            }
            case DONE -> {
                if (slot == FIRST_SYSTEM) {
                    player.closeInventory();
                    plugin.setSetupDone(true);
                    player.performCommand("hvac link-tool");
                    link(player, "Build your first system, step by step", GETTING_STARTED);
                    return false;
                }
                if (slot == FINISH) {
                    finish(player);
                    return false;
                }
                return false;
            }
        }
        return false;
    }

    private static int choiceCount(Step step) {
        return switch (step) {
            case TEMPERATURES, STATISTICS -> 2;
            case UPDATES -> 3;
            case WIND, HUD, BACKUPS -> 4;
            default -> 0;
        };
    }

    private void setHud(HudChoice choice) {
        boolean show = choice != HudChoice.OFF;
        plugin.getConfig().set("hud.show-to-new-players", show);
        if (show) plugin.getConfig().set("hud.style", choice.name().toLowerCase());
        plugin.saveConfig();
        if (show) plugin.hud().setStyle(choice.style());
        plugin.hud().setShowToNewPlayers(show);
    }

    // ---- the title, typed in chat ----

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Step back = naming.remove(player.getUniqueId());
        if (back == null) return;
        event.setCancelled(true);
        String typed = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            String name = SetupSteps.cleanName(typed);
            if (name == null || name.equalsIgnoreCase("cancel")) {
                player.sendMessage(Component.text("The title stays as it was.", NamedTextColor.GRAY));
            } else {
                String mini = SetupSteps.title(MiniMessage.miniMessage().escapeTags(name));
                plugin.getConfig().set("hud.sidebar.title", mini);
                plugin.saveConfig();
                plugin.hud().setTitle(mini);
                player.sendMessage(Component.text("The HUD's title is now: ", NamedTextColor.GREEN)
                        .append(MiniMessage.miniMessage().deserialize(mini)));
                plugin.getLogger().info(player.getName() + " renamed the HUD's title to Climate - " + name);
            }
            open(player, back);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        naming.remove(event.getPlayer().getUniqueId());
    }

    // ---- helpers ----

    private void changed(Player player, String what) {
        plugin.getLogger().info(player.getName() + " " + what + " in the setup.");
    }

    private static void link(Player player, String name, String url) {
        player.sendMessage(Component.text(name + ": ", NamedTextColor.AQUA)
                .append(Component.text(url, NamedTextColor.WHITE).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url))));
    }

    private static void sound(Player player, boolean ok) {
        player.playSound(player.getLocation(), ok ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f);
    }

    private static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return i;
        return -1;
    }

    private static Component gray(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    /** A description split into lines of about {@code width} characters, at most {@code lines} of them. */
    static List<Component> wrap(String text, int width, int lines) {
        List<Component> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text).split("\\s+")) {
            if (word.isEmpty()) continue;
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(gray(line.toString()));
                line.setLength(0);
                if (out.size() == lines) break;
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0 && out.size() < lines) out.add(gray(line.toString()));
        return out;
    }

    private static ItemStack item(Material icon, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = new ItemStack(icon);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        if (glint) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }
}
