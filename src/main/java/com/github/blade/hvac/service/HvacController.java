package com.github.blade.hvac.service;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.simulation.PerformanceModel;
import com.github.blade.hvac.simulation.ThermalModel;
import com.github.blade.hvac.simulation.ThermostatControl;
import org.bukkit.block.Sign;

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

    public HvacController(HvacPlugin plugin, HvacRegistry registry,
                          OutdoorTemperatureService outdoor, AirflowService airflow,
                          HvacSettings settings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public CycleResult cycle(long elapsedTicks) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsed ticks must be non-negative");
        HvacRegistry.PruneResult pruned = registry.pruneMissingLoadedDevices();
        for (GroupId group : pruned.invalidatedGroups()) airflow.invalidate(group);
        registry.reconcilePendingCleanup();

        LinkedHashSet<EquipmentUnit> started = new LinkedHashSet<>();
        LinkedHashSet<EquipmentUnit> stopped = new LinkedHashSet<>();
        Map<GroupId, Double> deliveredEquivalent = measureDeliveredCapacity(
                elapsedTicks, stopped);

        List<Thermostat> thermostats = new ArrayList<>(registry.thermostats());
        thermostats.sort(Comparator.comparing(Thermostat::position));
        for (Thermostat thermostat : thermostats) {
            try {
                updateThermostat(thermostat, deliveredEquivalent, elapsedTicks);
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
                                  long elapsedTicks) {
        boolean authoritative = registry.thermostatFor(thermostat.group()) == thermostat;
        boolean controllerLoaded = authoritative && isControllerLoaded(thermostat);
        double outside = outdoor.temperatureFor(thermostat);
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

        Set<BlockKey> managedOutputs = managedOutputs();
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

    private Set<BlockKey> managedOutputs() {
        Set<BlockKey> outputs = new HashSet<>();
        for (EquipmentUnit unit : registry.equipment())
            if (unit.ownedOutput() != null) outputs.add(unit.ownedOutput());
        for (EquipmentUnit unit : registry.pendingCleanup())
            if (unit.ownedOutput() != null) outputs.add(unit.ownedOutput());
        return outputs;
    }

    private static boolean isControllerLoaded(Thermostat thermostat) {
        if (!thermostat.position().isChunkLoaded()) return false;
        var location = thermostat.position().location();
        return location != null && location.getBlock().getState() instanceof Sign;
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
