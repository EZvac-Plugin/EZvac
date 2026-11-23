package com.github.blade.hvac.command;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import com.github.blade.hvac.service.SystemLinks;
import com.github.blade.hvac.service.SystemNames;
import com.github.blade.hvac.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.github.blade.hvac.lang.Lang.arg;

/**
 * The link tool (3.0): one stick for all linking. Right-click a thermostat
 * to select its system, then equipment, vent blocks, a settings panel and
 * RPM monitors to link them; or select a sensor hub, then sensors.
 * Sneak-clicking unlinks. It replaced the duct tool and the sensor tool,
 * and still answers to both, so tools already in inventories keep working.
 *
 * <p>A device always belongs to some system: a unit created on its own, or
 * one left behind when its thermostat broke, sits in a headless system.
 * Linking any part of a headless system brings the whole of it along; a
 * part of a system that has its own thermostat is never taken.
 */
public final class LinkTool {
    static final String ADMIN_PERMISSION = "ezvac.admin";

    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final AirflowService airflow;
    private final DisplayService displays;
    private final HvacSettings settings;
    private final NamespacedKey toolKey;
    /** The 2.x duct tool; its group selection is no longer used. */
    private final NamespacedKey ductToolKey;
    /** The 2.x sensor tool. */
    private final NamespacedKey sensorToolKey;
    /** The selected thermostat: world;x;y;z. */
    private final NamespacedKey thermostatKey;
    /** The selected sensor hub, as the sensor tool stored it: world;x;y;z. */
    private final NamespacedKey hubKey;

    /** What the tool has selected: a thermostat, or a sensor hub. */
    public record Selection(BlockKey thermostat, BlockKey hub) {}

    public LinkTool(HvacPlugin plugin, HvacRegistry registry, AirflowService airflow,
                    DisplayService displays, HvacSettings settings) {
        this.plugin = plugin;
        this.registry = registry;
        this.airflow = airflow;
        this.displays = displays;
        this.settings = settings;
        toolKey = new NamespacedKey(plugin, "link_tool");
        ductToolKey = new NamespacedKey(plugin, "duct_link_tool");
        sensorToolKey = new NamespacedKey(plugin, "sensor_link_tool");
        thermostatKey = new NamespacedKey(plugin, "link_thermostat");
        hubKey = new NamespacedKey(plugin, "sensor_hub");
    }

    public void give(Player player) {
        ItemStack tool = new ItemStack(Material.STICK);
        ItemMeta meta = tool.getItemMeta();
        meta.displayName(Lang.message("link.tool.name"));
        meta.lore(instructions());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        tool.setItemMeta(meta);
        if (player.getInventory().addItem(tool).isEmpty()) {
            player.sendMessage(Lang.message("link.tool.given"));
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), tool);
            player.sendMessage(Lang.message("link.tool.dropped"));
        }
    }

    private static List<Component> instructions() {
        return List.of(
                Lang.message("link.tool.lore.1"), Lang.message("link.tool.lore.2"),
                Lang.message("link.tool.lore.3"), Lang.message("link.tool.lore.unlink"));
    }

    /** The link tool, or a duct or sensor tool from before 3.0. */
    public boolean isTool(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) return false;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return data.has(toolKey, PersistentDataType.BYTE) || data.has(ductToolKey, PersistentDataType.BYTE)
                || data.has(sensorToolKey, PersistentDataType.BYTE);
    }

    /** What the tool in hand has selected, or null. */
    public Selection selection(ItemStack item) {
        if (!isTool(item)) return null;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        BlockKey thermostat = parse(data.get(thermostatKey, PersistentDataType.STRING));
        BlockKey hub = parse(data.get(hubKey, PersistentDataType.STRING));
        return thermostat == null && hub == null ? null : new Selection(thermostat, hub);
    }

    /** The thermostat the tool has selected, if it is still there. */
    public Thermostat selectedThermostat(ItemStack item) {
        Selection selection = selection(item);
        return selection == null || selection.thermostat() == null ? null
                : registry.thermostatAt(selection.thermostat());
    }

    public void handleClick(Player player, Block block, ItemStack item, boolean sneaking) {
        if (!isTool(item) || !player.hasPermission(ADMIN_PERMISSION)) return;
        try {
            click(player, block, item, sneaking);
        } catch (IllegalArgumentException refused) {
            // The registry refuses what it can't do safely, such as moving a unit whose output waits for cleanup.
            player.sendMessage(Lang.message("link.refused", arg("reason", String.valueOf(refused.getMessage()))));
        }
    }

    private void click(Player player, Block block, ItemStack item, boolean sneaking) {
        BlockKey position = BlockKey.of(block.getLocation());
        Thermostat thermostat = registry.thermostatAt(position);
        if (thermostat != null) {
            if (sneaking) detachThermostat(player, thermostat);
            else selectThermostat(player, item, thermostat);
            return;
        }
        SensorHub hub = registry.sensorHubAt(position);
        if (hub != null) {
            selectHub(player, item, hub, position);
            return;
        }
        Sensor sensor = registry.sensorAt(position);
        if (sensor != null) {
            linkSensor(player, item, sensor, position, sneaking);
            return;
        }
        if (sneaking) {
            unlink(player, block, position);
            return;
        }
        Thermostat head = selectedThermostat(item);
        if (head == null) {
            Selection selection = selection(item);
            player.sendMessage(Lang.message(selection != null && selection.thermostat() != null
                    ? "link.thermostat-gone" : "link.select-thermostat-first"));
            return;
        }
        if (!head.position().worldId().equals(position.worldId())) {
            player.sendMessage(Lang.message("link.other-world"));
            return;
        }
        link(player, head, block, position);
    }

    /**
     * Links a device just created where the player looks, when they hold the
     * tool with a thermostat selected; it is linked exactly as a click would.
     *
     * @return false when there was no thermostat to link it to
     */
    public boolean linkCreated(Player player, Block block) {
        ItemStack item = player.getInventory().getItemInMainHand();
        Thermostat head = selectedThermostat(item);
        if (head == null || !head.position().worldId().equals(block.getWorld().getUID())) return false;
        link(player, head, block, BlockKey.of(block.getLocation()));
        return true;
    }

    private void selectThermostat(Player player, ItemStack item, Thermostat thermostat) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(thermostatKey, PersistentDataType.STRING, write(thermostat.position()));
        meta.getPersistentDataContainer().remove(hubKey);
        meta.lore(List.of(Lang.message("link.tool.lore.selected", arg("selected", SystemNames.of(thermostat))),
                Lang.message("link.tool.lore.thermostat-1"), Lang.message("link.tool.lore.thermostat-2"),
                Lang.message("link.tool.lore.unlink")));
        item.setItemMeta(meta);
        player.sendMessage(Lang.message("link.selected", arg("thermostat", SystemNames.of(thermostat)),
                arg("linked", SystemNames.contents(registry.members(thermostat.system())))));
        showLinked(player, thermostat);
    }

    /** The action bar summary after selecting or linking. */
    private void showLinked(Player player, Thermostat head) {
        player.sendActionBar(Lang.message("link.action-bar", arg("thermostat", SystemNames.of(head)),
                arg("linked", SystemNames.contents(registry.members(head.system())))));
    }

    private void link(Player player, Thermostat head, Block block, BlockKey position) {
        SystemId system = head.system();
        SystemId current = registry.systemAt(position);
        if (current == null) {
            linkLoose(player, head, block, position);
            return;
        }
        String what = deviceName(position);
        if (current.equals(system)) {
            player.sendMessage(Lang.message("link.already", arg("device", what), arg("thermostat", head.id())));
            return;
        }
        Thermostat owner = registry.thermostatFor(current);
        if (owner != null) {
            player.sendMessage(Lang.message("link.belongs", arg("device", what), arg("owner", SystemNames.of(owner))));
            return;
        }
        // Part of a headless system: the whole system comes along.
        String conflict = SystemLinks.conflict(registry.shapeOf(system), registry.shapeOf(current));
        if (conflict != null) {
            player.sendMessage(Lang.message("link.conflict", arg("thermostat", head.id()), arg("conflict", conflict)));
            return;
        }
        HvacRegistry.Members joining = registry.members(current);
        HvacRegistry.MergeResult merged = registry.merge(current, system);
        airflow.invalidate(current);
        airflow.invalidate(system);
        boolean single = joining.units() + joining.vents() + joining.panels() + joining.monitors() == 1;
        player.sendMessage(single ? Lang.message("link.linked", arg("device", capitalized(what)), arg("thermostat", head.id()))
                : Lang.message("link.linked-headless", arg("thermostat", head.id()),
                arg("linked", SystemNames.contents(joining))));
        if (merged.unlinkedPanel() != null)
            player.sendMessage(Lang.message("link.panel-replaced", arg("thermostat", head.id()),
                    arg("at", SystemNames.at(merged.unlinkedPanel().position()))));
        changed(player, head);
    }

    /** A block in no system: a vent block, or a panel or monitor not linked yet. */
    private void linkLoose(Player player, Thermostat head, Block block, BlockKey position) {
        SystemId system = head.system();
        SettingsPanel panel = registry.settingsPanelAt(position);
        RpmMonitor monitor = registry.rpmMonitorAt(position);
        if (panel != null) {
            SettingsPanel existing = registry.settingsPanelFor(system);
            if (existing != null) {
                player.sendMessage(Lang.message("link.has-panel", arg("thermostat", head.id()),
                        arg("at", SystemNames.at(existing.position()))));
                return;
            }
            registry.linkSettingsPanel(panel, system);
            player.sendMessage(Lang.message("link.linked", arg("device", Lang.text("link.device.settings-panel-capital")), arg("thermostat", head.id())));
        } else if (monitor != null) {
            registry.linkRpmMonitor(monitor, system);
            player.sendMessage(Lang.message("link.linked", arg("device", Lang.text("link.device.rpm-monitor")), arg("thermostat", head.id())));
        } else if (registry.climateUnitAt(position) != null) {
            player.sendMessage(Lang.message("link.climate-unit"));
            return;
        } else if (ClimateVent.isVentBlock(block.getType()) && registry.deviceKind(position) == HvacRegistry.DeviceKind.NONE) {
            boolean water = ClimateVent.isWaterVent(block.getType());
            String conflict = SystemLinks.conflict(registry.shapeOf(system), SystemLinks.Shape.vent(water));
            if (conflict != null) {
                player.sendMessage(Lang.message("link.conflict", arg("thermostat", head.id()), arg("conflict", conflict)));
                return;
            }
            registry.addVent(new ClimateVent(position, system));
            airflow.invalidate(system);
            player.sendMessage(Lang.message("link.linked", arg("device", Lang.text(water ? "link.device.water-vent-capital"
                    : "link.device.vent-capital")), arg("thermostat", head.id())));
        } else {
            player.sendMessage(Lang.message("link.what-to-click"));
            return;
        }
        changed(player, head);
    }

    /** After a link: the thermostat follows what it now holds, and the signs show it. */
    private void changed(Player player, Thermostat head) {
        Thermostat switched = registry.settleHead(head.system());
        if (switched != null)
            player.sendMessage(Lang.message(switched.pool() ? "link.now-pool-set" : "link.now-room-set",
                    arg("thermostat", head.id()), arg("range", range(switched.pool())),
                    arg("target", settings.units().format(switched.targetF()))));
        registry.markDirty();
        displays.refreshAll(true);
        plugin.requestImmediateSync();
        showLinked(player, head);
    }

    private void unlink(Player player, Block block, BlockKey position) {
        EquipmentUnit unit = registry.equipmentAt(position);
        SystemId previous = registry.systemAt(position);
        String from = previous == null ? "" : Lang.text("link.from", arg("system", SystemNames.of(registry, previous)));
        if (unit != null) {
            HvacRegistry.Members members = registry.members(unit.system());
            if (members.thermostat() == null && members.units() == 1 && members.vents() + members.panels()
                    + members.monitors() == 0) {
                player.sendMessage(Lang.message("link.not-linked", arg("device", deviceName(position))));
                return;
            }
            registry.linkEquipment(unit, registry.newSystem(position.worldId()));
            player.sendMessage(Lang.message("link.unlinked", arg("device", capitalized(deviceName(position))), arg("from", from)));
        } else if (registry.ventAt(position) != null) {
            ClimateVent[] removed = new ClimateVent[1];
            registry.removingByCommand(() -> removed[0] = registry.removeVent(position));
            player.sendMessage(Lang.message("link.unlinked", arg("device", Lang.text(ClimateVent.isWaterVent(block.getType())
                    ? "link.device.water-vent-capital" : "link.device.vent-capital")), arg("from", from)));
        } else if (registry.settingsPanelAt(position) != null) {
            SettingsPanel panel = registry.settingsPanelAt(position);
            if (panel.system() == null) { player.sendMessage(Lang.message("link.panel-not-linked")); return; }
            registry.linkSettingsPanel(panel, null);
            player.sendMessage(Lang.message("link.panel-unlinked", arg("from", from)));
        } else if (registry.rpmMonitorAt(position) != null) {
            RpmMonitor monitor = registry.rpmMonitorAt(position);
            if (monitor.system() == null) { player.sendMessage(Lang.message("link.monitor-not-linked")); return; }
            registry.linkRpmMonitor(monitor, null);
            player.sendMessage(Lang.message("link.unlinked", arg("device", Lang.text("link.device.rpm-monitor")), arg("from", from)));
        } else {
            player.sendMessage(Lang.message("link.nothing-there"));
            return;
        }
        if (previous != null) {
            airflow.invalidate(previous);
            Thermostat head = registry.thermostatFor(previous);
            if (head != null) {
                Thermostat switched = registry.settleHead(previous);
                if (switched != null)
                    player.sendMessage(Lang.message(switched.pool() ? "link.now-pool" : "link.now-room",
                            arg("thermostat", head.id()), arg("range", range(switched.pool()))));
            }
        }
        registry.markDirty();
        displays.refreshAll(true);
        plugin.requestImmediateSync();
    }

    /** The thermostat leaves its system, which carries on headless. */
    private void detachThermostat(Player player, Thermostat thermostat) {
        SystemId previous = thermostat.system();
        HvacRegistry.Members members = registry.members(previous);
        if (members.units() + members.vents() + members.panels() + members.monitors() == 0) {
            player.sendMessage(Lang.message("link.nothing-linked", arg("thermostat", thermostat.id())));
            return;
        }
        registry.linkThermostat(thermostat, registry.newSystem(previous.worldId()));
        airflow.invalidate(previous);
        player.sendMessage(Lang.message("link.thermostat-unlinked", arg("thermostat", thermostat.id()),
                arg("linked", SystemNames.contents(members))));
        registry.markDirty();
        displays.refreshAll(true);
        plugin.requestImmediateSync();
    }

    private void selectHub(Player player, ItemStack item, SensorHub hub, BlockKey position) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(hubKey, PersistentDataType.STRING, write(position));
        meta.getPersistentDataContainer().remove(thermostatKey);
        meta.lore(List.of(Lang.message("link.tool.lore.selected-hub", arg("hub", hub.label())),
                Lang.message("link.tool.lore.hub"), Lang.message("link.tool.lore.unlink")));
        item.setItemMeta(meta);
        player.sendMessage(Lang.message("link.hub.selected", arg("hub", hub.label()), arg("count", hub.sensors().size()),
                arg("max", SensorHub.MAX_SENSORS)));
    }

    private void linkSensor(Player player, ItemStack item, Sensor sensor, BlockKey position, boolean unlink) {
        Selection selection = selection(item);
        SensorHub hub = selection == null || selection.hub() == null ? null : registry.sensorHubAt(selection.hub());
        if (hub == null) {
            player.sendMessage(Lang.message(selection == null || selection.hub() == null
                    ? "link.hub.select-first" : "link.hub.gone"));
            return;
        }
        if (unlink) {
            player.sendMessage(Lang.message(hub.unlink(position) ? "link.hub.unlinked" : "link.hub.not-linked",
                    arg("sensor", sensor.label()), arg("hub", hub.label())));
        } else {
            player.sendMessage(switch (hub.link(position)) {
                case LINKED -> Lang.message("link.hub.linked", arg("sensor", sensor.label()), arg("hub", hub.label()),
                        arg("count", hub.sensors().size()), arg("max", SensorHub.MAX_SENSORS));
                case ALREADY_LINKED -> Lang.message("link.hub.already", arg("hub", hub.label()));
                case FULL -> Lang.message("link.hub.full", arg("hub", hub.label()), arg("max", SensorHub.MAX_SENSORS));
                case OTHER_WORLD -> Lang.message("link.hub.other-world");
            });
        }
        registry.markDirty();
        displays.refreshSensorHub(hub, true);
    }

    /** "heat pump", "water vent", "settings panel": what is at a block, for messages. */
    private String deviceName(BlockKey position) {
        EquipmentUnit unit = registry.equipmentAt(position);
        if (unit != null) return unit.type().displayName().toLowerCase(Locale.ROOT);
        if (registry.ventAt(position) != null) return Lang.text("link.device.vent");
        if (registry.settingsPanelAt(position) != null) return Lang.text("link.device.settings-panel");
        if (registry.rpmMonitorAt(position) != null) return Lang.text("link.device.rpm-monitor");
        if (registry.thermostatAt(position) != null) return Lang.text("link.device.thermostat");
        return Lang.text("link.device.device");
    }

    private String range(boolean pool) {
        double low = pool ? settings.poolMinimumTargetF() : settings.minimumTargetF();
        double high = pool ? settings.poolMaximumTargetF() : settings.maximumTargetF();
        return Lang.text("link.range", arg("low", settings.units().format(low, 0)), arg("high", settings.units().format(high, 0)));
    }

    private static String capitalized(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String write(BlockKey position) {
        return position.worldId() + ";" + position.x() + ";" + position.y() + ";" + position.z();
    }

    private static BlockKey parse(String stored) {
        if (stored == null) return null;
        try {
            String[] parts = stored.split(";");
            return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

}
