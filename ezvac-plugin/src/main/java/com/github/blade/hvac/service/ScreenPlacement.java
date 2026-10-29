package com.github.blade.hvac.service;

/**
 * Where a screen goes so it sits on the face of its sign (2.1): just
 * in front of the board, centred on it, turned the way the sign faces. Pure:
 * positions are relative to the sign block's corner.
 */
public final class ScreenPlacement {
    private ScreenPlacement() {}

    /** How a sign is put up, which decides where its board is. */
    public enum Mount {
        /** On the side of a block: the board lies against that block. */
        WALL(0.53, -0.36, 0.4),
        /** On a post: the board is in the middle of the block, near its top. */
        STANDING(0.77, 0.08, 0.4),
        /** A hanging sign, on a wall or under a ceiling: a smaller board, low in the block. */
        HANGING(0.31, 0.08, 0.34);

        final double centreY;
        /** How far in front of the block's middle the screen goes, along the way the sign faces. */
        final double depth;
        /** One screen pixel is scale / 40 blocks; a 100-pixel line is then the board's width. */
        final double scale;

        Mount(double centreY, double depth, double scale) {
            this.centreY = centreY;
            this.depth = depth;
            this.scale = scale;
        }

        public double scale() { return scale; }
    }

    /** Pixels in one screen line, at any scale: the board's width. */
    public static final int LINE_WIDTH = 100;

    /** Where the screen's entity stands; a text display grows upward from there. */
    public record Spot(double x, double y, double z, float yaw) {}

    /**
     * @param facingX the way the sign's text faces, as a horizontal direction
     * @param lines   how many lines the screen has, so it stays centred on the board
     */
    public static Spot of(Mount mount, double facingX, double facingZ, int lines) {
        return spot(mount, facingX, facingZ, mount.centreY - height(mount, lines) / 2.0, 0.0);
    }

    /** How far in front of the body the big reading floats, in blocks, so the two never flicker. */
    static final double BIG_LIFT = 0.01;

    /** Where the three parts of a status-strip screen go (BETA 1.0); {@code big} is null without one. */
    public record Layout(Spot header, Spot body, Spot big) {}

    /**
     * The header strip on top of the body, the two centred on the board as
     * one. A big reading, drawn at twice the size, sits over the body's top
     * {@link ScreenText#BIG_LINES} lines, which the body leaves blank.
     */
    public static Layout layout(Mount mount, double facingX, double facingZ, int bodyLines, boolean big) {
        double pixel = mount.scale / 40.0;
        double bodyHeight = height(mount, bodyLines);
        double bottom = mount.centreY - (height(mount, 1) + bodyHeight) / 2.0;
        Spot body = spot(mount, facingX, facingZ, bottom, 0.0);
        Spot header = spot(mount, facingX, facingZ, bottom + bodyHeight, 0.0);
        if (!big) return new Layout(header, body, null);
        // The blank rows run from a pixel under the body's top down 10 pixels a line; the big
        // reading, 12 pixels tall at twice the size, is centred on them.
        double rowsCentre = bottom + bodyHeight - pixel * (1 + 5 * ScreenText.BIG_LINES);
        return new Layout(header, body, spot(mount, facingX, facingZ, rowsCentre - 12 * pixel, BIG_LIFT));
    }

    /** A display's height in blocks: ten pixels a line and a pixel of panel above and below. */
    static double height(Mount mount, int lines) {
        return (lines * 10 + 2) * mount.scale / 40.0;
    }

    private static Spot spot(Mount mount, double facingX, double facingZ, double y, double lift) {
        double length = Math.hypot(facingX, facingZ);
        double fx = length == 0.0 ? 0.0 : facingX / length, fz = length == 0.0 ? 1.0 : facingZ / length;
        // Minecraft's yaw: 0 faces south (+z), 90 west, 180 north, -90 east.
        float yaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
        double depth = mount.depth + lift;
        return new Spot(0.5 + fx * depth, y, 0.5 + fz * depth, yaw);
    }
}
