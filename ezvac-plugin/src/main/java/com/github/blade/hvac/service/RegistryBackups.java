package com.github.blade.hvac.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Backups of the registry (BETA 1.0), in plugins/EZvac/backups: one made
 * from /hvac quicksettings, or automatically every few hours of real time
 * ({@code backups.auto-every-hours}). Each is hvac.yml as it is that moment,
 * with seasons.yml beside it. Only the newest {@code backups.keep} automatic
 * ones are kept; one made by hand stays until someone deletes it.
 *
 * <p>Restoring is by hand, with the server stopped: copy a backup over
 * hvac.yml (and its seasons file over seasons.yml). When hvac.yml can't be
 * read, the registry falls back to the newest readable backup itself, and a
 * stopped EZvac's recovery menu restores one ({@link RegistrySalvage}).
 */
public final class RegistryBackups {
    /** The automatic intervals, in hours; 0 is off. */
    public static final List<Integer> INTERVALS = List.of(0, 4, 8, 12, 24, 48, 168);
    /** Automatic backups are on by default, once a day of real time (BETA 1.0 Pilot 2; off in Pilot 1). */
    public static final int DEFAULT_HOURS = 24;
    static final String AUTO = "auto", MANUAL = "manual";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    /** Whether an automatic one is due is checked this often. */
    private static final long CHECK_TICKS = 20L * 60;
    private static final long HOUR_MILLIS = 60L * 60 * 1000;

    /** What a backup folder holds: how many backups, and the newest (null when none). */
    public record Summary(int count, String newest, long newestAt) {}

    private final Plugin plugin;
    private final HvacRegistry registry;
    private final File folder;
    private final File seasons;
    private final FileConfiguration config;
    private final Runnable saveConfig;
    private volatile boolean writing;
    private BukkitTask task;

    public RegistryBackups(Plugin plugin, HvacRegistry registry, FileConfiguration config, Runnable saveConfig) {
        this.plugin = plugin;
        this.registry = registry;
        this.folder = new File(plugin.getDataFolder(), "backups");
        this.seasons = new File(plugin.getDataFolder(), "seasons.yml");
        this.config = config;
        this.saveConfig = saveConfig;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::backUpIfDue, CHECK_TICKS, CHECK_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public File folder() { return folder; }

    /** Hours between automatic backups; 0 is off. A value that is no interval counts as off. */
    public int intervalHours() {
        int hours = config.getInt("backups.auto-every-hours", DEFAULT_HOURS);
        return INTERVALS.contains(hours) ? hours : 0;
    }

    public void setIntervalHours(int hours) {
        if (!INTERVALS.contains(hours)) throw new IllegalArgumentException("not a backup interval: " + hours);
        config.set("backups.auto-every-hours", hours);
        saveConfig.run();
    }

    /** How many automatic backups are kept, at least one. */
    public int keep() { return Math.max(1, config.getInt("backups.keep", 10)); }

    /** The interval after this one, wrapping from a week back to off; one that is no interval counts as off. */
    public static int nextInterval(int hours) {
        int index = Math.max(0, INTERVALS.indexOf(hours));
        return INTERVALS.get((index + 1) % INTERVALS.size());
    }

    /** "Every 24 hours", "Every week" or "Off". */
    public static String describe(int hours) {
        if (hours == 0) return "Off";
        if (hours == 168) return "Every week";
        return "Every " + hours + " hours";
    }

    /**
     * Backs up now, by hand. {@code done} gets the backup's file name, or
     * null and why it failed; on the main thread.
     */
    public void backUpNow(BiConsumer<String, String> done) {
        backUp(false, done);
    }

    private void backUpIfDue() {
        int hours = intervalHours();
        if (hours == 0 || writing) return;
        if (!due(newestAutoAt(), System.currentTimeMillis(), hours)) return;
        backUp(true, (name, failure) -> {
            if (failure != null)
                plugin.getLogger().warning("The automatic registry backup failed (" + failure + "); trying again"
                        + " in a minute.");
            else plugin.getLogger().info("Backed up the registry: backups/" + name + " (automatic, "
                    + describe(hours).toLowerCase(java.util.Locale.ROOT) + ").");
        });
    }

    private void backUp(boolean auto, BiConsumer<String, String> done) {
        if (writing) {
            done.accept(null, "a backup is already being written");
            return;
        }
        // The snapshot is taken here, on the main thread; only the writing moves off it.
        String yaml = registry.snapshot().saveToString();
        String stamp = LocalDateTime.now().format(STAMP);
        File target = new File(folder, name("hvac", stamp, auto));
        File seasonsTarget = new File(folder, name("seasons", stamp, auto));
        writing = true;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String failure = null;
            try {
                Files.createDirectories(folder.toPath());
                write(yaml, target);
                if (seasons.isFile()) Files.copy(seasons.toPath(), seasonsTarget.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                if (auto) prune();
            } catch (IOException | RuntimeException exception) {
                failure = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            } finally {
                writing = false;
            }
            String why = failure;
            if (!plugin.isEnabled()) return;
            try {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> done.accept(why == null ? target.getName() : null, why));
            } catch (IllegalStateException | org.bukkit.plugin.IllegalPluginAccessException disabling) {
                // EZvac is stopping; the file is written either way.
            }
        });
    }

    /** Off the main thread: to a temporary file first, so a backup is never half-written. */
    private static void write(String yaml, File target) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        Files.writeString(temporary.toPath(), yaml, StandardCharsets.UTF_8);
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        // A backup that can't be read back is no backup.
        YamlConfiguration check = new YamlConfiguration();
        try {
            check.load(target);
        } catch (org.bukkit.configuration.InvalidConfigurationException unreadable) {
            throw new IOException("the backup did not read back: " + unreadable.getMessage());
        }
    }

    /** Deletes the automatic backups past the newest {@link #keep()}, with their seasons files. */
    private void prune() throws IOException {
        for (String name : toPrune(names(), keep())) {
            Files.deleteIfExists(new File(folder, name).toPath());
            Files.deleteIfExists(new File(folder, "seasons" + name.substring("hvac".length())).toPath());
        }
    }

    private List<String> names() {
        String[] names = folder.list();
        return names == null ? List.of() : Arrays.asList(names);
    }

    private long newestAutoAt() {
        List<String> autos = backups(names(), true);
        return autos.isEmpty() ? 0L : new File(folder, autos.get(autos.size() - 1)).lastModified();
    }

    /** The backups in the folder, newest last. */
    public Summary summary() {
        List<String> all = new ArrayList<>(backups(names(), false));
        all.addAll(backups(names(), true));
        all.sort(RegistryBackups::byStamp);
        if (all.isEmpty()) return new Summary(0, null, 0L);
        String newest = all.get(all.size() - 1);
        return new Summary(all.size(), newest, new File(folder, newest).lastModified());
    }

    static String name(String file, String stamp, boolean auto) {
        return file + "-" + stamp + "-" + (auto ? AUTO : MANUAL) + ".yml";
    }

    /** Every registry backup among these file names, automatic and manual, newest first (BETA 1.0, recovery). */
    static List<String> newestFirst(List<String> names) {
        List<String> all = new ArrayList<>(backups(names, false));
        all.addAll(backups(names, true));
        all.sort(RegistryBackups::byStamp);
        java.util.Collections.reverse(all);
        return all;
    }

    /** The registry backups among these file names, automatic or manual, oldest first. */
    static List<String> backups(List<String> names, boolean auto) {
        String suffix = "-" + (auto ? AUTO : MANUAL) + ".yml";
        return names.stream().filter(name -> name.startsWith("hvac-") && name.endsWith(suffix))
                .sorted(RegistryBackups::byStamp).toList();
    }

    /** The automatic backups to delete so only the newest {@code keep} stay; manual ones are never among them. */
    static List<String> toPrune(List<String> names, int keep) {
        List<String> autos = backups(names, true);
        return autos.size() <= keep ? List.of() : autos.subList(0, autos.size() - keep);
    }

    /** Whether an automatic backup is due: none yet, or the newest is at least the interval old. */
    static boolean due(long newestAt, long now, int hours) {
        return hours > 0 && (newestAt <= 0L || now - newestAt >= hours * HOUR_MILLIS);
    }

    /** The date stamp sorts in time order: hvac-2026-10-29_14-05-00-auto.yml. */
    private static int byStamp(String a, String b) {
        return stamp(a).compareTo(stamp(b));
    }

    private static String stamp(String name) {
        int end = name.lastIndexOf('-');
        return end > 5 ? name.substring(5, end) : name;
    }
}
