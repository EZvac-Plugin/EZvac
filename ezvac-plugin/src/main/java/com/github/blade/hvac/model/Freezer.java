package com.github.blade.hvac.model;

import com.github.blade.hvac.config.RefrigerationSettings;

import java.util.Map;

/** A freezer (BETA 1.0 Pilot 7): holds its barrel at -10 to 20 F, 0 by default. */
public final class Freezer extends Refrigerator {
    public Freezer(BlockKey position, BlockKey unit, BlockKey barrel, String label, int setpointF) {
        this(position, unit, barrel, label, setpointF, true, false, Double.NaN);
    }

    private Freezer(BlockKey position, BlockKey unit, BlockKey barrel, String label, int setpointF,
                    boolean powered, boolean running, double insideF) {
        super(position, unit, barrel, label, setpointF, powered, running, insideF);
    }

    @Override public Kind kind() { return Kind.FREEZER; }
    @Override public int minimumF() { return RefrigerationSettings.FREEZER_MINIMUM_F; }
    @Override public int maximumF() { return RefrigerationSettings.FREEZER_MAXIMUM_F; }
    @Override public double heatFPerHour(RefrigerationSettings settings) { return settings.freezerHeatFPerHour(); }

    public static Freezer fromMap(Map<String, Object> map) {
        Saved saved = Saved.read(map, "Freezer", 0);
        return new Freezer(saved.position(), saved.unit(), saved.barrel(), saved.label(), saved.setpoint(),
                saved.powered(), saved.running(), saved.insideF());
    }
}
