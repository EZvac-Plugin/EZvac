package com.github.blade.hvac.gui;

import com.github.blade.hvac.config.HudSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.config.WindSettings;

import java.util.List;
import java.util.Locale;

/**
 * The first-start setup's pages and how its choices read (BETA 1.0 Pilot 10).
 * Pure: the menu ({@link SetupWizard}) draws them and applies the choices.
 */
public final class SetupSteps {
    private SetupSteps() {}

    /** The pages, in order. */
    public enum Step {
        WELCOME("Welcome"), TEMPERATURES("Temperatures"), WIND("Wind speeds"), HUD("The HUD"), UPDATES("Updates"),
        BACKUPS("Backups"), STATISTICS("Anonymous statistics"), ADDONS("Official addons"), DONE("All set");

        private final String title;

        Step(String title) {
            this.title = title;
        }

        public String title() { return title; }

        /** The page after this one, or null on the last. */
        public Step next() {
            return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
        }

        /** The page before this one, or null on the first. */
        public Step previous() {
            return ordinal() > 0 ? values()[ordinal() - 1] : null;
        }

        /** "EZvac setup · 3 of 9: Wind speeds". */
        public String menuTitle() {
            return "EZvac setup · " + (ordinal() + 1) + " of " + values().length;
        }
    }

    /** The HUD for players who never chose: none, or one of the styles. */
    public enum HudChoice {
        OFF("Off", "Players turn it on with /hvac hud"),
        SIDEBAR("Sidebar", "A scoreboard on the right"),
        ACTIONBAR("Action bar", "One line above the hotbar"),
        BOSSBAR("Boss bar", "A bar across the top");

        private final String label, what;

        HudChoice(String label, String what) {
            this.label = label;
            this.what = what;
        }

        public String label() { return label; }

        public String what() { return what; }

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
            case 0 -> "Off";
            case 168 -> "Weekly";
            default -> "Every " + hours + " hours";
        };
    }

    public static String windLabel(WindSettings.Units units) {
        return switch (units) {
            case AUTO -> "Follow the temperatures";
            case MS -> "m/s";
            case KMH -> "km/h";
            case MPH -> "mph";
        };
    }

    public static String channelLabel(String setting) {
        return switch (setting) {
            case "stable" -> "Stable";
            case "pilot" -> "Pilot";
            default -> "Follow this build";
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
        return List.of("Temperatures: °" + units.name(), "Wind speeds: " + windLabel(wind),
                "HUD for new players: " + hud.label(), "Updates: " + channelLabel(channel),
                "Backups: " + backupLabel(backupHours), "Anonymous statistics: " + (statistics ? "On" : "Off"));
    }
}
