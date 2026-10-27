package com.github.blade.hvac.command;

import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.simulation.WindModel;
import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.api.event.ThermostatTargetChangeEvent;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.hud.ClimateHudService;
import com.github.blade.hvac.integration.api.ApiEvents;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.service.*;
import com.github.blade.hvac.simulation.AirQualityModel;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.Seasons;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
    private final LinkTool linkTool;
    /** "/hvac remove system" shows what it would remove; the confirm must follow within this long. */
    private static final long CONFIRM_MILLIS = 15_000L;
    private final Map<UUID, PendingRemoval> pendingRemovals = new HashMap<>();

    private record PendingRemoval(SystemId system, long expiresAt) {}

    /** Said when a pre-3.0 command names a group. */
    private static final String GROUPS_GONE = "Groups are gone since EZvac 3.0, so the group name was ignored."
            + " Link devices to a thermostat with /hvac link-tool.";
    private static final String LINK_HINT = "Link it with /hvac link-tool: right-click the thermostat, then it.";

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
        this.linkTool = new LinkTool(plugin, registry, airflow, displays, settings);
    }

    public LinkTool linkTool() { return linkTool; }

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
        if (subcommand.equals("wind")) {
            try {
                wind(sender, args);
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
                case "rename" -> { if (requireAdmin(player)) rename(player, args); }
                case "group" -> { if (requireAdmin(player)) player.sendMessage(yellow("Groups are gone since EZvac"
                        + " 3.0. Link devices to a thermostat with /hvac link-tool; switch a system on or off by"
                        + " sneak-clicking its thermostat, and /hvac info on any part of it shows the system.")); }
                // The duct and sensor tools became the link tool in 3.0; their names still give it.
                case "link-tool", "linktool", "duct-tool", "ducttool", "tool", "sensor-tool", "sensortool" ->
                        { if (requireAdmin(player)) linkTool.give(player); }
                case "remove" -> { if (requireAdmin(player)) remove(player, args); }
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
        if (args.length >= 2 && args[1].equalsIgnoreCase("sections")) {
            hudSections(player);
            return;
        }
        boolean show;
        if (args.length < 2) show = !hud.isShowing(player);
        else if (args[1].equalsIgnoreCase("on")) show = true;
        else if (args[1].equalsIgnoreCase("off")) show = false;
        else throw new IllegalArgumentException("Usage: /hvac hud [on|off|sections]");
        hud.setShowing(player, show);
        player.sendMessage(show ? green("Climate HUD on. Use /hvac hud again to hide it.")
                : gray("Climate HUD off."));
    }

    /** The addon sections on the HUD (API 1.1), for admins placing them in config.yml. */
    private void hudSections(Player player) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(red("You need ezvac.admin to list the HUD's addon sections."));
            return;
        }
        List<String> ids = hud.sectionIds();
        if (ids.isEmpty()) {
            player.sendMessage(gray("No addon has added a section to the HUD."));
            return;
        }
        List<String> hidden = hud.format().settings().hiddenSections();
        player.sendMessage(aqua("HUD sections from addons:"));
        for (String id : ids)
            player.sendMessage(join(gray(" - "), aqua(id), gray(hidden.contains(id) ? " (hidden in hud.sections.hidden)"
                    : "  {section:" + id + "}")));
    }

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(red("Usage: /hvac create <thermostat|poolthermostat|heatpump|ac"
                    + "|furnace|boiler|poolheatpump|thermometer|hygrometer|thermohygrometer|rpmmonitor"
                    + "|settingspanel|dehumidifier|humidifier|purifier|airmonitor> ..."));
            return;
        }
        Block block = target(player);
        if (block == null) return;
        BlockKey position = BlockKey.of(block.getLocation());
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "thermostat", "poolthermostat", "pool" -> {
                boolean pool = !args[1].equalsIgnoreCase("thermostat");
                if (args.length < 3) throw new IllegalArgumentException(
                        "Usage: /hvac create " + args[1] + " <label> [target]");
                requireSign(block);
                // Before 3.0 the word after the label was the group: create thermostat main house 72.
                int targetArg = 3;
                if (args.length >= 4 && !isTemperature(args[3])) {
                    targetArg = 4;
                    player.sendMessage(yellow(GROUPS_GONE));
                }
                double target = args.length > targetArg ? settings.units().parse(args[targetArg])
                        : (pool ? Thermostat.POOL_DEFAULT_F : Thermostat.ROOM_DEFAULT_F);
                Thermostat thermostat = new Thermostat(position, args[2], registry.newSystem(position.worldId()),
                        target, outdoor.temperatureAt(block.getLocation()), settings, pool);
                registry.addThermostat(thermostat);
                displays.refreshThermostat(thermostat, true);
                player.sendMessage(green("Created " + (pool ? "pool thermostat" : "thermostat") + " '"
                        + thermostat.id() + "'. Link equipment, vents and signs to it with /hvac link-tool."));
            }
            case "heatpump", "heat_pump" -> createEquipment(player, block, args, EquipmentType.HEAT_PUMP);
            case "ac", "airconditioner", "air_conditioner" ->
                    createEquipment(player, block, args, EquipmentType.AIR_CONDITIONER);
            case "furnace" -> createEquipment(player, block, args, EquipmentType.FURNACE);
            case "boiler" -> createEquipment(player, block, args, EquipmentType.BOILER);
            case "poolheatpump", "pool_heat_pump", "poolhp" ->
                    createEquipment(player, block, args, EquipmentType.POOL_HEAT_PUMP);
            case "thermometer", "hygrometer", "thermohygrometer", "thermohygro", "thermo-hygrometer",
                 "airmonitor", "air-monitor", "aqi" -> {
                requireSign(block);
                Thermometer.Reads reads = switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "thermometer" -> Thermometer.Reads.TEMPERATURE;
                    case "hygrometer" -> Thermometer.Reads.HUMIDITY;
                    case "airmonitor", "air-monitor", "aqi" -> Thermometer.Reads.AIR;
                    default -> Thermometer.Reads.BOTH;
                };
                // Labels have no spaces or dashes: Thermohygrometer, Airmonitor.
                String displayLabel = args.length >= 3 ? args[2]
                        : reads.displayName().replace("-", "").replace(" ", "");
                registry.addThermometer(new Thermometer(position, displayLabel, reads));
                displays.refreshAll(true);
                player.sendMessage(green("Created independent point " + reads.displayName().toLowerCase(Locale.ROOT) + "."));
            }
            case "rpmmonitor", "rpm" -> {
                requireSign(block);
                // Before 3.0: create rpmmonitor <group> <kind> [label]. A monitor now shows its whole system.
                String displayLabel = args.length >= 3 ? args[2] : "RPM";
                if (args.length >= 4 && isEquipmentType(args[3])) {
                    displayLabel = args.length >= 5 ? args[4] : args[2];
                    player.sendMessage(yellow(GROUPS_GONE));
                }
                RpmMonitor monitor = new RpmMonitor(position, displayLabel);
                registry.addRpmMonitor(monitor);
                player.sendMessage(green("Created RPM monitor '" + monitor.label() + "'."));
                if (!linkTool.linkCreated(player, block)) player.sendMessage(gray(LINK_HINT));
                displays.refreshRpmMonitor(monitor, true);
            }
            case "settingspanel", "settings", "panel" -> {
                requireSign(block);
                // Before 3.0: create settingspanel <group> [label]. The label is the last word.
                String displayLabel = args.length >= 3 ? args[args.length - 1] : "Settings";
                if (args.length >= 4) player.sendMessage(yellow(GROUPS_GONE));
                SettingsPanel panel = new SettingsPanel(position, displayLabel);
                registry.addSettingsPanel(panel);
                player.sendMessage(green("Created settings panel '" + panel.label() + "' in NORMAL mode."));
                if (!linkTool.linkCreated(player, block)) player.sendMessage(gray(LINK_HINT));
                displays.refreshSettingsPanel(panel, true);
            }
            case "dehumidifier", "dehum" -> createDehumidifier(player, block, position, args);
            case "humidifier", "hum" -> createHumidifier(player, block, position, args);
            case "purifier", "airpurifier", "air-purifier" -> createPurifier(player, block, position, args);
            case "sensor" -> {
                Sensor.Kind reads = Sensor.forButton(block.getType());
                if (reads == null)
                    throw new IllegalArgumentException("Look at a stone button (temperature), a polished blackstone"
                            + " button (humidity), a mangrove button (both), a birch button (weather), a cherry"
                            + " button (air quality) or a bamboo button (wind).");
                Sensor sensor = new Sensor(position, args.length >= 3 ? args[2] : "Sensor", reads);
                registry.addSensor(sensor);
                player.sendMessage(green("Created " + reads.description() + " sensor '" + sensor.label()
                        + "'. Link it to a sensor hub with /hvac link-tool."
                        + (reads == Sensor.Kind.WEATHER ? " It needs open sky above it, like a rain gauge." : "")
                        + (reads == Sensor.Kind.WIND ? " Put it up high in the open: walls and roofs shelter it."
                        : "")));
            }
            case "sensor-hub", "sensorhub" -> {
                requireSign(block);
                SensorHub hub = new SensorHub(position, args.length >= 3 ? args[2] : "Sensors");
                registry.addSensorHub(hub);
                displays.refreshSensorHub(hub, true);
                player.sendMessage(green("Created sensor hub '" + hub.label() + "'. Link up to "
                        + SensorHub.MAX_SENSORS + " sensors to it with /hvac link-tool."));
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
                    + " (sneak-click it with /hvac link-tool).");
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

    /**
     * An air purifier (3.2) is built like a dehumidifier: an iron block, with
     * the sign on it, standing on a tube coral block, which is its filter.
     */
    private void createPurifier(Player player, Block sign, BlockKey position, String[] args) {
        requireSign(sign);
        Block iron = sign.getBlockData() instanceof WallSign wall
                ? sign.getRelative(wall.getFacing().getOppositeFace()) : sign.getRelative(BlockFace.DOWN);
        if (iron.getType() != Material.IRON_BLOCK)
            throw new IllegalArgumentException("Put the sign on an iron block: on its side, or standing on top.");
        Block filter = iron.getRelative(BlockFace.DOWN);
        if (filter.getType() != AirPurifier.FILTER)
            throw new IllegalArgumentException("The iron block must sit on a tube coral block: the filter.");
        String label = args.length >= 3 ? args[2] : "Purifier";
        registerUnit(player, new AirPurifier(position, BlockKey.of(iron.getLocation()),
                BlockKey.of(filter.getLocation()), label, AirPurifier.Mode.AUTO));
        if (!units.air().enabled())
            player.sendMessage(yellow("Air quality is switched off on this server (air-quality.enabled), so it"
                    + " will stay idle."));
    }

    private void registerUnit(Player player, ClimateUnit unit) {
        registry.addClimateUnit(unit);
        airflow.invalidate(ZoneId.of(unit));
        displays.refreshClimateUnit(unit, true);
        if (unit instanceof AirPurifier) {
            player.sendMessage(green("Created air purifier '" + unit.label() + "' in Auto. Click the sign to pick"
                    + " a fan speed, sneak-click to switch it on or off."));
            return;
        }
        player.sendMessage(green("Created " + unit.kind().displayName().toLowerCase(Locale.ROOT) + " '"
                + unit.label() + "' at " + unit.setpoint() + "%. Click the sign to change the setpoint,"
                + " sneak-click to switch it on or off."));
    }

    private void createEquipment(Player player, Block block, String[] args, EquipmentType type) {
        if (block.getType() != Material.DISPENSER)
            throw new IllegalArgumentException("Look directly at a dispenser.");
        // The word after the kind is a tier (3.1); before 3.0 it was a group, now ignored.
        // New units are two-stage unless told (3.1); every unit from before then kept what it was.
        Tier tier = args.length >= 3 ? Tier.parse(args[2]) : Tier.TWO_STAGE;
        if (tier == null) {
            player.sendMessage(yellow(GROUPS_GONE));
            tier = Tier.TWO_STAGE;
        }
        BlockKey position = BlockKey.of(block.getLocation());
        // A unit starts in a headless system of its own, until it is linked to a thermostat.
        EquipmentUnit unit = new EquipmentUnit(position, registry.newSystem(position.worldId()), type);
        unit.setTier(tier);
        registry.addEquipment(unit);
        player.sendMessage(green("Registered " + type.displayName() + " " + tierName(tier, type) + "."));
        if (!linkTool.linkCreated(player, block)) player.sendMessage(gray(LINK_HINT));
    }

    private static final List<String> THERMOSTAT_ACTIONS = List.of("set", "settemp", "enable", "disable", "info");

    /** {@code /hvac thermostat <set|enable|disable|info>}, looking at the thermostat. Labels may repeat. */
    private void thermostat(Player player, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException(
                "Usage: look at a thermostat, then /hvac thermostat <set <temperature>|enable|disable|info>");
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!THERMOSTAT_ACTIONS.contains(action)) {
            // Before 3.0: /hvac thermostat <id> <action>. Labels may repeat now, so a name finds nothing.
            String rest = String.join(" ", Arrays.asList(args).subList(Math.min(2, args.length), args.length));
            throw new IllegalArgumentException("Thermostats aren't found by name any more, since two can share"
                    + " one. Look at the thermostat and run /hvac thermostat " + (rest.isEmpty() ? "info" : rest) + ".");
        }
        Block block = target(player);
        if (block == null) return;
        Thermostat thermostat = registry.thermostatAt(BlockKey.of(block.getLocation()));
        if (thermostat == null) throw new IllegalArgumentException("Look directly at a thermostat sign.");
        switch (action) {
            case "set", "settemp" -> {
                if (args.length < 3) throw new IllegalArgumentException("Usage: /hvac thermostat set <temperature>");
                double previous = thermostat.targetF();
                thermostat.setTargetF(settings.units().parse(args[2]), settings);
                registry.markDirty();
                ApiEvents.targetChanged(thermostat, previous, ThermostatTargetChangeEvent.Cause.COMMAND, controller);
                player.sendMessage(green("Target set to " + temp(thermostat.targetF()) + "."));
            }
            case "enable" -> { thermostat.setEnabled(true); registry.markDirty(); player.sendMessage(green("Thermostat enabled.")); }
            case "disable" -> { thermostat.setEnabled(false); registry.markDirty(); player.sendMessage(yellow("Thermostat disabled.")); }
            default -> showThermostat(player, thermostat);
        }
        displays.refreshThermostat(thermostat, true);
        plugin.requestImmediateSync();
    }

    private void equipment(Player player, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException(
                "Usage: /hvac equipment <enable|disable|tier <t1|t2|t3>|info>");
        Block block = target(player);
        if (block == null) return;
        EquipmentUnit unit = registry.equipmentAt(BlockKey.of(block.getLocation()));
        if (unit == null) throw new IllegalArgumentException("That block is not registered HVAC equipment.");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "enable" -> { unit.setEnabled(true); player.sendMessage(green("Equipment enabled.")); }
            case "disable" -> { unit.setEnabled(false); unit.stop(); player.sendMessage(yellow("Equipment disabled.")); }
            case "tier" -> {
                Tier tier = args.length >= 3 ? Tier.parse(args[2]) : null;
                if (tier == null) throw new IllegalArgumentException("Usage: /hvac equipment tier <t1|t2|t3>"
                        + " (single-stage, two-stage or variable)");
                // Stopped first, so it starts again on its new tier's curve.
                unit.stop();
                unit.setTier(tier);
                player.sendMessage(green(unit.type().displayName() + " is now " + tierName(tier, unit.type()) + "."));
            }
            case "group" -> throw new IllegalArgumentException("Groups are gone since EZvac 3.0. Sneak-click the"
                    + " unit with /hvac link-tool to unlink it, then link it to another thermostat.");
            case "info" -> showEquipment(player, unit);
            default -> throw new IllegalArgumentException("Unknown equipment action '" + args[1] + "'.");
        }
        registry.markDirty();
        plugin.requestImmediateSync();
    }

    /** {@code /hvac rename <label>}: the sign looked at. Labels are only names, so they may repeat. */
    private void rename(Player player, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Usage: look at a sign, then /hvac rename <label>");
        Block block = target(player);
        if (block == null) return;
        BlockKey key = BlockKey.of(block.getLocation());
        String label = Thermostat.validateId(args[1]);
        Thermostat thermostat = registry.thermostatAt(key);
        RpmMonitor monitor = registry.rpmMonitorAt(key);
        SettingsPanel panel = registry.settingsPanelAt(key);
        SensorHub hub = registry.sensorHubAt(key);
        ClimateUnit unit = registry.climateUnitAt(key);
        if (thermostat != null) thermostat.setId(label);
        else if (monitor != null) monitor.setLabel(label);
        else if (panel != null) panel.setLabel(label);
        else if (hub != null) hub.setLabel(label);
        else if (unit != null && unit.position().equals(key)) unit.setLabel(label);
        else if (!registry.relabel(key, label))
            throw new IllegalArgumentException("Look at a thermostat, an RPM monitor, a settings panel, a sensor"
                    + " hub, a thermometer, a sensor, or a dehumidifier or humidifier sign.");
        registry.markDirty();
        displays.invalidate(key);
        displays.refreshAll(true);
        player.sendMessage(green("Renamed to '" + label + "'."));
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
        if (!water) {
            Component feels = join(gray("Feels like: "),
                    white(temp(reading.feelsLikeF())),
                    gray(" | Humidity: "), white(Math.round(reading.humidity()) + "%"));
            if (windShown(player.getWorld()))
                feels = feels.append(join(gray(" | Wind: "),
                        white(WindText.describe(reading.windMs(), outdoor.climate().wind(), settings.units()))));
            player.sendMessage(feels);
        }
        if (!water && units.air().enabled() && reading.air().known())
            player.sendMessage(join(gray("Air: "), airLine(reading.air())));
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
            player.sendMessage(join(gray("System: "), white(SystemNames.label(registry, reading.system())),
                    gray(" | Path: "),
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

    /** Whether a world has wind to show: wind is on, and the world has a sky (3.2). */
    private boolean windShown(World world) {
        return outdoor.wind() != null && outdoor.wind().enabled() && WindService.hasWind(world);
    }

    /** How many days {@code /hvac wind} looks ahead, today included. */
    private static final int FORECAST_DAYS = 3;

    /**
     * {@code /hvac wind [set <calm|breezy|windy|gale|auto>] [world]} (3.2):
     * anyone may look at today's wind and the next days', admins may set
     * today's. From the console the world can be named last; it defaults to
     * the main world.
     */
    private void wind(CommandSender sender, String[] args) {
        WindService wind = outdoor.wind();
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        World world = sender instanceof Player player ? player.getWorld() : plugin.getServer().getWorlds().get(0);
        if (!rest.isEmpty()) {
            World named = plugin.getServer().getWorld(rest.get(rest.size() - 1));
            if (named != null) {
                world = named;
                rest.remove(rest.size() - 1);
            }
        }
        if (wind == null || !wind.enabled()) {
            sender.sendMessage(yellow("Wind is turned off on this server (wind.enabled in config.yml)."));
            return;
        }
        if (!WindService.hasWind(world)) {
            sender.sendMessage(yellow("'" + world.getName() + "' has no wind: only worlds with a sky do."));
            return;
        }
        String action = rest.isEmpty() ? "info" : rest.get(0).toLowerCase(Locale.ROOT);
        WindSettings.Units units = WindText.units(wind.settings(), settings.units());
        switch (action) {
            case "info" -> { }
            case "set" -> {
                if (!requireAdmin(sender)) return;
                if (rest.size() < 2) throw new IllegalArgumentException("Usage: /hvac wind set <calm|breezy|windy|gale|auto>");
                if (rest.get(1).equalsIgnoreCase("auto")) {
                    wind.hold(world, null);
                    sender.sendMessage(green("The wind in '" + world.getName() + "' blows as it would again: "
                            + day(wind.day(world), units) + " today."));
                    return;
                }
                WindModel.Level level = WindModel.Level.parse(rest.get(1));
                if (level == null) throw new IllegalArgumentException("'" + rest.get(1)
                        + "' is not a wind: calm, breezy, windy, gale, or auto.");
                wind.hold(world, level);
                sender.sendMessage(green("It is " + day(wind.day(world), units) + " in '" + world.getName()
                        + "' until the day ends. /hvac wind set auto lets it blow as it would."));
                return;
            }
            default -> throw new IllegalArgumentException("Usage: /hvac wind [set <calm|breezy|windy|gale|auto>]");
        }
        List<WindModel.Day> forecast = wind.forecast(world, FORECAST_DAYS);
        Component line = join(gold("Wind"), gray(" | Today: "), white(day(forecast.get(0), units)));
        if (wind.held(world)) line = line.append(yellow(" (set by an admin)"));
        sender.sendMessage(line);
        if (sender instanceof Player player && player.getWorld().equals(world)) {
            AirflowService.TemperatureReading here =
                    airflow.temperatureForBody(player.getLocation(), player.getEyeLocation());
            sender.sendMessage(join(gray("Here: "),
                    white(here.water() ? "in water" : WindText.describe(here.windMs(), wind.settings(),
                            settings.units())),
                    gray(" | Out in the open: "),
                    white(WindText.describe(outdoor.climateAt(player.getLocation()).windMs(), wind.settings(),
                            settings.units()))));
        }
        Component ahead = gray("Next days: ");
        for (int index = 1; index < forecast.size(); index++)
            ahead = ahead.append(white((index > 1 ? ", " : "") + forecast.get(index).level().displayName()));
        sender.sendMessage(ahead);
    }

    /** "Windy, about 22 km/h". */
    private static String day(WindModel.Day day, WindSettings.Units units) {
        return day.level().displayName() + ", about " + WindText.speed(day.meanMs(), units);
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
        UUID world = player.getWorld().getUID();
        List<SystemId> systems = registry.allSystems().stream()
                .filter(system -> system.worldId().equals(world)).toList();
        if (systems.isEmpty()) { player.sendMessage(gray("No HVAC systems exist in this world.")); return; }
        player.sendMessage(gold("EZvac systems:"));
        for (SystemId system : systems) {
            HvacRegistry.Members members = registry.members(system);
            if (members.thermostat() == null) {
                BlockKey anchor = anchor(system);
                player.sendMessage(join(gray("Headless"), gray(anchor == null ? "" : " (" + SystemNames.at(anchor) + ")"),
                        gray(" | "), white(SystemNames.contents(members))));
                continue;
            }
            HvacController.SystemStatus status = controller.status(system);
            player.sendMessage(join(white(SystemNames.of(members.thermostat())), gray(" | "),
                    white(status.mode().name()), gray(" | "), white(status.profile().name()), gray(" | "),
                    white(temp(status.roomF()) + " / " + temp(status.targetF())),
                    gray(" | equipment "), white(status.running() + "/" + status.equipment()),
                    gray(" | "), white(SystemNames.count(members.vents(), "vent"))));
        }
    }

    /** Where a headless system can be found: its first unit, else its first vent, else a sign. */
    private BlockKey anchor(SystemId system) {
        List<EquipmentUnit> equipment = registry.equipmentFor(system);
        if (!equipment.isEmpty()) return equipment.get(0).position();
        for (ClimateVent vent : registry.ventsFor(system)) return vent.position();
        SettingsPanel panel = registry.settingsPanelFor(system);
        if (panel != null) return panel.position();
        List<RpmMonitor> monitors = registry.rpmMonitorsFor(system);
        return monitors.isEmpty() ? null : monitors.get(0).position();
    }

    /** "System: Home (120, 64, -30)", or "Not linked" for a sign without a system. */
    private Component systemOf(SystemId system) {
        return system == null ? gray(" | Not linked") : join(gray(" | System: "), white(SystemNames.of(registry, system)));
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
        if (vent != null) { player.sendMessage(join(gold("Vent"), systemOf(vent.system()))); return; }
        Thermometer thermometer = registry.thermometerAt(key);
        if (thermometer != null) {
            AirflowService.TemperatureReading reading = airflow.temperatureAt(block.getLocation());
            String source = reading.conditioned() ? SystemNames.label(registry, reading.system()) : "outdoor";
            String humidity = Double.isFinite(reading.humidity()) ? Math.round(reading.humidity()) + "%" : "--%";
            String local = switch (thermometer.reads()) {
                case TEMPERATURE -> temp(reading.localF());
                case HUMIDITY -> humidity;
                case BOTH -> temp(reading.localF()) + ", " + humidity + ", feels " + temp(reading.feelsLikeF());
                case AIR -> AirText.summary(reading.air(), units.air().settings());
            };
            player.sendMessage(join(gold(thermometer.reads().displayName()), gray(" | Local: "), white(local),
                    gray(" | Source: "), white(source)));
            return;
        }
        RpmMonitor monitor = registry.rpmMonitorAt(key);
        if (monitor != null) { player.sendMessage(join(gold("RPM monitor "), white(monitor.label()),
                systemOf(monitor.system()), gray(" | auto-flip " + (monitor.autoFlip() ? "on" : "off")))); return; }
        SettingsPanel panel = registry.settingsPanelAt(key);
        if (panel != null) { player.sendMessage(join(gold("Settings panel "), white(panel.label()),
                systemOf(panel.system()), gray(" | Profile: "), white(panel.profile().name()))); return; }
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

    private void remove(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("system")) {
            removeSystem(player, args.length >= 3 && args[2].equalsIgnoreCase("confirm"));
            return;
        }
        Block block = target(player);
        if (block == null) return;
        registry.removingByCommand(() -> remove(player, BlockKey.of(block.getLocation())));
    }

    /**
     * {@code /hvac remove system [confirm]}: every device of the system the
     * player looks at, holding the link tool so it can't happen by accident.
     * The first run only says what would go; the confirm does it.
     */
    private void removeSystem(Player player, boolean confirm) {
        if (!linkTool.isTool(player.getInventory().getItemInMainHand()))
            throw new IllegalArgumentException("Hold the link tool to remove a whole system, and look at any part of it.");
        if (confirm) {
            PendingRemoval pending = pendingRemovals.remove(player.getUniqueId());
            if (pending == null || System.currentTimeMillis() > pending.expiresAt())
                throw new IllegalArgumentException("Nothing to confirm. Run /hvac remove system while looking at"
                        + " the system first.");
            HvacRegistry.Members members = registry.members(pending.system());
            if (members.empty()) throw new IllegalArgumentException("That system is already gone.");
            String name = SystemNames.of(registry, pending.system());
            registry.removingByCommand(() -> registry.removeSystem(pending.system()));
            airflow.invalidate(pending.system());
            displays.refreshAll(true);
            plugin.requestImmediateSync();
            player.sendMessage(green("Removed " + name + ": " + everything(members) + ". The blocks are still there."));
            return;
        }
        Block block = target(player);
        if (block == null) return;
        SystemId system = registry.systemAt(BlockKey.of(block.getLocation()));
        if (system == null) throw new IllegalArgumentException("Look at a thermostat, equipment, a vent, a settings"
                + " panel or an RPM monitor that belongs to a system.");
        pendingRemovals.put(player.getUniqueId(),
                new PendingRemoval(system, System.currentTimeMillis() + CONFIRM_MILLIS));
        player.sendMessage(yellow("This removes " + SystemNames.of(registry, system) + ": "
                + everything(registry.members(system)) + ". Run /hvac remove system confirm within "
                + CONFIRM_MILLIS / 1000 + " seconds to go ahead."));
    }

    /** "the thermostat, 4 units and 32 vents". */
    private static String everything(HvacRegistry.Members members) {
        String rest = SystemNames.contents(members);
        if (members.thermostat() == null) return rest;
        return rest.equals("nothing") ? "the thermostat" : "the thermostat, " + rest;
    }

    private void remove(Player player, BlockKey key) {
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat != null) {
            HvacRegistry.Members members = registry.members(thermostat.system());
            registry.removeThermostat(key);
            boolean headless = members.units() + members.vents() + members.panels() + members.monitors() > 0;
            player.sendMessage(green("Thermostat removed." + (headless ? " Its system is headless now: link a"
                    + " thermostat to any part of it to take it over." : "")));
        } else if (registry.equipmentAt(key) != null) {
            registry.removeEquipment(key);
            player.sendMessage(green("Equipment removed; owned output cleanup was queued if needed."));
        } else {
            ClimateVent vent = registry.removeVent(key);
            if (vent != null) { airflow.invalidate(vent.system()); player.sendMessage(green("Vent unlinked.")); }
            else if (registry.thermometerAt(key) != null) {
                String name = registry.thermometerAt(key).reads().displayName();
                registry.removeThermometer(key);
                player.sendMessage(green(name + " removed."));
            }
            else if (registry.removeRpmMonitor(key)) player.sendMessage(green("RPM monitor removed."));
            else if (registry.removeSettingsPanel(key))
                player.sendMessage(green("Settings panel removed; its system runs on Normal now."));
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
            case AIR -> AirText.summary(reading.air(), units.air().settings());
            case WIND -> WindText.describe(reading.windMs(), outdoor.climate().wind(), settings.units());
            case WEATHER -> throw new AssertionError("handled above");
        };
        String where = reading.conditioned() ? "system " + SystemNames.label(registry, reading.system()) : "no system";
        return join(gold(sensor.label()), gray(" | "), white(values), gray(" | " + where));
    }

    /** Clicks with the link tool belong to the tool, not to the block clicked. */
    public boolean isTool(ItemStack item) {
        return linkTool.isTool(item);
    }

    private void showThermostat(Player player, Thermostat thermostat) {
        player.sendMessage(join(gold(thermostat.pool() ? "Pool thermostat " : "Thermostat "), white(thermostat.id()),
                gray(" | Linked: "), white(SystemNames.contents(registry.members(thermostat.system())))));
        player.sendMessage(join(gray("Room/target: "), white(temp(thermostat.roomF()) + " / "
                + temp(thermostat.targetF())), gray(" | Mode: "), white(thermostat.mode().name()),
                gray(" | Profile: "), white(registry.profileFor(thermostat.system()).name()),
                gray(" | "), white(thermostat.enabled() ? "enabled" : "disabled")));
        if (!thermostat.pool() && thermostat.hasRoomDewPoint()) {
            double humidity = Psychrometrics.relativeHumidity(thermostat.roomF(), thermostat.roomDewPointF());
            player.sendMessage(join(gray("Humidity: "), white(Math.round(humidity) + "%"),
                    gray(" | Feels like: "), white(temp(
                            Psychrometrics.apparentTemperatureF(thermostat.roomF(), humidity, 0.0)))));
        }
        if (!thermostat.pool() && units.air().enabled() && thermostat.roomAir().known()) {
            player.sendMessage(join(gray("Air: "), airLine(thermostat.roomAir())));
            player.sendMessage(gray(AirText.details(thermostat.roomAir())));
        }
    }

    /** {@code AQI 42 Good · Pollen High}, coloured by its band (3.2). */
    private Component airLine(AirQualityModel.Air air) {
        return Component.text(AirText.summary(air, units.air().settings()), AirText.color(air));
    }

    /** An air purifier for /hvac info (3.2): what it reads, its filter, and the particles in detail. */
    private void showPurifier(Player player, AirPurifier purifier) {
        String space = purifier.servedSystem() == null ? "its own zone"
                : "system " + SystemNames.label(registry, purifier.servedSystem());
        player.sendMessage(join(gold("Air purifier "), white(purifier.label()), gray(" | Cleaning: "), white(space)));
        String filter = switch (purifier.installed()) {
            case MISSING -> "none: place a tube coral block under it";
            case SPENT -> "full: break the dead coral and place fresh tube coral";
            case FRESH -> Math.round(purifier.filter() * 100) + "% left";
        };
        String fan = purifier.mode() == AirPurifier.Mode.AUTO
                ? "Auto" + (purifier.running() ? ", running " + purifier.speed().displayName() : "")
                : purifier.mode().displayName();
        player.sendMessage(join(gray("Fan: "), white(fan),
                gray(" | State: "), white(purifier.state().name()), gray(" | Filter: "), white(filter)));
        AirQualityModel.Air air = purifier.lastAir();
        if (air.known()) {
            player.sendMessage(join(gray("Air: "), airLine(air)));
            player.sendMessage(gray(AirText.details(air)));
        } else {
            player.sendMessage(gray(units.air().enabled() ? "Air: not measured yet."
                    : "Air quality is switched off on this server (air-quality.enabled)."));
        }
    }

    private void showClimateUnit(Player player, ClimateUnit unit) {
        if (unit instanceof AirPurifier purifier) {
            showPurifier(player, purifier);
            return;
        }
        String space = unit.servedSystem() == null ? "its own zone"
                : "system " + SystemNames.label(registry, unit.servedSystem());
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
            Thermostat thermostat = unit.servedSystem() == null ? null : registry.thermostatFor(unit.servedSystem());
            double dewPointF = thermostat == null ? unit.zoneDewPointF() : thermostat.roomDewPointF();
            player.sendMessage(gold("Air too cold: its dew point (" + settings.units().format(dewPointF, 0)
                    + ") is at or below what the coil can remove ("
                    + settings.units().format(units.climate().dehumidifier().dryDewPointF(), 0)
                    + "), so it can't dry this air."));
        }
    }

    private void showEquipment(Player player, EquipmentUnit unit) {
        player.sendMessage(join(gold(unit.type().displayName() + " "), aqua(tierName(unit.tier(), unit.type())),
                systemOf(unit.system())));
        player.sendMessage(join(gray("State: "), white(unit.running() ? unit.operatingMode().name() : "IDLE"),
                gray(" | RPM: "), white(String.format(Locale.US, "%.0f", unit.motorRpm())),
                gray(" | Profile: "), white(unit.type().fixedSpeed()
                        ? "FIXED" : registry.profileFor(unit.system()).name()),
                gray(" | Output: "), white(unit.hasOwnedOutput() ? "owned" : "none"),
                gray(" | "), white(unit.enabled() ? "enabled" : "disabled")));
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

    /** "★★☆ Two-stage". */
    private String tierName(Tier tier, EquipmentType type) {
        return settings.stars(tier) + " " + tier.displayName(type);
    }

    private boolean isTemperature(String text) {
        try {
            settings.units().parse(text);
            return true;
        } catch (IllegalArgumentException notATemperature) {
            return false;
        }
    }

    private static boolean isEquipmentType(String text) {
        try {
            parseEquipmentType(text);
            return true;
        } catch (IllegalArgumentException notAKind) {
            return false;
        }
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
        sender.sendMessage(join(aqua("/hvac wind"), gray(" - today's wind, the wind here, and the next days'")));
        if (!sender.hasPermission(ADMIN_PERMISSION)) return;
        sender.sendMessage(join(aqua("/hvac season set <season> [day] | next | pause | resume"),
                gray(" - change the season")));
        sender.sendMessage(join(aqua("/hvac wind set <calm|breezy|windy|gale|auto>"),
                gray(" - set today's wind, until the day ends")));
        sender.sendMessage(join(aqua("/hvac create thermostat <label> [target]"), gray(" - register a sign")));
        sender.sendMessage(join(aqua("/hvac create heatpump|ac|furnace|boiler|poolheatpump [t1|t2|t3]"),
                gray(" - register a dispenser, two-stage (t2) unless told")));
        sender.sendMessage(join(aqua("/hvac create thermometer|hygrometer|thermohygrometer|airmonitor [label]"),
                gray(" - register a point-sensor sign")));
        sender.sendMessage(join(aqua("/hvac create rpmmonitor | settingspanel [label]"),
                gray(" - a sign for a system's equipment, or its profile")));
        sender.sendMessage(join(aqua("/hvac create dehumidifier [label]"), gray(" - sign on an iron block on a grate")));
        sender.sendMessage(join(aqua("/hvac create humidifier [label]"), gray(" - sign on an iron block under a copper slab")));
        sender.sendMessage(join(aqua("/hvac create purifier [label]"), gray(" - sign on an iron block on tube coral")));
        sender.sendMessage(join(aqua("/hvac create sensor [label]"),
                gray(" - a stone, polished blackstone, mangrove, birch, cherry or bamboo button")));
        sender.sendMessage(join(aqua("/hvac create sensor-hub [label]"), gray(" - a sign showing up to 9 sensors")));
        sender.sendMessage(join(aqua("/hvac link-tool"), gray(" - link devices to a thermostat, or sensors to a hub")));
        sender.sendMessage(join(aqua("/hvac thermostat set|enable|disable|info"), gray(" - the thermostat you look at")));
        sender.sendMessage(join(aqua("/hvac equipment enable|disable|tier|info"), gray(" - the unit you look at")));
        sender.sendMessage(join(aqua("/hvac rename <label>"), gray(" - rename the sign you look at")));
        sender.sendMessage(join(aqua("/hvac remove | remove system | stats | sync"), gray(" - remove, diagnose, reconcile")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(args[0], "help", "temperature", "hud", "list", "info",
                "create", "thermostat", "equipment", "link-tool", "rename", "remove", "stats", "sync", "season",
                "wind");
        if (args[0].equalsIgnoreCase("season")) {
            if (args.length == 2) return sender.hasPermission(ADMIN_PERMISSION)
                    ? filter(args[1], "set", "next", "pause", "resume") : List.of();
            if (args.length == 3 && args[1].equalsIgnoreCase("set"))
                return filter(args[2], "spring", "summer", "autumn", "winter");
            return filter(args[args.length - 1], plugin.getServer().getWorlds().stream()
                    .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                    .map(World::getName).toArray(String[]::new));
        }
        if (args[0].equalsIgnoreCase("wind")) {
            if (args.length == 2) return sender.hasPermission(ADMIN_PERMISSION) ? filter(args[1], "set") : List.of();
            if (args.length == 3 && args[1].equalsIgnoreCase("set"))
                return filter(args[2], "calm", "breezy", "windy", "gale", "auto");
            return filter(args[args.length - 1], plugin.getServer().getWorlds().stream()
                    .filter(WindService::hasWind).map(World::getName).toArray(String[]::new));
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> filter(args[1], "thermostat", "poolthermostat", "heatpump",
                        "ac", "furnace", "boiler", "poolheatpump", "thermometer", "hygrometer",
                        "thermohygrometer", "airmonitor", "rpmmonitor", "settingspanel", "dehumidifier",
                        "humidifier", "purifier", "sensor", "sensor-hub");
                case "thermostat" -> filter(args[1], "set", "enable", "disable", "info");
                case "equipment" -> filter(args[1], "enable", "disable", "tier", "info");
                case "remove" -> filter(args[1], "system");
                case "hud" -> sender.hasPermission(ADMIN_PERMISSION) ? filter(args[1], "on", "off", "sections")
                        : filter(args[1], "on", "off");
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("remove") && args[1].equalsIgnoreCase("system"))
            return filter(args[2], "confirm");
        if (args.length == 3 && (args[0].equalsIgnoreCase("equipment") && args[1].equalsIgnoreCase("tier")
                || args[0].equalsIgnoreCase("create") && isEquipmentType(args[1])))
            return filter(args[2], "t1", "t2", "t3");
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
