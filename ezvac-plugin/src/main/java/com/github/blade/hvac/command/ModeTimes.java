package com.github.blade.hvac.command;

import com.github.blade.hvac.lang.Lang;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.Thermostat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.github.blade.hvac.lang.Lang.arg;

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
            line.append(Lang.text(Lang.key("command.mode-times.now", thermostat.mode().name()),
                    arg("for", duration(Math.max(0L, nowMillis - thermostat.modeSinceMillis())))));
        if (thermostat.lastRunMode() != OperatingMode.IDLE && thermostat.lastRunStartMillis() > 0L) {
            if (!line.isEmpty()) line.append(" · ");
            line.append(Lang.text(Lang.key("command.mode-times.last", thermostat.lastRunMode().name()),
                    arg("from", clock(thermostat.lastRunStartMillis(), zone)),
                    arg("to", clock(thermostat.lastRunEndMillis(), zone))));
        }
        return line.toString();
    }

    /** "45 s", "12 min", "3 h 05 min", "2 days". */
    static String duration(long millis) {
        long seconds = millis / 1000L;
        if (seconds < 60) return Lang.text("time.seconds", arg("n", seconds));
        long minutes = seconds / 60;
        if (minutes < 60) return Lang.text("time.minutes", arg("n", minutes));
        long hours = minutes / 60;
        if (hours < 48) return Lang.text("time.hours-minutes", arg("h", hours),
                arg("m", String.format("%02d", minutes % 60)));
        return Lang.text("time.days", arg("n", hours / 24));
    }

    private static String clock(long millis, ZoneId zone) {
        return CLOCK.format(Instant.ofEpochMilli(millis).atZone(zone));
    }
}
