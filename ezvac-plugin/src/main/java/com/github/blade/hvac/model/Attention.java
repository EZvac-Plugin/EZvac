package com.github.blade.hvac.model;

/**
 * What a thermostat's system needs someone to look at (BETA 1.0 Pilot 2), in
 * the order its menu lists them. The screen counts them beside a ⚠; the
 * thermostat's menu says what each is and how to fix it. Worked out each
 * cycle, never saved.
 */
public enum Attention {
    /** Nothing linked to heat or cool with. It can't start. */
    NO_UNITS,
    /** No vent linked (a water vent for a pool). It can't start. */
    NO_VENTS,
    /** It needs heating or cooling, but every unit that could is switched off server-wide. */
    UNITS_OFF,
    /** Even flat out it loses more than it makes, to openings or the wind: it can't reach its target. */
    CANT_KEEP_UP,
    /** Too few units for the building's size, for the mode it runs or needs (building size on). */
    UNDERSIZED;

    /**
     * What a system needs someone to look at.
     *
     * @param units      whether anything is linked to heat or cool with
     * @param vented     whether a vent is linked
     * @param waiting    whether it waits on units switched off server-wide
     * @param lagging    whether, running flat out, it still loses more than it makes
     * @param undersized whether it has too few units for its size, in the mode it runs or needs
     */
    public static java.util.Set<Attention> of(boolean units, boolean vented, boolean waiting, boolean lagging,
                                              boolean undersized) {
        java.util.Set<Attention> found = java.util.EnumSet.noneOf(Attention.class);
        if (!units) found.add(NO_UNITS);
        if (!vented) found.add(NO_VENTS);
        if (waiting) found.add(UNITS_OFF);
        if (lagging) found.add(CANT_KEEP_UP);
        if (undersized) found.add(UNDERSIZED);
        return found;
    }

    /** Whether it keeps the system from starting, so its screen says so on a line of its own. */
    public boolean blocking() {
        return this != UNDERSIZED && this != CANT_KEEP_UP;
    }
}
