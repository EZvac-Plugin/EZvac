package com.github.blade.hvac.gui;

import com.github.blade.hvac.config.HudSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.lang.Lang;

import java.util.List;
import java.util.Locale;

import static com.github.blade.hvac.lang.Lang.arg;

/**
 * The first-start setup's pages and how its choices read (BETA 1.0 Pilot 10).
 * Pure: the menu ({@link SetupWizard}) draws them and applies the choices.
 */
public final class SetupSteps {
    private SetupSteps() {}

    /** The pages, in order. */
    public enum Step {
        WELCOME, TEMPERATURES, WIND, HUD, UPDATES, BACKUPS, STATISTICS, ADDONS, DONE;

        public String title() { return Lang.text(Lang.key("menu.setup.step", name())); }

        /** The page after this one, or null on the last. */
        public Step next() {
            return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
        }

        /** The page before this one, or null on the first. */
        public Step previous() {
            return ordinal() > 0 ? values()[ordinal() - 1] : null;
        }

        /**
         * "Setup 3/9 · Wind": short, so it fits a chest's title (BETA 1.0
         * Pilot 10: "EZvac setup · 7 of 9: Anonymous statistics" ran off it).
         */
        public String menuTitle() {
            return Lang.text("menu.setup.menu-title", arg("n", ordinal() + 1),
                    arg("of", values().length), arg("title", title()));
        }
    }

    /** The HUD for players who never chose: none, or one of the styles. */
    public enum HudChoice {
        OFF, SIDEBAR, ACTIONBAR, BOSSBAR;

        public String label() { return Lang.text(Lang.key("menu.setup.hud.label", name())); }

        public String what() { return Lang.text(Lang.key("menu.setup.hud.what", name())); }

        /** The style it sets; null for off. */
        public HudSettings.Style style() {
            return this == OFF ? null : HudSettings.Style.valueOf(name());
        }

        /** What the settings say now: off unless new players are shown it. */
        public static HudChoice of(boolean showToNewPlayers, String style) {
            if (!showToNewPlayers) return OFF;
            String normalized = style == null ? "" : style.trim().toUpperCase(Locale.ROOT).replace("_", "").replace("-", "");
            for (HudChoice choice : values()) if (choice != OFF && choice.name().equals(normalized)) return choice;
            return SIDEBAR;
        }
    }

    /** The backup intervals the setup offers, in hours (0 is off). */
    public static final List<Integer> BACKUP_HOURS = List.of(0, 12, 24, 168);

    public static String backupLabel(int hours) {
        return switch (hours) {
            case 0 -> Lang.text("menu.setup.backups.off");
            case 168 -> Lang.text("menu.setup.backups.weekly");
            default -> Lang.text("menu.setup.backups.every", arg("hours", hours));
        };
    }

    public static String windLabel(WindSettings.Units units) {
        return switch (units) {
            case AUTO -> Lang.text("menu.setup.wind.auto");
            case MS -> "m/s";
            case KMH -> "km/h";
            case MPH -> "mph";
        };
    }

    public static String channelLabel(String setting) {
        return switch (setting) {
            case "stable" -> Lang.text("menu.setup.channel.stable");
            case "pilot" -> Lang.text("menu.setup.channel.pilot");
            default -> Lang.text("menu.setup.channel.auto");
        };
    }

    /**
     * The sidebar's title with a server's name in place of "EZvac", kept in
     * the stock title's style. The name is escaped by the caller.
     */
    public static String title(String escapedName) {
        return "<gradient:#3999fb:#f69500><bold>Climate</bold></gradient> <dark_gray>-</dark_gray> <gray>" + escapedName;
    }

    /** A name typed for the title: trimmed and at most 24 characters; null when nothing is left. */
    public static String cleanName(String typed) {
        if (typed == null) return null;
        String name = typed.strip().replaceAll("\\s+", " ");
        if (name.isEmpty()) return null;
        return name.length() > 24 ? name.substring(0, 24) : name;
    }

    /** The summary on the last page. */
    public static List<String> summary(TemperatureUnit units, WindSettings.Units wind, HudChoice hud, String channel,
                                       int backupHours, boolean statistics) {
        return List.of(Lang.text("menu.setup.summary.units", arg("unit", units.name())),
                Lang.text("menu.setup.summary.wind", arg("wind", windLabel(wind))),
                Lang.text("menu.setup.summary.hud", arg("hud", hud.label())),
                Lang.text("menu.setup.summary.updates", arg("channel", channelLabel(channel))),
                Lang.text("menu.setup.summary.backups", arg("backups", backupLabel(backupHours))),
                Lang.text(statistics ? "menu.setup.summary.statistics-on" : "menu.setup.summary.statistics-off"));
    }
}
