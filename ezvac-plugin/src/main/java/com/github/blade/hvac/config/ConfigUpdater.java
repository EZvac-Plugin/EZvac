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
