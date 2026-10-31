package com.github.blade.hvac.service;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Salvages an unreadable hvac.yml (BETA 1.0, /hvac registry repair --force):
 * keeps every entry that still reads on its own and drops only the broken
 * ones. EZvac writes the file in a fixed shape (a section at the margin, then
 * {@code itemN:} two spaces in, then its fields), so a typo or a cut-off file
 * spoils one entry, not the rest. Pure: the caller reads and writes files.
 */
public final class RegistrySalvage {
    /** A top-level key at the margin: {@code thermostats:}, {@code meta:}. */
    private static final Pattern SECTION = Pattern.compile("^[A-Za-z][\\w-]*:.*$");
    /** An entry two spaces in, {@code   item0:}, or any other key there, which starts an entry of its own. */
    private static final Pattern ENTRY = Pattern.compile("^  [^\\s#][^:]*:.*$");

    /**
     * @param yaml    what was kept, readable
     * @param kept    entries kept: devices and the like, not {@code meta}'s fields
     * @param dropped what was dropped, as {@code section.entry} (or a whole section)
     */
    public record Result(String yaml, int kept, List<String> dropped) {}

    private RegistrySalvage() {}

    public static Result salvage(String text) {
        List<List<String>> sections = new ArrayList<>();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            if (SECTION.matcher(line).matches()) sections.add(new ArrayList<>(List.of(line)));
            else if (!sections.isEmpty()) sections.get(sections.size() - 1).add(line);
        }
        StringBuilder out = new StringBuilder();
        List<String> dropped = new ArrayList<>();
        int kept = 0;
        for (List<String> section : sections) {
            String header = section.get(0);
            String name = header.substring(0, header.indexOf(':'));
            if (readable(String.join("\n", section))) {
                // A whole section that reads is kept as it is: meta, or one with nothing broken in it.
                out.append(String.join("\n", section)).append('\n');
                if (!name.equals("meta"))
                    kept += (int) section.stream().filter(line -> ENTRY.matcher(line).matches()).count();
                continue;
            }
            List<List<String>> entries = new ArrayList<>();
            for (String line : section.subList(1, section.size())) {
                if (ENTRY.matcher(line).matches()) entries.add(new ArrayList<>(List.of(line)));
                else if (!entries.isEmpty()) entries.get(entries.size() - 1).add(line);
            }
            if (entries.isEmpty()) {
                dropped.add(name);
                continue;
            }
            StringBuilder good = new StringBuilder(header.trim().endsWith(":") ? header : name + ":").append('\n');
            int goodEntries = 0;
            for (List<String> entry : entries) {
                String block = String.join("\n", entry);
                if (readable(name + ":\n" + block)) {
                    good.append(block).append('\n');
                    goodEntries++;
                } else {
                    String key = entry.get(0).trim();
                    dropped.add(name + "." + key.substring(0, key.indexOf(':')));
                }
            }
            if (goodEntries > 0) {
                out.append(good);
                if (!name.equals("meta")) kept += goodEntries;
            }
        }
        String yaml = out.toString();
        if (!readable(yaml)) throw new IllegalStateException("what was kept of hvac.yml still doesn't read");
        return new Result(yaml, kept, List.copyOf(dropped));
    }

    /**
     * Salvages plugins/EZvac/hvac.yml in place: the original is kept as
     * {@code hvac.yml.unreadable-<time>}, what still reads replaces it. For
     * a stopped EZvac; it starts from it at the next start.
     *
     * @return what was kept and dropped, and the original's new name
     */
    public static Saved salvageFile(java.io.File dataFolder) throws java.io.IOException {
        java.io.File data = new java.io.File(dataFolder, "hvac.yml");
        String text = java.nio.file.Files.readString(data.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        Result result = salvage(text);
        java.io.File original = new java.io.File(dataFolder, "hvac.yml.unreadable-" + System.currentTimeMillis());
        java.nio.file.Files.copy(data.toPath(), original.toPath());
        java.io.File temporary = new java.io.File(dataFolder, "hvac.yml.tmp");
        java.nio.file.Files.writeString(temporary.toPath(), result.yaml(), java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.move(temporary.toPath(), data.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new Saved(result, original.getName());
    }

    /** A salvage written to disk, and where the original went. */
    public record Saved(Result result, String original) {}

    /**
     * A backup in plugins/EZvac/backups (BETA 1.0, the recovery menu).
     *
     * @param name     its file name, such as {@code hvac-2026-10-30_08-00-00-auto.yml}
     * @param readable whether this EZvac can start from it: it reads, and isn't from a newer format
     */
    public record Backup(String name, boolean readable) {}

    /** The backups in plugins/EZvac/backups, newest first, each checked. */
    public static List<Backup> backups(java.io.File dataFolder) {
        java.io.File folder = new java.io.File(dataFolder, "backups");
        String[] names = folder.list();
        if (names == null) return List.of();
        List<Backup> out = new ArrayList<>();
        for (String name : RegistryBackups.newestFirst(List.of(names))) {
            boolean readable;
            try {
                YamlConfiguration configuration = new YamlConfiguration();
                configuration.load(new java.io.File(folder, name));
                readable = configuration.getInt("meta.format", HvacRegistry.FORMAT_VERSION) <= HvacRegistry.FORMAT_VERSION;
            } catch (java.io.IOException | InvalidConfigurationException | RuntimeException exception) {
                readable = false;
            }
            out.add(new Backup(name, readable));
        }
        return out;
    }

    /**
     * Restores a backup over hvac.yml, and its seasons file over seasons.yml
     * when there is one, for a stopped EZvac to start from at the next start.
     * The files it replaces are kept, as {@code hvac.yml.before-restore-<time>}
     * and {@code seasons.yml.before-restore-<time>}.
     *
     * @return the name hvac.yml was kept under, or null when there was none
     */
    public static String restore(java.io.File dataFolder, String name) throws java.io.IOException {
        java.io.File folder = new java.io.File(dataFolder, "backups");
        java.io.File backup = new java.io.File(folder, name);
        if (!name.startsWith("hvac-") || name.contains("/") || name.contains("\\") || !backup.isFile())
            throw new java.io.IOException("no backup called " + name);
        long now = System.currentTimeMillis();
        java.io.File data = new java.io.File(dataFolder, "hvac.yml");
        String kept = null;
        if (data.isFile()) {
            kept = "hvac.yml.before-restore-" + now;
            java.nio.file.Files.copy(data.toPath(), new java.io.File(dataFolder, kept).toPath());
        }
        java.io.File seasonsBackup = new java.io.File(folder, "seasons" + name.substring("hvac".length()));
        java.io.File seasons = new java.io.File(dataFolder, "seasons.yml");
        if (seasonsBackup.isFile()) {
            if (seasons.isFile())
                java.nio.file.Files.copy(seasons.toPath(),
                        new java.io.File(dataFolder, "seasons.yml.before-restore-" + now).toPath());
            java.nio.file.Files.copy(seasonsBackup.toPath(), seasons.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        java.nio.file.Files.copy(backup.toPath(), data.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return kept;
    }

    private static boolean readable(String yaml) {
        try {
            new YamlConfiguration().loadFromString(yaml);
            return true;
        } catch (InvalidConfigurationException | RuntimeException exception) {
            return false;
        }
    }
}
