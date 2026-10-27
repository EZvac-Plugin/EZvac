package com.github.blade.hvac.model;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.simulation.AirQualityModel;
import com.github.blade.hvac.simulation.ThermalModel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

import java.util.Map;
import java.util.Objects;

/**
 * A standalone climate device: an iron block with a control sign and one
 * more block: a dehumidifier's grate or a humidifier's slab, which show its
 * water, or an air purifier's filter (3.2). It needs no system. Its own
 * airflow zone decides which space it works on: a system's room when the
 * zone touches one, otherwise the zone itself (a shed, a basement), whose
 * moisture and air it then keeps here.
 *
 * <p>The control logic is pure, so it can be tested without a server.
 */
public abstract sealed class ClimateUnit permits Dehumidifier, Humidifier, AirPurifier {
    public enum Kind {
        DEHUMIDIFIER("Dehumidifier"), HUMIDIFIER("Humidifier"), AIR_PURIFIER("Air purifier");

        private final String displayName;
        Kind(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }

    /** EMPTYING is a dehumidifier's; NO_FILTER and FILTER_SPENT an air purifier's (3.2). */
    public enum State { OFF, IDLE, RUNNING, EMPTYING, NO_FILTER, FILTER_SPENT }

    private final BlockKey position;
    private final BlockKey unit;
    private final BlockKey part;
    private String label;
    private int setpoint;
    private boolean powered;
    protected boolean running;
    /** Moisture of its own zone when no system's room holds it; NaN until first simulated. */
    private double zoneDewPointF;
    /** The system whose room its zone touches, or null; worked out each cycle, never saved. */
    private SystemId servedSystem;
    /** RH of the space it serves at the last cycle, for its sign; never saved. */
    private double lastHumidity = Double.NaN;
    /** What is in its own zone's air when no system's room holds it (3.2); unknown until first simulated. */
    private AirQualityModel.Air zoneAir = AirQualityModel.Air.UNKNOWN;
    /** The air of the space it serves at the last cycle, for its sign; never saved. */
    private AirQualityModel.Air lastAir = AirQualityModel.Air.UNKNOWN;

    protected ClimateUnit(BlockKey position, BlockKey unit, BlockKey part, String label, int setpoint,
                          boolean powered, boolean running, double zoneDewPointF) {
        this.position = Objects.requireNonNull(position, "position");
        this.unit = Objects.requireNonNull(unit, "unit");
        this.part = Objects.requireNonNull(part, "part");
        if (!position.worldId().equals(unit.worldId()) || !unit.worldId().equals(part.worldId()))
            throw new IllegalArgumentException(getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT)
                    + " parts are in different worlds");
        this.label = Thermostat.validateId(label);
        this.setpoint = clampSetpoint(setpoint);
        this.powered = powered;
        this.running = running && powered;
        setZoneDewPointF(zoneDewPointF);
    }

    public abstract Kind kind();

    /** The control sign; the key the registry files it under. */
    public BlockKey position() { return position; }
    /** The iron block. */
    public BlockKey unit() { return unit; }
    /** The block that shows its water: a dehumidifier's grate, a humidifier's slab. */
    public BlockKey part() { return part; }
    public String label() { return label; }
    public void setLabel(String label) { this.label = Thermostat.validateId(label); }
    public int setpoint() { return setpoint; }
    public boolean powered() { return powered; }
    public boolean running() { return running; }
    public double zoneDewPointF() { return zoneDewPointF; }
    public SystemId servedSystem() { return servedSystem; }
    public void setServedSystem(SystemId system) { this.servedSystem = system; }
    public double lastHumidity() { return lastHumidity; }
    public void setLastHumidity(double humidity) { this.lastHumidity = humidity; }
    public AirQualityModel.Air zoneAir() { return zoneAir; }
    public void setZoneAir(AirQualityModel.Air air) { this.zoneAir = air == null ? AirQualityModel.Air.UNKNOWN : air; }
    public AirQualityModel.Air lastAir() { return lastAir; }
    public void setLastAir(AirQualityModel.Air air) { this.lastAir = air == null ? AirQualityModel.Air.UNKNOWN : air; }

    /**
     * Whether it is gone when its part is: a dehumidifier without its grate is
     * broken, but an air purifier whose filter was taken out just waits for a
     * new one.
     */
    public boolean partRequired() { return true; }

    public State state() {
        if (!powered) return State.OFF;
        return running ? State.RUNNING : State.IDLE;
    }

    public void setSetpoint(int setpoint) { this.setpoint = clampSetpoint(setpoint); }

    /** The lowest setpoint it accepts: 1% RH; an air purifier's is an AQI. */
    protected int minimumSetpoint() { return 1; }
    protected int maximumSetpoint() { return 99; }

    /** Off stops it at once; anything else it holds stays exactly as it is until it is back on. */
    public void setPowered(boolean powered) {
        this.powered = powered;
        if (!powered) running = false;
    }

    public void setZoneDewPointF(double dewPointF) {
        this.zoneDewPointF = Double.isFinite(dewPointF) ? ThermalModel.clampTemperature(dewPointF) : Double.NaN;
    }

    /**
     * Decides whether to run, from the space's humidity, with hysteresis so it
     * does not chatter around one value.
     *
     * @return true when the running state changed
     */
    public abstract boolean control(double humidity, ClimateSettings settings);

    /** What its water block must be, so the registry can tell when it was broken. */
    public abstract Material partMaterial();

    /**
     * Shows water in its water block, or dries it. Set without physics, and
     * the listener cancels any flow from it, so the water stays inside the
     * block instead of flooding the room. Never loads a chunk.
     */
    public void setPartWater(boolean water) {
        if (!part.isChunkLoaded()) return;
        Location location = part.location();
        if (location == null) return;
        Block block = location.getBlock();
        if (block.getType() != partMaterial()
                || !(block.getBlockData() instanceof Waterlogged data) || data.isWaterlogged() == water) return;
        data.setWaterlogged(water);
        block.setBlockData(data, false);
    }

    public abstract Map<String, Object> toMap();

    /** The fields every unit saves, in the order they are written. */
    protected void writeCommon(Map<String, Object> map, String partPrefix) {
        position.write(map, "");
        unit.write(map, "unit");
        part.write(map, partPrefix);
        map.put("label", label);
        map.put("setpoint", setpoint);
        map.put("powered", powered);
        map.put("running", running);
    }

    protected void writeZone(Map<String, Object> map) {
        if (Double.isFinite(zoneDewPointF)) map.put("zoneDewPointF", zoneDewPointF);
        zoneAir.write(map, "zone");
    }

    /** Restores what {@link #writeZone} saved beyond the dew point, which the constructor takes. */
    protected void readZoneAir(Map<String, Object> map) {
        setZoneAir(AirQualityModel.Air.read(map, "zone"));
    }

    // Only ever called with constants by subclasses, so it is safe from the constructor.
    private int clampSetpoint(int value) {
        return Math.max(minimumSetpoint(), Math.min(maximumSetpoint(), value));
    }

    protected static String text(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    protected static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue() : fallback;
    }

    protected static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }
}
