package com.github.blade.hvac.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Brings an existing config.yml up to date with the one bundled in the jar.
 * EZvac only writes config.yml when there is none, so a server upgraded from
 * an older release never saw the settings added since - they applied with
 * their defaults, invisibly.
 *
 * <p>It adds only what is missing, with its default value and its comments.
 * It never changes a value the server already has, not even one whose default
 * has since changed, never removes anything, and never replaces a value with
 * a section.
 */
public final class ConfigUpdater {
    private ConfigUpdater() {}

    /**
     * Adds every setting in {@code defaults} that {@code target} lacks.
     *
     * @return the paths of the settings added, in the bundled file's order; empty when nothing was missing
     */
    public static List<String> addMissing(YamlConfiguration target, YamlConfiguration defaults) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(defaults, "defaults");
        List<String> added = new ArrayList<>();
        // Parents come before their children in getKeys(true), so a new
        // section is created, with its header comment, before its settings.
        for (String path : defaults.getKeys(true)) {
            if (target.contains(path, true) || blockedByValue(target, path)) continue;
            if (defaults.isConfigurationSection(path)) {
                target.createSection(path);
            } else {
                target.set(path, defaults.get(path));
                added.add(path);
            }
            target.setComments(path, defaults.getComments(path));
            target.setInlineComments(path, defaults.getInlineComments(path));
        }
        return added;
    }

    /**
     * Everything a start does to bring an older config.yml up to date (BETA 1.0
     * gathered it here, so a test can run the same steps on every release's
     * file): the missing settings, then the values carried over from settings
     * that replaced older ones, then the stock HUD layouts, step by step from
     * the oldest. Nothing at all happens to a file that lacks nothing.
     *
     * @return the paths added or brought up to date, as {@link #addMissing} reports them
     */
    public static List<String> upgrade(YamlConfiguration target, YamlConfiguration defaults) {
        List<String> added = addMissing(target, defaults);
        if (added.isEmpty()) return added;
        SoundSettings.carryOverHum(target, added);
        TemperatureUnit.carryOver(target, added);
        HudSettings.addSeasonLine(target, added);
        HudSettings.addAddonsLine(target, added);
        HudSettings.addAirLine(target, added);
        HudSettings.addWindLine(target, added);
        HudSettings.adoptSections(target, added);
        HudSettings.adoptAccents(target, added);
        SizingSettings.offOnUpgrade(target, added);
        pilotTwo(target, added);
        return added;
    }

    /** Added in BETA 1.0 Pilot 2, so its being added marks a server's first Pilot 2 start. */
    public static final String PILOT_2 = "updates.channel";

    /** Pilot 1's humidifier bubbles, moved to a deeper pitch in Pilot 2. */
    static final String OLD_HUMIDIFIER_BUBBLES = "block.bubble_column.upwards_ambient 0.35 1.0";

    /**
     * A server's first Pilot 2 start: the stock humidifier sound moves to its
     * deeper pitch (one an owner changed is kept), and automatic backups go
     * from Pilot 1's off to every 24 hours (maintainer, 2026-11-02: Pilot 1's
     * 0 was its default, not a choice). An ALPHA config gets both from the
     * bundled file.
     */
    static void pilotTwo(YamlConfiguration target, List<String> added) {
        if (!added.contains(PILOT_2)) return;
        String bubbles = "audio.sounds." + SoundSettings.Event.HUMIDIFIER_BUBBLES.key();
        if (!added.contains(bubbles) && List.of(OLD_HUMIDIFIER_BUBBLES).equals(target.getStringList(bubbles)))
            target.set(bubbles, List.of(new SoundSettings.Cue("block.bubble_column.upwards_ambient", 0.35f, 0.5f).line()));
        String backups = "backups.auto-every-hours";
        if (!added.contains(backups) && target.getInt(backups, 0) == 0)
            target.set(backups, com.github.blade.hvac.service.RegistryBackups.DEFAULT_HOURS);
    }

    /** True when some parent of the path holds a plain value, which adding the path would overwrite. */
    private static boolean blockedByValue(ConfigurationSection target, String path) {
        int dot = path.indexOf('.');
        while (dot > 0) {
            String parent = path.substring(0, dot);
            if (target.contains(parent, true) && !target.isConfigurationSection(parent)) return true;
            dot = path.indexOf('.', dot + 1);
        }
        return false;
    }

    /** The top-level sections the added paths belong to, for a short log line. */
    public static List<String> topLevel(List<String> paths) {
        List<String> sections = new ArrayList<>();
        for (String path : paths) {
            int dot = path.indexOf('.');
            String top = dot < 0 ? path : path.substring(0, dot);
            if (!sections.contains(top)) sections.add(top);
        }
        return sections;
    }
}
