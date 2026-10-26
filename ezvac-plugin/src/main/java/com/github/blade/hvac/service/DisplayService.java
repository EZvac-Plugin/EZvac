package com.github.blade.hvac.service;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.simulation.EtaEstimator;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.ThermalModel;
import net.kyori.adventure.text.Component;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.block.HangingSign;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import java.util.*;
import java.util.logging.Level;

/** Renders all sign devices from authoritative state with write de-duplication. */
public final class DisplayService {
    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final AirflowService airflow;
    private final OutdoorTemperatureService outdoor;
    private final HvacSettings settings;
    private final ScreenService screens;
    private final Map<BlockKey, String> fingerprints = new HashMap<>();
    /**
     * The ETA forecast simulates up to 1800 thermal steps and is rendered only
     * as a m:ss string, so it is refreshed on the display cadence rather than
     * every controller cycle. Mode changes recompute immediately.
     */
    private final Map<BlockKey, CachedEta> etaCache = new HashMap<>();

    private record CachedEta(OperatingMode mode, boolean enabled, OptionalLong eta) {}

    public DisplayService(HvacPlugin plugin, HvacRegistry registry, AirflowService airflow,
                          OutdoorTemperatureService outdoor, HvacSettings settings, ScreenService screens) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.screens = Objects.requireNonNull(screens, "screens");
    }

    public ScreenService screens() { return screens; }

    /**
     * How far from a player, in chunks, signs keep being rewritten: four
     * chunks, 64 blocks and a little more, well past where sign text can be
     * read. Rewriting a sign snapshots its whole tile entity, and in profiles
     * it was EZvac's largest single cost.
     */
    static final int WATCH_RADIUS_CHUNKS = 4;

    /**
     * Rewrites the signs players could be looking at. Signs farther from
     * every player are skipped and keep the text they last showed; they
     * remember it, so the first pass after someone comes near sees it is out
     * of date and rewrites it. Nothing else depends on sign text: the
     * simulation, equipment, saving and readings carry on regardless.
     */
    public void refreshAll(boolean force) {
        if (force && screens.enabled()) screens.retainOnly(devicePositions());
        Map<UUID, LongOpenHashSet> watched = watchedChunks();
        if (watched.isEmpty()) return;
        for (Thermostat thermostat : registry.thermostats())
            if (watched(watched, thermostat.position())) refreshThermostat(thermostat, force);
        for (Thermometer thermometer : registry.thermometers())
            if (watched(watched, thermometer.position())) refreshThermometer(thermometer, force);
        for (RpmMonitor monitor : registry.rpmMonitors())
            if (watched(watched, monitor.position())) refreshRpmMonitor(monitor, force);
        for (SettingsPanel panel : registry.settingsPanels())
            if (watched(watched, panel.position())) refreshSettingsPanel(panel, force);
        for (ClimateUnit unit : registry.climateUnits())
            if (watched(watched, unit.position())) refreshClimateUnit(unit, force);
        for (SensorHub hub : registry.sensorHubs())
            if (watched(watched, hub.position())) refreshSensorHub(hub, force);
    }

    /** Every sign device, so the screens of devices that are gone can be taken down. */
    private Set<BlockKey> devicePositions() {
        Set<BlockKey> positions = new HashSet<>();
        registry.thermostats().forEach(device -> positions.add(device.position()));
        registry.thermometers().forEach(device -> positions.add(device.position()));
        registry.rpmMonitors().forEach(device -> positions.add(device.position()));
        registry.settingsPanels().forEach(device -> positions.add(device.position()));
        registry.climateUnits().forEach(device -> positions.add(device.position()));
        registry.sensorHubs().forEach(device -> positions.add(device.position()));
        return positions;
    }

    /** Turns the page of auto-flipping sensor hubs and RPM monitors; the next refresh draws it. */
    public void advanceAutoFlip(long elapsedTicks) {
        for (SensorHub hub : registry.sensorHubs()) hub.advanceAutoFlip(elapsedTicks);
        for (RpmMonitor monitor : registry.rpmMonitors())
            if (monitor.autoFlip() && monitor.system() != null)
                monitor.advanceAutoFlip(elapsedTicks, rowsOf(monitor).size());
    }

    /**
     * A sensor hub's sign: its label and page on the first line, then that
     * page's sensors, one to a line. A sensor in an unloaded chunk shows --.
     */
    public void refreshSensorHub(SensorHub hub, boolean force) {
        List<SensorReading> readings = new ArrayList<>();
        for (BlockKey key : hub.sensorsOnPage()) {
            Sensor sensor = registry.sensorAt(key);
            if (sensor != null) readings.add(read(sensor));
        }
        String pageText = " " + (hub.page() + 1) + "/" + hub.pages();
        String fingerprint = hub.label() + pageText + "|" + String.join("|",
                readings.stream().map(reading -> reading.line(SignText.SIGN_WIDTH)).toList());
        render(hub.position(), fingerprint, force, lines -> {
            lines.set(0, SensorLine.fit(hub.label(), lines.width() - SignText.width(pageText)) + pageText,
                    NamedTextColor.AQUA);
            if (hub.sensors().isEmpty()) {
                lines.set(1, "No sensors", NamedTextColor.GRAY);
                lines.set(2, "Link with", NamedTextColor.GRAY);
                lines.set(3, "the sensor-tool", NamedTextColor.GRAY);
                return;
            }
            for (int line = 0; line < SensorHub.PER_PAGE; line++)
                lines.set(line + 1, line < readings.size() ? readings.get(line).line(lines.width()) : "",
                        NamedTextColor.WHITE);
        }, () -> {
            // Up to five sensors fit on a screen at once; with more it pages along with the sign.
            boolean all = hub.sensors().size() <= ScreenText.LINES - 1;
            ScreenText screen = ScreenText.titled(hub.label(), all ? "" : "page" + pageText);
            if (hub.sensors().isEmpty()) return screen.line("No sensors yet:", NamedTextColor.GRAY)
                    .line("link with the", NamedTextColor.GRAY).line("sensor-tool", NamedTextColor.GRAY);
            for (BlockKey key : all ? hub.sensors() : hub.sensorsOnPage()) {
                Sensor sensor = registry.sensorAt(key);
                if (sensor != null) screen.line(read(sensor).line(ScreenText.WIDTH), NamedTextColor.WHITE);
            }
            return screen;
        });
    }

    /** What a sensor read, before it is fitted to a sign. */
    private record SensorReading(Sensor sensor, boolean loaded, double temperatureF, double humidity,
                                 String weather, TemperatureUnit units) {
        String line(int width) {
            return SensorLine.format(sensor.label(), sensor.reads(), loaded, temperatureF, humidity, weather, width,
                    units);
        }
    }

    /** Reads a sensor; never loads its chunk. */
    private SensorReading read(Sensor sensor) {
        BlockKey position = sensor.position();
        var location = position.isChunkLoaded() ? position.location() : null;
        TemperatureUnit units = settings.units();
        if (location == null) return new SensorReading(sensor, false, Double.NaN, Double.NaN, "", units);
        if (sensor.reads() == Sensor.Kind.WEATHER)
            return new SensorReading(sensor, true, Double.NaN, Double.NaN, weatherAt(location), units);
        AirflowService.TemperatureReading reading = airflow.temperatureAt(location);
        return new SensorReading(sensor, true, reading.localF(), reading.humidity(), "", units);
    }

    /** What a weather sensor shows when something above it keeps the rain off. */
    public static final String COVERED = "Covered";

    /**
     * What a weather sensor at this spot reports. Like a rain gauge it needs
     * open sky: anything above it that would keep rain off (a roof, glass,
     * leaves) makes it {@link #COVERED}. Otherwise the sky's weather, even
     * with EZvac's weather effects switched off.
     */
    public String weatherAt(Location location) {
        World world = location.getWorld();
        if (world == null) return COVERED;
        int top = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ(), HeightMap.MOTION_BLOCKING);
        if (top > location.getBlockY()) return COVERED;
        return outdoor.skyAt(location);
    }

    /** The chunks near each online player, per world. */
    private Map<UUID, LongOpenHashSet> watchedChunks() {
        Map<UUID, LongOpenHashSet> chunks = new HashMap<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location location = player.getLocation();
            if (location.getWorld() == null) continue;
            watchAround(chunks.computeIfAbsent(location.getWorld().getUID(), ignored -> new LongOpenHashSet()),
                    location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }
        return chunks;
    }

    static void watchAround(LongOpenHashSet chunks, int chunkX, int chunkZ) {
        for (int dx = -WATCH_RADIUS_CHUNKS; dx <= WATCH_RADIUS_CHUNKS; dx++)
            for (int dz = -WATCH_RADIUS_CHUNKS; dz <= WATCH_RADIUS_CHUNKS; dz++)
                chunks.add(HvacRegistry.cellKey(chunkX + dx, chunkZ + dz));
    }

    static boolean watched(Map<UUID, LongOpenHashSet> watched, BlockKey position) {
        LongOpenHashSet chunks = watched.get(position.worldId());
        return chunks != null && chunks.contains(HvacRegistry.cellKey(position.x() >> 4, position.z() >> 4));
    }

    public void invalidate(BlockKey position) {
        fingerprints.remove(position);
        etaCache.remove(position);
        screens.remove(position);
    }

    public void refreshThermostat(Thermostat thermostat, boolean force) {
        String mode = switch (thermostat.mode()) {
            case COOLING -> "COOL";
            case HEATING -> "HEAT";
            case IDLE -> thermostat.enabled() ? "IDLE" : "OFF";
        };
        List<EquipmentUnit> running = registry.equipmentFor(thermostat.system()).stream()
                .filter(unit -> unit.running() && unit.operatingMode() == thermostat.mode()).toList();
        double fixedEquivalent = running.stream().filter(unit -> unit.type() == EquipmentType.FURNACE)
                .mapToDouble(unit -> unit.operatingCapacity(settings)).sum();
        double variableEquivalent = running.stream().filter(unit -> unit.type() != EquipmentType.FURNACE)
                .mapToDouble(unit -> unit.operatingCapacity(settings)).sum();
        double capacity = ThermalModel.aggregateIndependentCapacity(fixedEquivalent,
                variableEquivalent, settings.maximumSystemCapacity());
        int percent = (int) Math.round(capacity * 100.0);
        List<EtaEstimator.UnitSnapshot> snapshots = running.stream()
                .map(unit -> new EtaEstimator.UnitSnapshot(unit.type(), unit.motorRpm())).toList();
        OptionalLong eta = estimateEta(thermostat, snapshots, force);
        String footer = !thermostat.enabled() ? "System off"
                : thermostat.mode() == OperatingMode.IDLE ? "Ready"
                : eta.isPresent() ? "ETA " + formatEta(eta) : "ETA --";
        // Since 3.0 the last line counts the units at work where the group's name used to be.
        List<EquipmentUnit> units = registry.equipmentFor(thermostat.system());
        long working = units.stream().filter(EquipmentUnit::running).count();
        String linked = units.isEmpty() ? footer : footer + " | " + working + "/" + units.size();
        String fingerprint = String.format(Locale.US, "%.1f|%.1f|%s|%d|%s|%s",
                thermostat.roomF(), thermostat.targetF(), mode, percent, linked, thermostat.id());
        render(thermostat.position(), fingerprint, force, lines -> {
            lines.set(0, lines.titled(thermostat.id()), NamedTextColor.AQUA);
            NamedTextColor color = thermostat.mode() == OperatingMode.COOLING ? NamedTextColor.AQUA
                    : thermostat.mode() == OperatingMode.HEATING ? NamedTextColor.GOLD : NamedTextColor.GRAY;
            lines.set(1, temp(thermostat.roomF()) + " | " + mode, color);
            String setpoint = "Set " + temp(thermostat.targetF());
            if (thermostat.mode() != OperatingMode.IDLE) setpoint += " | " + percent + "%";
            lines.set(2, setpoint, NamedTextColor.WHITE);
            lines.set(3, linked,
                    eta.isPresent() && thermostat.mode() != OperatingMode.IDLE
                            ? NamedTextColor.GREEN : NamedTextColor.GRAY);
        }, () -> {
            ScreenText screen = ScreenText.titled(thermostat.id(), thermostat.pool() ? "Pool" : "")
                    .big(degrees(thermostat.roomF()));
            String target = degrees(thermostat.targetF());
            if (!thermostat.enabled()) screen.line("System off · set " + target, NamedTextColor.RED);
            else switch (thermostat.mode()) {
                case COOLING -> screen.line("❄ Cooling to " + target, NamedTextColor.AQUA)
                        .line(ScreenText.bar(capacity, 10, NamedTextColor.AQUA));
                case HEATING -> screen.line("♨ Heating to " + target, NamedTextColor.GOLD)
                        .line(ScreenText.bar(capacity, 10, NamedTextColor.GOLD));
                case IDLE -> screen.line("● Holding " + target, NamedTextColor.GREEN);
            }
            String humidity = thermostat.hasRoomDewPoint() ? Math.round(Psychrometrics.relativeHumidity(
                    thermostat.roomF(), thermostat.roomDewPointF())) + "% RH" : "";
            String status = thermostat.enabled() && thermostat.mode() != OperatingMode.IDLE
                    ? "ETA " + formatEta(eta) : "";
            screen.line(String.join(" · ", nonEmpty(status, humidity)), NamedTextColor.GRAY);
            screen.line(String.join(" · ", nonEmpty("Out " + degrees(thermostat.lastOutdoorF()),
                    season(thermostat.position().worldId()))), NamedTextColor.GRAY);
            return screen;
        });
    }

    /** {@code 72.4°F}, the way screens show temperatures. */
    private String degrees(double fahrenheit) {
        return settings.units().degrees(fahrenheit);
    }

    /** The season in a world, or empty where there are none. */
    private String season(UUID world) {
        SeasonService seasons = plugin.seasons();
        if (seasons == null || !seasons.settings().enabled() || !seasons.tracks(world)) return "";
        return seasons.season(world).displayName();
    }

    private static List<String> nonEmpty(String... parts) {
        return Arrays.stream(parts).filter(part -> part != null && !part.isEmpty()).toList();
    }

    /** Reuses the last forecast unless forced or the operating state changed. */
    private OptionalLong estimateEta(Thermostat thermostat,
                                     List<EtaEstimator.UnitSnapshot> snapshots, boolean force) {
        CachedEta cached = etaCache.get(thermostat.position());
        if (!force && cached != null && cached.mode() == thermostat.mode()
                && cached.enabled() == thermostat.enabled()) {
            return cached.eta();
        }
        OperatingProfile profile = registry.profileFor(thermostat.system());
        OptionalLong eta = EtaEstimator.estimateTicks(thermostat.roomF(), thermostat.targetF(),
                outdoor.driftBaselineFor(thermostat), thermostat.mode(), snapshots, profile, settings);
        etaCache.put(thermostat.position(), new CachedEta(thermostat.mode(), thermostat.enabled(), eta));
        return eta;
    }

    private void refreshThermometer(Thermometer thermometer, boolean force) {
        var location = thermometer.position().location();
        if (location == null) return;
        AirflowService.TemperatureReading reading = airflow.temperatureAt(location);
        String inside = percent(reading.humidity());
        String outside = thermometer.reads() == Thermometer.Reads.TEMPERATURE ? ""
                : percent(outdoor.climateAt(location).humidity());
        String fingerprint = String.format(Locale.US, "%.1f|%.1f|%.1f|%s|%s|%s|%s", reading.localF(),
                reading.outdoorF(), reading.feelsLikeF(), inside, outside, thermometer.reads(), thermometer.label());
        render(thermometer.position(), fingerprint, force, lines -> {
            lines.set(0, signTitle(thermometer), NamedTextColor.AQUA);
            switch (thermometer.reads()) {
                case TEMPERATURE -> {
                    lines.set(1, "Thermometer", NamedTextColor.WHITE);
                    lines.set(2, "Inside: " + temp(reading.localF()),
                            NamedTextColor.YELLOW);
                    lines.set(3, "Outside: " + temp(reading.outdoorF()),
                            NamedTextColor.GRAY);
                }
                case HUMIDITY -> {
                    lines.set(1, "Hygrometer", NamedTextColor.WHITE);
                    lines.set(2, "Inside: " + inside, NamedTextColor.AQUA);
                    lines.set(3, "Outside: " + outside, NamedTextColor.GRAY);
                }
                case BOTH -> {
                    lines.set(1, "In " + temp(reading.localF()) + " " + inside,
                            NamedTextColor.YELLOW);
                    lines.set(2, "Feels " + temp(reading.feelsLikeF()),
                            NamedTextColor.WHITE);
                    lines.set(3, "Out " + temp(reading.outdoorF()) + " " + outside,
                            NamedTextColor.GRAY);
                }
            }
        }, () -> {
            ScreenText screen = ScreenText.titled(thermometer.label(), "");
            String sky = String.join(" · ", nonEmpty(outdoor.climateAt(location).weather(),
                    season(thermometer.position().worldId())));
            switch (thermometer.reads()) {
                case TEMPERATURE -> screen.big(degrees(reading.localF()))
                        .line("Feels " + degrees(reading.feelsLikeF()), NamedTextColor.YELLOW)
                        .line("Out " + degrees(reading.outdoorF()), NamedTextColor.GRAY);
                case HUMIDITY -> screen.big(inside).line("Humidity", NamedTextColor.WHITE)
                        .line("Out " + outside, NamedTextColor.GRAY);
                case BOTH -> screen.big(degrees(reading.localF()))
                        .line("Humidity " + inside, NamedTextColor.WHITE)
                        .line("Feels " + degrees(reading.feelsLikeF()), NamedTextColor.YELLOW)
                        .line("Out " + degrees(reading.outdoorF()) + " · " + outside, NamedTextColor.GRAY);
            }
            if (!sky.isEmpty()) screen.line(sky, NamedTextColor.GRAY);
            return screen;
        });
    }

    /**
     * A sensor's label as its sign shows it. The default thermo-hygrometer
     * label is one letter too long for a sign line, so it is shortened there;
     * the stored label, and any label a player chose, stay as they are.
     */
    static String signTitle(Thermometer sensor) {
        return sensor.label().equalsIgnoreCase("Thermohygrometer") ? "T. Hygrometer" : sensor.label();
    }

    /** "45%", or "--%" where there is no air to measure (a sign in water). */
    private static String percent(double humidity) {
        return Double.isFinite(humidity) ? Math.round(humidity) + "%" : "--%";
    }

    private List<RpmMonitor.Row> rowsOf(RpmMonitor monitor) {
        return monitor.system() == null ? List.of() : RpmMonitor.rows(registry.equipmentFor(monitor.system()));
    }

    /**
     * An RPM monitor's sign: its label (and page) on the first line, then a
     * row for each kind of equipment in its system, three to a page.
     */
    public void refreshRpmMonitor(RpmMonitor monitor, boolean force) {
        List<RpmMonitor.Row> rows = rowsOf(monitor);
        int pages = RpmMonitor.pages(rows.size());
        String pageText = pages > 1 ? " " + (monitor.page(rows.size()) + 1) + "/" + pages : "";
        List<RpmMonitor.Row> shown = monitor.onPage(rows);
        StringBuilder fingerprint = new StringBuilder(monitor.label()).append(pageText)
                .append(monitor.system() == null ? "|unlinked" : "|linked");
        for (RpmMonitor.Row row : rows)
            fingerprint.append(String.format(Locale.US, "|%s %d %d %.0f", row.type(), row.units(), row.running(), row.rpm()));
        render(monitor.position(), fingerprint.toString(), force, lines -> {
            lines.set(0, SignText.fit(monitor.label(), lines.width() - SignText.width(pageText)) + pageText,
                    NamedTextColor.AQUA);
            if (monitor.system() == null) {
                lines.set(1, "Not linked", NamedTextColor.GRAY);
                lines.set(2, "Link with", NamedTextColor.GRAY);
                lines.set(3, "the link-tool", NamedTextColor.GRAY);
                return;
            }
            if (rows.isEmpty()) {
                lines.set(1, "No equipment", NamedTextColor.RED);
                return;
            }
            for (int line = 0; line < Pager.PER_PAGE; line++) {
                if (line >= shown.size()) { lines.set(line + 1, "", NamedTextColor.WHITE); continue; }
                RpmMonitor.Row row = shown.get(line);
                lines.set(line + 1, rowText(row, lines.width()),
                        row.running() > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY);
            }
        }, () -> {
            ScreenText screen = ScreenText.titled(monitor.label(),
                    monitor.system() == null ? "" : SystemNames.label(registry, monitor.system()));
            if (monitor.system() == null) return screen.line("Not linked:", NamedTextColor.GRAY)
                    .line("link with the", NamedTextColor.GRAY).line("link-tool", NamedTextColor.GRAY);
            if (rows.isEmpty()) return screen.line("No equipment", NamedTextColor.RED);
            // A screen holds five rows; with more it pages along with the sign.
            for (RpmMonitor.Row row : rows.size() <= ScreenText.LINES - 1 ? rows : shown)
                screen.line(rowText(row, ScreenText.WIDTH), row.running() > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY);
            return screen;
        });
    }

    /**
     * "Heat pump ×4 2,950": the kind, how many when more than one, and the
     * average speed of those running ("off" when none is). A row too wide
     * for the sign shortens the kind first.
     */
    static String rowText(RpmMonitor.Row row, int width) {
        String count = row.units() > 1 ? " ×" + row.units() : "";
        String state = row.running() == 0 ? "off" : String.format(Locale.US, "%,.0f", row.rpm());
        String full = row.type().displayName() + count + " " + state;
        if (SignText.width(full) <= width) return full;
        return SignText.fit(shortName(row.type()) + count + " " + state, width);
    }

    private static String shortName(EquipmentType type) {
        return switch (type) {
            case HEAT_PUMP -> "HP";
            case AIR_CONDITIONER -> "AC";
            case FURNACE -> "Furnace";
            case BOILER -> "Boiler";
            case POOL_HEAT_PUMP -> "Pool HP";
        };
    }

    public void refreshSettingsPanel(SettingsPanel panel, boolean force) {
        String system = panel.system() == null ? "Not linked" : "System: " + SystemNames.label(registry, panel.system());
        String fingerprint = panel.label() + "|" + system + "|" + panel.profile();
        NamedTextColor profileColor = switch (panel.profile()) {
            case ECO -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.AQUA;
            case TURBO -> NamedTextColor.GOLD;
        };
        render(panel.position(), fingerprint, force, lines -> {
            lines.set(0, lines.titled(panel.label()), NamedTextColor.AQUA);
            lines.set(1, system, panel.system() == null ? NamedTextColor.GRAY : NamedTextColor.WHITE);
            lines.set(2, "[ " + panel.profile().name() + " ]", profileColor);
            lines.set(3, "Click > | Sneak <", NamedTextColor.GRAY);
        }, () -> ScreenText.titled(panel.label(), panel.system() == null ? "Not linked"
                        : SystemNames.label(registry, panel.system()))
                .big(panel.profile().name())
                .line("Click: next · Sneak: back", NamedTextColor.GRAY));
    }

    /** A dehumidifier's last line is its tank; a humidifier, being plumbed, just says what it is. */
    public void refreshClimateUnit(ClimateUnit unit, boolean force) {
        double humidity = unit.lastHumidity();
        String reading = (Double.isFinite(humidity) ? Math.round(humidity) + "%" : "--%")
                + " > " + unit.setpoint() + "%";
        ClimateUnit.State state = unit.state();
        // Running in air it can't dry: say so rather than look like it works.
        boolean tooCold = state == ClimateUnit.State.RUNNING && unit instanceof Dehumidifier dry && dry.tooCold();
        String stateText = tooCold ? "Air too cold" : switch (state) {
            case OFF -> "OFF";
            case IDLE -> "Idle";
            case RUNNING -> "Running";
            case EMPTYING -> "Emptying...";
        };
        NamedTextColor stateColor = tooCold ? NamedTextColor.GOLD : switch (state) {
            case OFF -> NamedTextColor.RED;
            case IDLE -> NamedTextColor.GRAY;
            case RUNNING -> NamedTextColor.GREEN;
            case EMPTYING -> NamedTextColor.AQUA;
        };
        String footer = unit instanceof Dehumidifier dehumidifier
                ? "Tank " + Math.round(dehumidifier.tank() * 100) + "%" : unit.kind().displayName();
        String fingerprint = unit.label() + "|" + reading + "|" + stateText + "|" + footer;
        render(unit.position(), fingerprint, force, lines -> {
            lines.set(0, lines.titled(unit.label()), NamedTextColor.AQUA);
            lines.set(1, reading, NamedTextColor.WHITE);
            lines.set(2, stateText, stateColor);
            lines.set(3, footer, NamedTextColor.GRAY);
        }, () -> {
            ScreenText screen = ScreenText.titled(unit.label(), unit.kind().displayName())
                    .big(Double.isFinite(humidity) ? Math.round(humidity) + "%" : "--%")
                    .line("Target " + unit.setpoint() + "%", NamedTextColor.WHITE)
                    .line("● " + stateText, stateColor);
            if (unit instanceof Dehumidifier dehumidifier)
                screen.line(Component.text("Tank ", NamedTextColor.GRAY)
                        .append(ScreenText.bar(dehumidifier.tank(), 8, NamedTextColor.AQUA)));
            return screen;
        });
    }

    /** A temperature as a sign shows it: {@code 72.4 F}, or {@code 22.4 C} with units: C. */
    private String temp(double fahrenheit) {
        return settings.units().format(fahrenheit);
    }

    /** A sign's front, written line by line; every line is cut to what the sign can show. */
    record Lines(SignSide side, int width) {
        void set(int line, String text, NamedTextColor color) {
            side.line(line, Component.text(SignText.fit(text, width), color));
        }

        /** {@code EZvac | label}, or the label alone when both don't fit. */
        String titled(String label) {
            return SignText.titled(label, width);
        }
    }

    /**
     * Writes a sign, and its screen where screens are on. The screen is
     * redrawn with the sign, on every forced refresh (for what only the screen
     * shows), and whenever it is missing: a chunk unloading takes screens away,
     * and this puts them back. Its text is only sent when it changed.
     */
    private void render(BlockKey position, String fingerprint, boolean force,
                        java.util.function.Consumer<Lines> writer, java.util.function.Supplier<ScreenText> screen) {
        if (!position.isChunkLoaded()) return;
        if (screens.enabled()) {
            var block = position.location() == null ? null : position.location().getBlock();
            if (block != null && org.bukkit.Tag.ALL_SIGNS.isTagged(block.getType())) {
                if (force || !fingerprint.equals(fingerprints.get(position)) || !screens.showing(position, block)) {
                    ScreenText text = screen.get();
                    screens.show(position, block, text.text(), text.lines());
                }
            } else {
                screens.remove(position);
            }
        }
        if (!force && fingerprint.equals(fingerprints.get(position))) return;
        var location = position.location();
        if (location == null || !(location.getBlock().getState() instanceof Sign sign)) return;
        try {
            writer.accept(new Lines(sign.getSide(Side.FRONT),
                    sign instanceof HangingSign ? SignText.HANGING_SIGN_WIDTH : SignText.SIGN_WIDTH));
            sign.update(false, false);
            fingerprints.put(position, fingerprint);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not refresh HVAC display at " + position, exception);
        }
    }

    private static String formatEta(OptionalLong eta) {
        if (eta.isEmpty()) return "--";
        long seconds = Math.max(0L, (eta.getAsLong() + 19L) / 20L);
        if (seconds == 0L) return "now";
        long minutes = seconds / 60L;
        if (minutes >= 100L) return "99m+";
        return String.format(Locale.US, "%d:%02d", minutes, seconds % 60L);
    }
}
