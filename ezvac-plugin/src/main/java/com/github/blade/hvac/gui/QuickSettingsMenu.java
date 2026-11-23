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
import com.github.blade.hvac.lang.Lang;
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
import static com.github.blade.hvac.gui.ControlMenus.lore;
import static com.github.blade.hvac.lang.Lang.arg;

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
        MAIN("EZvac quick settings", 36), DEVICES("Quick settings › Devices", 54),
        CLIMATE("Quick settings › Climate systems", 36), BACKUPS("Quick settings › Backups & registry", 36),
        STATISTICS("Quick settings › Health", 36), UPDATES("Quick settings › Updates", 36),
        SEASONS("Quick settings › Seasons & wind", 36), DISPLAY("Quick settings › Display & sound", 36),
        ADDONS("Quick settings › Addons", 54), ADDON_PARTS("Quick settings › Addon", 36),
        SHARING("Quick settings › Anonymous statistics", 36);

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
    /** Anonymous statistics (BETA 1.0 Pilot 9), under the others. */
    static final int SHARING_SECTION = 31;
    /**
     * The statistics page: what always goes while they're on, the switch for
     * all of them, and one for each of EZvac's own charts, lights beneath.
     */
    static final int ALWAYS_SENT = 4, SHARE_ALL = 10, SHARE_UNITS = 12, SHARE_UNLOADED = 13, SHARE_SYSTEMS = 14,
            SHARE_ADDONS = 15;
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
    /** The kinds as the Devices page shows them: the climate devices in a row, fridges and freezers below (Pilot 7). */
    static final DeviceKind[] DEVICE_ORDER = {DeviceKind.HEAT_PUMP, DeviceKind.AIR_CONDITIONER, DeviceKind.FURNACE,
            DeviceKind.BOILER, DeviceKind.POOL_HEAT_PUMP, DeviceKind.DEHUMIDIFIER, DeviceKind.HUMIDIFIER,
            DeviceKind.AIR_PURIFIER, DeviceKind.WEATHER_STATION, DeviceKind.MINI_FRIDGE, DeviceKind.FRIDGE,
            DeviceKind.FRIDGE_FREEZER, DeviceKind.FREEZER};
    static final int[] DEVICE_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17, 28, 30, 32, 34};
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
        menu.inventory = Bukkit.createInventory(menu, page.size, Lang.message(Lang.key("menu.qs.title", page.name())));
        draw(menu);
        player.openInventory(menu.inventory);
    }

    private void draw(Menu menu) {
        Inventory inventory = menu.inventory;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        inventory.setItem(menu.page.close(), item(Material.BARRIER, Lang.message("menu.close"), List.of()));
        if (menu.page != Page.MAIN)
            inventory.setItem(menu.page.back(), item(Material.ARROW, Lang.message("menu.back"),
                    List.of(lore(menu.page == Page.ADDON_PARTS ? "menu.qs.back-addons" : "menu.qs.back-main"))));
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
            case SHARING -> drawSharing(inventory);
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
            inventory.setItem(WORLD_INFO, item(Material.BARRIER, Lang.message("menu.qs.seasons.no-world"),
                    List.of(lore("menu.qs.seasons.no-world-lore"))));
            return;
        }
        inventory.setItem(WORLD_INFO, item(Material.GRASS_BLOCK,
                Lang.message("menu.qs.seasons.world", arg("world", world.getName())),
                List.of(lore("menu.qs.seasons.world-1"), lore("menu.qs.seasons.world-2"),
                        lore("menu.qs.seasons.world-3"))));
        com.github.blade.hvac.service.SeasonService seasons = plugin.seasons();
        if (!switches.seasons() || seasons == null || !seasons.tracks(worldId)) {
            inventory.setItem(SEASON_NOW, item(Material.OAK_SAPLING, Lang.message("menu.qs.seasons.off"),
                    List.of(lore(switches.seasons() ? "menu.qs.seasons.not-started" : "menu.qs.seasons.switch-on"))));
        } else {
            int days = seasons.settings().daysPerSeason(), day = seasons.dayOfSeason(worldId);
            com.github.blade.hvac.simulation.Seasons.Season season = seasons.season(worldId);
            boolean paused = seasons.paused(worldId);
            inventory.setItem(SEASON_NOW, item(seasonIcon(season), Lang.message("menu.qs.seasons.now",
                            arg("season", Lang.text("command.season.day", arg("season", season.displayName()),
                                    arg("day", day), arg("days", days)))),
                    List.of(lore(paused ? "menu.qs.seasons.paused" : "menu.qs.seasons.running"),
                            lore("menu.qs.seasons.year", arg("days", days * 4)))));
            inventory.setItem(DAY_BACK, item(Material.RED_CANDLE, Lang.message("menu.qs.seasons.day-back"),
                    List.of(day > 1 ? lore("menu.qs.seasons.to-day", arg("day", day - 1))
                            : lore("menu.qs.seasons.first-day"))));
            inventory.setItem(DAY_ON, item(Material.LIME_CANDLE, Lang.message("menu.qs.seasons.day-on"),
                    List.of(day < days ? lore("menu.qs.seasons.to-day", arg("day", day + 1))
                            : lore("menu.qs.seasons.to-season", arg("season", season.next().displayName())))));
            inventory.setItem(NEXT_SEASON, item(seasonIcon(season.next()), Lang.message("menu.qs.seasons.next"),
                    List.of(lore("menu.qs.seasons.to-season", arg("season", season.next().displayName())))));
            inventory.setItem(PAUSE, item(paused ? Material.LIME_DYE : Material.GRAY_DYE,
                    Lang.message(paused ? "menu.qs.seasons.resume" : "menu.qs.seasons.pause"),
                    List.of(lore(paused ? "menu.qs.seasons.resume-lore" : "menu.qs.seasons.pause-lore"))));
        }
        com.github.blade.hvac.service.WindService wind = plugin.outdoor().wind();
        if (!switches.wind() || wind == null || !com.github.blade.hvac.service.WindService.hasWind(world)) {
            inventory.setItem(WIND_NOW, item(Material.FEATHER, Lang.message("menu.qs.wind.off"),
                    List.of(lore("menu.qs.wind.switch-on"))));
            return;
        }
        com.github.blade.hvac.simulation.WindModel.Day today = wind.day(world);
        boolean held = wind.held(world);
        com.github.blade.hvac.simulation.WindModel.Level next = nextWind(held ? today.level() : null);
        inventory.setItem(WIND_NOW, item(Material.FEATHER, Lang.message("menu.qs.wind.today",
                        arg("level", today.level().displayName())),
                List.of(lore("menu.qs.wind.about", arg("speed", com.github.blade.hvac.service.WindText.speed(today.meanMs(),
                                com.github.blade.hvac.service.WindText.units(wind.settings(), plugin.settings().units())))),
                        lore(held ? "menu.qs.wind.held" : "menu.qs.wind.as-it-would"),
                        lore("menu.qs.click-for", arg("next", next == null ? Lang.text("menu.qs.wind.as-it-would-blow")
                                : next.displayName())))));
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
                Lang.message("menu.qs.display.units", arg("unit", unit.name())),
                List.of(lore("menu.qs.display.units-1"), lore("menu.qs.display.units-2"),
                        lore("menu.qs.click-for", arg("next", "°" + (fahrenheit ? "C" : "F"))))));
        com.github.blade.hvac.service.WindService wind = plugin.outdoor().wind();
        com.github.blade.hvac.config.WindSettings.Units speeds = wind == null
                ? com.github.blade.hvac.config.WindSettings.Units.AUTO : wind.settings().units();
        inventory.setItem(WIND_UNITS, item(Material.FEATHER,
                Lang.message("menu.qs.display.wind-units", arg("units", speeds.displayName())),
                List.of(lore("menu.qs.display.wind-units-auto"),
                        lore("menu.qs.click-for", arg("next", nextWindUnits(speeds).displayName())))));
        boolean sounds = plugin.settings().audioEnabled();
        placeSwitch(inventory, SOUNDS, Material.NOTE_BLOCK, Lang.text("menu.qs.display.sounds"), sounds, List.of(
                lore("menu.qs.display.sounds-1"), lore("menu.qs.display.sounds-2"), clickTo(sounds)));

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
                Lang.message("menu.qs.addons.title"), List.of(
                        lore("menu.qs.addons.counts", arg("installed", installed.size()), arg("store", store.size())),
                        lore("menu.qs.addons.help-1"), lore("menu.qs.addons.help-2"),
                        lore("menu.qs.addons.help-3"), lore("menu.qs.addons.help-4"),
                        lore("menu.qs.addons.help-5"))));
        builtIn(inventory);
        for (int i = 0; i < Math.min(ADDON_SLOTS, installed.size()); i++) {
            AddonService.Installed addon = installed.get(i);
            AddonCatalogue.Entry entry = addon.entry();
            List<Component> lore = new ArrayList<>();
            lore.add(lore("menu.qs.addons.version", arg("version", addon.version())));
            if (addon.update() != null) lore.add(lore("menu.qs.addons.update", arg("version", addon.update())));
            describe(lore, entry);
            if (entry.folded()) {
                AddonCatalogue.Entry into = addons.entry(entry.foldedInto());
                lore.add(lore("menu.qs.addons.legacy"));
                lore.add(lore("menu.qs.addons.part-of", arg("addon", into == null ? entry.foldedInto() : into.name())));
            }
            boolean on = addon.running() && addon.active();
            if (!addon.running()) lore.add(lore("menu.qs.addons.not-running"));
            else if (!addon.switchable()) {
                lore.add(lore("menu.qs.addons.not-switchable-1"));
                lore.add(lore("menu.qs.addons.not-switchable-2"));
            } else {
                if (!addon.parts().isEmpty()) {
                    long partsOn = addon.parts().stream().filter(AddonService.PartView::on).count();
                    lore.add(lore("menu.qs.addons.parts-on", arg("on", partsOn), arg("parts", addon.parts().size())));
                    lore.add(lore("menu.qs.addons.click-parts"));
                    lore.add(lore(addon.active() ? "menu.qs.addons.dye-off" : "menu.qs.addons.dye-on"));
                } else lore.add(clickTo(addon.active()));
            }
            if (addon.update() != null) lore.add(lore("menu.qs.addons.shift-link"));
            placeSwitch(inventory, ADDONS_FROM + i, icon(entry), entry.name(), on, lore);
        }
        if (installed.isEmpty())
            inventory.setItem(ADDONS_FROM + 2, item(Material.GLASS_BOTTLE, Lang.message("menu.qs.addons.none"),
                    List.of(lore("menu.qs.addons.store-below"))));
        for (int i = 0; i < Math.min(ADDONS_ROW, store.size()); i++) {
            AddonCatalogue.Entry entry = store.get(i);
            List<Component> lore = new ArrayList<>();
            describe(lore, entry);
            if (!entry.needs().isEmpty()) lore.add(lore("menu.qs.addons.needs", arg("needs", entry.needs())));
            if (!addons.runsHere(entry))
                lore.add(lore("menu.qs.addons.needs-minecraft", arg("minecraft", entry.minecraft())));
            if (entry.folded()) {
                AddonCatalogue.Entry into = addons.entry(entry.foldedInto());
                lore.add(lore("menu.qs.addons.part-of", arg("addon", into == null ? entry.foldedInto() : into.name())));
            }
            lore.add(lore("menu.qs.addons.click-link"));
            inventory.setItem(STORE_FROM + i, item(icon(entry),
                    Component.text(entry.name(), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), lore));
        }
        if (store.isEmpty())
            inventory.setItem(STORE_FROM + 3, item(Material.EMERALD, Lang.message("menu.qs.addons.all-installed"), List.of()));
        List<String> news = addons.updates();
        inventory.setItem(CHECK_ADDONS, item(Material.SPYGLASS,
                Lang.message("menu.qs.check-now"), List.of(
                        lore("menu.qs.addons.check-1"), lore("menu.qs.addons.check-2"),
                        news.isEmpty() ? lore("menu.qs.addons.no-updates")
                                : lore("menu.qs.addons.updates", arg("updates", String.join(", ", news))),
                        lore(updates.checking() ? "menu.qs.addons.also-6h" : "menu.qs.addons.background-off"))));
    }

    /**
     * EZvac's built-in EZdebug, first in the installed row: on or off, or
     * replaced while EZdebug+ is installed and switched on.
     */
    private void builtIn(Inventory inventory) {
        boolean on = switches.ezdebug();
        boolean replaced = plugin.ezdebug() != null && on && !plugin.ezdebug().active();
        List<Component> lore = new ArrayList<>();
        lore.add(lore("menu.qs.ezdebug.1"));
        lore.add(lore("menu.qs.ezdebug.2"));
        lore.add(lore("menu.qs.ezdebug.3"));
        lore.add(lore("menu.qs.ezdebug.4"));
        if (replaced) {
            lore.add(lore("menu.qs.ezdebug.replaced-1"));
            lore.add(lore("menu.qs.ezdebug.replaced-2"));
        } else lore.add(clickTo(on));
        Component title = Lang.message(replaced ? "menu.qs.ezdebug.title-replaced"
                : on ? "menu.qs.ezdebug.title-on" : "menu.qs.ezdebug.title-off");
        inventory.setItem(BUILT_IN, item(Material.SPYGLASS, title, lore));
        inventory.setItem(BUILT_IN + 9, item(replaced ? Material.ORANGE_DYE : on ? Material.LIME_DYE : Material.RED_DYE,
                title, lore));
    }

    /** One addon's parts, each with its switch beneath; they only run while the addon itself is on. */
    private void drawAddonParts(Inventory inventory, String pluginName) {
        AddonService.Installed addon = installed(pluginName);
        if (addon == null) {
            inventory.setItem(ADDON_INFO, item(Material.BARRIER, Lang.message("menu.qs.addons.gone"), List.of()));
            return;
        }
        List<Component> lore = new ArrayList<>();
        lore.add(lore("menu.qs.addons.version", arg("version", addon.version())));
        describe(lore, addon.entry());
        lore.add(lore(addon.active() ? "menu.qs.addons.on" : "menu.qs.addons.off-waiting"));
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
        inventory.setItem(DEVICES, section(Material.DISPENSER, Lang.text("menu.qs.main.devices"),
                off == 0 ? Lang.text("menu.qs.main.devices-all-on") : Lang.text("menu.qs.main.devices-off", arg("count", off)),
                Lang.text("menu.qs.main.devices-what")));
        int systemsOff = (switches.airQuality() ? 0 : 1) + (switches.pollen() ? 0 : 1) + (switches.wind() ? 0 : 1)
                + (switches.seasons() ? 0 : 1) + (switches.sizing() ? 0 : 1);
        inventory.setItem(CLIMATE, section(Material.SUNFLOWER, Lang.text("menu.qs.main.climate"),
                systemsOff == 0 ? Lang.text("menu.qs.main.all-on") : Lang.text("menu.qs.main.switched-off", arg("count", systemsOff)),
                Lang.text("menu.qs.main.climate-what")));
        inventory.setItem(BACKUPS, section(Material.CHEST, Lang.text("menu.qs.main.backups"),
                Lang.text("menu.qs.main.automatic", arg("interval", RegistryBackups.describe(backups.intervalHours()))),
                Lang.text("menu.qs.main.backups-what")));
        List<com.github.blade.hvac.service.TickCost.Part> cost = plugin.tickCost().lastMinute();
        inventory.setItem(STATISTICS, section(Material.BOOK, Lang.text("menu.qs.main.health"),
                cost.isEmpty() ? Lang.text("menu.qs.main.health-counts", arg("thermostats", registry.thermostats().size()),
                        arg("units", registry.equipment().size()))
                        : Lang.text("menu.qs.main.health-cost", arg("ms", String.format(Locale.US, "%.2f",
                        com.github.blade.hvac.service.TickCost.total(cost)))),
                Lang.text("menu.qs.main.health-what")));
        org.bukkit.World here = plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0);
        inventory.setItem(SEASONS_AND_WIND, section(Material.OAK_SAPLING, Lang.text("menu.qs.main.seasons"),
                plugin.seasons() != null && switches.seasons() && here != null && plugin.seasons().tracks(here.getUID())
                        ? Lang.text("menu.qs.main.season-in", arg("season", plugin.seasons().season(here.getUID()).displayName()),
                        arg("day", plugin.seasons().dayOfSeason(here.getUID())), arg("world", here.getName()))
                        : Lang.text("menu.qs.main.seasons-state"),
                Lang.text("menu.qs.main.seasons-what")));
        inventory.setItem(DISPLAY, section(Material.PAINTING, Lang.text("menu.qs.main.display"),
                Lang.text(plugin.settings().audioEnabled() ? "menu.qs.main.display-sounds-on" : "menu.qs.main.display-sounds-off",
                        arg("unit", plugin.settings().units().name())),
                Lang.text("menu.qs.main.display-what")));
        inventory.setItem(UPDATES, section(Material.CLOCK, Lang.text("menu.qs.main.updates"),
                Lang.text("menu.qs.main.running", arg("running", updates.runningName())),
                Lang.text("menu.qs.main.updates-what")));
        int installed = addons.installed().size(), news = addons.updates().size();
        inventory.setItem(ADDONS, section(Material.BOOKSHELF, Lang.text("menu.qs.main.addons"),
                news == 0 ? Lang.text("menu.qs.main.installed", arg("count", installed))
                        : Lang.text("menu.qs.main.installed-updates", arg("count", installed), arg("updates", news)),
                Lang.text("menu.qs.main.addons-what")));
        inventory.setItem(SHARING_SECTION, section(Material.COMPASS, Lang.text("menu.qs.main.sharing"),
                Lang.text(plugin.usageStatsOn() ? "menu.qs.main.sharing-on" : "menu.qs.main.sharing-off"),
                Lang.text("menu.qs.main.sharing-what")));
    }

    /**
     * What EZvac sends to bStats (BETA 1.0 Pilot 9): bStats' own figures,
     * which go together while statistics are on, and each of EZvac's charts
     * on its own switch.
     */
    private void drawSharing(Inventory inventory) {
        boolean on = plugin.usageStatsOn();
        List<Component> always = new ArrayList<>();
        for (int line = 1; line <= 9; line++) always.add(lore("menu.qs.sharing.always-" + line));
        inventory.setItem(ALWAYS_SENT, item(Material.PAPER, Lang.message("menu.qs.sharing.always"), always));
        placeSwitch(inventory, SHARE_ALL, Material.COMPASS, Lang.text("menu.qs.main.sharing"), on, List.of(
                lore("menu.qs.sharing.all-1"), lore("menu.qs.sharing.all-2"), clickTo(on)));
        shareSwitch(inventory, SHARE_UNITS, Material.PAINTING, Lang.text("menu.qs.sharing.units"), "temperature-unit", on,
                Lang.text("menu.qs.sharing.units-what"));
        shareSwitch(inventory, SHARE_UNLOADED, Material.MAP, Lang.text("menu.qs.sharing.unloaded"), "unloaded-devices", on,
                Lang.text("menu.qs.sharing.unloaded-what"));
        shareSwitch(inventory, SHARE_SYSTEMS, Material.IRON_TRAPDOOR, Lang.text("menu.qs.sharing.systems"), "systems", on,
                Lang.text("menu.qs.sharing.systems-what"));
        shareSwitch(inventory, SHARE_ADDONS, Material.BOOKSHELF, Lang.text("menu.qs.sharing.addons"), "addons", on,
                Lang.text("menu.qs.sharing.addons-what"));
    }

    private void shareSwitch(Inventory inventory, int slot, Material icon, String name, String chart, boolean all,
                             String what) {
        boolean on = plugin.statOn(chart);
        placeSwitch(inventory, slot, icon, name, on, List.of(gray(what),
                lore(all ? "menu.qs.sharing.sent" : "menu.qs.sharing.off"), clickTo(on)));
    }

    private void clickSharing(Player player, int slot) {
        int at = switchedAt(slot);
        String chart = switch (at) {
            case SHARE_UNITS -> "temperature-unit";
            case SHARE_UNLOADED -> "unloaded-devices";
            case SHARE_SYSTEMS -> "systems";
            case SHARE_ADDONS -> "addons";
            default -> null;
        };
        if (at == SHARE_ALL) {
            boolean on = !plugin.usageStatsOn();
            plugin.setUsageStats(on);
            logChange(player, "switched anonymous statistics " + (on ? "on" : "off"));
        } else if (chart != null) {
            boolean on = !plugin.statOn(chart);
            plugin.setStat(chart, on);
            logChange(player, "switched " + chart + " in the anonymous statistics " + (on ? "on" : "off"));
        } else return;
        click(player, true);
    }

    private static ItemStack section(Material icon, String name, String state, String what) {
        return item(icon, Component.text(name, NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(gray(state), gray(what), lore("menu.qs.click-open")));
    }

    private void drawDevices(Inventory inventory) {
        DeviceKind[] kinds = DEVICE_ORDER;
        for (int i = 0; i < kinds.length; i++) {
            DeviceKind kind = kinds[i];
            boolean on = !switches.isOff(kind);
            List<Component> lore = new ArrayList<>();
            lore.add(lore("menu.qs.devices.count", arg("count", count(kind)), arg("running", running(kind))));
            lore.add(lore(on ? "menu.qs.devices.off-means" : "menu.qs.devices.on-means"));
            lore.add(clickTo(on));
            placeSwitch(inventory, DEVICE_SLOTS[i], kind.icon(), kind.plural(), on, lore);
        }
    }

    private void drawClimate(Inventory inventory) {
        placeSwitch(inventory, AIR, Material.CAMPFIRE, Lang.text("menu.qs.climate.air"), switches.airQuality(), List.of(
                lore("menu.qs.climate.air-1"), lore("menu.qs.climate.air-2"),
                clickTo(switches.airQuality())));
        if (!switches.airQuality())
            placeSwitch(inventory, POLLEN, Material.DANDELION, Lang.text("menu.qs.climate.pollen"), false, List.of(
                    lore("menu.qs.climate.pollen-with-air"), lore("menu.qs.climate.pollen-air-first")));
        else
            placeSwitch(inventory, POLLEN, Material.DANDELION, Lang.text("menu.qs.climate.pollen"), switches.pollen(), List.of(
                    lore("menu.qs.climate.pollen-1"), lore("menu.qs.climate.pollen-2"),
                    clickTo(switches.pollen())));
        placeSwitch(inventory, WIND, Material.FEATHER, Lang.text("menu.qs.climate.wind"), switches.wind(), List.of(
                lore("menu.qs.climate.wind-1"), lore("menu.qs.climate.wind-2"),
                clickTo(switches.wind())));
        placeSwitch(inventory, SEASONS, Material.OAK_SAPLING, Lang.text("menu.qs.climate.seasons"), switches.seasons(), List.of(
                lore("menu.qs.climate.seasons-1"), lore("menu.qs.climate.seasons-2"),
                clickTo(switches.seasons())));
        placeSwitch(inventory, SIZING, Material.BRICKS, Lang.text("menu.qs.climate.sizing"), switches.sizing(), List.of(
                lore("menu.qs.climate.sizing-1"), lore("menu.qs.climate.sizing-2", arg("cells", String.format(Locale.US, "%,d",
                        plugin.controller().sizing().cellsPerUnit()))),
                lore("menu.qs.climate.sizing-3"), clickTo(switches.sizing())));
    }

    private void drawBackups(Inventory inventory, boolean discardArmed) {
        inventory.setItem(REGISTRY, registryItem(discardArmed));
        inventory.setItem(BACK_UP_NOW, item(Material.WRITABLE_BOOK,
                Lang.message("menu.qs.backups.now"),
                List.of(lore("menu.qs.backups.now-1"), lore("menu.qs.backups.now-2"),
                        lore("menu.qs.backups.now-3"))));
        int hours = backups.intervalHours();
        inventory.setItem(AUTOMATIC, item(Material.CLOCK, Lang.message("menu.qs.main.automatic",
                        arg("interval", RegistryBackups.describe(hours))).color(hours == 0 ? NamedTextColor.RED : NamedTextColor.GREEN),
                List.of(lore("menu.qs.backups.real-time"), lore("menu.qs.backups.keep", arg("keep", backups.keep())),
                        lore("menu.qs.click-for", arg("next", RegistryBackups.describe(RegistryBackups.nextInterval(hours)))))));
        RegistryBackups.Summary summary = backups.summary();
        List<Component> lore = new ArrayList<>();
        if (summary.count() == 0) lore.add(lore("menu.qs.backups.none"));
        else {
            lore.add(lore("menu.qs.backups.newest", arg("name", summary.newest())));
            lore.add(lore("menu.qs.backups.made", arg("ago", ago(summary.newestAt()))));
            lore.add(lore("menu.qs.backups.count", arg("count", summary.count())));
        }
        lore.add(lore("menu.qs.backups.restore-1"));
        lore.add(lore("menu.qs.backups.restore-2"));
        inventory.setItem(BACKUP_INFO, item(Material.PAPER, Lang.message("menu.qs.backups.title"), lore));
    }

    private void drawStatistics(Inventory inventory) {
        List<Component> devices = new ArrayList<>();
        for (DeviceKind kind : DeviceKind.values())
            devices.add(lore(switches.isOff(kind) ? "menu.qs.health.kind-off" : "menu.qs.health.kind",
                    arg("kinds", kind.plural()), arg("count", count(kind)), arg("running", running(kind))));
        inventory.setItem(DEVICE_COUNTS, item(Material.DISPENSER, Lang.message("menu.qs.health.devices"), devices));
        long enabled = registry.thermostats().stream().filter(Thermostat::enabled).count();
        inventory.setItem(SYSTEM_COUNTS, item(Material.OAK_SIGN, Lang.message("menu.qs.health.systems"), List.of(
                lore("menu.qs.health.thermostats", arg("count", registry.thermostats().size()), arg("on", enabled)),
                lore("menu.qs.health.vents", arg("count", registry.vents().size())),
                lore("menu.qs.health.panels", arg("count", registry.settingsPanels().size())),
                lore("menu.qs.health.thermometers", arg("count", registry.thermometers().size())),
                lore("menu.qs.health.monitors", arg("count", registry.rpmMonitors().size())),
                lore("menu.qs.health.sensors", arg("count", registry.sensors().size()), arg("hubs", registry.sensorHubs().size())))));
        inventory.setItem(SIZES, item(Material.BRICKS, Lang.message("menu.qs.health.sizes"), sizeLines()));
        AirflowService.Figures figures = airflow.figures();
        inventory.setItem(AIRFLOW, item(Material.COMPASS, Lang.message("menu.qs.health.airflow"), List.of(
                lore("menu.qs.health.cells", arg("count", figures.cachedCells())),
                lore(figures.rebuilding() ? "menu.qs.health.waiting-mapping" : "menu.qs.health.waiting",
                        arg("count", figures.waiting())),
                lore("menu.qs.health.maps", arg("count", figures.completed())),
                lore("menu.qs.health.visited", arg("count", figures.visited())),
                lore("menu.qs.health.capped", arg("count", figures.capped())))));
        inventory.setItem(REFRESH, item(Material.SPYGLASS, Lang.message("menu.qs.health.refresh"),
                List.of(lore("menu.qs.health.refresh-lore"))));
        // What each part of EZvac costs the tick, over the last minute (BETA 1.0 Pilot 4).
        List<com.github.blade.hvac.service.TickCost.Part> cost = plugin.tickCost().lastMinute();
        List<Component> lines = new ArrayList<>();
        if (cost.isEmpty()) lines.add(lore("menu.qs.health.measuring"));
        else {
            double total = com.github.blade.hvac.service.TickCost.total(cost);
            lines.add(colored(Lang.text("menu.qs.health.of-tick", arg("ms", String.format(Locale.US, "%.2f", total))),
                    total < 5.0 ? NamedTextColor.GREEN : total < 15.0 ? NamedTextColor.GOLD : NamedTextColor.RED));
            for (com.github.blade.hvac.service.TickCost.Part part : cost)
                lines.add(lore("menu.qs.health.part", arg("part", part.name()),
                        arg("ms", String.format(Locale.US, "%.3f", part.msPerTick())),
                        arg("longest", String.format(Locale.US, "%.1f", part.worstMs()))));
            lines.add(lore("menu.qs.health.averaged-1"));
            lines.add(lore("menu.qs.health.averaged-2"));
        }
        inventory.setItem(TICK_COST, item(Material.REDSTONE, Lang.message("menu.qs.health.tick-cost"), lines));
    }

    private void drawUpdates(Inventory inventory) {
        inventory.setItem(RUNNING, item(Material.NAME_TAG, Lang.message("menu.qs.updates.running",
                arg("running", updates.runningName())), List.of(lore("menu.qs.updates.minecraft",
                arg("minecraft", updates.minecraft())))));
        inventory.setItem(CHECK_NOW, item(Material.SPYGLASS, Lang.message("menu.qs.check-now"),
                List.of(lore("menu.qs.updates.check-1"), lore("menu.qs.updates.check-2"),
                        lore("menu.qs.updates.check-3"))));
        inventory.setItem(CHANNEL, channel(updates.channel(), updates.channelSetting()));
        placeSwitch(inventory, BACKGROUND, Material.REPEATER, Lang.text("menu.qs.updates.background"), updates.checking(), List.of(
                lore("menu.qs.updates.background-1"), clickTo(updates.checking())));
        placeSwitch(inventory, TELL_ADMINS, Material.BELL, Lang.text("menu.qs.updates.tell"), updates.notifyAdmins(), List.of(
                lore("menu.qs.updates.tell-1"), clickTo(updates.notifyAdmins())));
    }

    /**
     * The update channel: which it is, what each hears about, and whether it
     * was chosen or follows the build (auto).
     */
    static ItemStack channel(UpdateCheck.Channel channel, String setting) {
        boolean auto = setting.equals("auto");
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text((channel == UpdateCheck.Channel.STABLE ? "● " : "  ")
                + Lang.text("menu.qs.channel.stable"), channel == UpdateCheck.Channel.STABLE
                ? NamedTextColor.GREEN : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text((channel == UpdateCheck.Channel.PILOT ? "● " : "  ")
                + Lang.text("menu.qs.channel.pilot"), channel == UpdateCheck.Channel.PILOT
                ? NamedTextColor.GOLD : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(lore(auto ? "menu.qs.channel.auto" : "menu.qs.channel.chosen"));
        lore.add(lore("menu.qs.channel.click"));
        return item(channel == UpdateCheck.Channel.PILOT ? Material.TRIAL_KEY : Material.TRIPWIRE_HOOK,
                Lang.message(auto ? "menu.qs.channel.title-auto" : "menu.qs.channel.title",
                        arg("channel", channel.displayName()))
                        .color(channel == UpdateCheck.Channel.PILOT ? NamedTextColor.GOLD : NamedTextColor.GREEN)
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
        Component title = Lang.message(on ? "menu.qs.switch-on" : "menu.qs.switch-off", arg("name", name));
        inventory.setItem(slot, item(icon, title, lore));
        inventory.setItem(slot + 9, item(on ? Material.LIME_DYE : Material.RED_DYE, title, lore));
    }

    private static Component clickTo(boolean on) {
        return lore(on ? "menu.qs.click-off" : "menu.qs.click-on");
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
                    case SHARING_SECTION -> Page.SHARING;
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
            case SHARING -> clickSharing(player, slot);
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
                player.sendMessage(Lang.message("update.prefix").append(Lang.message(!answered
                        ? "menu.qs.addons.unreachable" : news.isEmpty() ? "menu.qs.addons.up-to-date"
                        : "menu.qs.addons.updates-chat", arg("updates", String.join(", ", news)))));
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
            player.sendMessage(Lang.message("update.prefix").append(Lang.message("menu.qs.addons.switch-failed",
                    arg("addon", addon.entry().name()))));
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
            player.sendMessage(Lang.message("update.prefix")
                    .append(Lang.message(update == null ? "menu.qs.addons.link" : "menu.qs.addons.link-update",
                            arg("addon", entry.name()), arg("version", String.valueOf(update))))
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
        // Each kind is its icon and the switch under it.
        int index = indexOf(DEVICE_SLOTS, slot);
        if (index < 0) index = indexOf(DEVICE_SLOTS, slot - 9);
        if (index < 0) return;
        DeviceKind kind = DEVICE_ORDER[index];
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
        lore.add(saved.isFile() ? lore("menu.qs.registry.saved", arg("ago", ago(saved.lastModified())))
                : lore("menu.qs.registry.not-saved"));
        lore.add(lore(registry.isWritable() ? "menu.qs.registry.saving" : "menu.qs.registry.not-saving"));
        NamedTextColor color = NamedTextColor.GREEN;
        String state = Lang.text("menu.qs.registry.state.read-fine");
        if (registry.recoveredFrom() != null) {
            state = Lang.text("menu.qs.registry.state.recovered");
            color = NamedTextColor.GOLD;
            lore.add(lore("menu.qs.registry.recovered-1"));
            lore.add(lore("menu.qs.registry.recovered-2", arg("from", registry.recoveredFrom())));
            lore.add(lore("menu.qs.registry.recovered-3", arg("aside", registry.setAside())));
        } else if (registry.migratedFrom() > 0) {
            state = Lang.text("menu.qs.registry.state.upgraded");
            lore.add(lore("menu.qs.registry.upgraded", arg("from", registry.migratedFrom())));
        } else {
            lore.add(lore("menu.qs.registry.read-fine"));
        }
        if (aside.isEmpty()) {
            lore.add(lore("menu.qs.registry.nothing-aside"));
        } else {
            color = NamedTextColor.GOLD;
            state = Lang.text("menu.qs.registry.state.aside", arg("count", aside.size()));
            lore.add(lore(aside.size() == 1 ? "menu.qs.registry.aside-one" : "menu.qs.registry.aside-many",
                    arg("count", aside.size())));
            for (HvacRegistry.KeptAside entry : aside.subList(0, Math.min(LISTED_ASIDE, aside.size()))) {
                // Wrapped, never cut: why it couldn't be loaded is what the admin needs to read.
                lore.add(gray(" " + entry.section() + " " + entry.entry() + ":"));
                for (String line : com.github.blade.hvac.addons.AddonCatalogue.wrap(entry.reason(), 40))
                    lore.add(gray("   " + line));
            }
            if (aside.size() > LISTED_ASIDE) lore.add(lore("menu.qs.registry.more", arg("count", aside.size() - LISTED_ASIDE)));
            lore.add(lore("menu.qs.registry.copied-first"));
            lore.add(lore((discardArmed ? "menu.qs.registry.discard-again" : "menu.qs.registry.discard")
                    + (aside.size() == 1 ? "-one" : "-many")));
        }
        return item(Material.BOOK, Lang.message("menu.qs.registry.title", arg("state", state)).color(color), lore);
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
                    player.sendMessage(Lang.message("update.prefix").append(Lang.message(repaired.discarded() == 1
                            ? "command.registry.discarded.one" : "command.registry.discarded.many",
                            arg("count", repaired.discarded()), arg("copy", repaired.copy()))));
                } catch (java.io.IOException failure) {
                    click(player, false);
                    player.sendMessage(Lang.message("update.prefix").append(Lang.message("command.registry.copy-failed",
                            arg("reason", String.valueOf(failure.getMessage())))));
                }
            }
        } else if (slot == BACK_UP_NOW) {
            click(player, true);
            backups.backUpNow((name, failure) -> {
                if (name != null) {
                    plugin.getLogger().info(player.getName() + " backed up the registry: backups/" + name);
                    if (player.isOnline()) player.sendMessage(Lang.message("update.prefix")
                            .append(Lang.message("menu.qs.backups.done", arg("name", name))));
                } else if (player.isOnline()) player.sendMessage(Lang.message("update.prefix")
                        .append(Lang.message("menu.qs.backups.failed", arg("reason", String.valueOf(failure)))));
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
        if (!switches.sizing()) return List.of(lore("menu.qs.sizes.off-1"), lore("menu.qs.sizes.off-2"));
        List<com.github.blade.hvac.service.HvacController.SizeLine> lines = plugin.controller().sizeLines();
        if (lines.isEmpty()) return List.of(lore("menu.qs.sizes.none"));
        List<Component> out = new ArrayList<>();
        out.add(lore("menu.qs.sizes.title"));
        for (int i = 0; i < Math.min(LISTED_SYSTEMS, lines.size()); i++)
            out.add(Component.text(sizeText(lines.get(i)), lines.get(i).worst() < 1.0 ? NamedTextColor.GOLD
                    : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (lines.size() > LISTED_SYSTEMS) out.add(lore("menu.qs.sizes.more", arg("count", lines.size() - LISTED_SYSTEMS)));
        return out;
    }

    /** {@code Home1: 4,800 air · cool 1/2.6 · heat 2/2.6}, or that it isn't mapped yet. */
    public static String sizeText(com.github.blade.hvac.service.HvacController.SizeLine line) {
        if (line.cooling() == null) return Lang.text("menu.qs.sizes.unmapped", arg("name", line.name()));
        StringBuilder text = new StringBuilder(Lang.text(line.cooling().water() ? "menu.qs.sizes.water"
                : "menu.qs.sizes.air", arg("name", line.name()),
                arg("size", String.format(Locale.US, "%,d", line.cooling().size()))));
        if (!line.cooling().water() && line.cooling().openings() > 0)
            text.append(Lang.text("menu.qs.sizes.open", arg("openings", line.cooling().openings())));
        if (line.cooling().installed() > 0.0)
            text.append(Lang.text("menu.qs.sizes.cool", arg("ratio", line.cooling().ratio())));
        if (line.heating().installed() > 0.0)
            text.append(Lang.text("menu.qs.sizes.heat", arg("ratio", line.heating().ratio())));
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
        if (minutes < 1) return Lang.text("time.ago.now");
        if (minutes < 60) return Lang.text(minutes == 1 ? "time.ago.minute" : "time.ago.minutes", arg("n", minutes));
        long hours = minutes / 60;
        if (hours < 48) return Lang.text(hours == 1 ? "time.ago.hour" : "time.ago.hours", arg("n", hours));
        return Lang.text("time.ago.days", arg("n", hours / 24));
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
