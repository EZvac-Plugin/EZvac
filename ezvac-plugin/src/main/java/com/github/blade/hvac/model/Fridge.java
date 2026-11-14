package com.github.blade.hvac.model;

import com.github.blade.hvac.config.RefrigerationSettings;

import java.util.List;
import java.util.Map;

/**
 * A fridge (BETA 1.0 Pilot 7): an all-fridge, as a shop's or a restaurant's
 * is, holding 33 to 50 F (37 by default) in one to three barrels stacked on
 * its iron block. They share one inside; each barrel warms the room a
 * fridge's worth.
 */
public final class Fridge extends Refrigerator {
    /** The most barrels one fridge keeps, stacked. */
    public static final int MOST_BARRELS = 3;
    /** Its barrels above the first, bottom up; empty for a one-barrel fridge. */
    private final List<BlockKey> upper;

    public Fridge(BlockKey position, BlockKey unit, BlockKey barrel, String label, int setpointF) {
        this(position, unit, barrel, List.of(), label, setpointF);
    }

    public Fridge(BlockKey position, BlockKey unit, BlockKey barrel, List<BlockKey> upper, String label, int setpointF) {
        this(position, unit, barrel, upper, label, setpointF, true, false, Double.NaN);
    }

    private Fridge(BlockKey position, BlockKey unit, BlockKey barrel, List<BlockKey> upper, String label, int setpointF,
                   boolean powered, boolean running, double insideF) {
        super(position, unit, barrel, label, setpointF, powered, running, insideF);
        if (upper.size() > MOST_BARRELS - 1)
            throw new IllegalArgumentException("a fridge keeps at most " + MOST_BARRELS + " barrels");
        for (BlockKey key : upper)
            if (!key.worldId().equals(unit.worldId()) || key.equals(barrel) || key.equals(unit) || key.equals(position))
                throw new IllegalArgumentException("a fridge's barrels must be its own, in its world");
        this.upper = List.copyOf(upper);
    }

    /** How many barrels it keeps cold. */
    public int barrels() { return 1 + upper.size(); }

    @Override public List<BlockKey> extraBlocks() { return upper; }

    @Override
    public double insideAt(BlockKey barrel) {
        return upper.contains(barrel) ? insideF() : super.insideAt(barrel);
    }

    @Override
    public boolean keepsCold(BlockKey barrel) {
        return upper.contains(barrel) || super.keepsCold(barrel);
    }

    @Override public Kind kind() { return Kind.FRIDGE; }
    @Override public int minimumF() { return RefrigerationSettings.FRIDGE_MINIMUM_F; }
    @Override public int maximumF() { return RefrigerationSettings.FRIDGE_MAXIMUM_F; }
    @Override public double heatFPerHour(RefrigerationSettings settings) { return settings.fridgeHeatFPerHour() * barrels(); }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        for (int i = 0; i < upper.size(); i++) upper.get(i).write(map, "barrel" + (i + 2));
        return map;
    }

    public static Fridge fromMap(Map<String, Object> map) {
        Saved saved = Saved.read(map, "Fridge", 37);
        List<BlockKey> upper = new java.util.ArrayList<>();
        for (int n = 2; n <= MOST_BARRELS && map.containsKey("barrel" + n + "X"); n++)
            upper.add(BlockKey.read(map, "barrel" + n));
        return new Fridge(saved.position(), saved.unit(), saved.barrel(), upper, saved.label(), saved.setpoint(),
                saved.powered(), saved.running(), saved.insideF());
    }
}
