package com.github.blade.hvac.model;

import com.github.blade.hvac.config.RefrigerationSettings;
import com.github.blade.hvac.simulation.ThermalModel;
import org.bukkit.Material;

import java.util.Map;

/**
 * A mini fridge (BETA 1.0 Pilot 7): a barrel with the control sign on its
 * side, its compressor inside, so no iron block. It holds a fridge's range
 * (33 to 50 F) but less: seven slots of food along the middle row, a gap,
 * and a chiller box in the last slot, which sits about 10 F below the
 * setpoint (there's no separate control for it, as on a real one). The rest
 * of the barrel is its walls. Smaller, it cools at half a fridge's pace,
 * an opening lets in half as much again, and it warms its room half as much.
 * It never frosts.
 *
 * <p>The model is pure, so it can be tested without a server.
 */
public final class MiniFridge extends Refrigerator {
    /** The barrel's slots: rows of 9; food in the middle row's first seven, then a gap, then the chiller box. */
    public static final int FIRST_FOOD = 9, LAST_FOOD = 15, GAP = 16, CHILLER = 17;
    /** How far below its inside the chiller box sits while it runs. */
    public static final double CHILLER_BELOW_F = 10.0;
    /** How quickly the chiller box closes on where it should be, per in-game hour. */
    static final double CHILLER_RATE = 6.0;
    static final double PULL = 0.5, DOOR = 1.5, HEAT = 0.5;

    /** The chiller box's inside, in F; NaN until first stepped. */
    private double chillerF;

    public MiniFridge(BlockKey position, BlockKey barrel, String label, int setpointF) {
        this(position, barrel, label, setpointF, true, false, Double.NaN, Double.NaN);
    }

    private MiniFridge(BlockKey position, BlockKey barrel, String label, int setpointF, boolean powered,
                       boolean running, double insideF, double chillerF) {
        super(position, barrel, barrel, label, setpointF, powered, running, insideF);
        this.chillerF = Double.isFinite(chillerF) ? ThermalModel.clampTemperature(chillerF) : Double.NaN;
    }

    @Override public Kind kind() { return Kind.MINI_FRIDGE; }
    @Override public int minimumF() { return RefrigerationSettings.FRIDGE_MINIMUM_F; }
    @Override public int maximumF() { return RefrigerationSettings.FRIDGE_MAXIMUM_F; }
    @Override public double heatFPerHour(RefrigerationSettings settings) { return settings.fridgeHeatFPerHour() * HEAT; }
    @Override public Material unitMaterial() { return BARREL; }
    @Override protected double pullFactor(RefrigerationSettings settings) { return PULL; }
    @Override protected double doorFactor() { return DOOR; }

    public double chillerF() { return chillerF; }

    /** Whether food may lie in this slot of its barrel: the seven along the middle and the chiller box. */
    public static boolean holdsFood(int slot) {
        return (slot >= FIRST_FOOD && slot <= LAST_FOOD) || slot == CHILLER;
    }

    @Override
    public double insideAt(BlockKey barrel, int slot) {
        return slot == CHILLER && barrel().equals(barrel) ? chillerF : insideAt(barrel);
    }

    /**
     * The chiller box follows the inside, sitting {@link #CHILLER_BELOW_F}
     * under it while it is switched on, and just the inside's temperature
     * while it is off; it gets there within minutes, the coil being in it.
     */
    @Override
    protected void stepCompartments(double roomF, double hours, RefrigerationSettings settings) {
        double inside = insideF();
        if (!Double.isFinite(chillerF)) chillerF = inside;
        double target = switchedOn() && settings.enabled() ? inside - CHILLER_BELOW_F : inside;
        chillerF += (target - chillerF) * (1.0 - Math.exp(-CHILLER_RATE * hours));
        chillerF = ThermalModel.clampTemperature(chillerF);
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        if (Double.isFinite(chillerF)) map.put("chillerF", chillerF);
        return map;
    }

    public static MiniFridge fromMap(Map<String, Object> map) {
        Saved saved = Saved.read(map, "Mini-fridge", 37);
        return new MiniFridge(saved.position(), saved.barrel(), saved.label(), saved.setpoint(), saved.powered(),
                saved.running(), saved.insideF(), number(map, "chillerF", Double.NaN));
    }
}
