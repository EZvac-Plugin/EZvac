package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;

import java.util.*;

/**
 * Allocates shared equipment capacity across multiple zones within a group.
 * Uses smart priority based on temperature delta (demand) to distribute
 * heating/cooling where it's needed most.
 */
public final class ZoneController {
    
    /**
     * Represents a zone's heating/cooling demand.
     */
    public record ZoneDemand(ZoneId zoneId, Thermostat thermostat, 
                              OperatingMode mode, double temperatureDelta,
                              double allocatedCapacity) implements Comparable<ZoneDemand> {
        
        /**
         * Returns demand priority (higher = more urgent).
         * Based on absolute temperature delta from target.
         */
        public double priority() {
            return Math.abs(temperatureDelta);
        }

        @Override
        public int compareTo(ZoneDemand other) {
            // Higher priority (larger delta) comes first
            int priorityCompare = Double.compare(other.priority(), this.priority());
            if (priorityCompare != 0) return priorityCompare;
            // Tie-breaker: zone ID
            return zoneId.compareTo(other.zoneId);
        }
    }

    private ZoneController() {}

    /**
     * Calculates capacity allocation for each zone in a group.
     * Returns a map of ZoneId → allocated capacity fraction (0.0 to 1.0).
     * 
     * @param group the HVAC group
     * @param registry the device registry
     * @param mode the current operating mode (HEATING or COOLING)
     * @param totalCapacity the total available capacity from equipment
     * @param settings HVAC settings
     * @return zone demands with allocated capacity
     */
    public static List<ZoneDemand> allocateCapacity(
            GroupId group, HvacRegistry registry, OperatingMode mode,
            double totalCapacity, HvacSettings settings) {
        
        if (mode == OperatingMode.IDLE || totalCapacity <= 0.0) {
            return Collections.emptyList();
        }

        // Gather all zones that need service in this mode
        List<Thermostat> thermostats = registry.thermostatsFor(group);
        List<ZoneDemand> demands = new ArrayList<>();

        for (Thermostat thermostat : thermostats) {
            if (!thermostat.enabled()) continue;

            double delta = thermostat.roomF() - thermostat.targetF();
            boolean needsCooling = delta > settings.coolingStartAboveF();
            boolean needsHeating = delta < -settings.heatingStartBelowF();

            if ((mode == OperatingMode.COOLING && needsCooling) ||
                (mode == OperatingMode.HEATING && needsHeating)) {
                demands.add(new ZoneDemand(
                    thermostat.zoneId(),
                    thermostat,
                    mode,
                    delta,
                    0.0 // Will be filled in below
                ));
            }
        }

        if (demands.isEmpty()) {
            return Collections.emptyList();
        }

        // Sort by priority (highest demand first)
        demands.sort(Comparator.naturalOrder());

        // Allocate capacity proportionally based on demand
        double totalDemand = demands.stream().mapToDouble(ZoneDemand::priority).sum();
        
        List<ZoneDemand> allocated = new ArrayList<>();
        for (ZoneDemand demand : demands) {
            double fraction = demand.priority() / totalDemand;
            double zoneCapacity = totalCapacity * fraction;
            allocated.add(new ZoneDemand(
                demand.zoneId(),
                demand.thermostat(),
                demand.mode(),
                demand.temperatureDelta(),
                zoneCapacity
            ));
        }

        return allocated;
    }

    /**
     * For single-zone systems (backward compatibility), returns the full capacity
     * allocated to the primary thermostat.
     */
    public static double capacityForThermostat(
            Thermostat thermostat, List<ZoneDemand> allocations) {
        if (allocations.isEmpty()) return 0.0;
        
        return allocations.stream()
                .filter(d -> d.zoneId().equals(thermostat.zoneId()))
                .mapToDouble(ZoneDemand::allocatedCapacity)
                .sum();
    }

    /**
     * Returns true if any zone in the group needs the specified mode.
     */
    public static boolean anyZoneNeedsMode(GroupId group, HvacRegistry registry,
                                            OperatingMode mode, HvacSettings settings) {
        List<Thermostat> thermostats = registry.thermostatsFor(group);
        
        for (Thermostat thermostat : thermostats) {
            if (!thermostat.enabled()) continue;
            
            double delta = thermostat.roomF() - thermostat.targetF();
            
            if (mode == OperatingMode.COOLING && delta > settings.coolingStartAboveF()) {
                return true;
            }
            if (mode == OperatingMode.HEATING && delta < -settings.heatingStartBelowF()) {
                return true;
            }
        }
        
        return false;
    }
}
