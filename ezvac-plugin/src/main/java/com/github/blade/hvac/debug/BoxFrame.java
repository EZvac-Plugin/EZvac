package com.github.blade.hvac.debug;

import java.util.ArrayList;
import java.util.List;

/**
 * A dotted box of specks along a block's twelve edges, a little outside it:
 * how equipment in the selected system is marked for the player linking it,
 * on top of its glowing outline, which every debug viewer shares. Pure:
 * points are relative to the block's corner.
 */
public final class BoxFrame {
    /** Specks along each edge, corners included. */
    public static final int PER_EDGE = 5;
    /** How far outside the block the box sits. */
    public static final double OUTSET = 0.06;

    private BoxFrame() {}

    public record Point(double x, double y, double z) {}

    public static List<Point> points() {
        double low = -OUTSET;
        double high = 1.0 + OUTSET;
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < PER_EDGE; i++) {
            double t = low + (high - low) * i / (PER_EDGE - 1);
            for (double a : new double[]{low, high}) {
                for (double b : new double[]{low, high}) {
                    points.add(new Point(t, a, b));
                    // The corners are on the x edges already; the other edges add their middles only.
                    if (i > 0 && i < PER_EDGE - 1) {
                        points.add(new Point(a, t, b));
                        points.add(new Point(a, b, t));
                    }
                }
            }
        }
        return points;
    }
}
