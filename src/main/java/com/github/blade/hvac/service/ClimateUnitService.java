package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.simulation.HumidityModel;
import com.github.blade.hvac.simulation.Psychrometrics;
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
    private final Map<GroupId, List<Dehumidifier>> drying = new HashMap<>();
    private final Map<GroupId, List<Humidifier>> humidifying = new HashMap<>();

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
            GroupId served = servedSystem(unit);
            unit.setServedGroup(served);
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
    private GroupId servedSystem(ClimateUnit climateUnit) {
        BlockKey unit = climateUnit.unit();
        if (!unit.isChunkLoaded()) return climateUnit.servedGroup();
        for (int[] side : SIDES) {
            GroupId group = airflow.systemAt(unit.worldId(), unit.x() + side[0], unit.y() + side[1], unit.z() + side[2]);
            if (group == null) continue;
            Thermostat thermostat = registry.thermostatFor(group);
            if (thermostat != null && !thermostat.pool()) return group;
        }
        return null;
    }

    /** How hard one unit works on a space this size: fully at its reference size, weaker in larger spaces. */
    static double strength(int cells, int referenceCells) {
        return Math.min(1.0, referenceCells / (double) Math.max(1, cells));
    }

    /** The combined drying strength of the dehumidifiers running in a system's room. */
    public double scaleFor(GroupId group) {
        List<Dehumidifier> units = drying.get(group);
        if (units == null) return 0.0;
        return units.size() * strength(airflow.mapSize(group), climate.dehumidifier().referenceCells());
    }

    /** The combined strength of the humidifiers running in a system's room. */
    public double humidifierScaleFor(GroupId group) {
        List<Humidifier> units = humidifying.get(group);
        if (units == null) return 0.0;
        return units.size() * strength(airflow.mapSize(group), climate.humidifier().referenceCells());
    }

    /** Shares the water a system's dehumidifiers pulled out this step between their tanks. */
    public void credit(GroupId group, double dehumidifiedF) {
        List<Dehumidifier> units = drying.get(group);
        if (units == null || units.isEmpty() || dehumidifiedF <= 0.0) return;
        double share = dehumidifiedF / units.size();
        int cells = airflow.mapSize(group);
        for (Dehumidifier dehumidifier : units) dehumidifier.fill(tankFill(share, cells), climate.dehumidifier());
    }

    /**
     * Converts a dew point drop in a space into tank level. The water a unit
     * holds scales with the size of the space it took it from, so a unit
     * filling at its rated rate fills in tank-fill-hours whatever the space.
     */
    private double tankFill(double dewDropF, int cells) {
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
        Set<BlockKey> outputs = ordered.isEmpty() ? Set.of() : registry.ownedOutputs();
        for (ClimateUnit climateUnit : ordered) {
            boolean wasRunning = climateUnit.running();
            boolean wasEmptying = climateUnit instanceof Dehumidifier dehumidifier && dehumidifier.emptying();
            Location unit = climateUnit.unit().isChunkLoaded() ? climateUnit.unit().location() : null;
            double humidity = Double.NaN;
            if (unit != null) {
                humidity = climateUnit.servedGroup() == null ? stepZone(climateUnit, unit, elapsedTicks, outputs)
                        : systemHumidity(climateUnit.servedGroup());
                if (climateUnit instanceof Dehumidifier dehumidifier) dehumidifier.advanceEmptying(elapsedTicks);
            }
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

    private double systemHumidity(GroupId group) {
        Thermostat thermostat = registry.thermostatFor(group);
        if (thermostat == null || !thermostat.hasRoomDewPoint()) return Double.NaN;
        return Psychrometrics.relativeHumidity(thermostat.roomF(), thermostat.roomDewPointF());
    }

    /**
     * Steps the moisture of a unit's own zone. Nothing conditions its
     * temperature, so the zone is at the outdoor air temperature, and its
     * moisture drifts toward the outdoors like any room's.
     */
    private double stepZone(ClimateUnit climateUnit, Location unit, long elapsedTicks, Set<BlockKey> outputs) {
        OutdoorTemperatureService.Outdoor out = outdoor.climateAt(unit);
        ZoneId zone = ZoneId.of(climateUnit);
        int cells = airflow.mapSize(zone);
        AirflowService.WaterExposure exposure = airflow.waterExposure(zone)
                .excluding(climateUnit.unit().worldId(), outputs);
        boolean working = climateUnit.running() && cells > 0;
        double drying = working && climateUnit instanceof Dehumidifier
                ? strength(cells, climate.dehumidifier().referenceCells()) : 0.0;
        double adding = working && climateUnit instanceof Humidifier
                ? strength(cells, climate.humidifier().referenceCells()) : 0.0;
        HumidityModel.Result result = HumidityModel.step(climateUnit.zoneDewPointF(), out.airF(), out.dewPointF(),
                new HumidityModel.Loads(0.0, exposure.wetFraction(), Double.NaN, drying, adding),
                elapsedTicks, climate);
        climateUnit.setZoneDewPointF(result.dewPointF());
        if (climateUnit instanceof Dehumidifier dehumidifier && result.dehumidifiedF() > 0.0)
            dehumidifier.fill(tankFill(result.dehumidifiedF(), cells), climate.dehumidifier());
        return Psychrometrics.relativeHumidity(out.airF(), result.dewPointF());
    }

    /** Drops a removed unit's zone; the registry already dried its water block. */
    public void release(ClimateUnit unit) {
        airflow.forget(ZoneId.of(unit));
    }
}
