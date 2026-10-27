package com.github.blade.hvac.service;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.simulation.AirQualityModel;
import com.github.blade.hvac.simulation.HumidityModel;
import com.github.blade.hvac.simulation.ThermalModel;
import com.github.blade.hvac.simulation.Psychrometrics;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.bukkit.Location;

import java.util.*;

/**
 * Runs standalone dehumidifiers, humidifiers and air purifiers once per controller cycle,
 * in two halves around the controller: {@link #prepare()} works out which
 * space each unit serves before the controller steps every system's
 * humidity, and {@link #afterCycle(long)} steps standalone zones, handles
 * full tanks and decides who runs next. A unit decided on in one cycle works
 * in the next, as equipment does.
 */
public final class ClimateUnitService {
    /** What changed this cycle, for sounds, bubbles and signs. */
    public record Changes(List<ClimateUnit> started, List<ClimateUnit> stopped,
                          List<Dehumidifier> emptying, List<ClimateUnit> running) {}

    private static final double TICKS_PER_HOUR = ThermalModel.MINECRAFT_TICKS_PER_HOUR;

    private static final int[][] SIDES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final HvacRegistry registry;
    private final AirflowService airflow;
    private final OutdoorTemperatureService outdoor;
    private final AirQualityService air;
    private final ClimateSettings climate;
    /** Running dehumidifiers and humidifiers per system they serve, from prepare(). */
    private final Map<SystemId, List<Dehumidifier>> drying = new HashMap<>();
    private final Map<SystemId, List<Humidifier>> humidifying = new HashMap<>();
    private final Map<SystemId, List<AirPurifier>> purifying = new HashMap<>();
    /** Purifiers whose filter filled this cycle. */
    private final List<AirPurifier> spent = new ArrayList<>();

    public ClimateUnitService(HvacRegistry registry, AirflowService airflow, OutdoorTemperatureService outdoor,
                              AirQualityService air) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.air = Objects.requireNonNull(air, "air");
        this.climate = outdoor.climate();
    }

    public ClimateSettings climate() { return climate; }

    public AirQualityService air() { return air; }

    /** Before the controller: which space each unit serves, and which systems are being worked on. */
    public void prepare() {
        drying.clear();
        humidifying.clear();
        purifying.clear();
        spent.clear();
        for (ClimateUnit unit : registry.climateUnits()) {
            SystemId served = servedSystem(unit);
            unit.setServedSystem(served);
            if (served == null || !unit.running()) continue;
            switch (unit) {
                case Dehumidifier dehumidifier -> drying.computeIfAbsent(served, ignored -> new ArrayList<>()).add(dehumidifier);
                case Humidifier humidifier -> humidifying.computeIfAbsent(served, ignored -> new ArrayList<>()).add(humidifier);
                case AirPurifier purifier ->
                        purifying.computeIfAbsent(served, ignored -> new ArrayList<>()).add(purifier);
            }
        }
    }

    /**
     * A unit serves a system's room when the air right around it is part of
     * that system's airflow. Pools have no air of their own, so they never count.
     */
    private SystemId servedSystem(ClimateUnit climateUnit) {
        BlockKey unit = climateUnit.unit();
        if (!unit.isChunkLoaded()) return climateUnit.servedSystem();
        for (int[] side : SIDES) {
            SystemId system = airflow.systemAt(unit.worldId(), unit.x() + side[0], unit.y() + side[1], unit.z() + side[2]);
            if (system == null) continue;
            Thermostat thermostat = registry.thermostatFor(system);
            if (thermostat != null && !thermostat.pool()) return system;
        }
        return null;
    }

    /** How hard one unit works on a space this size: fully at its reference size, weaker in larger spaces. */
    static double strength(int cells, int referenceCells) {
        return Math.min(1.0, referenceCells / (double) Math.max(1, cells));
    }

    /** The combined drying strength of the dehumidifiers running in a system's room. */
    public double scaleFor(SystemId system) {
        List<Dehumidifier> units = drying.get(system);
        if (units == null) return 0.0;
        return units.size() * strength(airflow.mapSize(system), climate.dehumidifier().referenceCells());
    }

    /** The combined strength of the humidifiers running in a system's room. */
    public double humidifierScaleFor(SystemId system) {
        List<Humidifier> units = humidifying.get(system);
        if (units == null) return 0.0;
        return units.size() * strength(airflow.mapSize(system), climate.humidifier().referenceCells());
    }

    /** The combined strength of the air purifiers running in a system's room (3.2), each at its fan speed. */
    public double purifierScaleFor(SystemId system) {
        List<AirPurifier> units = purifying.get(system);
        if (units == null) return 0.0;
        return speeds(units, air.settings()) * strength(airflow.mapSize(system), air.settings().referenceCells());
    }

    /** The purifiers' fan speeds added up: 1 is one purifier on High. */
    private static double speeds(List<AirPurifier> units, AirQualitySettings settings) {
        double total = 0.0;
        for (AirPurifier purifier : units) total += purifier.speedFactor(settings.purifier());
        return total;
    }

    /** Shares what a system's purifiers caught this step between their filters (3.2). */
    public void creditAir(SystemId system, AirQualityModel.Air captured, long elapsedTicks) {
        List<AirPurifier> units = purifying.get(system);
        if (units == null || units.isEmpty()) return;
        wear(units, captured, airflow.mapSize(system), elapsedTicks, air.settings(), spent);
    }

    /**
     * Fills the filters of the purifiers that shared a space, each by its
     * share of the cleaning: a purifier on High catches more than one on Low.
     * What they caught is scaled to the reference size, so a purifier
     * cleaning a large space fills faster than one in a small space with the
     * same air.
     */
    static void wear(List<AirPurifier> units, AirQualityModel.Air captured, int cells, long elapsedTicks,
                     AirQualitySettings settings, List<AirPurifier> filled) {
        double total = speeds(units, settings);
        if (!(total > 0.0)) return;
        double size = Math.max(1, cells) / (double) settings.referenceCells();
        double hours = elapsedTicks / TICKS_PER_HOUR;
        for (AirPurifier purifier : units) {
            double share = purifier.speedFactor(settings.purifier()) / total;
            if (purifier.wear(captured.times(size * share), hours, settings.purifier())) {
                purifier.showSpentFilter();
                filled.add(purifier);
            }
        }
    }

    /** Purifiers whose filters filled during this cycle. */
    public List<AirPurifier> spentFilters() { return List.copyOf(spent); }

    /** Shares the water a system's dehumidifiers pulled out this step between their tanks. */
    public void credit(SystemId system, double dehumidifiedF) {
        List<Dehumidifier> units = drying.get(system);
        if (units == null || units.isEmpty() || dehumidifiedF <= 0.0) return;
        double share = dehumidifiedF / units.size();
        int cells = airflow.mapSize(system);
        for (Dehumidifier dehumidifier : units) dehumidifier.fill(tankFill(share, cells, climate), climate.dehumidifier());
    }

    /**
     * Converts a dew point drop in a space into tank level. The water a unit
     * holds scales with the size of the space it took it from, so a unit
     * filling at its rated rate fills in tank-fill-hours whatever the space.
     */
    private static double tankFill(double dewDropF, int cells, ClimateSettings climate) {
        ClimateSettings.Dehumidifier settings = climate.dehumidifier();
        return dewDropF * (Math.max(1, cells) / (double) settings.referenceCells())
                / (settings.dryFPerHour() * settings.tankFillHours());
    }

    /** After the controller: standalone zones, emptying tanks, water blocks, and who runs next cycle. */
    public Changes afterCycle(long elapsedTicks) {
        List<ClimateUnit> started = new ArrayList<>();
        List<ClimateUnit> stopped = new ArrayList<>();
        List<Dehumidifier> emptying = new ArrayList<>();
        List<ClimateUnit> running = new ArrayList<>();
        List<ClimateUnit> ordered = registry.climateUnits();
        Map<UUID, LongOpenHashSet> outputs = ordered.isEmpty() ? Map.of()
                : AirflowService.packByWorld(registry.ownedOutputs());
        Map<ClimateUnit, Double> standalone = stepStandalone(ordered, elapsedTicks, outputs);
        for (ClimateUnit climateUnit : ordered) {
            boolean wasRunning = climateUnit.running();
            boolean wasEmptying = climateUnit instanceof Dehumidifier dehumidifier && dehumidifier.emptying();
            Location unit = climateUnit.unit().isChunkLoaded() ? climateUnit.unit().location() : null;
            double humidity = Double.NaN;
            double dewPointF = Double.NaN;
            AirQualityModel.Air spaceAir = AirQualityModel.Air.UNKNOWN;
            if (unit != null) {
                if (climateUnit.servedSystem() == null) {
                    humidity = standalone.getOrDefault(climateUnit, Double.NaN);
                    dewPointF = climateUnit.zoneDewPointF();
                    spaceAir = climateUnit.zoneAir();
                } else {
                    humidity = systemHumidity(climateUnit.servedSystem());
                    dewPointF = systemDewPoint(climateUnit.servedSystem());
                    spaceAir = systemAir(climateUnit.servedSystem());
                }
                if (climateUnit instanceof Dehumidifier dehumidifier) dehumidifier.advanceEmptying(elapsedTicks);
                if (climateUnit instanceof AirPurifier purifier) purifier.readFilterBlock();
            }
            if (climateUnit instanceof Dehumidifier dehumidifier)
                dehumidifier.updateTooCold(dewPointF, climate.dehumidifier());
            climateUnit.setLastHumidity(humidity);
            climateUnit.setLastAir(air.enabled() ? spaceAir : AirQualityModel.Air.UNKNOWN);
            // A unit whose chunk is unloaded sees NaN and stops, like equipment out of reach.
            climateUnit.control(humidity, climate);
            if (unit != null) climateUnit.setPartWater(showsWater(climateUnit));
            if (climateUnit instanceof Dehumidifier dehumidifier && dehumidifier.emptying() && !wasEmptying)
                emptying.add(dehumidifier);
            if (climateUnit.running() && !wasRunning) started.add(climateUnit);
            if (!climateUnit.running() && wasRunning) stopped.add(climateUnit);
            if (climateUnit.running()) running.add(climateUnit);
        }
        if (!ordered.isEmpty()) registry.markDirty();
        return new Changes(started, stopped, emptying, running);
    }

    /** A dehumidifier shows water while its full tank empties; a humidifier while it runs. */
    private static boolean showsWater(ClimateUnit unit) {
        return switch (unit) {
            case Dehumidifier dehumidifier -> dehumidifier.emptying();
            case Humidifier humidifier -> humidifier.running();
            case AirPurifier ignored -> false;
        };
    }

    private double systemHumidity(SystemId system) {
        Thermostat thermostat = registry.thermostatFor(system);
        if (thermostat == null || !thermostat.hasRoomDewPoint()) return Double.NaN;
        return Psychrometrics.relativeHumidity(thermostat.roomF(), thermostat.roomDewPointF());
    }

    private AirQualityModel.Air systemAir(SystemId system) {
        Thermostat thermostat = registry.thermostatFor(system);
        return thermostat == null ? AirQualityModel.Air.UNKNOWN : thermostat.roomAir();
    }

    private double systemDewPoint(SystemId system) {
        Thermostat thermostat = registry.thermostatFor(system);
        return thermostat == null || !thermostat.hasRoomDewPoint() ? Double.NaN : thermostat.roomDewPointF();
    }

    /**
     * Steps every standalone space once. Units whose zones share air are one
     * space, a shed with a dehumidifier and a humidifier in it, so they read
     * and work on the same humidity instead of each keeping its own.
     *
     * @return the RH of each loaded standalone unit's space
     */
    private Map<ClimateUnit, Double> stepStandalone(List<ClimateUnit> units, long elapsedTicks,
                                                    Map<UUID, LongOpenHashSet> outputs) {
        Set<ClimateUnit> standalone = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ClimateUnit unit : units)
            if (unit.servedSystem() == null && unit.unit().isChunkLoaded() && unit.unit().location() != null)
                standalone.add(unit);
        Map<ClimateUnit, Double> humidity = new IdentityHashMap<>();
        Set<ClimateUnit> systemed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ClimateUnit unit : units) {
            if (!standalone.contains(unit) || !systemed.add(unit)) continue;
            List<ClimateUnit> space = new ArrayList<>(List.of(unit));
            for (int next = 0; next < space.size(); next++)
                for (ClimateUnit other : airflow.unitsSharingAir(space.get(next)))
                    if (standalone.contains(other) && systemed.add(other)) space.add(other);
            space.sort(Comparator.comparing(ClimateUnit::position));
            double rh = stepSpace(space, elapsedTicks, outputs);
            for (ClimateUnit member : space) humidity.put(member, rh);
        }
        return humidity;
    }

    /**
     * Steps the moisture of one standalone space. Its size and water are its
     * largest zone's, and the outdoors is its first unit's.
     */
    private double stepSpace(List<ClimateUnit> space, long elapsedTicks, Map<UUID, LongOpenHashSet> outputs) {
        ClimateUnit first = space.get(0);
        OutdoorTemperatureService.Outdoor out = outdoor.climateAt(first.unit().location());
        ClimateUnit largest = first;
        for (ClimateUnit unit : space)
            if (airflow.mapSize(ZoneId.of(unit)) > airflow.mapSize(ZoneId.of(largest))) largest = unit;
        ZoneId zone = ZoneId.of(largest);
        int cells = airflow.mapSize(zone);
        double wetFraction = airflow.waterExposure(zone).excluding(outputs.get(largest.unit().worldId())).wetFraction();
        // Wind outside makes the space leakier (3.2).
        double leak = climate.wind().infiltrationFactor(out.windMs());
        if (air.enabled()) {
            AirQualityModel.Air sources = air.sourcesFor(zone, largest.unit().worldId(), cells);
            stepSharedAir(space, cells, sources, out.air(), elapsedTicks,
                    climate.humidity().infiltrationPerHour() * leak, air.settings(), spent);
        }
        return stepSharedSpace(space, cells, wetFraction, out.airF(), out.dewPointF(), elapsedTicks, climate, leak);
    }

    /**
     * One standalone space's air step (3.2), for every unit in it: it drifts
     * toward the outdoors, its fires add to it, its running purifiers clean
     * it and share what they caught, and every unit keeps the space's new air.
     * Pure apart from the units it updates.
     *
     * @return the space's new air
     */
    static AirQualityModel.Air stepSharedAir(List<ClimateUnit> space, int cells, AirQualityModel.Air sources,
                                             AirQualityModel.Air outdoorAir, long elapsedTicks,
                                             double infiltrationPerHour, AirQualitySettings settings,
                                             List<AirPurifier> filled) {
        AirQualityModel.Air start = AirQualityModel.Air.UNKNOWN;
        List<AirPurifier> purifiers = new ArrayList<>();
        for (ClimateUnit unit : space) {
            if (!start.known() && unit.zoneAir().known()) start = unit.zoneAir();
            if (unit instanceof AirPurifier purifier && purifier.running() && cells > 0) purifiers.add(purifier);
        }
        double scale = speeds(purifiers, settings) * strength(cells, settings.referenceCells());
        AirQualityModel.Result result = AirQualityModel.step(start, outdoorAir,
                new AirQualityModel.Loads(sources, 0.0, scale), elapsedTicks, infiltrationPerHour, settings);
        for (ClimateUnit unit : space) unit.setZoneAir(result.air());
        if (!purifiers.isEmpty()) wear(purifiers, result.captured(), cells, elapsedTicks, settings, filled);
        return result.air();
    }

    /**
     * One standalone space's moisture step, for every unit in it. Nothing
     * conditions its temperature, so it is at the outdoor air temperature and
     * its moisture drifts toward the outdoors like any room's. What its
     * running units do adds up; the water its dehumidifiers pull out is shared
     * between their tanks; and every unit keeps the space's new dew point.
     * Pure apart from the units it updates.
     *
     * @param space the units that share the space
     * @param cells the space's size in airflow cells; 0 while it is unmapped
     * @return the space's RH
     */
    static double stepSharedSpace(List<ClimateUnit> space, int cells, double wetFraction, double outdoorF,
                                  double outdoorDewPointF, long elapsedTicks, ClimateSettings climate) {
        return stepSharedSpace(space, cells, wetFraction, outdoorF, outdoorDewPointF, elapsedTicks, climate, 1.0);
    }

    /** The same, with wind outside multiplying the air exchange by {@code leak} (3.2). */
    static double stepSharedSpace(List<ClimateUnit> space, int cells, double wetFraction, double outdoorF,
                                  double outdoorDewPointF, long elapsedTicks, ClimateSettings climate, double leak) {
        double drying = 0.0;
        double adding = 0.0;
        List<Dehumidifier> dehumidifiers = new ArrayList<>();
        double dewSum = 0.0;
        int dewCount = 0;
        for (ClimateUnit unit : space) {
            if (Double.isFinite(unit.zoneDewPointF())) {
                dewSum += unit.zoneDewPointF();
                dewCount++;
            }
            if (!unit.running() || cells <= 0) continue;
            switch (unit) {
                case Dehumidifier dehumidifier -> {
                    drying += strength(cells, climate.dehumidifier().referenceCells());
                    dehumidifiers.add(dehumidifier);
                }
                case Humidifier ignored -> adding += strength(cells, climate.humidifier().referenceCells());
                case AirPurifier ignored -> { }
            }
        }
        double startF = dewCount == 0 ? Double.NaN : dewSum / dewCount;
        HumidityModel.Result result = HumidityModel.step(startF, outdoorF, outdoorDewPointF,
                new HumidityModel.Loads(0.0, wetFraction, Double.NaN, drying, adding), elapsedTicks, climate, leak);
        for (ClimateUnit unit : space) unit.setZoneDewPointF(result.dewPointF());
        if (result.dehumidifiedF() > 0.0 && !dehumidifiers.isEmpty()) {
            double share = result.dehumidifiedF() / dehumidifiers.size();
            for (Dehumidifier dehumidifier : dehumidifiers)
                dehumidifier.fill(tankFill(share, cells, climate), climate.dehumidifier());
        }
        return Psychrometrics.relativeHumidity(outdoorF, result.dewPointF());
    }

    /** Drops a removed unit's zone; the registry already dried its water block. */
    public void release(ClimateUnit unit) {
        airflow.forget(ZoneId.of(unit));
        air.forget(ZoneId.of(unit));
    }
}
