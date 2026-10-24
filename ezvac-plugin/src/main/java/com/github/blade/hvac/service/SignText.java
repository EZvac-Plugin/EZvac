package com.github.blade.hvac.service;

/**
 * Fits text to a sign line the way the client draws it. A sign line holds a
 * fixed number of pixels, not characters: the client wraps a wider line at a
 * space and shows only the first part, so {@code EZvac | Dehumidifier}
 * appeared as {@code EZvac |}. Widths follow the vanilla default font; any
 * character it doesn't list is counted wide, so a line measured here never
 * wraps. Pure.
 */
public final class SignText {
    /** Pixels in a line of a standing or wall sign. */
    public static final int SIGN_WIDTH = 90;
    /** Pixels in a line of a hanging sign. */
    public static final int HANGING_SIGN_WIDTH = 60;
    /** The prefix headers carry when there is room for it. */
    static final String PREFIX = "EZvac | ";

    /** Counted for anything outside printable ASCII: wider than any glyph a label is likely to use. */
    private static final int UNKNOWN_WIDTH = 9;
    private static final int[] ASCII = new int[128];

    static {
        java.util.Arrays.fill(ASCII, 6);
        set(2, "!,.:;i|");
        set(3, "'`l");
        set(4, " It[]");
        set(5, "\"()*<>fk{}");
        set(7, "@~");
    }

    private SignText() {}

    private static void set(int width, String characters) {
        for (char character : characters.toCharArray()) ASCII[character] = width;
    }

    /** The pixels a line takes, the 1 px gap after each glyph included. */
    public static int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) width += width(text.charAt(i));
        return width;
    }

    private static int width(char character) {
        return character >= 32 && character < 127 ? ASCII[character] : UNKNOWN_WIDTH;
    }

    /**
     * The longest start of the text that fits in the given pixels. A reading
     * is never shown cut short: when the cut falls inside one ({@code 78%},
     * {@code 61.2}, {@code -40F}, {@code 12:34}) the whole reading is left off
     * instead, so {@code Out 61.2 F 78%} becomes {@code Out 61.2 F}, not
     * {@code Out 61.2 F 7}. Labels are cut wherever the room ends, digits or
     * not, and a separator left dangling at the end ({@code ETA 0:31 |}) goes too.
     */
    public static String fit(String text, int maxWidth) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += width(text.charAt(i));
            if (width > maxWidth) return cutAt(text, i);
        }
        return text;
    }

    private static String cutAt(String text, int end) {
        int start = text.lastIndexOf(' ', end - 1) + 1;
        boolean insideReading = text.charAt(end) != ' ' && start > 0 && isReading(text, start);
        return withoutDanglingSeparator(text.substring(0, insideReading ? start : end));
    }

    /** A word that is a number: it starts with a digit, or a sign and then a digit. */
    private static boolean isReading(String text, int start) {
        char first = text.charAt(start);
        if (Character.isDigit(first)) return true;
        return (first == '-' || first == '+') && start + 1 < text.length() && Character.isDigit(text.charAt(start + 1));
    }

    private static String withoutDanglingSeparator(String cut) {
        String trimmed = cut.stripTrailing();
        while (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
        return trimmed;
    }

    /** {@code EZvac | label} when that fits, otherwise the label alone, cut to fit. */
    public static String titled(String label, int maxWidth) {
        String titled = PREFIX + label;
        return width(titled) <= maxWidth ? titled : fit(label, maxWidth);
    }
}
