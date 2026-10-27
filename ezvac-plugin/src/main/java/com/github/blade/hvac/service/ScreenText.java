package com.github.blade.hvac.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * The text of a screen (2.1): a title, one big reading, then a few
 * smaller lines, in the colours the signs already use. Every screen is the
 * same size: a text display sizes its panel to its text, so each screen is
 * padded to {@link #LINES} lines and its title to the full width. Pure.
 */
public final class ScreenText {
    /** Lines on every screen; a device with less to say gets blank ones. */
    public static final int LINES = 6;
    /** Pixels a line may take: the screen's width, less a pixel either side so nothing wraps. */
    static final int WIDTH = ScreenPlacement.LINE_WIDTH - 2;
    private final List<Component> lines = new ArrayList<>();
    private int titleWidth;

    /** The device's name, and in grey whatever it belongs to. */
    public static ScreenText titled(String title, String belongsTo) {
        ScreenText screen = new ScreenText();
        String suffix = belongsTo == null || belongsTo.isBlank() ? "" : "  · " + belongsTo;
        String name = SignText.fit(title, WIDTH - width(suffix));
        if (name.isEmpty()) {
            name = SignText.fit(title, WIDTH);
            suffix = "";
        }
        Component line = Component.text(name, NamedTextColor.AQUA);
        if (!suffix.isEmpty()) line = line.append(Component.text(suffix, NamedTextColor.GRAY));
        screen.lines.add(line);
        screen.titleWidth = width(name + suffix);
        return screen;
    }

    /** Pixels a title takes; its · separator is as narrow as a full stop, though the sign's table doesn't know it. */
    private static int width(String text) {
        return SignText.width(text.replace('·', '.'));
    }

    /** The reading that matters most, bold. */
    public ScreenText big(String reading) {
        lines.add(Component.text(reading, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
        return this;
    }

    /**
     * A line of EZvac's own wording, short enough by design. Only names a
     * player chose are cut to fit ({@link #named}): the sign's width table
     * counts symbols like ° and ❄ as wide as any glyph, so it would cut lines
     * that fit.
     */
    public ScreenText line(String text, TextColor color) {
        lines.add(Component.text(text, color));
        return this;
    }

    /** A line holding a name a player chose, cut to the screen's width. */
    public ScreenText named(String text, TextColor color) {
        lines.add(Component.text(SignText.fit(text, WIDTH), color));
        return this;
    }

    /** A line made of parts in different colours; it must already fit. */
    public ScreenText line(Component line) {
        lines.add(line);
        return this;
    }

    /** Lines in use so far, before padding. */
    public int used() { return lines.size(); }

    /** Lines on the screen: always {@link #LINES} unless a device needed more. */
    public int lines() { return Math.max(LINES, lines.size()); }

    public Component text() {
        List<Component> all = new ArrayList<>(lines);
        // Spaces after the title stretch the panel to the full width; blank lines give it the full height.
        int spaces = Math.max(0, (WIDTH - titleWidth) / SignText.width(" "));
        if (!all.isEmpty() && spaces > 0) all.set(0, all.get(0).append(Component.text(" ".repeat(spaces))));
        while (all.size() < LINES) all.add(Component.text(" "));
        return Component.join(JoinConfiguration.newlines(), all);
    }

    /** The text as plain characters, without the padding. */
    String plain() {
        return PlainTextComponentSerializer.plainText().serialize(
                Component.join(JoinConfiguration.newlines(), lines));
    }

    /** A filled-and-empty bar: ▮▮▮▮▮▮▯▯▯▯ for 0.6 over ten cells. */
    public static String bar(double fraction, int cells) {
        double clamped = Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) : 0.0;
        int filled = (int) Math.round(clamped * cells);
        return "▮".repeat(filled) + "▯".repeat(cells - filled);
    }

    /** A bar in colour with its percentage after it: a system's load (3.1), 0 to 100%. */
    public static Component bar(double fraction, int cells, TextColor color) {
        double value = Double.isFinite(fraction) ? Math.max(0.0, fraction) : 0.0;
        return Component.text(bar(value, cells), color)
                .append(Component.text(" " + Math.round(value * 100) + "%", NamedTextColor.WHITE));
    }
}
