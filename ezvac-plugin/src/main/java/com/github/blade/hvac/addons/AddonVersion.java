package com.github.blade.hvac.addons;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An addon's version, as its plugin and its GitHub release tags give it
 * (BETA 1.0 Pilot 4): {@code 0.2.1}, {@code v0.3.0}, {@code 0.1.0-SNAPSHOT}.
 * Only the numbers count; a build's suffix doesn't make it older or newer.
 * Pure.
 */
public record AddonVersion(int[] numbers) implements Comparable<AddonVersion> {
    private static final Pattern NUMBERS = Pattern.compile("[vV]?([0-9]{1,6}(?:[.][0-9]{1,6}){0,5})(?:[-+ ._].*)?");

    /** A release found on GitHub. */
    public record Release(String tag, AddonVersion version, boolean prerelease) {}

    /** The version a text gives, or null when it starts with none. */
    public static AddonVersion parse(String text) {
        if (text == null) return null;
        Matcher match = NUMBERS.matcher(text.trim());
        if (!match.matches()) return null;
        String[] parts = match.group(1).split("[.]");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) numbers[i] = Integer.parseInt(parts[i]);
        return new AddonVersion(numbers);
    }

    @Override
    public int compareTo(AddonVersion other) {
        for (int i = 0; i < Math.max(numbers.length, other.numbers.length); i++) {
            int order = Integer.compare(i < numbers.length ? numbers[i] : 0,
                    i < other.numbers.length ? other.numbers[i] : 0);
            if (order != 0) return order;
        }
        return 0;
    }

    /**
     * The newest release in GitHub's list that is newer than
     * {@code running}, or null. Drafts never count, and prereleases only
     * where the server follows pilots.
     */
    public static Release newer(String releasesJson, AddonVersion running, boolean prereleases) {
        if (running == null) return null;
        JsonElement root;
        try {
            root = JsonParser.parseString(releasesJson == null ? "" : releasesJson);
        } catch (JsonParseException unreadable) {
            return null;
        }
        if (root == null || !root.isJsonArray()) return null;
        Release best = null;
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject release = element.getAsJsonObject();
            if (flag(release, "draft")) continue;
            boolean prerelease = flag(release, "prerelease");
            if (prerelease && !prereleases) continue;
            JsonElement tag = release.get("tag_name");
            if (tag == null || !tag.isJsonPrimitive()) continue;
            AddonVersion version = parse(tag.getAsString());
            if (version == null || version.compareTo(running) <= 0) continue;
            if (best == null || version.compareTo(best.version()) > 0)
                best = new Release(AddonCatalogue.clean(tag.getAsString(), 24), version, prerelease);
        }
        return best;
    }

    private static boolean flag(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AddonVersion version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        int last = numbers.length;
        while (last > 0 && numbers[last - 1] == 0) last--;
        for (int i = 0; i < last; i++) hash = hash * 31 + numbers[i];
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int number : numbers) text.append(text.length() == 0 ? "" : ".").append(number);
        return text.toString();
    }
}
