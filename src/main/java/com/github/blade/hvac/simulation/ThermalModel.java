package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.OperatingMode;

import java.util.Objects;

/** Stateless, piecewise-linear room temperature integrator. */
public final class ThermalModel {
    public static final double MINECRAFT_TICKS_PER_HOUR = 1_000.0;
    public static final double MINIMUM_SIMULATED_F = -100.0;
    public static final double MAXIMUM_SIMULATED_F = 200.0;
    private static final double EPSILON = 1.0e-10;

    private ThermalModel() {}

    public record Result(double previousF, double temperatureF, double outdoorEffectF,
                         double equipmentEffectF, double deliveredCapacity) {}

    public static Result step(double roomF, double controlBoundaryF, double outdoorF,
                              OperatingMode mode, double capacity, long elapsedTicks,
                              HvacSettings settings) {
        requireFinite("room temperature", roomF);
        requireFinite("control boundary", controlBoundaryF);
        requireFinite("outdoor temperature", outdoorF);
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(settings, "settings");
        requireFinite("capacity", capacity);
        if (capacity < 0.0 || capacity > settings.maximumGroupCapacity())
            throw new IllegalArgumentException("capacity is outside the configured range");
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsed ticks must be non-negative");

        double room = clampTemperature(roomF);
        double boundary = clampTemperature(controlBoundaryF);
        double outdoor = clampTemperature(outdoorF);
        if (elapsedTicks == 0) return new Result(room, room, 0.0, 0.0, 0.0);

        double remaining = elapsedTicks / MINECRAFT_TICKS_PER_HOUR;
        double current = room;
        double outdoorEffect = 0.0;
        double equipmentEffect = 0.0;
        boolean delivered = false;

        // Rates are constant between crossings of the outdoor temperature and
        // the active control boundary. Exact crossings keep results invariant
        // when callers split the same elapsed interval into smaller steps.
        for (int transitions = 0; remaining > EPSILON; transitions++) {
            if (transitions > 24) throw new IllegalStateException("thermal integration did not converge");

            double equipmentRate = equipmentRate(current, boundary, outdoor, mode, capacity, settings);
            double driftMagnitude = equipmentRate == 0.0
                    ? settings.idleDriftFPerHour() : settings.activeLeakFPerHour();
            double outdoorRate = outdoorDirection(outdoor, current, equipmentRate) * driftMagnitude;

            // If a very low startup RPM cannot overcome the environmental
            // load at exact outdoor equilibrium, the room stays at equilibrium
            // instead of chattering infinitesimally across the boundary.
            if (approximately(current, outdoor) && equipmentRate != 0.0
                    && Math.abs(equipmentRate) <= driftMagnitude) {
                outdoorRate = -equipmentRate;
            }

            // At a control boundary, active equipment may cancel an adverse
            // outdoor load but may never push through the boundary.
            if (approximately(current, boundary)) {
                if (mode == OperatingMode.COOLING && equipmentRate < 0.0
                        && outdoorRate + equipmentRate < 0.0) {
                    equipmentRate = -outdoorRate;
                } else if (mode == OperatingMode.HEATING && equipmentRate > 0.0
                        && outdoorRate + equipmentRate > 0.0) {
                    equipmentRate = -outdoorRate;
                }
            }

            double totalRate = outdoorRate + equipmentRate;
            if (approximately(totalRate, 0.0)) {
                outdoorEffect += outdoorRate * remaining;
                equipmentEffect += equipmentRate * remaining;
                delivered |= equipmentRate != 0.0;
                remaining = 0.0;
                break;
            }

            double interval = remaining;
            double reachedBoundary = Double.NaN;
            double controlTime = positiveCrossingTime(current, boundary, totalRate);
            if (controlTime <= interval && movesTowardControl(current, boundary, mode)) {
                interval = controlTime;
                reachedBoundary = boundary;
            }
            double outdoorTime = positiveCrossingTime(current, outdoor, totalRate);
            if (outdoorTime <= interval) {
                interval = outdoorTime;
                reachedBoundary = outdoor;
            }

            current += totalRate * interval;
            outdoorEffect += outdoorRate * interval;
            equipmentEffect += equipmentRate * interval;
            delivered |= equipmentRate != 0.0;
            remaining -= interval;
            if (Double.isFinite(reachedBoundary)) current = reachedBoundary;
        }

        current = clampTemperature(current);
        double accountingError = current - room - outdoorEffect - equipmentEffect;
        if (delivered) equipmentEffect += accountingError;
        else outdoorEffect += accountingError;
        return new Result(room, current, outdoorEffect, equipmentEffect, delivered ? capacity : 0.0);
    }

    private static double equipmentRate(double current, double boundary, double outdoor,
                                        OperatingMode mode, double capacity, HvacSettings settings) {
        if (capacity <= 0.0) return 0.0;
        if (mode == OperatingMode.COOLING
                && (current > boundary || (approximately(current, boundary) && outdoor > current))) {
            return -settings.coolingRateFPerHour() * capacity;
        }
        if (mode == OperatingMode.HEATING
                && (current < boundary || (approximately(current, boundary) && outdoor < current))) {
            return settings.heatingRateFPerHour() * capacity;
        }
        return 0.0;
    }

    private static boolean movesTowardControl(double current, double boundary, OperatingMode mode) {
        return mode == OperatingMode.COOLING ? current > boundary
                : mode == OperatingMode.HEATING && current < boundary;
    }

    private static double positiveCrossingTime(double current, double boundary, double rate) {
        double time = (boundary - current) / rate;
        return Double.isFinite(time) && time > EPSILON ? time : Double.POSITIVE_INFINITY;
    }

    public static double outdoorTemperature(long worldTime, double noonF, double midnightF) {
        requireFinite("noon temperature", noonF);
        requireFinite("midnight temperature", midnightF);
        long sinceNoon = Math.floorMod(worldTime - 6_000L, 24_000L);
        double angle = sinceNoon / 24_000.0 * Math.PI * 2.0;
        return clampTemperature((noonF + midnightF) / 2.0
                + (noonF - midnightF) / 2.0 * Math.cos(angle));
    }

    public static double aggregateCapacity(double equivalentUnits, double maximum) {
        requireFinite("equivalent units", equivalentUnits);
        requireFinite("maximum capacity", maximum);
        if (equivalentUnits < 0.0 || maximum <= 0.0)
            throw new IllegalArgumentException("capacity inputs are invalid");
        if (equivalentUnits <= 1.0) return equivalentUnits;
        return Math.min(maximum, Math.sqrt(equivalentUnits));
    }

    /**
     * Combines fixed-speed and variable-speed contributions without averaging
     * either class down. Diminishing returns apply only after both independent
     * contributions have been added.
     */
    public static double aggregateIndependentCapacity(double fixedEquivalent,
                                                      double variableEquivalent,
                                                      double maximum) {
        requireFinite("fixed capacity", fixedEquivalent);
        requireFinite("variable capacity", variableEquivalent);
        if (fixedEquivalent < 0.0 || variableEquivalent < 0.0)
            throw new IllegalArgumentException("independent capacities must be non-negative");
        return aggregateCapacity(fixedEquivalent + variableEquivalent, maximum);
    }

    public static double clampTemperature(double value) {
        requireFinite("temperature", value);
        return Math.max(MINIMUM_SIMULATED_F, Math.min(MAXIMUM_SIMULATED_F, value));
    }

    private static int direction(double value) {
        return value > EPSILON ? 1 : value < -EPSILON ? -1 : 0;
    }

    private static int outdoorDirection(double outdoor, double current, double equipmentRate) {
        int direction = direction(outdoor - current);
        if (direction != 0) return direction;
        // At exact equilibrium, active equipment immediately moves the room to
        // one side of the outdoor value; resistance begins in that same instant.
        return equipmentRate < 0.0 ? 1 : equipmentRate > 0.0 ? -1 : 0;
    }

    private static boolean approximately(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
