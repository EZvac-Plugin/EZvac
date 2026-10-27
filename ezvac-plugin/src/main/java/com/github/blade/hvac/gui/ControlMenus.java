package com.github.blade.hvac.gui;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.api.event.ThermostatTargetChangeEvent;
import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.integration.api.ApiEvents;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.AirPurifier;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.AirText;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import com.github.blade.hvac.simulation.Psychrometrics;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The control menu a thermostat's or a dehumidifier's/humidifier's sign opens
 * (2.1), in place of editing the sign: coloured panes raise and lower
 * the setpoint, a lever switches it on or off. Raising is on the left, lowering
 * on the right, the biggest steps on top. Main thread only.
 */
public final class ControlMenus implements Listener {
    /** Rows of 9: 0-8 on top, 9-17 in the middle, 18-26 at the bottom. */
    static final int RAISE_5 = 2, RAISE_1 = 11, RAISE_TENTH = 20;
    static final int LOWER_5 = 6, LOWER_1 = 15, LOWER_TENTH = 24;
    static final int INFO = 13, POWER = 22;
    /** Humidity only moves in fives, on the middle row. */
    static final int HUMIDITY_RAISE = 11, HUMIDITY_LOWER = 15;

    private enum Device { THERMOSTAT, CLIMATE_UNIT }

    /** Which device an open menu belongs to; looked up again on every click, so a removed device just closes it. */
    private static final class Menu implements InventoryHolder {
        final Device device;
        final BlockKey position;
        Inventory inventory;

        Menu(Device device, BlockKey position) {
            this.device = device;
            this.position = position;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final DisplayService displays;
    private final HvacSettings settings;
    private final ClimateSettings climate;

    public ControlMenus(HvacPlugin plugin, HvacRegistry registry, DisplayService displays, HvacSettings settings,
                        ClimateSettings climate) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.displays = Objects.requireNonNull(displays, "displays");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.climate = Objects.requireNonNull(climate, "climate");
    }

    public void open(Player player, Thermostat thermostat) {
        Menu menu = new Menu(Device.THERMOSTAT, thermostat.position());
        menu.inventory = Bukkit.createInventory(menu, 27, Component.text(thermostat.id() + " · "
                + (thermostat.pool() ? "pool" : "room") + " thermostat"));
        draw(menu);
        player.openInventory(menu.inventory);
    }

    public void open(Player player, ClimateUnit unit) {
        Menu menu = new Menu(Device.CLIMATE_UNIT, unit.position());
        menu.inventory = Bukkit.createInventory(menu, 27,
                Component.text(unit.label() + " · " + unit.kind().displayName().toLowerCase(Locale.ROOT)));
        draw(menu);
        player.openInventory(menu.inventory);
    }

    private void draw(Menu menu) {
        Inventory inventory = menu.inventory;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        if (menu.device == Device.THERMOSTAT) {
            Thermostat thermostat = registry.thermostatAt(menu.position);
            if (thermostat != null) drawThermostat(inventory, thermostat);
        } else {
            ClimateUnit unit = registry.climateUnitAt(menu.position);
            if (unit != null) drawUnit(inventory, unit);
        }
    }

    private void drawThermostat(Inventory inventory, Thermostat thermostat) {
        TemperatureUnit units = settings.units();
        String unit = units.name();
        inventory.setItem(RAISE_5, step(Material.BLACK_STAINED_GLASS_PANE, "+5 " + unit, NamedTextColor.WHITE));
        inventory.setItem(RAISE_1, step(Material.GREEN_STAINED_GLASS_PANE, "+1 " + unit, NamedTextColor.DARK_GREEN));
        inventory.setItem(RAISE_TENTH, step(Material.LIME_STAINED_GLASS_PANE, "+0.1 " + unit, NamedTextColor.GREEN));
        inventory.setItem(LOWER_5, step(Material.WHITE_STAINED_GLASS_PANE, "-5 " + unit, NamedTextColor.WHITE));
        inventory.setItem(LOWER_1, step(Material.RED_STAINED_GLASS_PANE, "-1 " + unit, NamedTextColor.RED));
        inventory.setItem(LOWER_TENTH, step(Material.ORANGE_STAINED_GLASS_PANE, "-0.1 " + unit, NamedTextColor.GOLD));
        double minimum = thermostat.pool() ? settings.poolMinimumTargetF() : settings.minimumTargetF();
        double maximum = thermostat.pool() ? settings.poolMaximumTargetF() : settings.maximumTargetF();
        List<Component> lore = new ArrayList<>();
        lore.add(gray("Now " + units.degrees(thermostat.roomF())
                + (thermostat.hasRoomDewPoint() ? " · " + Math.round(Psychrometrics.relativeHumidity(
                thermostat.roomF(), thermostat.roomDewPointF())) + "% RH" : "")));
        lore.add(gray("Range " + units.degrees(minimum) + " to " + units.degrees(maximum)));
        inventory.setItem(INFO, item(Material.DAYLIGHT_DETECTOR,
                Component.text("Set " + units.degrees(thermostat.targetF()), NamedTextColor.AQUA), lore));
        inventory.setItem(POWER, power(thermostat.enabled(), "System"));
    }

    private void drawUnit(Inventory inventory, ClimateUnit unit) {
        if (unit instanceof AirPurifier purifier) {
            drawPurifier(inventory, purifier);
            return;
        }
        inventory.setItem(HUMIDITY_RAISE, step(Material.BLACK_STAINED_GLASS_PANE, "+5%", NamedTextColor.WHITE));
        inventory.setItem(HUMIDITY_LOWER, step(Material.WHITE_STAINED_GLASS_PANE, "-5%", NamedTextColor.WHITE));
        int[] range = range(unit);
        double humidity = unit.lastHumidity();
        inventory.setItem(INFO, item(Material.DAYLIGHT_DETECTOR,
                Component.text("Set " + unit.setpoint() + "%", NamedTextColor.AQUA),
                List.of(gray("Now " + (Double.isFinite(humidity) ? Math.round(humidity) + "%" : "--%")),
                        gray("Range " + range[0] + "% to " + range[1] + "%"))));
        inventory.setItem(POWER, power(unit.powered(), unit.kind().displayName()));
    }

    /**
     * An air purifier has fan speeds instead of a setpoint (3.2): Low, Medium
     * and High, and Auto, which picks one from the air. The one it is on glints.
     */
    private void drawPurifier(Inventory inventory, AirPurifier purifier) {
        for (int i = 0; i < PURIFIER_MODES.length; i++) {
            AirPurifier.Mode mode = PURIFIER_MODES[i];
            boolean on = purifier.mode() == mode;
            ItemStack button = item(PURIFIER_PANES[i], Component.text((on ? "● " : "") + mode.displayName(),
                    on ? NamedTextColor.GREEN : NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                    List.of(gray(switch (mode) {
                        case LOW -> "Quiet, gentle cleaning";
                        case MEDIUM -> "Steady cleaning";
                        case HIGH -> "Full power";
                        case AUTO -> "Speeds up when the air gets worse";
                    })));
            if (on) button.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            inventory.setItem(PURIFIER_SLOTS[i], button);
        }
        List<Component> lore = new ArrayList<>();
        lore.add(gray("Now " + AirText.aqi(purifier.lastAir())));
        lore.add(gray("Running " + purifier.modeText()));
        lore.add(gray("Filter " + Math.round(purifier.filter() * 100) + "%"));
        inventory.setItem(INFO, item(Material.TUBE_CORAL_BLOCK,
                Component.text(purifier.mode().displayName(), NamedTextColor.AQUA), lore));
        inventory.setItem(POWER, power(purifier.powered(), purifier.kind().displayName()));
    }

    /** An air purifier's modes, along the top row. */
    private static final AirPurifier.Mode[] PURIFIER_MODES = AirPurifier.Mode.values();
    static final int[] PURIFIER_SLOTS = {1, 3, 5, 7};
    private static final Material[] PURIFIER_PANES = {Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE};

    /** The setpoints a unit can be given: from its lowest preset to its highest. */
    private int[] range(ClimateUnit unit) {
        return switch (unit.kind()) {
            case DEHUMIDIFIER -> new int[] {climate.dehumidifier().minimumSetpoint(),
                    climate.dehumidifier().maximumSetpoint()};
            case HUMIDIFIER -> new int[] {climate.humidifier().minimumSetpoint(),
                    climate.humidifier().maximumSetpoint()};
            case AIR_PURIFIER -> new int[] {0, 0};    // it has modes, not a setpoint
        };
    }

    /** How far from its device a menu still works, in blocks: a little past reach. */
    static final double REACH_BLOCKS = 8.0;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        // Nothing moves in or out of a control menu, whichever inventory was clicked.
        event.setCancelled(true);
        if (event.getClickedInventory() != menu.inventory || !(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("ezvac.use") || !withinReach(player, menu.position)) {
            // Opened from the sign, it only works there, and only while the player may use it.
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        boolean changed = menu.device == Device.THERMOSTAT ? clickThermostat(player, menu, event.getSlot())
                : clickUnit(player, menu, event.getSlot());
        if (changed) {
            registry.markDirty();
            plugin.requestImmediateSync();
        }
        draw(menu);
    }

    private static boolean withinReach(Player player, BlockKey position) {
        var location = position.location();
        return location != null && location.getWorld().equals(player.getWorld())
                && location.add(0.5, 0.5, 0.5).distanceSquared(player.getLocation()) <= REACH_BLOCKS * REACH_BLOCKS;
    }

    /**
     * Closes every open control menu, when EZvac stops: with its listener
     * gone, the items in a menu left open could be taken out.
     */
    public void closeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    private boolean clickThermostat(Player player, Menu menu, int slot) {
        Thermostat thermostat = registry.thermostatAt(menu.position);
        if (thermostat == null) {
            player.closeInventory();
            return false;
        }
        if (slot == POWER) {
            thermostat.setEnabled(!thermostat.enabled());
            click(player, true);
            displays.refreshThermostat(thermostat, true);
            return true;
        }
        double step = switch (slot) {
            case RAISE_5 -> 5.0;
            case RAISE_1 -> 1.0;
            case RAISE_TENTH -> 0.1;
            case LOWER_5 -> -5.0;
            case LOWER_1 -> -1.0;
            case LOWER_TENTH -> -0.1;
            default -> 0.0;
        };
        if (step == 0.0) return false;
        double minimum = thermostat.pool() ? settings.poolMinimumTargetF() : settings.minimumTargetF();
        double maximum = thermostat.pool() ? settings.poolMaximumTargetF() : settings.maximumTargetF();
        double target = Setpoints.stepTarget(thermostat.targetF(), step, settings.units(), minimum, maximum);
        boolean moved = Math.abs(target - thermostat.targetF()) > 1e-9;
        click(player, moved);
        if (!moved) return false;
        double previous = thermostat.targetF();
        thermostat.setTargetF(target, settings);
        ApiEvents.targetChanged(thermostat, previous, ThermostatTargetChangeEvent.Cause.MENU, plugin.controller());
        displays.refreshThermostat(thermostat, true);
        return true;
    }

    private boolean clickUnit(Player player, Menu menu, int slot) {
        ClimateUnit unit = registry.climateUnitAt(menu.position);
        if (unit == null) {
            player.closeInventory();
            return false;
        }
        int modeSlot = java.util.Arrays.stream(PURIFIER_SLOTS).boxed().toList().indexOf(slot);
        if (slot == POWER) unit.setPowered(!unit.powered());
        else if (unit instanceof AirPurifier purifier) {
            if (modeSlot < 0) return false;
            if (purifier.mode() == PURIFIER_MODES[modeSlot]) {
                click(player, false);
                return false;
            }
            purifier.setMode(PURIFIER_MODES[modeSlot]);
        } else if (slot == HUMIDITY_RAISE || slot == HUMIDITY_LOWER) {
            int[] range = range(unit);
            int setpoint = Setpoints.stepHumidity(unit.setpoint(), slot == HUMIDITY_RAISE ? 5 : -5,
                    range[0], range[1]);
            if (setpoint == unit.setpoint()) {
                click(player, false);
                return false;
            }
            unit.setSetpoint(setpoint);
        } else return false;
        click(player, true);
        displays.refreshClimateUnit(unit, true);
        return true;
    }

    /** A button click, or a dull note when it couldn't go any further. */
    private static void click(Player player, boolean worked) {
        player.playSound(player, worked ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f,
                worked ? 1.2f : 0.7f);
    }

    private static ItemStack step(Material pane, String label, TextColor color) {
        return item(pane, Component.text(label, color).decorate(TextDecoration.BOLD), List.of());
    }

    private static ItemStack power(boolean on, String what) {
        return item(Material.LEVER, Component.text(what + (on ? " on" : " off"),
                        on ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(gray(on ? "Click to switch it off" : "Click to switch it on")));
    }

    private static Component gray(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
