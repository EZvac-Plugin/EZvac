package com.github.blade.hvac.gui;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.ServerSwitches;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.DeviceKind;
import com.github.blade.hvac.model.EquipmentUnit;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import com.github.blade.hvac.service.RegistryBackups;
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
        MAIN("EZvac quick settings", 27), DEVICES("Quick settings › Devices", 36),
        CLIMATE("Quick settings › Climate systems", 36), BACKUPS("Quick settings › Backups", 36),
        STATISTICS("Quick settings › Statistics", 36), UPDATES("Quick settings › Updates", 36);

        final String title;
        final int size;

        Page(String title, int size) {
            this.title = title;
            this.size = size;
        }

        int back() { return size - 9; }
        int close() { return size - 1; }
    }

    /** The main page's sections, in the middle row. */
    static final int DEVICES = 11, CLIMATE = 12, BACKUPS = 13, STATISTICS = 14, UPDATES = 15;
    /** The device kinds in the second row: the five sources, a gap, the three room units; their switches beneath. */
    static final int[] DEVICE_SLOTS = {9, 10, 11, 12, 13, 15, 16, 17};
    /** Air quality, pollen, wind and seasons, with their switches beneath. */
    static final int AIR = 10, POLLEN = 12, WIND = 14, SEASONS = 16;
    static final int BACK_UP_NOW = 11, AUTOMATIC = 13, BACKUP_INFO = 15;
    static final int DEVICE_COUNTS = 10, SYSTEM_COUNTS = 12, AIRFLOW = 14, SAVED = 16, REFRESH = 22;
    static final int RUNNING = 10, CHECK_NOW = 12, BACKGROUND = 14, TELL_ADMINS = 16;
    private static final String ADMIN = "ezvac.admin";

    private static final class Menu implements InventoryHolder {
        final Page page;
        Inventory inventory;

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

    public QuickSettingsMenu(HvacPlugin plugin, HvacRegistry registry, AirflowService airflow, DisplayService displays,
                             ServerSwitches switches, RegistryBackups backups, UpdateNotifier updates) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.displays = Objects.requireNonNull(displays, "displays");
        this.switches = Objects.requireNonNull(switches, "switches");
        this.backups = Objects.requireNonNull(backups, "backups");
        this.updates = Objects.requireNonNull(updates, "updates");
    }

    public void open(Player player) { open(player, Page.MAIN); }

    private void open(Player player, Page page) {
        Menu menu = new Menu(page);
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
                    List.of(gray("To the quick settings"))));
        switch (menu.page) {
            case MAIN -> drawMain(inventory);
            case DEVICES -> drawDevices(inventory);
            case CLIMATE -> drawClimate(inventory);
            case BACKUPS -> drawBackups(inventory);
            case STATISTICS -> drawStatistics(inventory);
            case UPDATES -> drawUpdates(inventory);
        }
    }

    private void drawMain(Inventory inventory) {
        long off = java.util.Arrays.stream(DeviceKind.values()).filter(switches::isOff).count();
        inventory.setItem(DEVICES, section(Material.DISPENSER, "Devices",
                off == 0 ? "Every kind is on" : off + " kind(s) switched off", "Switch device kinds on or off"));
        int systemsOff = (switches.airQuality() ? 0 : 1) + (switches.pollen() ? 0 : 1) + (switches.wind() ? 0 : 1)
                + (switches.seasons() ? 0 : 1);
        inventory.setItem(CLIMATE, section(Material.SUNFLOWER, "Climate systems",
                systemsOff == 0 ? "All on" : systemsOff + " switched off", "Air quality, pollen, wind, seasons"));
        inventory.setItem(BACKUPS, section(Material.CHEST, "Backups",
                "Automatic: " + RegistryBackups.describe(backups.intervalHours()), "Back up the registry"));
        inventory.setItem(STATISTICS, section(Material.BOOK, "Statistics",
                registry.thermostats().size() + " thermostat(s), " + registry.equipment().size() + " unit(s)",
                "What /hvac stats shows"));
        inventory.setItem(UPDATES, section(Material.CLOCK, "Updates", "Running " + updates.runningName(),
                "Check GitHub for a newer EZvac"));
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
    }

    private void drawBackups(Inventory inventory) {
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
        AirflowService.Figures figures = airflow.figures();
        inventory.setItem(AIRFLOW, item(Material.COMPASS, Component.text("Airflow", NamedTextColor.AQUA), List.of(
                gray("Cells mapped: " + figures.cachedCells()),
                gray("Waiting to be mapped: " + figures.waiting() + (figures.rebuilding() ? ", one mapping now" : "")),
                gray("Maps made since start-up: " + figures.completed()),
                gray("Cells visited: " + figures.visited()),
                gray("Maps that hit the size cap: " + figures.capped()))));
        File saved = new File(plugin.getDataFolder(), "hvac.yml");
        inventory.setItem(SAVED, item(Material.FILLED_MAP, Component.text("Registry", NamedTextColor.AQUA), List.of(
                gray(saved.isFile() ? "hvac.yml saved " + ago(saved.lastModified()) : "Not saved yet"),
                gray(registry.isWritable() ? "Saving normally" : "Not saving: hvac.yml could not be read"))));
        inventory.setItem(REFRESH, item(Material.SPYGLASS, Component.text("Refresh", NamedTextColor.YELLOW),
                List.of(gray("Read the figures again"))));
    }

    private void drawUpdates(Inventory inventory) {
        inventory.setItem(RUNNING, item(Material.NAME_TAG, Component.text("EZvac " + updates.runningName(),
                NamedTextColor.AQUA), List.of(gray("On Minecraft " + updates.minecraft()))));
        inventory.setItem(CHECK_NOW, item(Material.SPYGLASS,
                Component.text("Check now", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                List.of(gray("Asks GitHub for a newer EZvac,"), gray("and whether it runs here."),
                        gray("The answer comes in chat."))));
        placeSwitch(inventory, BACKGROUND, Material.REPEATER, "Background checks", updates.checking(), List.of(
                gray("At start-up and every 6 hours"), clickTo(updates.checking())));
        placeSwitch(inventory, TELL_ADMINS, Material.BELL, "Tell admins on join", updates.notifyAdmins(), List.of(
                gray("When a background check finds one"), clickTo(updates.notifyAdmins())));
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
            reopen(player, Page.MAIN);
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
            case BACKUPS -> clickBackups(player, slot);
            case STATISTICS -> {
                if (slot == REFRESH) click(player, true);
            }
            case UPDATES -> {
                if (clickUpdates(player, slot)) return;
            }
        }
        draw(menu);
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
        logChange(player, "switched " + kind.plural().toLowerCase(java.util.Locale.ROOT)
                + (switchOff ? " off" : " on") + " server-wide");
        plugin.requestImmediateSync();
        for (ClimateUnit unit : registry.climateUnits())
            if (DeviceKind.of(unit.kind()) == kind) displays.refreshClimateUnit(unit, true);
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
            default -> {
                return;
            }
        }
        click(player, true);
        plugin.requestImmediateSync();
        displays.refreshAll(true);
    }

    private void clickBackups(Player player, int slot) {
        if (slot == BACK_UP_NOW) {
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
            logChange(player, "set automatic backups to " + RegistryBackups.describe(next).toLowerCase(java.util.Locale.ROOT));
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

    private void logChange(Player player, String what) {
        plugin.getLogger().info(player.getName() + " " + what + " (/hvac quicksettings).");
    }

    private long count(DeviceKind kind) {
        return registry.equipment().stream().filter(unit -> DeviceKind.of(unit.type()) == kind).count()
                + registry.climateUnits().stream().filter(unit -> DeviceKind.of(unit.kind()) == kind).count();
    }

    private long running(DeviceKind kind) {
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
