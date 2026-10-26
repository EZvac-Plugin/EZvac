package com.github.blade.hvac.service;

import com.github.blade.hvac.model.EquipmentType;

/**
 * What may share a system. A system is all air or all water: water cannot
 * come out of a ceiling vent, and conditioned air cannot be piped into a
 * pool. Its medium also decides whether its thermostat is a pool thermostat.
 *
 * <p>Pure, so the rules can be tested without a server.
 */
public final class SystemLinks {
    private SystemLinks() {}

    /**
     * The devices of a system that decide its medium. A vent whose chunk is
     * not loaded cannot be inspected, so it is simply not counted.
     */
    public record Shape(boolean airEquipment, boolean waterEquipment, boolean airVents, boolean waterVents) {
        public static final Shape EMPTY = new Shape(false, false, false, false);

        public static Shape equipment(EquipmentType type) {
            return new Shape(!type.waterLoop(), type.waterLoop(), false, false);
        }

        public static Shape vent(boolean water) {
            return new Shape(false, false, !water, water);
        }

        public Shape plus(Shape other) {
            return new Shape(airEquipment || other.airEquipment, waterEquipment || other.waterEquipment,
                    airVents || other.airVents, waterVents || other.waterVents);
        }

        public boolean air() { return airEquipment || airVents; }
        public boolean water() { return waterEquipment || waterVents; }
        public boolean empty() { return !air() && !water(); }
    }

    /**
     * Why something with the shape {@code joining} cannot join a system with
     * the shape {@code system}, finishing "Home ...", or null when it can.
     */
    public static String conflict(Shape system, Shape joining) {
        if (!(system.air() && joining.water()) && !(system.water() && joining.air())) return null;
        return system.water()
                ? "is a water system: it takes boilers, pool heat pumps and waxed copper grates"
                : "is an air system: it takes heat pumps, air conditioners, furnaces and iron trapdoors";
    }

    /**
     * Whether a system's thermostat should be a pool thermostat, or null when
     * nothing in it decides (an empty system keeps whatever it was).
     */
    public static Boolean pool(Shape shape) {
        if (shape.empty() || (shape.air() && shape.water())) return null;
        return shape.water();
    }
}
