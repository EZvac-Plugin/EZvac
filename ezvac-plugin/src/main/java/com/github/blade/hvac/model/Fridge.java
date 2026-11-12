package com.github.blade.hvac.model;

import com.github.blade.hvac.config.RefrigerationSettings;

import java.util.Map;

/** A fridge (BETA 1.0 Pilot 7): holds its barrel at 33 to 50 F, 37 by default. */
public final class Fridge extends Refrigerator {
    public Fridge(BlockKey position, BlockKey unit, BlockKey barrel, String label, int setpointF) {
        this(position, unit, barrel, label, setpointF, true, false, Double.NaN);
    }

    private Fridge(BlockKey position, BlockKey unit, BlockKey barrel, String label, int setpointF,
                   boolean powered, boolean running, double insideF) {
        super(position, unit, barrel, label, setpointF, powered, running, insideF);
    }

    @Override public Kind kind() { return Kind.FRIDGE; }
    @Override public int minimumF() { return RefrigerationSettings.FRIDGE_MINIMUM_F; }
    @Override public int maximumF() { return RefrigerationSettings.FRIDGE_MAXIMUM_F; }
    @Override public double heatFPerHour(RefrigerationSettings settings) { return settings.fridgeHeatFPerHour(); }

    public static Fridge fromMap(Map<String, Object> map) {
        Saved saved = Saved.read(map, "Fridge", 37);
        return new Fridge(saved.position(), saved.unit(), saved.barrel(), saved.label(), saved.setpoint(),
                saved.powered(), saved.running(), saved.insideF());
    }
}
