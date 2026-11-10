package com.github.blade.hvac.service;

/**
 * Measures text the way the client draws it, and fits it to a screen's line.
 * A line holds a fixed number of pixels, not characters, and the client wraps
 * a wider one. Widths follow the vanilla default font; any character it
 * doesn't list is counted wide, so a line measured here never wraps. Pure.
 * (SignText until BETA 1.0 Pilot 4, when devices stopped writing on signs.)
 */
public final class TextWidth {
    /** Counted for anything else outside printable ASCII: as wide as the client's fallback (unifont) glyphs. */
    private static final int UNKNOWN_WIDTH = 9;
    private static final int[] ASCII = new int[128];
    /**
     * The symbols EZvac writes itself, at their width in the client's font
     * (BETA 1.0, measured from its glyph sheets): counted as unknown before,
     * a degree sign took 9 pixels for its 5, so screen lines weren't measured.
     */
    private static final java.util.Map<Character, Integer> SYMBOLS = java.util.Map.ofEntries(
            java.util.Map.entry('°', 5), java.util.Map.entry('·', 2), java.util.Map.entry('×', 6),
            java.util.Map.entry('★', 8), java.util.Map.entry('☆', 8), java.util.Map.entry('❄', 8),
            java.util.Map.entry('●', 5), java.util.Map.entry('○', 6), java.util.Map.entry('◆', 6),
            java.util.Map.entry('⚠', 10));

    static {
        java.util.Arrays.fill(ASCII, 6);
        set(2, "!,.:;i|");
        set(3, "'`l");
        set(4, " It[]");
        set(5, "\"()*<>fk{}");
        set(7, "@~");
    }

    private TextWidth() {}

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
        return character >= 32 && character < 127 ? ASCII[character] : SYMBOLS.getOrDefault(character, UNKNOWN_WIDTH);
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
}
