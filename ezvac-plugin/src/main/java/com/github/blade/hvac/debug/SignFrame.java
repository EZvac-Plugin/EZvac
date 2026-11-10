package com.github.blade.hvac.debug;

import java.util.ArrayList;
import java.util.List;

/**
 * A dotted frame of coloured specks around a sign's board, in the colour of
 * its kind. Signs can't glow: Minecraft draws their boards with a
 * block-entity renderer that a display entity can't copy. The frame sits just
 * in front of the board and a little wider, so it rings the board and the
 * screen on it. Pure: points are relative to the sign block's corner. (Copied from EZdebug+.)
 */
public final class SignFrame {
    private SignFrame() {}

    /** How a sign is put up, which decides where its board is (EZvac's own screen measurements). */
    public enum Mount {
        /** On the side of a block: the board lies against that block. */
        WALL(1.0, 0.5, 0.53, -0.375),
        /** On a post: the board is in the middle of the block, near its top. */
        STANDING(1.0, 0.5, 0.77, 0.04),
        /** A hanging sign, on a wall or under a ceiling: a smaller board, low in the block. */
        HANGING(0.875, 0.625, 0.31, 0.0625);

        final double width, height, centreY;
        /** Where the board's front face is, along the way the sign faces, from the block's middle. */
        final double front;

        Mount(double width, double height, double centreY, double front) {
            this.width = width;
            this.height = height;
            this.centreY = centreY;
            this.front = front;
        }
    }

    /** The frame is this much wider and taller than the board, so it rings it. */
    static final double MARGIN = 1.1;
    /** And this far in front of the board's face, so the board never hides it. */
    static final double AHEAD = 0.12;
    /** Specks along each side, corners included. */
    static final int PER_SIDE = 5;

    /** One speck. */
    public record Point(double x, double y, double z) {}

    /** @param facingX the way the sign's text faces, as a horizontal direction */
    public static List<Point> of(Mount mount, double facingX, double facingZ) {
        double length = Math.hypot(facingX, facingZ);
        double fx = length == 0.0 ? 0.0 : facingX / length, fz = length == 0.0 ? 1.0 : facingZ / length;
        // Along the board: facing turned a quarter, so the frame lies flat on the board's face.
        double rx = fz, rz = -fx;
        double cx = 0.5 + fx * (mount.front + AHEAD), cz = 0.5 + fz * (mount.front + AHEAD);
        double halfWidth = mount.width * MARGIN / 2.0, halfHeight = mount.height * MARGIN / 2.0;
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < PER_SIDE; i++) {
            double t = -1.0 + 2.0 * i / (PER_SIDE - 1);
            double along = t * halfWidth;
            points.add(at(cx, cz, rx, rz, along, mount.centreY + halfHeight));
            points.add(at(cx, cz, rx, rz, along, mount.centreY - halfHeight));
            if (i == 0 || i == PER_SIDE - 1) continue;
            double up = t * halfHeight;
            points.add(at(cx, cz, rx, rz, -halfWidth, mount.centreY + up));
            points.add(at(cx, cz, rx, rz, halfWidth, mount.centreY + up));
        }
        return points;
    }

    private static Point at(double cx, double cz, double rx, double rz, double along, double y) {
        return new Point(cx + rx * along, y, cz + rz * along);
    }
}
