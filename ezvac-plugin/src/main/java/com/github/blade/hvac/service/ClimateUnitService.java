package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.simulation.HumidityModel;
import com.github.blade.hvac.simulation.Psychrometrics;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.bukkit.Location;

import java.util.*;

/**
 * Runs standalone dehumidifiers and humidifiers once per controller cycle,
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

    private static final int[][] SIDES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final HvacRegistry registry;
    private final AirflowService airflow;
    private final OutdoorTemperatureService outdoor;
    private final ClimateSettings climate;
    /** Running dehumidifiers and humidifiers per system they serve, from prepare(). */
    private final Map<SystemId, List<Dehumidifier>> drying = new HashMap<>();
    private final Map<SystemId, List<Humidifier>> humidifying = new HashMap<>();

    public ClimateUnitService(HvacRegistry registry, AirflowService airflow, OutdoorTemperatureService outdoor) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.climate = outdoor.climate();
    }

    public ClimateSettings climate() { return climate; }

    /** Before the controller: which space each unit serves, and which systems are being worked on. */
    public void prepare() {
        drying.clear();
        humidifying.clear();
        for (ClimateUnit unit : registry.climateUnits()) {
            SystemId served = servedSystem(unit);
            unit.setServedSystem(served);
            if (served == null || !unit.running()) continue;
            switch (unit) {
                case Dehumidifier dehumidifier -> drying.computeIfAbsent(served, ignored -> new ArrayList<>()).add(dehumidifier);
                case Humidifier humidifier -> humidifying.computeIfAbsent(served, ignored -> new ArrayList<>()).add(humidifier);
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
            if (unit != null) {
                if (climateUnit.servedSystem() == null) {
                    humidity = standalone.getOrDefault(climateUnit, Double.NaN);
                    dewPointF = climateUnit.zoneDewPointF();
                } else {
                    humidity = systemHumidity(climateUnit.servedSystem());
                    dewPointF = systemDewPoint(climateUnit.servedSystem());
                }
                if (climateUnit instanceof Dehumidifier dehumidifier) dehumidifier.advanceEmptying(elapsedTicks);
            }
            if (climateUnit instanceof Dehumidifier dehumidifier)
                dehumidifier.updateTooCold(dewPointF, climate.dehumidifier());
            climateUnit.setLastHumidity(humidity);
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
        };
    }

    private double systemHumidity(SystemId system) {
        Thermostat thermostat = registry.thermostatFor(system);
        if (thermostat == null || !thermostat.hasRoomDewPoint()) return Double.NaN;
        return Psychrometrics.relativeHumidity(thermostat.roomF(), thermostat.roomDewPointF());
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
        return stepSharedSpace(space, cells, wetFraction, out.airF(), out.dewPointF(), elapsedTicks, climate);
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
            }
        }
        double startF = dewCount == 0 ? Double.NaN : dewSum / dewCount;
        HumidityModel.Result result = HumidityModel.step(startF, outdoorF, outdoorDewPointF,
                new HumidityModel.Loads(0.0, wetFraction, Double.NaN, drying, adding), elapsedTicks, climate);
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
    }
}
