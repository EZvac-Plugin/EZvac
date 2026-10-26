package com.github.blade.hvac.service;

import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.SystemId;
import com.github.blade.hvac.model.Thermostat;

/**
 * How systems are named to players. A system has no name of its own: it is
 * called after its thermostat, and labels may repeat, so a message that has
 * to tell two Homes apart adds where the thermostat is.
 */
public final class SystemNames {
    private SystemNames() {}

    /** "Home (120, 64, -30)". */
    public static String of(Thermostat thermostat) {
        return thermostat.id() + " (" + at(thermostat.position()) + ")";
    }

    /** "Home (120, 64, -30)", or "a headless system" while it has no thermostat. */
    public static String of(HvacRegistry registry, SystemId system) {
        Thermostat head = registry.thermostatFor(system);
        return head == null ? "a headless system" : of(head);
    }

    /** Just the thermostat's label, for signs and the HUD; "Headless" without one. */
    public static String label(HvacRegistry registry, SystemId system) {
        Thermostat head = registry.thermostatFor(system);
        return head == null ? "Headless" : head.id();
    }

    /** "120, 64, -30". */
    public static String at(BlockKey position) {
        return position.x() + ", " + position.y() + ", " + position.z();
    }

    /** "4 units, 32 vents, 1 settings panel and 2 RPM monitors": what is linked besides the thermostat. */
    public static String contents(HvacRegistry.Members members) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (members.units() > 0) parts.add(count(members.units(), "unit"));
        if (members.vents() > 0) parts.add(count(members.vents(), "vent"));
        if (members.panels() > 0) parts.add(count(members.panels(), "settings panel"));
        if (members.monitors() > 0) parts.add(count(members.monitors(), "RPM monitor"));
        if (parts.isEmpty()) return "nothing";
        if (parts.size() == 1) return parts.get(0);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    public static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }
}
