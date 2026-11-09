package com.github.blade.hvac.api;

import org.bukkit.World;

import java.util.List;
import java.util.Optional;

/**
 * Weather an addon plans for a world (API 1.9), as EZclimates does. With one
 * registered ({@link EzvacApi#setWeatherPlan}), EZvac's weather stations
 * forecast from it, its outdoor air takes on a heat wave's or a cold snap's
 * shift, and a storm's wind. The addon drives Minecraft's own weather to
 * match; EZvac still reads whether it rains from the world. Without one,
 * EZvac forecasts from Minecraft's own weather. Main thread only.
 *
 * @since 1.9
 */
public interface WeatherPlan {
    /**
     * One day of the plan, as a forecast shows it.
     *
     * @param ahead         0 for today, 1 for tomorrow
     * @param rainChance    the chance of rain or snow that day, 0 to 1
     * @param thunderChance the chance of thunder that day, 0 to 1
     * @param shiftF        how far the day's air is above (positive) or below the season's usual, in F
     * @param event         the day's event, if any
     * @param note          what is known about it, such as {@code Snowstorm from 14:00}; empty for nothing
     */
    record Outlook(int ahead, double rainChance, double thunderChance, double shiftF, Optional<WeatherEvent> event,
                   String note) {
        public Outlook {
            event = event == null ? Optional.empty() : event;
            note = note == null ? "" : note;
            rainChance = clamp(rainChance);
            thunderChance = Math.min(rainChance, clamp(thunderChance));
            shiftF = Double.isFinite(shiftF) ? shiftF : 0.0;
        }

        private static double clamp(double chance) {
            return Double.isFinite(chance) ? Math.max(0.0, Math.min(1.0, chance)) : 0.0;
        }
    }

    /** Today and the days after it, {@code days} in all; fewer, or none, for a world without a plan. */
    List<Outlook> outlook(World world, int days);

    /** How far the air is above (positive) or below the season's usual right now, in F; 0 normally. */
    double shiftNowF(World world);

    /** The event going on in a world right now, if any. */
    Optional<WeatherEvent> eventNow(World world);

    /** A plan that plans nothing. */
    WeatherPlan NONE = new WeatherPlan() {
        @Override
        public List<Outlook> outlook(World world, int days) { return List.of(); }

        @Override
        public double shiftNowF(World world) { return 0.0; }

        @Override
        public Optional<WeatherEvent> eventNow(World world) { return Optional.empty(); }
    };

    /** A day's outlook, for an addon that only knows the chances. */
    static Outlook outlook(int ahead, double rainChance, double thunderChance) {
        return new Outlook(ahead, rainChance, thunderChance, 0.0, Optional.empty(), "");
    }
}
