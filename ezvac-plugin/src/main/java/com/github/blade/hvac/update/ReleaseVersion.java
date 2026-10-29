package com.github.blade.hvac.update;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An EZvac release name, such as ALPHA-3.2, ALPHA-3.2-HOTFIX1 or BETA-1.0-P1.
 * Releases order by stage (every ALPHA before every BETA), then by number;
 * a number's pilots come before it, and its hotfixes after it.
 */
public record ReleaseVersion(Stage stage, int[] numbers, int pilot, int hotfix)
        implements Comparable<ReleaseVersion> {

    public enum Stage {
        ALPHA("A"), BETA("B");

        private final String letter;

        Stage(String letter) { this.letter = letter; }
    }

    /** ALPHA-1.1-HOTFIX, the one hotfix without a number, counts as HOTFIX1. */
    private static final Pattern NAME = Pattern.compile(
            "(ALPHA|BETA)-([0-9]+(?:[.][0-9]+)*)(?:-HOTFIX([0-9]*))?(?:-P([0-9]+))?", Pattern.CASE_INSENSITIVE);

    /** Null when the text is not an EZvac release name, such as a local SNAPSHOT build. */
    public static ReleaseVersion parse(String text) {
        if (text == null) return null;
        Matcher match = NAME.matcher(text.trim());
        if (!match.matches()) return null;
        int[] numbers = Arrays.stream(match.group(2).split("[.]")).mapToInt(Integer::parseInt).toArray();
        int hotfix = match.group(3) == null ? 0 : match.group(3).isEmpty() ? 1 : Integer.parseInt(match.group(3));
        int pilot = match.group(4) == null ? 0 : Integer.parseInt(match.group(4));
        return new ReleaseVersion(Stage.valueOf(match.group(1).toUpperCase(Locale.ROOT)), numbers, pilot, hotfix);
    }

    /** A pilot is a prerelease of its number. */
    public boolean isPilot() { return pilot > 0; }

    /** The short form the HUD shows: ALPHA-1.8-HOTFIX1 is A1.8_H1, BETA-1.0-P1 is B1.0_P1. */
    public String shortName() {
        StringBuilder out = new StringBuilder(stage.letter);
        for (int i = 0; i < numbers.length; i++) out.append(i == 0 ? "" : ".").append(numbers[i]);
        if (hotfix > 0) out.append("_H").append(hotfix);
        if (pilot > 0) out.append("_P").append(pilot);
        return out.toString();
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        int order = stage.compareTo(other.stage);
        if (order != 0) return order;
        for (int i = 0; i < Math.max(numbers.length, other.numbers.length); i++) {
            order = Integer.compare(number(i), other.number(i));
            if (order != 0) return order;
        }
        order = Integer.compare(pilotRank(), other.pilotRank());
        return order != 0 ? order : Integer.compare(hotfix, other.hotfix);
    }

    private int number(int index) { return index < numbers.length ? numbers[index] : 0; }

    /** Pilots in order, then the release itself. */
    private int pilotRank() { return pilot > 0 ? pilot : Integer.MAX_VALUE; }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReleaseVersion version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        int[] trimmed = numbers;
        int length = trimmed.length;
        while (length > 1 && trimmed[length - 1] == 0) length--;
        return 31 * (31 * (31 * stage.hashCode() + Arrays.hashCode(Arrays.copyOf(trimmed, length))) + pilot) + hotfix;
    }

    @Override
    public String toString() {
        return shortName();
    }
}
