package com.github.blade.hvac.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * What each part of EZvac costs the server's tick (BETA 1.0 Pilot 4), for
 * the Health page of quick settings and {@code /hvac stats}: the time each
 * part's work took, averaged over every tick of the last minute, and its
 * longest single run in that minute. Main thread only.
 */
public final class TickCost {
    /** A minute of server ticks. */
    static final int WINDOW_TICKS = 1_200;
    /** A window younger than this says too little to show. */
    static final int LEAST_TICKS = 100;

    /**
     * @param msPerTick the part's time spread over every tick of the window
     * @param worstMs   its longest single run in the window
     */
    public record Part(String name, double msPerTick, double worstMs) {}

    private final IntSupplier serverTick;
    /** Per part: the nanoseconds spent in this window, and the longest run. */
    private final Map<String, long[]> parts = new LinkedHashMap<>();
    private int windowStart;
    private List<Part> last = List.of();

    /** @param serverTick the server's tick counter */
    public TickCost(IntSupplier serverTick) {
        this.serverTick = serverTick;
        this.windowStart = serverTick.getAsInt();
    }

    /** The same work, timed under a part's name. */
    public Runnable timed(String part, Runnable work) {
        parts.computeIfAbsent(part, ignored -> new long[2]);
        return () -> {
            long start = System.nanoTime();
            try {
                work.run();
            } finally {
                add(part, System.nanoTime() - start);
            }
        };
    }

    /** Counts one run of a part that took this long. */
    public void add(String part, long nanos) {
        roll();
        long[] cell = parts.computeIfAbsent(part, ignored -> new long[2]);
        cell[0] += Math.max(0L, nanos);
        if (nanos > cell[1]) cell[1] = nanos;
    }

    private void roll() {
        int now = serverTick.getAsInt();
        if (now - windowStart < WINDOW_TICKS) return;
        last = measure(now - windowStart);
        for (long[] cell : parts.values()) cell[0] = cell[1] = 0L;
        windowStart = now;
    }

    private List<Part> measure(int ticks) {
        List<Part> measured = new ArrayList<>();
        for (Map.Entry<String, long[]> part : parts.entrySet())
            measured.add(new Part(part.getKey(), part.getValue()[0] / 1e6 / Math.max(1, ticks),
                    part.getValue()[1] / 1e6));
        return measured;
    }

    /**
     * Each part's cost over the last whole minute; in the first minute after
     * start-up, over what has run so far, and empty before there is enough.
     */
    public List<Part> lastMinute() {
        roll();
        if (!last.isEmpty()) return last;
        int ticks = serverTick.getAsInt() - windowStart;
        return ticks < LEAST_TICKS ? List.of() : measure(ticks);
    }

    /** Every part together, in milliseconds per tick. */
    public static double total(List<Part> parts) {
        double total = 0.0;
        for (Part part : parts) total += part.msPerTick();
        return total;
    }
}
