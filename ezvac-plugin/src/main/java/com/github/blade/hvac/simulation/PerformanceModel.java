package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.OperatingProfile;
import com.github.blade.hvac.model.Tier;

/** Pure compressor/blower capacity and ramp calculations. */
public final class PerformanceModel {
    public static final double FURNACE_MAXIMUM_RPM = 1_100.0;
    public static final double FURNACE_RAMP_UP_PER_SECOND = 1_000.0;
    public static final double FURNACE_RAMP_DOWN_PER_SECOND = 1_300.0;

    private PerformanceModel() {}

    public static double requestedVariableCapacity(double absoluteErrorF, HvacSettings settings) {
        finite("temperature error", absoluteErrorF);
        if (settings == null) throw new IllegalArgumentException("settings are required");
        double error = Math.max(0.0, absoluteErrorF);
        return clamp(settings.variableMinimumCapacity()
                + (1.0 - settings.variableMinimumCapacity())
                * error / settings.variableApproachBandF(),
                settings.variableMinimumCapacity(), 1.0);
    }

    public static double variableTargetRpm(double capacity, HvacSettings settings) {
        return variableTargetRpm(capacity, OperatingProfile.NORMAL, settings);
    }

    public static double variableTargetRpm(double capacity, OperatingProfile profile,
                                           HvacSettings settings) {
        finite("capacity", capacity);
        if (profile == null) throw new IllegalArgumentException("operating profile is required");
        if (settings == null) throw new IllegalArgumentException("settings are required");
        double normalized = (clamp(capacity, settings.variableMinimumCapacity(), 1.0)
                - settings.variableMinimumCapacity()) / (1.0 - settings.variableMinimumCapacity());
        double maximumRpm = switch (profile) {
            case ECO -> settings.variableEcoMaximumRpm();
            case NORMAL -> settings.variableMaximumRpm();
            case TURBO -> settings.variableTurboMaximumRpm();
        };
        return settings.variableMinimumRpm()
                + normalized * (maximumRpm - settings.variableMinimumRpm());
    }

    public static double variableCapacityFromRpm(double rpm, HvacSettings settings) {
        finite("rpm", rpm);
        if (settings == null) throw new IllegalArgumentException("settings are required");
        if (rpm <= 0.0) return 0.0;
        if (rpm < settings.variableMinimumRpm())
            return settings.variableMinimumCapacity() * rpm / settings.variableMinimumRpm();
        double normalized = (rpm - settings.variableMinimumRpm())
                / (settings.variableMaximumRpm() - settings.variableMinimumRpm());
        double maximumCapacity = settings.variableMinimumCapacity()
                + (settings.variableTurboMaximumRpm() - settings.variableMinimumRpm())
                / (settings.variableMaximumRpm() - settings.variableMinimumRpm())
                * (1.0 - settings.variableMinimumCapacity());
        return clamp(settings.variableMinimumCapacity()
                + normalized * (1.0 - settings.variableMinimumCapacity()), 0.0, maximumCapacity);
    }

    /**
     * What a unit of a tier is asked for, as a share of flat out (3.1). A
     * single-stage unit only runs flat out; a two-stage one drops to its low
     * stage within the approach band of the target; a variable one eases down
     * on the variable-speed curve, as every compressor always did.
     */
    public static double stagedRequest(Tier tier, double absoluteErrorF, HvacSettings settings) {
        if (tier == null || settings == null) throw new IllegalArgumentException("tier and settings are required");
        return switch (tier) {
            case SINGLE_STAGE -> 1.0;
            case TWO_STAGE -> Math.abs(absoluteErrorF) <= settings.variableApproachBandF() ? settings.twoStageLow() : 1.0;
            case VARIABLE -> requestedVariableCapacity(absoluteErrorF, settings);
        };
    }

    /** A unit as the hold works it out: what it is, and its tier. */
    public record Unit(EquipmentType type, Tier tier) {}

    /**
     * The least every unit of a running system is asked for, so it delivers
     * what the room loses and keeps closing on the target (BETA 1.0 Pilot 2).
     * Units ease down near the target, but a real one only slows as far as
     * the room allows: with a room's leak at a fixed rate, easing down to the
     * minimum could leave it short of the target for good.
     *
     * @param request  0 to 1: the least request, for every unit alike
     * @param keepsUp  false when even flat out it loses more than it makes
     */
    public record Hold(double request, boolean keepsUp) {
        public static final Hold NONE = new Hold(0.0, true);
    }

    /**
     * Whether the outdoors works against a mode: colder outside while
     * heating, warmer while cooling. Level with the room counts too: a system
     * that can't keep up is pulled to the outdoor temperature and held there,
     * and ThermalModel lets the outdoors oppose it at that point.
     */
    public static boolean against(OperatingMode mode, double roomF, double outdoorF) {
        return mode == OperatingMode.HEATING ? outdoorF <= roomF + 0.05
                : mode == OperatingMode.COOLING && outdoorF >= roomF - 0.05;
    }

    /** How fast a held room still closes on its target, in °F an in-game hour, beyond its loss. */
    public static final double HOLD_MARGIN_F_PER_HOUR = 1.0;

    /**
     * The hold for a system's units in a mode, losing {@code lossFPerHour} to
     * the outdoors (0 when the outdoors helps), as {@code sizeFactor} scales
     * them (NaN with building size off).
     */
    public static Hold hold(java.util.List<Unit> units, OperatingMode mode, OperatingProfile profile,
                            double sizeFactor, double lossFPerHour, HvacSettings settings) {
        if (mode == OperatingMode.IDLE || !(lossFPerHour > 0.0) || units.isEmpty()) return Hold.NONE;
        double rate = mode == OperatingMode.HEATING ? settings.heatingRateFPerHour() : settings.coolingRateFPerHour();
        double loss = lossFPerHour / rate, needed = (lossFPerHour + HOLD_MARGIN_F_PER_HOUR) / rate;
        double flatOut = delivered(units, 1.0, profile, sizeFactor, settings);
        if (flatOut < needed) return new Hold(1.0, flatOut > loss);
        double low = 0.0, high = 1.0;
        for (int i = 0; i < 30; i++) {
            double middle = (low + high) / 2.0;
            if (delivered(units, middle, profile, sizeFactor, settings) >= needed) high = middle;
            else low = middle;
        }
        return new Hold(high, true);
    }

    /** What a system's units deliver together, steady, all asked for the same. */
    static double delivered(java.util.List<Unit> units, double request, OperatingProfile profile, double sizeFactor,
                            HvacSettings settings) {
        double fixed = 0.0, variable = 0.0;
        for (Unit unit : units) {
            double capacity = steadyCapacity(unit.type(), unit.tier(), request, profile, settings);
            if (unit.type().fixedSpeed()) fixed += capacity;
            else variable += capacity;
        }
        return SizingModel.combine(fixed, variable, sizeFactor, settings);
    }

    /**
     * What a unit delivers once its motor has caught up with a request: flat
     * out for single-stage, its low stage or flat out for two-stage, and on
     * the curve for variable, as EquipmentUnit's motor works it out.
     */
    public static double steadyCapacity(EquipmentType type, Tier tier, double request, OperatingProfile profile,
                                        HvacSettings settings) {
        double asked = switch (tier) {
            case SINGLE_STAGE -> 1.0;
            case TWO_STAGE -> request <= settings.twoStageLow() + 1e-9 ? settings.twoStageLow() : 1.0;
            case VARIABLE -> request;
        };
        if (type.fixedSpeed())
            return settings.tierCapacity(tier) * Math.max(settings.variableMinimumCapacity(), Math.min(1.0, asked));
        return settings.tierCapacity(tier) * variableCapacityFromRpm(variableTargetRpm(asked, profile, settings), settings);
    }

    /**
     * A unit's request with the system's hold under it: a variable unit runs
     * no slower than the hold, and a two-stage one goes to its high stage when
     * its low one isn't enough.
     */
    public static double withHold(Tier tier, double staged, double hold) {
        return switch (tier) {
            case SINGLE_STAGE -> 1.0;
            case TWO_STAGE -> staged >= hold ? staged : 1.0;
            case VARIABLE -> Math.max(staged, hold);
        };
    }

    /**
     * How a profile scales a variable unit's flat out (BETA 1.0 Pilot 2): 1
     * on Normal, less on Eco, more on Turbo. Furnaces and boilers ignore it.
     */
    public static double profileShare(OperatingProfile profile, HvacSettings settings) {
        return variableCapacityFromRpm(variableTargetRpm(1.0, profile, settings), settings)
                / variableCapacityFromRpm(variableTargetRpm(1.0, OperatingProfile.NORMAL, settings), settings);
    }

    /**
     * What a unit delivers flat out: a furnace or boiler its full heat, a
     * compressor at its operating profile's top speed, each as its tier allows.
     */
    public static double fullCapacity(EquipmentType type, Tier tier, OperatingProfile profile, HvacSettings settings) {
        if (type == null || tier == null || profile == null || settings == null)
            throw new IllegalArgumentException("type, tier, profile and settings are required");
        if (type.fixedSpeed()) return settings.tierCapacity(tier);
        return settings.tierCapacity(tier) * variableCapacityFromRpm(variableTargetRpm(1.0, profile, settings), settings);
    }

    /**
     * How hard a system is working, 0 to 1: what it delivers now as a share of
     * what its units could deliver flat out, both after the multi-unit
     * diminishing returns. 1 means everything that can run is at full speed,
     * whatever the number or tiers of its units.
     */
    public static double load(double deliveredFixed, double deliveredVariable, double fullFixed, double fullVariable,
                              HvacSettings settings) {
        return load(deliveredFixed, deliveredVariable, fullFixed, fullVariable, Double.NaN, settings);
    }

    /** The same, combined as a system of this size combines its units (BETA 1.0; NaN with building size off). */
    public static double load(double deliveredFixed, double deliveredVariable, double fullFixed, double fullVariable,
                              double sizeFactor, HvacSettings settings) {
        double full = SizingModel.combine(fullFixed, fullVariable, sizeFactor, settings);
        if (full <= 0.0) return 0.0;
        double delivered = SizingModel.combine(deliveredFixed, deliveredVariable, sizeFactor, settings);
        return clamp(delivered / full, 0.0, 1.0);
    }

    public static double approach(double current, double target, double elapsedSeconds,
                                  double upPerSecond, double downPerSecond) {
        finite("current rpm", current);
        finite("target rpm", target);
        finite("elapsed seconds", elapsedSeconds);
        finite("ramp up", upPerSecond);
        finite("ramp down", downPerSecond);
        if (elapsedSeconds < 0.0 || upPerSecond <= 0.0 || downPerSecond <= 0.0)
            throw new IllegalArgumentException("RPM ramp inputs are invalid");
        double difference = target - current;
        double limit = (difference >= 0.0 ? upPerSecond : downPerSecond) * elapsedSeconds;
        if (Math.abs(difference) <= limit) return Math.max(0.0, target);
        return Math.max(0.0, current + Math.copySign(limit, difference));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void finite(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
