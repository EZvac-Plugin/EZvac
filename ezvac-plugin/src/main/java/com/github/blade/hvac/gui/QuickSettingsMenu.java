package com.github.blade.hvac.gui;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.addons.AddonCatalogue;
import com.github.blade.hvac.addons.AddonService;
import com.github.blade.hvac.config.ServerSwitches;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.DeviceKind;
import com.github.blade.hvac.model.EquipmentUnit;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.model.WeatherStation;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import com.github.blade.hvac.service.RegistryBackups;
import com.github.blade.hvac.simulation.SizingModel;
import com.github.blade.hvac.update.UpdateCheck;
import com.github.blade.hvac.update.UpdateNotifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.github.blade.hvac.gui.ControlMenus.click;
import static com.github.blade.hvac.gui.ControlMenus.gray;
import static com.github.blade.hvac.gui.ControlMenus.item;

/**
 * /hvac quicksettings (BETA 1.0): one menu for the server-wide settings an
 * owner would otherwise edit config.yml for. Its pages switch device kinds
 * and the climate systems on and off, make and schedule registry backups,
 * show the statistics and check for updates. Every subpage has Back in the
 * bottom-left corner and Close in the bottom-right. A change is written to
 * config.yml and applies at once. Admins only; main thread only.
 */
public final class QuickSettingsMenu implements Listener {
    enum Page {
        MAIN("EZvac quick settings", 36), DEVICES("Quick settings › Devices", 36),
        CLIMATE("Quick settings › Climate systems", 36), BACKUPS("Quick settings › Backups & registry", 36),
        STATISTICS("Quick settings › Health", 36), UPDATES("Quick settings › Updates", 36),
        SEASONS("Quick settings › Seasons & wind", 36), DISPLAY("Quick settings › Display & sound", 36),
        ADDONS("Quick settings › Addons", 54), ADDON_PARTS("Quick settings › Addon", 36);

        final String title;
        final int size;

        Page(String title, int size) {
            this.title = title;
            this.size = size;
        }

        int back() { return size - 9; }
        int close() { return size - 1; }
    }

    /**
     * The main page's sections (regrouped in BETA 1.0 Pilot 4): the server's
     * climate in the upper row, its upkeep in the lower.
     */
    static final int DEVICES = 10, CLIMATE = 12, SEASONS_AND_WIND = 14, DISPLAY = 16;
    static final int ADDONS = 19, BACKUPS = 21, STATISTICS = 23, UPDATES = 25;
    /** Seasons & wind: the world it is about, its calendar's buttons, and today's wind. */
    static final int WORLD_INFO = 4, SEASON_NOW = 10, DAY_BACK = 11, DAY_ON = 12, NEXT_SEASON = 13, PAUSE = 14,
            WIND_NOW = 16;
    /** Display & sound: units, wind units, and device sounds with their switch beneath. */
    static final int UNITS = 11, WIND_UNITS = 13, SOUNDS = 15;
    /** Health: what each part of EZvac costs the tick. */
    static final int TICK_COST = 16;
    /** The Addons page: installed addons with their switches beneath, the store's row, and the check. */
    static final int ADDONS_INFO = 4, INSTALLED_FROM = 10, STORE_FROM = 37, ADDONS_ROW = 7, CHECK_ADDONS = 49;
    /** The built-in EZdebug (BETA 1.0 Pilot 5): first in the installed row, before the addons. */
    static final int BUILT_IN = INSTALLED_FROM, ADDONS_FROM = INSTALLED_FROM + 1, ADDON_SLOTS = ADDONS_ROW - 1;
    /** An addon's own page: what it is, and its parts with their switches beneath. */
    static final int ADDON_INFO = 4, PARTS_FROM = 9, PARTS_ROW = 9;
    /** The device kinds in the second row: the five sources, the three room units, weather stations; switches beneath. */
    static final int[] DEVICE_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    /** Air quality, pollen, wind, seasons and building size, with their switches beneath. */
    static final int AIR = 9, POLLEN = 11, WIND = 13, SEASONS = 15, SIZING = 17;
    static final int BACK_UP_NOW = 11, AUTOMATIC = 13, BACKUP_INFO = 15;
    /** hvac.yml's health at this start, under the backups (BETA 1.0); clicked twice, it discards what was kept aside. */
    static final int REGISTRY = 22;
    /** The kept-aside entries its lore lists at most. */
    private static final int LISTED_ASIDE = 5;
    static final int DEVICE_COUNTS = 10, SYSTEM_COUNTS = 11, SIZES = 13, AIRFLOW = 15, REFRESH = 22;
    /** The systems the sizes item lists at most, the worst sized first. */
    private static final int LISTED_SYSTEMS = 10;
    static final int RUNNING = 10, CHECK_NOW = 12, BACKGROUND = 14, TELL_ADMINS = 16;
    /** Under the running version (BETA 1.0 Pilot 2): which releases this server hears about. */
    static final int CHANNEL = 19;
    private static final String ADMIN = "ezvac.admin";

    private static final class Menu implements InventoryHolder {
        final Page page;
        Inventory inventory;
        /** Clicked once: the next click on the registry item discards what was kept aside. */
        boolean discardArmed;
        /** On an addon's own page: its plugin's name. */
        String addon;
        /** On Seasons & wind: the world it is about. */
        java.util.UUID world;

        Menu(Page page) { this.page = page; }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final AirflowService airflow;
    private final DisplayService displays;
    private final ServerSwitches switches;
    private final RegistryBackups backups;
    private final UpdateNotifier updates;
    private final AddonService addons;

    public QuickSettingsMenu(HvacPlugin plugin, HvacRegistry registry, AirflowService airflow, DisplayService displays,
                             ServerSwitches switches, RegistryBackups backups, UpdateNotifier updates,
                             AddonService addons) {
        this.addons = Objects.requireNonNull(addons, "addons");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.displays = Objects.requireNonNull(displays, "displays");
        this.switches = Objects.requireNonNull(switches, "switches");
        this.backups = Objects.requireNonNull(backups, "backups");
        this.updates = Objects.requireNonNull(updates, "updates");
    }

    public void open(Player player) { open(player, Page.MAIN, null); }

    /** /hvac addons: straight to the Addons page. */
    public void openAddons(Player player) { open(player, Page.ADDONS, null); }

    private void open(Player player, Page page) { open(player, page, null); }

    private void open(Player player, Page page, String addon) {
        Menu menu = new Menu(page);
        menu.addon = addon;
        menu.world = seasonalWorld(player);
        menu.inventory = Bukkit.createInventory(menu, page.size, Component.text(page.title));
        draw(menu);
        player.openInventory(menu.inventory);
    }

    private void draw(Menu menu) {
        Inventory inventory = menu.inventory;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        inventory.setItem(menu.page.close(), item(Material.BARRIER, Component.text("Close", NamedTextColor.RED),
                List.of()));
        if (menu.page != Page.MAIN)
            inventory.setItem(menu.page.back(), item(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW),
                    List.of(gray(menu.page == Page.ADDON_PARTS ? "To the addons" : "To the quick settings"))));
        switch (menu.page) {
            case MAIN -> drawMain(inventory);
            case DEVICES -> drawDevices(inventory);
            case CLIMATE -> drawClimate(inventory);
            case BACKUPS -> drawBackups(inventory, menu.discardArmed);
            case STATISTICS -> drawStatistics(inventory);
            case UPDATES -> drawUpdates(inventory);
            case ADDONS -> drawAddons(inventory);
            case ADDON_PARTS -> drawAddonParts(inventory, menu.addon);
            case SEASONS -> drawSeasons(inventory, menu.world);
            case DISPLAY -> drawDisplay(inventory);
        }
    }

    /** The world Seasons & wind is about: the admin's own when it has a sky, else the first that has. */
    private java.util.UUID seasonalWorld(Player player) {
        if (player.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL) return player.getWorld().getUID();
        for (org.bukkit.World world : plugin.getServer().getWorlds())
            if (world.getEnvironment() == org.bukkit.World.Environment.NORMAL) return world.getUID();
        return null;
    }

    private static Material seasonIcon(com.github.blade.hvac.simulation.Seasons.Season season) {
        return switch (season) {
            case SPRING -> Material.PINK_TULIP;
            case SUMMER -> Material.SUNFLOWER;
            case AUTUMN -> Material.DEAD_BUSH;
            case WINTER -> Material.SNOWBALL;
        };
    }

    /**
     * A world's calendar and today's wind (BETA 1.0 Pilot 4): what
     * /hvac season and /hvac wind set do, a click at a time.
     */
    private void drawSeasons(Inventory inventory, java.util.UUID worldId) {
        org.bukkit.World world = worldId == null ? null : plugin.getServer().getWorld(worldId);
        if (world == null) {
            inventory.setItem(WORLD_INFO, item(Material.BARRIER, Component.text("No world with a sky",
                    NamedTextColor.RED), List.of(gray("Seasons and wind need one"))));
            return;
        }
        inventory.setItem(WORLD_INFO, item(Material.GRASS_BLOCK,
                Component.text("World: " + world.getName(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(gray("The world you stand in, when it has"), gray("a sky; open this from another"),
                        gray("world to change that one"))));
        com.github.blade.hvac.service.SeasonService seasons = plugin.seasons();
        if (!switches.seasons() || seasons == null || !seasons.tracks(worldId)) {
            inventory.setItem(SEASON_NOW, item(Material.OAK_SAPLING, Component.text("Seasons: Off", NamedTextColor.RED),
                    List.of(gray(switches.seasons() ? "This world's calendar hasn't started yet"
                            : "Switch them on under Climate systems"))));
        } else {
            int days = seasons.settings().daysPerSeason(), day = seasons.dayOfSeason(worldId);
            com.github.blade.hvac.simulation.Seasons.Season season = seasons.season(worldId);
            boolean paused = seasons.paused(worldId);
            inventory.setItem(SEASON_NOW, item(seasonIcon(season), Component.text(season.displayName() + ", day " + day
                            + " of " + days, NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                    List.of(gray(paused ? "Paused: the days pass, the season stays" : "The calendar is running"),
                            gray("A year is " + days * 4 + " days"))));
            inventory.setItem(DAY_BACK, item(Material.RED_CANDLE, Component.text("A day back", NamedTextColor.YELLOW),
                    List.of(gray(day > 1 ? "To day " + (day - 1) : "This is the season's first day"))));
            inventory.setItem(DAY_ON, item(Material.LIME_CANDLE, Component.text("A day on", NamedTextColor.YELLOW),
                    List.of(gray(day < days ? "To day " + (day + 1) : "To " + season.next().displayName() + ", day 1"))));
            inventory.setItem(NEXT_SEASON, item(seasonIcon(season.next()), Component.text("Next season",
                    NamedTextColor.YELLOW), List.of(gray("To " + season.next().displayName() + ", day 1"))));
            inventory.setItem(PAUSE, item(paused ? Material.LIME_DYE : Material.GRAY_DYE,
                    Component.text(paused ? "Resume the calendar" : "Pause the calendar", NamedTextColor.YELLOW),
                    List.of(gray(paused ? "The seasons move on again" : "The season stays as it is"))));
        }
        com.github.blade.hvac.service.WindService wind = plugin.outdoor().wind();
        if (!switches.wind() || wind == null || !com.github.blade.hvac.service.WindService.hasWind(world)) {
            inventory.setItem(WIND_NOW, item(Material.FEATHER, Component.text("Wind: Off", NamedTextColor.RED),
                    List.of(gray("Switch it on under Climate systems"))));
            return;
        }
        com.github.blade.hvac.simulation.WindModel.Day today = wind.day(world);
        boolean held = wind.held(world);
        com.github.blade.hvac.simulation.WindModel.Level next = nextWind(held ? today.level() : null);
        inventory.setItem(WIND_NOW, item(Material.FEATHER, Component.text("Wind today: " + today.level().displayName(),
                        NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(gray("About " + com.github.blade.hvac.service.WindText.speed(today.meanMs(),
                                com.github.blade.hvac.service.WindText.units(wind.settings(), plugin.settings().units()))),
                        gray(held ? "Set by an admin, until the day ends" : "Blowing as it would"),
                        yellow("Click for: " + (next == null ? "as it would blow" : next.displayName())))));
    }

    /** What a click on the wind sets: calm, breezy, windy, gale, then back to as it would blow (null). */
    static com.github.blade.hvac.simulation.WindModel.Level nextWind(com.github.blade.hvac.simulation.WindModel.Level held) {
        if (held == null) return com.github.blade.hvac.simulation.WindModel.Level.CALM;
        com.github.blade.hvac.simulation.WindModel.Level[] levels = com.github.blade.hvac.simulation.WindModel.Level.values();
        return held.ordinal() + 1 < levels.length ? levels[held.ordinal() + 1] : null;
    }

    private void clickSeasons(Player player, int slot, java.util.UUID worldId) {
        org.bukkit.World world = worldId == null ? null : plugin.getServer().getWorld(worldId);
        if (world == null) return;
        if (slot == WIND_NOW) {
            com.github.blade.hvac.service.WindService wind = plugin.outdoor().wind();
            if (!switches.wind() || wind == null || !com.github.blade.hvac.service.WindService.hasWind(world)) {
                click(player, false);
                return;
            }
            com.github.blade.hvac.simulation.WindModel.Level next = nextWind(wind.held(world) ? wind.day(world).level() : null);
            wind.hold(world, next);
            click(player, true);
            logChange(player, next == null ? "let the wind in " + world.getName() + " blow as it would"
                    : "set today's wind in " + world.getName() + " to " + next.displayName().toLowerCase(Locale.ROOT));
            plugin.requestImmediateSync();
            displays.refreshAll(true);
            return;
        }
        com.github.blade.hvac.service.SeasonService seasons = plugin.seasons();
        if (!switches.seasons() || seasons == null || !seasons.tracks(worldId)) {
            if (slot >= SEASON_NOW && slot <= PAUSE) click(player, false);
            return;
        }
        int days = seasons.settings().daysPerSeason(), day = seasons.dayOfSeason(worldId);
        switch (slot) {
            case DAY_BACK -> {
                if (day <= 1) {
                    click(player, false);
                    return;
                }
                seasons.set(worldId, seasons.season(worldId), day - 1);
            }
            case DAY_ON -> {
                if (day >= days) seasons.next(worldId);
                else seasons.set(worldId, seasons.season(worldId), day + 1);
            }
            case NEXT_SEASON -> seasons.next(worldId);
            case PAUSE -> seasons.setPaused(worldId, !seasons.paused(worldId));
            default -> {
                return;
            }
        }
        seasons.save();
        click(player, true);
        logChange(player, slot == PAUSE ? (seasons.paused(worldId) ? "paused" : "resumed") + " the calendar in "
                + world.getName() : "moved " + world.getName() + " to " + seasons.season(worldId).displayName()
                .toLowerCase(Locale.ROOT) + ", day " + seasons.dayOfSeason(worldId));
        plugin.requestImmediateSync();
        displays.refreshAll(true);
    }

    /** How readings are shown, and whether devices are heard (BETA 1.0 Pilot 4). */
    private void drawDisplay(Inventory inventory) {
        com.github.blade.hvac.config.TemperatureUnit unit = plugin.settings().units();
        boolean fahrenheit = unit == com.github.blade.hvac.config.TemperatureUnit.F;
        inventory.setItem(UNITS, item(fahrenheit ? Material.MAGMA_CREAM : Material.SNOWBALL,
                Component.text("Temperatures: °" + unit.name(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(gray("On screens, the HUD, menus and chat"), gray("Setpoints keep their temperature"),
                        yellow("Click for: °" + (fahrenheit ? "C" : "F")))));
        com.github.blade.hvac.service.WindService wind = plugin.outdoor().wind();
        com.github.blade.hvac.config.WindSettings.Units speeds = wind == null
                ? com.github.blade.hvac.config.WindSettings.Units.AUTO : wind.settings().units();
        inventory.setItem(WIND_UNITS, item(Material.FEATHER,
                Component.text("Wind speeds: " + speeds.displayName(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(gray("Auto: mph with °F, km/h with °C"),
                        yellow("Click for: " + nextWindUnits(speeds).displayName()))));
        boolean sounds = plugin.settings().audioEnabled();
        placeSwitch(inventory, SOUNDS, Material.NOTE_BLOCK, "Device sounds", sounds, List.of(
                gray("Units starting and stopping, their hum,"), gray("and air at the vents"), clickTo(sounds)));

    }

    static com.github.blade.hvac.config.WindSettings.Units nextWindUnits(com.github.blade.hvac.config.WindSettings.Units now) {
        com.github.blade.hvac.config.WindSettings.Units[] all = com.github.blade.hvac.config.WindSettings.Units.values();
        return all[(now.ordinal() + 1) % all.length];
    }

    private void clickDisplay(Player player, int slot) {
        int at = switchedAt(slot);
        if (slot == UNITS) {
            com.github.blade.hvac.config.TemperatureUnit next = plugin.settings().units()
                    == com.github.blade.hvac.config.TemperatureUnit.F ? com.github.blade.hvac.config.TemperatureUnit.C
                    : com.github.blade.hvac.config.TemperatureUnit.F;
            switches.setUnits(next);
            logChange(player, "set temperatures to °" + next.name());
        } else if (slot == WIND_UNITS) {
            com.github.blade.hvac.service.WindService wind = plugin.outdoor().wind();
            if (wind == null) {
                click(player, false);
                return;
            }
            com.github.blade.hvac.config.WindSettings.Units next = nextWindUnits(wind.settings().units());
            switches.setWindUnits(next);
            logChange(player, "set wind speeds to " + next.displayName());
        } else if (at == SOUNDS) {
            boolean on = !plugin.settings().audioEnabled();
            switches.setSounds(on);
            logChange(player, "switched device sounds " + (on ? "on" : "off"));

        } else return;
        click(player, true);
        plugin.requestImmediateSync();
        displays.refreshAll(true);
    }

    private static Material icon(AddonCatalogue.Entry entry) {
        Material material = Material.matchMaterial(entry.icon());
        return material == null || !material.isItem() ? Material.PAPER : material;
    }

    private static void describe(List<Component> lore, AddonCatalogue.Entry entry) {
        for (String line : AddonCatalogue.wrap(entry.description(), 38)) lore.add(gray(line));
    }

    private static Component yellow(String text) {
        return Component.text(text, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false);
    }

    private static Component colored(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * The official addons (BETA 1.0 Pilot 4): those installed, each with its
     * switch beneath, and beneath them the store, the ones this server
     * doesn't have, which give their link when clicked.
     */
    private void drawAddons(Inventory inventory) {
        List<AddonService.Installed> installed = addons.installed();
        List<AddonCatalogue.Entry> store = addons.store();
        inventory.setItem(ADDONS_INFO, item(Material.BOOKSHELF,
                Component.text("Official addons", NamedTextColor.AQUA).decorate(TextDecoration.BOLD), List.of(
                        gray(installed.size() + " installed, " + store.size() + " more in the store"),
                        gray("Top row: installed. Click the dye"), gray("beneath one to switch it."),
                        gray("Bottom row: the store. Click one"), gray("for its link; EZvac never"),
                        gray("downloads or installs anything."))));
        builtIn(inventory);
        for (int i = 0; i < Math.min(ADDON_SLOTS, installed.size()); i++) {
            AddonService.Installed addon = installed.get(i);
            AddonCatalogue.Entry entry = addon.entry();
            List<Component> lore = new ArrayList<>();
            lore.add(gray("Version " + addon.version()));
            if (addon.update() != null) lore.add(colored("Update available: " + addon.update(), NamedTextColor.GOLD));
            describe(lore, entry);
            if (entry.folded()) {
                AddonCatalogue.Entry into = addons.entry(entry.foldedInto());
                lore.add(colored("Now part of " + (into == null ? entry.foldedInto() : into.name()),
                        NamedTextColor.GOLD));
            }
            boolean on = addon.running() && addon.active();
            if (!addon.running()) lore.add(colored("Its plugin isn't running: see the console", NamedTextColor.RED));
            else if (!addon.switchable()) {
                lore.add(gray("Runs, but can't be switched here:"));
                lore.add(gray("update it for that"));
            } else {
                if (!addon.parts().isEmpty()) {
                    long partsOn = addon.parts().stream().filter(AddonService.PartView::on).count();
                    lore.add(gray(partsOn + " of " + addon.parts().size() + " parts on"));
                    lore.add(yellow("Click for its parts"));
                    lore.add(yellow("Click the dye to switch it " + (addon.active() ? "off" : "on")));
                } else lore.add(clickTo(addon.active()));
            }
            if (addon.update() != null) lore.add(yellow("Shift-click for its link"));
            placeSwitch(inventory, ADDONS_FROM + i, icon(entry), entry.name(), on, lore);
        }
        if (installed.isEmpty())
            inventory.setItem(ADDONS_FROM + 2, item(Material.GLASS_BOTTLE, Component.text("No official addons yet",
                    NamedTextColor.GRAY), List.of(gray("The store is in the row below"))));
        for (int i = 0; i < Math.min(ADDONS_ROW, store.size()); i++) {
            AddonCatalogue.Entry entry = store.get(i);
            List<Component> lore = new ArrayList<>();
            describe(lore, entry);
            if (!entry.needs().isEmpty()) lore.add(gray("Needs " + entry.needs()));
            if (!addons.runsHere(entry))
                lore.add(colored("Needs Minecraft " + entry.minecraft() + " or newer", NamedTextColor.RED));
            if (entry.folded()) {
                AddonCatalogue.Entry into = addons.entry(entry.foldedInto());
                lore.add(colored("Now part of " + (into == null ? entry.foldedInto() : into.name()),
                        NamedTextColor.GOLD));
            }
            lore.add(yellow("Click for its link"));
            inventory.setItem(STORE_FROM + i, item(icon(entry),
                    Component.text(entry.name(), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), lore));
        }
        if (store.isEmpty())
            inventory.setItem(STORE_FROM + 3, item(Material.EMERALD, Component.text("Every official addon is installed",
                    NamedTextColor.GREEN), List.of()));
        List<String> news = addons.updates();
        inventory.setItem(CHECK_ADDONS, item(Material.SPYGLASS,
                Component.text("Check now", NamedTextColor.GREEN).decorate(TextDecoration.BOLD), List.of(
                        gray("Asks GitHub for new addons and"), gray("newer releases of these."),
                        gray(news.isEmpty() ? "No updates known" : "Updates: " + String.join(", ", news)),
                        gray(updates.checking() ? "Also checked every 6 hours" : "Background checks are off"))));
    }

    /**
     * EZvac's built-in EZdebug, first in the installed row: on or off, or
     * replaced while EZdebug+ is installed and switched on.
     */
    private void builtIn(Inventory inventory) {
        boolean on = switches.ezdebug();
        boolean replaced = plugin.ezdebug() != null && on && !plugin.ezdebug().active();
        List<Component> lore = new ArrayList<>();
        lore.add(gray("Built into EZvac"));
        lore.add(gray("Hold the link tool with a system"));
        lore.add(gray("selected: its devices, the edge of"));
        lore.add(gray("its room and its leaks show"));
        if (replaced) {
            lore.add(colored("Replaced by EZdebug+, which shows", NamedTextColor.GOLD));
            lore.add(colored("all of this and more", NamedTextColor.GOLD));
        } else lore.add(clickTo(on));
        Component title = Component.text("EZdebug (built in): ", NamedTextColor.WHITE).append(replaced
                ? Component.text("Replaced", NamedTextColor.GOLD)
                : Component.text(on ? "On" : "Off", on ? NamedTextColor.GREEN : NamedTextColor.RED));
        inventory.setItem(BUILT_IN, item(Material.SPYGLASS, title, lore));
        inventory.setItem(BUILT_IN + 9, item(replaced ? Material.ORANGE_DYE : on ? Material.LIME_DYE : Material.RED_DYE,
                title, lore));
    }

    /** One addon's parts, each with its switch beneath; they only run while the addon itself is on. */
    private void drawAddonParts(Inventory inventory, String pluginName) {
        AddonService.Installed addon = installed(pluginName);
        if (addon == null) {
            inventory.setItem(ADDON_INFO, item(Material.BARRIER, Component.text("No longer installed",
                    NamedTextColor.RED), List.of()));
            return;
        }
        List<Component> lore = new ArrayList<>();
        lore.add(gray("Version " + addon.version()));
        describe(lore, addon.entry());
        lore.add(colored(addon.active() ? "On" : "Off: its parts wait until it is on",
                addon.active() ? NamedTextColor.GREEN : NamedTextColor.RED));
        inventory.setItem(ADDON_INFO, item(icon(addon.entry()),
                Component.text(addon.entry().name(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD), lore));
        for (int i = 0; i < Math.min(PARTS_ROW, addon.parts().size()); i++) {
            AddonService.PartView part = addon.parts().get(i);
            List<Component> partLore = new ArrayList<>();
            for (String line : AddonCatalogue.wrap(part.description(), 38)) partLore.add(gray(line));
            partLore.add(clickTo(part.on()));
            placeSwitch(inventory, PARTS_FROM + i, part.on() ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS,
                    part.name(), part.on(), partLore);
        }
    }

    private AddonService.Installed installed(String pluginName) {
        for (AddonService.Installed addon : addons.installed())
            if (addon.entry().plugin().equals(pluginName)) return addon;
        return null;
    }

    private void drawMain(Inventory inventory) {
        long off = java.util.Arrays.stream(DeviceKind.values()).filter(switches::isOff).count();
        inventory.setItem(DEVICES, section(Material.DISPENSER, "Devices",
                off == 0 ? "Every kind is on" : off + " kind(s) switched off", "Switch device kinds on or off"));
        int systemsOff = (switches.airQuality() ? 0 : 1) + (switches.pollen() ? 0 : 1) + (switches.wind() ? 0 : 1)
                + (switches.seasons() ? 0 : 1) + (switches.sizing() ? 0 : 1);
        inventory.setItem(CLIMATE, section(Material.SUNFLOWER, "Climate systems",
                systemsOff == 0 ? "All on" : systemsOff + " switched off",
                "Air quality, pollen, wind, seasons, size"));
        inventory.setItem(BACKUPS, section(Material.CHEST, "Backups & registry",
                "Automatic: " + RegistryBackups.describe(backups.intervalHours()), "Back up hvac.yml; its health"));
        List<com.github.blade.hvac.service.TickCost.Part> cost = plugin.tickCost().lastMinute();
        inventory.setItem(STATISTICS, section(Material.BOOK, "Health",
                cost.isEmpty() ? registry.thermostats().size() + " thermostat(s), " + registry.equipment().size()
                        + " unit(s)" : String.format(Locale.US, "EZvac takes %.2f ms a tick",
                        com.github.blade.hvac.service.TickCost.total(cost)),
                "What EZvac costs, and its figures"));
        org.bukkit.World here = plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0);
        inventory.setItem(SEASONS_AND_WIND, section(Material.OAK_SAPLING, "Seasons & wind",
                plugin.seasons() != null && switches.seasons() && here != null && plugin.seasons().tracks(here.getUID())
                        ? plugin.seasons().season(here.getUID()).displayName() + ", day "
                        + plugin.seasons().dayOfSeason(here.getUID()) + " in " + here.getName() : "The calendar and the wind",
                "Move the season; set today's wind"));
        inventory.setItem(DISPLAY, section(Material.PAINTING, "Display & sound",
                "°" + plugin.settings().units().name() + ", sounds " + (plugin.settings().audioEnabled() ? "on" : "off"),
                "Units, wind speeds, device sounds"));
        inventory.setItem(UPDATES, section(Material.CLOCK, "Updates", "Running " + updates.runningName(),
                "Check GitHub for a newer EZvac"));
        int installed = addons.installed().size(), news = addons.updates().size();
        inventory.setItem(ADDONS, section(Material.BOOKSHELF, "Addons",
                installed + " installed" + (news == 0 ? "" : ", " + news + " with an update"),
                "Switch official addons; find more"));
    }

    private static ItemStack section(Material icon, String name, String state, String what) {
        return item(icon, Component.text(name, NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(gray(state), gray(what), Component.text("Click to open", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)));
    }

    private void drawDevices(Inventory inventory) {
        DeviceKind[] kinds = DeviceKind.values();
        for (int i = 0; i < kinds.length; i++) {
            DeviceKind kind = kinds[i];
            boolean on = !switches.isOff(kind);
            List<Component> lore = new ArrayList<>();
            lore.add(gray(count(kind) + " on this server, " + running(kind) + " running"));
            lore.add(gray(on ? "Off: they stop, and none can be made" : "On: they run again as they were set"));
            lore.add(clickTo(on));
            placeSwitch(inventory, DEVICE_SLOTS[i], kind.icon(), kind.plural(), on, lore);
        }
    }

    private void drawClimate(Inventory inventory) {
        placeSwitch(inventory, AIR, Material.CAMPFIRE, "Air quality", switches.airQuality(), List.of(
                gray("Smoke, dust and the AQI, and air purifiers"), gray("Off: no air readings; purifiers idle"),
                clickTo(switches.airQuality())));
        if (!switches.airQuality())
            placeSwitch(inventory, POLLEN, Material.DANDELION, "Pollen", false, List.of(
                    gray("Off with air quality"), gray("Switch air quality on first")));
        else
            placeSwitch(inventory, POLLEN, Material.DANDELION, "Pollen", switches.pollen(), List.of(
                    gray("Pollen outdoors and in, and the HUD's level"), gray("Off: no pollen anywhere"),
                    clickTo(switches.pollen())));
        placeSwitch(inventory, WIND, Material.FEATHER, "Wind", switches.wind(), List.of(
                gray("Daily wind, wind chill and drafts"), gray("Off: only the weather's light breeze"),
                clickTo(switches.wind())));
        placeSwitch(inventory, SEASONS, Material.OAK_SAPLING, "Seasons", switches.seasons(), List.of(
                gray("Seasonal warmth, cold and pollen"), gray("Off: no seasonal effect; the calendar keeps counting"),
                clickTo(switches.seasons())));
        placeSwitch(inventory, SIZING, Material.BRICKS, "Building size", switches.sizing(), List.of(
                gray("A unit heats or cools what fits its size:"), gray("one three-star unit per " + String.format(Locale.US, "%,d",
                        plugin.controller().sizing().cellsPerUnit()) + " blocks of air"),
                gray("Off: any size, as before BETA 1.0"), clickTo(switches.sizing())));
    }

    private void drawBackups(Inventory inventory, boolean discardArmed) {
        inventory.setItem(REGISTRY, registryItem(discardArmed));
        inventory.setItem(BACK_UP_NOW, item(Material.WRITABLE_BOOK,
                Component.text("Back up now", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(gray("hvac.yml and seasons.yml, to"), gray("plugins/EZvac/backups"),
                        gray("Kept until you delete it"))));
        int hours = backups.intervalHours();
        inventory.setItem(AUTOMATIC, item(Material.CLOCK, Component.text("Automatic: " + RegistryBackups.describe(hours),
                        hours == 0 ? NamedTextColor.RED : NamedTextColor.GREEN),
                List.of(gray("In real time, through restarts"), gray("The newest " + backups.keep() + " are kept"),
                        Component.text("Click for: " + RegistryBackups.describe(RegistryBackups.nextInterval(hours)),
                                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))));
        RegistryBackups.Summary summary = backups.summary();
        List<Component> lore = new ArrayList<>();
        if (summary.count() == 0) lore.add(gray("No backups yet"));
        else {
            lore.add(gray("Newest: " + summary.newest()));
            lore.add(gray("Made " + ago(summary.newestAt())));
            lore.add(gray(summary.count() + " backup(s) in the folder"));
        }
        lore.add(gray("To restore: stop the server, copy"));
        lore.add(gray("a backup over hvac.yml"));
        inventory.setItem(BACKUP_INFO, item(Material.PAPER, Component.text("Backups", NamedTextColor.AQUA), lore));
    }

    private void drawStatistics(Inventory inventory) {
        List<Component> devices = new ArrayList<>();
        for (DeviceKind kind : DeviceKind.values())
            devices.add(gray(kind.plural() + ": " + count(kind) + " (" + running(kind) + " running)"
                    + (switches.isOff(kind) ? ", off" : "")));
        inventory.setItem(DEVICE_COUNTS, item(Material.DISPENSER,
                Component.text("Devices", NamedTextColor.AQUA), devices));
        long enabled = registry.thermostats().stream().filter(Thermostat::enabled).count();
        inventory.setItem(SYSTEM_COUNTS, item(Material.OAK_SIGN, Component.text("Systems", NamedTextColor.AQUA), List.of(
                gray("Thermostats: " + registry.thermostats().size() + " (" + enabled + " on)"),
                gray("Vents: " + registry.vents().size()),
                gray("Settings panels: " + registry.settingsPanels().size()),
                gray("Thermometers: " + registry.thermometers().size()),
                gray("RPM monitors: " + registry.rpmMonitors().size()),
                gray("Sensors: " + registry.sensors().size() + ", hubs: " + registry.sensorHubs().size()))));
        inventory.setItem(SIZES, item(Material.BRICKS, Component.text("Sizes", NamedTextColor.AQUA), sizeLines()));
        AirflowService.Figures figures = airflow.figures();
        inventory.setItem(AIRFLOW, item(Material.COMPASS, Component.text("Airflow", NamedTextColor.AQUA), List.of(
                gray("Cells mapped: " + figures.cachedCells()),
                gray("Waiting to be mapped: " + figures.waiting() + (figures.rebuilding() ? ", one mapping now" : "")),
                gray("Maps made since start-up: " + figures.completed()),
                gray("Cells visited: " + figures.visited()),
                gray("Maps that hit the size cap: " + figures.capped()))));
        inventory.setItem(REFRESH, item(Material.SPYGLASS, Component.text("Refresh", NamedTextColor.YELLOW),
                List.of(gray("Read the figures again"))));
        // What each part of EZvac costs the tick, over the last minute (BETA 1.0 Pilot 4).
        List<com.github.blade.hvac.service.TickCost.Part> cost = plugin.tickCost().lastMinute();
        List<Component> lines = new ArrayList<>();
        if (cost.isEmpty()) lines.add(gray("Measuring: look again in a moment"));
        else {
            double total = com.github.blade.hvac.service.TickCost.total(cost);
            lines.add(colored(String.format(Locale.US, "%.2f ms of each 50 ms tick", total),
                    total < 5.0 ? NamedTextColor.GREEN : total < 15.0 ? NamedTextColor.GOLD : NamedTextColor.RED));
            for (com.github.blade.hvac.service.TickCost.Part part : cost)
                lines.add(gray(String.format(Locale.US, "%s: %.3f ms (longest %.1f)", part.name(), part.msPerTick(),
                        part.worstMs())));
            lines.add(gray("Averaged over the last minute;"));
            lines.add(gray("longest is one run's time"));
        }
        inventory.setItem(TICK_COST, item(Material.REDSTONE,
                Component.text("Tick cost", NamedTextColor.AQUA), lines));
    }

    private void drawUpdates(Inventory inventory) {
        inventory.setItem(RUNNING, item(Material.NAME_TAG, Component.text("EZvac " + updates.runningName(),
                NamedTextColor.AQUA), List.of(gray("On Minecraft " + updates.minecraft()))));
        inventory.setItem(CHECK_NOW, item(Material.SPYGLASS,
                Component.text("Check now", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(gray("Asks GitHub for a newer EZvac,"), gray("and whether it runs here."),
                        gray("The answer comes in chat."))));
        inventory.setItem(CHANNEL, channel(updates.channel(), updates.channelSetting()));
        placeSwitch(inventory, BACKGROUND, Material.REPEATER, "Background checks", updates.checking(), List.of(
                gray("At start-up and every 6 hours"), clickTo(updates.checking())));
        placeSwitch(inventory, TELL_ADMINS, Material.BELL, "Tell admins on join", updates.notifyAdmins(), List.of(
                gray("When a background check finds one"), clickTo(updates.notifyAdmins())));
    }

    /**
     * The update channel: which it is, what each hears about, and whether it
     * was chosen or follows the build (auto).
     */
    static ItemStack channel(UpdateCheck.Channel channel, String setting) {
        boolean auto = setting.equals("auto");
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text((channel == UpdateCheck.Channel.STABLE ? "● " : "  ")
                + "Stable: full releases and their hotfixes", channel == UpdateCheck.Channel.STABLE
                ? NamedTextColor.GREEN : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text((channel == UpdateCheck.Channel.PILOT ? "● " : "  ")
                + "Pilot: pilots too, the test builds before one", channel == UpdateCheck.Channel.PILOT
                ? NamedTextColor.GOLD : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(gray(auto ? "Auto: follows the EZvac this server runs" : "Chosen here"));
        lore.add(gray("Click: auto › stable › pilot"));
        return item(channel == UpdateCheck.Channel.PILOT ? Material.TRIAL_KEY : Material.TRIPWIRE_HOOK,
                Component.text("Channel: " + channel.displayName() + (auto ? " (auto)" : ""),
                        channel == UpdateCheck.Channel.PILOT ? NamedTextColor.GOLD : NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD), lore);
    }

    /** The setting a click on the channel moves to: auto, then stable, then pilot, then auto again. */
    static String nextChannel(String setting) {
        return switch (setting) {
            case "auto" -> "stable";
            case "stable" -> "pilot";
            default -> "auto";
        };
    }

    /** An icon with a lime or red dye beneath it; clicking either switches it. */
    private static void placeSwitch(Inventory inventory, int slot, Material icon, String name, boolean on,
                                    List<Component> lore) {
        Component title = Component.text(name + ": ", NamedTextColor.WHITE)
                .append(Component.text(on ? "On" : "Off", on ? NamedTextColor.GREEN : NamedTextColor.RED));
        inventory.setItem(slot, item(icon, title, lore));
        inventory.setItem(slot + 9, item(on ? Material.LIME_DYE : Material.RED_DYE, title, lore));
    }

    private static Component clickTo(boolean on) {
        return Component.text(on ? "Click to switch off" : "Click to switch on", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != menu.inventory || !(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission(ADMIN)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        int slot = event.getSlot();
        if (slot == menu.page.close()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (menu.page != Page.MAIN && slot == menu.page.back()) {
            click(player, true);
            reopen(player, menu.page == Page.ADDON_PARTS ? Page.ADDONS : Page.MAIN);
            return;
        }
        switch (menu.page) {
            case MAIN -> {
                Page page = switch (slot) {
                    case DEVICES -> Page.DEVICES;
                    case CLIMATE -> Page.CLIMATE;
                    case BACKUPS -> Page.BACKUPS;
                    case STATISTICS -> Page.STATISTICS;
                    case UPDATES -> Page.UPDATES;
                    case ADDONS -> Page.ADDONS;
                    case SEASONS_AND_WIND -> Page.SEASONS;
                    case DISPLAY -> Page.DISPLAY;
                    default -> null;
                };
                if (page != null) {
                    click(player, true);
                    reopen(player, page);
                }
                return;
            }
            case DEVICES -> clickDevices(player, slot);
            case CLIMATE -> clickClimate(player, slot);
            case BACKUPS -> clickBackups(player, slot, menu);
            case STATISTICS -> {
                if (slot == REFRESH) click(player, true);
            }
            case UPDATES -> {
                if (clickUpdates(player, slot)) return;
            }
            case ADDONS -> {
                if (clickAddons(player, slot, event.isShiftClick())) return;
            }
            case ADDON_PARTS -> clickAddonParts(player, slot, menu.addon);
            case SEASONS -> clickSeasons(player, slot, menu.world);
            case DISPLAY -> clickDisplay(player, slot);
        }
        draw(menu);
    }

    /** True when the menu closed or another page opens. */
    private boolean clickAddons(Player player, int slot, boolean shift) {
        if (slot == CHECK_ADDONS) {
            click(player, true);
            addons.refresh(answered -> {
                if (!player.isOnline()) return;
                List<String> news = addons.updates();
                player.sendMessage(Component.text("[EZvac] ", NamedTextColor.GOLD).append(Component.text(
                        !answered ? "GitHub couldn't be reached; the addons are as last known."
                                : news.isEmpty() ? "The addons here are up to date."
                                : "Updates: " + String.join(", ", news) + ".",
                        answered ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu open
                        && open.page == Page.ADDONS) draw(open);
            });
            return false;
        }
        if (slot >= STORE_FROM && slot < STORE_FROM + ADDONS_ROW) {
            List<AddonCatalogue.Entry> store = addons.store();
            if (slot - STORE_FROM >= store.size()) return false;
            sendLink(player, store.get(slot - STORE_FROM), null);
            return true;
        }
        boolean dye = slot >= 18 && slot < 27;
        if (switchedAt(slot) == BUILT_IN) {
            if (plugin.ezdebug() != null && switches.ezdebug() && !plugin.ezdebug().active()) {
                click(player, false);   // EZdebug+ shows it: switch EZdebug+ instead
                return false;
            }
            boolean on = !switches.ezdebug();
            switches.setEzdebug(on);
            click(player, true);
            logChange(player, "switched the built-in EZdebug " + (on ? "on" : "off"));
            return false;
        }
        int index = switchedAt(slot) - ADDONS_FROM;
        List<AddonService.Installed> installed = addons.installed();
        if (index < 0 || index >= Math.min(ADDON_SLOTS, installed.size())) return false;
        AddonService.Installed addon = installed.get(index);
        if (shift && addon.update() != null) {
            sendLink(player, addon.entry(), addon.update());
            return true;
        }
        if (!addon.running() || !addon.switchable()) {
            click(player, false);
            return false;
        }
        if (!dye && !addon.parts().isEmpty()) {
            click(player, true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.hasPermission(ADMIN)) open(player, Page.ADDON_PARTS, addon.entry().plugin());
            });
            return true;
        }
        boolean on = !addon.active();
        if (addons.setActive(addon.entry().plugin(), on)) {
            click(player, true);
            logChange(player, "switched the addon " + addon.entry().name() + (on ? " on" : " off"));
        } else {
            click(player, false);
            player.sendMessage(Component.text("[EZvac] ", NamedTextColor.GOLD).append(Component.text(
                    addon.entry().name() + " couldn't be switched: see the console.", NamedTextColor.RED)));
        }
        return false;
    }

    private void clickAddonParts(Player player, int slot, String pluginName) {
        AddonService.Installed addon = installed(pluginName);
        int index = switchedAt(slot) - PARTS_FROM;
        if (addon == null || index < 0 || index >= Math.min(PARTS_ROW, addon.parts().size())) return;
        AddonService.PartView part = addon.parts().get(index);
        if (addons.setPart(pluginName, part.id(), !part.on())) {
            click(player, true);
            logChange(player, "switched " + addon.entry().name() + "'s " + part.name().toLowerCase(Locale.ROOT)
                    + (part.on() ? " off" : " on"));
        } else click(player, false);
    }

    /** Closes the menu and puts an addon's link in chat: its page, or its releases for an update. */
    private void sendLink(Player player, AddonCatalogue.Entry entry, String update) {
        click(player, true);
        String url = update == null ? entry.url() : entry.url() + "/releases";
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            player.sendMessage(Component.text("[EZvac] ", NamedTextColor.GOLD)
                    .append(Component.text(entry.name() + (update == null ? ": " : " " + update + ": "),
                            NamedTextColor.YELLOW))
                    .append(Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))));
        });
    }

    /** Opening another inventory from inside a click is done a tick later, as Bukkit asks. */
    private void reopen(Player player, Page page) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.hasPermission(ADMIN)) open(player, page);
        });
    }

    private void clickDevices(Player player, int slot) {
        int index = indexOf(DEVICE_SLOTS, switchedAt(slot));
        if (index < 0) return;
        DeviceKind kind = DeviceKind.values()[index];
        boolean switchOff = !switches.isOff(kind);
        switches.setOff(kind, switchOff);
        click(player, true);
        logChange(player, "switched " + kind.plural().toLowerCase(Locale.ROOT)
                + (switchOff ? " off" : " on") + " server-wide");
        plugin.requestImmediateSync();
        for (ClimateUnit unit : registry.climateUnits())
            if (DeviceKind.of(unit.kind()) == kind) displays.refreshClimateUnit(unit, true);
        if (kind == DeviceKind.WEATHER_STATION)
            for (WeatherStation station : registry.weatherStations()) displays.refreshWeatherStation(station, true);
    }

    private void clickClimate(Player player, int slot) {
        switch (switchedAt(slot)) {
            case AIR -> {
                switches.setAirQuality(!switches.airQuality());
                logChange(player, "switched air quality " + (switches.airQuality() ? "on" : "off"));
            }
            case POLLEN -> {
                if (!switches.airQuality()) {
                    click(player, false);
                    return;
                }
                switches.setPollen(!switches.pollen());
                logChange(player, "switched pollen " + (switches.pollen() ? "on" : "off"));
            }
            case WIND -> {
                switches.setWind(!switches.wind());
                logChange(player, "switched the wind " + (switches.wind() ? "on" : "off"));
            }
            case SEASONS -> {
                switches.setSeasons(!switches.seasons());
                logChange(player, "switched the seasons " + (switches.seasons() ? "on" : "off"));
            }
            case SIZING -> {
                switches.setSizing(!switches.sizing());
                // The outdoor reach changes every air system's map.
                airflow.invalidateAllSystems();
                logChange(player, "switched building size " + (switches.sizing() ? "on" : "off"));
            }
            default -> {
                return;
            }
        }
        click(player, true);
        plugin.requestImmediateSync();
        displays.refreshAll(true);
    }

    /**
     * How hvac.yml read at this start: fine, upgraded, recovered from a
     * backup, or with entries kept aside, listed. With some kept aside, a
     * click arms the discard and a second does it, like /hvac registry repair
     * --force.
     */
    private ItemStack registryItem(boolean discardArmed) {
        List<HvacRegistry.KeptAside> aside = registry.keptAsideRecords();
        List<Component> lore = new ArrayList<>();
        // When it was last saved: on the Statistics page until BETA 1.0's registry health joined the backups.
        File saved = new File(plugin.getDataFolder(), "hvac.yml");
        lore.add(gray(saved.isFile() ? "Saved " + ago(saved.lastModified()) : "Not saved yet"));
        lore.add(gray(registry.isWritable() ? "Saving normally" : "Not saving: hvac.yml could not be read"));
        NamedTextColor color = NamedTextColor.GREEN;
        String state = "read fine";
        if (registry.recoveredFrom() != null) {
            state = "recovered";
            color = NamedTextColor.GOLD;
            lore.add(gray("Unreadable at this start: recovered"));
            lore.add(gray("from " + registry.recoveredFrom()));
            lore.add(gray("The unreadable file: " + registry.setAside()));
        } else if (registry.migratedFrom() > 0) {
            state = "upgraded";
            lore.add(gray("Upgraded from format " + registry.migratedFrom() + " at this start"));
        } else {
            lore.add(gray("Read fine at this start"));
        }
        if (aside.isEmpty()) {
            lore.add(gray("Nothing is kept aside"));
        } else {
            color = NamedTextColor.GOLD;
            state = aside.size() + " kept aside";
            lore.add(gray(aside.size() + (aside.size() == 1 ? " entry couldn't be loaded" : " entries couldn't be loaded")
                    + ", not running:"));
            for (HvacRegistry.KeptAside entry : aside.subList(0, Math.min(LISTED_ASIDE, aside.size()))) {
                String reason = entry.reason().length() > 40 ? entry.reason().substring(0, 39) + "…" : entry.reason();
                lore.add(gray(" " + entry.section() + " " + entry.entry() + ": " + reason));
            }
            if (aside.size() > LISTED_ASIDE) lore.add(gray(" and " + (aside.size() - LISTED_ASIDE) + " more (/hvac registry)"));
            lore.add(gray("hvac.yml is copied aside first"));
            lore.add(discardArmed
                    ? Component.text("Click again to discard " + (aside.size() == 1 ? "it" : "them") + " for good",
                    NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                    : Component.text("Click to discard " + (aside.size() == 1 ? "it" : "them") + " (asks again)",
                    NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        return item(Material.BOOK, Component.text("hvac.yml: " + state, color), lore);
    }

    private void clickBackups(Player player, int slot, Menu menu) {
        if (slot != REGISTRY) menu.discardArmed = false;
        if (slot == REGISTRY) {
            if (registry.keptAside() == 0) {
                click(player, false);
            } else if (!menu.discardArmed) {
                menu.discardArmed = true;
                click(player, true);
            } else {
                menu.discardArmed = false;
                try {
                    HvacRegistry.Repair repaired = registry.repairKeptAside();
                    click(player, true);
                    logChange(player, "discarded " + repaired.discarded() + " kept-aside entr"
                            + (repaired.discarded() == 1 ? "y" : "ies") + " from hvac.yml (copied first to "
                            + repaired.copy() + ")");
                    player.sendMessage(Component.text("[EZvac] ", NamedTextColor.GOLD).append(Component.text(
                            "Discarded " + repaired.discarded() + " entr" + (repaired.discarded() == 1 ? "y" : "ies")
                                    + ". hvac.yml as it was is kept as " + repaired.copy() + ".", NamedTextColor.GREEN)));
                } catch (java.io.IOException failure) {
                    click(player, false);
                    player.sendMessage(Component.text("[EZvac] ", NamedTextColor.GOLD).append(Component.text(
                            "Couldn't copy hvac.yml aside, so nothing was discarded: " + failure.getMessage(),
                            NamedTextColor.RED)));
                }
            }
        } else if (slot == BACK_UP_NOW) {
            click(player, true);
            backups.backUpNow((name, failure) -> {
                if (name != null) {
                    plugin.getLogger().info(player.getName() + " backed up the registry: backups/" + name);
                    if (player.isOnline()) player.sendMessage(Component.text("[EZvac] ", NamedTextColor.GOLD)
                            .append(Component.text("Backed up to backups/" + name + ".", NamedTextColor.GREEN)));
                } else if (player.isOnline()) player.sendMessage(Component.text("[EZvac] ", NamedTextColor.GOLD)
                        .append(Component.text("The backup failed: " + failure + ".", NamedTextColor.RED)));
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() instanceof Menu open
                        && open.page == Page.BACKUPS) draw(open);
            });
        } else if (slot == AUTOMATIC) {
            int next = RegistryBackups.nextInterval(backups.intervalHours());
            backups.setIntervalHours(next);
            click(player, true);
            logChange(player, "set automatic backups to " + RegistryBackups.describe(next).toLowerCase(Locale.ROOT));
        }
    }

    /** True when the menu closed. */
    private boolean clickUpdates(Player player, int slot) {
        int at = switchedAt(slot);
        if (slot == CHECK_NOW) {
            click(player, true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                updates.checkNow(player);
            });
            return true;
        }
        if (slot == CHANNEL) {
            String setting = nextChannel(updates.channelSetting());
            updates.setChannel(setting);
            plugin.getConfig().set("updates.channel", setting);
            plugin.saveConfig();
            logChange(player, "set the update channel to " + setting + " (" + updates.channel().displayName() + ")");
            click(player, true);
            return false;
        }
        if (at == BACKGROUND) {
            boolean on = !updates.checking();
            updates.setChecking(on);
            plugin.getConfig().set("updates.check", on);
            plugin.saveConfig();
            logChange(player, "switched background update checks " + (on ? "on" : "off"));
        } else if (at == TELL_ADMINS) {
            boolean on = !updates.notifyAdmins();
            updates.setNotifyAdmins(on);
            plugin.getConfig().set("updates.notify-admins", on);
            plugin.saveConfig();
            logChange(player, "switched update notices for admins " + (on ? "on" : "off"));
        } else return false;
        click(player, true);
        return false;
    }

    /**
     * Each system's size and what it has of the units it needs, heating and
     * cooling, the worst sized first (BETA 1.0).
     */
    private List<Component> sizeLines() {
        if (!switches.sizing()) return List.of(gray("Building size is off:"), gray("any size heats as before"));
        List<com.github.blade.hvac.service.HvacController.SizeLine> lines = plugin.controller().sizeLines();
        if (lines.isEmpty()) return List.of(gray("No systems yet"));
        List<Component> out = new ArrayList<>();
        out.add(gray("Has/needs, in three-star units"));
        for (int i = 0; i < Math.min(LISTED_SYSTEMS, lines.size()); i++)
            out.add(Component.text(sizeText(lines.get(i)), lines.get(i).worst() < 1.0 ? NamedTextColor.GOLD
                    : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (lines.size() > LISTED_SYSTEMS) out.add(gray("and " + (lines.size() - LISTED_SYSTEMS) + " more"));
        return out;
    }

    /** {@code Home1: 4,800 air · cool 1/2.6 · heat 2/2.6}, or that it isn't mapped yet. */
    public static String sizeText(com.github.blade.hvac.service.HvacController.SizeLine line) {
        if (line.cooling() == null) return line.name() + ": not mapped yet";
        StringBuilder text = new StringBuilder(line.name() + ": " + String.format(Locale.US, "%,d",
                line.cooling().size()) + (line.cooling().water() ? " water" : " air"));
        if (!line.cooling().water() && line.cooling().openings() > 0)
            text.append(", ").append(line.cooling().openings()).append(" open");
        if (line.cooling().installed() > 0.0) text.append(" · cool ").append(line.cooling().ratio());
        if (line.heating().installed() > 0.0) text.append(" · heat ").append(line.heating().ratio());
        return text.toString();
    }

    private void logChange(Player player, String what) {
        plugin.getLogger().info(player.getName() + " " + what + " (/hvac quicksettings).");
    }

    private long count(DeviceKind kind) {
        if (kind == DeviceKind.WEATHER_STATION) return registry.weatherStations().size();
        return registry.equipment().stream().filter(unit -> DeviceKind.of(unit.type()) == kind).count()
                + registry.climateUnits().stream().filter(unit -> DeviceKind.of(unit.kind()) == kind).count();
    }

    private long running(DeviceKind kind) {
        if (kind == DeviceKind.WEATHER_STATION)
            return registry.weatherStations().stream().filter(station -> station.enabled() && station.intact()).count();
        return registry.equipment().stream().filter(unit -> DeviceKind.of(unit.type()) == kind)
                .filter(EquipmentUnit::running).count()
                + registry.climateUnits().stream().filter(unit -> DeviceKind.of(unit.kind()) == kind)
                .filter(ClimateUnit::running).count();
    }

    /** The icon a click was on: a switch's dye, in the third row, stands for the icon above it. */
    static int switchedAt(int slot) {
        return slot >= 18 && slot < 27 ? slot - 9 : slot;
    }

    private static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return i;
        return -1;
    }

    /** "just now", "12 minutes ago", "3 hours ago", "2 days ago". */
    static String ago(long at) {
        long minutes = Math.max(0L, (System.currentTimeMillis() - at) / 60_000L);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 48) return hours + (hours == 1 ? " hour ago" : " hours ago");
        return hours / 24 + " days ago";
    }

    /** Closes every open quick settings menu when EZvac stops, so nothing can be taken out of it. */
    public void closeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }
}
