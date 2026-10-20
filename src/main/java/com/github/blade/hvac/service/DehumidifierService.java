package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.Dehumidifier;
import com.github.blade.hvac.model.GroupId;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.model.ZoneId;
import com.github.blade.hvac.simulation.HumidityModel;
import com.github.blade.hvac.simulation.Psychrometrics;
import org.bukkit.Location;

import java.util.*;

/**
 * Runs standalone dehumidifiers once per controller cycle, in two halves
 * around the controller: {@link #prepare()} works out which space each unit
 * dries before the controller steps every system's humidity, and
 * {@link #afterCycle(long)} dries standalone zones, handles full tanks and
 * decides who runs next. A unit decided on in one cycle dries in the next,
 * as equipment does.
 */
public final class DehumidifierService {
    /** What changed this cycle, for sounds and signs. */
    public record Changes(List<Dehumidifier> started, List<Dehumidifier> stopped,
                          List<Dehumidifier> emptying, List<Dehumidifier> running) {}

    private static final int[][] SIDES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final HvacRegistry registry;
    private final AirflowService airflow;
    private final OutdoorTemperatureService outdoor;
    private final ClimateSettings.Dehumidifier settings;
    /** Running units per system they dry, from prepare(). */
    private final Map<GroupId, List<Dehumidifier>> drying = new HashMap<>();

    public DehumidifierService(HvacRegistry registry, AirflowService airflow, OutdoorTemperatureService outdoor) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.settings = outdoor.climate().dehumidifier();
    }

    /** Before the controller: which space each unit dries, and which systems are being dried. */
    public void prepare() {
        drying.clear();
        for (Dehumidifier dehumidifier : registry.dehumidifiers()) {
            GroupId served = servedSystem(dehumidifier);
            dehumidifier.setServedGroup(served);
            if (served != null && dehumidifier.running())
                drying.computeIfAbsent(served, ignored -> new ArrayList<>()).add(dehumidifier);
        }
    }

    /**
     * A unit dries a system's room when the air right around it is part of
     * that system's airflow. Pools have no air to dry, so they never count.
     */
    private GroupId servedSystem(Dehumidifier dehumidifier) {
        BlockKey unit = dehumidifier.unit();
        if (!unit.isChunkLoaded()) return dehumidifier.servedGroup();
        for (int[] side : SIDES) {
            GroupId group = airflow.systemAt(unit.worldId(), unit.x() + side[0], unit.y() + side[1], unit.z() + side[2]);
            if (group == null) continue;
            Thermostat thermostat = registry.thermostatFor(group);
            if (thermostat != null && !thermostat.pool()) return group;
        }
        return null;
    }

    /** How hard one unit dries a space this size: fully at its reference size, weaker in larger spaces. */
    double strength(int cells) {
        return Math.min(1.0, settings.referenceCells() / (double) Math.max(1, cells));
    }

    /** The combined drying strength of the units running in a system's room, for the humidity step. */
    public double scaleFor(GroupId group) {
        List<Dehumidifier> units = drying.get(group);
        if (units == null) return 0.0;
        return units.size() * strength(airflow.mapSize(group));
    }

    /** Shares the water a system's dehumidifiers pulled out this step between their tanks. */
    public void credit(GroupId group, double dehumidifiedF) {
        List<Dehumidifier> units = drying.get(group);
        if (units == null || units.isEmpty() || dehumidifiedF <= 0.0) return;
        double share = dehumidifiedF / units.size();
        int cells = airflow.mapSize(group);
        for (Dehumidifier dehumidifier : units) dehumidifier.fill(tankFill(share, cells), settings);
    }

    /**
     * Converts a dew point drop in a space into tank level. The water a unit
     * holds scales with the size of the space it took it from, so a unit
     * filling at its rated rate fills in tank-fill-hours whatever the space.
     */
    private double tankFill(double dewDropF, int cells) {
        return dewDropF * (Math.max(1, cells) / (double) settings.referenceCells())
                / (settings.dryFPerHour() * settings.tankFillHours());
    }

    /** After the controller: standalone zones, emptying tanks, and who runs next cycle. */
    public Changes afterCycle(long elapsedTicks) {
        List<Dehumidifier> started = new ArrayList<>();
        List<Dehumidifier> stopped = new ArrayList<>();
        List<Dehumidifier> emptying = new ArrayList<>();
        List<Dehumidifier> running = new ArrayList<>();
        List<Dehumidifier> ordered = new ArrayList<>(registry.dehumidifiers());
        ordered.sort(Comparator.comparing(Dehumidifier::position));
        Set<BlockKey> outputs = ordered.isEmpty() ? Set.of() : registry.ownedOutputs();
        for (Dehumidifier dehumidifier : ordered) {
            boolean wasRunning = dehumidifier.running();
            boolean wasEmptying = dehumidifier.emptying();
            Location unit = dehumidifier.unit().isChunkLoaded() ? dehumidifier.unit().location() : null;
            double humidity = Double.NaN;
            if (unit != null) {
                if (dehumidifier.servedGroup() == null) humidity = stepZone(dehumidifier, unit, elapsedTicks, outputs);
                else humidity = systemHumidity(dehumidifier.servedGroup());
                dehumidifier.advanceEmptying(elapsedTicks);
            }
            dehumidifier.setLastHumidity(humidity);
            // A unit whose chunk is unloaded sees NaN and stops, like equipment out of reach.
            dehumidifier.control(humidity, settings);
            if (unit != null) dehumidifier.setGrateWater(dehumidifier.emptying());
            if (dehumidifier.emptying() && !wasEmptying) emptying.add(dehumidifier);
            if (dehumidifier.running() && !wasRunning) started.add(dehumidifier);
            if (!dehumidifier.running() && wasRunning) stopped.add(dehumidifier);
            if (dehumidifier.running()) running.add(dehumidifier);
        }
        registry.markDirty();
        return new Changes(started, stopped, emptying, running);
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
    private double stepZone(Dehumidifier dehumidifier, Location unit, long elapsedTicks, Set<BlockKey> outputs) {
        OutdoorTemperatureService.Outdoor out = outdoor.climateAt(unit);
        ZoneId zone = ZoneId.of(dehumidifier);
        int cells = airflow.mapSize(zone);
        AirflowService.WaterExposure exposure = airflow.waterExposure(zone)
                .excluding(dehumidifier.unit().worldId(), outputs);
        double scale = dehumidifier.running() && cells > 0 ? strength(cells) : 0.0;
        HumidityModel.Result result = HumidityModel.step(dehumidifier.zoneDewPointF(), out.airF(), out.dewPointF(),
                new HumidityModel.Loads(0.0, exposure.wetFraction(), Double.NaN, scale),
                elapsedTicks, outdoor.climate());
        dehumidifier.setZoneDewPointF(result.dewPointF());
        if (result.dehumidifiedF() > 0.0) dehumidifier.fill(tankFill(result.dehumidifiedF(), cells), settings);
        return Psychrometrics.relativeHumidity(out.airF(), result.dewPointF());
    }

    /** Drops a removed unit's zone; the registry already dried its grate. */
    public void release(Dehumidifier dehumidifier) {
        airflow.forget(ZoneId.of(dehumidifier));
    }

    public ClimateSettings.Dehumidifier settings() { return settings; }
}
