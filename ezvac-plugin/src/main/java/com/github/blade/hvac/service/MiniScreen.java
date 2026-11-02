package com.github.blade.hvac.service;

import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.model.OperatingMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A mini thermostat's display (BETA 1.0 Pilot 2): a dark panel a little
 * bigger than the wall button behind it, with the room's temperature big in
 * whole degrees and the colour of what it's doing, the target under it
 * ("Set 85°"), and a gauge of thin bars for how far the room has come toward
 * it, where a full thermostat shows its ETA (the maintainer's pick of three
 * designs). Nothing a real thermostat leaves
 * out: no header, outdoor readings or season. A ⚠ and a count follow the
 * target when something needs attention; the menu says what. Two text
 * displays, as a screen's body and big reading. Pure.
 */
public final class MiniScreen {
    /** Pixels in one of its lines: the panel's width. */
    public static final int LINE_WIDTH = 60;
    /** Pixels a line may take, a pixel spare either side so nothing wraps. */
    static final int WIDTH = LINE_WIDTH - 2;
    /** Body lines: two under the big reading, then the target and the gauge. */
    public static final int BODY_LINES = 4;
    /** Bars in the gauge. */
    static final int GAUGE = 16;

    static final TextColor HEATING = TextColor.color(0xFF9A3C);
    static final TextColor COOLING = TextColor.color(0x5AB4FF);
    static final TextColor EMPTY = TextColor.color(0x4A4F57);

    private final Component big;
    private final List<Component> lines;

    private MiniScreen(Component big, List<Component> lines) {
        this.big = big;
        this.lines = List.copyOf(lines);
    }

    /**
     * @param progress  how far the room has come toward the target since it started, 0 to 1; NaN while idle
     * @param attention how many things need attention
     * @param blocked   what keeps it from starting, short ("No vent"), or empty
     */
    public static MiniScreen of(TemperatureUnit units, double roomF, double targetF, OperatingMode mode,
                                boolean enabled, double progress, int attention, String blocked) {
        TextColor modeColor = !enabled ? NamedTextColor.GRAY : switch (mode) {
            case HEATING -> HEATING;
            case COOLING -> COOLING;
            case IDLE -> NamedTextColor.WHITE;
        };
        // Whole degrees, as a real thermostat shows them, in the colour of what it's doing.
        Component big = Component.text(degrees(units, roomF), modeColor).decorate(TextDecoration.BOLD);

        // A line too wide (a pool's 104° and a count) loses its "Set"; never the count.
        String warning = attention > 0 ? " ⚠" + attention : "";
        String target = "Set " + degrees(units, targetF);
        if (SignText.width(target + warning) > WIDTH) target = degrees(units, targetF);
        Component targetLine = Component.text(target, NamedTextColor.GRAY);
        if (!warning.isEmpty()) targetLine = targetLine.append(Component.text(warning, ScreenText.ATTENTION));

        Component gauge;
        if (!enabled) gauge = Component.text("Off", NamedTextColor.GRAY);
        else if (!blocked.isEmpty()) gauge = Component.text(blocked, NamedTextColor.RED);
        else if (mode == OperatingMode.IDLE || !Double.isFinite(progress)) gauge = Component.text("●", NamedTextColor.GREEN);
        else gauge = gauge(progress, modeColor);

        List<Component> lines = new ArrayList<>();
        lines.add(targetLine);
        lines.add(gauge);
        return new MiniScreen(big, lines);
    }

    /** {@code 71°}: whole degrees, without the F or C every other display already says. */
    static String degrees(TemperatureUnit units, double fahrenheit) {
        return String.format(Locale.US, "%.0f°", units.fromF(fahrenheit));
    }

    /** Thin bars filled up to how far the room has come, in the mode's colour; the rest dark. */
    static Component gauge(double progress, TextColor color) {
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * GAUGE);
        return Component.text("|".repeat(filled), color).append(Component.text("|".repeat(GAUGE - filled), EMPTY));
    }

    /** Bold glyphs are a pixel wider. */
    static int boldWidth(String text) {
        return SignText.width(text) + text.length();
    }

    /** A line of spaces just short of the wrap width, so the panel is always the same size. */
    static final String BLANK = " ".repeat(WIDTH / SignText.width(" ") - 1);

    public Component bigText() { return big; }

    /** The body: two blank lines under the big reading, then the target and the gauge. */
    public Component body() {
        List<Component> all = new ArrayList<>();
        all.add(Component.text(BLANK));
        all.add(Component.text(BLANK));
        all.addAll(lines);
        return Component.join(JoinConfiguration.newlines(), all);
    }

    /** The text as plain characters, the big reading first: for tests. */
    String plain() {
        List<String> all = new ArrayList<>();
        all.add(PlainTextComponentSerializer.plainText().serialize(big));
        for (Component line : lines) all.add(PlainTextComponentSerializer.plainText().serialize(line));
        return String.join("\n", all);
    }

    /** Each line's width in pixels, for tests. */
    List<Integer> widths() {
        List<Integer> out = new ArrayList<>();
        for (Component line : lines) out.add(SignText.width(PlainTextComponentSerializer.plainText().serialize(line)));
        return out;
    }
}
