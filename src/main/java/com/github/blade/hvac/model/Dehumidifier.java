package com.github.blade.hvac.model;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.simulation.ThermalModel;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A standalone dehumidifier: an iron block on a waxed copper grate, with a
 * control sign on the iron block. It needs no system. Its own small airflow
 * zone decides which space it dries: a system's room when the zone touches
 * one, otherwise the zone itself (a shed, a basement), whose moisture it
 * then keeps here.
 *
 * <p>The control logic is pure and lives here so it can be tested without a
 * server: when to run, filling the tank, and the emptying cycle.
 */
public final class Dehumidifier {
    public enum State { OFF, IDLE, RUNNING, EMPTYING }

    private final BlockKey position;
    private final BlockKey unit;
    private final BlockKey grate;
    private final String label;
    private int setpoint;
    private boolean powered;
    private boolean running;
    /** 0 empty .. 1 full. */
    private double tank;
    /** Ticks of emptying left; above 0 only while a full tank drains. */
    private long emptyingTicks;
    /** Moisture of its own zone when no system's room holds it; NaN until first simulated. */
    private double zoneDewPointF = Double.NaN;
    /** The system whose room its zone touches, or null; worked out each cycle, never saved. */
    private GroupId servedGroup;
    /** RH of the space it serves at the last cycle, for its sign; never saved. */
    private double lastHumidity = Double.NaN;

    public Dehumidifier(BlockKey position, BlockKey unit, BlockKey grate, String label, int setpoint) {
        this(position, unit, grate, label, setpoint, true, false, 0.0, 0L, Double.NaN);
    }

    private Dehumidifier(BlockKey position, BlockKey unit, BlockKey grate, String label, int setpoint,
                         boolean powered, boolean running, double tank, long emptyingTicks, double zoneDewPointF) {
        this.position = Objects.requireNonNull(position, "position");
        this.unit = Objects.requireNonNull(unit, "unit");
        this.grate = Objects.requireNonNull(grate, "grate");
        if (!position.worldId().equals(unit.worldId()) || !unit.worldId().equals(grate.worldId()))
            throw new IllegalArgumentException("dehumidifier parts are in different worlds");
        this.label = Thermostat.validateId(label);
        this.setpoint = clampSetpoint(setpoint);
        this.powered = powered;
        this.running = running && powered && emptyingTicks <= 0;
        this.tank = Double.isFinite(tank) ? Math.max(0.0, Math.min(1.0, tank)) : 0.0;
        this.emptyingTicks = Math.max(0L, emptyingTicks);
        this.zoneDewPointF = Double.isFinite(zoneDewPointF) ? ThermalModel.clampTemperature(zoneDewPointF) : Double.NaN;
    }

    /** The control sign; the key the registry files it under. */
    public BlockKey position() { return position; }
    /** The iron block. */
    public BlockKey unit() { return unit; }
    public BlockKey grate() { return grate; }
    public String label() { return label; }
    public int setpoint() { return setpoint; }
    public boolean powered() { return powered; }
    public boolean running() { return running; }
    public double tank() { return tank; }
    public long emptyingTicks() { return emptyingTicks; }
    public boolean emptying() { return emptyingTicks > 0; }
    public double zoneDewPointF() { return zoneDewPointF; }
    public GroupId servedGroup() { return servedGroup; }
    public void setServedGroup(GroupId group) { this.servedGroup = group; }
    public double lastHumidity() { return lastHumidity; }
    public void setLastHumidity(double humidity) { this.lastHumidity = humidity; }

    public State state() {
        if (!powered) return State.OFF;
        if (emptying()) return State.EMPTYING;
        return running ? State.RUNNING : State.IDLE;
    }

    public void setSetpoint(int setpoint) { this.setpoint = clampSetpoint(setpoint); }

    /** Off stops drying at once; the tank and any emptying stay exactly as they are until it is back on. */
    public void setPowered(boolean powered) {
        this.powered = powered;
        if (!powered) running = false;
    }

    public void setZoneDewPointF(double dewPointF) {
        this.zoneDewPointF = Double.isFinite(dewPointF) ? ThermalModel.clampTemperature(dewPointF) : Double.NaN;
    }

    /**
     * Decides whether to dry, from the space's humidity. It starts once RH is
     * the hysteresis above the setpoint and runs until it is back down to the
     * setpoint, so it does not chatter around one value.
     *
     * @return true when the running state changed
     */
    public boolean control(double humidity, ClimateSettings.Dehumidifier settings) {
        boolean wanted = powered && !emptying() && Double.isFinite(humidity)
                && (running ? humidity > setpoint : humidity > setpoint + settings.hysteresis());
        boolean changed = wanted != running;
        running = wanted;
        return changed;
    }

    /**
     * Adds water to the tank. A full tank stops the unit and starts emptying.
     *
     * @return true when this filled the tank
     */
    public boolean fill(double fraction, ClimateSettings.Dehumidifier settings) {
        if (!Double.isFinite(fraction) || fraction <= 0.0 || emptying()) return false;
        tank = Math.min(1.0, tank + fraction);
        if (tank < 1.0) return false;
        running = false;
        emptyingTicks = settings.emptySeconds() * 20L;
        return true;
    }

    /**
     * Counts down an emptying tank while the unit is powered.
     *
     * @return true when the tank just finished emptying
     */
    public boolean advanceEmptying(long elapsedTicks) {
        if (!powered || !emptying() || elapsedTicks <= 0) return false;
        emptyingTicks -= elapsedTicks;
        if (emptyingTicks > 0) return false;
        emptyingTicks = 0;
        tank = 0.0;
        return true;
    }

    /**
     * Shows a full tank as a waterlogged grate, or dries it. Set without
     * physics, and the listener cancels any flow from it, so the water stays
     * inside the grate instead of flooding the room. Never loads a chunk.
     */
    public void setGrateWater(boolean water) {
        if (!grate.isChunkLoaded()) return;
        Location location = grate.location();
        if (location == null) return;
        Block block = location.getBlock();
        if (block.getType() != ClimateVent.WATER_VENT
                || !(block.getBlockData() instanceof Waterlogged data) || data.isWaterlogged() == water) return;
        data.setWaterlogged(water);
        block.setBlockData(data, false);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        unit.write(map, "unit");
        grate.write(map, "grate");
        map.put("label", label);
        map.put("setpoint", setpoint);
        map.put("powered", powered);
        map.put("running", running);
        map.put("tank", tank);
        map.put("emptyingTicks", emptyingTicks);
        if (Double.isFinite(zoneDewPointF)) map.put("zoneDewPointF", zoneDewPointF);
        return map;
    }

    public static Dehumidifier fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        Object label = map.get("label");
        return new Dehumidifier(position, BlockKey.read(map, "unit"), BlockKey.read(map, "grate"),
                label instanceof String text && !text.isBlank() ? text : "Dehumidifier",
                (int) Math.round(number(map, "setpoint", 45.0)),
                bool(map, "powered", true), bool(map, "running", false),
                number(map, "tank", 0.0), (long) number(map, "emptyingTicks", 0.0),
                number(map, "zoneDewPointF", Double.NaN));
    }

    private static int clampSetpoint(int value) {
        return Math.max(1, Math.min(99, value));
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
