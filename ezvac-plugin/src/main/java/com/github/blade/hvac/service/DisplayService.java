package com.github.blade.hvac.service;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.simulation.AirQualityIndex;
import com.github.blade.hvac.simulation.AirQualityModel;
import com.github.blade.hvac.simulation.EtaEstimator;
import com.github.blade.hvac.simulation.Forecast;
import com.github.blade.hvac.simulation.PerformanceModel;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.ThermalModel;
import net.kyori.adventure.text.Component;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import java.util.*;
import java.util.logging.Level;

/**
 * Draws every device's screen from authoritative state, sending only what
 * changed. Screens are how devices show since BETA 1.0 Pilot 4: the sign a
 * device is made on stays behind its screen, blank, to be clicked.
 */
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
    /**
     * Fresh ETA forecasts per server tick at most (BETA 1.0). Many systems
     * changing mode at once (a device kind switched off, or a thousand
     * identical rooms reaching their target together) otherwise forecast all
     * in one tick; past this, a screen keeps its last ETA until its next refresh.
     */
    static final int ETA_FORECASTS_PER_TICK = 64;
    private int etaTick = Integer.MIN_VALUE;
    private int etaForecasts;

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

    private WeatherStationService stations;

    public void setWeatherStations(WeatherStationService stations) { this.stations = stations; }

    /**
     * How far from a player, in chunks, screens keep being redrawn: four
     * chunks, 64 blocks and a little more, well past where a screen can be
     * read. Working out what each device shows was, with the old sign text,
     * EZvac's largest single cost.
     */
    static final int WATCH_RADIUS_CHUNKS = 4;

    /**
     * Redraws the screens players could be looking at. Devices farther from
     * every player are skipped and keep what they last showed; they
     * remember it, so the first pass after someone comes near sees it is out
     * of date and redraws it. Nothing else depends on a screen: the
     * simulation, equipment, saving and readings carry on regardless.
     */
    public void refreshAll(boolean force) {
        if (force) screens.retainOnly(devicePositions());
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
        for (WeatherStation station : registry.weatherStations())
            if (watched(watched, station.position())) refreshWeatherStation(station, force);
    }

    /** Every device with a screen, so the screens of devices that are gone can be taken down. */
    private Set<BlockKey> devicePositions() {
        Set<BlockKey> positions = new HashSet<>();
        registry.thermostats().forEach(device -> positions.add(device.position()));
        registry.thermometers().forEach(device -> positions.add(device.position()));
        registry.rpmMonitors().forEach(device -> positions.add(device.position()));
        registry.settingsPanels().forEach(device -> positions.add(device.position()));
        registry.climateUnits().forEach(device -> positions.add(device.position()));
        registry.sensorHubs().forEach(device -> positions.add(device.position()));
        registry.weatherStations().forEach(device -> positions.add(device.position()));
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
     * A sensor hub's screen: its label and page in the header, then that
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
                readings.stream().map(reading -> reading.line(ScreenText.WIDTH)).toList());
        render(hub.position(), fingerprint, force, () -> {
            // Up to six sensors fit on a screen at once; with more it pages.
            boolean all = hub.sensors().size() <= ScreenText.ROWS;
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

    /** A weather station's header colour and glyph, by today's sky (BETA 1.0). */
    static ScreenText.Tone tone(Forecast.Sky sky) {
        return switch (sky) {
            case CLEAR -> new ScreenText.Tone(0x1F5F9F, "☀");
            case RAIN -> new ScreenText.Tone(0x34506B, "☂");
            case SNOW -> new ScreenText.Tone(0x4F6A85, "❄");
            case THUNDER -> new ScreenText.Tone(0x4B3A6B, "⚡");
        };
    }

    /**
     * A weather station's screen (BETA 1.0): the outdoor temperature of its
     * area big, today's sky, high and low and wind, then what is known for
     * certain today or else tomorrow's sky. Off, damaged or switched off
     * server-wide, it says so instead.
     */
    public void refreshWeatherStation(WeatherStation station, boolean force) {
        if (stations == null) return;
        TemperatureUnit units = settings.units();
        boolean serverOff = !WeatherStationService.switchedOn();
        List<Forecast.Day> days = serverOff || !station.enabled() || !station.intact() ? List.of()
                : stations.forecast(station);
        double nowF = stations.nowF(station);
        String state = serverOff ? OFF_SERVER_WIDE : !station.enabled() ? "Off" : !station.intact() ? "Damaged"
                : days.isEmpty() ? "Measuring" : "";
        Forecast.Day today = days.isEmpty() ? null : days.get(0);
        Forecast.Day tomorrow = days.size() > 1 ? days.get(1) : null;
        // Today's wind and where it blows from, then the air (BETA 1.0 Pilot 3); the AQI gives way first.
        String windLine = today == null || today.wind() == null ? ""
                : WindText.sign(today.windMs(), today.wind().fromDegrees(), outdoor.climate().wind(), units);
        AirQualityModel.Air air = today == null ? null : stations.airNow(station);
        String airLine = air == null ? "" : AirText.aqi(air);
        String conditions = windLine.isEmpty() ? airLine : airLine.isEmpty() ? windLine : windLine + " · " + airLine;
        String fingerprint = String.join("|", station.label(), state, StationText.temperature(nowF, units),
                today == null ? "" : StationText.sky(today, stations.snowDay(station, today)) + StationText.highLow(today, units) + today.note(),
                tomorrow == null ? "" : StationText.sky(tomorrow, stations.snowDay(station, tomorrow)), conditions);
        render(station.position(), fingerprint, force, () -> {
            // No "belongs to" tag: the label needs the room, and the sky's glyph says what it is.
            ScreenText screen = ScreenText.titled(station.label(), "");
            if (today == null) {
                if (!state.equals("Measuring")) screen.tone(state.equals("Damaged") ? ScreenText.Tone.ALERT
                        : ScreenText.Tone.OFF);
                screen.line(state, state.equals("Damaged") ? NamedTextColor.GOLD : NamedTextColor.GRAY);
                if (state.equals("Damaged")) screen.line("Rebuild its tower", NamedTextColor.GRAY);
                return screen;
            }
            screen.tone(tone(today.sky())).big(StationText.temperature(nowF, units))
                    .line(StationText.sky(today, stations.snowDay(station, today)), NamedTextColor.WHITE)
                    .line(StationText.highLow(today, units), NamedTextColor.GRAY);
            if (!conditions.isEmpty()) screen.line(conditions, NamedTextColor.GRAY);
            if (!today.note().isEmpty()) screen.line(today.note(), NamedTextColor.GOLD);
            else if (tomorrow != null) screen.line(StationText.tomorrow(tomorrow, stations.snowDay(station, tomorrow)), NamedTextColor.AQUA);
            return screen;
        });
    }

    /** What a sensor read, before it is fitted to a line. */
    private record SensorReading(Sensor sensor, boolean loaded, double temperatureF, double humidity,
                                 String weather, TemperatureUnit units, String shorter) {
        SensorReading(Sensor sensor, boolean loaded, double temperatureF, double humidity, String weather,
                      TemperatureUnit units) {
            this(sensor, loaded, temperatureF, humidity, weather, units, null);
        }

        /** Its line; with {@code shorter} (a wind sensor's, without direction) where the full one would cut the label. */
        String line(int width) {
            String line = SensorLine.format(sensor.label(), sensor.reads(), loaded, temperatureF, humidity, weather,
                    width, units);
            if (shorter == null || line.startsWith(sensor.label() + " ")) return line;
            return SensorLine.format(sensor.label(), sensor.reads(), loaded, temperatureF, humidity, shorter, width,
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
        if (sensor.reads() == Sensor.Kind.AIR)
            return new SensorReading(sensor, true, Double.NaN, Double.NaN,
                    airSettings().enabled() ? AirText.aqi(reading.air()) : "Off", units);
        if (sensor.reads() == Sensor.Kind.WIND)
            return new SensorReading(sensor, true, Double.NaN, Double.NaN,
                    WindText.sign(reading.windMs(), reading.windFromDegrees(), outdoor.climate().wind(), units), units,
                    WindText.sign(reading.windMs(), outdoor.climate().wind(), units));
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
        if (thermostat.mini()) {
            refreshMini(thermostat, force);
            return;
        }
        String mode = switch (thermostat.mode()) {
            case COOLING -> "COOL";
            case HEATING -> "HEAT";
            case IDLE -> thermostat.enabled() ? "IDLE" : "OFF";
        };
        // Its units are switched off server-wide (BETA 1.0): say so, not "Ready" or "Holding".
        OperatingMode waiting = thermostat.enabled() ? thermostat.waitingOnUnits() : null;
        List<EquipmentUnit> running = registry.equipmentFor(thermostat.system()).stream()
                .filter(unit -> unit.running() && unit.operatingMode() == thermostat.mode()).toList();
        double fixedEquivalent = running.stream().filter(unit -> unit.type().fixedSpeed())
                .mapToDouble(unit -> unit.operatingCapacity(settings)).sum();
        double variableEquivalent = running.stream().filter(unit -> !unit.type().fixedSpeed())
                .mapToDouble(unit -> unit.operatingCapacity(settings)).sum();
        // The meter is the system's load (3.1): what it delivers now as a share of
        // what its units could deliver flat out. It used to be raw capacity, which
        // read 141% for two units and 112% for one Premium unit.
        double fullFixed = 0.0;
        double fullVariable = 0.0;
        OperatingProfile profile = registry.profileFor(thermostat.system());
        for (EquipmentUnit unit : registry.equipmentFor(thermostat.system())) {
            if (!unit.enabled() || !unit.type().supports(thermostat.mode())) continue;
            double full = PerformanceModel.fullCapacity(unit.type(), unit.tier(), profile, settings);
            if (unit.type().fixedSpeed()) fullFixed += full;
            else fullVariable += full;
        }
        double sizeFactor = plugin.controller() == null ? Double.NaN : plugin.controller().sizeFactor(thermostat.system());
        double load = PerformanceModel.load(fixedEquivalent, variableEquivalent, fullFixed, fullVariable, sizeFactor,
                settings);
        int percent = (int) Math.round(load * 100.0);
        List<EtaEstimator.UnitSnapshot> snapshots = running.stream()
                .map(unit -> new EtaEstimator.UnitSnapshot(unit.type(), unit.motorRpm(), unit.tier())).toList();
        OptionalLong eta = estimateEta(thermostat, snapshots, force);
        String footer = !thermostat.enabled() ? "System off"
                : waiting != null ? UNITS_OFF
                : thermostat.mode() == OperatingMode.IDLE ? "Ready"
                : eta.isPresent() ? "ETA " + formatEta(eta) : "ETA --";
        // Since 3.0 the last line counts the units at work where the group's name used to be.
        List<EquipmentUnit> units = registry.equipmentFor(thermostat.system());
        long working = units.stream().filter(EquipmentUnit::running).count();
        String linked = units.isEmpty() ? footer : footer + " | " + working + "/" + units.size();
        // What needs looking at (BETA 1.0 Pilot 2): counted beside a ⚠, and what keeps it from starting said.
        // Undersized is one of them: its detail moved from the screen into the thermostat's menu.
        java.util.Set<Attention> attention = thermostat.attention();
        String blocked = !thermostat.enabled() || waiting != null ? "" : blockedLine(attention, thermostat.pool());
        String fingerprint = String.format(Locale.US, "%.1f|%.1f|%s|%d|%s|%s|%s",
                thermostat.roomF(), thermostat.targetF(), mode, percent, linked, thermostat.id(), attention);
        render(thermostat.position(), fingerprint, force, () -> {
            ScreenText screen = ScreenText.titled(thermostat.id(), thermostat.pool() ? "Pool" : "")
                    .big(degrees(thermostat.roomF())).attention(attention.size());
            String target = degrees(thermostat.targetF());
            if (!thermostat.enabled())
                screen.tone(ScreenText.Tone.OFF).line("Off · set " + target, NamedTextColor.GRAY);
            else if (!blocked.isEmpty())
                screen.tone(ScreenText.Tone.OFF).line("Set " + target, NamedTextColor.GRAY)
                        .line(blocked, NamedTextColor.RED);
            else if (waiting != null)
                screen.tone(ScreenText.Tone.OFF).line(waitingLine(waiting, target),
                        waiting == OperatingMode.HEATING ? NamedTextColor.GOLD : NamedTextColor.AQUA)
                        .line(UNITS_OFF, NamedTextColor.RED);
            else switch (thermostat.mode()) {
                case COOLING -> screen.tone(ScreenText.Tone.COOLING).line("Cooling to " + target, NamedTextColor.AQUA)
                        .line(ScreenText.bar(load, 10, NamedTextColor.AQUA));
                case HEATING -> screen.tone(ScreenText.Tone.HEATING).line("Heating to " + target, NamedTextColor.GOLD)
                        .line(ScreenText.bar(load, 10, NamedTextColor.GOLD));
                case IDLE -> screen.tone(ScreenText.Tone.HOLDING).line("Holding " + target, NamedTextColor.GREEN);
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
        int tick = plugin.getServer().getCurrentTick();
        if (tick != etaTick) {
            etaTick = tick;
            etaForecasts = 0;
        }
        if (++etaForecasts > ETA_FORECASTS_PER_TICK) return cached == null ? OptionalLong.empty() : cached.eta();
        OperatingProfile profile = registry.profileFor(thermostat.system());
        // A windy day makes the room leakier, as the controller steps it (3.2); a pool's water takes none.
        HvacController controller = plugin.controller();
        double leak = thermostat.pool() ? 1.0
                : outdoor.climate().wind().infiltrationFactor(outdoor.cachedClimateFor(thermostat).windMs())
                * (controller == null ? 1.0 : controller.openness(thermostat.system()));
        OptionalLong eta = EtaEstimator.estimateTicks(thermostat.roomF(), thermostat.targetF(),
                outdoor.driftBaselineFor(thermostat), thermostat.mode(), snapshots, profile, settings, leak,
                controller == null ? Double.NaN : controller.sizeFactor(thermostat.system()));
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
        boolean airOn = airSettings().enabled();
        String airLine = airOn ? AirText.signLine(reading.air()) : "Air quality off";
        String pollen = airOn && reading.air().known()
                ? "Pollen " + AirText.pollenName(reading.air(), airSettings()) : "";
        String fingerprint = String.format(Locale.US, "%.1f|%.1f|%.1f|%s|%s|%s|%s|%s|%s", reading.localF(),
                reading.outdoorF(), reading.feelsLikeF(), inside, outside, thermometer.reads(), thermometer.label(),
                thermometer.reads() == Thermometer.Reads.AIR ? airLine : "", pollen);
        render(thermometer.position(), fingerprint, force, () -> {
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
                case AIR -> {
                    if (!airOn || !reading.air().known()) {
                        screen.big("--").line(airOn ? "AQI" : "Air quality off", NamedTextColor.GRAY);
                    } else {
                        AirQualityModel.Air air = reading.air();
                        AirQualityIndex.Band band = AirText.band(air);
                        screen.tone(tone(band)).big(AirText.aqiNumber(air))
                                .line("AQI · " + band.displayName(), AirText.color(band));
                        if (band != AirQualityIndex.Band.GOOD)
                            screen.line("Mostly " + AirQualityIndex.dominant(air).cause().toLowerCase(Locale.ROOT),
                                    NamedTextColor.WHITE);
                        screen.line(pollen, NamedTextColor.YELLOW);
                    }
                }
            }
            if (!sky.isEmpty()) screen.line(sky, NamedTextColor.GRAY);
            return screen;
        });
    }

    /** "45%", or "--%" where there is no air to measure (a sign in water). */
    private static String percent(double humidity) {
        return Double.isFinite(humidity) ? Math.round(humidity) + "%" : "--%";
    }

    private List<RpmMonitor.Row> rowsOf(RpmMonitor monitor) {
        return monitor.system() == null ? List.of() : RpmMonitor.rows(registry.equipmentFor(monitor.system()));
    }

    /**
     * An RPM monitor's screen: its label (and page) in the header, then a
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
        render(monitor.position(), fingerprint.toString(), force, () -> {
            ScreenText screen = ScreenText.titled(monitor.label(),
                    monitor.system() == null ? "" : SystemNames.label(registry, monitor.system()));
            if (monitor.system() == null) return screen.line("Not linked:", NamedTextColor.GRAY)
                    .line("link with the", NamedTextColor.GRAY).line("link-tool", NamedTextColor.GRAY);
            if (rows.isEmpty()) return screen.line("No equipment", NamedTextColor.RED);
            // A screen holds six rows; with more it pages.
            if (rows.stream().anyMatch(row -> row.running() > 0)) screen.tone(ScreenText.Tone.RUNNING);
            for (RpmMonitor.Row row : rows.size() <= ScreenText.ROWS ? rows : shown)
                screen.line(rowText(row, ScreenText.WIDTH),
                        row.running() > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY);
            return screen;
        });
    }

    /**
     * "Heat pump T3 ×4 2,950": the kind, its tier (T1 to T3; stars before
     * BETA 1.0, which took twice the room), how many when more than one, and
     * the average speed of those running ("off" when none is; a furnace or
     * boiler, which has a fixed speed, says "on"). A row too wide for its line
     * tries shorter forms in turn: a short kind, the speed as "2.9k", a
     * compact kind, the count closed up, then without the count and the tier.
     * The speed is never what's cut: on a narrow line only the kind is.
     */
    static String rowText(RpmMonitor.Row row, int width) {
        String tier = " T" + row.tier().number();
        String count = row.units() > 1 ? " ×" + row.units() : "";
        boolean fixed = row.type().fixedSpeed();
        String state = row.running() == 0 ? "off" : fixed ? "on" : String.format(Locale.US, "%,.0f", row.rpm());
        String compact = row.running() == 0 || fixed ? state : String.format(Locale.US, "%.1fk", row.rpm() / 1000.0);
        String name = compactName(row.type());
        String[] forms = {
                row.type().displayName() + tier + count + " " + state,
                shortName(row.type()) + tier + count + " " + state,
                shortName(row.type()) + tier + count + " " + compact,
                name + tier + count + " " + compact,
                name + tier + count.trim() + " " + compact,
                name + tier + " " + compact,
                name + " " + compact};
        for (String form : forms) if (TextWidth.width(form) <= width) return form;
        return TextWidth.fit(name, width - TextWidth.width(" " + compact)) + " " + compact;
    }

    /** The shortest names, for a row that needs every pixel. */
    private static String compactName(EquipmentType type) {
        return switch (type) {
            case HEAT_PUMP -> "HP";
            case AIR_CONDITIONER -> "AC";
            case FURNACE -> "Furn";
            case BOILER -> "Boil";
            case POOL_HEAT_PUMP -> "PHP";
        };
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
        render(panel.position(), fingerprint, force, () -> ScreenText.titled(panel.label(), panel.system() == null ? "Not linked"
                        : SystemNames.label(registry, panel.system()))
                .tone(switch (panel.profile()) {
                    case ECO -> ScreenText.Tone.HOLDING.withGlyph("");
                    case NORMAL -> ScreenText.Tone.NEUTRAL;
                    case TURBO -> ScreenText.Tone.HEATING.withGlyph("");
                })
                .big(panel.profile().name())
                .line("Click: next", NamedTextColor.GRAY)
                .line("Sneak: back", NamedTextColor.GRAY));
    }

    /**
     * What keeps a thermostat from starting at all, for its screen (BETA 1.0
     * Pilot 2): nothing to heat or cool with, or no vent. Empty when nothing
     * does; units switched off server-wide say {@link #UNITS_OFF} instead.
     */
    static String blockedLine(java.util.Set<Attention> attention, boolean pool) {
        if (attention.contains(Attention.NO_UNITS)) return "Needs a unit";
        if (attention.contains(Attention.NO_VENTS)) return pool ? "Needs a water vent" : "Needs a vent";
        return "";
    }

    /** A thermostat whose call waits on units switched off in /hvac quicksettings (BETA 1.0). */
    static final String UNITS_OFF = "Units off";

    /**
     * What a waiting thermostat's screen says it wants: {@code Heat to 72.0°F}.
     * Shorter than "Heating to", so it fits a screen line at any target; a
     * line that wraps pushes the body up over the big reading.
     */
    static String waitingLine(OperatingMode waiting, String target) {
        return (waiting == OperatingMode.HEATING ? "Heat to " : "Cool to ") + target;
    }

    /** A unit whose kind is switched off in /hvac quicksettings (BETA 1.0). */
    static final String OFF_SERVER_WIDE = "Off server-wide";

    /** A dehumidifier's last line is its tank; a humidifier, being plumbed, just says what it is. */
    public void refreshClimateUnit(ClimateUnit unit, boolean force) {
        if (unit instanceof AirPurifier purifier) {
            refreshPurifier(purifier, force);
            return;
        }
        double humidity = unit.lastHumidity();
        String reading = (Double.isFinite(humidity) ? Math.round(humidity) + "%" : "--%")
                + " > " + unit.setpoint() + "%";
        ClimateUnit.State state = unit.state();
        // Running in air it can't dry: say so rather than look like it works.
        boolean tooCold = state == ClimateUnit.State.RUNNING && unit instanceof Dehumidifier dry && dry.tooCold();
        String stateText = tooCold ? "Air too cold" : switch (state) {
            case OFF -> unit.offServerWide() ? OFF_SERVER_WIDE : "OFF";
            case IDLE -> "Idle";
            case RUNNING -> "Running";
            case EMPTYING -> "Emptying...";
            case NO_FILTER, FILTER_SPENT -> state.name();
        };
        NamedTextColor stateColor = tooCold ? NamedTextColor.GOLD : switch (state) {
            case OFF -> NamedTextColor.RED;
            case IDLE -> NamedTextColor.GRAY;
            case RUNNING -> NamedTextColor.GREEN;
            case EMPTYING -> NamedTextColor.AQUA;
            case NO_FILTER, FILTER_SPENT -> NamedTextColor.GOLD;
        };
        String footer = unit instanceof Dehumidifier dehumidifier
                ? "Tank " + Math.round(dehumidifier.tank() * 100) + "%" : unit.kind().displayName();
        String fingerprint = unit.label() + "|" + reading + "|" + stateText + "|" + footer;
        render(unit.position(), fingerprint, force, () -> {
            ScreenText screen = ScreenText.titled(unit.label(), unit.kind().displayName())
                    .tone(tooCold ? ScreenText.Tone.ALERT : tone(state))
                    .big(Double.isFinite(humidity) ? Math.round(humidity) + "%" : "--%")
                    .line("Target " + unit.setpoint() + "%", NamedTextColor.WHITE)
                    .line(stateText, stateColor);
            if (unit instanceof Dehumidifier dehumidifier)
                screen.line(Component.text("Tank ", NamedTextColor.GRAY)
                        .append(ScreenText.bar(dehumidifier.tank(), 8, NamedTextColor.AQUA)));
            return screen;
        });
    }

    /**
     * An air purifier's screen (3.2): the AQI it reads, its fan (Auto shows the
     * speed it picked), and how full its filter is.
     */
    private void refreshPurifier(AirPurifier purifier, boolean force) {
        AirQualityModel.Air air = purifier.lastAir();
        String reading = AirText.signLine(air);
        ClimateUnit.State state = purifier.state();
        String stateText = switch (state) {
            case OFF -> purifier.offServerWide() ? OFF_SERVER_WIDE : "OFF";
            case IDLE -> purifier.mode().displayName();
            case RUNNING -> purifier.modeText();
            case NO_FILTER -> "No filter";
            case FILTER_SPENT, EMPTYING -> "Filter spent";
        };
        NamedTextColor stateColor = switch (state) {
            case OFF -> NamedTextColor.RED;
            case IDLE -> NamedTextColor.GRAY;
            case RUNNING -> NamedTextColor.GREEN;
            case NO_FILTER, FILTER_SPENT, EMPTYING -> NamedTextColor.GOLD;
        };
        String footer = purifier.installed() == AirPurifier.Filter.MISSING ? "Place tube coral"
                : "Filter " + Math.round(purifier.filter() * 100) + "%";
        String pollen = air.known() ? "Pollen " + AirText.pollenName(air, airSettings()) : "";
        String fingerprint = purifier.label() + "|" + reading + "|" + stateText + "|" + footer + "|" + pollen;
        render(purifier.position(), fingerprint, force, () -> {
            ScreenText.Tone tone = switch (state) {
                case OFF -> ScreenText.Tone.OFF;
                case NO_FILTER, FILTER_SPENT, EMPTYING -> ScreenText.Tone.ALERT;
                case IDLE, RUNNING -> air.known() ? tone(AirText.band(air)) : ScreenText.Tone.NEUTRAL;
            };
            ScreenText screen = ScreenText.titled(purifier.label(), purifier.kind().displayName()).tone(tone)
                    .big(air.known() ? AirText.aqiNumber(air) : "--")
                    .line(air.known() ? "AQI · " + AirText.band(air).displayName() : "AQI", AirText.color(air))
                    .line(stateText, stateColor);
            if (purifier.installed() != AirPurifier.Filter.MISSING)
                screen.line(Component.text("Filter ", NamedTextColor.GRAY)
                        .append(ScreenText.bar(purifier.filter(), 8, NamedTextColor.AQUA)));
            if (!pollen.isEmpty()) screen.line(pollen, NamedTextColor.YELLOW);
            return screen;
        });
    }

    private AirQualitySettings airSettings() {
        return outdoor.climate().airQuality();
    }

    /** A mini thermostat's panel on its wall button (BETA 1.0 Pilot 2). */
    private void renderMini(BlockKey position, String fingerprint, boolean force,
                            java.util.function.Supplier<MiniScreen> screen) {
        if (!position.isChunkLoaded()) return;
        var block = position.location() == null ? null : position.location().getBlock();
        if (block == null || !org.bukkit.Tag.BUTTONS.isTagged(block.getType())) {
            screens.remove(position);
            return;
        }
        if (force || !fingerprint.equals(fingerprints.get(position)) || !screens.showing(position, block)) {
            screens.showMini(position, block, screen.get());
            fingerprints.put(position, fingerprint);
        }
    }

    /** A mini thermostat's short reason it can't start, for its gauge's line; empty when nothing stops it. */
    static String miniBlocked(java.util.Set<Attention> attention, boolean waiting) {
        if (attention.contains(Attention.NO_UNITS)) return "No unit";
        if (attention.contains(Attention.NO_VENTS)) return "No vent";
        return waiting ? "Units off" : "";
    }

    private void refreshMini(Thermostat thermostat, boolean force) {
        java.util.Set<Attention> attention = thermostat.attention();
        String blocked = thermostat.enabled() ? miniBlocked(attention, thermostat.waitingOnUnits() != null) : "";
        double progress = thermostat.progress();
        String fingerprint = String.format(Locale.US, "mini|%.1f|%.1f|%s|%s|%d|%s|%d", thermostat.roomF(),
                thermostat.targetF(), thermostat.mode(), thermostat.enabled(),
                Double.isFinite(progress) ? (int) Math.round(progress * 16) : -1, attention, blocked.length());
        renderMini(thermostat.position(), fingerprint, force, () -> MiniScreen.of(settings.units(),
                thermostat.roomF(), thermostat.targetF(), thermostat.mode(), thermostat.enabled(), progress,
                attention.size(), blocked));
    }

    /**
     * Draws a device's screen in front of its sign: when what it shows
     * changed, on every forced refresh, and whenever it is missing (a chunk
     * unloading takes screens away, and this puts them back). Its text is
     * only sent when it changed.
     *
     * <p>The sign behind stays blank: from 26.1 a sign's own text shows
     * through the screen's panel. It is looked at the first time the device
     * is drawn and on forced refreshes, not with every change: reading a
     * sign snapshots its whole block entity, once EZvac's largest single cost.
     */
    private void render(BlockKey position, String fingerprint, boolean force,
                        java.util.function.Supplier<ScreenText> screen) {
        if (!position.isChunkLoaded()) return;
        var location = position.location();
        var block = location == null ? null : location.getBlock();
        if (block == null || !org.bukkit.Tag.ALL_SIGNS.isTagged(block.getType())) {
            screens.remove(position);
            return;
        }
        String before = fingerprints.get(position);
        boolean changed = force || !fingerprint.equals(before);
        if (!changed && screens.showing(position, block)) return;
        try {
            screens.show(position, block, screen.get());
            if ((force || before == null) && block.getState() instanceof Sign sign && clear(sign.getSide(Side.FRONT)))
                sign.update(false, false);
            fingerprints.put(position, fingerprint);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not refresh HVAC display at " + position, exception);
        }
    }

    /** A screen header for the air (BETA 1.0): the AQI band's colour, darkened to carry white text. */
    static ScreenText.Tone tone(AirQualityIndex.Band band) {
        return switch (band) {
            case GOOD -> new ScreenText.Tone(0x2E7D32, "●");
            case MODERATE -> new ScreenText.Tone(0x8A7400, "●");
            case SENSITIVE -> new ScreenText.Tone(0xA8521C, "●");
            case UNHEALTHY -> new ScreenText.Tone(0xA32D2D, "●");
            case VERY_UNHEALTHY -> new ScreenText.Tone(0x6A2A8A, "●");
            case HAZARDOUS -> new ScreenText.Tone(0x6E1E2E, "●");
        };
    }

    /** A (de)humidifier's header for what it is doing. */
    static ScreenText.Tone tone(ClimateUnit.State state) {
        return switch (state) {
            case OFF -> ScreenText.Tone.OFF;
            case IDLE -> ScreenText.Tone.NEUTRAL;
            case RUNNING -> ScreenText.Tone.RUNNING;
            case EMPTYING -> ScreenText.Tone.COOLING.withGlyph("≈");
            case NO_FILTER, FILTER_SPENT -> ScreenText.Tone.ALERT;
        };
    }

    /** Empties every line of a sign's side; false when there was nothing to empty. */
    static boolean clear(SignSide side) {
        boolean changed = false;
        for (int line = 0; line < 4; line++) {
            if (PlainTextComponentSerializer.plainText().serialize(side.line(line)).isEmpty()) continue;
            side.line(line, Component.empty());
            changed = true;
        }
        return changed;
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
