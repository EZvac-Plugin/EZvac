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

    /**
     * Latin-1, Latin Extended-A, Greek and Cyrillic letters (BETA 1.1, for
     * translations), at their width in the vanilla default font, measured
     * from its glyph sheets. Anything else stays counted wide.
     */
    private static final java.util.Map<Character, Integer> LETTERS = new java.util.HashMap<>();

    static {
        java.util.Arrays.fill(ASCII, 6);
        set(2, "!,.:;i|");
        set(3, "'`l");
        set(4, " It[]");
        set(5, "\"()*<>fk{}");
        set(7, "@~");
        letters(2, "\u00a1\u00a6\u00b7\u0131\u037e\u0387\u0456");
        letters(3, "\u00b4\u00ec\u00ed\u012f\u013a\u013c\u03af\u03b9");
        letters(4, "\u00a8\u00ad\u00b9\u00cc\u00cd\u00ce\u00cf\u00ee\u00ef\u012a\u012b\u012e"
                + "\u0130\u013e\u0140\u0163\u0167\u017f\u0399\u03aa\u03ca\u0406\u0407\u0457"
                + "\u04c0");
        letters(5, "\u00aa\u00b0\u00b2\u00b3\u00ba\u0128\u0129\u012c\u012d\u0133\u0137\u0138"
                + "\u0142\u0165\u03ba\u0433\u043a\u0453\u045c\u0495\u04c4\u04f7");
        letters(6, "\u00a2\u00a3\u00a5\u00a7\u00ac\u00af\u00b1\u00b5\u00bf\u00c0\u00c1\u00c2"
                + "\u00c3\u00c4\u00c5\u00c7\u00c8\u00c9\u00ca\u00cb\u00d1\u00d2\u00d3\u00d4"
                + "\u00d5\u00d6\u00d7\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00de\u00df\u00e0"
                + "\u00e1\u00e2\u00e3\u00e4\u00e5\u00e7\u00e8\u00e9\u00ea\u00eb\u00f0\u00f1"
                + "\u00f2\u00f3\u00f4\u00f5\u00f6\u00f7\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd"
                + "\u00fe\u00ff\u0100\u0101\u0102\u0103\u0104\u0105\u0106\u0107\u0108\u0109"
                + "\u010a\u010b\u010c\u010d\u010e\u0112\u0113\u0114\u0115\u0116\u0117\u0118"
                + "\u0119\u011a\u011b\u011c\u011d\u011e\u011f\u0120\u0121\u0122\u0123\u0124"
                + "\u0125\u0132\u0134\u0135\u0136\u0139\u013b\u013d\u013f\u0143\u0144\u0145"
                + "\u0146\u0147\u0148\u014a\u014b\u014c\u014d\u014e\u014f\u0150\u0151\u0154"
                + "\u0155\u0156\u0157\u0158\u0159\u015a\u015b\u015c\u015d\u015e\u015f\u0160"
                + "\u0161\u0162\u0164\u0166\u0168\u0169\u016a\u016b\u016c\u016d\u016e\u016f"
                + "\u0170\u0171\u0172\u0173\u0174\u0175\u0176\u0177\u0178\u0179\u017a\u017b"
                + "\u017c\u017d\u017e\u038a\u0390\u0391\u0392\u0393\u0394\u0395\u0396\u0397"
                + "\u0398\u039a\u039b\u039c\u039d\u039e\u039f\u03a0\u03a1\u03a3\u03a4\u03a5"
                + "\u03a6\u03a7\u03a8\u03a9\u03ab\u03ac\u03ad\u03ae\u03b0\u03b1\u03b2\u03b3"
                + "\u03b4\u03b5\u03b6\u03b7\u03b8\u03bb\u03bc\u03bd\u03be\u03bf\u03c0\u03c1"
                + "\u03c2\u03c4\u03c5\u03c6\u03c7\u03c8\u03cb\u03cc\u03cd\u03db\u0400\u0401"
                + "\u0403\u0404\u0405\u0408\u040c\u040d\u040e\u040f\u0410\u0411\u0412\u0413"
                + "\u0415\u0417\u0418\u0419\u041a\u041b\u041c\u041d\u041e\u041f\u0420\u0421"
                + "\u0422\u0423\u0425\u0427\u042c\u042d\u042f\u0430\u0431\u0432\u0435\u0436"
                + "\u0437\u0438\u0439\u043b\u043c\u043d\u043e\u043f\u0440\u0441\u0442\u0443"
                + "\u0444\u0445\u0446\u0447\u0448\u044c\u044d\u044f\u0450\u0451\u0454\u0455"
                + "\u0458\u045d\u045e\u045f\u0472\u0473\u048e\u048f\u0490\u0491\u0493\u0494"
                + "\u0498\u0499\u049b\u049c\u049d\u049f\u04a1\u04aa\u04ab\u04ac\u04ad\u04ae"
                + "\u04af\u04b0\u04b1\u04b8\u04b9\u04ba\u04bb\u04c2\u04c3\u04c7\u04c8\u04cb"
                + "\u04cc\u04d0\u04d1\u04d2\u04d3\u04d6\u04d7\u04d8\u04d9\u04da\u04db\u04dd"
                + "\u04de\u04df\u04e0\u04e1\u04e2\u04e3\u04e4\u04e5\u04e6\u04e7\u04e8\u04e9"
                + "\u04ea\u04eb\u04ec\u04ed\u04ee\u04ef\u04f0\u04f1\u04f2\u04f3\u04f4\u04f5"
                + "\u04f6\u04fb\u04fc\u04fd\u04fe\u04ff");
        letters(7, "\u00ab\u00b6\u00bb\u00d0\u0110\u0111\u0127\u0141\u03c3\u03d4\u040a\u040b"
                + "\u0414\u0426\u042a\u0434\u0449\u044a\u044b\u0452\u045a\u045b\u0462\u0463"
                + "\u0474\u0475\u0476\u0477\u048a\u048b\u048c\u048d\u0492\u0497\u049a\u049e"
                + "\u04a0\u04a2\u04a3\u04a4\u04a5\u04a8\u04a9\u04b2\u04b3\u04b5\u04b6\u04b7"
                + "\u04c5\u04c6\u04c9\u04ca\u04cd\u04ce\u04f9\u04fa");
        letters(8, "\u00a4\u00a9\u00ae\u00bc\u00bd\u00be\u010f\u0126\u0149\u0386\u0388\u0389"
                + "\u038c\u038e\u038f\u03c9\u03ce\u0402\u0409\u0416\u0424\u0428\u042b\u042e"
                + "\u044e\u0459\u04b4\u04bc\u04bd\u04be\u04bf\u04c1\u04dc\u04f8");
        letters(9, "\u0429\u0496\u04a6\u04a7");
        letters(10, "\u00c6\u00e6\u0152\u0153\u03d3\u04d4\u04d5");
    }

    private TextWidth() {}

    private static void letters(int width, String characters) {
        for (char character : characters.toCharArray()) LETTERS.put(character, width);
    }

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
        return character >= 32 && character < 127 ? ASCII[character]
                : SYMBOLS.getOrDefault(character, LETTERS.getOrDefault(character, UNKNOWN_WIDTH));
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
