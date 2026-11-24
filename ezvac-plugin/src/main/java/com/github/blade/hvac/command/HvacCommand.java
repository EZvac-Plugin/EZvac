package com.github.blade.hvac.command;

import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.simulation.WindModel;
import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.StartupNotices;
import com.github.blade.hvac.api.event.ThermostatTargetChangeEvent;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.RefrigerationSettings;
import com.github.blade.hvac.config.ServerSwitches;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.hud.ClimateHudService;
import com.github.blade.hvac.hud.HudFormatter;
import com.github.blade.hvac.integration.api.ApiEvents;
import com.github.blade.hvac.lang.Lang;
import com.github.blade.hvac.lang.UserError;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.service.*;
import com.github.blade.hvac.simulation.AirQualityModel;
import com.github.blade.hvac.simulation.Forecast;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.Seasons;
import com.github.blade.hvac.simulation.SizingModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Tag;
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

import static com.github.blade.hvac.lang.Lang.arg;

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
    private static final String GROUPS_GONE = "command.groups-gone";
    private static final String LINK_HINT = "command.link-hint";

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
            helpPage(args.length >= 2 ? args[1] : "", sender.hasPermission(HUD_PERMISSION),
                    sender.hasPermission(ADMIN_PERMISSION)).forEach(sender::sendMessage);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (subcommand.equals("stats")) {
            if (!requireAdmin(sender)) return true;
            if (args.length >= 2 && args[1].equalsIgnoreCase("sizes")) {
                sizes(sender);
                return true;
            }
            sender.sendMessage(Lang.message("command.stats.summary", arg("thermostats", registry.thermostats().size()),
                    arg("equipment", registry.equipment().size()), arg("panels", registry.settingsPanels().size()),
                    arg("airflow", airflow.stats())));
            // What each part costs the tick, over the last minute (BETA 1.0 Pilot 4).
            List<com.github.blade.hvac.service.TickCost.Part> cost = plugin.tickCost().lastMinute();
            if (!cost.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (com.github.blade.hvac.service.TickCost.Part part : cost)
                    parts.add(part.name().toLowerCase(Locale.ROOT) + String.format(Locale.US, " %.3f", part.msPerTick()));
                sender.sendMessage(Lang.message("command.stats.tick-cost", arg("ms", String.format(Locale.US, "%.3f",
                        com.github.blade.hvac.service.TickCost.total(cost))), arg("parts", String.join(", ", parts))));
            }
            return true;
        }
        if (subcommand.equals("sync")) {
            if (!requireAdmin(sender)) return true;
            // Blocks changed without events (/fill, WorldEdit) leave maps stale until remapped (BETA 1.0).
            plugin.airflow().invalidateAllSystems();
            plugin.weatherStations().recheckAll();
            plugin.requestImmediateSync();
            sender.sendMessage(Lang.message("command.sync.queued"));
            return true;
        }
        if (subcommand.equals("registry")) {
            if (!requireAdmin(sender)) return true;
            registryCommand(sender, args);
            return true;
        }
        if (subcommand.equals("update")) {
            if (!requireAdmin(sender)) return true;
            if (plugin.updates() == null) sender.sendMessage(Lang.message("command.still-starting"));
            else plugin.updates().checkNow(sender);
            return true;
        }
        if (subcommand.equals("season")) {
            try {
                season(sender, args);
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(UserError.describe(exception));
            }
            return true;
        }
        if (subcommand.equals("wind")) {
            try {
                wind(sender, args);
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(UserError.describe(exception));
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.message("command.needs-player"));
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
                case "frost" -> { if (requireAdmin(player)) frost(player, args); }
                case "group" -> { if (requireAdmin(player)) player.sendMessage(Lang.message("command.group.gone")); }
                // The duct and sensor tools became the link tool in 3.0; their names still give it.
                case "link-tool", "linktool", "duct-tool", "ducttool", "tool", "sensor-tool", "sensortool" ->
                        { if (requireAdmin(player)) linkTool.give(player); }
                case "remove" -> { if (requireAdmin(player)) remove(player, args); }
                case "quicksettings", "qs" -> { if (requireAdmin(player)) plugin.quickSettings().open(player); }
                // The first-start setup (BETA 1.0 Pilot 10), again any time; skip marks it done.
                case "setup" -> {
                    if (!requireAdmin(player)) return true;
                    if (args.length > 1 && args[1].equalsIgnoreCase("skip")) plugin.setup().skip(player);
                    else plugin.setup().open(player);
                }
                // Build your first system (BETA 1.0 Pilot 10): a guided tutorial in a world of its own.
                case "tutorial" -> {
                    if (!requireAdmin(player)) return true;
                    var tutorial = plugin.tutorial();
                    if (tutorial == null) player.sendMessage(Lang.message("command.tutorial.unavailable"));
                    else if (args.length > 1 && args[1].equalsIgnoreCase("leave")) {
                        if (tutorial.inTutorial(player)) tutorial.leave(player, true);
                        else player.sendMessage(Lang.message("command.tutorial.not-in"));
                    } else tutorial.begin(player);
                }
                case "addons", "addon" -> { if (requireAdmin(player)) plugin.quickSettings().openAddons(player); }
                case "forecast", "weatherfc" -> plugin.forecastMenu().openForecast(player, null);
                default -> player.sendMessage(Lang.message("command.unknown"));
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage(UserError.describe(exception));
        }
        return true;
    }

    private void hud(Player player, String[] args) {
        if (!player.hasPermission(HUD_PERMISSION)) {
            player.sendMessage(Lang.message("command.hud.not-allowed"));
            return;
        }
        if (!hud.enabled()) {
            player.sendMessage(Lang.message("command.hud.off-on-server"));
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
        else throw usage("/hvac hud [on|off|sections]");
        hud.setShowing(player, show);
        player.sendMessage(Lang.message(show ? "command.hud.on" : "command.hud.off"));
    }

    /** The addon sections on the HUD (API 1.1), for admins placing them in config.yml. */
    private void hudSections(Player player) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(Lang.message("command.hud.sections.not-admin"));
            return;
        }
        List<String> ids = hud.sectionIds();
        if (ids.isEmpty()) {
            player.sendMessage(Lang.message("command.hud.sections.none"));
            return;
        }
        List<String> hidden = hud.format().settings().hiddenSections();
        player.sendMessage(Lang.message("command.hud.sections.title"));
        for (String id : ids)
            player.sendMessage(Lang.message(hidden.contains(id) ? "command.hud.sections.hidden"
                    : "command.hud.sections.shown", arg("id", id), arg("token", "{section:" + id + "}")));
    }

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(usage("/hvac create <thermostat|poolthermostat|heatpump|ac"
                    + "|furnace|boiler|poolheatpump|thermometer|hygrometer|thermohygrometer|rpmmonitor"
                    + "|settingspanel|dehumidifier|humidifier|purifier|fridge|deepfreezer|refrigerator|minifridge|airmonitor> ...")
                    .component());
            return;
        }
        Block block = target(player);
        if (block == null) return;
        BlockKey position = BlockKey.of(block.getLocation());
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "thermostat", "poolthermostat", "pool" -> {
                boolean pool = !args[1].equalsIgnoreCase("thermostat");
                if (args.length < 3) throw usage("/hvac create " + args[1] + " <label> [target]");
                // At a button on a wall, a mini thermostat (BETA 1.0 Pilot 2): the same, with a small display.
                boolean mini = Tag.BUTTONS.isTagged(block.getType());
                if (mini) requireWallButton(block);
                else requireSign(block);
                // Before 3.0 the word after the label was the group: create thermostat main house 72.
                int targetArg = 3;
                if (args.length >= 4 && !isTemperature(args[3])) {
                    targetArg = 4;
                    player.sendMessage(Lang.message(GROUPS_GONE));
                }
                double target = args.length > targetArg ? settings.units().parse(args[targetArg])
                        : (pool ? Thermostat.POOL_DEFAULT_F : Thermostat.ROOM_DEFAULT_F);
                Thermostat thermostat = new Thermostat(position, args[2], registry.newSystem(position.worldId()),
                        target, outdoor.temperatureAt(block.getLocation()), settings, pool);
                thermostat.setMini(mini);
                registry.addThermostat(thermostat);
                displays.refreshThermostat(thermostat, true);
                player.sendMessage(Lang.message(Lang.key("command.create.thermostat",
                        (mini ? "mini-" : "") + (pool ? "pool" : "room")), arg("name", thermostat.id())));
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
                player.sendMessage(Lang.message("command.create.instrument", arg("kind",
                        reads.displayName().toLowerCase(Locale.ROOT))));
            }
            case "rpmmonitor", "rpm" -> {
                requireSign(block);
                // Before 3.0: create rpmmonitor <group> <kind> [label]. A monitor now shows its whole system.
                String displayLabel = args.length >= 3 ? args[2] : "RPM";
                if (args.length >= 4 && isEquipmentType(args[3])) {
                    displayLabel = args.length >= 5 ? args[4] : args[2];
                    player.sendMessage(Lang.message(GROUPS_GONE));
                }
                RpmMonitor monitor = new RpmMonitor(position, displayLabel);
                registry.addRpmMonitor(monitor);
                player.sendMessage(Lang.message("command.create.rpm-monitor", arg("name", monitor.label())));
                if (!linkTool.linkCreated(player, block)) player.sendMessage(Lang.message(LINK_HINT));
                displays.refreshRpmMonitor(monitor, true);
            }
            case "settingspanel", "settings", "panel" -> {
                requireSign(block);
                // Before 3.0: create settingspanel <group> [label]. The label is the last word.
                String displayLabel = args.length >= 3 ? args[args.length - 1] : "Settings";
                if (args.length >= 4) player.sendMessage(Lang.message(GROUPS_GONE));
                SettingsPanel panel = new SettingsPanel(position, displayLabel);
                registry.addSettingsPanel(panel);
                player.sendMessage(Lang.message("command.create.settings-panel", arg("name", panel.label())));
                if (!linkTool.linkCreated(player, block)) player.sendMessage(Lang.message(LINK_HINT));
                displays.refreshSettingsPanel(panel, true);
            }
            case "dehumidifier", "dehum" -> createDehumidifier(player, block, position, args);
            case "humidifier", "hum" -> createHumidifier(player, block, position, args);
            case "purifier", "airpurifier", "air-purifier" -> createPurifier(player, block, position, args);
            case "fridge" -> createRefrigerator(player, block, position, args, false);
            case "deepfreezer", "deep-freezer", "freezer" -> createRefrigerator(player, block, position, args, true);
            case "refrigerator", "fridge-freezer", "fridgefreezer", "combo" ->
                    createFridgeFreezer(player, block, position, args);
            case "minifridge", "mini-fridge", "mini" -> createMiniFridge(player, block, position, args);
            case "sensor" -> {
                Sensor.Kind reads = Sensor.forButton(block.getType());
                if (reads == null)
                    throw new UserError("command.create.sensor.not-a-button");
                Sensor sensor = new Sensor(position, args.length >= 3 ? args[2] : "Sensor", reads);
                registry.addSensor(sensor);
                player.sendMessage(Lang.message(reads == Sensor.Kind.WEATHER ? "command.create.sensor.made-weather"
                        : reads == Sensor.Kind.WIND ? "command.create.sensor.made-wind" : "command.create.sensor.made",
                        arg("kind", reads.description()), arg("name", sensor.label())));
            }
            case "weatherstation", "weather-station", "station" -> createWeatherStation(player, block, position, args);
            case "sensor-hub", "sensorhub" -> {
                requireSign(block);
                SensorHub hub = new SensorHub(position, args.length >= 3 ? args[2] : "Sensors");
                registry.addSensorHub(hub);
                displays.refreshSensorHub(hub, true);
                player.sendMessage(Lang.message("command.create.sensor-hub", arg("name", hub.label()),
                        arg("max", SensorHub.MAX_SENSORS)));
            }
            default -> throw new UserError("command.create.unknown-kind", arg("kind", args[1]));
        }
        plugin.requestImmediateSync();
    }

    /**
     * A weather station (BETA 1.0): the wall sign on the waxed cut copper of
     * a whole tower, in a world with weather. It measures its area at once.
     */
    private void createWeatherStation(Player player, Block sign, BlockKey position, String[] args) {
        requireSwitchedOn(DeviceKind.WEATHER_STATION);
        if (sign.getWorld().getEnvironment() != World.Environment.NORMAL)
            throw new UserError("command.create.station.overworld");
        WeatherStationService.Check check = WeatherStationService.check(sign);
        if (!check.whole()) throw new UserError("command.create.station.not-whole", arg("missing", check.missing()));
        WeatherStation station = new WeatherStation(position, args.length >= 3 ? args[2] : "Station");
        registry.addWeatherStation(station);
        plugin.weatherStations().measure(station);
        displays.refreshWeatherStation(station, true);
        player.sendMessage(Lang.message("command.create.station.made", arg("name", station.label()),
                arg("range", plugin.weatherStations().settings().range())));
    }

    /**
     * A dehumidifier is an iron block on a waxed copper grate, controlled by
     * the sign the player is looking at: a wall sign on the iron block, or a
     * sign standing on top of it.
     */
    private void createDehumidifier(Player player, Block sign, BlockKey position, String[] args) {
        requireSwitchedOn(DeviceKind.DEHUMIDIFIER);
        requireSign(sign);
        Block iron = sign.getBlockData() instanceof WallSign wall
                ? sign.getRelative(wall.getFacing().getOppositeFace()) : sign.getRelative(BlockFace.DOWN);
        if (iron.getType() != Material.IRON_BLOCK)
            throw new UserError("command.create.iron-sign");
        Block grate = iron.getRelative(BlockFace.DOWN);
        if (grate.getType() != ClimateVent.WATER_VENT)
            throw new UserError("command.create.dehumidifier.grate");
        BlockKey grateKey = BlockKey.of(grate.getLocation());
        if (registry.ventAt(grateKey) != null)
            throw new UserError("command.create.dehumidifier.grate-is-vent");
        String label = args.length >= 3 ? args[2] : "Dehumidifier";
        registerUnit(player, new Dehumidifier(position, BlockKey.of(iron.getLocation()), grateKey, label,
                units.climate().dehumidifier().defaultSetpoint()));
    }

    /**
     * A humidifier is an iron block with a waxed cut copper slab (bottom half)
     * on top, controlled by a wall sign on the iron block.
     */
    private void createHumidifier(Player player, Block sign, BlockKey position, String[] args) {
        requireSwitchedOn(DeviceKind.HUMIDIFIER);
        requireSign(sign);
        if (!(sign.getBlockData() instanceof WallSign wall))
            throw new UserError("command.create.humidifier.wall-sign");
        Block iron = sign.getRelative(wall.getFacing().getOppositeFace());
        if (iron.getType() != Material.IRON_BLOCK)
            throw new UserError("command.create.humidifier.iron");
        Block slab = iron.getRelative(BlockFace.UP);
        if (slab.getType() != Humidifier.TOP || !(slab.getBlockData() instanceof Slab half)
                || half.getType() != Slab.Type.BOTTOM)
            throw new UserError("command.create.humidifier.slab");
        String label = args.length >= 3 ? args[2] : "Humidifier";
        registerUnit(player, new Humidifier(position, BlockKey.of(iron.getLocation()), BlockKey.of(slab.getLocation()),
                label, units.climate().humidifier().defaultSetpoint()));
    }

    /**
     * An air purifier (3.2) is built like a dehumidifier: an iron block, with
     * the sign on it, standing on a tube coral block, which is its filter.
     */
    private void createPurifier(Player player, Block sign, BlockKey position, String[] args) {
        requireSwitchedOn(DeviceKind.AIR_PURIFIER);
        requireSign(sign);
        Block iron = sign.getBlockData() instanceof WallSign wall
                ? sign.getRelative(wall.getFacing().getOppositeFace()) : sign.getRelative(BlockFace.DOWN);
        if (iron.getType() != Material.IRON_BLOCK)
            throw new UserError("command.create.iron-sign");
        // The filter can go in afterwards (3.2 HOTFIX 1): tube coral dies away from water within seconds,
        // so a purifier can be made over an empty block, or over dead coral, and wait for a fresh one.
        Block filter = iron.getRelative(BlockFace.DOWN);
        Material below = filter.getType();
        if (below != AirPurifier.FILTER && below != AirPurifier.SPENT_FILTER && !below.isAir())
            throw new UserError("command.create.purifier.filter");
        String label = args.length >= 3 ? args[2] : "Purifier";
        AirPurifier purifier = new AirPurifier(position, BlockKey.of(iron.getLocation()),
                BlockKey.of(filter.getLocation()), label, AirPurifier.Mode.AUTO);
        purifier.readFilter(below);
        registerUnit(player, purifier);
        if (purifier.installed() != AirPurifier.Filter.FRESH)
            player.sendMessage(Lang.message(below.isAir() ? "command.create.purifier.waits-empty"
                    : "command.create.purifier.waits-dead"));
        if (!units.air().enabled())
            player.sendMessage(Lang.message("command.create.purifier.air-off"));
    }

    private void registerUnit(Player player, ClimateUnit unit) {
        registry.addClimateUnit(unit);
        airflow.invalidate(ZoneId.of(unit));
        displays.refreshClimateUnit(unit, true);
        if (unit instanceof MiniFridge mini) {
            plugin.frost().showWalls(mini);
            player.sendMessage(Lang.message("command.create.mini-fridge", arg("name", unit.label()),
                    arg("setpoint", settings.units().format(mini.setpoint(), 0))));
            return;
        }
        if (unit instanceof FridgeFreezer both) {
            player.sendMessage(Lang.message("command.create.refrigerator", arg("name", unit.label()),
                    arg("fridge", settings.units().format(both.fridgeSetpoint(), 0)),
                    arg("freezer", settings.units().format(both.setpoint(), 0))));
            return;
        }
        if (unit instanceof Freezer freezer) {
            player.sendMessage(Lang.message("command.create.deep-freezer", arg("name", unit.label()),
                    arg("setpoint", settings.units().format(freezer.setpoint(), 0))));
            return;
        }
        if (unit instanceof Fridge fridge) {
            player.sendMessage(Lang.message(fridge.barrels() == 1 ? "command.create.fridge.one" : "command.create.fridge.many",
                    arg("name", unit.label()), arg("setpoint", settings.units().format(fridge.setpoint(), 0)),
                    arg("barrels", fridge.barrels())));
            return;
        }
        if (unit instanceof AirPurifier) {
            player.sendMessage(Lang.message("command.create.purifier.made", arg("name", unit.label())));
            return;
        }
        player.sendMessage(Lang.message("command.create.climate-unit", arg("kind",
                unit.kind().displayName().toLowerCase(Locale.ROOT)), arg("name", unit.label()),
                arg("setpoint", unit.setpoint())));
    }

    /**
     * A fridge or deep freezer (BETA 1.0 Pilot 7) is an iron block, its
     * compressor, with a barrel on top and the control sign on the iron
     * block's side. A fridge keeps up to three barrels stacked there.
     */
    private void createRefrigerator(Player player, Block sign, BlockKey position, String[] args, boolean freezer) {
        requireSwitchedOn(freezer ? DeviceKind.FREEZER : DeviceKind.FRIDGE);
        requireSign(sign);
        if (!(sign.getBlockData() instanceof WallSign wall))
            throw new UserError("command.create.humidifier.wall-sign");
        Block iron = sign.getRelative(wall.getFacing().getOppositeFace());
        if (iron.getType() != Material.IRON_BLOCK)
            throw new UserError("command.create.humidifier.iron");
        Block barrel = iron.getRelative(BlockFace.UP);
        if (barrel.getType() != Refrigerator.BARREL)
            throw new UserError("command.create.fridge-barrel");
        String label = args.length >= 3 ? args[2] : freezer ? "Deep-freezer" : "Fridge";
        RefrigerationSettings cold = units.climate().refrigeration();
        BlockKey unit = BlockKey.of(iron.getLocation()), box = BlockKey.of(barrel.getLocation());
        if (freezer) {
            registerUnit(player, new Freezer(position, unit, box, label, cold.freezerDefaultF()));
            return;
        }
        // A fridge takes every barrel stacked on it, up to three.
        List<BlockKey> upper = new ArrayList<>();
        Block above = barrel.getRelative(BlockFace.UP);
        while (upper.size() < Fridge.MOST_BARRELS - 1 && above.getType() == Refrigerator.BARREL
                && registry.deviceKind(BlockKey.of(above.getLocation())) == HvacRegistry.DeviceKind.NONE) {
            upper.add(BlockKey.of(above.getLocation()));
            above = above.getRelative(BlockFace.UP);
        }
        registerUnit(player, new Fridge(position, unit, box, upper, label, cold.fridgeDefaultF()));
    }

    /**
     * A refrigerator (BETA 1.0 Pilot 7, a fridge-freezer): an iron block with the control sign
     * on its side, the freezer barrel on top of it and the fridge barrel on
     * top of that.
     */
    private void createFridgeFreezer(Player player, Block sign, BlockKey position, String[] args) {
        requireSwitchedOn(DeviceKind.FRIDGE_FREEZER);
        requireSign(sign);
        if (!(sign.getBlockData() instanceof WallSign wall))
            throw new UserError("command.create.humidifier.wall-sign");
        Block iron = sign.getRelative(wall.getFacing().getOppositeFace());
        if (iron.getType() != Material.IRON_BLOCK)
            throw new UserError("command.create.humidifier.iron");
        Block freezer = iron.getRelative(BlockFace.UP), fridge = freezer.getRelative(BlockFace.UP);
        if (freezer.getType() != Refrigerator.BARREL || fridge.getType() != Refrigerator.BARREL)
            throw new UserError("command.create.refrigerator-barrels");
        String label = args.length >= 3 ? args[2] : "Refrigerator";
        RefrigerationSettings cold = units.climate().refrigeration();
        registerUnit(player, new FridgeFreezer(position, BlockKey.of(iron.getLocation()),
                BlockKey.of(freezer.getLocation()), BlockKey.of(fridge.getLocation()), label,
                cold.refrigeratorFreezerDefaultF(), cold.fridgeDefaultF()));
    }

    /**
     * A mini fridge (BETA 1.0 Pilot 7): a barrel with the control sign on its
     * side, its compressor inside, so no iron block.
     */
    private void createMiniFridge(Player player, Block sign, BlockKey position, String[] args) {
        requireSwitchedOn(DeviceKind.MINI_FRIDGE);
        requireSign(sign);
        if (!(sign.getBlockData() instanceof WallSign wall))
            throw new UserError("command.create.mini-fridge-wall-sign");
        Block barrel = sign.getRelative(wall.getFacing().getOppositeFace());
        if (barrel.getType() != Refrigerator.BARREL)
            throw new UserError("command.create.mini-fridge-barrel");
        String label = args.length >= 3 ? args[2] : "Mini-fridge";
        registerUnit(player, new MiniFridge(position, BlockKey.of(barrel.getLocation()), label,
                units.climate().refrigeration().fridgeDefaultF()));
    }

    /** Kinds switched off in /hvac quicksettings can't be made (BETA 1.0). */
    private static void requireSwitchedOn(DeviceKind kind) {
        if (ServerSwitches.deviceOff(kind)) throw new IllegalArgumentException(kind.refusal());
    }

    private void createEquipment(Player player, Block block, String[] args, EquipmentType type) {
        requireSwitchedOn(DeviceKind.of(type));
        if (block.getType() != Material.DISPENSER)
            throw new UserError("command.create.equipment.dispenser");
        // The word after the kind is a tier (3.1); before 3.0 it was a group, now ignored.
        // New units are two-stage unless told (3.1); every unit from before then kept what it was.
        Tier tier = args.length >= 3 ? Tier.parse(args[2]) : Tier.TWO_STAGE;
        if (tier == null) {
            player.sendMessage(Lang.message(GROUPS_GONE));
            tier = Tier.TWO_STAGE;
        }
        BlockKey position = BlockKey.of(block.getLocation());
        // A unit starts in a headless system of its own, until it is linked to a thermostat.
        EquipmentUnit unit = new EquipmentUnit(position, registry.newSystem(position.worldId()), type);
        unit.setTier(tier);
        registry.addEquipment(unit);
        player.sendMessage(Lang.message("command.create.equipment.made", arg("kind", type.displayName()),
                arg("tier", tierName(tier, type))));
        if (!linkTool.linkCreated(player, block)) player.sendMessage(Lang.message(LINK_HINT));
    }

    private static final List<String> THERMOSTAT_ACTIONS = List.of("set", "settemp", "enable", "disable", "info");

    /** {@code /hvac thermostat <set|enable|disable|info>}, looking at the thermostat. Labels may repeat. */
    private void thermostat(Player player, String[] args) {
        if (args.length < 2) throw new UserError("command.thermostat.usage",
                arg("usage", "/hvac thermostat <set <temperature>|enable|disable|info>"));
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!THERMOSTAT_ACTIONS.contains(action)) {
            // Before 3.0: /hvac thermostat <id> <action>. Labels may repeat now, so a name finds nothing.
            String rest = String.join(" ", Arrays.asList(args).subList(Math.min(2, args.length), args.length));
            throw new UserError("command.thermostat.by-name", arg("command",
                    "/hvac thermostat " + (rest.isEmpty() ? "info" : rest)));
        }
        Block block = target(player);
        if (block == null) return;
        Thermostat thermostat = registry.thermostatAt(BlockKey.of(block.getLocation()));
        if (thermostat == null) throw new UserError("command.thermostat.look");
        switch (action) {
            case "set", "settemp" -> {
                if (args.length < 3) throw usage("/hvac thermostat set <temperature>");
                double previous = thermostat.targetF();
                thermostat.setTargetF(settings.units().parse(args[2]), settings);
                registry.markDirty();
                ApiEvents.targetChanged(thermostat, previous, ThermostatTargetChangeEvent.Cause.COMMAND, controller);
                player.sendMessage(Lang.message("command.thermostat.target-set", arg("target", temp(thermostat.targetF()))));
            }
            case "enable" -> { thermostat.setEnabled(true); registry.markDirty(); player.sendMessage(Lang.message("command.thermostat.enabled")); }
            case "disable" -> { thermostat.setEnabled(false); registry.markDirty(); player.sendMessage(Lang.message("command.thermostat.disabled")); }
            default -> showThermostat(player, thermostat);
        }
        displays.refreshThermostat(thermostat, true);
        plugin.requestImmediateSync();
    }

    private void equipment(Player player, String[] args) {
        if (args.length < 2) throw usage("/hvac equipment <enable|disable|tier <t1|t2|t3>|info>");
        Block block = target(player);
        if (block == null) return;
        EquipmentUnit unit = registry.equipmentAt(BlockKey.of(block.getLocation()));
        if (unit == null) throw new UserError("command.equipment.not-equipment");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "enable" -> { unit.setEnabled(true); player.sendMessage(Lang.message("command.equipment.enabled")); }
            case "disable" -> { unit.setEnabled(false); unit.stop(); player.sendMessage(Lang.message("command.equipment.disabled")); }
            case "tier" -> {
                Tier tier = args.length >= 3 ? Tier.parse(args[2]) : null;
                if (tier == null) throw new UserError("command.equipment.tier-usage",
                        arg("usage", "/hvac equipment tier <t1|t2|t3>"));
                // Stopped first, so it starts again on its new tier's curve.
                unit.stop();
                unit.setTier(tier);
                player.sendMessage(Lang.message("command.equipment.tier-set", arg("kind", unit.type().displayName()),
                        arg("tier", tierName(tier, unit.type()))));
            }
            case "group" -> throw new UserError("command.equipment.groups-gone");
            case "info" -> showEquipment(player, unit);
            default -> throw new UserError("command.equipment.unknown-action", arg("action", args[1]));
        }
        registry.markDirty();
        plugin.requestImmediateSync();
    }

    /** {@code /hvac rename <label>}: the sign looked at. Labels are only names, so they may repeat. */
    private void rename(Player player, String[] args) {
        if (args.length < 2) throw new UserError("command.rename.usage", arg("usage", "/hvac rename <label>"));
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
            throw new UserError("command.rename.look");
        registry.markDirty();
        displays.invalidate(key);
        displays.refreshAll(true);
        player.sendMessage(Lang.message("command.rename.done", arg("name", label)));
    }

    /**
     * /hvac registry (BETA 1.0): what this start recovered hvac.yml from and
     * which entries it kept aside; {@code repair --force} discards those for
     * good, after copying hvac.yml aside.
     */
    private void registryCommand(CommandSender sender, String[] args) {
        List<HvacRegistry.KeptAside> aside = registry.keptAsideRecords();
        boolean repair = args.length >= 2 && args[1].equalsIgnoreCase("repair");
        boolean force = repair && args.length >= 3 && args[2].equalsIgnoreCase("--force");
        if (!repair) {
            sender.sendMessage(registry.recoveredFrom() == null ? Lang.message("command.registry.read-fine")
                    : Lang.message("command.registry.recovered", arg("from", registry.recoveredFrom()),
                    arg("aside", registry.setAside())));
            if (aside.isEmpty()) {
                sender.sendMessage(Lang.message("command.registry.nothing-aside"));
                return;
            }
            sender.sendMessage(Lang.message(aside.size() == 1 ? "command.registry.aside.one" : "command.registry.aside.many",
                    arg("count", aside.size())));
            for (HvacRegistry.KeptAside entry : aside.subList(0, Math.min(10, aside.size())))
                sender.sendMessage(Lang.message("command.registry.aside.entry", arg("section", entry.section()),
                        arg("entry", entry.entry()), arg("reason", entry.reason())));
            if (aside.size() > 10) sender.sendMessage(Lang.message("command.registry.aside.more",
                    arg("count", aside.size() - 10)));
            sender.sendMessage(Lang.message("command.registry.aside.fix", arg("command", StartupNotices.REPAIR)));
            return;
        }
        if (aside.isEmpty()) {
            sender.sendMessage(Lang.message("command.registry.nothing-to-repair"));
            return;
        }
        if (!force) {
            sender.sendMessage(Lang.message(aside.size() == 1 ? "command.registry.confirm.one"
                    : "command.registry.confirm.many", arg("count", aside.size()), arg("command", StartupNotices.REPAIR)));
            return;
        }
        HvacRegistry.Repair repaired;
        try {
            repaired = registry.repairKeptAside();
        } catch (java.io.IOException failure) {
            sender.sendMessage(Lang.message("command.registry.copy-failed", arg("reason", String.valueOf(failure.getMessage()))));
            return;
        }
        int discarded = repaired.discarded();
        plugin.getLogger().warning(sender.getName() + " discarded " + discarded + " kept-aside entr"
                + (discarded == 1 ? "y" : "ies") + " from hvac.yml with " + StartupNotices.REPAIR
                + "; the file before is " + repaired.copy());
        sender.sendMessage(Lang.message(discarded == 1 ? "command.registry.discarded.one"
                : "command.registry.discarded.many", arg("count", discarded), arg("copy", repaired.copy())));
    }

    /** /hvac info on a weather station (BETA 1.0). */
    private void showWeatherStation(Player player, WeatherStation station) {
        WeatherStationService stations = plugin.weatherStations();
        String state = Lang.text(!WeatherStationService.switchedOn() ? "command.station.state.off-server"
                : !station.enabled() ? "command.station.state.off"
                : !station.intact() ? "command.station.state.damaged" : "command.station.state.on");
        player.sendMessage(Lang.message("command.station.title", arg("name", station.label()), arg("state", state)));
        if (station.area() == null) player.sendMessage(Lang.message("command.station.unmeasured"));
        else player.sendMessage(Lang.message("command.station.outdoors",
                arg("temperature", StationText.temperature(stations.nowF(station), settings.units())),
                arg("radius", stations.settings().areaRadius()), arg("height", stations.settings().areaHeight())));
        List<Forecast.Day> days = WeatherStationService.working(station) ? stations.forecast(station) : List.of();
        if (!days.isEmpty()) {
            Forecast.Day today = days.get(0);
            boolean snow = stations.snowDay(station, today);
            player.sendMessage(Lang.message(today.note().isEmpty() ? "command.station.today" : "command.station.today-note",
                    arg("sky", StationText.sky(today, snow)), arg("high-low", StationText.highLow(today, settings.units())),
                    arg("note", today.note())));
        }
        player.sendMessage(Lang.message(station.strikes() == 1 ? "command.station.strikes.one"
                : "command.station.strikes.many", arg("count", station.strikes())));
    }

    private void temperature(Player player) {
        AirflowService.TemperatureReading reading =
                airflow.temperatureForBody(player.getLocation(), player.getEyeLocation());
        boolean water = reading.water();
        String outside = temp(reading.outdoorF()) + (reading.weather().isEmpty() ? "" : " " + DisplayService.word(reading.weather()));
        player.sendMessage(Lang.message(water ? "command.temperature.water" : "command.temperature.air",
                arg("here", temp(reading.localF())), arg("outside", outside)));
        if (!water) {
            Component feels = Lang.message("command.temperature.feels", arg("feels", temp(reading.feelsLikeF())),
                    arg("humidity", Math.round(reading.humidity())));
            if (windShown(player.getWorld()))
                feels = feels.append(Lang.message("command.temperature.wind",
                        arg("wind", WindText.describe(reading.windMs(), reading.windFromDegrees(), outdoor.climate().wind(),
                                settings.units()))));
            player.sendMessage(feels);
        }
        if (!water && units.air().enabled() && reading.air().known())
            player.sendMessage(Lang.message("command.temperature.air-quality", arg("air", airLine(reading.air()))));
        UUID world = player.getWorld().getUID();
        if (plugin.seasons().settings().enabled() && plugin.seasons().tracks(world))
            player.sendMessage(Lang.message("command.temperature.season", arg("season", seasonText(world)),
                    arg("shift", shift(outdoor.seasonShiftAt(player.getLocation())))));
        if (!reading.conditioned()) {
            player.sendMessage(Lang.message(water ? "command.temperature.natural-water" : "command.temperature.no-path"));
        } else {
            int strength = HudFormatter.strength(reading.airflowDistance() <= settings.airflowCoreRange(),
                    reading.influence());
            String text = strength + "%";
            player.sendMessage(Lang.message("command.temperature.system", arg("system",
                    SystemNames.label(registry, reading.system())), arg("distance", reading.airflowDistance()),
                    arg("strength", strength == 100 ? green(text) : strength < 50 ? gold(text) : white(text))));
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
            sender.sendMessage(Lang.message("command.season.off"));
            return;
        }
        if (world.getEnvironment() != World.Environment.NORMAL || !seasons.tracks(world.getUID())) {
            sender.sendMessage(Lang.message("command.season.no-seasons", arg("world", world.getName())));
            return;
        }
        UUID id = world.getUID();
        String action = rest.isEmpty() ? "info" : rest.get(0).toLowerCase(Locale.ROOT);
        int days = seasons.settings().daysPerSeason();
        switch (action) {
            case "info" -> { }
            case "set" -> {
                if (!requireAdmin(sender)) return;
                if (rest.size() < 2) throw usage("/hvac season set <spring|summer|autumn|winter> [day]");
                Seasons.Season season = Seasons.Season.parse(rest.get(1));
                if (season == null) throw new UserError("command.season.not-a-season", arg("word", rest.get(1)));
                int day = 1;
                if (rest.size() >= 3) {
                    try {
                        day = Integer.parseInt(rest.get(2));
                    } catch (NumberFormatException notNumber) {
                        throw new UserError("command.season.day-number", arg("days", days));
                    }
                    if (day < 1 || day > days) throw new UserError("command.season.day-range", arg("days", days));
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
                sender.sendMessage(Lang.message(action.equals("pause") ? "command.season.paused" : "command.season.resumed",
                        arg("world", world.getName())));
                return;
            }
            default -> throw usage("/hvac season [set <season> [day] | next | pause | resume]");
        }
        if (!action.equals("info")) {
            seasons.save();
            sender.sendMessage(Lang.message("command.season.set", arg("season", seasonText(id)),
                    arg("world", world.getName())));
            return;
        }
        Component line = Lang.message("command.season.info", arg("season", seasonText(id)));
        if (sender instanceof Player player && player.getWorld().equals(world))
            line = line.append(Lang.message("command.season.info-here", arg("shift",
                    shift(outdoor.seasonShiftAt(player.getLocation())))));
        if (seasons.paused(id)) line = line.append(Lang.message("command.season.info-paused"));
        sender.sendMessage(line);
    }

    /** Whether a world has wind to show: wind is on, and the world has a sky (3.2). */
    private boolean windShown(World world) {
        return outdoor.wind() != null && outdoor.wind().enabled() && WindService.hasWind(world);
    }

    /** How many days {@code /hvac wind} looks ahead, today included. */
    private static final int FORECAST_DAYS = 3;

    /**
     * {@code /hvac wind [set <calm|breezy|windy|gale|auto> [direction]] [world]}
     * (3.2): anyone may look at today's wind and the next days', admins may set
     * today's. From the console the world can be named last; it defaults to
     * the main world. A player sees their own region's wind (BETA 1.0 Pilot 3),
     * the console the world's.
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
            sender.sendMessage(Lang.message("command.wind.off"));
            return;
        }
        if (!WindService.hasWind(world)) {
            sender.sendMessage(Lang.message("command.wind.no-wind", arg("world", world.getName())));
            return;
        }
        String action = rest.isEmpty() ? "info" : rest.get(0).toLowerCase(Locale.ROOT);
        WindSettings.Units units = WindText.units(wind.settings(), settings.units());
        switch (action) {
            case "info" -> { }
            case "set" -> {
                if (!requireAdmin(sender)) return;
                if (rest.size() < 2) throw usage(
                        "/hvac wind set <calm|breezy|windy|gale|auto> [direction, such as nw or 270]");
                if (rest.get(1).equalsIgnoreCase("auto")) {
                    wind.hold(world, null);
                    sender.sendMessage(Lang.message("command.wind.auto", arg("world", world.getName()),
                            arg("day", day(wind.day(world), units))));
                    return;
                }
                WindModel.Level level = WindModel.Level.parse(rest.get(1));
                if (level == null) throw new UserError("command.wind.not-a-wind", arg("word", rest.get(1)));
                double from = Double.NaN;
                if (rest.size() > 2) {
                    from = WindText.parseDirection(rest.get(2));
                    if (Double.isNaN(from)) throw new UserError("command.wind.not-a-direction", arg("word", rest.get(2)));
                }
                wind.hold(world, level, from);
                sender.sendMessage(Lang.message("command.wind.set", arg("day", day(wind.day(world), units)),
                        arg("world", world.getName())));
                return;
            }
            default -> throw usage("/hvac wind [set <calm|breezy|windy|gale|auto> [direction]]");
        }
        Player here = sender instanceof Player player && player.getWorld().equals(world) ? player : null;
        List<WindModel.Day> forecast = here == null ? wind.forecast(world, FORECAST_DAYS)
                : wind.forecastAt(world, here.getLocation().getX(), here.getLocation().getZ(), FORECAST_DAYS);
        Component line = Lang.message(here == null ? "command.wind.today" : "command.wind.today-here",
                arg("day", day(forecast.get(0), units)));
        if (wind.held(world)) line = line.append(Lang.message("command.wind.held"));
        else if (here != null && forecast.get(0).level() != wind.day(world).level())
            line = line.append(Lang.message("command.wind.across-world", arg("level",
                    wind.day(world).level().displayName())));
        sender.sendMessage(line);
        if (here != null) {
            AirflowService.TemperatureReading reading =
                    airflow.temperatureForBody(here.getLocation(), here.getEyeLocation());
            OutdoorTemperatureService.Outdoor open = outdoor.climateAt(here.getLocation());
            sender.sendMessage(Lang.message("command.wind.here", arg("here", reading.water()
                            ? Lang.text("command.wind.in-water") : WindText.describe(reading.windMs(),
                            reading.windFromDegrees(), wind.settings(), settings.units())),
                    arg("open", WindText.describe(open.windMs(), open.windFromDegrees(), wind.settings(),
                            settings.units()))));
        }
        List<String> days = new ArrayList<>();
        for (int index = 1; index < forecast.size(); index++) {
            WindModel.Day next = forecast.get(index);
            days.add(next.level() == WindModel.Level.CALM ? next.level().displayName()
                    : Lang.text("command.wind.next-from", arg("level", next.level().displayName()),
                    arg("from", WindText.compass(next.fromDegrees()))));
        }
        sender.sendMessage(Lang.message("command.wind.next", arg("days", String.join(Lang.text("list.separator"), days))));
    }

    /** "Windy, about 22 km/h from the W"; a calm day has no direction. */
    private static String day(WindModel.Day day, WindSettings.Units units) {
        return Lang.text(day.level() == WindModel.Level.CALM ? "command.wind.day-calm" : "command.wind.day",
                arg("level", day.level().displayName()), arg("speed", WindText.speed(day.meanMs(), units)),
                arg("from", WindText.compass(day.fromDegrees())));
    }

    /** "Summer, day 3 of 15". */
    private String seasonText(UUID world) {
        SeasonService seasons = plugin.seasons();
        return Lang.text("command.season.day", arg("season", seasons.season(world).displayName()),
                arg("day", seasons.dayOfSeason(world)), arg("days", seasons.settings().daysPerSeason()));
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
        if (systems.isEmpty()) { player.sendMessage(Lang.message("command.list.none")); return; }
        player.sendMessage(Lang.message("command.list.title"));
        for (SystemId system : systems) {
            HvacRegistry.Members members = registry.members(system);
            if (members.thermostat() == null) {
                BlockKey anchor = anchor(system);
                player.sendMessage(Lang.message(anchor == null ? "command.list.headless" : "command.list.headless-at",
                        arg("at", anchor == null ? "" : SystemNames.at(anchor)), arg("contents", SystemNames.contents(members))));
                continue;
            }
            HvacController.SystemStatus status = controller.status(system);
            player.sendMessage(join(white(SystemNames.of(members.thermostat())), gray(" | "),
                    white(status.mode().name()), gray(" | "), white(status.profile().name()), gray(" | "),
                    white(temp(status.roomF()) + " / " + temp(status.targetF())),
                    Lang.message("command.list.equipment", arg("running", status.running()), arg("total", status.equipment())),
                    gray(" | "), white(SystemNames.count(members.vents(), "vent")),
                    // BETA 1.0 Pilot 8: running from what EZvac last saw there.
                    members.thermostat().position().isChunkLoaded() ? Component.empty()
                            : Lang.message(plugin.settings().unloadedDevices() ? "command.list.running-unloaded"
                            : "command.list.unloaded")));
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
        return system == null ? Lang.message("command.info.not-linked")
                : Lang.message("command.info.system", arg("system", SystemNames.of(registry, system)));
    }

    /** Who last changed it from a plugin (a web panel, say) or its screen (BETA 1.0 Pilot 7), when anyone has. */
    private void lastChange(Player player, BlockKey position) {
        if (plugin.controls() == null) return;
        plugin.controls().lastChange(position).ifPresent(change ->
                player.sendMessage(Lang.message("command.info.last-changed", arg("by", change))));
    }

    private void info(Player player) {
        Block block = target(player);
        if (block == null) return;
        BlockKey key = BlockKey.of(block.getLocation());
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat != null) { showThermostat(player, thermostat); lastChange(player, thermostat.position()); return; }
        EquipmentUnit unit = registry.equipmentAt(key);
        if (unit != null) { showEquipment(player, unit); lastChange(player, unit.position()); return; }
        ClimateVent vent = registry.ventAt(key);
        if (vent != null) { player.sendMessage(Lang.message("command.info.vent", arg("system", systemOf(vent.system())))); return; }
        Thermometer thermometer = registry.thermometerAt(key);
        if (thermometer != null) {
            AirflowService.TemperatureReading reading = airflow.temperatureAt(block.getLocation());
            String source = reading.conditioned() ? SystemNames.label(registry, reading.system())
                    : Lang.text("command.info.outdoor");
            String humidity = Double.isFinite(reading.humidity()) ? Math.round(reading.humidity()) + "%" : "--%";
            String local = switch (thermometer.reads()) {
                case TEMPERATURE -> temp(reading.localF());
                case HUMIDITY -> humidity;
                case BOTH -> Lang.text("command.info.instrument-both", arg("temperature", temp(reading.localF())),
                        arg("humidity", humidity), arg("feels", temp(reading.feelsLikeF())));
                case AIR -> AirText.summary(reading.air(), units.air().settings());
            };
            player.sendMessage(Lang.message("command.info.instrument", arg("kind", thermometer.reads().displayName()),
                    arg("local", local), arg("source", source)));
            return;
        }
        RpmMonitor monitor = registry.rpmMonitorAt(key);
        if (monitor != null) { player.sendMessage(Lang.message(monitor.autoFlip() ? "command.info.rpm-monitor.flip-on"
                : "command.info.rpm-monitor.flip-off", arg("name", monitor.label()), arg("system", systemOf(monitor.system()))));
            return; }
        SettingsPanel panel = registry.settingsPanelAt(key);
        if (panel != null) { player.sendMessage(Lang.message("command.info.settings-panel", arg("name", panel.label()),
                arg("system", systemOf(panel.system())), arg("profile", panel.profile().name()))); return; }
        ClimateUnit climateUnit = registry.climateUnitAt(key);
        if (climateUnit != null) {
            showClimateUnit(player, climateUnit);
            lastChange(player, climateUnit.position());
            return;
        }
        Sensor sensor = registry.sensorAt(key);
        if (sensor != null) {
            player.sendMessage(describeSensor(sensor));
            int hubs = registry.hubsShowing(key).size();
            player.sendMessage(Lang.message(hubs == 1 ? "command.info.sensor-hubs.one" : "command.info.sensor-hubs.many",
                    arg("count", hubs)));
            return;
        }
        WeatherStation station = registry.weatherStationAt(key);
        if (station != null) {
            showWeatherStation(player, station);
            return;
        }
        SensorHub hub = registry.sensorHubAt(key);
        if (hub != null) {
            player.sendMessage(Lang.message(hub.autoFlip() ? "command.info.sensor-hub.flip-on"
                    : "command.info.sensor-hub.flip-off", arg("name", hub.label()), arg("count", hub.sensors().size()),
                    arg("max", SensorHub.MAX_SENSORS)));
            for (BlockKey linked : hub.sensors()) {
                Sensor shown = registry.sensorAt(linked);
                if (shown != null) player.sendMessage(describeSensor(shown));
            }
            return;
        }
        player.sendMessage(Lang.message("command.info.no-device"));
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
            throw new UserError("command.remove.system.hold-tool");
        if (confirm) {
            PendingRemoval pending = pendingRemovals.remove(player.getUniqueId());
            if (pending == null || System.currentTimeMillis() > pending.expiresAt())
                throw new UserError("command.remove.system.nothing-to-confirm");
            HvacRegistry.Members members = registry.members(pending.system());
            if (members.empty()) throw new UserError("command.remove.system.gone");
            String name = SystemNames.of(registry, pending.system());
            registry.removingByCommand(() -> registry.removeSystem(pending.system()));
            airflow.invalidate(pending.system());
            displays.refreshAll(true);
            plugin.requestImmediateSync();
            player.sendMessage(Lang.message("command.remove.system.done", arg("system", name),
                    arg("devices", everything(members))));
            return;
        }
        Block block = target(player);
        if (block == null) return;
        SystemId system = registry.systemAt(BlockKey.of(block.getLocation()));
        if (system == null) throw new UserError("command.remove.system.look");
        pendingRemovals.put(player.getUniqueId(),
                new PendingRemoval(system, System.currentTimeMillis() + CONFIRM_MILLIS));
        player.sendMessage(Lang.message("command.remove.system.confirm", arg("system", SystemNames.of(registry, system)),
                arg("devices", everything(registry.members(system))), arg("seconds", CONFIRM_MILLIS / 1000)));
    }

    /** "the thermostat, 4 units and 32 vents". */
    private static String everything(HvacRegistry.Members members) {
        String rest = SystemNames.contents(members);
        if (members.thermostat() == null) return rest;
        return rest.equals(SystemNames.nothing()) ? Lang.text("command.remove.system.just-thermostat")
                : Lang.text("command.remove.system.thermostat-and", arg("rest", rest));
    }

    private void remove(Player player, BlockKey key) {
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat != null) {
            HvacRegistry.Members members = registry.members(thermostat.system());
            registry.removeThermostat(key);
            boolean headless = members.units() + members.vents() + members.panels() + members.monitors() > 0;
            player.sendMessage(Lang.message(headless ? "command.remove.thermostat-headless" : "command.remove.thermostat"));
        } else if (registry.equipmentAt(key) != null) {
            registry.removeEquipment(key);
            player.sendMessage(Lang.message("command.remove.equipment"));
        } else {
            ClimateVent vent = registry.removeVent(key);
            if (vent != null) { airflow.invalidate(vent.system()); player.sendMessage(Lang.message("command.remove.vent")); }
            else if (registry.thermometerAt(key) != null) {
                String name = registry.thermometerAt(key).reads().displayName();
                registry.removeThermometer(key);
                player.sendMessage(Lang.message("command.remove.named", arg("kind", name)));
            }
            else if (registry.removeRpmMonitor(key)) player.sendMessage(Lang.message("command.remove.rpm-monitor"));
            else if (registry.removeSettingsPanel(key))
                player.sendMessage(Lang.message("command.remove.settings-panel"));
            else if (registry.climateUnitAt(key) != null) {
                ClimateUnit removed = registry.removeClimateUnit(key);
                units.release(removed);
                displays.invalidate(removed.position());
                player.sendMessage(Lang.message("command.remove.named", arg("kind", removed.kind().displayName())));
            }
            else if (registry.removeSensor(key) != null)
                player.sendMessage(Lang.message("command.remove.sensor"));
            else if (registry.removeSensorHub(key) != null) player.sendMessage(Lang.message("command.remove.sensor-hub"));
            else if (registry.removeWeatherStation(key) != null) {
                plugin.weatherStations().forget(key);
                player.sendMessage(Lang.message("command.remove.station"));
            }
            else { player.sendMessage(Lang.message("command.info.no-device")); return; }
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
            return Lang.message("command.sensor.not-loaded", arg("name", sensor.label()));
        if (sensor.reads() == Sensor.Kind.WEATHER) {
            String weather = displays.weatherAt(location);
            return Lang.message(weather.equals(DisplayService.COVERED) ? "command.sensor.weather-covered"
                    : "command.sensor.weather", arg("name", sensor.label()), arg("weather", DisplayService.word(weather)));
        }
        AirflowService.TemperatureReading reading = airflow.temperatureAt(location);
        String humidity = Double.isFinite(reading.humidity()) ? Math.round(reading.humidity()) + "%" : "--%";
        String values = switch (sensor.reads()) {
            case TEMPERATURE -> temp(reading.localF());
            case HUMIDITY -> humidity;
            case BOTH -> Lang.text("command.sensor.both", arg("temperature", temp(reading.localF())),
                    arg("humidity", humidity), arg("feels", temp(reading.feelsLikeF())));
            case AIR -> AirText.summary(reading.air(), units.air().settings());
            case WIND -> WindText.describe(reading.windMs(), reading.windFromDegrees(), outdoor.climate().wind(),
                    settings.units());
            case WEATHER -> throw new AssertionError("handled above");
        };
        String where = reading.conditioned() ? Lang.text("command.sensor.in-system", arg("system",
                SystemNames.label(registry, reading.system()))) : Lang.text("command.sensor.no-system");
        return Lang.message("command.sensor.reading", arg("name", sensor.label()), arg("values", values),
                arg("where", where));
    }

    /** Clicks with the link tool belong to the tool, not to the block clicked. */
    public boolean isTool(ItemStack item) {
        return linkTool.isTool(item);
    }

    /** /hvac stats sizes (BETA 1.0): every system's size and what it has of what it needs, the worst sized first. */
    private void sizes(CommandSender sender) {
        if (!plugin.controller().sizing().enabled()) {
            sender.sendMessage(Lang.message("command.size.off"));
            return;
        }
        List<HvacController.SizeLine> lines = plugin.controller().sizeLines();
        if (lines.isEmpty()) {
            sender.sendMessage(Lang.message("command.size.no-systems"));
            return;
        }
        sender.sendMessage(Lang.message("command.size.title"));
        for (HvacController.SizeLine line : lines)
            sender.sendMessage(Component.text(" " + com.github.blade.hvac.gui.QuickSettingsMenu.sizeText(line),
                    line.worst() < 1.0 ? NamedTextColor.GOLD : NamedTextColor.GRAY));
    }

    /** How the system measures up (BETA 1.0): its size, what it needs and has, its openings and neighbours. */
    private void showSize(Player player, Thermostat thermostat) {
        if (!plugin.controller().sizing().enabled()) {
            player.sendMessage(Lang.message("command.size.off"));
            return;
        }
        SizingModel.Assessment cooling = plugin.controller().assess(thermostat.system(), OperatingMode.COOLING);
        SizingModel.Assessment heating = plugin.controller().assess(thermostat.system(), OperatingMode.HEATING);
        if (cooling == null) {
            player.sendMessage(Lang.message("command.size.unmapped"));
            return;
        }
        String size = String.format(Locale.US, "%,d", cooling.size());
        player.sendMessage(cooling.water() ? Lang.message("command.size.water", arg("size", size))
                : Lang.message(cooling.openings() == 0 ? "command.size.air-sealed" : "command.size.air",
                arg("size", size), arg("openings", cooling.openings()),
                arg("leaks", String.format(Locale.US, "%.1f", cooling.openness()))));
        List<Component> parts = new ArrayList<>();
        for (SizingModel.Assessment sized : List.of(cooling, heating)) {
            if (sized.installed() <= 0.0) continue;
            if (!parts.isEmpty()) parts.add(Lang.message("command.size.separator"));
            String[] ratio = sized.ratio().split("/", 2);
            parts.add(Lang.message(sized == cooling ? "command.size.cooling" : "command.size.heating",
                    arg("has", Component.text(Lang.text("command.size.of", arg("has", ratio[0]),
                            arg("needs", ratio.length > 1 ? ratio[1] : "")),
                            sized.undersized() ? NamedTextColor.GOLD : NamedTextColor.WHITE))));
        }
        if (!parts.isEmpty()) {
            parts.add(Lang.message("command.size.units-worth"));
            player.sendMessage(join(parts.toArray(Component[]::new)));
        }
        AirflowService.Shape shape = airflow.shape(thermostat.system());
        if (shape != null && !shape.contacts().isEmpty()) {
            List<String> touching = new ArrayList<>();
            shape.contacts().entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .forEach(contact -> touching.add(Lang.text(contact.getValue() == 1 ? "command.size.contact.one"
                            : "command.size.contact.many", arg("system", SystemNames.label(registry, contact.getKey())),
                            arg("faces", contact.getValue()))));
            player.sendMessage(Lang.message("command.size.touches", arg("systems",
                    String.join(Lang.text("list.separator"), touching))));
        }
    }

    private void showThermostat(Player player, Thermostat thermostat) {
        player.sendMessage(Lang.message(thermostat.pool() ? "command.thermostat.info.pool" : "command.thermostat.info.room",
                arg("name", thermostat.id()), arg("linked", SystemNames.contents(registry.members(thermostat.system())))));
        player.sendMessage(Lang.message("command.thermostat.info.state", arg("room", temp(thermostat.roomF())),
                arg("target", temp(thermostat.targetF())), arg("mode", thermostat.mode().name()),
                arg("profile", registry.profileFor(thermostat.system()).name()),
                arg("enabled", Lang.text(thermostat.enabled() ? "command.thermostat.info.enabled"
                        : "command.thermostat.info.disabled"))));
        String times = ModeTimes.line(thermostat, System.currentTimeMillis());
        if (!times.isEmpty()) player.sendMessage(gray(times));
        showSize(player, thermostat);
        if (thermostat.enabled() && thermostat.waitingOnUnits() != null)
            player.sendMessage(Lang.message(thermostat.waitingOnUnits() == OperatingMode.HEATING
                    ? "command.thermostat.info.needs-heat" : "command.thermostat.info.needs-cooling"));
        if (!thermostat.pool() && thermostat.hasRoomDewPoint()) {
            double humidity = Psychrometrics.relativeHumidity(thermostat.roomF(), thermostat.roomDewPointF());
            player.sendMessage(Lang.message("command.thermostat.info.humidity", arg("humidity", Math.round(humidity)),
                    arg("feels", temp(Psychrometrics.apparentTemperatureF(thermostat.roomF(), humidity, 0.0)))));
        }
        if (!thermostat.pool() && units.air().enabled() && thermostat.roomAir().known()) {
            player.sendMessage(Lang.message("command.temperature.air-quality", arg("air", airLine(thermostat.roomAir()))));
            player.sendMessage(gray(AirText.details(thermostat.roomAir())));
        }
    }

    /** {@code AQI 42 Good · Pollen High}, coloured by its band (3.2). */
    private Component airLine(AirQualityModel.Air air) {
        return Component.text(AirText.summary(air, units.air().settings()), AirText.color(air));
    }

    /** An air purifier for /hvac info (3.2): what it reads, its filter, and the particles in detail. */
    private void showPurifier(Player player, AirPurifier purifier) {
        String space = servedSpace(purifier.servedSystem());
        player.sendMessage(Lang.message("command.purifier.title", arg("name", purifier.label()), arg("space", space)));
        String filter = switch (purifier.installed()) {
            case MISSING -> Lang.text("command.purifier.filter.missing");
            case SPENT -> Lang.text("command.purifier.filter.spent");
            case FRESH -> Lang.text("command.purifier.filter.left", arg("percent", Math.round(purifier.filter() * 100)));
        };
        String fan = purifier.mode() == AirPurifier.Mode.AUTO
                ? purifier.running() ? Lang.text("command.purifier.auto-running", arg("speed",
                purifier.speed().displayName())) : Lang.text("command.purifier.auto")
                : purifier.mode().displayName();
        player.sendMessage(Lang.message("command.purifier.state", arg("fan", fan), arg("state", purifier.state().name()),
                arg("filter", filter)));
        AirQualityModel.Air air = purifier.lastAir();
        if (air.known()) {
            player.sendMessage(Lang.message("command.temperature.air-quality", arg("air", airLine(air))));
            player.sendMessage(Component.text(AirText.details(air), NamedTextColor.GRAY));
        } else {
            player.sendMessage(Lang.message(units.air().enabled() ? "command.purifier.unmeasured"
                    : "command.purifier.air-off"));
        }
    }

    /** "its own zone", or the system a unit works on. */
    private String servedSpace(SystemId system) {
        return system == null ? Lang.text("command.climate-unit.own-zone")
                : Lang.text("command.sensor.in-system", arg("system", SystemNames.label(registry, system)));
    }

    private void showClimateUnit(Player player, ClimateUnit unit) {
        if (unit.offServerWide())
            player.sendMessage(Lang.message("command.info.kind-off", arg("kinds", DeviceKind.of(unit.kind()).plural())));
        if (unit instanceof AirPurifier purifier) {
            showPurifier(player, purifier);
            return;
        }
        if (unit instanceof Refrigerator refrigerator) {
            showRefrigerator(player, refrigerator);
            return;
        }
        String space = servedSpace(unit.servedSystem());
        double humidity = unit.lastHumidity();
        player.sendMessage(Lang.message(unit instanceof Dehumidifier ? "command.climate-unit.drying"
                : "command.climate-unit.humidifying", arg("kind", unit.kind().displayName()), arg("name", unit.label()),
                arg("space", space)));
        player.sendMessage(Lang.message("command.climate-unit.state", arg("humidity", Double.isFinite(humidity)
                        ? Math.round(humidity) + "%" : Lang.text("command.climate-unit.unknown")),
                arg("setpoint", unit.setpoint()), arg("state", unit.state().name()),
                arg("water", unit instanceof Dehumidifier dehumidifier
                        ? Lang.message("command.climate-unit.tank", arg("tank", Math.round(dehumidifier.tank() * 100)))
                        : Lang.message("command.climate-unit.plumbed"))));
        if (unit instanceof Dehumidifier dehumidifier && dehumidifier.tooCold()) {
            Thermostat thermostat = unit.servedSystem() == null ? null : registry.thermostatFor(unit.servedSystem());
            double dewPointF = thermostat == null ? unit.zoneDewPointF() : thermostat.roomDewPointF();
            player.sendMessage(Lang.message("command.climate-unit.too-cold", arg("dew", settings.units().format(dewPointF, 0)),
                    arg("coil", settings.units().format(units.climate().dehumidifier().dryDewPointF(), 0))));
        }
    }

    /**
     * {@code /hvac frost <percent>} (BETA 1.0 Pilot 7): sets the frost of the
     * freezer being looked at, to see it, or to test a server's settings.
     */
    private void frost(Player player, String[] args) {
        if (args.length < 2) throw new UserError("command.frost.usage", arg("usage", "/hvac frost <0-100>"));
        int percent;
        try {
            percent = Integer.parseInt(args[1].replace("%", ""));
        } catch (NumberFormatException notANumber) {
            throw new UserError("command.frost.usage-number", arg("usage", "/hvac frost <0-100>"));
        }
        Block block = target(player);
        if (block == null) return;
        if (!(registry.refrigeratorAt(BlockKey.of(block.getLocation())) instanceof Freezer freezer))
            throw new UserError("command.frost.look");
        freezer.setFrost(Math.max(0, Math.min(100, percent)) / 100.0);
        registry.markDirty();
        plugin.frost().show(freezer);
        displays.refreshClimateUnit(freezer, true);
        player.sendMessage(Lang.message(freezer.autoDefrost() && freezer.frost() >= units.climate().refrigeration().autoDefrostAt()
                ? "command.frost.set-defrosts" : "command.frost.set", arg("name", freezer.label()),
                arg("frost", Math.round(freezer.frost() * 100))));
    }

    /** A fridge or freezer (BETA 1.0 Pilot 7): its inside, setpoint, what it's doing, and the room it warms. */
    private void showRefrigerator(Player player, Refrigerator refrigerator) {
        String room = refrigerator.servedSystem() == null ? Lang.text("command.fridge.room-outside")
                : Lang.text("command.fridge.room-system", arg("system",
                SystemNames.label(registry, refrigerator.servedSystem())));
        double inside = refrigerator.insideF();
        String unmeasured = Lang.text("command.fridge.unmeasured");
        player.sendMessage(Lang.message("command.fridge.title", arg("kind", refrigerator.kind().displayName()),
                arg("name", refrigerator.label()), arg("room", room)));
        player.sendMessage(Lang.message(refrigerator instanceof FridgeFreezer ? "command.fridge.freezer-below"
                        : "command.fridge.inside",
                arg("inside", Double.isFinite(inside) ? settings.units().format(inside, 1) : unmeasured),
                arg("setpoint", settings.units().format(refrigerator.setpoint(), 0)),
                arg("activity", DisplayService.word(refrigerator.activity()))));
        if (refrigerator instanceof MiniFridge mini) {
            double chiller = mini.chillerF();
            player.sendMessage(Lang.message("command.fridge.chiller", arg("chiller", Double.isFinite(chiller)
                    ? settings.units().format(chiller, 1) : unmeasured)));
        }
        if (refrigerator instanceof FridgeFreezer both) {
            double fridge = both.fridgeInsideF();
            player.sendMessage(Lang.message(both.fridgeDoorOpen() ? "command.fridge.top-open" : "command.fridge.top",
                    arg("inside", Double.isFinite(fridge) ? settings.units().format(fridge, 1) : unmeasured),
                    arg("setpoint", settings.units().format(both.fridgeSetpoint(), 0))));
        }
        if (refrigerator instanceof Freezer freezer)
            player.sendMessage(Lang.message(freezer.defrosting() ? "command.fridge.frost-defrosting" : "command.fridge.frost",
                    arg("frost", Math.round(freezer.frost() * 100)),
                    arg("defrost", Lang.message(!freezer.hasAutoDefrost() ? "command.fridge.defrost.by-hand"
                            : freezer.autoDefrost() ? "command.fridge.defrost.auto-on" : "command.fridge.defrost.auto-off"))));
    }

    private void showEquipment(Player player, EquipmentUnit unit) {
        player.sendMessage(Lang.message("command.equipment.info.title", arg("kind", unit.type().displayName()),
                arg("tier", tierName(unit.tier(), unit.type())), arg("system", systemOf(unit.system()))));
        player.sendMessage(Lang.message("command.equipment.info.state",
                arg("state", unit.running() ? unit.operatingMode().name() : "IDLE"),
                arg("rpm", String.format(Locale.US, "%.0f", unit.motorRpm())),
                arg("profile", unit.type().fixedSpeed() ? "FIXED" : registry.profileFor(unit.system()).name()),
                arg("output", Lang.text(unit.hasOwnedOutput() ? "command.equipment.info.owned" : "command.equipment.info.none")),
                arg("enabled", Lang.text(unit.enabled() ? "command.thermostat.info.enabled"
                        : "command.thermostat.info.disabled"))));
        if (ServerSwitches.deviceOff(DeviceKind.of(unit.type())))
            player.sendMessage(Lang.message("command.info.kind-off", arg("kinds", DeviceKind.of(unit.type()).plural())));
    }

    private Block target(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) player.sendMessage(Lang.message("command.look-at-block"));
        return block;
    }

    /** A mini thermostat goes on a button on a wall, like a real one. */
    private void requireWallButton(Block block) {
        if (!(block.getBlockData() instanceof org.bukkit.block.data.FaceAttachable attached)
                || attached.getAttachedFace() != org.bukkit.block.data.FaceAttachable.AttachedFace.WALL)
            throw new UserError("command.create.mini-on-wall");
    }

    private static void requireSign(Block block) {
        if (!(block.getState() instanceof Sign)) throw new UserError("command.look-at-sign");
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
        sender.sendMessage(Lang.message("command.needs-permission", arg("permission", ADMIN_PERMISSION)));
        return false;
    }

    /**
     * The help (BETA 1.0): a short index anyone sees, and for admins a page on
     * building ({@code build}) and one on running the server ({@code server}),
     * each short enough for one chat screen. Anything else gives the index.
     */
    static List<Component> helpPage(String page, boolean hud, boolean admin) {
        List<Component> lines = new ArrayList<>();
        String which = admin ? page.toLowerCase(Locale.ROOT) : "";
        switch (which) {
            case "build" -> {
                lines.add(Lang.message("command.help.build.title"));
                lines.add(help("/hvac create thermostat|poolthermostat <label> [target]", "command.help.build.create-thermostat"));
                lines.add(help("/hvac create heatpump|ac|furnace|boiler|poolheatpump [t1|t2|t3]",
                        "command.help.build.create-equipment"));
                lines.add(help("/hvac create thermometer|hygrometer|thermohygrometer|airmonitor|rpmmonitor"
                        + "|settingspanel|sensor-hub [label]", "command.help.build.create-sign"));
                lines.add(help("/hvac create dehumidifier|humidifier|purifier|fridge|freezer [label]",
                        "command.help.build.create-unit"));
                lines.add(help("/hvac create sensor|weatherstation [label]", "command.help.build.create-sensor"));
                lines.add(help("/hvac link-tool", "command.help.build.link-tool"));
                lines.add(help("/hvac tutorial [leave]", "command.help.build.tutorial"));
                lines.add(help("/hvac thermostat set|enable|disable", "command.help.build.thermostat"));
                lines.add(help("/hvac equipment enable|disable|tier", "command.help.build.equipment"));
                lines.add(help("/hvac rename <label>", "command.help.build.rename"));
                lines.add(help("/hvac remove [system]", "command.help.build.remove"));
            }
            case "server" -> {
                lines.add(Lang.message("command.help.server.title"));
                lines.add(help("/hvac quicksettings", "command.help.server.quicksettings"));
                lines.add(help("/hvac setup", "command.help.server.setup"));
                lines.add(help("/hvac addons", "command.help.server.addons"));
                lines.add(help("/hvac stats [sizes]", "command.help.server.stats"));
                lines.add(help("/hvac sync", "command.help.server.sync"));
                lines.add(help("/hvac update", "command.help.server.update"));
                lines.add(help("/hvac season set <season> [day] | next | pause | resume", "command.help.server.season"));
                lines.add(help("/hvac wind set <calm|breezy|windy|gale|auto>", "command.help.server.wind"));
                lines.add(help("/hvac hud sections", "command.help.server.hud-sections"));
                lines.add(help("/hvac registry [repair --force]", "command.help.server.registry"));
                lines.add(help("/hvac frost <0-100>", "command.help.server.frost"));
            }
            default -> {
                lines.add(Lang.message("command.help.index.title"));
                lines.add(help("/hvac temperature", "command.help.index.temperature"));
                if (hud) lines.add(help("/hvac hud [on|off]", "command.help.index.hud"));
                lines.add(help("/hvac info", "command.help.index.info"));
                lines.add(help("/hvac list", "command.help.index.list"));
                lines.add(help("/hvac season", "command.help.index.season"));
                lines.add(help("/hvac wind", "command.help.index.wind"));
                lines.add(help("/hvac forecast", "command.help.index.forecast"));
                if (admin) {
                    lines.add(help("/hvac help build", "command.help.index.build"));
                    lines.add(help("/hvac help server", "command.help.index.server"));
                }
            }
        }
        return lines;
    }

    private static Component help(String command, String what) {
        return Lang.message("command.help.line", arg("command", command), arg("what", Lang.text(what)));
    }

    /** The first word's completions: only what the sender can run. */
    static List<String> subcommands(boolean hud, boolean admin) {
        List<String> words = new ArrayList<>(List.of("help", "temperature", "list", "info", "season", "wind",
                "forecast"));
        if (hud) words.add("hud");
        if (admin) words.addAll(List.of("create", "thermostat", "equipment", "link-tool", "rename", "remove", "frost",
                "stats", "sync", "update", "quicksettings", "setup", "tutorial", "addons", "registry"));
        return words;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (args.length == 1)
            return filter(args[0], subcommands(sender.hasPermission(HUD_PERMISSION), admin).toArray(String[]::new));
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) return admin ? filter(args[1], "skip") : List.of();
        if (args.length == 2 && args[0].equalsIgnoreCase("tutorial")) return admin ? filter(args[1], "leave") : List.of();
        if (args[0].equalsIgnoreCase("season")) {
            if (args.length == 2) return admin ? filter(args[1], "set", "next", "pause", "resume") : List.of();
            if (args.length == 3 && args[1].equalsIgnoreCase("set"))
                return filter(args[2], "spring", "summer", "autumn", "winter");
            return filter(args[args.length - 1], plugin.getServer().getWorlds().stream()
                    .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                    .map(World::getName).toArray(String[]::new));
        }
        if (args[0].equalsIgnoreCase("wind")) {
            if (args.length == 2) return admin ? filter(args[1], "set") : List.of();
            if (args.length == 3 && args[1].equalsIgnoreCase("set"))
                return filter(args[2], "calm", "breezy", "windy", "gale", "auto");
            if (args.length == 4 && args[1].equalsIgnoreCase("set") && !args[2].equalsIgnoreCase("auto")) {
                List<String> directions = new ArrayList<>(filter(args[3], "n", "ne", "e", "se", "s", "sw", "w", "nw"));
                directions.addAll(filter(args[3], plugin.getServer().getWorlds().stream()
                        .filter(WindService::hasWind).map(World::getName).toArray(String[]::new)));
                return directions;
            }
            return filter(args[args.length - 1], plugin.getServer().getWorlds().stream()
                    .filter(WindService::hasWind).map(World::getName).toArray(String[]::new));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) return admin ? filter(args[1], "build", "server") : List.of();
        if (admin && args[0].equalsIgnoreCase("registry")) {
            if (args.length == 2) return filter(args[1], "repair");
            if (args.length == 3 && args[1].equalsIgnoreCase("repair")) return filter(args[2], "--force");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("hud"))
            return admin ? filter(args[1], "on", "off", "sections") : filter(args[1], "on", "off");
        if (!admin) return List.of();
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> filter(args[1], "thermostat", "poolthermostat", "heatpump",
                        "ac", "furnace", "boiler", "poolheatpump", "thermometer", "hygrometer",
                        "thermohygrometer", "airmonitor", "rpmmonitor", "settingspanel", "dehumidifier",
                        "humidifier", "purifier", "fridge", "deepfreezer", "refrigerator", "minifridge", "sensor", "sensor-hub",
                        "weatherstation");
                case "thermostat" -> filter(args[1], "set", "enable", "disable", "info");
                case "equipment" -> filter(args[1], "enable", "disable", "tier", "info");
                case "remove" -> filter(args[1], "system");
                case "frost" -> filter(args[1], "0", "25", "50", "75", "100");
                case "stats" -> filter(args[1], "sizes");
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

    /** A command typed wrong: its syntax, which stays English on every server. */
    private static UserError usage(String syntax) {
        return new UserError("command.usage", arg("usage", syntax));
    }

    private static Component green(String value) { return Component.text(value, NamedTextColor.GREEN); }
    private static Component red(String value) { return Component.text(value, NamedTextColor.RED); }
    private static Component yellow(String value) { return Component.text(value, NamedTextColor.YELLOW); }
    private static Component gray(String value) { return Component.text(value, NamedTextColor.GRAY); }
    private static Component white(String value) { return Component.text(value, NamedTextColor.WHITE); }
    private static Component gold(String value) { return Component.text(value, NamedTextColor.GOLD); }
    private static Component aqua(String value) { return Component.text(value, NamedTextColor.AQUA); }
}
