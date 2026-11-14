package com.github.blade.hvac.addons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * EZvac's official addons (BETA 1.0 Pilot 4): what the Addons menu
 * recognises on a server and offers in its store. The list ships with EZvac
 * ({@code addons.json}) and is refreshed from the same file in the
 * EZvac-Plugin organisation on GitHub, so a new addon shows up without an
 * EZvac update.
 *
 * <p>Nothing read here is trusted further than being shown: every name is
 * checked against a narrow pattern, texts are cut and cleaned, and the only
 * link ever made is {@code https://github.com/EZvac-Plugin/<repository>}.
 * Pure.
 */
public final class AddonCatalogue {
    /** Where every official addon lives; no other link is ever shown. */
    public static final String ORGANISATION = "https://github.com/EZvac-Plugin/";
    /** More than this in a list is not EZvac's list. */
    static final int MOST = 32;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,39}");
    private static final Pattern CLASS = Pattern.compile("[A-Za-z_][A-Za-z0-9_.$]{0,119}");
    private static final Pattern ICON = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
    private static final Pattern MINECRAFT = Pattern.compile("[0-9]+(?:[.][0-9]+){0,3}");

    /**
     * One official addon.
     *
     * @param id          its key: lower case, as in {@code folded-into}
     * @param plugin      the plugin's name on a server
     * @param mainClass   the plugin's main class; both must match for it to be recognised
     * @param name        as shown: {@code EZdebug+}
     * @param description what it does, a sentence or two
     * @param repository  its repository in the EZvac-Plugin organisation
     * @param minecraft   the oldest Minecraft it runs on, or empty for any EZvac runs on
     * @param needs       what else it needs, as shown; may be empty
     * @param foldedInto  the id of the addon that took it in, or empty
     * @param icon        a Material's name for the menu
     */
    public record Entry(String id, String plugin, String mainClass, String name, String description,
                        String repository, String minecraft, String needs, String foldedInto, String icon) {
        /** Its page on GitHub. */
        public String url() { return ORGANISATION + repository; }

        /** Its releases, as GitHub's API lists them. */
        public String releasesApi() {
            return "https://api.github.com/repos/EZvac-Plugin/" + repository + "/releases?per_page=30";
        }

        public boolean folded() { return !foldedInto.isEmpty(); }
    }

    private AddonCatalogue() {}

    /**
     * The addons in a list file. Entries that don't read cleanly are left
     * out, as is a second entry with an id or plugin name already seen; a
     * file that isn't a list at all gives none.
     */
    public static List<Entry> parse(String json) {
        List<Entry> entries = new ArrayList<>();
        JsonElement root;
        try {
            root = JsonParser.parseString(json == null ? "" : json);
        } catch (JsonParseException unreadable) {
            return entries;
        }
        if (root == null || !root.isJsonObject()) return entries;
        JsonElement list = root.getAsJsonObject().get("addons");
        if (list == null || !list.isJsonArray()) return entries;
        JsonArray addons = list.getAsJsonArray();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement element : addons) {
            if (entries.size() >= MOST) break;
            if (!element.isJsonObject()) continue;
            Entry entry = entry(element.getAsJsonObject());
            if (entry == null || !seen.add("id " + entry.id()) || !seen.add("plugin " + entry.plugin())) continue;
            entries.add(entry);
        }
        return entries;
    }

    private static Entry entry(JsonObject object) {
        String id = text(object, "id"), plugin = text(object, "plugin"), main = text(object, "main");
        String repository = text(object, "repository");
        if (!NAME.matcher(id).matches() || !id.equals(id.toLowerCase(java.util.Locale.ROOT))) return null;
        if (!NAME.matcher(plugin).matches() || !NAME.matcher(repository).matches()) return null;
        if (repository.equals(".") || repository.equals("..") || !CLASS.matcher(main).matches()) return null;
        String name = clean(text(object, "name"), 40);
        if (name.isEmpty()) name = plugin;
        String minecraft = text(object, "minecraft");
        String folded = text(object, "folded-into");
        String icon = text(object, "icon");
        return new Entry(id, plugin, main, name, clean(text(object, "description"), DESCRIPTION_MOST), repository,
                MINECRAFT.matcher(minecraft).matches() ? minecraft : "", clean(text(object, "needs"), 200),
                NAME.matcher(folded).matches() ? folded : "", ICON.matcher(icon).matches() ? icon : "PAPER");
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString().trim() : "";
    }

    /** Text safe to show: no colour codes or control characters, and no longer than {@code most}. */
    /**
     * The longest description shown, in characters: well past any real one, so
     * nothing is cut short, but a broken or hostile one can't fill the menu.
     */
    public static final int DESCRIPTION_MOST = 600;

    /**
     * Text safe to show: no colour codes or control characters, and at most
     * {@code most} characters. Longer text is cut at a space and ends with an
     * ellipsis, never in the middle of a word.
     */
    static String clean(String text, int most) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '§') {
                i++;   // a colour code: the sign and the letter after it
                continue;
            }
            if (character < 32 || character == 127) continue;
            out.append(character);
        }
        String cleaned = out.toString().trim();
        if (cleaned.length() <= most) return cleaned;
        int space = cleaned.lastIndexOf(' ', most - 1);
        return (space > most / 2 ? cleaned.substring(0, space) : cleaned.substring(0, most - 1)).trim() + "…";
    }

    /** A sentence as lines of a menu item's lore, broken at spaces. */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
