package com.github.blade.hvac.model;

import com.github.blade.hvac.config.ClimateSettings;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A standalone dehumidifier: an iron block on a waxed copper grate, with a
 * control sign on the iron block. The water it pulls from the air fills a
 * tank; a full tank shows as a waterlogged grate while it empties.
 */
public final class Dehumidifier extends ClimateUnit {
    /** 0 empty .. 1 full. */
    private double tank;
    /** Ticks of emptying left; above 0 only while a full tank drains. */
    private long emptyingTicks;
    /** The air it works on is too dry for its coil; worked out each cycle, never saved. */
    private boolean tooCold;
    private static final double TOO_COLD_HYSTERESIS_F = 1.0;

    public Dehumidifier(BlockKey position, BlockKey unit, BlockKey grate, String label, int setpoint) {
        this(position, unit, grate, label, setpoint, true, false, 0.0, 0L, Double.NaN);
    }

    private Dehumidifier(BlockKey position, BlockKey unit, BlockKey grate, String label, int setpoint,
                         boolean powered, boolean running, double tank, long emptyingTicks, double zoneDewPointF) {
        super(position, unit, grate, label, setpoint, powered, running && emptyingTicks <= 0, zoneDewPointF);
        this.tank = Double.isFinite(tank) ? Math.max(0.0, Math.min(1.0, tank)) : 0.0;
        this.emptyingTicks = Math.max(0L, emptyingTicks);
    }

    @Override public Kind kind() { return Kind.DEHUMIDIFIER; }
    @Override public Material partMaterial() { return ClimateVent.WATER_VENT; }

    public BlockKey grate() { return part(); }
    public double tank() { return tank; }
    public long emptyingTicks() { return emptyingTicks; }
    public boolean emptying() { return emptyingTicks > 0; }
    public boolean tooCold() { return tooCold; }

    /**
     * Follows the dew point of the space it works on. At or below its coil's
     * dry dew point there is nothing for the coil to condense, so it can run
     * but can't dry; 1 F of hysteresis keeps its sign from flickering.
     */
    public void updateTooCold(double dewPointF, ClimateSettings.Dehumidifier settings) {
        if (!Double.isFinite(dewPointF)) {
            tooCold = false;
            return;
        }
        tooCold = dewPointF <= settings.dryDewPointF() + (tooCold ? TOO_COLD_HYSTERESIS_F : 0.0);
    }

    @Override
    public State state() {
        if (powered() && emptying()) return State.EMPTYING;
        return super.state();
    }

    @Override
    public boolean control(double humidity, ClimateSettings settings) {
        return control(humidity, settings.dehumidifier());
    }

    /**
     * Dries once RH is the hysteresis above the setpoint and runs until it is
     * back down to the setpoint. Never while its tank is emptying.
     */
    public boolean control(double humidity, ClimateSettings.Dehumidifier settings) {
        boolean wanted = powered() && !emptying() && Double.isFinite(humidity)
                && (running ? humidity > setpoint() : humidity > setpoint() + settings.hysteresis());
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
        if (!powered() || !emptying() || elapsedTicks <= 0) return false;
        emptyingTicks -= elapsedTicks;
        if (emptyingTicks > 0) return false;
        emptyingTicks = 0;
        tank = 0.0;
        return true;
    }

    /** Shows a full tank as a waterlogged grate, or dries it. */
    public void setGrateWater(boolean water) { setPartWater(water); }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        writeCommon(map, "grate");
        map.put("tank", tank);
        map.put("emptyingTicks", emptyingTicks);
        writeZone(map);
        return map;
    }

    public static Dehumidifier fromMap(Map<String, Object> map) {
        Dehumidifier dehumidifier = new Dehumidifier(BlockKey.read(map, ""), BlockKey.read(map, "unit"),
                BlockKey.read(map, "grate"),
                text(map, "label", "Dehumidifier"), (int) Math.round(number(map, "setpoint", 45.0)),
                bool(map, "powered", true), bool(map, "running", false),
                number(map, "tank", 0.0), (long) number(map, "emptyingTicks", 0.0),
                number(map, "zoneDewPointF", Double.NaN));
        dehumidifier.readZoneAir(map);
        return dehumidifier;
    }
}
