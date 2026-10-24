package com.github.blade.hvac.model;

import com.github.blade.hvac.config.ClimateSettings;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A standalone humidifier: an iron block with a waxed cut copper slab on top
 * and a control sign on the iron block. It is plumbed, like every EZvac
 * device: it never runs dry and consumes nothing. While it runs the slab is
 * waterlogged and bubbles.
 */
public final class Humidifier extends ClimateUnit {
    /** The block on top of the iron block: a bottom slab, so the water sits above it. */
    public static final Material TOP = Material.WAXED_CUT_COPPER_SLAB;

    public Humidifier(BlockKey position, BlockKey unit, BlockKey slab, String label, int setpoint) {
        this(position, unit, slab, label, setpoint, true, false, Double.NaN);
    }

    private Humidifier(BlockKey position, BlockKey unit, BlockKey slab, String label, int setpoint,
                       boolean powered, boolean running, double zoneDewPointF) {
        super(position, unit, slab, label, setpoint, powered, running, zoneDewPointF);
    }

    @Override public Kind kind() { return Kind.HUMIDIFIER; }
    @Override public Material partMaterial() { return TOP; }

    public BlockKey slab() { return part(); }

    @Override
    public boolean control(double humidity, ClimateSettings settings) {
        return control(humidity, settings.humidifier());
    }

    /** Adds moisture once RH is the hysteresis below the setpoint and runs until it is back up to it. */
    public boolean control(double humidity, ClimateSettings.Humidifier settings) {
        boolean wanted = powered() && Double.isFinite(humidity)
                && (running ? humidity < setpoint() : humidity < setpoint() - settings.hysteresis());
        boolean changed = wanted != running;
        running = wanted;
        return changed;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        writeCommon(map, "slab");
        writeZone(map);
        return map;
    }

    public static Humidifier fromMap(Map<String, Object> map) {
        return new Humidifier(BlockKey.read(map, ""), BlockKey.read(map, "unit"), BlockKey.read(map, "slab"),
                text(map, "label", "Humidifier"), (int) Math.round(number(map, "setpoint", 40.0)),
                bool(map, "powered", true), bool(map, "running", false),
                number(map, "zoneDewPointF", Double.NaN));
    }
}
