package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.ServerSwitches;
import com.github.blade.hvac.config.SizingSettings;
import com.github.blade.hvac.model.DeviceKind;
import com.github.blade.hvac.model.EquipmentUnit;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.OperatingProfile;

import java.util.Locale;

/**
 * Size-based heating and cooling (BETA 1.0). Pure.
 *
 * <p>A system's units heat or cool the air it owns. One variable-speed unit
 * is sized for {@link SizingSettings#cellsPerUnit()} blocks of air (pools:
 * {@link SizingSettings#poolCellsPerUnit()} of water); a two-star unit counts
 * 0.85 of one, a one-star 0.70. Units add up in full and the sum is scaled by
 * how big the system is against that, so a system sized right pushes its
 * temperature exactly as one unit in a normal house always has, a bigger one
 * is slower, and a smaller one quicker, up to {@link SizingSettings#maximumSpeedUp()}.
 * Openings to the sky make a room leak more; systems whose air touches even
 * out through it.
 */
public final class SizingModel {
    /** Below this share of what it needs, a system is called undersized. */
    static final double UNDERSIZED_BELOW = 0.95;

    private SizingModel() {}

    /** How many variable-speed units' worth a system of this size needs. */
    public static double required(int size, boolean water, SizingSettings sizing) {
        return Math.max(0, size) / (double) (water ? sizing.poolCellsPerUnit() : sizing.cellsPerUnit());
    }

    /**
     * What a system's units add up to for a mode, in variable-speed units:
     * each enabled unit that does that mode counts its tier's share, unless
     * its kind is switched off server-wide.
     */
    public static double installed(Iterable<EquipmentUnit> units, OperatingMode mode, HvacSettings settings) {
        return installed(units, mode, OperatingProfile.NORMAL, settings);
    }

    /**
     * The same on a profile (BETA 1.0 Pilot 2): Eco's lower top speed makes
     * a variable unit count for less, Turbo's for more. Furnaces and boilers
     * count the same on every profile.
     */
    public static double installed(Iterable<EquipmentUnit> units, OperatingMode mode, OperatingProfile profile,
                                   HvacSettings settings) {
        double share = PerformanceModel.profileShare(profile, settings);
        double total = 0.0;
        for (EquipmentUnit unit : units) {
            if (!unit.enabled() || !unit.type().supports(mode)) continue;
            if (ServerSwitches.deviceOff(DeviceKind.of(unit.type()))) continue;
            total += settings.tierCapacity(unit.tier()) * (unit.type().fixedSpeed() ? 1.0 : share);
        }
        return total;
    }

    /** What a system's size scales its units by: 1 when one unit is sized for it, less when it is bigger. */
    public static double factor(int size, boolean water, SizingSettings sizing) {
        int reference = water ? sizing.poolCellsPerUnit() : sizing.cellsPerUnit();
        return Math.min(sizing.maximumSpeedUp(), reference / (double) Math.max(1, size));
    }

    /** How much more a room leaks than a sealed one: 1, plus each opening block per 1,000 blocks of air. */
    public static double openness(int openings, int size, SizingSettings sizing) {
        if (openings <= 0) return 1.0;
        double perThousand = openings * 1000.0 / Math.max(1, size);
        return Math.min(sizing.maximumOpenness(), 1.0 + sizing.openingLeak() * perThousand);
    }

    /**
     * The capacity a system delivers. With sizing ({@code factor} not NaN)
     * its units add up in full, scaled by its size, up to the system's
     * maximum; without it, as before BETA 1.0: diminishing returns past one
     * unit, up to the same maximum.
     */
    public static double combine(double fixedEquivalent, double variableEquivalent, double factor,
                                 HvacSettings settings) {
        if (Double.isNaN(factor))
            return ThermalModel.aggregateIndependentCapacity(fixedEquivalent, variableEquivalent,
                    settings.maximumSystemCapacity());
        double total = Math.max(0.0, fixedEquivalent) + Math.max(0.0, variableEquivalent);
        return Math.min(settings.maximumSystemCapacity(), total * Math.max(0.0, factor));
    }

    /**
     * How far a neighbour whose air touches this system's pulls its
     * temperature in this time, in °F: faster the more faces touch and the
     * smaller this system is. It never goes more than half the gap, so two
     * neighbours stepped from the same start can meet but never cross.
     */
    public static double pull(int contact, int size, double fromF, double towardF, long elapsedTicks,
                              SizingSettings sizing) {
        if (contact <= 0 || elapsedTicks <= 0 || !Double.isFinite(fromF) || !Double.isFinite(towardF)) return 0.0;
        double gap = towardF - fromF;
        double hours = elapsedTicks / ThermalModel.MINECRAFT_TICKS_PER_HOUR;
        double moved = sizing.neighbourExchange() * contact / Math.max(1, size) * gap * hours;
        return Math.abs(moved) > Math.abs(gap) / 2.0 ? gap / 2.0 : moved;
    }

    /**
     * How a system measures up, for its screen and /hvac info.
     *
     * @param size      blocks of air (or water) it owns
     * @param required  variable-speed units' worth it needs
     * @param installed what its units for the mode add up to
     */
    public record Assessment(int size, boolean water, int openings, double required, double installed,
                             double factor, double openness) {
        public boolean undersized() { return installed < required * UNDERSIZED_BELOW; }

        /** {@code 1/2.6}: what it has of what it needs. */
        public String ratio() { return number(installed) + "/" + number(Math.max(required, 0.1)); }
    }

    public static Assessment assess(int size, boolean water, int openings, Iterable<EquipmentUnit> units,
                                    OperatingMode mode, HvacSettings settings, SizingSettings sizing) {
        return assess(size, water, openings, units, mode, OperatingProfile.NORMAL, settings, sizing);
    }

    /** The same on a profile (BETA 1.0 Pilot 2): on Eco a system can be short that Normal sizes right. */
    public static Assessment assess(int size, boolean water, int openings, Iterable<EquipmentUnit> units,
                                    OperatingMode mode, OperatingProfile profile, HvacSettings settings,
                                    SizingSettings sizing) {
        return new Assessment(size, water, openings, required(size, water, sizing),
                installed(units, mode, profile, settings), factor(size, water, sizing),
                water ? 1.0 : openness(openings, size, sizing));
    }

    /** 1, 0.9, 2.6, and from 10 whole numbers (13, 108), so a screen line always fits. */
    static String number(double value) {
        if (value >= 10.0) return Long.toString(Math.round(value));
        double rounded = Math.round(value * 10.0) / 10.0;
        return rounded == Math.rint(rounded) ? Long.toString((long) rounded)
                : String.format(Locale.US, "%.1f", rounded);
    }
}
