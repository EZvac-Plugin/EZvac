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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Everything EZvac says to players, in the server's language (BETA 1.1).
 * The words live in {@code lang/<language>.yml} in the jar, English being
 * {@code en.yml}; a server may lay its own {@code plugins/EZvac/lang/<language>.yml}
 * over them, a key at a time. Each value is MiniMessage with named
 * placeholders ({@code <world>}); anything a key lacks comes from English.
 * Only colour and decoration tags work in a value. Command words, the
 * console and config comments stay English.
 *
 * <p>Read from any thread: the tables are swapped whole and never changed.
 */
public final class Lang {
    /** The language EZvac is written in, and what anything missing falls back to. */
    public static final String ENGLISH = "en";

    /**
     * Reads every value, with only colours and decorations ({@code <green>},
     * {@code <bold>}) besides the placeholders: a translation can't make text
     * that runs a command when clicked, shows on hover or hides itself. Any
     * other tag shows as typed.
     */
    private static final TagResolver STYLE = TagResolver.builder()
            .resolver(net.kyori.adventure.text.minimessage.tag.standard.StandardTags.color())
            .resolver(net.kyori.adventure.text.minimessage.tag.standard.StandardTags.decorations())
            .resolver(net.kyori.adventure.text.minimessage.tag.standard.StandardTags.reset())
            .build();
    static final MiniMessage MINI = MiniMessage.builder().tags(STYLE).build();
    /** A template that only colours and fills in placeholders, split into text and placeholder names. */
    private static final Map<String, List<String>> PIECES = new ConcurrentHashMap<>();
    /** What {@link #PIECES} holds for a template MiniMessage has to read. */
    private static final List<String> READ_IT = new ArrayList<>();
    private static final Pattern TAG = Pattern.compile("[!/]?[a-z0-9_#:-]+");
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
        String template = template(key);
        String quick = quickly(template, args);
        return quick != null ? quick
                : PlainTextComponentSerializer.plainText().serialize(MINI.deserialize(template, args));
    }

    /**
     * Plain text the quick way, for screens that ask many times a second:
     * colours dropped and placeholders filled in, exactly as MiniMessage
     * would. Null when MiniMessage has to read it (an escape, an unknown tag,
     * a placeholder that isn't plain text).
     */
    static String quickly(String template, TagResolver... args) {
        List<String> pieces = PIECES.computeIfAbsent(template, Lang::pieces);
        if (pieces == READ_IT) return null;
        StringBuilder out = new StringBuilder(template.length() + 16);
        for (int i = 0; i < pieces.size(); i++) {
            if (i % 2 == 0) {
                out.append(pieces.get(i));
                continue;
            }
            String value = null;
            for (TagResolver arg : args)
                if (arg instanceof Plain plain && plain.name().equals(pieces.get(i))) value = plain.value();
            if (value == null) return null;
            out.append(value);
        }
        return out.toString();
    }

    /** Text and placeholder names in turn, colours left out; {@link #READ_IT} when it isn't that simple. */
    private static List<String> pieces(String template) {
        if (template.indexOf('\\') >= 0) return READ_IT;
        List<String> pieces = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int at = 0;
        while (at < template.length()) {
            int open = template.indexOf('<', at);
            if (open < 0) {
                text.append(template, at, template.length());
                break;
            }
            int close = template.indexOf('>', open);
            if (close < 0) return READ_IT;
            String inside = template.substring(open + 1, close);
            if (!TAG.matcher(inside).matches()) return READ_IT;
            text.append(template, at, open);
            String name = inside.replaceFirst("^[!/]", "");
            int colon = name.indexOf(':');
            if (STYLE.has(colon < 0 ? name : name.substring(0, colon))) {
                at = close + 1;
                continue;
            }
            if (!inside.equals(name) || colon >= 0) return READ_IT;
            pieces.add(text.toString());
            pieces.add(name);
            text.setLength(0);
            at = close + 1;
        }
        pieces.add(text.toString());
        return List.copyOf(pieces);
    }

    /** A value for a placeholder, put in as it is: a name with {@code <red>} in it stays as typed. */
    public static TagResolver arg(String name, Object value) {
        String text = String.valueOf(value);
        return new Plain(name, text, Placeholder.unparsed(name, text));
    }

    /** A placeholder that is plain text, which {@link #text} can fill in without MiniMessage. */
    private record Plain(String name, String value, TagResolver resolver) implements TagResolver {
        @Override
        public net.kyori.adventure.text.minimessage.tag.Tag resolve(String tag,
                net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue arguments,
                net.kyori.adventure.text.minimessage.Context context)
                throws net.kyori.adventure.text.minimessage.ParsingException {
            return resolver.resolve(tag, arguments, context);
        }

        @Override
        public boolean has(String tag) {
            return resolver.has(tag);
        }
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
