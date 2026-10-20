package com.github.blade.hvac.service;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.simulation.HumidityModel;
import com.github.blade.hvac.simulation.PerformanceModel;
import com.github.blade.hvac.simulation.ThermalModel;
import com.github.blade.hvac.simulation.ThermostatControl;
import org.bukkit.Tag;
import org.bukkit.World;

import java.util.*;
import java.util.logging.Level;

/** Authoritative phased controller: measure, integrate, decide, then actuate. */
public final class HvacController {
    public record CycleResult(List<EquipmentUnit> started, List<EquipmentUnit> stopped,
                              List<EquipmentUnit> running, int prunedDevices) {}

    public record GroupStatus(GroupId group, OperatingMode mode, double roomF, double targetF,
                              int equipment, int running, double capacity,
                              OperatingProfile profile, boolean controllerLoaded) {}

    private record Delivery(double fixedEquivalent, double variableEquivalent) {
        Delivery add(Delivery other) {
            return new Delivery(fixedEquivalent + other.fixedEquivalent,
                    variableEquivalent + other.variableEquivalent);
        }
    }

    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final OutdoorTemperatureService outdoor;
    private final AirflowService airflow;
    private final HvacSettings settings;
    private final DehumidifierService dehumidifiers;

    public HvacController(HvacPlugin plugin, HvacRegistry registry,
                          OutdoorTemperatureService outdoor, AirflowService airflow,
                          HvacSettings settings, DehumidifierService dehumidifiers) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.dehumidifiers = Objects.requireNonNull(dehumidifiers, "dehumidifiers");
    }

    public CycleResult cycle(long elapsedTicks) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsed ticks must be non-negative");
        simulatedTicks += elapsedTicks;
        HvacRegistry.PruneResult pruned = registry.pruneMissingLoadedDevices();
        for (GroupId group : pruned.invalidatedGroups()) airflow.invalidate(group);
        registry.reconcilePendingCleanup();

        LinkedHashSet<EquipmentUnit> started = new LinkedHashSet<>();
        LinkedHashSet<EquipmentUnit> stopped = new LinkedHashSet<>();
        Map<GroupId, Double> deliveredEquivalent = measureDeliveredCapacity(
                elapsedTicks, stopped);

        List<Thermostat> thermostats = new ArrayList<>(registry.thermostats());
        thermostats.sort(Comparator.comparing(Thermostat::position));
        // Nothing in the thermostat phase changes output ownership, so this
        // snapshot is invariant across the loop instead of rebuilt per device.
        Set<BlockKey> managedOutputs = managedOutputs();
        for (Thermostat thermostat : thermostats) {
            try {
                updateThermostat(thermostat, deliveredEquivalent, elapsedTicks, managedOutputs);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not update thermostat '"
                        + thermostat.id() + "' at " + thermostat.position(), exception);
                thermostat.setControlState(OperatingMode.IDLE, 0L);
            }
        }

        reconcileActuators(started, stopped);
        stopped.removeAll(started); // a repaired actuator emits one restart transition, not stop+start noise
        registry.markDirty();
        List<EquipmentUnit> running = registry.equipment().stream()
                .filter(EquipmentUnit::running).sorted(Comparator.comparing(EquipmentUnit::position)).toList();
        return new CycleResult(List.copyOf(started), List.copyOf(stopped), running,
                pruned.removedDevices());
    }

    /** Measures only equipment that physically delivered during the elapsed interval. */
    private Map<GroupId, Double> measureDeliveredCapacity(long elapsedTicks,
                                                           Set<EquipmentUnit> stopped) {
        Map<GroupId, Delivery> deliveries = new HashMap<>();
        Map<GroupId, Double> sharedVariableCommands = new HashMap<>();
        Map<GroupId, OperatingProfile> sharedProfiles = new HashMap<>();
        Set<BlockKey> reservedOutputs = new HashSet<>();
        List<EquipmentUnit> ordered = new ArrayList<>(registry.equipment());
        ordered.sort(Comparator.comparing(EquipmentUnit::position));
        for (EquipmentUnit unit : ordered) {
            try {
                Thermostat controller = registry.thermostatFor(unit.group());
                boolean validController = controller != null && isControllerLoaded(controller)
                        && controller.enabled() && controller.mode() == unit.operatingMode();
                BlockKey output = unit.ownedOutput();
                boolean delivering = validController && unit.isDelivering() && output != null
                        && reservedOutputs.add(output);
                if (!delivering) {
                    record(unit.stop(), unit, null, stopped);
                    unit.advanceMotor(0.0, elapsedTicks, settings);
                    continue;
                }
                double requested = unit.type() == EquipmentType.FURNACE ? 1.0
                        : sharedVariableCommands.computeIfAbsent(unit.group(), ignored ->
                        PerformanceModel.requestedVariableCapacity(
                                Math.abs(controller.roomF() - controller.targetF()), settings));
                OperatingProfile profile = unit.type() == EquipmentType.FURNACE
                        ? OperatingProfile.NORMAL : sharedProfiles.computeIfAbsent(
                        unit.group(), registry::profileFor);
                unit.advanceMotor(requested, elapsedTicks, settings, profile);
                double capacity = unit.operatingCapacity(settings);
                Delivery contribution = unit.type() == EquipmentType.FURNACE
                        ? new Delivery(capacity, 0.0) : new Delivery(0.0, capacity);
                deliveries.merge(unit.group(), contribution, Delivery::add);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Isolating failed HVAC equipment at "
                        + unit.position(), exception);
                record(unit.stop(), unit, null, stopped);
                unit.resetMotor();
            }
        }
        Map<GroupId, Double> equivalent = new HashMap<>();
        deliveries.forEach((group, delivery) -> equivalent.put(group,
                ThermalModel.aggregateIndependentCapacity(delivery.fixedEquivalent(),
                        delivery.variableEquivalent(), settings.maximumGroupCapacity())));
        return equivalent;
    }

    private void updateThermostat(Thermostat thermostat, Map<GroupId, Double> delivered,
                                  long elapsedTicks, Set<BlockKey> managedOutputs) {
        boolean authoritative = registry.thermostatFor(thermostat.group()) == thermostat;
        boolean controllerLoaded = authoritative && isControllerLoaded(thermostat);
        double outside = outdoor.driftBaselineFor(thermostat);
        OperatingMode intervalMode = controllerLoaded && thermostat.enabled()
                ? thermostat.mode() : OperatingMode.IDLE;
        double capacity = intervalMode == OperatingMode.IDLE ? 0.0
                : delivered.getOrDefault(thermostat.group(), 0.0);
        double boundary = switch (intervalMode) {
            case COOLING -> thermostat.targetF() - settings.coolingStopBelowF();
            case HEATING -> thermostat.targetF() + settings.heatingStopAboveF();
            case IDLE -> thermostat.targetF();
        };
        ThermalModel.Result thermal = ThermalModel.step(thermostat.roomF(), boundary, outside,
                intervalMode, capacity, elapsedTicks, settings);
        thermostat.setRoomF(thermal.temperatureF());
        if (!thermostat.pool()) updateHumidity(thermostat, intervalMode, capacity, elapsedTicks, managedOutputs);

        boolean coolingAvailable = controllerLoaded && hasAvailableEquipment(
                thermostat.group(), OperatingMode.COOLING, managedOutputs);
        boolean heatingAvailable = controllerLoaded && hasAvailableEquipment(
                thermostat.group(), OperatingMode.HEATING, managedOutputs);
        ThermostatControl.State control = ThermostatControl.advance(
                authoritative ? thermostat.mode() : OperatingMode.IDLE,
                authoritative ? thermostat.ticksInMode() : 0L,
                thermostat.roomF(), thermostat.targetF(),
                authoritative && controllerLoaded && thermostat.enabled(),
                coolingAvailable, heatingAvailable, elapsedTicks, settings);
        thermostat.setControlState(control.mode(), control.ticksInMode());
    }

    /** Pools have no air of their own to hold moisture; every air system does. */
    private void updateHumidity(Thermostat thermostat, OperatingMode mode, double capacity, long elapsedTicks,
                                Set<BlockKey> managedOutputs) {
        OutdoorTemperatureService.Outdoor out = outdoor.cachedClimateFor(thermostat);
        AirflowService.WaterExposure exposure = airflow.waterExposure(thermostat.group())
                .excluding(thermostat.group().worldId(), managedOutputs);
        HumidityModel.Loads loads = new HumidityModel.Loads(
                mode == OperatingMode.COOLING ? capacity : 0.0, exposure.wetFraction(),
                exposure.waterCells().length == 0 ? Double.NaN : waterTemperature(thermostat, exposure),
                dehumidifiers.scaleFor(thermostat.group()));
        HumidityModel.Result result = HumidityModel.step(thermostat.roomDewPointF(), thermostat.roomF(),
                out.dewPointF(), loads, elapsedTicks, outdoor.climate());
        thermostat.setRoomDewPointF(result.dewPointF());
        dehumidifiers.credit(thermostat.group(), result.dehumidifiedF());
    }

    /** Samples to estimate the temperature of a space's water: a heated pool evaporates faster than a pond. */
    private static final int WATER_SAMPLES = 4;
    /** Water temperature barely moves within seconds, so each space's is re-measured this often. */
    private static final long WATER_REFRESH_TICKS = 200L;

    private record WaterReading(double temperatureF, long measuredAt) {}

    private final Map<GroupId, WaterReading> waterReadings = new HashMap<>();
    private long simulatedTicks;

    private double waterTemperature(Thermostat thermostat, AirflowService.WaterExposure exposure) {
        WaterReading cached = waterReadings.get(thermostat.group());
        if (cached != null && simulatedTicks - cached.measuredAt() < WATER_REFRESH_TICKS) return cached.temperatureF();
        World world = thermostat.position().world();
        if (world == null) return Double.NaN;
        long[] cells = exposure.waterCells();
        int step = Math.max(1, cells.length / WATER_SAMPLES);
        double total = 0.0;
        int samples = 0;
        for (int index = 0; index < cells.length && samples < WATER_SAMPLES; index += step) {
            int x = AirflowService.unpackX(cells[index]);
            int z = AirflowService.unpackZ(cells[index]);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            total += airflow.waterTemperatureAt(world, x, AirflowService.unpackY(cells[index]), z);
            samples++;
        }
        double value = samples == 0 ? Double.NaN : total / samples;
        // Systems come and go; a stale entry only costs one map slot until the next sweep.
        if (waterReadings.size() > 2 * registry.thermostats().size() + 16) waterReadings.clear();
        waterReadings.put(thermostat.group(), new WaterReading(value, simulatedTicks));
        return value;
    }

    private boolean hasAvailableEquipment(GroupId group, OperatingMode mode,
                                          Set<BlockKey> managedOutputs) {
        for (EquipmentUnit unit : registry.equipmentFor(group)) {
            BlockKey output = unit.outputPosition();
            if (output != null && unit.canStart(mode, managedOutputs.contains(output))) return true;
        }
        return false;
    }

    private void reconcileActuators(Set<EquipmentUnit> started, Set<EquipmentUnit> stopped) {
        List<EquipmentUnit> ordered = new ArrayList<>(registry.equipment());
        ordered.sort(Comparator.comparingInt((EquipmentUnit unit) -> unit.running() ? 0 : 1)
                .thenComparing(EquipmentUnit::position));
        Set<BlockKey> managedOutputs = managedOutputs();
        Set<BlockKey> reserved = new HashSet<>();
        Set<EquipmentUnit> selected = Collections.newSetFromMap(new IdentityHashMap<>());

        for (EquipmentUnit unit : ordered) {
            Thermostat controller = registry.thermostatFor(unit.group());
            if (controller == null || !isControllerLoaded(controller) || !controller.enabled()
                    || controller.mode() == OperatingMode.IDLE || !unit.type().supports(controller.mode())) continue;
            BlockKey output = unit.outputPosition();
            if (output != null && unit.canStart(controller.mode(), managedOutputs.contains(output))
                    && reserved.add(output)) selected.add(unit);
        }

        // Every losing owner stops before a selected unit claims a shared output.
        for (EquipmentUnit unit : ordered) {
            if (selected.contains(unit)) continue;
            record(unit.stop(), unit, started, stopped);
        }
        // Rotation cleanup is a separate pass so swaps cannot depend on iteration order.
        for (EquipmentUnit unit : ordered) {
            if (!selected.contains(unit)) continue;
            BlockKey output = unit.outputPosition();
            if (unit.ownedOutput() != null && output != null && !unit.ownedOutput().equals(output))
                unit.retractOwnedOutput();
        }
        for (EquipmentUnit unit : ordered) {
            if (!selected.contains(unit)) continue;
            Thermostat controller = registry.thermostatFor(unit.group());
            try {
                record(unit.start(controller.mode()), unit, started, stopped);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not actuate HVAC equipment at "
                        + unit.position(), exception);
                record(unit.stop(), unit, started, stopped);
            }
        }
    }

    /** A snapshot: callers rely on it staying fixed while units start and stop. */
    private Set<BlockKey> managedOutputs() {
        return registry.ownedOutputs();
    }

    /**
     * getState() snapshots the whole sign tile entity, text included, and this
     * runs several times per equipment unit per cycle. The material tag answers
     * the same question - verified equivalent for every block material.
     */
    private static boolean isControllerLoaded(Thermostat thermostat) {
        if (!thermostat.position().isChunkLoaded()) return false;
        var location = thermostat.position().location();
        return location != null && Tag.ALL_SIGNS.isTagged(location.getBlock().getType());
    }

    private static void record(EquipmentUnit.Actuation actuation, EquipmentUnit unit,
                               Set<EquipmentUnit> started, Set<EquipmentUnit> stopped) {
        if (actuation == EquipmentUnit.Actuation.STARTED && started != null) started.add(unit);
        if (actuation == EquipmentUnit.Actuation.STOPPED && stopped != null) stopped.add(unit);
    }

    public GroupStatus status(GroupId group) {
        Thermostat thermostat = registry.thermostatFor(group);
        List<EquipmentUnit> units = registry.equipmentFor(group);
        int running = (int) units.stream().filter(EquipmentUnit::running).count();
        double fixedEquivalent = units.stream().filter(EquipmentUnit::running)
                .filter(unit -> unit.type() == EquipmentType.FURNACE)
                .mapToDouble(unit -> unit.operatingCapacity(settings)).sum();
        double variableEquivalent = units.stream().filter(EquipmentUnit::running)
                .filter(unit -> unit.type() != EquipmentType.FURNACE)
                .mapToDouble(unit -> unit.operatingCapacity(settings)).sum();
        return new GroupStatus(group, thermostat == null ? OperatingMode.IDLE : thermostat.mode(),
                thermostat == null ? Double.NaN : thermostat.roomF(),
                thermostat == null ? Double.NaN : thermostat.targetF(), units.size(), running,
                ThermalModel.aggregateIndependentCapacity(fixedEquivalent, variableEquivalent,
                        settings.maximumGroupCapacity()),
                registry.profileFor(group),
                thermostat != null && isControllerLoaded(thermostat));
    }
}
