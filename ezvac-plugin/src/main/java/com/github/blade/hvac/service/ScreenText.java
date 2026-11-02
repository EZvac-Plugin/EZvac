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
 * The text of a screen, as a status strip (BETA 1.0): a header in the colour
 * of what the device is doing, with its name and a glyph; under it a dark
 * body with one big reading and a few centred lines. The header, the body and
 * the big reading are three text displays (see {@link ScreenService}), since a
 * display has one background colour and one text size. Every screen is the
 * same size: a display sizes its panel to its widest line and centres the
 * others on it, so every body holds at least one blank line of spaces as wide
 * as the screen, and is {@link #BODY_LINES} lines tall. Lines themselves are
 * never padded: a line pushed past the display's wrap width would wrap, and
 * the extra line would move everything up under the big reading. Pure.
 */
public final class ScreenText {
    /** Lines in the body. A big reading takes the top two. */
    public static final int BODY_LINES = 6;
    /** Lines a big reading takes: it is drawn at twice the size. */
    public static final int BIG_LINES = 2;
    /** Small lines a screen shows when it has no big reading, such as a hub's sensors: one stays blank. */
    public static final int ROWS = BODY_LINES - 1;
    /** Pixels a line may take: the screen's width, less a pixel either side so nothing wraps. */
    static final int WIDTH = ScreenPlacement.LINE_WIDTH - 2;

    /**
     * What a device is doing, as its header shows it: a colour (opaque, RGB)
     * and a glyph at the header's right end.
     */
    public record Tone(int rgb, String glyph) {
        public static final Tone NEUTRAL = new Tone(0x2F3A48, "");
        public static final Tone COOLING = new Tone(0x1F5F9F, "❄");
        public static final Tone HEATING = new Tone(0xA8521C, "♨");
        public static final Tone HOLDING = new Tone(0x2E7D32, "●");
        public static final Tone RUNNING = new Tone(0x2E7D32, "●");
        public static final Tone OFF = new Tone(0x4A4F57, "○");
        /** Something needs a player: no filter, a spent filter, air too cold to dry. */
        public static final Tone ALERT = new Tone(0x9A6400, "!");

        /** The same colour with another glyph. */
        public Tone withGlyph(String other) { return new Tone(rgb, other); }
    }

    private final String title;
    private final String belongsTo;
    private Tone tone = Tone.NEUTRAL;
    private int attention;
    private Component big;
    private final List<Component> lines = new ArrayList<>();

    private ScreenText(String title, String belongsTo) {
        this.title = title;
        this.belongsTo = belongsTo;
    }

    /** The device's name, and in a lighter shade whatever it belongs to. */
    public static ScreenText titled(String title, String belongsTo) {
        return new ScreenText(title, belongsTo == null || belongsTo.isBlank() ? "" : belongsTo.trim());
    }

    /** The header's colour and glyph; {@link Tone#NEUTRAL} unless a device says otherwise. */
    public ScreenText tone(Tone value) {
        tone = value;
        return this;
    }

    public Tone tone() { return tone; }

    /**
     * How many things need someone's attention (BETA 1.0 Pilot 2): shown in
     * amber as {@code ⚠2} before the header's glyph; none when 0. What they
     * are is in the device's menu.
     */
    public ScreenText attention(int count) {
        attention = Math.max(0, count);
        return this;
    }

    /** The amber of the attention count. */
    static final TextColor ATTENTION = TextColor.color(0xFFC247);

    /** The reading that matters most, big and bold. */
    public ScreenText big(String reading) {
        big = Component.text(reading, NamedTextColor.WHITE).decorate(TextDecoration.BOLD);
        return this;
    }

    public boolean hasBig() { return big != null; }

    /**
     * A line of EZvac's own wording. One too wide for the screen would wrap,
     * and the extra line would push the body up under the big reading (BETA
     * 1.0: {@code Out 106.6°F · Summer}), so it gives up its last parts after
     * a {@code ·} first, then is cut.
     */
    public ScreenText line(String text, TextColor color) {
        lines.add(Component.text(fitted(text), color));
        return this;
    }

    /** The text, without its last {@code ·} parts while it is too wide, then cut to the width. */
    static String fitted(String text) {
        String line = text;
        while (SignText.width(line) > WIDTH && line.contains(" · ")) line = line.substring(0, line.lastIndexOf(" · "));
        return SignText.fit(line, WIDTH);
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

    /** Small lines in use so far. */
    public int used() { return lines.size(); }

    /** Lines in the body: always {@link #BODY_LINES} unless a device needed more. */
    public int bodyLines() {
        return Math.max(BODY_LINES, lines.size() + (big == null ? 1 : BIG_LINES));
    }

    /**
     * The header: the name in white, what it belongs to in a lighter shade,
     * then spaces out to the glyph at the right end, which also stretches
     * the strip to the full width.
     */
    public Component header() {
        String suffix = belongsTo.isEmpty() ? "" : "  · " + belongsTo;
        // At least a space always comes before the glyph, and a space's width is left spare at the
        // end, so a glyph a little wider than the sign's table thinks never wraps the strip.
        String warning = attention > 0 ? "⚠" + attention + " " : "";
        int room = WIDTH - glyphWidth() - SignText.width(warning) - SignText.width(" "),
                text = room - SignText.width(" ");
        String name = SignText.fit(title, text - SignText.width(suffix));
        if (name.isEmpty()) {
            name = SignText.fit(title, text);
            suffix = "";
        }
        Component line = Component.text(name, NamedTextColor.WHITE);
        if (!suffix.isEmpty()) line = line.append(Component.text(suffix, TextColor.color(0xC8D0DA)));
        int spaces = Math.max(1, (room - SignText.width(name + suffix)) / SignText.width(" "));
        line = line.append(Component.text(" ".repeat(spaces)));
        if (!warning.isEmpty()) line = line.append(Component.text(warning, ATTENTION));
        return tone.glyph().isEmpty() ? line : line.append(Component.text(tone.glyph(), NamedTextColor.WHITE));
    }

    private int glyphWidth() {
        return tone.glyph().isEmpty() ? 0 : SignText.width(tone.glyph());
    }

    /** The big reading, or null when the screen has none. */
    public Component bigText() { return big; }

    /**
     * The body, centred by its display: blank rows where the big reading is
     * drawn over it, then the small lines, then blank rows to the full
     * height. The blank rows are the screen's width in spaces, which sets the
     * panel's width; a screen without a big reading always ends in one.
     */
    public Component body() {
        List<Component> all = new ArrayList<>();
        if (big != null) for (int i = 0; i < BIG_LINES; i++) all.add(Component.text(BLANK));
        all.addAll(lines);
        if (big == null) all.add(Component.text(BLANK));
        while (all.size() < BODY_LINES) all.add(Component.text(BLANK));
        return Component.join(JoinConfiguration.newlines(), all);
    }

    /** A line of spaces just short of the wrap width, so it can never wrap. */
    static final String BLANK = " ".repeat(WIDTH / SignText.width(" ") - 1);

    /** The text as plain characters, without the padding: the header, the big reading, the lines. */
    String plain() {
        List<String> all = new ArrayList<>();
        all.add((belongsTo.isEmpty() ? title : title + "  · " + belongsTo) + (attention > 0 ? " ⚠" + attention : ""));
        if (big != null) all.add(PlainTextComponentSerializer.plainText().serialize(big));
        for (Component line : lines) all.add(PlainTextComponentSerializer.plainText().serialize(line));
        return String.join("\n", all);
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
