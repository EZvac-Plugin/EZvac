package com.github.blade.hvac.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A device's screen as a panel on a fixed grid (BETA 1.0 Pilot 7, the new
 * look): exactly a sign board's size, 100 by 50 screen pixels (a mini
 * thermostat's own, 60 by 42), with each part in its own zone. Coloured
 * fills, text, smooth bars, small turning icons and buttons. Pure: it says
 * what goes where, {@link ScreenService} draws it, and {@link #problems()}
 * checks that every part stays on the panel, no two buttons touch, no text
 * runs out of its zone, and no button covers text.
 *
 * <p>Coordinates are screen pixels from the top left, y going down.
 */
public final class Panel {
    /** The grid a panel is drawn on. */
    public enum Size {
        /** A sign's board: twice as wide as it is tall. */
        SIGN(100, 50),
        /** A mini thermostat's: exactly its wall button's face, 6 by 4 pixels of a block. */
        MINI(60, 40);

        public final int width, height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    public enum Align { LEFT, CENTER, RIGHT }

    /** One thing on the panel; its id stays the same from one drawing to the next, so only changes are sent. */
    public sealed interface Part permits Fill, Label, Bar, Icon, Button, Tick {
        String id();
    }

    /** A filled rectangle, in ARGB: a header strip, a button's face, a bar's track. */
    public record Fill(String id, int x, int y, int w, int h, int argb) implements Part {}

    /**
     * Text in a zone {@code w} wide whose glyphs start at {@code y} (top), at
     * one or two times the size; a line is 8 pixels tall at one, 16 at two.
     */
    public record Label(String id, int x, int y, int w, Component text, double scale, Align align) implements Part {
        public int height() { return (int) Math.ceil(9 * scale); }

        /** How wide its text is drawn, in screen pixels: bold glyphs are a pixel wider. */
        public int textWidth() { return (int) Math.ceil(Panel.width(text) * scale); }
    }

    /** A bar filled {@code fraction} of the way from its left; the fill moves smoothly when it changes. */
    public record Bar(String id, int x, int y, int w, int h, double fraction, int argb, int trackArgb) implements Part {}

    /**
     * An item drawn flat as an icon, centred on {@code cx, cy}, {@code size}
     * pixels across; it turns {@code turnsPerSecond} times a second (0: still).
     * {@code dim} draws it in the dark, as a thing that is off.
     */
    public record Icon(String id, int cx, int cy, int size, Material item, double turnsPerSecond, boolean dim)
            implements Part {}

    /** A button: its face, its label centred on it, and what clicking it does. */
    public record Button(String id, int x, int y, int w, int h, String label, String action, int argb, int textRgb)
            implements Part {}

    /** One short segment of a dial, centred {@code r} pixels out from {@code cx, cy} at {@code angle} degrees (0 up). */
    public record Tick(String id, int cx, int cy, double r, double angle, int len, int thick, int argb) implements Part {}

    private final Size size;
    private final Map<String, Part> parts = new LinkedHashMap<>();

    public Panel(Size size) {
        this.size = Objects.requireNonNull(size, "size");
    }

    public Size size() { return size; }
    public List<Part> parts() { return List.copyOf(parts.values()); }

    public Panel add(Part part) {
        if (parts.putIfAbsent(part.id(), part) != null)
            throw new IllegalArgumentException("two parts are called " + part.id());
        return this;
    }

    public Panel fill(String id, int x, int y, int w, int h, int argb) {
        return add(new Fill(id, x, y, w, h, argb));
    }

    public Panel label(String id, int x, int y, int w, Component text, double scale, Align align) {
        return add(new Label(id, x, y, w, text, scale, align));
    }

    public Panel label(String id, int x, int y, int w, String text, TextColor color, Align align) {
        return add(new Label(id, x, y, w, Component.text(text, color), SMALL, align));
    }

    public Panel big(String id, int x, int y, int w, String text, TextColor color, Align align) {
        return add(new Label(id, x, y, w, Component.text(text, color).decorate(TextDecoration.BOLD), BIG, align));
    }

    public Panel bar(String id, int x, int y, int w, int h, double fraction, int argb) {
        return add(new Bar(id, x, y, w, h, Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) : 0.0,
                argb, TRACK));
    }

    public Panel icon(String id, int cx, int cy, int size, Material item, double turnsPerSecond, boolean dim) {
        return add(new Icon(id, cx, cy, size, item, turnsPerSecond, dim));
    }

    public Panel button(String id, int x, int y, int w, int h, String label, String action) {
        return add(new Button(id, x, y, w, h, label, action, BUTTON, 0xE8ECF1));
    }

    public Panel selected(String id, int x, int y, int w, int h, String label, String action) {
        return add(new Button(id, x, y, w, h, label, action, SELECTED, 0xC9F5D2));
    }

    public Panel tick(String id, int cx, int cy, double r, double angle, int len, int thick, int argb) {
        return add(new Tick(id, cx, cy, r, angle, len, thick, argb));
    }

    /** The panel itself, opaque, so the sign behind never shows through. */
    public static final int BOARD = 0xFF121820;
    public static final int TRACK = 0xFF26303C;
    public static final int BUTTON = 0xFF222C38;
    public static final int SELECTED = 0xFF2B5F3A;
    /** A button's face for a moment after it is pressed. */
    public static final int PRESSED = 0xFF4A7BB0;
    /** Small text's size: three quarters of the font's, so it sits in its strip with room round it. */
    public static final double SMALL = 0.75;
    /** A big reading's size: one and a half times the font's, so three digits still fit their zone. */
    public static final double BIG = 1.5;
    /** How tall the header strip is. */
    public static final int HEADER = 9;

    /** A text's width in screen pixels at one times the size: bold glyphs are a pixel wider. */
    static int width(Component text) {
        String plain = PlainTextComponentSerializer.plainText().serialize(text);
        int width = TextWidth.width(plain);
        if (text.hasDecoration(TextDecoration.BOLD)) width += plain.length();
        return width;
    }

    /** Where a part's box lies: x, y, w, h. */
    static int[] box(Part part) {
        return switch (part) {
            case Fill f -> new int[] {f.x(), f.y(), f.w(), f.h()};
            case Label l -> new int[] {l.x(), l.y(), l.w(), l.height()};
            case Bar b -> new int[] {b.x(), b.y(), b.w(), b.h()};
            case Icon i -> new int[] {i.cx() - i.size() / 2, i.cy() - i.size() / 2, i.size(), i.size()};
            case Button b -> new int[] {b.x(), b.y(), b.w(), b.h()};
            case Tick t -> {
                double rad = Math.toRadians(t.angle());
                int x = (int) Math.round(t.cx() + Math.sin(rad) * t.r()), y = (int) Math.round(t.cy() - Math.cos(rad) * t.r());
                int half = (t.len() + 1) / 2;
                yield new int[] {x - half, y - half, half * 2, half * 2};
            }
        };
    }

    private static boolean overlap(int[] a, int[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    /**
     * What's wrong with the layout, empty when nothing is: a part off the
     * panel, two buttons touching, text wider than its zone, or a button over
     * text. The tests check every screen with this.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        List<Part> all = parts();
        for (Part part : all) {
            int[] box = box(part);
            if (box[0] < 0 || box[1] < 0 || box[0] + box[2] > size.width || box[1] + box[3] > size.height)
                problems.add(part.id() + " runs off the panel");
            if (part instanceof Label label && label.textWidth() > label.w())
                problems.add(part.id() + "'s text is " + label.textWidth() + " wide in a zone of " + label.w());
            if (part instanceof Button button) {
                int text = (int) Math.ceil(TextWidth.width(button.label()) * SMALL);
                if (text > button.w() - 2) problems.add(part.id() + "'s label doesn't fit on it");
                if (button.h() < 9) problems.add(part.id() + " is too short for its label");
            }
        }
        for (int i = 0; i < all.size(); i++)
            for (int j = i + 1; j < all.size(); j++) {
                Part a = all.get(i), b = all.get(j);
                boolean buttons = a instanceof Button && b instanceof Button;
                boolean buttonOnText = (a instanceof Button && b instanceof Label) || (a instanceof Label && b instanceof Button);
                if ((buttons || buttonOnText) && overlap(box(a), box(b)))
                    problems.add(a.id() + " and " + b.id() + " overlap");
            }
        return problems;
    }

    /** A fingerprint of everything shown: two panels that look the same have the same one. */
    public String fingerprint() {
        StringBuilder out = new StringBuilder(size.name());
        for (Part part : parts.values()) {
            out.append('|').append(part.id());
            switch (part) {
                // A label's text by its plain characters and a hash of its colours: far cheaper than printing it whole.
                case Label l -> out.append(l.x()).append(',').append(l.y()).append(',').append(l.w()).append(',')
                        .append(l.scale()).append(l.align().ordinal()).append(':')
                        .append(PlainTextComponentSerializer.plainText().serialize(l.text())).append('#')
                        .append(l.text().hashCode());
                case Fill f -> out.append(f.x()).append(',').append(f.y()).append(',').append(f.w()).append(',')
                        .append(f.h()).append(',').append(f.argb());
                case Bar bar -> out.append(bar.x()).append(',').append(bar.y()).append(',').append(bar.w()).append(',')
                        .append(bar.h()).append(',').append(Math.round(bar.fraction() * 1000)).append(',').append(bar.argb());
                case Icon i -> out.append(i.cx()).append(',').append(i.cy()).append(',').append(i.size()).append(',')
                        .append(i.item().ordinal()).append(',').append(i.turnsPerSecond()).append(i.dim());
                case Button btn -> out.append(btn.x()).append(',').append(btn.y()).append(',').append(btn.w()).append(',')
                        .append(btn.h()).append(btn.label()).append(btn.action()).append(btn.argb());
                case Tick t -> out.append(t.cx()).append(',').append(t.cy()).append(',').append(t.r()).append(',')
                        .append(t.angle()).append(',').append(t.argb());
            }
        }
        return out.toString();
    }
}
