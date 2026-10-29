package com.github.blade.hvac.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Which GitHub release, if any, a server should hear about (BETA 1.0). A
 * server running a pilot hears about newer pilots and releases; one running a
 * release only about newer releases and their hotfixes.
 */
public final class UpdateCheck {

    public record Release(ReleaseVersion version, String tag, String url, boolean pilot) {}

    private UpdateCheck() {}

    /**
     * GitHub's list of releases, newest first. Drafts and tags that are not
     * EZvac release names are skipped; a GitHub prerelease counts as a pilot.
     */
    public static List<Release> parse(String json) {
        List<Release> releases = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) return releases;
        JsonArray list = root.getAsJsonArray();
        for (JsonElement element : list) {
            if (!element.isJsonObject()) continue;
            JsonObject release = element.getAsJsonObject();
            if (flag(release, "draft")) continue;
            String tag = text(release, "tag_name");
            ReleaseVersion version = ReleaseVersion.parse(tag);
            if (version == null) continue;
            releases.add(new Release(version, tag, text(release, "html_url"),
                    version.isPilot() || flag(release, "prerelease")));
        }
        return releases;
    }

    /** The newest release newer than {@code current} that this server should hear about, or null. */
    public static Release newer(ReleaseVersion current, List<Release> releases) {
        if (current == null) return null;
        Release best = null;
        for (Release release : releases) {
            if (release.version().compareTo(current) <= 0) continue;
            if (release.pilot() && !current.isPilot()) continue;
            if (best == null || release.version().compareTo(best.version()) > 0) best = release;
        }
        return best;
    }

    private static boolean flag(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
