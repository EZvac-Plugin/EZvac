package com.github.blade.hvac.service;

/**
 * Where a screen goes so it sits on the face of its sign (2.1 Pilot 3): just
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
        double length = Math.hypot(facingX, facingZ);
        double fx = length == 0.0 ? 0.0 : facingX / length, fz = length == 0.0 ? 1.0 : facingZ / length;
        double height = (lines * 10 + 2) * mount.scale / 40.0;
        // Minecraft's yaw: 0 faces south (+z), 90 west, 180 north, -90 east.
        float yaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
        return new Spot(0.5 + fx * mount.depth, mount.centreY - height / 2.0, 0.5 + fz * mount.depth, yaw);
    }
}
