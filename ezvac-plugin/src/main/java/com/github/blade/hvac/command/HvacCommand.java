package com.github.blade.hvac.command;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.api.event.ThermostatTargetChangeEvent;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.hud.ClimateHudService;
import com.github.blade.hvac.integration.api.ApiEvents;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.service.*;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.Seasons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Compact command surface for the standalone plugin. */
public final class HvacCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "ezvac.admin";
    private static final String HUD_PERMISSION = "ezvac.hud";

    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final HvacController controller;
    private final OutdoorTemperatureService outdoor;
    private final AirflowService airflow;
    private final DisplayService displays;
    private final HvacSettings settings;
    private final ClimateHudService hud;
    private final ClimateUnitService units;
    private final NamespacedKey toolKey;
    private final NamespacedKey toolGroupKey;
    private final NamespacedKey sensorToolKey;
    private final NamespacedKey sensorToolHubKey;
    private final NamespacedKey toolWorldKey;

    public HvacCommand(HvacPlugin plugin, HvacRegistry registry, HvacController controller,
                       OutdoorTemperatureService outdoor, AirflowService airflow,
                       DisplayService displays, HvacSettings settings, ClimateHudService hud,
                       ClimateUnitService units) {
        this.plugin = plugin;
        this.registry = registry;
        this.controller = controller;
        this.outdoor = outdoor;
        this.airflow = airflow;
        this.displays = displays;
        this.settings = settings;
        this.hud = hud;
        this.units = units;
        toolKey = new NamespacedKey(plugin, "duct_link_tool");
        toolGroupKey = new NamespacedKey(plugin, "duct_group");
        sensorToolKey = new NamespacedKey(plugin, "sensor_link_tool");
        sensorToolHubKey = new NamespacedKey(plugin, "sensor_hub");
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
        if (subcommand.equals("season")) {
            try {
                season(sender, args);
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(red(exception.getMessage()));
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(red("This command requires a player."));
            return true;
        }

        try {
            switch (subcommand) {
                case "temperature", "temp" -> temperature(player);
                case "hud" -> hud(player, args);
                case "list" -> list(player);
                case "info" -> info(player);
                case "create" -> { if (requireAdmin(player)) create(player, args); }
                case "thermostat" -> { if (requireAdmin(player)) thermostat(player, args); }
                case "equipment" -> { if (requireAdmin(player)) equipment(player, args); }
                case "group" -> { if (requireAdmin(player)) group(player, args); }
                // "tool" was its name before the sensor tool existed; kept so old habits still work.
                case "duct-tool", "ducttool", "tool" -> { if (requireAdmin(player)) giveTool(player); }
                case "sensor-tool", "sensortool" -> { if (requireAdmin(player)) giveSensorTool(player); }
                case "remove" -> { if (requireAdmin(player)) remove(player); }
                default -> player.sendMessage(red("Unknown subcommand. Use /hvac help."));
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage(red(exception.getMessage()));
        }
        return true;
    }

    private void hud(Player player, String[] args) {
        if (!player.hasPermission(HUD_PERMISSION)) {
            player.sendMessage(red("You may not use the climate HUD."));
            return;
        }
        if (!hud.enabled()) {
            player.sendMessage(yellow("The climate HUD is turned off on this server."));
            return;
        }
        boolean show;
        if (args.length < 2) show = !hud.isShowing(player);
        else if (args[1].equalsIgnoreCase("on")) show = true;
        else if (args[1].equalsIgnoreCase("off")) show = false;
        else throw new IllegalArgumentException("Usage: /hvac hud [on|off]");
        hud.setShowing(player, show);
        player.sendMessage(show ? green("Climate HUD on. Use /hvac hud again to hide it.")
                : gray("Climate HUD off."));
    }

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(red("Usage: /hvac create <thermostat|poolthermostat|heatpump|ac"
                    + "|furnace|boiler|poolheatpump|thermometer|hygrometer|thermohygrometer|rpmmonitor"
                    + "|settingspanel|dehumidifier|humidifier> ..."));
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
                // A pool thermostat exists to unlock the water target range, so it
                // may only control a water-loop system. Without this a player could
                // heat a room to 104 F by pointing one at heat pumps.
                if (registry.hasEquipmentOfLoop(target0, !pool))
                    throw new IllegalArgumentException("System '" + target0.label() + "' is powered by "
                            + (pool ? "air-loop equipment; a pool thermostat needs a boiler or pool heat pump"
                                    : "water-loop equipment; use /hvac create poolthermostat") + ".");
                double target = args.length >= 5 ? settings.units().parse(args[4])
                        : (pool ? 82.0 : 72.0);
                Thermostat thermostat = new Thermostat(position, args[2], args[3], target,
                        outdoor.temperatureAt(block.getLocation()), settings, pool);
                registry.addThermostat(thermostat);
                displays.refreshThermostat(thermostat, true);
                player.sendMessage(green("Created " + (pool ? "pool thermostat" : "thermostat")
                        + " '" + thermostat.id() + "' for group '" + thermostat.group().label()
                        + "'. Target range " + range(pool ? settings.poolMinimumTargetF() : settings.minimumTargetF(),
                        pool ? settings.poolMaximumTargetF() : settings.maximumTargetF()) + "."));
            }
            case "heatpump", "heat_pump" -> createEquipment(player, block, args, EquipmentType.HEAT_PUMP);
            case "ac", "airconditioner", "air_conditioner" ->
                    createEquipment(player, block, args, EquipmentType.AIR_CONDITIONER);
            case "furnace" -> createEquipment(player, block, args, EquipmentType.FURNACE);
            case "boiler" -> createEquipment(player, block, args, EquipmentType.BOILER);
            case "poolheatpump", "pool_heat_pump", "poolhp" ->
                    createEquipment(player, block, args, EquipmentType.POOL_HEAT_PUMP);
            case "thermometer", "hygrometer", "thermohygrometer", "thermohygro", "thermo-hygrometer" -> {
                requireSign(block);
                Thermometer.Reads reads = switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "thermometer" -> Thermometer.Reads.TEMPERATURE;
                    case "hygrometer" -> Thermometer.Reads.HUMIDITY;
                    default -> Thermometer.Reads.BOTH;
                };
                String displayLabel = args.length >= 3 ? args[2] : reads.displayName().replace("-", "");
                registry.addThermometer(new Thermometer(position, displayLabel, reads));
                displays.refreshAll(true);
                player.sendMessage(green("Created independent point " + reads.displayName().toLowerCase(Locale.ROOT) + "."));
            }
            case "rpmmonitor", "rpm" -> {
                if (args.length < 4) throw new IllegalArgumentException(
                        "Usage: /hvac create rpmmonitor <group> <heatpump|ac|furnace|boiler|poolheatpump> [label]");
                requireSign(block);
                GroupId group = new GroupId(block.getWorld().getUID(), args[2]);
                EquipmentType type = parseEquipmentType(args[3]);
                if (registry.equipmentFor(group).stream().noneMatch(unit -> unit.type() == type))
                    throw new IllegalArgumentException("No " + type.displayName() + " exists in that group.");
                String displayLabel = args.length >= 5 ? args[4] : group.label();
                registry.addRpmMonitor(new RpmMonitor(position, group, displayLabel, type));
                displays.refreshAll(true);
                player.sendMessage(green("Created " + type.displayName() + " RPM monitor."));
            }
            case "settingspanel", "settings", "panel" -> {
                if (args.length < 3) throw new IllegalArgumentException(
                        "Usage: /hvac create settingspanel <group> [label]");
                requireSign(block);
                GroupId group = new GroupId(block.getWorld().getUID(), args[2]);
                if (registry.thermostatFor(group) == null && registry.equipmentFor(group).isEmpty())
                    throw new IllegalArgumentException("No HVAC group named '" + group.label()
                            + "' exists in this world.");
                String displayLabel = args.length >= 4 ? args[3] : group.label();
                SettingsPanel panel = new SettingsPanel(position, group.label(), displayLabel);
                registry.addSettingsPanel(panel);
                displays.refreshSettingsPanel(panel, true);
                player.sendMessage(green("Created settings panel for group '" + group.label()
                        + "' in NORMAL mode."));
            }
            case "dehumidifier", "dehum" -> createDehumidifier(player, block, position, args);
            case "humidifier", "hum" -> createHumidifier(player, block, position, args);
            case "sensor" -> {
                Sensor.Kind reads = Sensor.forButton(block.getType());
                if (reads == null)
                    throw new IllegalArgumentException("Look at a stone button (temperature), a polished blackstone"
                            + " button (humidity), a mangrove button (both) or a birch button (weather).");
                Sensor sensor = new Sensor(position, args.length >= 3 ? args[2] : "Sensor", reads);
                registry.addSensor(sensor);
                player.sendMessage(green("Created " + reads.description() + " sensor '" + sensor.label()
                        + "'. Link it to a sensor hub with /hvac sensor-tool."
                        + (reads == Sensor.Kind.WEATHER ? " It needs open sky above it, like a rain gauge." : "")));
            }
            case "sensor-hub", "sensorhub" -> {
                requireSign(block);
                SensorHub hub = new SensorHub(position, args.length >= 3 ? args[2] : "Sensors");
                registry.addSensorHub(hub);
                displays.refreshSensorHub(hub, true);
                player.sendMessage(green("Created sensor hub '" + hub.label() + "'. Link up to "
                        + SensorHub.MAX_SENSORS + " sensors to it with /hvac sensor-tool."));
            }
            default -> throw new IllegalArgumentException("Unknown device type '" + args[1] + "'.");
        }
        plugin.requestImmediateSync();
    }

    /**
     * A dehumidifier is an iron block on a waxed copper grate, controlled by
     * the sign the player is looking at: a wall sign on the iron block, or a
     * sign standing on top of it.
     */
    private void createDehumidifier(Player player, Block sign, BlockKey position, String[] args) {
        requireSign(sign);
        Block iron = sign.getBlockData() instanceof WallSign wall
                ? sign.getRelative(wall.getFacing().getOppositeFace()) : sign.getRelative(BlockFace.DOWN);
        if (iron.getType() != Material.IRON_BLOCK)
            throw new IllegalArgumentException("Put the sign on an iron block: on its side, or standing on top.");
        Block grate = iron.getRelative(BlockFace.DOWN);
        if (grate.getType() != ClimateVent.WATER_VENT)
            throw new IllegalArgumentException("The iron block must sit on a waxed copper grate.");
        BlockKey grateKey = BlockKey.of(grate.getLocation());
        if (registry.ventAt(grateKey) != null)
            throw new IllegalArgumentException("That grate is linked as a water vent. Unlink it first"
                    + " (sneak-click it with /hvac tool).");
        String label = args.length >= 3 ? args[2] : "Dehumidifier";
        registerUnit(player, new Dehumidifier(position, BlockKey.of(iron.getLocation()), grateKey, label,
                units.climate().dehumidifier().defaultSetpoint()));
    }

    /**
     * A humidifier is an iron block with a waxed cut copper slab (bottom half)
     * on top, controlled by a wall sign on the iron block.
     */
    private void createHumidifier(Player player, Block sign, BlockKey position, String[] args) {
        requireSign(sign);
        if (!(sign.getBlockData() instanceof WallSign wall))
            throw new IllegalArgumentException("Put the sign on the side of the iron block.");
        Block iron = sign.getRelative(wall.getFacing().getOppositeFace());
        if (iron.getType() != Material.IRON_BLOCK)
            throw new IllegalArgumentException("Put the sign on the side of an iron block.");
        Block slab = iron.getRelative(BlockFace.UP);
        if (slab.getType() != Humidifier.TOP || !(slab.getBlockData() instanceof Slab half)
                || half.getType() != Slab.Type.BOTTOM)
            throw new IllegalArgumentException("Put a waxed cut copper slab (bottom half) on top of the iron block.");
        String label = args.length >= 3 ? args[2] : "Humidifier";
        registerUnit(player, new Humidifier(position, BlockKey.of(iron.getLocation()), BlockKey.of(slab.getLocation()),
                label, units.climate().humidifier().defaultSetpoint()));
    }

    private void registerUnit(Player player, ClimateUnit unit) {
        registry.addClimateUnit(unit);
        airflow.invalidate(ZoneId.of(unit));
        displays.refreshClimateUnit(unit, true);
        player.sendMessage(green("Created " + unit.kind().displayName().toLowerCase(Locale.ROOT) + " '"
                + unit.label() + "' at " + unit.setpoint() + "%. Click the sign to change the setpoint,"
                + " sneak-click to switch it on or off."));
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
        Thermostat controller = registry.thermostatFor(target);
        if (controller != null && controller.pool() != type.waterLoop())
            throw new IllegalArgumentException("System '" + target.label() + "' is controlled by a "
                    + (controller.pool() ? "pool thermostat, which needs a boiler or pool heat pump"
                                         : "room thermostat, which cannot use water-loop equipment") + ".");
        if (registry.hasVentOfMedium(target, !type.waterLoop()))
            throw new IllegalArgumentException(type.displayName() + " is a "
                    + (type.waterLoop() ? "water" : "air") + " source, but system '"
                    + target.label() + "' distributes through "
                    + (type.waterLoop() ? "iron trapdoors (air)" : "waxed copper grates (water)")
                    + ".");
        EquipmentUnit unit = new EquipmentUnit(position, args[2], type);
        registry.addEquipment(unit);
        player.sendMessage(green("Registered " + type.displayName() + " in group '"
                + unit.group().label() + "'."));
    }

    private void thermostat(Player player, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException(
                "Usage: /hvac thermostat <id> <set|enable|disable|group|info> ...");
        Thermostat thermostat = registry.thermostatById(args[1]);
        if (thermostat == null) throw new IllegalArgumentException("No thermostat named '" + args[1] + "'.");
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "set", "settemp" -> {
                if (args.length < 4) throw new IllegalArgumentException("Usage: /hvac thermostat <id> set <temperatureF>");
                double previous = thermostat.targetF();
                thermostat.setTargetF(settings.units().parse(args[3]), settings);
                registry.markDirty();
                ApiEvents.targetChanged(thermostat, previous, ThermostatTargetChangeEvent.Cause.COMMAND, controller);
                player.sendMessage(green("Target set to " + temp(thermostat.targetF()) + "."));
            }
            case "enable" -> { thermostat.setEnabled(true); registry.markDirty(); player.sendMessage(green("Thermostat enabled.")); }
            case "disable" -> { thermostat.setEnabled(false); registry.markDirty(); player.sendMessage(yellow("Thermostat disabled.")); }
            case "group", "setgroup", "sethvac" -> {
                if (args.length < 4) throw new IllegalArgumentException("Usage: /hvac thermostat <id> group <group>");
                GroupId replacement = GroupId.of(thermostat.position(), args[3]);
                Thermostat existing = registry.thermostatFor(replacement);
                if (existing != null && existing != thermostat)
                    throw new IllegalArgumentException("That group already has thermostat '" + existing.id() + "'.");
                GroupId previous = thermostat.group();
                registry.regroupThermostat(thermostat, args[3]);
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
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "enable" -> { unit.setEnabled(true); player.sendMessage(green("Equipment enabled.")); }
            case "disable" -> { unit.setEnabled(false); unit.stop(); player.sendMessage(yellow("Equipment disabled.")); }
            case "group" -> {
                if (args.length < 3) throw new IllegalArgumentException("Usage: /hvac equipment group <group>");
                unit.stop();
                if (unit.hasOwnedOutput())
                    throw new IllegalArgumentException("The old output chunk must load before this unit can change groups.");
                registry.regroupEquipment(unit, args[2]);
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
        AirflowService.TemperatureReading reading =
                airflow.temperatureForBody(player.getLocation(), player.getEyeLocation());
        boolean water = reading.water();
        player.sendMessage(join(gold("Temperature"), gray(water ? " | Water: " : " | Here: "),
                white(temp(reading.localF())),
                gray(water ? " | Air: " : " | Outside: "),
                white(temp(reading.outdoorF())
                        + (reading.weather().isEmpty() ? "" : " " + reading.weather()))));
        if (!water)
            player.sendMessage(join(gray("Feels like: "),
                    white(temp(reading.feelsLikeF())),
                    gray(" | Humidity: "), white(Math.round(reading.humidity()) + "%")));
        UUID world = player.getWorld().getUID();
        if (plugin.seasons().settings().enabled() && plugin.seasons().tracks(world))
            player.sendMessage(join(gray("Season: "), white(seasonText(world)),
                    gray(" | Outside "), white(shift(outdoor.seasonShiftAt(player.getLocation())))));
        if (!reading.conditioned()) {
            player.sendMessage(gray(water ? "Natural water: no pool system reaches this block."
                    : "No completed linked-vent path reaches this block."));
        } else {
            String zone = reading.airflowDistance() <= settings.airflowCoreRange()
                    ? "core" : Math.round(reading.influence() * 100) + (water ? "% mixing" : "% leakage");
            player.sendMessage(join(gray("Group: "), white(reading.group().label()), gray(" | Path: "),
                    white(reading.airflowDistance() + " blocks"), gray(" | Zone: "), white(zone)));
        }
    }

    /**
     * {@code /hvac season [set <season> [day] | next | pause | resume] [world]}:
     * anyone may look, admins may change the calendar. From the console the
     * world can be named last; it defaults to the main world.
     */
    private void season(CommandSender sender, String[] args) {
        SeasonService seasons = plugin.seasons();
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        World world = sender instanceof Player player ? player.getWorld() : plugin.getServer().getWorlds().get(0);
        if (!rest.isEmpty()) {
            World named = plugin.getServer().getWorld(rest.get(rest.size() - 1));
            if (named != null) {
                world = named;
                rest.remove(rest.size() - 1);
            }
        }
        if (!seasons.settings().enabled()) {
            sender.sendMessage(yellow("Seasons are turned off on this server (seasons.enabled in config.yml)."));
            return;
        }
        if (world.getEnvironment() != World.Environment.NORMAL || !seasons.tracks(world.getUID())) {
            sender.sendMessage(yellow("'" + world.getName() + "' has no seasons: only worlds with a sun do."));
            return;
        }
        UUID id = world.getUID();
        String action = rest.isEmpty() ? "info" : rest.get(0).toLowerCase(Locale.ROOT);
        int days = seasons.settings().daysPerSeason();
        switch (action) {
            case "info" -> { }
            case "set" -> {
                if (!requireAdmin(sender)) return;
                if (rest.size() < 2) throw new IllegalArgumentException("Usage: /hvac season set <spring|summer|autumn|winter> [day]");
                Seasons.Season season = Seasons.Season.parse(rest.get(1));
                if (season == null) throw new IllegalArgumentException("'" + rest.get(1) + "' is not a season: spring, summer, autumn or winter.");
                int day = 1;
                if (rest.size() >= 3) {
                    try {
                        day = Integer.parseInt(rest.get(2));
                    } catch (NumberFormatException notNumber) {
                        throw new IllegalArgumentException("The day must be a number from 1 to " + days + ".");
                    }
                    if (day < 1 || day > days) throw new IllegalArgumentException("The day must be 1 to " + days + ".");
                }
                seasons.set(id, season, day);
            }
            case "next" -> {
                if (!requireAdmin(sender)) return;
                seasons.next(id);
            }
            case "pause", "resume" -> {
                if (!requireAdmin(sender)) return;
                seasons.setPaused(id, action.equals("pause"));
                seasons.save();
                sender.sendMessage(action.equals("pause")
                        ? yellow("The calendar in '" + world.getName() + "' is paused: the days pass, the season stays.")
                        : green("The calendar in '" + world.getName() + "' runs again."));
                return;
            }
            default -> throw new IllegalArgumentException("Usage: /hvac season [set <season> [day] | next | pause | resume]");
        }
        if (!action.equals("info")) {
            seasons.save();
            sender.sendMessage(green("It is now " + seasonText(id) + " in '" + world.getName()
                    + "'. Outdoor temperatures follow at once; rooms drift there over time."));
            return;
        }
        Component line = join(gold("Season"), gray(" | "), white(seasonText(id)));
        if (sender instanceof Player player && player.getWorld().equals(world))
            line = line.append(join(gray(" | Here: "), white(shift(outdoor.seasonShiftAt(player.getLocation())))));
        if (seasons.paused(id)) line = line.append(yellow(" (paused)"));
        sender.sendMessage(line);
    }

    /** "Summer, day 3 of 15". */
    private String seasonText(UUID world) {
        SeasonService seasons = plugin.seasons();
        return seasons.season(world).displayName() + ", day " + seasons.dayOfSeason(world) + " of "
                + seasons.settings().daysPerSeason();
    }

    /** A temperature change in the server's unit: "+12.4 F", "-3.1 C". */
    private String shift(double fahrenheit) {
        double value = settings.units() == TemperatureUnit.C ? fahrenheit * 5.0 / 9.0 : fahrenheit;
        return String.format(Locale.US, "%+.1f %s", value, settings.units().name());
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
                    ? temp(status.roomF()) + " / " + temp(status.targetF()) : "no thermostat";
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
            String humidity = Double.isFinite(reading.humidity()) ? Math.round(reading.humidity()) + "%" : "--%";
            String local = switch (thermometer.reads()) {
                case TEMPERATURE -> temp(reading.localF());
                case HUMIDITY -> humidity;
                case BOTH -> temp(reading.localF()) + ", " + humidity + ", feels " + temp(reading.feelsLikeF());
            };
            player.sendMessage(join(gold(thermometer.reads().displayName()), gray(" | Local: "), white(local),
                    gray(" | Source: "), white(source)));
            return;
        }
        RpmMonitor monitor = registry.rpmMonitorAt(key);
        if (monitor != null) { player.sendMessage(join(gold("RPM monitor"), gray(" | Group: "),
                white(monitor.group().label()), gray(" | Type: "), white(monitor.equipmentType().displayName()))); return; }
        SettingsPanel panel = registry.settingsPanelAt(key);
        if (panel != null) { player.sendMessage(join(gold("Settings panel"), gray(" | Group: "),
                white(panel.group().label()), gray(" | Profile: "), white(panel.profile().name()))); return; }
        ClimateUnit climateUnit = registry.climateUnitAt(key);
        if (climateUnit != null) { showClimateUnit(player, climateUnit); return; }
        Sensor sensor = registry.sensorAt(key);
        if (sensor != null) {
            player.sendMessage(describeSensor(sensor));
            int hubs = registry.hubsShowing(key).size();
            player.sendMessage(gray("Shown on " + hubs + " sensor hub" + (hubs == 1 ? "" : "s") + "."));
            return;
        }
        SensorHub hub = registry.sensorHubAt(key);
        if (hub != null) {
            player.sendMessage(join(gold("Sensor hub "), white(hub.label()), gray(" | " + hub.sensors().size() + "/"
                    + SensorHub.MAX_SENSORS + " sensors | auto-flip " + (hub.autoFlip() ? "on" : "off"))));
            for (BlockKey linked : hub.sensors()) {
                Sensor shown = registry.sensorAt(linked);
                if (shown != null) player.sendMessage(describeSensor(shown));
            }
            return;
        }
        player.sendMessage(red("No EZvac device is registered at that block."));
    }

    private void remove(Player player) {
        Block block = target(player);
        if (block == null) return;
        BlockKey key = BlockKey.of(block.getLocation());
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
            else if (registry.thermometerAt(key) != null) {
                String name = registry.thermometerAt(key).reads().displayName();
                registry.removeThermometer(key);
                player.sendMessage(green(name + " removed."));
            }
            else if (registry.removeRpmMonitor(key)) player.sendMessage(green("RPM monitor removed."));
            else if (registry.removeSettingsPanel(key))
                player.sendMessage(green("Settings panel removed; group reverted to NORMAL."));
            else if (registry.climateUnitAt(key) != null) {
                ClimateUnit removed = registry.removeClimateUnit(key);
                units.release(removed);
                displays.invalidate(removed.position());
                player.sendMessage(green(removed.kind().displayName() + " removed."));
            }
            else if (registry.removeSensor(key) != null)
                player.sendMessage(green("Sensor removed, and taken off every sensor hub that showed it."));
            else if (registry.removeSensorHub(key) != null) player.sendMessage(green("Sensor hub removed."));
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

    /**
     * A sensor's reading for chat: everything its button measures, and the
     * system whose air it is in. Never loads the sensor's chunk.
     */
    public Component describeSensor(Sensor sensor) {
        BlockKey position = sensor.position();
        var location = position.isChunkLoaded() ? position.location() : null;
        if (location == null)
            return join(gold(sensor.label()), gray(" | not loaded right now"));
        if (sensor.reads() == Sensor.Kind.WEATHER) {
            String weather = displays.weatherAt(location);
            return join(gold(sensor.label()), gray(" | "), white(weather), gray(weather.equals(DisplayService.COVERED)
                    ? " | it needs open sky above it" : " | outside"));
        }
        AirflowService.TemperatureReading reading = airflow.temperatureAt(location);
        String humidity = Double.isFinite(reading.humidity()) ? Math.round(reading.humidity()) + "%" : "--%";
        String values = switch (sensor.reads()) {
            case TEMPERATURE -> temp(reading.localF());
            case HUMIDITY -> humidity;
            case BOTH -> temp(reading.localF()) + " | " + humidity + " | feels " + temp(reading.feelsLikeF());
            case WEATHER -> throw new AssertionError("handled above");
        };
        String where = reading.conditioned() ? "system '" + reading.group().label() + "'" : "no system";
        return join(gold(sensor.label()), gray(" | "), white(values), gray(" | " + where));
    }

    private void giveSensorTool(Player player) {
        ItemStack tool = new ItemStack(Material.STICK);
        ItemMeta meta = tool.getItemMeta();
        meta.displayName(Component.text("HVAC Sensor Link Tool", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                Component.text("Select a sensor hub, then sensors.", NamedTextColor.GRAY),
                Component.text("Sneak-click a sensor to unlink it.", NamedTextColor.GRAY)));
        // A glint, so it can't be mistaken for the duct tool at a glance.
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(sensorToolKey, PersistentDataType.BYTE, (byte) 1);
        tool.setItemMeta(meta);
        player.getInventory().addItem(tool);
        player.sendMessage(green("Sensor link tool added to your inventory."));
    }

    public boolean isSensorTool(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(sensorToolKey, PersistentDataType.BYTE);
    }

    /** Either linking tool: clicks with one belong to the tool, not to the block clicked. */
    public boolean isTool(ItemStack item) {
        return isDuctTool(item) || isSensorTool(item);
    }

    public void handleSensorToolClick(Player player, Block block, ItemStack item, boolean unlink) {
        if (!isSensorTool(item) || !player.hasPermission(ADMIN_PERMISSION)) return;
        BlockKey position = BlockKey.of(block.getLocation());
        SensorHub selected = registry.sensorHubAt(position);
        if (selected != null) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(sensorToolHubKey, PersistentDataType.STRING,
                    position.worldId() + ";" + position.x() + ";" + position.y() + ";" + position.z());
            meta.lore(List.of(Component.text("Selected: " + selected.label(), NamedTextColor.GRAY),
                    Component.text("Click sensors to link them.", NamedTextColor.GRAY)));
            item.setItemMeta(meta);
            player.sendMessage(green("Selected sensor hub '" + selected.label() + "' (" + selected.sensors().size()
                    + "/" + SensorHub.MAX_SENSORS + " sensors)."));
            return;
        }
        Sensor sensor = registry.sensorAt(position);
        if (sensor == null) {
            player.sendMessage(yellow("Click a sensor hub, then sensors: stone, polished blackstone, mangrove or"
                    + " birch buttons registered with /hvac create sensor."));
            return;
        }
        String stored = item.getItemMeta().getPersistentDataContainer().get(sensorToolHubKey, PersistentDataType.STRING);
        SensorHub hub = stored == null ? null : registry.sensorHubAt(parseToolKey(stored));
        if (hub == null) {
            player.sendMessage(red(stored == null ? "Select a sensor hub with this tool first."
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

    private static BlockKey parseToolKey(String stored) {
        try {
            String[] parts = stored.split(";");
            return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    public boolean isDuctTool(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    public void handleDuctToolClick(Player player, Block block, ItemStack item, boolean unlink) {
        if (!isDuctTool(item) || !player.hasPermission(ADMIN_PERMISSION)) return;
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
        if (registry.climateUnitAt(position) != null) {
            player.sendMessage(yellow("That grate is part of a dehumidifier, not a vent."));
            return;
        }
        if (unlink) {
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
        boolean water = ClimateVent.isWaterVent(block.getType());
        if (registry.hasEquipmentOfLoop(group, !water)) {
            player.sendMessage(red("System '" + group.label() + "' is powered by "
                    + (water ? "air-loop equipment, which cannot condition water"
                             : "water-loop equipment, which cannot condition air") + "."));
            return;
        }
        if (registry.hasVentOfMedium(group, !water)) {
            player.sendMessage(red("System '" + group.label() + "' already uses "
                    + (water ? "iron trapdoors (air)" : "waxed copper grates (water)")
                    + ". A system must be all air or all water; unlink its existing vents first."));
            return;
        }
        ClimateVent previous = registry.ventAt(position);
        registry.addVent(new ClimateVent(position, group));
        if (previous != null) airflow.invalidate(previous.group());
        airflow.invalidate(group);
        player.sendMessage(green((water ? "Water vent" : "Vent") + " linked to group '"
                + group.label() + "'."));
    }

    private void showThermostat(Player player, Thermostat thermostat) {
        player.sendMessage(join(gold("Thermostat "), white(thermostat.id()), gray(" | Group: "),
                white(thermostat.group().label())));
        player.sendMessage(join(gray("Room/target: "), white(temp(thermostat.roomF()) + " / "
                + temp(thermostat.targetF())), gray(" | Mode: "), white(thermostat.mode().name()),
                gray(" | Profile: "), white(registry.profileFor(thermostat.group()).name()),
                gray(" | "), white(thermostat.enabled() ? "enabled" : "disabled")));
        if (!thermostat.pool() && thermostat.hasRoomDewPoint()) {
            double humidity = Psychrometrics.relativeHumidity(thermostat.roomF(), thermostat.roomDewPointF());
            player.sendMessage(join(gray("Humidity: "), white(Math.round(humidity) + "%"),
                    gray(" | Feels like: "), white(temp(
                            Psychrometrics.apparentTemperatureF(thermostat.roomF(), humidity, 0.0)))));
        }
    }

    private void showClimateUnit(Player player, ClimateUnit unit) {
        String space = unit.servedGroup() == null ? "its own zone"
                : "system '" + unit.servedGroup().label() + "'";
        double humidity = unit.lastHumidity();
        player.sendMessage(join(gold(unit.kind().displayName() + " "), white(unit.label()),
                gray(unit instanceof Dehumidifier ? " | Drying: " : " | Humidifying: "), white(space)));
        player.sendMessage(join(gray("Humidity: "), white(Double.isFinite(humidity) ? Math.round(humidity) + "%" : "unknown"),
                gray(" | Setpoint: "), white(unit.setpoint() + "%"),
                gray(" | State: "), white(unit.state().name()),
                unit instanceof Dehumidifier dehumidifier
                        ? join(gray(" | Tank: "), white(Math.round(dehumidifier.tank() * 100) + "%"))
                        : gray(" | Plumbed")));
        if (unit instanceof Dehumidifier dehumidifier && dehumidifier.tooCold()) {
            Thermostat thermostat = unit.servedGroup() == null ? null : registry.thermostatFor(unit.servedGroup());
            double dewPointF = thermostat == null ? unit.zoneDewPointF() : thermostat.roomDewPointF();
            player.sendMessage(gold("Air too cold: its dew point (" + settings.units().format(dewPointF, 0)
                    + ") is at or below what the coil can remove ("
                    + settings.units().format(units.climate().dehumidifier().dryDewPointF(), 0)
                    + "), so it can't dry this air."));
        }
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


    private static EquipmentType parseEquipmentType(String text) {
        return switch (text.toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "heatpump", "heat_pump" -> EquipmentType.HEAT_PUMP;
            case "ac", "airconditioner", "air_conditioner" -> EquipmentType.AIR_CONDITIONER;
            case "furnace" -> EquipmentType.FURNACE;
            case "boiler" -> EquipmentType.BOILER;
            case "poolheatpump", "pool_heat_pump", "poolhp" -> EquipmentType.POOL_HEAT_PUMP;
            default -> throw new IllegalArgumentException(
                    "equipment type must be heatpump, ac, furnace, boiler, or poolheatpump");
        };
    }

    /** A temperature as players read it: {@code 72.4 F}, or {@code 22.4 C} with units: C. */
    private String temp(double fahrenheit) {
        return settings.units().format(fahrenheit);
    }

    /** A target range: whole degrees in F, tenths in C, where whole degrees would round the limits away. */
    private String range(double lowF, double highF) {
        TemperatureUnit unit = settings.units();
        int decimals = unit == TemperatureUnit.F ? 0 : 1;
        return String.format(Locale.US, "%." + decimals + "f", unit.fromF(lowF)) + "-" + unit.format(highF, decimals);
    }

    private static boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) return true;
        sender.sendMessage(red("You need " + ADMIN_PERMISSION + "."));
        return false;
    }

    private static void help(CommandSender sender) {
        sender.sendMessage(gold("EZvac commands"));
        sender.sendMessage(join(aqua("/hvac temperature"), gray(" - read local/outdoor temperature")));
        if (sender.hasPermission(HUD_PERMISSION))
            sender.sendMessage(join(aqua("/hvac hud [on|off]"), gray(" - show or hide the on-screen climate HUD")));
        sender.sendMessage(join(aqua("/hvac list | info"), gray(" - inspect systems or the targeted device")));
        sender.sendMessage(join(aqua("/hvac season"), gray(" - the season and how much it warms or cools outside")));
        if (!sender.hasPermission(ADMIN_PERMISSION)) return;
        sender.sendMessage(join(aqua("/hvac season set <season> [day] | next | pause | resume"),
                gray(" - change the season")));
        sender.sendMessage(join(aqua("/hvac create thermostat <id> <group> [target]"), gray(" - register a sign")));
        sender.sendMessage(join(aqua("/hvac create heatpump|ac|furnace <group>"), gray(" - register a dispenser")));
        sender.sendMessage(join(aqua("/hvac create boiler|poolheatpump <group>"), gray(" - register a pool source")));
        sender.sendMessage(join(aqua("/hvac create thermometer|hygrometer|thermohygrometer [label]"),
                gray(" - register a point-sensor sign")));
        sender.sendMessage(join(aqua("/hvac create rpmmonitor <group> <type> [label]"), gray(" - register a sign")));
        sender.sendMessage(join(aqua("/hvac create settingspanel <group> [label]"), gray(" - register profile controls")));
        sender.sendMessage(join(aqua("/hvac create dehumidifier [label]"), gray(" - sign on an iron block on a grate")));
        sender.sendMessage(join(aqua("/hvac create humidifier [label]"), gray(" - sign on an iron block under a copper slab")));
        sender.sendMessage(join(aqua("/hvac thermostat <id> set|enable|disable|group"), gray(" - control a thermostat")));
        sender.sendMessage(join(aqua("/hvac equipment <enable|disable|group|info>"), gray(" - edit targeted equipment")));
        sender.sendMessage(join(aqua("/hvac group <group> <enable|disable|info>"), gray(" - control a system")));
        sender.sendMessage(join(aqua("/hvac create sensor [label]"), gray(" - a stone, polished blackstone, mangrove or birch button")));
        sender.sendMessage(join(aqua("/hvac create sensor-hub [label]"), gray(" - a sign showing up to 9 sensors")));
        sender.sendMessage(join(aqua("/hvac duct-tool | sensor-tool"), gray(" - link vents, or link sensors to a hub")));
        sender.sendMessage(join(aqua("/hvac remove | stats | sync"), gray(" - remove, diagnose, reconcile")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(args[0], "help", "temperature", "hud", "list", "info",
                "create", "thermostat", "equipment", "group", "duct-tool", "sensor-tool", "remove", "stats", "sync",
                "season");
        if (args[0].equalsIgnoreCase("season")) {
            if (args.length == 2) return sender.hasPermission(ADMIN_PERMISSION)
                    ? filter(args[1], "set", "next", "pause", "resume") : List.of();
            if (args.length == 3 && args[1].equalsIgnoreCase("set"))
                return filter(args[2], "spring", "summer", "autumn", "winter");
            return filter(args[args.length - 1], plugin.getServer().getWorlds().stream()
                    .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                    .map(World::getName).toArray(String[]::new));
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> filter(args[1], "thermostat", "poolthermostat", "heatpump",
                        "ac", "furnace", "boiler", "poolheatpump", "thermometer", "hygrometer",
                        "thermohygrometer", "rpmmonitor", "settingspanel", "dehumidifier", "humidifier",
                        "sensor", "sensor-hub");
                case "thermostat" -> filter(args[1], registry.thermostats().stream().map(Thermostat::id).toArray(String[]::new));
                case "equipment" -> filter(args[1], "enable", "disable", "group", "info");
                case "hud" -> filter(args[1], "on", "off");
                case "group" -> filter(args[1], registry.thermostats().stream().map(value -> value.group().label()).distinct().toArray(String[]::new));
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("thermostat"))
            return filter(args[2], "set", "enable", "disable", "group", "info");
        if (args.length == 3 && args[0].equalsIgnoreCase("group"))
            return filter(args[2], "enable", "disable", "info");
        if (args.length == 4 && args[0].equalsIgnoreCase("create") && args[1].equalsIgnoreCase("rpmmonitor"))
            return filter(args[3], "heatpump", "ac", "furnace", "boiler", "poolheatpump");
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
