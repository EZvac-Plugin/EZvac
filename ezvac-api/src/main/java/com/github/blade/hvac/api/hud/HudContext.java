package com.github.blade.hvac.api.hud;

import com.github.blade.hvac.api.ClimateReading;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/**
 * One player's HUD update, handed to a {@link HudSection}, with helpers that
 * draw values exactly as the rest of the server's HUD does.
 *
 * @since 1.1
 */
public interface HudContext {
    /** The player the HUD is drawn for. */
    Player player();

    /** The climate the HUD shows them this update. */
    ClimateReading reading();

    /** True when the player is in water, and the HUD shows its water layout. */
    default boolean inWater() { return reading().inWater(); }

    /** A Fahrenheit temperature in the server's units and the HUD's style, such as "98.6°F" or "37.0°C". */
    String temperature(double fahrenheit);

    /**
     * A bar in the HUD's own style: the server's {@code hud.bar} glyphs and
     * length, filled in {@code color}, the rest in the bar's empty colour.
     * Fill an addon's meter with it and it matches EZvac's feels-like bar.
     *
     * @param fraction how full, from 0 to 1 (clamped)
     */
    Component bar(double fraction, TextColor color);

    /**
     * A meter in the HUD's own style, as EZvac draws its feels-like bar: with
     * {@code hud.bar.style: gauge}, a line whose every segment is coloured by
     * where along the scale it stands, with the marker at {@code fraction};
     * with {@code blocks}, a bar filled to {@code fraction} in the colour
     * there. For an addon meter with a scale of its own, such as a body
     * temperature from cold to hot.
     *
     * @param fraction where the value stands, from 0 to 1 (clamped)
     * @param colorAt  the colour at each point along the scale, 0 to 1
     * @since 1.11
     */
    default Component gauge(double fraction, java.util.function.DoubleFunction<TextColor> colorAt) {
        double clamped = Double.isNaN(fraction) ? 0.0 : Math.max(0.0, Math.min(1.0, fraction));
        return bar(clamped, colorAt.apply(clamped));
    }
}
