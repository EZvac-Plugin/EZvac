package com.github.blade.hvac.model;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.simulation.PerformanceModel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Dispenser-backed HVAC actuator with explicit, persisted fluid ownership. */
public final class EquipmentUnit {
    public enum Actuation { STARTED, STOPPED, UNCHANGED, PENDING }

    private final BlockKey position;
    private GroupId group;
    private final EquipmentType type;
    private boolean enabled;
    private boolean running;
    private OperatingMode operatingMode;
    private double motorRpm;
    private BlockKey ownedOutput;

    public EquipmentUnit(BlockKey position, String groupLabel, EquipmentType type) {
        this(position, GroupId.of(position, groupLabel), type, true, false,
                OperatingMode.IDLE, 0.0, null);
    }

    private EquipmentUnit(BlockKey position, GroupId group, EquipmentType type, boolean enabled,
                          boolean running, OperatingMode operatingMode, double motorRpm,
                          BlockKey ownedOutput) {
        this.position = Objects.requireNonNull(position, "position");
        this.group = Objects.requireNonNull(group, "group");
        this.type = Objects.requireNonNull(type, "type");
        if (!position.worldId().equals(group.worldId()))
            throw new IllegalArgumentException("equipment and group worlds differ");
        this.enabled = enabled;
        this.running = running;
        this.operatingMode = running ? Objects.requireNonNull(operatingMode, "operatingMode")
                : OperatingMode.IDLE;
        this.motorRpm = Double.isFinite(motorRpm) ? Math.max(0.0, motorRpm) : 0.0;
        this.ownedOutput = ownedOutput;
    }

    public BlockKey position() { return position; }
    public GroupId group() { return group; }
    public EquipmentType type() { return type; }
    public boolean enabled() { return enabled; }
    public boolean running() { return running; }
    public OperatingMode operatingMode() { return operatingMode; }
    public double motorRpm() { return motorRpm; }
    public BlockKey ownedOutput() { return ownedOutput; }
    public boolean hasOwnedOutput() { return ownedOutput != null; }

    public void setGroup(String label) { this.group = GroupId.of(position, label); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public double operatingCapacity(HvacSettings settings) {
        if (!running) return 0.0;
        return type == EquipmentType.FURNACE ? 1.0
                : PerformanceModel.variableCapacityFromRpm(motorRpm, settings);
    }

    public BlockKey outputPosition() {
        if (!position.isChunkLoaded()) return null;
        Location source = position.location();
        if (source == null) return null;
        Block dispenser = source.getBlock();
        if (dispenser.getType() != Material.DISPENSER
                || !(dispenser.getBlockData() instanceof Directional directional)) return null;
        Block front = dispenser.getRelative(directional.getFacing());
        if (!front.getWorld().isChunkLoaded(front.getX() >> 4, front.getZ() >> 4)) return null;
        return BlockKey.of(front.getLocation());
    }

    public boolean blockExists() {
        if (!position.isChunkLoaded()) return true;
        Location location = position.location();
        return location != null && location.getBlock().getType() == Material.DISPENSER;
    }

    /** True only while the registered dispenser still owns its current, loaded output. */
    public boolean isDelivering() {
        if (!running || !enabled || !type.supports(operatingMode) || !position.isChunkLoaded()) return false;
        BlockKey currentOutput = outputPosition();
        if (currentOutput == null || ownedOutput == null || !ownedOutput.equals(currentOutput)
                || !ownedOutput.isChunkLoaded()) return false;
        Location location = ownedOutput.location();
        return location != null && location.getBlock().getType() == type.outputMaterial();
    }

    /** Read-only availability check; never force-loads either source or output chunks. */
    public boolean canStart(OperatingMode mode, boolean managedFluidAtOutput) {
        if (!enabled || !type.supports(mode) || !position.isChunkLoaded()) return false;
        BlockKey output = outputPosition();
        if (output == null) return false;
        Location outputLocation = output.location();
        if (outputLocation == null) return false;
        Material material = outputLocation.getBlock().getType();
        boolean usable = material.isAir() || (managedFluidAtOutput
                && (material == Material.WATER || material == Material.LAVA));
        if (ownedOutput == null) return usable;
        if (ownedOutput.equals(output)) return material == type.outputMaterial() || material.isAir();
        return ownedOutput.isChunkLoaded() && usable;
    }

    public Actuation start(OperatingMode requestedMode) {
        boolean wasRunning = running;
        if (!enabled || !type.supports(requestedMode)) return stop();
        BlockKey output = outputPosition();
        if (output == null) {
            running = false;
            operatingMode = OperatingMode.IDLE;
            return wasRunning ? Actuation.STOPPED : Actuation.PENDING;
        }
        if (ownedOutput != null && !ownedOutput.equals(output) && !retractOwnedOutput()) {
            running = false;
            operatingMode = OperatingMode.IDLE;
            return wasRunning ? Actuation.STOPPED : Actuation.PENDING;
        }

        Location location = output.location();
        if (location == null) return Actuation.PENDING;
        Block front = location.getBlock();
        if (ownedOutput != null && ownedOutput.equals(output)) {
            if (front.getType() == type.outputMaterial()) {
                running = true;
                operatingMode = requestedMode;
                return wasRunning ? Actuation.UNCHANGED : Actuation.STARTED;
            }
            ownedOutput = null; // externally replaced; never claim it implicitly
        }
        if (!front.getType().isAir()) {
            running = false;
            operatingMode = OperatingMode.IDLE;
            return wasRunning ? Actuation.STOPPED : Actuation.UNCHANGED;
        }
        front.setType(type.outputMaterial(), true);
        ownedOutput = output;
        running = true;
        operatingMode = requestedMode;
        return wasRunning ? Actuation.UNCHANGED : Actuation.STARTED;
    }

    public Actuation stop() {
        boolean wasRunning = running;
        running = false;
        operatingMode = OperatingMode.IDLE;
        boolean cleaned = retractOwnedOutput();
        if (wasRunning) return Actuation.STOPPED;
        return cleaned || ownedOutput == null ? Actuation.UNCHANGED : Actuation.PENDING;
    }

    public boolean retractOwnedOutput() {
        if (ownedOutput == null || !ownedOutput.isChunkLoaded()) return false;
        Location location = ownedOutput.location();
        if (location == null) return false;
        Block block = location.getBlock();
        if (block.getType() == type.outputMaterial()) block.setType(Material.AIR, true);
        ownedOutput = null;
        return true;
    }

    public boolean releaseOwnershipAt(BlockKey changedBlock) {
        if (ownedOutput == null || !ownedOutput.equals(changedBlock)) return false;
        ownedOutput = null;
        return true;
    }

    public void advanceMotor(double requestedCapacity, long elapsedTicks, HvacSettings settings) {
        advanceMotor(requestedCapacity, elapsedTicks, settings, OperatingProfile.NORMAL);
    }

    public void advanceMotor(double requestedCapacity, long elapsedTicks, HvacSettings settings,
                             OperatingProfile profile) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsed ticks must be non-negative");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(profile, "profile");
        double seconds = elapsedTicks / 20.0;
        if (type == EquipmentType.FURNACE) {
            motorRpm = PerformanceModel.approach(motorRpm,
                    running ? PerformanceModel.FURNACE_MAXIMUM_RPM : 0.0, seconds,
                    PerformanceModel.FURNACE_RAMP_UP_PER_SECOND,
                    PerformanceModel.FURNACE_RAMP_DOWN_PER_SECOND);
        } else {
            double target = running
                    ? PerformanceModel.variableTargetRpm(requestedCapacity, profile, settings) : 0.0;
            motorRpm = PerformanceModel.approach(motorRpm, target, seconds,
                    settings.variableRampUpRpmPerSecond(),
                    settings.variableRampDownRpmPerSecond());
        }
    }

    public void resetMotor() { motorRpm = 0.0; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("group", group.label());
        map.put("equipmentType", type.name());
        position.write(map, "");
        map.put("enabled", enabled);
        map.put("running", running);
        map.put("operatingMode", operatingMode.name());
        map.put("motorRpm", motorRpm);
        if (ownedOutput != null) ownedOutput.write(map, "output");
        return map;
    }

    public static EquipmentUnit fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        String group = string(map, "group", string(map, "label", null));
        EquipmentType type = EquipmentType.parse(map.get("equipmentType"));
        boolean running = bool(map, "running", false);
        BlockKey output = null;
        try { output = BlockKey.read(map, "output"); }
        catch (IllegalArgumentException ignored) {
            try { output = BlockKey.read(map, "water"); }
            catch (IllegalArgumentException ignoredLegacy) { /* no owned output */ }
        }
        OperatingMode mode = OperatingMode.parse(map.get("operatingMode"));
        if (running && mode == OperatingMode.IDLE) mode = OperatingMode.COOLING;
        return new EquipmentUnit(position, GroupId.of(position, group), type,
                bool(map, "enabled", true), running, mode,
                number(map, "motorRpm", 0.0), output);
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue() : fallback;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }
}
