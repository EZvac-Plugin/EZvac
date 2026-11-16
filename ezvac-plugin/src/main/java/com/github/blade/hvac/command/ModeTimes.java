package com.github.blade.hvac.command;

import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.Thermostat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * How long a thermostat has been in its mode, and its last run, for
 * /hvac info (BETA 1.0 Pilot 8): "Heating for 12 min · last cooled 14:02 to 14:20".
 */
final class ModeTimes {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private ModeTimes() {}

    static String line(Thermostat thermostat, long nowMillis) {
        return line(thermostat, nowMillis, ZoneId.systemDefault());
    }

    static String line(Thermostat thermostat, long nowMillis, ZoneId zone) {
        StringBuilder line = new StringBuilder();
        if (thermostat.modeSinceMillis() > 0L)
            line.append(word(thermostat.mode(), true)).append(" for ")
                    .append(duration(Math.max(0L, nowMillis - thermostat.modeSinceMillis())));
        if (thermostat.lastRunMode() != OperatingMode.IDLE && thermostat.lastRunStartMillis() > 0L) {
            if (!line.isEmpty()) line.append(" · ");
            line.append("last ").append(word(thermostat.lastRunMode(), false)).append(' ')
                    .append(clock(thermostat.lastRunStartMillis(), zone)).append(" to ")
                    .append(clock(thermostat.lastRunEndMillis(), zone));
        }
        return line.toString();
    }

    /** "Heating" or "heated". */
    private static String word(OperatingMode mode, boolean now) {
        return switch (mode) {
            case HEATING -> now ? "Heating" : "heated";
            case COOLING -> now ? "Cooling" : "cooled";
            case IDLE -> now ? "Idle" : "idle";
        };
    }

    /** "45 s", "12 min", "3 h 05 min", "2 days". */
    static String duration(long millis) {
        long seconds = millis / 1000L;
        if (seconds < 60) return seconds + " s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        if (hours < 48) return hours + " h " + String.format("%02d", minutes % 60) + " min";
        return hours / 24 + " days";
    }

    private static String clock(long millis, ZoneId zone) {
        return CLOCK.format(Instant.ofEpochMilli(millis).atZone(zone));
    }
}
