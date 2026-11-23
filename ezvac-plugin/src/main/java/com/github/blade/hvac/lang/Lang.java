package com.github.blade.hvac.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Everything EZvac says to players, in the server's language (BETA 1.1).
 * The words live in {@code lang/<language>.yml} in the jar, English being
 * {@code en.yml}; a server may lay its own {@code plugins/EZvac/lang/<language>.yml}
 * over them, a key at a time. Each value is MiniMessage with named
 * placeholders ({@code <world>}); anything a key lacks comes from English.
 * Command words, the console and config comments stay English.
 *
 * <p>Read from any thread: the tables are swapped whole and never changed.
 */
public final class Lang {
    /** The language EZvac is written in, and what anything missing falls back to. */
    public static final String ENGLISH = "en";

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static volatile Map<String, String> english = read(ENGLISH);
    private static volatile Map<String, String> current = english;
    private static volatile String language = ENGLISH;
    private static volatile Consumer<String> warn = message -> java.util.logging.Logger.getLogger("EZvac").warning(message);
    private static final Set<String> missing = ConcurrentHashMap.newKeySet();

    private Lang() {}

    /**
     * Sets the server's language: the jar's file for it, then the server's
     * own, over English.
     *
     * @param folder EZvac's data folder, for {@code lang/<language>.yml}; null for none
     */
    public static void load(String code, File folder, Consumer<String> warnings) {
        if (warnings != null) warn = warnings;
        missing.clear();
        String wanted = code == null || code.isBlank() ? ENGLISH : code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        Map<String, String> table = new HashMap<>(english);
        Map<String, String> bundled = read(wanted);
        File own = folder == null ? null : new File(new File(folder, "lang"), wanted + ".yml");
        boolean hasOwn = own != null && own.isFile();
        if (bundled.isEmpty() && !hasOwn && !wanted.equals(ENGLISH)) {
            warn.accept("There are no '" + wanted + "' words for EZvac yet (language in config.yml), so it speaks"
                    + " English. Translations are welcome: https://ezvac-plugin.github.io/EZvac/translating/");
            wanted = ENGLISH;
        }
        table.putAll(bundled);
        if (hasOwn) table.putAll(flatten(YamlConfiguration.loadConfiguration(own)));
        current = Map.copyOf(table);
        language = wanted;
    }

    /** Back to English only, as before any load (for tests). */
    public static void reset() {
        current = english;
        language = ENGLISH;
        missing.clear();
    }

    /** The language EZvac speaks now, such as {@code en}. */
    public static String language() {
        return language;
    }

    /** A message, coloured as its template says. */
    public static Component message(String key, TagResolver... args) {
        return MINI.deserialize(template(key), args);
    }

    /** The same as plain text, for signs and screens that measure their text. */
    public static String text(String key, TagResolver... args) {
        return PlainTextComponentSerializer.plainText().serialize(message(key, args));
    }

    /** A value for a placeholder, put in as it is: a name with {@code <red>} in it stays as typed. */
    public static TagResolver arg(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** A placeholder that is itself a component, already coloured. */
    public static TagResolver arg(String name, Component value) {
        return Placeholder.component(name, value);
    }

    /**
     * A key made of a fixed prefix and a part known only when it runs, such
     * as {@code key("wind.level", "gale")}. Every key under the prefix
     * counts as used.
     */
    public static String key(String prefix, String part) {
        return prefix + "." + part.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Whether a key has words, in the language or in English. */
    public static boolean has(String key) {
        return current.containsKey(key);
    }

    private static String template(String key) {
        String value = current.get(key);
        if (value != null) return value;
        if (missing.add(key)) warn.accept("EZvac has no words for '" + key + "'; showing the key instead.");
        return key;
    }

    /** The English table, key to template (for tests). */
    static Map<String, String> english() {
        return english;
    }

    /** A language bundled in the jar, flattened; empty when there is none. */
    static Map<String, String> read(String code) {
        try (InputStream in = Lang.class.getClassLoader().getResourceAsStream("lang/" + code + ".yml")) {
            if (in == null) return Map.of();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return flatten(YamlConfiguration.loadConfiguration(reader));
            }
        } catch (java.io.IOException failure) {
            return Map.of();
        }
    }

    private static Map<String, String> flatten(YamlConfiguration yaml) {
        Map<String, String> out = new HashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key)) continue;
            Object value = yaml.get(key);
            if (value != null) out.put(key, String.valueOf(value));
        }
        return out;
    }
}
