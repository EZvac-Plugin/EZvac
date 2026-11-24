package com.github.blade.hvac.lang;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The words (BETA 1.1): every key the code asks for exists, every one is used, and translations keep their placeholders. */
class LangTest {
    private static final Path SOURCES = Path.of("src/main/java");
    private static final Path LANG = Path.of("src/main/resources/lang");
    private static final Pattern ASKED = Pattern.compile(
            "(?:(?:Lang\\.(?:message|text)|new UserError|lore)\\(|(?:say|hint|done)\\([^,()]+(?:\\(\\))?,)\\s*\"([a-z0-9_.-]+)\"");
    private static final Pattern PREFIX = Pattern.compile("Lang\\.key\\(\\s*\"([a-z0-9_.-]+)\"");
    private static final Pattern LITERAL = Pattern.compile("\"([a-z0-9_-]+(?:\\.[a-z0-9_-]+)+)\"");
    private static final Pattern TAG = Pattern.compile("<([a-z0-9_-]+)(?::[^>]*)?>");

    @AfterEach
    void english() {
        Lang.reset();
    }

    private static String sources() throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator)
                all.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
        }
        return all.toString();
    }

    @Test
    void everyKeyTheCodeAsksForHasEnglishWords() throws IOException {
        Map<String, String> english = Lang.english();
        Set<String> lacking = new TreeSet<>();
        Matcher asked = ASKED.matcher(sources());
        while (asked.find()) if (!english.containsKey(asked.group(1))) lacking.add(asked.group(1));
        Matcher prefix = PREFIX.matcher(sources());
        while (prefix.find()) {
            String p = prefix.group(1) + ".";
            if (english.keySet().stream().noneMatch(k -> k.startsWith(p))) lacking.add(p + "*");
        }
        assertEquals(Set.of(), lacking, "keys asked for with no words in en.yml");
    }

    @Test
    void everyEnglishKeyIsUsed() throws IOException {
        String code = sources();
        Set<String> used = new TreeSet<>();
        Matcher literal = LITERAL.matcher(code);
        while (literal.find()) used.add(literal.group(1));
        List<String> prefixes = new ArrayList<>();
        Matcher prefix = PREFIX.matcher(code);
        while (prefix.find()) prefixes.add(prefix.group(1) + ".");
        Set<String> unused = new TreeSet<>();
        for (String key : Lang.english().keySet())
            if (!used.contains(key) && prefixes.stream().noneMatch(key::startsWith)) unused.add(key);
        assertEquals(Set.of(), unused, "en.yml keys nothing uses");
    }

    /** Each value filled with X for every placeholder, as plain text: nothing left that looks like a tag. */
    @Test
    void everyEnglishValueReadsAsWords() {
        assertEquals(List.of(), unreadable(Lang.english()));
    }

    /** The same for every language file: a misspelled colour or a broken tag fails the build, not a screen. */
    @Test
    void everyTranslatedValueReadsAsWords() throws IOException {
        List<String> wrong = new ArrayList<>();
        try (Stream<Path> files = Files.list(LANG)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String code = file.getFileName().toString().replace(".yml", "");
                for (String problem : unreadable(Lang.read(code))) wrong.add(code + ": " + problem);
            }
        }
        assertEquals(List.of(), wrong);
    }

    @Test
    void aBrokenTranslationIsCaught() {
        String key = Lang.english().keySet().stream().filter(k -> placeholders(Lang.english().get(k)).size() == 1)
                .findFirst().orElseThrow();
        String name = placeholders(Lang.english().get(key)).iterator().next();
        assertEquals(List.of(), unreadable(Map.of(key, "<green>Fine <" + name + ">")));
        assertEquals(1, unreadable(Map.of(key, "<gren>Misspelt <" + name + ">")).size());
        assertEquals(1, unreadable(Map.of(key, "<green Unclosed <" + name + ">")).size());
        assertEquals(1, unreadable(Map.of(key, "   ")).size());
    }

    /**
     * What doesn't read as words in a table: each value, filled with X for
     * every placeholder its English has, must show no tag and not be empty.
     */
    private static List<String> unreadable(Map<String, String> table) {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> entry : new java.util.TreeMap<>(table).entrySet()) {
            String english = Lang.english().getOrDefault(entry.getKey(), entry.getValue());
            List<TagResolver> args = new ArrayList<>();
            for (String name : placeholders(english)) args.add(Lang.arg(name, "X"));
            String text;
            try {
                text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(entry.getValue(),
                                args.toArray(TagResolver[]::new)));
            } catch (RuntimeException broken) {
                wrong.add(entry.getKey() + " doesn't read: " + broken.getMessage());
                continue;
            }
            if (text.contains("<") || text.contains(">")) wrong.add(entry.getKey() + " leaves a tag: " + text);
            else if (text.isBlank()) wrong.add(entry.getKey() + " is empty");
        }
        return wrong;
    }

    @Test
    void translationsKeepTheirPlaceholders() throws IOException {
        Map<String, String> english = Lang.english();
        List<String> wrong = new ArrayList<>();
        try (Stream<Path> files = Files.list(LANG)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String code = file.getFileName().toString().replace(".yml", "");
                if (code.equals(Lang.ENGLISH)) continue;
                for (Map.Entry<String, String> entry : Lang.read(code).entrySet()) {
                    String base = english.get(entry.getKey());
                    if (base == null) wrong.add(code + ": " + entry.getKey() + " is not an English key");
                    else if (!placeholders(base).equals(placeholders(entry.getValue())))
                        wrong.add(code + ": " + entry.getKey() + " has " + placeholders(entry.getValue())
                                + ", English has " + placeholders(base));
                }
            }
        }
        assertEquals(List.of(), wrong);
    }

    @Test
    void aServersOwnFileChangesSingleWordsAndTheRestStaysEnglish(@TempDir File folder) throws IOException {
        String key = Lang.english().keySet().stream().sorted().findFirst().orElseThrow();
        File lang = new File(folder, "lang");
        assertTrue(lang.mkdirs());
        org.bukkit.configuration.file.YamlConfiguration own = new org.bukkit.configuration.file.YamlConfiguration();
        own.set(key, "Changed");
        own.save(new File(lang, "en.yml"));
        Lang.load("en", folder, message -> fail(message));
        assertEquals("Changed", Lang.text(key));
        assertEquals(Lang.english().size(), Lang.english().keySet().stream().filter(Lang::has).count());
    }

    @Test
    void anUnknownLanguageSaysSoOnceAndSpeaksEnglish(@TempDir File folder) {
        List<String> warnings = new ArrayList<>();
        Lang.load("zz", folder, warnings::add);
        assertEquals("en", Lang.language());
        assertEquals(1, warnings.size());
        assertEquals("missing.key", Lang.text("missing.key"), "a key with no words shows itself");
        Lang.text("missing.key");
        assertEquals(2, warnings.size(), "said once per key");
    }

    @Test
    void aPlaceholderValueIsNeverReadAsTags() {
        assertEquals("<red>World",
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<w>",
                                Lang.arg("w", "<red>World"))));
    }

    @Test
    void noLanguageFileSaysAKeyTwice() throws IOException {
        Pattern entry = Pattern.compile("^( *)(\"[^\"]+\"|[^#\\s\"][^:]*):");
        List<String> twice = new ArrayList<>();
        try (Stream<Path> files = Files.list(LANG)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                List<String> path = new ArrayList<>();
                Set<String> seen = new java.util.HashSet<>();
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    Matcher m = entry.matcher(line);
                    if (!m.find()) continue;
                    int depth = m.group(1).length() / 2;
                    while (path.size() > depth) path.remove(path.size() - 1);
                    path.add(m.group(2).replace("\"", ""));
                    String key = String.join(".", path);
                    if (!seen.add(key)) twice.add(file.getFileName() + ": " + key);
                }
            }
        }
        assertEquals(List.of(), twice, "YAML keeps only the last of a repeated key");
    }

    private static Set<String> placeholders(String template) {
        Set<String> names = new TreeSet<>();
        Matcher tag = TAG.matcher(template);
        while (tag.find())
            if (!TagResolver.standard().has(tag.group(1))) names.add(tag.group(1));
        return names;
    }
}
