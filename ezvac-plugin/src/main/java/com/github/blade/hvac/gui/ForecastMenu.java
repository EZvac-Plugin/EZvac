package com.github.blade.hvac.gui;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.WeatherStation;
import com.github.blade.hvac.service.AirText;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import com.github.blade.hvac.service.SeasonService;
import com.github.blade.hvac.service.StationText;
import com.github.blade.hvac.service.WeatherStationService;
import com.github.blade.hvac.service.WindText;
import com.github.blade.hvac.simulation.AirQualityIndex;
import com.github.blade.hvac.simulation.Forecast;
import com.github.blade.hvac.simulation.Seasons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static com.github.blade.hvac.gui.ControlMenus.click;
import static com.github.blade.hvac.gui.ControlMenus.gray;
import static com.github.blade.hvac.gui.ControlMenus.item;

/**
 * Weather stations' menus (BETA 1.0). /hvac forecast, or clicking a station's
 * screen, opens the forecast: up to five stations in range along the top,
 * nearest first, and the chosen one's seven days beneath. An admin clicking a
 * station gets its own menu instead: on and off, its record, and the forecast.
 */
public final class ForecastMenu implements Listener {
    /** The stations along the top, nearest first, and the seven days, today first, in the middle row. */
    static final int[] STATION_SLOTS = {2, 3, 4, 5, 6};
    static final int[] DAY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    static final int CLOSE = 26;
    /** The station menu: its switch, its record and the way to the forecast. */
    static final int SWITCH = 11, RECORD = 13, FORECAST = 15;
    public static final int MAX_STATIONS = STATION_SLOTS.length;
    private static final String ADMIN = "ezvac.admin";

    private enum Kind { FORECAST, STATION }

    private static final class Menu implements InventoryHolder {
        final Kind kind;
        final List<WeatherStation> stations;
        /** Where the player stood when it opened, for the distances. */
        final org.bukkit.Location from;
        WeatherStation selected;
        Inventory inventory;

        Menu(Kind kind, List<WeatherStation> stations, WeatherStation selected, org.bukkit.Location from) {
            this.kind = kind;
            this.stations = stations;
            this.selected = selected;
            this.from = from;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final WeatherStationService service;
    private final DisplayService displays;
    private final Predicate<ItemStack> isTool;

    public ForecastMenu(HvacPlugin plugin, HvacRegistry registry, WeatherStationService service,
                        DisplayService displays, Predicate<ItemStack> isTool) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.service = Objects.requireNonNull(service, "service");
        this.displays = Objects.requireNonNull(displays, "displays");
        this.isTool = Objects.requireNonNull(isTool, "isTool");
    }

    /**
     * Opens the forecast of the stations near a player, showing {@code chosen}
     * first when given (the one they clicked), else the nearest.
     */
    public void openForecast(Player player, WeatherStation chosen) {
        if (!WeatherStationService.switchedOn()) {
            player.sendMessage(Component.text("Weather stations are switched off on this server.", NamedTextColor.YELLOW));
            return;
        }
        List<WeatherStation> stations = new ArrayList<>(service.nearby(player.getLocation(), MAX_STATIONS));
        if (chosen != null && WeatherStationService.working(chosen) && chosen.area() != null && !stations.contains(chosen)) {
            if (stations.size() == MAX_STATIONS) stations.remove(MAX_STATIONS - 1);
            stations.add(0, chosen);
        }
        if (stations.isEmpty()) {
            player.sendMessage(Component.text("There's no weather station within " + service.settings().range()
                    + " blocks of you.", NamedTextColor.YELLOW));
            return;
        }
        Menu menu = new Menu(Kind.FORECAST, stations, chosen != null && stations.contains(chosen) ? chosen : stations.get(0),
                player.getLocation());
        menu.inventory = Bukkit.createInventory(menu, 27, Component.text("Forecast"));
        draw(menu);
        player.openInventory(menu.inventory);
    }

    /** An admin's menu for one station. */
    public void openStation(Player player, WeatherStation station) {
        Menu menu = new Menu(Kind.STATION, List.of(station), station, player.getLocation());
        menu.inventory = Bukkit.createInventory(menu, 27, Component.text("Weather station: " + station.label()));
        draw(menu);
        player.openInventory(menu.inventory);
    }

    private void draw(Menu menu) {
        Inventory inventory = menu.inventory;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        inventory.setItem(CLOSE, item(Material.BARRIER, Component.text("Close", NamedTextColor.RED), List.of()));
        if (menu.kind == Kind.STATION) drawStation(inventory, menu.selected);
        else drawForecast(inventory, menu);
    }

    private void drawForecast(Inventory inventory, Menu menu) {
        TemperatureUnit units = plugin.settings().units();
        for (int i = 0; i < menu.stations.size(); i++) {
            WeatherStation station = menu.stations.get(i);
            boolean selected = station.equals(menu.selected);
            List<Component> lore = new ArrayList<>();
            lore.add(gray(StationText.distance(Math.sqrt(WeatherStationService.distanceSquared(station, menu.from)))));
            lore.add(gray("Now " + StationText.temperature(service.nowF(station), units)));
            lore.add(selected ? Component.text("Showing its forecast", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                    : Component.text("Click to see its forecast", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            inventory.setItem(STATION_SLOTS[i], glint(item(Material.LIGHTNING_ROD,
                    Component.text(station.label(), selected ? NamedTextColor.AQUA : NamedTextColor.WHITE), lore), selected));
        }
        WeatherStation station = menu.selected;
        List<Forecast.Day> days = service.forecast(station);
        boolean dry = station.area() != null && station.area().rainShare() + station.area().snowShare() < 0.1;
        SeasonService seasons = plugin.seasons();
        AirQualitySettings air = plugin.climate().airQuality();
        for (Forecast.Day day : days) {
            if (day.ahead() >= DAY_SLOTS.length) break;
            boolean snow = service.snowDay(station, day);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(StationText.sky(day, snow), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lore.add(gray(StationText.highLow(day, units)));
            if (!dry) lore.add(gray((snow ? "Snow " : "Rain ") + StationText.percent(day.rainChance())
                    + " · Thunder " + StationText.percent(day.thunderChance())));
            if (day.wind() != null)
                lore.add(gray("Wind: " + WindText.describe(day.windMs(), day.wind().fromDegrees(),
                        plugin.climate().wind(), units)));
            // The day's air (BETA 1.0 Pilot 3), coloured by its band as AQI readings are.
            if (day.aqi() >= 0) {
                AirQualityIndex.Band band = AirQualityIndex.Band.of(day.aqi());
                lore.add(Component.text("AQI " + day.aqi() + " (" + band.displayName() + ")", AirText.color(band))
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (Double.isFinite(day.pollen()))
                lore.add(gray("Pollen: " + AirQualityIndex.PollenLevel.of(day.pollen(), air.pollenLevels()).displayName()));
            if (!day.note().isEmpty())
                lore.add(Component.text(day.note(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            String season = null;
            int dayOfSeason = 0;
            if (seasons != null && seasons.tracks(station.position().worldId())) {
                Seasons.Season in = seasons.seasonIn(station.position().worldId(), day.ahead());
                season = in == null ? null : in.displayName();
                dayOfSeason = seasons.dayOfSeasonIn(station.position().worldId(), day.ahead());
            }
            inventory.setItem(DAY_SLOTS[day.ahead()], item(icon(day.sky()), Component.text(
                    StationText.dayName(day.ahead(), season, dayOfSeason),
                    day.ahead() == 0 ? NamedTextColor.YELLOW : NamedTextColor.WHITE), lore));
        }
    }

    private void drawStation(Inventory inventory, WeatherStation station) {
        boolean on = station.enabled();
        inventory.setItem(SWITCH, item(on ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text(on ? "On" : "Off", on ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(gray(on ? "Forecasting for players in range" : "No forecast, no sparks"),
                        Component.text("Click to switch " + (on ? "off" : "on"), NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false))));
        List<Component> record = new ArrayList<>();
        record.add(gray(station.intact() ? "Its tower is whole" : "Its tower is damaged: rebuild it"));
        record.add(gray("Struck by lightning " + station.strikes() + (station.strikes() == 1 ? " time" : " times")));
        WeatherStation.Area area = station.area();
        record.add(gray(area == null ? "Hasn't measured its area yet"
                : "Measures " + service.settings().areaRadius() + " blocks around, " + service.settings().areaHeight()
                + " up"));
        if (!WeatherStationService.switchedOn()) record.add(gray("Weather stations are switched off server-wide"));
        inventory.setItem(RECORD, item(Material.LIGHTNING_ROD, Component.text(station.label(), NamedTextColor.AQUA), record));
        inventory.setItem(FORECAST, item(Material.CLOCK, Component.text("Forecast", NamedTextColor.YELLOW),
                List.of(gray("The next seven days"))));
    }

    static Material icon(Forecast.Sky sky) {
        return switch (sky) {
            case CLEAR -> Material.SUNFLOWER;
            case RAIN -> Material.WATER_BUCKET;
            case SNOW -> Material.SNOWBALL;
            case THUNDER -> Material.TRIDENT;
        };
    }

    private static ItemStack glint(ItemStack item, boolean on) {
        if (!on) return item;
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    // ---- clicks ----

    /** Clicking a station's screen: an admin gets its menu, anyone else its forecast. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStationClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != EquipmentSlot.HAND || isTool.test(event.getItem())) return;
        WeatherStation station = registry.weatherStationAt(BlockKey.of(event.getClickedBlock().getLocation()));
        if (station == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.hasPermission(ADMIN)) openStation(player, station);
        else if (!player.hasPermission("ezvac.use"))
            player.sendMessage(Component.text("You may not use this sign.", NamedTextColor.RED));
        else if (!WeatherStationService.working(station) || station.area() == null)
            player.sendMessage(Component.text(station.label() + " isn't forecasting right now.", NamedTextColor.YELLOW));
        else openForecast(player, station);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != menu.inventory) return;
        int slot = event.getRawSlot();
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }
        if (menu.kind == Kind.FORECAST) {
            for (int i = 0; i < menu.stations.size(); i++)
                if (STATION_SLOTS[i] == slot && !menu.stations.get(i).equals(menu.selected)) {
                    menu.selected = menu.stations.get(i);
                    click(player, true);
                    draw(menu);
                }
            return;
        }
        if (!player.hasPermission(ADMIN)) return;
        WeatherStation station = registry.weatherStationAt(menu.selected.position());
        if (station == null) {
            player.closeInventory();
            return;
        }
        if (slot == SWITCH) {
            station.setEnabled(!station.enabled());
            registry.markDirty();
            click(player, true);
            displays.refreshWeatherStation(station, true);
            plugin.getLogger().info(player.getName() + " switched weather station '" + station.label() + "' at "
                    + station.position() + (station.enabled() ? " on" : " off"));
            draw(menu);
        } else if (slot == FORECAST) {
            click(player, true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) openForecast(player, station);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
    }
}
