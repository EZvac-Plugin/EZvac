package com.github.blade.hvac.command;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.service.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Compact command surface for the standalone plugin. */
public final class HvacCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "ezvac.admin";
    private static final String BUILD_PERMISSION = "ezvac.build";

    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final HvacController controller;
    private final OutdoorTemperatureService outdoor;
    private final AirflowService airflow;
    private final DisplayService displays;
    private final HvacSettings settings;
    private final NamespacedKey toolKey;
    private final NamespacedKey toolGroupKey;
    private final NamespacedKey toolWorldKey;

    public HvacCommand(HvacPlugin plugin, HvacRegistry registry, HvacController controller,
                       OutdoorTemperatureService outdoor, AirflowService airflow,
                       DisplayService displays, HvacSettings settings) {
        this.plugin = plugin;
        this.registry = registry;
        this.controller = controller;
        this.outdoor = outdoor;
        this.airflow = airflow;
        this.displays = displays;
        this.settings = settings;
        toolKey = new NamespacedKey(plugin, "duct_link_tool");
        toolGroupKey = new NamespacedKey(plugin, "duct_group");
        toolWorldKey = new NamespacedKey(plugin, "duct_world");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("stats")) {
            if (!requireAdmin(sender)) return true;
            sender.sendMessage(join(gold("EZvac: "), gray(registry.thermostats().size()
                    + " thermostat(s), " + registry.equipment().size() + " equipment unit(s), "
                    + registry.settingsPanels().size() + " settings panel(s), " + airflow.stats())));
            return true;
        }
        if (subcommand.equals("sync")) {
            if (!requireAdmin(sender)) return true;
            plugin.requestImmediateSync();
            sender.sendMessage(green("HVAC reconciliation queued."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(red("This command requires a player."));
            return true;
        }

        try {
            switch (subcommand) {
                case "temperature", "temp" -> temperature(player);
                case "list" -> list(player);
                case "info" -> info(player);
                case "create" -> { if (requireBuild(player)) create(player, args); }
                case "thermostat" -> thermostat(player, args);
                case "equipment" -> equipment(player, args);
                case "group" -> group(player, args);
                case "tool" -> { if (requireBuild(player)) giveTool(player); }
                case "remove" -> { if (requireBuild(player)) remove(player); }
                case "owner" -> { if (requireAdmin(player)) owner(player, args); }
                default -> player.sendMessage(red("Unknown subcommand. Use /hvac help."));
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage(red(exception.getMessage()));
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(red("Usage: /hvac create <thermostat|poolthermostat|heatpump|ac"
                    + "|furnace|boiler|thermometer|rpmmonitor|settingspanel> ..."));
            return;
        }
        Block block = target(player);
        if (block == null) return;
        BlockKey position = BlockKey.of(block.getLocation());
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "thermostat", "poolthermostat", "pool" -> {
                boolean pool = !args[1].equalsIgnoreCase("thermostat");
                if (args.length < 4) throw new IllegalArgumentException(
                        "Usage: /hvac create " + args[1] + " <id> <group> [targetF]");
                requireSign(block);
                GroupId target0 = GroupId.of(position, args[3]);
                if (!mayJoin(player, target0)) return;
                // A pool thermostat exists to unlock the water target range, so it
                // may only control a water-loop system. Without this a player could
                // heat a room to 104 F by pointing one at heat pumps.
                if (registry.hasEquipmentOfLoop(target0, !pool))
                    throw new IllegalArgumentException("System '" + target0.label() + "' is powered by "
                            + (pool ? "air-loop equipment; a pool thermostat needs boilers"
                                    : "boilers; use /hvac create poolthermostat") + ".");
                double target = args.length >= 5 ? finiteDouble(args[4], "target temperature")
                        : (pool ? 82.0 : 72.0);
                Thermostat thermostat = new Thermostat(position, args[2], args[3], target,
                        outdoor.temperatureAt(block.getLocation()), settings, pool,
                        player.getUniqueId());
                registry.addThermostat(thermostat);
                displays.refreshThermostat(thermostat, true);
                player.sendMessage(green("Created " + (pool ? "pool thermostat" : "thermostat")
                        + " '" + thermostat.id() + "' for group '" + thermostat.group().label()
                        + "'. Target range " + String.format(Locale.US, "%.0f-%.0f F",
                        pool ? settings.poolMinimumTargetF() : settings.minimumTargetF(),
                        pool ? settings.poolMaximumTargetF() : settings.maximumTargetF()) + "."));
            }
            case "heatpump", "heat_pump" -> createEquipment(player, block, args, EquipmentType.HEAT_PUMP);
            case "ac", "airconditioner", "air_conditioner" ->
                    createEquipment(player, block, args, EquipmentType.AIR_CONDITIONER);
            case "furnace" -> createEquipment(player, block, args, EquipmentType.FURNACE);
            case "boiler" -> createEquipment(player, block, args, EquipmentType.BOILER);
            case "thermometer" -> {
                requireSign(block);
                String displayLabel = args.length >= 3 ? args[2] : "Thermometer";
                registry.addThermometer(new Thermometer(position, displayLabel, player.getUniqueId()));
                displays.refreshAll(true);
                player.sendMessage(green("Created independent point thermometer."));
            }
            case "rpmmonitor", "rpm" -> {
                if (args.length < 4) throw new IllegalArgumentException(
                        "Usage: /hvac create rpmmonitor <group> <heatpump|ac|furnace|boiler> [label]");
                requireSign(block);
                GroupId group = new GroupId(block.getWorld().getUID(), args[2]);
                if (!mayJoin(player, group)) return;
                EquipmentType type = parseEquipmentType(args[3]);
                if (registry.equipmentFor(group).stream().noneMatch(unit -> unit.type() == type))
                    throw new IllegalArgumentException("No " + type.displayName() + " exists in that group.");
                String displayLabel = args.length >= 5 ? args[4] : group.label();
                registry.addRpmMonitor(new RpmMonitor(position, group, displayLabel, type,
                        player.getUniqueId()));
                displays.refreshAll(true);
                player.sendMessage(green("Created " + type.displayName() + " RPM monitor."));
            }
            case "settingspanel", "settings", "panel" -> {
                if (args.length < 3) throw new IllegalArgumentException(
                        "Usage: /hvac create settingspanel <group> [label]");
                requireSign(block);
                GroupId group = new GroupId(block.getWorld().getUID(), args[2]);
                if (!mayJoin(player, group)) return;
                if (registry.thermostatFor(group) == null && registry.equipmentFor(group).isEmpty())
                    throw new IllegalArgumentException("No HVAC group named '" + group.label()
                            + "' exists in this world.");
                String displayLabel = args.length >= 4 ? args[3] : group.label();
                SettingsPanel panel = new SettingsPanel(position, group.label(), displayLabel,
                        player.getUniqueId());
                registry.addSettingsPanel(panel);
                displays.refreshSettingsPanel(panel, true);
                player.sendMessage(green("Created settings panel for group '" + group.label()
                        + "' in NORMAL mode."));
            }
            default -> throw new IllegalArgumentException("Unknown device type '" + args[1] + "'.");
        }
        plugin.requestImmediateSync();
    }

    private void createEquipment(Player player, Block block, String[] args, EquipmentType type) {
        if (args.length < 3) throw new IllegalArgumentException(
                "Usage: /hvac create " + args[1] + " <group>");
        if (block.getType() != Material.DISPENSER)
            throw new IllegalArgumentException("Look directly at a dispenser.");
        BlockKey position = BlockKey.of(block.getLocation());
        // Water-loop sources serve vents in water; air-loop sources serve vents
        // in a room. A system draws on one loop only.
        GroupId target = GroupId.of(position, args[2]);
        if (!mayJoin(player, target)) return;
        Thermostat controller = registry.thermostatFor(target);
        if (controller != null && controller.pool() != type.waterLoop())
            throw new IllegalArgumentException("System '" + target.label() + "' is controlled by a "
                    + (controller.pool() ? "pool thermostat, which needs a boiler"
                                         : "room thermostat, which cannot use a boiler") + ".");
        if (registry.hasVentOfMedium(target, !type.waterLoop()))
            throw new IllegalArgumentException(type.displayName() + " is a "
                    + (type.waterLoop() ? "water" : "air") + " source, but system '"
                    + target.label() + "' distributes through "
                    + (type.waterLoop() ? "iron trapdoors (air)" : "waxed copper grates (water)")
                    + ".");
        EquipmentUnit unit = new EquipmentUnit(position, args[2], type, player.getUniqueId());
        registry.addEquipment(unit);
        player.sendMessage(green("Registered " + type.displayName() + " in group '"
                + unit.group().label() + "'."));
    }

    private void thermostat(Player player, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException(
                "Usage: /hvac thermostat <id> <set|enable|disable|group|info> ...");
        Thermostat thermostat = registry.thermostatById(args[1]);
        if (thermostat == null) throw new IllegalArgumentException("No thermostat named '" + args[1] + "'.");
        boolean readOnly = args[2].equalsIgnoreCase("info");
        if (!readOnly && !mayModify(player, thermostat.group(), thermostat.owner())) return;
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "set", "settemp" -> {
                if (args.length < 4) throw new IllegalArgumentException("Usage: /hvac thermostat <id> set <temperatureF>");
                thermostat.setTargetF(finiteDouble(args[3], "target temperature"), settings);
                registry.markDirty();
                player.sendMessage(green(String.format(Locale.US, "Target set to %.1f F.", thermostat.targetF())));
            }
            case "enable" -> { thermostat.setEnabled(true); registry.markDirty(); player.sendMessage(green("Thermostat enabled.")); }
            case "disable" -> { thermostat.setEnabled(false); registry.markDirty(); player.sendMessage(yellow("Thermostat disabled.")); }
            case "group", "setgroup", "sethvac" -> {
                if (args.length < 4) throw new IllegalArgumentException("Usage: /hvac thermostat <id> group <group>");
                GroupId replacement = GroupId.of(thermostat.position(), args[3]);
                if (!mayJoin(player, replacement)) return;
                Thermostat existing = registry.thermostatFor(replacement);
                if (existing != null && existing != thermostat)
                    throw new IllegalArgumentException("That group already has thermostat '" + existing.id() + "'.");
                GroupId previous = thermostat.group();
                thermostat.setGroup(args[3]);
                airflow.invalidate(previous);
                airflow.invalidate(thermostat.group());
                registry.markDirty();
                player.sendMessage(green("Thermostat moved to group '" + thermostat.group().label() + "'."));
            }
            case "info" -> showThermostat(player, thermostat);
            default -> throw new IllegalArgumentException("Unknown thermostat action '" + args[2] + "'.");
        }
        displays.refreshThermostat(thermostat, true);
        plugin.requestImmediateSync();
    }

    private void equipment(Player player, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException(
                "Usage: /hvac equipment <enable|disable|group|info>");
        Block block = target(player);
        if (block == null) return;
        EquipmentUnit unit = registry.equipmentAt(BlockKey.of(block.getLocation()));
        if (unit == null) throw new IllegalArgumentException("That block is not registered HVAC equipment.");
        if (!args[1].equalsIgnoreCase("info") && !mayModify(player, unit.group(), unit.owner())) return;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "enable" -> { unit.setEnabled(true); player.sendMessage(green("Equipment enabled.")); }
            case "disable" -> { unit.setEnabled(false); unit.stop(); player.sendMessage(yellow("Equipment disabled.")); }
            case "group" -> {
                if (args.length < 3) throw new IllegalArgumentException("Usage: /hvac equipment group <group>");
                if (!mayJoin(player, GroupId.of(unit.position(), args[2]))) return;
                unit.stop();
                if (unit.hasOwnedOutput())
                    throw new IllegalArgumentException("The old output chunk must load before this unit can change groups.");
                unit.setGroup(args[2]);
                player.sendMessage(green("Equipment moved to group '" + unit.group().label() + "'."));
            }
            case "info" -> showEquipment(player, unit);
            default -> throw new IllegalArgumentException("Unknown equipment action '" + args[1] + "'.");
        }
        registry.markDirty();
        plugin.requestImmediateSync();
    }

    private void group(Player player, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException(
                "Usage: /hvac group <group> <enable|disable|info>");
        GroupId group = new GroupId(player.getWorld().getUID(), args[1]);
        Thermostat thermostat = registry.thermostatFor(group);
        List<EquipmentUnit> units = registry.equipmentFor(group);
        if (!args[2].equalsIgnoreCase("info")
                && !mayModify(player, group, thermostat == null ? null : thermostat.owner())) return;
        if (thermostat == null && units.isEmpty())
            throw new IllegalArgumentException("No HVAC group named '" + group.label() + "' in this world.");
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "enable" -> {
                if (thermostat != null) thermostat.setEnabled(true);
                units.forEach(unit -> unit.setEnabled(true));
                player.sendMessage(green("Group '" + group.label() + "' enabled."));
            }
            case "disable" -> {
                if (thermostat != null) thermostat.setEnabled(false);
                units.forEach(unit -> { unit.setEnabled(false); unit.stop(); });
                player.sendMessage(yellow("Group '" + group.label() + "' disabled."));
            }
            case "info" -> showGroup(player, group);
            default -> throw new IllegalArgumentException("Unknown group action '" + args[2] + "'.");
        }
        registry.markDirty();
        plugin.requestImmediateSync();
    }

    private void temperature(Player player) {
        AirflowService.TemperatureReading reading = airflow.temperatureAt(player.getLocation());
        player.sendMessage(join(gold("Temperature"), gray(" | Here: "),
                white(String.format(Locale.US, "%.1f F", reading.localF())), gray(" | Outside: "),
                white(String.format(Locale.US, "%.1f F", reading.outdoorF()))));
        if (!reading.conditioned()) {
            player.sendMessage(gray("No completed linked-vent path reaches this block."));
        } else {
            String zone = reading.airflowDistance() <= settings.airflowCoreRange()
                    ? "core" : Math.round(reading.influence() * 100) + "% leakage";
            player.sendMessage(join(gray("Group: "), white(reading.group().label()), gray(" | Path: "),
                    white(reading.airflowDistance() + " blocks"), gray(" | Zone: "), white(zone)));
        }
    }

    private void list(Player player) {
        Set<GroupId> groups = new TreeSet<>();
        registry.thermostats().stream().map(Thermostat::group)
                .filter(group -> group.worldId().equals(player.getWorld().getUID())).forEach(groups::add);
        registry.equipment().stream().map(EquipmentUnit::group)
                .filter(group -> group.worldId().equals(player.getWorld().getUID())).forEach(groups::add);
        if (groups.isEmpty()) { player.sendMessage(gray("No HVAC systems exist in this world.")); return; }
        player.sendMessage(gold("EZvac systems:"));
        for (GroupId group : groups) {
            HvacController.GroupStatus status = controller.status(group);
            String temperatures = Double.isFinite(status.roomF())
                    ? String.format(Locale.US, "%.1f/%.1f F", status.roomF(), status.targetF()) : "no thermostat";
            player.sendMessage(join(white(group.label()), gray(" | "), white(status.mode().name()),
                    gray(" | "), white(status.profile().name()), gray(" | "), white(temperatures),
                    gray(" | equipment "),
                    white(status.running() + "/" + status.equipment())));
        }
    }

    private void info(Player player) {
        Block block = target(player);
        if (block == null) return;
        BlockKey key = BlockKey.of(block.getLocation());
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat != null) { showThermostat(player, thermostat); return; }
        EquipmentUnit unit = registry.equipmentAt(key);
        if (unit != null) { showEquipment(player, unit); return; }
        ClimateVent vent = registry.ventAt(key);
        if (vent != null) { player.sendMessage(join(gold("Vent"), gray(" | Group: "), white(vent.group().label()))); return; }
        Thermometer thermometer = registry.thermometerAt(key);
        if (thermometer != null) {
            AirflowService.TemperatureReading reading = airflow.temperatureAt(block.getLocation());
            String source = reading.conditioned() ? reading.group().label() : "outdoor";
            player.sendMessage(join(gold("Thermometer"), gray(" | Local: "),
                    white(String.format(Locale.US, "%.1f F", reading.localF())),
                    gray(" | Source: "), white(source)));
            return;
        }
        RpmMonitor monitor = registry.rpmMonitorAt(key);
        if (monitor != null) { player.sendMessage(join(gold("RPM monitor"), gray(" | Group: "),
                white(monitor.group().label()), gray(" | Type: "), white(monitor.equipmentType().displayName()))); return; }
        SettingsPanel panel = registry.settingsPanelAt(key);
        if (panel != null) { player.sendMessage(join(gold("Settings panel"), gray(" | Group: "),
                white(panel.group().label()), gray(" | Profile: "), white(panel.profile().name()))); return; }
        player.sendMessage(red("No EZvac device is registered at that block."));
    }

    /** Resolves whatever device sits at a block and checks it against its system. */
    private boolean mayModifyDeviceAt(Player player, BlockKey key) {
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat != null) return mayModify(player, thermostat.group(), thermostat.owner());
        EquipmentUnit unit = registry.equipmentAt(key);
        if (unit != null) return mayModify(player, unit.group(), unit.owner());
        ClimateVent vent = registry.ventAt(key);
        if (vent != null) return mayModify(player, vent.group(), vent.owner());
        SettingsPanel panel = registry.settingsPanelAt(key);
        if (panel != null) return mayModify(player, panel.group(), panel.owner());
        RpmMonitor monitor = registry.rpmMonitorAt(key);
        if (monitor != null) return mayModify(player, monitor.group(), monitor.owner());
        Thermometer probe = registry.thermometerAt(key);
        if (probe != null) return mayModify(player, null, probe.owner());
        return true; // nothing registered here; the caller reports that itself
    }

    private void owner(Player player, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Usage: /hvac owner <player>");
        Player recipient = plugin.getServer().getPlayerExact(args[1]);
        if (recipient == null) throw new IllegalArgumentException("Player '" + args[1]
                + "' must be online to receive ownership.");
        Block block = target(player);
        if (block == null) return;
        BlockKey key = BlockKey.of(block.getLocation());
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat != null) {
            int changed = registry.assignGroupOwner(thermostat.group(), recipient.getUniqueId());
            player.sendMessage(green("System '" + thermostat.group().label() + "' now belongs to "
                    + recipient.getName() + " (" + changed + " device(s) reassigned)."));
            recipient.sendMessage(green("You now own the HVAC system '"
                    + thermostat.group().label() + "'."));
        } else if (registry.assignDeviceOwner(key, recipient.getUniqueId())) {
            player.sendMessage(green("That device now belongs to " + recipient.getName() + "."));
            recipient.sendMessage(green("You now own an EZvac device at " + key + "."));
        } else {
            player.sendMessage(red("No EZvac device is registered at that block."));
            return;
        }
        plugin.requestImmediateSync();
    }

    private void remove(Player player) {
        Block block = target(player);
        if (block == null) return;
        BlockKey key = BlockKey.of(block.getLocation());
        if (!mayModifyDeviceAt(player, key)) return;
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat != null) {
            registry.removeThermostat(key);
            player.sendMessage(green("Thermostat removed."));
        } else if (registry.equipmentAt(key) != null) {
            registry.removeEquipment(key);
            player.sendMessage(green("Equipment removed; owned output cleanup was queued if needed."));
        } else {
            ClimateVent vent = registry.removeVent(key);
            if (vent != null) { airflow.invalidate(vent.group()); player.sendMessage(green("Vent unlinked.")); }
            else if (registry.removeThermometer(key)) player.sendMessage(green("Thermometer removed."));
            else if (registry.removeRpmMonitor(key)) player.sendMessage(green("RPM monitor removed."));
            else if (registry.removeSettingsPanel(key))
                player.sendMessage(green("Settings panel removed; group reverted to NORMAL."));
            else { player.sendMessage(red("No EZvac device is registered at that block.")); return; }
        }
        displays.invalidate(key);
        plugin.requestImmediateSync();
    }

    private void giveTool(Player player) {
        ItemStack tool = new ItemStack(Material.STICK);
        ItemMeta meta = tool.getItemMeta();
        meta.displayName(Component.text("HVAC Duct Link Tool", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Select equipment, then vents.", NamedTextColor.GRAY),
                Component.text("Iron trapdoor = air, waxed copper grate = water.", NamedTextColor.GRAY),
                Component.text("Sneak-click a vent to unlink it.", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        tool.setItemMeta(meta);
        player.getInventory().addItem(tool);
        player.sendMessage(green("Duct link tool added to your inventory."));
    }

    public boolean isDuctTool(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    public void handleDuctToolClick(Player player, Block block, ItemStack item, boolean unlink) {
        if (!isDuctTool(item)) return;
        if (!player.hasPermission(BUILD_PERMISSION) && !player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(red("You need " + BUILD_PERMISSION + " to link vents."));
            return;
        }
        BlockKey position = BlockKey.of(block.getLocation());
        EquipmentUnit unit = registry.equipmentAt(position);
        if (unit != null) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(toolGroupKey, PersistentDataType.STRING, unit.group().label());
            meta.getPersistentDataContainer().set(toolWorldKey, PersistentDataType.STRING, unit.group().worldId().toString());
            meta.lore(List.of(Component.text("Selected: " + unit.group().label(), NamedTextColor.GRAY),
                    Component.text("Click vents to link them.", NamedTextColor.GRAY)));
            item.setItemMeta(meta);
            player.sendMessage(green("Selected group '" + unit.group().label() + "'."));
            return;
        }
        if (!ClimateVent.isVentBlock(block.getType())) {
            player.sendMessage(yellow("Select registered equipment, an iron trapdoor (air),"
                    + " or a waxed copper grate (water)."));
            return;
        }
        if (unlink) {
            if (!mayModifyDeviceAt(player, position)) return;
            ClimateVent removed = registry.removeVent(position);
            if (removed == null) player.sendMessage(yellow("That block is not a registered vent."));
            else { airflow.invalidate(removed.group()); player.sendMessage(green("Vent unlinked.")); }
            return;
        }
        ItemMeta meta = item.getItemMeta();
        String label = meta.getPersistentDataContainer().get(toolGroupKey, PersistentDataType.STRING);
        String worldText = meta.getPersistentDataContainer().get(toolWorldKey, PersistentDataType.STRING);
        if (label == null || worldText == null) {
            player.sendMessage(red("Select registered equipment with this tool first."));
            return;
        }
        UUID worldId;
        try { worldId = UUID.fromString(worldText); }
        catch (IllegalArgumentException exception) { player.sendMessage(red("This tool contains invalid link data.")); return; }
        if (!worldId.equals(block.getWorld().getUID())) {
            player.sendMessage(red("Equipment and vents must be in the same world."));
            return;
        }
        GroupId group = new GroupId(worldId, label);
        if (registry.equipmentFor(group).isEmpty()) {
            player.sendMessage(red("The selected HVAC group no longer has equipment."));
            return;
        }
        // A system is all air or all water: water cannot come out of a ceiling
        // vent, and conditioned air cannot be piped into a pool.
        if (!mayJoin(player, group)) return;
        boolean water = ClimateVent.isWaterVent(block.getType());
        if (registry.hasEquipmentOfLoop(group, !water)) {
            player.sendMessage(red("System '" + group.label() + "' is powered by "
                    + (water ? "air-loop equipment, which cannot condition water"
                             : "a boiler, which cannot condition air") + "."));
            return;
        }
        if (registry.hasVentOfMedium(group, !water)) {
            player.sendMessage(red("System '" + group.label() + "' already uses "
                    + (water ? "iron trapdoors (air)" : "waxed copper grates (water)")
                    + ". A system must be all air or all water; unlink its existing vents first."));
            return;
        }
        ClimateVent previous = registry.ventAt(position);
        registry.addVent(new ClimateVent(position, group, player.getUniqueId()));
        if (previous != null) airflow.invalidate(previous.group());
        airflow.invalidate(group);
        player.sendMessage(green((water ? "Water vent" : "Vent") + " linked to group '"
                + group.label() + "'."));
    }

    private void showThermostat(Player player, Thermostat thermostat) {
        player.sendMessage(join(gold("Thermostat "), white(thermostat.id()), gray(" | Group: "),
                white(thermostat.group().label())));
        player.sendMessage(join(gray("Room/target: "), white(String.format(Locale.US, "%.1f / %.1f F",
                thermostat.roomF(), thermostat.targetF())), gray(" | Mode: "), white(thermostat.mode().name()),
                gray(" | Profile: "), white(registry.profileFor(thermostat.group()).name()),
                gray(" | "), white(thermostat.enabled() ? "enabled" : "disabled")));
    }

    private void showEquipment(Player player, EquipmentUnit unit) {
        player.sendMessage(join(gold(unit.type().displayName()), gray(" | Group: "), white(unit.group().label())));
        player.sendMessage(join(gray("State: "), white(unit.running() ? unit.operatingMode().name() : "IDLE"),
                gray(" | RPM: "), white(String.format(Locale.US, "%.0f", unit.motorRpm())),
                gray(" | Profile: "), white(unit.type() == EquipmentType.FURNACE
                        ? "FIXED" : registry.profileFor(unit.group()).name()),
                gray(" | Output: "), white(unit.hasOwnedOutput() ? "owned" : "none"),
                gray(" | "), white(unit.enabled() ? "enabled" : "disabled")));
    }

    private void showGroup(Player player, GroupId group) {
        HvacController.GroupStatus status = controller.status(group);
        player.sendMessage(join(gold("Group "), white(group.label()), gray(" | Mode: "), white(status.mode().name())));
        player.sendMessage(join(gray("Controller loaded: "), white(Boolean.toString(status.controllerLoaded())),
                gray(" | Profile: "), white(status.profile().name()),
                gray(" | Equipment: "), white(status.running() + "/" + status.equipment()),
                gray(" | Capacity: "), white(Math.round(status.capacity() * 100) + "%")));
    }

    private Block target(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) player.sendMessage(red("Look directly at a block within 6 blocks."));
        return block;
    }

    private static void requireSign(Block block) {
        if (!(block.getState() instanceof Sign)) throw new IllegalArgumentException("Look directly at a sign.");
    }

    private static double finiteDouble(String text, String name) {
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a finite number");
        }
    }

    private static EquipmentType parseEquipmentType(String text) {
        return switch (text.toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "heatpump", "heat_pump" -> EquipmentType.HEAT_PUMP;
            case "ac", "airconditioner", "air_conditioner" -> EquipmentType.AIR_CONDITIONER;
            case "furnace" -> EquipmentType.FURNACE;
            case "boiler" -> EquipmentType.BOILER;
            default -> throw new IllegalArgumentException(
                    "equipment type must be heatpump, ac, furnace, or boiler");
        };
    }

    private static boolean requireBuild(CommandSender sender) {
        if (sender.hasPermission(BUILD_PERMISSION) || sender.hasPermission(ADMIN_PERMISSION)) return true;
        sender.sendMessage(red("You need " + BUILD_PERMISSION + " to build HVAC devices."));
        return false;
    }

    /**
     * Ownership resolves through the system's thermostat, so the message has to
     * distinguish the two failures: a system someone else owns, and one nobody
     * owns. The second is the common case right after upgrading and is baffling
     * without an explanation.
     */
    /**
     * Whether a device may be moved <em>into</em> a group. Unlike modifying, an
     * empty or unowned destination is allowed: building equipment and then
     * naming a group that does not exist yet is the normal way to start a
     * system. What this blocks is joining a system somebody else owns, which
     * would otherwise let anyone attach unwanted equipment to it.
     */
    private boolean mayJoin(Player player, GroupId destination) {
        Thermostat controller = registry.thermostatFor(destination);
        java.util.UUID owner = controller == null ? null : controller.owner();
        if (owner == null || player.hasPermission(ADMIN_PERMISSION)
                || owner.equals(player.getUniqueId())) return true;
        player.sendMessage(red("Group '" + destination.label() + "' belongs to someone else."));
        return false;
    }

    public boolean mayModify(Player player, GroupId group, java.util.UUID deviceOwner) {
        java.util.UUID owner = registry.controllerOwner(group, deviceOwner);
        if (Ownership.mayModify(owner, player.getUniqueId(), player.hasPermission(ADMIN_PERMISSION)))
            return true;
        if (owner == null)
            player.sendMessage(red("That system has no owner, so only an administrator can change"
                    + " it. An administrator can assign it with /hvac owner <player>."));
        else
            player.sendMessage(red("That system belongs to someone else."));
        return false;
    }

    private static boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) return true;
        sender.sendMessage(red("You need " + ADMIN_PERMISSION + "."));
        return false;
    }

    private static void help(CommandSender sender) {
        sender.sendMessage(gold("EZvac commands"));
        sender.sendMessage(join(aqua("/hvac temperature"), gray(" - read local/outdoor temperature")));
        sender.sendMessage(join(aqua("/hvac list | info"), gray(" - inspect systems or the targeted device")));
        // Builders see the whole working set; only the two server-wide commands
        // and ownership transfer are held back for administrators.
        if (!sender.hasPermission(BUILD_PERMISSION) && !sender.hasPermission(ADMIN_PERMISSION)) return;
        sender.sendMessage(join(aqua("/hvac create thermostat|poolthermostat <id> <group> [target]"), gray(" - register a sign")));
        sender.sendMessage(join(aqua("/hvac create heatpump|ac|furnace|boiler <group>"), gray(" - register a dispenser")));
        sender.sendMessage(join(aqua("/hvac create thermometer [label]"), gray(" - register a point-sensor sign")));
        sender.sendMessage(join(aqua("/hvac create rpmmonitor <group> <type> [label]"), gray(" - register a sign")));
        sender.sendMessage(join(aqua("/hvac create settingspanel <group> [label]"), gray(" - register profile controls")));
        sender.sendMessage(join(aqua("/hvac thermostat <id> set|enable|disable|group"), gray(" - control a thermostat")));
        sender.sendMessage(join(aqua("/hvac equipment <enable|disable|group|info>"), gray(" - edit targeted equipment")));
        sender.sendMessage(join(aqua("/hvac group <group> <enable|disable|info>"), gray(" - control a system")));
        sender.sendMessage(join(aqua("/hvac tool | remove"), gray(" - link vents, remove the targeted device")));
        sender.sendMessage(gray("You can change only systems you own; a system belongs to whoever owns its thermostat."));
        if (!sender.hasPermission(ADMIN_PERMISSION)) return;
        sender.sendMessage(join(aqua("/hvac owner <player>"), gray(" - hand a system to another player")));
        sender.sendMessage(join(aqua("/hvac stats | sync"), gray(" - diagnose, reconcile")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(args[0], "help", "temperature", "list", "info",
                "create", "thermostat", "equipment", "group", "tool", "remove", "owner",
                "stats", "sync");
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> filter(args[1], "thermostat", "poolthermostat", "heatpump",
                        "ac", "furnace", "boiler", "thermometer", "rpmmonitor", "settingspanel");
                case "thermostat" -> filter(args[1], registry.thermostats().stream().map(Thermostat::id).toArray(String[]::new));
                case "equipment" -> filter(args[1], "enable", "disable", "group", "info");
                case "group" -> filter(args[1], registry.thermostats().stream().map(value -> value.group().label()).distinct().toArray(String[]::new));
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("thermostat"))
            return filter(args[2], "set", "enable", "disable", "group", "info");
        if (args.length == 3 && args[0].equalsIgnoreCase("group"))
            return filter(args[2], "enable", "disable", "info");
        if (args.length == 4 && args[0].equalsIgnoreCase("create") && args[1].equalsIgnoreCase("rpmmonitor"))
            return filter(args[3], "heatpump", "ac", "furnace", "boiler");
        return List.of();
    }

    private static List<String> filter(String prefix, String... options) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return Arrays.stream(options).filter(Objects::nonNull)
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
                .distinct().sorted().toList();
    }

    private static Component join(Component... parts) {
        Component message = Component.empty();
        for (Component part : parts) message = message.append(part);
        return message;
    }

    private static Component green(String value) { return Component.text(value, NamedTextColor.GREEN); }
    private static Component red(String value) { return Component.text(value, NamedTextColor.RED); }
    private static Component yellow(String value) { return Component.text(value, NamedTextColor.YELLOW); }
    private static Component gray(String value) { return Component.text(value, NamedTextColor.GRAY); }
    private static Component white(String value) { return Component.text(value, NamedTextColor.WHITE); }
    private static Component gold(String value) { return Component.text(value, NamedTextColor.GOLD); }
    private static Component aqua(String value) { return Component.text(value, NamedTextColor.AQUA); }
}
