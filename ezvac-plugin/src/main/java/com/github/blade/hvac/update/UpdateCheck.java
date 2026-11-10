package com.github.blade.hvac.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which GitHub release, if any, a server should hear about (BETA 1.0). It
 * follows the server's update channel (BETA 1.0 Pilot 2): on Pilot, newer
 * pilots and releases; on Stable, only newer releases and their hotfixes.
 */
public final class UpdateCheck {

    /** Which releases a server hears about (BETA 1.0 Pilot 2). */
    public enum Channel {
        /** Full releases and their hotfixes. */
        STABLE("Stable"),
        /** Pilots too: the test builds before a full release. */
        PILOT("Pilot");

        private final String displayName;

        Channel(String displayName) { this.displayName = displayName; }

        public String displayName() { return displayName; }

        /** {@code updates.channel}: stable or pilot, or auto (and anything else) for the running build's. */
        public static Channel of(String setting, ReleaseVersion running) {
            String value = setting == null ? "" : setting.trim().toLowerCase(java.util.Locale.ROOT);
            if (value.equals("stable")) return STABLE;
            if (value.equals("pilot")) return PILOT;
            return running != null && running.isPilot() ? PILOT : STABLE;
        }
    }

    public record Release(ReleaseVersion version, String tag, String url, boolean pilot) {}

    /** The Minecraft a release line needs, and the guide to moving a server there. */
    public record Requirement(String minecraft, String guide) {}

    /**
     * What a check found for this server: the newest release it should hear
     * about, and the newest of those that runs on its Minecraft. Both null
     * when it is up to date; they are the same release unless the newest
     * needs a newer Minecraft.
     */
    public record Outcome(Release newest, Release runsHere) {
        public boolean upToDate() { return newest == null; }

        public boolean blocked() { return newest != null && newest != runsHere; }
    }

    /**
     * BETA 2.x drops 1.21.x. A release only knows the cutoffs it was built
     * with, so a new one goes here in the releases before it (as this one is
     * in every BETA 1.x).
     */
    private static final Requirement BETA_2 = new Requirement("26.1",
            "https://github.com/EZvac-Plugin/EZvac/blob/main/docs/upgrading-and-troubleshooting.md#moving-to-minecraft-26x");

    private static final Pattern MINECRAFT = Pattern.compile("([0-9]+(?:[.][0-9]+)*)");

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

    /** The newest release newer than {@code current} on its build's own channel, or null. */
    public static Release newer(ReleaseVersion current, List<Release> releases) {
        return newer(current, releases, Channel.of("auto", current), release -> true);
    }

    /** What a server running {@code current} on Minecraft {@code minecraft} should hear about, on its build's channel. */
    public static Outcome check(ReleaseVersion current, List<Release> releases, String minecraft) {
        return check(current, releases, minecraft, Channel.of("auto", current));
    }

    /** What a server running {@code current} on Minecraft {@code minecraft} should hear about on a channel. */
    public static Outcome check(ReleaseVersion current, List<Release> releases, String minecraft, Channel channel) {
        Release newest = newer(current, releases, channel, release -> true);
        if (newest == null || runsOn(newest.version(), minecraft)) return new Outcome(newest, newest);
        return new Outcome(newest, newer(current, releases, channel, release -> runsOn(release.version(), minecraft)));
    }

    /** What a release needs beyond the 1.21.1 every ALPHA and BETA 1.x runs on, or null. */
    public static Requirement requirement(ReleaseVersion version) {
        boolean beta2 = version.stage() == ReleaseVersion.Stage.BETA && version.numbers()[0] >= 2;
        return beta2 ? BETA_2 : null;
    }

    /** Whether a release runs on that Minecraft. One whose version can't be read is given the benefit. */
    static boolean runsOn(ReleaseVersion version, String minecraft) {
        Requirement requirement = requirement(version);
        return requirement == null || compareMinecraft(minecraft, requirement.minecraft()) >= 0;
    }

    /** Orders Minecraft versions by their numbers (1.21.11 before 26.1); unreadable ones count as newest. */
    public static int compareMinecraft(String a, String b) {
        int[] left = numbers(a), right = numbers(b);
        if (left == null || right == null) return left == right ? 0 : left == null ? 1 : -1;
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int order = Integer.compare(i < left.length ? left[i] : 0, i < right.length ? right[i] : 0);
            if (order != 0) return order;
        }
        return 0;
    }

    private static int[] numbers(String minecraft) {
        if (minecraft == null) return null;
        Matcher match = MINECRAFT.matcher(minecraft.trim());
        if (!match.lookingAt()) return null;
        String[] parts = match.group(1).split("[.]");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        } catch (NumberFormatException tooLong) {
            return null;
        }
        return out;
    }

    private static Release newer(ReleaseVersion current, List<Release> releases, Channel channel,
                                 Predicate<Release> allowed) {
        if (current == null) return null;
        Release best = null;
        for (Release release : releases) {
            if (release.version().compareTo(current) <= 0) continue;
            if (release.pilot() && channel == Channel.STABLE) continue;
            if (!allowed.test(release)) continue;
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
