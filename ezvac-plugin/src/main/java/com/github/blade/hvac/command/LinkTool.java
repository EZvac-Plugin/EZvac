package com.github.blade.hvac.command;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import com.github.blade.hvac.service.SystemLinks;
import com.github.blade.hvac.service.SystemNames;
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
        meta.displayName(Component.text("EZvac Link Tool", NamedTextColor.AQUA));
        meta.lore(instructions());
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        tool.setItemMeta(meta);
        player.getInventory().addItem(tool);
        player.sendMessage(green("Link tool added to your inventory. Right-click a thermostat to start."));
    }

    private static List<Component> instructions() {
        return List.of(
                Component.text("Click a thermostat, then equipment, vents,", NamedTextColor.GRAY),
                Component.text("a settings panel or RPM monitors.", NamedTextColor.GRAY),
                Component.text("Or click a sensor hub, then sensors.", NamedTextColor.GRAY),
                Component.text("Sneak-click to unlink.", NamedTextColor.GRAY));
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
            player.sendMessage(red(selection != null && selection.thermostat() != null
                    ? "The thermostat you selected is gone. Right-click another thermostat with this tool."
                    : "Right-click a thermostat with this tool first, then the devices to link to it."));
            return;
        }
        if (!head.position().worldId().equals(position.worldId())) {
            player.sendMessage(red("Devices can only be linked to a thermostat in their own world."));
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
        meta.lore(List.of(Component.text("Selected: " + SystemNames.of(thermostat), NamedTextColor.GRAY),
                Component.text("Click equipment, vents, a settings panel", NamedTextColor.GRAY),
                Component.text("or RPM monitors to link them.", NamedTextColor.GRAY),
                Component.text("Sneak-click to unlink.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        player.sendMessage(green("Selected " + SystemNames.of(thermostat) + ". Linked: "
                + SystemNames.contents(registry.members(thermostat.system())) + "."));
        showLinked(player, thermostat);
    }

    /** The action bar summary after selecting or linking. */
    private void showLinked(Player player, Thermostat head) {
        player.sendActionBar(Component.text("Linking to " + SystemNames.of(head) + " · "
                + SystemNames.contents(registry.members(head.system())), NamedTextColor.AQUA));
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
            player.sendMessage(yellow("That " + what + " is already linked to " + head.id() + "."));
            return;
        }
        Thermostat owner = registry.thermostatFor(current);
        if (owner != null) {
            player.sendMessage(red("This " + what + " belongs to " + SystemNames.of(owner)
                    + ". Sneak-click it with this tool to unlink it first."));
            return;
        }
        // Part of a headless system: the whole system comes along.
        String conflict = SystemLinks.conflict(registry.shapeOf(system), registry.shapeOf(current));
        if (conflict != null) {
            player.sendMessage(red(head.id() + " " + conflict + "."));
            return;
        }
        HvacRegistry.Members joining = registry.members(current);
        HvacRegistry.MergeResult merged = registry.merge(current, system);
        airflow.invalidate(current);
        airflow.invalidate(system);
        boolean single = joining.units() + joining.vents() + joining.panels() + joining.monitors() == 1;
        player.sendMessage(green(single ? capitalized(what) + " linked to " + head.id() + "."
                : "Linked a headless system to " + head.id() + ": " + SystemNames.contents(joining) + "."));
        if (merged.unlinkedPanel() != null)
            player.sendMessage(yellow(head.id() + " already had a settings panel, so the one at "
                    + SystemNames.at(merged.unlinkedPanel().position()) + " is unlinked now."));
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
                player.sendMessage(red(head.id() + " already has a settings panel at "
                        + SystemNames.at(existing.position()) + "."));
                return;
            }
            registry.linkSettingsPanel(panel, system);
            player.sendMessage(green("Settings panel linked to " + head.id() + "."));
        } else if (monitor != null) {
            registry.linkRpmMonitor(monitor, system);
            player.sendMessage(green("RPM monitor linked to " + head.id() + "."));
        } else if (registry.climateUnitAt(position) != null) {
            player.sendMessage(yellow("That block is part of a dehumidifier or humidifier, which needs no system."));
            return;
        } else if (ClimateVent.isVentBlock(block.getType()) && registry.deviceKind(position) == HvacRegistry.DeviceKind.NONE) {
            boolean water = ClimateVent.isWaterVent(block.getType());
            String conflict = SystemLinks.conflict(registry.shapeOf(system), SystemLinks.Shape.vent(water));
            if (conflict != null) {
                player.sendMessage(red(head.id() + " " + conflict + "."));
                return;
            }
            registry.addVent(new ClimateVent(position, system));
            airflow.invalidate(system);
            player.sendMessage(green((water ? "Water vent" : "Vent") + " linked to " + head.id() + "."));
        } else {
            player.sendMessage(yellow("Click equipment, an iron trapdoor (air vent), a waxed copper grate"
                    + " (water vent), a settings panel or an RPM monitor."));
            return;
        }
        changed(player, head);
    }

    /** After a link: the thermostat follows what it now holds, and the signs show it. */
    private void changed(Player player, Thermostat head) {
        Thermostat switched = registry.settleHead(head.system());
        if (switched != null)
            player.sendMessage(aqua(head.id() + " is a " + (switched.pool() ? "pool" : "room")
                    + " thermostat now: targets " + range(switched.pool()) + ", set to "
                    + settings.units().format(switched.targetF()) + "."));
        registry.markDirty();
        displays.refreshAll(true);
        plugin.requestImmediateSync();
        showLinked(player, head);
    }

    private void unlink(Player player, Block block, BlockKey position) {
        EquipmentUnit unit = registry.equipmentAt(position);
        SystemId previous = registry.systemAt(position);
        String from = previous == null ? "" : " from " + SystemNames.of(registry, previous);
        if (unit != null) {
            HvacRegistry.Members members = registry.members(unit.system());
            if (members.thermostat() == null && members.units() == 1 && members.vents() + members.panels()
                    + members.monitors() == 0) {
                player.sendMessage(yellow("That " + deviceName(position) + " isn't linked to anything."));
                return;
            }
            registry.linkEquipment(unit, registry.newSystem(position.worldId()));
            player.sendMessage(green(capitalized(deviceName(position)) + " unlinked" + from + "."));
        } else if (registry.ventAt(position) != null) {
            ClimateVent[] removed = new ClimateVent[1];
            registry.removingByCommand(() -> removed[0] = registry.removeVent(position));
            player.sendMessage(green((ClimateVent.isWaterVent(block.getType()) ? "Water vent" : "Vent")
                    + " unlinked" + from + "."));
        } else if (registry.settingsPanelAt(position) != null) {
            SettingsPanel panel = registry.settingsPanelAt(position);
            if (panel.system() == null) { player.sendMessage(yellow("That settings panel isn't linked.")); return; }
            registry.linkSettingsPanel(panel, null);
            player.sendMessage(green("Settings panel unlinked" + from + "; that system runs on Normal now."));
        } else if (registry.rpmMonitorAt(position) != null) {
            RpmMonitor monitor = registry.rpmMonitorAt(position);
            if (monitor.system() == null) { player.sendMessage(yellow("That RPM monitor isn't linked.")); return; }
            registry.linkRpmMonitor(monitor, null);
            player.sendMessage(green("RPM monitor unlinked" + from + "."));
        } else {
            player.sendMessage(yellow("Nothing is linked there."));
            return;
        }
        if (previous != null) {
            airflow.invalidate(previous);
            Thermostat head = registry.thermostatFor(previous);
            if (head != null) {
                Thermostat switched = registry.settleHead(previous);
                if (switched != null)
                    player.sendMessage(aqua(head.id() + " is a " + (switched.pool() ? "pool" : "room")
                            + " thermostat now: targets " + range(switched.pool()) + "."));
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
            player.sendMessage(yellow(thermostat.id() + " has nothing linked to it."));
            return;
        }
        registry.linkThermostat(thermostat, registry.newSystem(previous.worldId()));
        airflow.invalidate(previous);
        player.sendMessage(green(thermostat.id() + " unlinked. Its system (" + SystemNames.contents(members)
                + ") is headless now: link a thermostat to any part of it to take it over."));
        registry.markDirty();
        displays.refreshAll(true);
        plugin.requestImmediateSync();
    }

    private void selectHub(Player player, ItemStack item, SensorHub hub, BlockKey position) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(hubKey, PersistentDataType.STRING, write(position));
        meta.getPersistentDataContainer().remove(thermostatKey);
        meta.lore(List.of(Component.text("Selected: sensor hub " + hub.label(), NamedTextColor.GRAY),
                Component.text("Click sensors to link them.", NamedTextColor.GRAY),
                Component.text("Sneak-click to unlink.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        player.sendMessage(green("Selected sensor hub '" + hub.label() + "' (" + hub.sensors().size()
                + "/" + SensorHub.MAX_SENSORS + " sensors)."));
    }

    private void linkSensor(Player player, ItemStack item, Sensor sensor, BlockKey position, boolean unlink) {
        Selection selection = selection(item);
        SensorHub hub = selection == null || selection.hub() == null ? null : registry.sensorHubAt(selection.hub());
        if (hub == null) {
            player.sendMessage(red(selection == null || selection.hub() == null
                    ? "Right-click a sensor hub with this tool first, then sensors."
                    : "The selected sensor hub no longer exists. Select another one."));
            return;
        }
        if (unlink) {
            player.sendMessage(hub.unlink(position) ? green("Sensor '" + sensor.label() + "' unlinked from '"
                    + hub.label() + "'.") : yellow("That sensor isn't linked to '" + hub.label() + "'."));
        } else {
            player.sendMessage(switch (hub.link(position)) {
                case LINKED -> green("Sensor '" + sensor.label() + "' linked to '" + hub.label() + "' ("
                        + hub.sensors().size() + "/" + SensorHub.MAX_SENSORS + ").");
                case ALREADY_LINKED -> yellow("That sensor is already on '" + hub.label() + "'.");
                case FULL -> red("'" + hub.label() + "' already shows " + SensorHub.MAX_SENSORS
                        + " sensors. Unlink one first (sneak-click it).");
                case OTHER_WORLD -> red("Sensors and sensor hubs must be in the same world.");
            });
        }
        registry.markDirty();
        displays.refreshSensorHub(hub, true);
    }

    /** "heat pump", "water vent", "settings panel": what is at a block, for messages. */
    private String deviceName(BlockKey position) {
        EquipmentUnit unit = registry.equipmentAt(position);
        if (unit != null) return unit.type().displayName().toLowerCase(Locale.ROOT);
        if (registry.ventAt(position) != null) return "vent";
        if (registry.settingsPanelAt(position) != null) return "settings panel";
        if (registry.rpmMonitorAt(position) != null) return "RPM monitor";
        if (registry.thermostatAt(position) != null) return "thermostat";
        return "device";
    }

    private String range(boolean pool) {
        double low = pool ? settings.poolMinimumTargetF() : settings.minimumTargetF();
        double high = pool ? settings.poolMaximumTargetF() : settings.maximumTargetF();
        return settings.units().format(low, 0) + " to " + settings.units().format(high, 0);
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

    private static Component green(String value) { return Component.text(value, NamedTextColor.GREEN); }
    private static Component red(String value) { return Component.text(value, NamedTextColor.RED); }
    private static Component yellow(String value) { return Component.text(value, NamedTextColor.YELLOW); }
    private static Component aqua(String value) { return Component.text(value, NamedTextColor.AQUA); }
}
