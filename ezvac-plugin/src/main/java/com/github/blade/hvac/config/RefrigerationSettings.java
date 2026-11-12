package com.github.blade.hvac.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.function.Consumer;

/**
 * Fridges and freezers (BETA 1.0 Pilot 7): how they cool the barrel they
 * keep, how its door lets the room in, and how much of their heat goes into
 * the room. In EZvac itself food never spoils: a fridge is a cold barrel
 * that warms its room; an addon such as EZclimates makes food keep by the
 * cold.
 *
 * @param fridgeDefaultF   a new fridge's setpoint (33 to 50 F)
 * @param freezerDefaultF  a new freezer's setpoint (-10 to 20 F)
 * @param pullDownFPerHour how fast a running one cools its inside, in F per in-game hour
 * @param leakPerHour      the share of the gap to the room its inside closes per in-game hour, shut
 * @param doorShare        the share of that gap that comes in at once when it is opened
 * @param doorLeakFactor   how many times faster it leaks while it stands open
 * @param hysteresisF      it starts this far above its setpoint and runs down to it
 * @param fridgeHeatFPerHour  what a running fridge adds to its room, F per in-game hour, at the reference size
 * @param freezerHeatFPerHour the same for a freezer
 * @param referenceCells   the room size those are for; a bigger room warms less
 */
public record RefrigerationSettings(boolean enabled, int fridgeDefaultF, int freezerDefaultF, double pullDownFPerHour,
                                    double leakPerHour, double doorShare, double doorLeakFactor, double hysteresisF,
                                    double fridgeHeatFPerHour, double freezerHeatFPerHour, int referenceCells) {
    public static final int FRIDGE_MINIMUM_F = 33, FRIDGE_MAXIMUM_F = 50;
    public static final int FREEZER_MINIMUM_F = -10, FREEZER_MAXIMUM_F = 20;

    public RefrigerationSettings {
        if (fridgeDefaultF < FRIDGE_MINIMUM_F || fridgeDefaultF > FRIDGE_MAXIMUM_F)
            throw new IllegalArgumentException("refrigeration.fridge-default-f must be 33-50");
        if (freezerDefaultF < FREEZER_MINIMUM_F || freezerDefaultF > FREEZER_MAXIMUM_F)
            throw new IllegalArgumentException("refrigeration.freezer-default-f must be -10-20");
        positive("refrigeration.pull-down-f-per-minecraft-hour", pullDownFPerHour);
        fraction("refrigeration.leak-per-minecraft-hour", leakPerHour);
        fraction("refrigeration.door-share", doorShare);
        if (!Double.isFinite(doorLeakFactor) || doorLeakFactor < 1.0)
            throw new IllegalArgumentException("refrigeration.door-leak-factor must be at least 1");
        nonNegative("refrigeration.hysteresis-f", hysteresisF);
        nonNegative("refrigeration.fridge-heat-f-per-minecraft-hour", fridgeHeatFPerHour);
        nonNegative("refrigeration.freezer-heat-f-per-minecraft-hour", freezerHeatFPerHour);
        if (referenceCells < 1) throw new IllegalArgumentException("refrigeration.reference-cells must be positive");
    }

    public static RefrigerationSettings defaults() {
        return new RefrigerationSettings(true, 37, 0, 20.0, 0.08, 0.25, 6.0, 2.0, 0.3, 0.5, 1_500);
    }

    /** Reads the section; a bad value warns and the whole section keeps its defaults. */
    public static RefrigerationSettings load(FileConfiguration config, Consumer<String> warn) {
        RefrigerationSettings d = defaults();
        try {
            return new RefrigerationSettings(config.getBoolean("refrigeration.enabled", d.enabled),
                    config.getInt("refrigeration.fridge-default-f", d.fridgeDefaultF),
                    config.getInt("refrigeration.freezer-default-f", d.freezerDefaultF),
                    config.getDouble("refrigeration.pull-down-f-per-minecraft-hour", d.pullDownFPerHour),
                    config.getDouble("refrigeration.leak-per-minecraft-hour", d.leakPerHour),
                    config.getDouble("refrigeration.door-share", d.doorShare),
                    config.getDouble("refrigeration.door-leak-factor", d.doorLeakFactor),
                    config.getDouble("refrigeration.hysteresis-f", d.hysteresisF),
                    config.getDouble("refrigeration.fridge-heat-f-per-minecraft-hour", d.fridgeHeatFPerHour),
                    config.getDouble("refrigeration.freezer-heat-f-per-minecraft-hour", d.freezerHeatFPerHour),
                    config.getInt("refrigeration.reference-cells", d.referenceCells));
        } catch (IllegalArgumentException invalid) {
            warn.accept(invalid.getMessage() + "; every refrigeration setting uses its default until it is fixed.");
            return d;
        }
    }

    private static void positive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) throw new IllegalArgumentException(name + " must be above 0");
    }

    private static void nonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException(name + " must not be negative");
    }

    private static void fraction(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0)
            throw new IllegalArgumentException(name + " must be 0-1");
    }
}
