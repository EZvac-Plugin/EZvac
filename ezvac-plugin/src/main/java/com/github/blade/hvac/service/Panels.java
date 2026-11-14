package com.github.blade.hvac.service;

import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.RpmMonitor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * Each device's screen in the new look (BETA 1.0 Pilot 7), built from what
 * it is doing: a header strip in the colour of its state with its name and,
 * when something needs looking at, the amber ⚠ count; the reading that
 * matters, big; and buttons where a choice belongs on the wall. Pure, so the
 * tests check every layout.
 */
public final class Panels {
    private Panels() {}

    // ---- The state colours: a header strip, and the same hue for text ----
    public static final int COOLING = 0xFF1F5F9F, HEATING = 0xFFA8521C, HOLDING = 0xFF2E7D32, OFF = 0xFF4A4F57,
            ALERT = 0xFF9A6400, NEUTRAL = 0xFF2F3A48;
    static final TextColor COOL_TEXT = TextColor.color(0x5AB4FF), HEAT_TEXT = TextColor.color(0xFF9A3C),
            HOLD_TEXT = TextColor.color(0x7BE08A), OFF_TEXT = TextColor.color(0x8A8F98),
            ICE_TEXT = TextColor.color(0x9FD3FF), DIM = TextColor.color(0xAAB3BF), AMBER = TextColor.color(0xFFC247);
    static final int DIAL_OFF = 0xFF2A3542;

    /** The header strip: the name on the left, the state (and any ⚠ count) on the right. */
    static void header(Panel panel, String title, String state, int tone, int attention) {
        int width = panel.size().width;
        panel.fill("header", 0, 0, width, Panel.HEADER, tone);
        Component right = Component.text(state, NamedTextColor.WHITE);
        if (attention > 0) right = right.append(Component.text(" ⚠" + attention, AMBER));
        int rightWidth = Panel.width(right);
        int rightZone = (int) Math.ceil(rightWidth * Panel.SMALL);
        panel.label("state", width - 3 - rightZone, 2, rightZone, right, Panel.SMALL, Panel.Align.RIGHT);
        int titleZone = width - 9 - rightZone;
        panel.label("title", 3, 2, titleZone, TextWidth.fit(title, (int) (titleZone / Panel.SMALL)), NamedTextColor.WHITE,
                Panel.Align.LEFT);
    }

    /** {@code 72°}: whole degrees, as an appliance or a thermostat shows them. */
    static String degrees(TemperatureUnit units, double fahrenheit) {
        return Double.isFinite(fahrenheit) ? String.format(Locale.US, "%.0f°", units.fromF(fahrenheit)) : "--°";
    }

    // ---- Thermostats ----

    /** What a thermostat's screen needs to know. */
    public record ThermostatState(String name, boolean pool, double roomF, double targetF, OperatingMode mode,
                                  boolean enabled, String blocked, double minimumF, double maximumF,
                                  double progress, int attention) {}

    static int tone(ThermostatState t) {
        if (!t.enabled() || !t.blocked().isEmpty()) return OFF;
        return switch (t.mode()) {
            case COOLING -> COOLING;
            case HEATING -> HEATING;
            case IDLE -> HOLDING;
        };
    }

    static TextColor text(ThermostatState t) {
        if (!t.enabled()) return OFF_TEXT;
        return switch (t.mode()) {
            case COOLING -> COOL_TEXT;
            case HEATING -> HEAT_TEXT;
            case IDLE -> HOLD_TEXT;
        };
    }

    static String state(ThermostatState t) {
        if (!t.enabled()) return "○ off";
        if (!t.blocked().isEmpty()) return "stopped";
        return switch (t.mode()) {
            case COOLING -> "❄ cooling";
            case HEATING -> "♨ heating";
            case IDLE -> "● holding";
        };
    }

    /**
     * A thermostat on its sign: a half-ring dial lit between the room and
     * the target, the room big in the middle, the target under it, and ▼ ▲
     * in the bottom corners.
     */
    public static Panel thermostat(TemperatureUnit units, ThermostatState t) {
        Panel panel = new Panel(Panel.Size.SIGN);
        header(panel, t.name(), state(t), tone(t), t.attention());
        int lit = tone(t) == OFF ? 0xFF5F6670 : (0xFF000000 | text(t).value());
        int ticks = 15;
        double low = Math.min(t.roomF(), t.targetF()), high = Math.max(t.roomF(), t.targetF());
        for (int i = 0; i < ticks; i++) {
            double angle = -80.0 + i * (160.0 / (ticks - 1));
            double tickF = t.minimumF() + (t.maximumF() - t.minimumF()) * i / (ticks - 1.0);
            double step = (t.maximumF() - t.minimumF()) / (ticks - 1.0) / 2.0;
            boolean target = Math.abs(tickF - t.targetF()) <= step;
            boolean between = t.enabled() && tickF >= low - step && tickF <= high + step;
            panel.tick("dial" + i, 50, 50, 31, angle, 4, 2, target ? 0xFFFFFFFF : between ? lit : DIAL_OFF);
        }
        bigOrNot(panel, "room", 30, 28, 40, degrees(units, t.roomF()), NamedTextColor.WHITE, Panel.Align.CENTER);
        panel.label("set", 26, 41, 48, "set " + degrees(units, t.targetF()), text(t), Panel.Align.CENTER);
        panel.button("down", 2, 38, 13, 11, "-", "down");
        panel.button("up", 85, 38, 13, 11, "+", "up");
        return panel;
    }

    /**
     * A mini thermostat on its button: no header; its state small at the
     * top, the room big in the mode's colour, the target, a bar filling as
     * the room comes round, and ▲ ▼ down its right side.
     */
    public static Panel mini(TemperatureUnit units, ThermostatState t) {
        Panel panel = new Panel(Panel.Size.MINI);
        // What stops it isn't spelt out: the ⚠ count says something does, and the menu says what.
        String said = state(t);
        TextColor saidColor = t.blocked().isEmpty() ? text(t) : OFF_TEXT;
        Component state = Component.text(said, saidColor);
        if (t.attention() > 0) {
            Component warning = Component.text(" ⚠" + t.attention(), AMBER);
            state = state.append(warning);
            // Too wide with the count: the state's glyph alone, and the count; never the count left out.
            if (Panel.width(state) * Panel.SMALL > 54) state = Component.text(said.substring(0, 1), saidColor).append(warning);
        }
        panel.label("state", 3, 2, 54, state, Panel.SMALL, Panel.Align.LEFT);
        String room = degrees(units, t.roomF());
        // A pool's 104° is too wide to be big on so small a panel: it is drawn at the normal size there.
        bigOrNot(panel, "room", 3, 13, 41, room, text(t), Panel.Align.LEFT);
        panel.label("set", 3, 27, 41, "set " + degrees(units, t.targetF()), DIM, Panel.Align.LEFT);
        boolean working = t.enabled() && t.blocked().isEmpty() && t.mode() != OperatingMode.IDLE
                && Double.isFinite(t.progress());
        panel.bar("progress", 3, 36, 40, 2, working ? t.progress() : 0.0, 0xFF000000 | text(t).value());
        panel.button("up", 46, 11, 12, 11, "+", "up");
        panel.button("down", 46, 25, 12, 11, "-", "down");
        return panel;
    }

    // ---- Refrigeration ----

    static int tone(String activity) {
        return switch (activity) {
            case "Cooling" -> COOLING;
            case "Holding" -> HOLDING;
            case "Door open" -> ALERT;
            case "Defrosting" -> 0xFF6A3A8A;
            default -> OFF;
        };
    }

    static String state(String activity) {
        return switch (activity) {
            case "Cooling" -> "❄ cooling";
            case "Holding" -> "● holding";
            case "Door open" -> "! door open";
            case "Defrosting" -> "≈ defrosting";
            default -> "○ off";
        };
    }

    /** What a fridge's, mini fridge's or deep freezer's screen needs to know. */
    public record ColdState(String name, String activity, double insideF, int setpoint, boolean deep,
                            boolean mini, double chillerF, int barrels, double frost, boolean defrosting) {}

    /**
     * A fridge, mini fridge or deep freezer: its inside big, its setpoint,
     * ▲ ▼ down the right side; a mini fridge's chiller, a deep freezer's
     * frost and its Defrost button along the bottom.
     */
    public static Panel cold(TemperatureUnit units, ColdState c) {
        Panel panel = new Panel(Panel.Size.SIGN);
        header(panel, c.name(), state(c.activity()), tone(c.activity()), 0);
        panel.big("inside", 4, 12, 70, degrees(units, c.insideF()) + units.name(),
                c.deep() ? ICE_TEXT : NamedTextColor.WHITE, Panel.Align.LEFT);
        panel.label("set", 4, c.deep() ? 29 : 30, c.deep() ? 44 : 78, "set " + degrees(units, c.setpoint()), DIM,
                Panel.Align.LEFT);
        if (c.mini())
            panel.label("chiller", 4, 40, 78, "chiller " + degrees(units, c.chillerF()), COOL_TEXT, Panel.Align.LEFT);
        else if (c.deep()) {
            panel.label("frostLabel", 4, 40, 28, "frost", DIM, Panel.Align.LEFT);
            panel.bar("frost", 32, 42, 12, 3, c.frost(), 0xFF7FDCFF);
            panel.button("defrost", 50, 39, 48, 10, c.defrosting() ? "..." : "Defrost", "defrost");
        } else if (c.barrels() > 1)
            panel.label("barrels", 4, 40, 78, c.barrels() + " barrels", DIM, Panel.Align.LEFT);
        // A deep freezer's arrows sit higher, leaving the bottom right to its Defrost button.
        panel.button("up", 86, 11, 12, c.deep() ? 11 : 12, "+", "up");
        panel.button("down", 86, c.deep() ? 24 : 37, 12, c.deep() ? 11 : 12, "-", "down");
        return panel;
    }

    /** What a refrigerator's screen needs to know. */
    public record RefrigeratorState(String name, String activity, double fridgeF, int fridgeSetpoint, double freezerF,
                                    int freezerSetpoint, double frost) {}

    /** A refrigerator: its two compartments side by side, each with its ▼ ▲, the freezer's frost under it. */
    public static Panel refrigerator(TemperatureUnit units, RefrigeratorState r) {
        Panel panel = new Panel(Panel.Size.SIGN);
        header(panel, r.name(), state(r.activity()), tone(r.activity()), 0);
        bigOrNot(panel, "fridge", 2, 11, 46, degrees(units, r.fridgeF()), NamedTextColor.WHITE, Panel.Align.CENTER);
        bigOrNot(panel, "freezer", 52, 11, 46, degrees(units, r.freezerF()), ICE_TEXT, Panel.Align.CENTER);
        panel.label("fridgeName", 2, 28, 46, "FRIDGE", DIM, Panel.Align.CENTER);
        panel.label("freezerName", 52, 28, 46, "FREEZER", DIM, Panel.Align.CENTER);
        if (r.frost() >= 0.05) panel.bar("frost", 58, 37, 34, 1, r.frost(), 0xFF7FDCFF);
        // Each compartment's setpoint between its arrows, a stepper.
        panel.button("fridgeDown", 2, 39, 11, 10, "-", "fridge-down");
        panel.label("fridgeSet", 13, 40, 24, degrees(units, r.fridgeSetpoint()), NamedTextColor.WHITE, Panel.Align.CENTER);
        panel.button("fridgeUp", 37, 39, 11, 10, "+", "fridge-up");
        panel.button("freezerDown", 51, 39, 11, 10, "-", "down");
        panel.label("freezerSet", 62, 40, 25, degrees(units, r.freezerSetpoint()), ICE_TEXT, Panel.Align.CENTER);
        panel.button("freezerUp", 87, 39, 11, 10, "+", "up");
        return panel;
    }

    /** A reading big when it fits its zone, else a little smaller, else at the font's size; never cut. */
    static void bigOrNot(Panel panel, String id, int x, int y, int w, String text, TextColor color, Panel.Align align) {
        Component big = Component.text(text, color).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        if (Panel.width(big) * Panel.BIG <= w) panel.big(id, x, y, w, text, color, align);
        else if (Panel.width(big) * 1.25 <= w) panel.label(id, x, y + 2, w, big, 1.25, align);
        else panel.label(id, x, y + 4, w, big, 1.0, align);
    }

    // ---- Point sensors: thermometers, hygrometers, air monitors ----

    /** What a thermometer's, hygrometer's or air monitor's screen needs to know. */
    public record PointState(String name, String reads, double localF, double feelsF, double outdoorF,
                             String humidity, String outdoorHumidity, String sky, boolean airOn, boolean airKnown,
                             String aqi, String band, TextColor bandColor, int bandTone, String cause, String pollen) {}

    /**
     * A point sensor: its reading big on the left, what goes with it on the
     * right, the sky and season along the bottom. Read-only: no buttons.
     */
    public static Panel point(TemperatureUnit units, PointState p) {
        Panel panel = new Panel(Panel.Size.SIGN);
        boolean air = p.reads().equals("AIR");
        header(panel, p.name(), "", air && p.airOn() && p.airKnown() ? p.bandTone() : NEUTRAL, 0);
        switch (p.reads()) {
            case "TEMPERATURE" -> {
                bigOrNot(panel, "reading", 4, 13, 54, units.compact(p.localF()).replace(units.name(), "°"),
                        NamedTextColor.WHITE, Panel.Align.LEFT);
                panel.label("feels", 56, 14, 41, "feels " + degrees(units, p.feelsF()), NamedTextColor.YELLOW, Panel.Align.RIGHT);
                panel.label("out", 56, 24, 41, "out " + degrees(units, p.outdoorF()), DIM, Panel.Align.RIGHT);
            }
            case "HUMIDITY" -> {
                bigOrNot(panel, "reading", 4, 13, 54, p.humidity(), NamedTextColor.WHITE, Panel.Align.LEFT);
                panel.label("what", 56, 14, 41, "humidity", NamedTextColor.WHITE, Panel.Align.RIGHT);
                panel.label("out", 56, 24, 41, "out " + p.outdoorHumidity(), DIM, Panel.Align.RIGHT);
            }
            case "BOTH" -> {
                bigOrNot(panel, "reading", 4, 13, 54, units.compact(p.localF()).replace(units.name(), "°"),
                        NamedTextColor.WHITE, Panel.Align.LEFT);
                panel.label("humidity", 56, 14, 41, p.humidity() + " RH", NamedTextColor.WHITE, Panel.Align.RIGHT);
                panel.label("feels", 56, 24, 41, "feels " + degrees(units, p.feelsF()), NamedTextColor.YELLOW, Panel.Align.RIGHT);
                panel.label("out", 4, 31, 92, "out " + degrees(units, p.outdoorF()) + " · " + p.outdoorHumidity(), DIM,
                        Panel.Align.LEFT);
            }
            default -> {
                if (!p.airOn() || !p.airKnown()) {
                    bigOrNot(panel, "reading", 4, 13, 54, "--", NamedTextColor.WHITE, Panel.Align.LEFT);
                    panel.label("what", 56, 14, 41, p.airOn() ? "AQI" : "air off", DIM, Panel.Align.RIGHT);
                } else {
                    bigOrNot(panel, "reading", 4, 13, 40, p.aqi(), p.bandColor(), Panel.Align.LEFT);
                    panel.label("band", 44, 14, 53, fit(p.band(), 53), p.bandColor(), Panel.Align.RIGHT);
                    if (!p.cause().isEmpty())
                        panel.label("cause", 44, 24, 53, fit("mostly " + p.cause(), 53), NamedTextColor.WHITE, Panel.Align.RIGHT);
                    if (!p.pollen().isEmpty())
                        panel.label("pollen", 4, 31, 92, fit(p.pollen(), 92), NamedTextColor.YELLOW, Panel.Align.LEFT);
                }
            }
        }
        if (!p.sky().isEmpty()) panel.label("sky", 4, 41, 92, fit(p.sky(), 92), DIM, Panel.Align.LEFT);
        return panel;
    }

    /** Text cut to fit a zone at the small size. */
    static String fit(String text, int zone) {
        return TextWidth.fit(text, (int) (zone / Panel.SMALL));
    }

    // ---- The settings panel ----

    /** A settings panel: the system's profile, big, and Eco, Normal and Turbo as buttons; the one in use is lit. */
    public static Panel settings(String name, String system, String profile) {
        Panel panel = new Panel(Panel.Size.SIGN);
        int tone = switch (profile) {
            case "ECO" -> HOLDING;
            case "TURBO" -> HEATING;
            default -> NEUTRAL;
        };
        header(panel, name, system.isEmpty() ? "not linked" : system, tone, 0);
        if (system.isEmpty()) {
            panel.label("hint", 4, 22, 92, "link it with the link-tool", DIM, Panel.Align.CENTER);
            return panel;
        }
        panel.big("profile", 10, 15, 80, profile.charAt(0) + profile.substring(1).toLowerCase(Locale.ROOT),
                NamedTextColor.WHITE, Panel.Align.CENTER);
        String[][] buttons = {{"Eco", "eco", "6", "26"}, {"Normal", "normal", "36", "30"}, {"Turbo", "turbo", "70", "26"}};
        for (String[] b : buttons) {
            int x = Integer.parseInt(b[2]), w = Integer.parseInt(b[3]);
            if (b[0].equalsIgnoreCase(profile)) panel.selected(b[1], x, 37, w, 11, b[0], b[1]);
            else panel.button(b[1], x, 37, w, 11, b[0], b[1]);
        }
        return panel;
    }

    // ---- Dehumidifiers and humidifiers ----

    /** What a dehumidifier's or humidifier's screen needs to know. */
    public record HumidityState(String name, boolean drying, String state, int tone, TextColor stateColor,
                                double humidity, int setpoint, double tank) {}

    /** A dehumidifier or humidifier: the room's humidity big, its target, what it's doing; a dehumidifier's tank. */
    public static Panel humidity(HumidityState h) {
        Panel panel = new Panel(Panel.Size.SIGN);
        header(panel, h.name(), h.drying() ? "dehumidifier" : "humidifier", h.tone(), 0);
        bigOrNot(panel, "humidity", 4, 13, 44, Double.isFinite(h.humidity()) ? Math.round(h.humidity()) + "%" : "--%",
                NamedTextColor.WHITE, Panel.Align.LEFT);
        panel.label("set", 4, 30, 44, "set " + h.setpoint() + "%", DIM, Panel.Align.LEFT);
        panel.label("doing", 4, 40, 78, fit(h.state(), 78), h.stateColor(), Panel.Align.LEFT);
        if (h.drying()) {
            panel.label("tankLabel", 52, 14, 30, "tank", DIM, Panel.Align.LEFT);
            panel.bar("tank", 52, 24, 30, 3, h.tank(), 0xFF5AB4FF);
        }
        panel.button("up", 86, 11, 12, 12, "+", "up");
        panel.button("down", 86, 37, 12, 12, "-", "down");
        return panel;
    }

    // ---- Air purifiers ----

    /** What an air purifier's screen needs to know. */
    public record PurifierState(String name, int tone, String aqi, String band, TextColor bandColor, String mode,
                                boolean filterIn, double filter, String state, TextColor stateColor) {}

    /** An air purifier: the AQI big, its band, the filter; its fan speeds along the bottom, the one in use lit. */
    public static Panel purifier(PurifierState p) {
        Panel panel = new Panel(Panel.Size.SIGN);
        header(panel, p.name(), fit(p.state(), 40), p.tone(), 0);
        bigOrNot(panel, "aqi", 4, 11, 36, p.aqi(), p.bandColor(), Panel.Align.LEFT);
        panel.label("band", 42, 12, 55, fit(p.band(), 55), p.bandColor(), Panel.Align.RIGHT);
        if (p.filterIn()) {
            panel.label("filterLabel", 42, 23, 22, "filter", DIM, Panel.Align.LEFT);
            panel.bar("filter", 66, 25, 30, 3, p.filter(), 0xFF5AB4FF);
        } else panel.label("filterLabel", 42, 23, 55, "no filter", NamedTextColor.GOLD, Panel.Align.RIGHT);
        String[][] modes = {{"L", "LOW", "4", "18"}, {"M", "MEDIUM", "24", "18"}, {"H", "HIGH", "44", "18"},
                {"Auto", "AUTO", "64", "32"}};
        for (String[] m : modes) {
            int x = Integer.parseInt(m[2]), w = Integer.parseInt(m[3]);
            String action = "mode-" + m[1].toLowerCase(Locale.ROOT);
            if (m[1].equals(p.mode())) panel.selected(action, x, 38, w, 11, m[0], action);
            else panel.button(action, x, 38, w, 11, m[0], action);
        }
        return panel;
    }

    // ---- Sensor hubs ----

    /** A sensor hub: a line for each sensor on the page, three to a page, &lt; &gt; to turn it. Read-only otherwise. */
    public static Panel hub(String name, List<String> lines, boolean empty, int page, int pages) {
        Panel panel = new Panel(Panel.Size.SIGN);
        pagedHeader(panel, name, NEUTRAL, page, pages);
        if (empty) {
            panel.label("empty", 4, 14, 92, "No sensors yet:", DIM, Panel.Align.CENTER);
            panel.label("empty1", 4, 23, 92, "link them", DIM, Panel.Align.CENTER);
            panel.label("empty2", 4, 32, 92, "with the sensor-tool", DIM, Panel.Align.CENTER);
            return panel;
        }
        for (int i = 0; i < lines.size() && i < 3; i++)
            panel.label("line" + i, 4, 13 + i * 12, 92, fit(lines.get(i), 92), NamedTextColor.WHITE, Panel.Align.LEFT);
        return panel;
    }

    /** A header with the name on the left and, when there's more than a page, &lt; page/pages &gt; on the right. */
    static void pagedHeader(Panel panel, String title, int tone, int page, int pages) {
        panel.fill("header", 0, 0, 100, Panel.HEADER, tone);
        if (pages > 1) {
            panel.label("title", 3, 2, 55, fit(title, 55), NamedTextColor.WHITE, Panel.Align.LEFT);
            panel.button("prev", 60, 0, 10, 9, "<", "prev");
            panel.label("page", 70, 2, 18, (page + 1) + "/" + pages, NamedTextColor.WHITE, Panel.Align.CENTER);
            panel.button("next", 88, 0, 10, 9, ">", "next");
        } else panel.label("title", 3, 2, 94, fit(title, 94), NamedTextColor.WHITE, Panel.Align.LEFT);
    }

    // ---- Weather stations ----

    /** What a weather station's screen needs to know; {@code state} is set while it can't forecast. */
    public record StationState(String name, String state, int tone, String now, String sky, String highLow,
                               String conditions, String note, String tomorrow) {}

    /** A weather station: the temperature now big, today's sky and high/low beside it, the wind and air, then tomorrow. */
    public static Panel station(StationState s) {
        Panel panel = new Panel(Panel.Size.SIGN);
        header(panel, s.name(), "", s.tone(), 0);
        if (!s.state().isEmpty()) {
            panel.label("status", 4, 20, 92, s.state(), s.state().equals("Damaged") ? NamedTextColor.GOLD : DIM,
                    Panel.Align.CENTER);
            if (s.state().equals("Damaged"))
                panel.label("hint", 4, 30, 92, "rebuild its tower", DIM, Panel.Align.CENTER);
            return panel;
        }
        bigOrNot(panel, "now", 4, 12, 46, s.now(), NamedTextColor.WHITE, Panel.Align.LEFT);
        panel.label("sky", 50, 13, 47, fit(s.sky(), 47), NamedTextColor.WHITE, Panel.Align.RIGHT);
        panel.label("highLow", 50, 23, 47, fit(s.highLow(), 47), DIM, Panel.Align.RIGHT);
        if (!s.conditions().isEmpty()) panel.label("conditions", 4, 31, 92, fit(s.conditions(), 92), DIM, Panel.Align.LEFT);
        if (!s.note().isEmpty()) panel.label("note", 4, 41, 92, fit(s.note(), 92), NamedTextColor.GOLD, Panel.Align.LEFT);
        else if (!s.tomorrow().isEmpty())
            panel.label("tomorrow", 4, 41, 92, fit(s.tomorrow(), 92), COOL_TEXT, Panel.Align.LEFT);
        return panel;
    }

    // ---- The equipment monitor ----

    /** A row's icon: a fan that turns with its motor, or a flame for what burns. */
    static Material icon(EquipmentType type) {
        return switch (type) {
            case FURNACE, BOILER -> Material.BLAZE_POWDER;
            default -> Material.WIND_CHARGE;
        };
    }

    static String kind(EquipmentType type) {
        return switch (type) {
            case HEAT_PUMP -> "HP";
            case AIR_CONDITIONER -> "AC";
            case FURNACE -> "Furnace";
            case BOILER -> "Boiler";
            case POOL_HEAT_PUMP -> "Pool HP";
        };
    }

    /**
     * An equipment monitor: one row per kind of equipment in its system, each
     * with its own icon turning at that kind's speed (still and dark when it's
     * off), three rows to a page, &lt; &gt; to turn the page when there are more.
     */
    public static Panel monitor(String name, String system, boolean linked, List<RpmMonitor.Row> rows, int page,
                                int pages) {
        Panel panel = new Panel(Panel.Size.SIGN);
        boolean running = rows.stream().anyMatch(row -> row.running() > 0);
        int tone = !linked || rows.isEmpty() ? NEUTRAL : running ? HOLDING : OFF;
        pagedHeader(panel, system.isEmpty() ? name : name + " · " + system, tone, page, pages);
        if (!linked) {
            panel.label("unlinked", 4, 18, 92, "Not linked:", DIM, Panel.Align.CENTER);
            panel.label("unlinked2", 4, 28, 92, "use the link-tool", DIM, Panel.Align.CENTER);
            return panel;
        }
        if (rows.isEmpty()) {
            panel.label("none", 4, 24, 92, "No equipment", NamedTextColor.RED, Panel.Align.CENTER);
            return panel;
        }
        for (int i = 0; i < rows.size() && i < 3; i++) {
            RpmMonitor.Row row = rows.get(i);
            int y = 11 + i * 13;
            boolean on = row.running() > 0;
            double turns = on ? Math.min(2.5, row.rpm() / 1200.0) : 0.0;
            panel.icon("icon" + i, 7, y + 5, 10, icon(row.type()), row.type().fixedSpeed() ? 0.0 : turns, !on);
            String what = kind(row.type()) + " T" + row.tier().number() + (row.units() > 1 ? " ×" + row.units() : "");
            TextColor color = on ? NamedTextColor.WHITE : OFF_TEXT;
            panel.label("kind" + i, 14, y + 2, 48, TextWidth.fit(what, (int) (48 / Panel.SMALL)), color, Panel.Align.LEFT);
            String speed = on ? String.format(Locale.US, "%,.0f", row.rpm()) : "off";
            panel.label("speed" + i, 63, y + 2, 34, speed, on ? HOLD_TEXT : OFF_TEXT, Panel.Align.RIGHT);
        }
        return panel;
    }
}
