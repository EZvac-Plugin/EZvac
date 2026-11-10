package com.github.blade.hvac.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What each part of EZvac costs the tick (BETA 1.0 Pilot 4). */
class TickCostTest {
    private int tick;

    @Test
    void costIsSpreadOverEveryTickOfTheMinute() {
        TickCost cost = new TickCost(() -> tick);
        assertTrue(cost.lastMinute().isEmpty(), "nothing to show at start-up");
        // The controller runs once a second and takes 2 ms; airflow every tick, 0.1 ms.
        for (tick = 0; tick < TickCost.WINDOW_TICKS; tick++) {
            if (tick % 20 == 0) cost.add("Controller", tick == 40 ? 9_000_000L : 2_000_000L);
            cost.add("Airflow", 100_000L);
            if (tick == 50) assertTrue(cost.lastMinute().isEmpty(), "too little to say yet");
            if (tick == 600) {
                List<TickCost.Part> early = cost.lastMinute();
                assertEquals(0.1, early.get(1).msPerTick(), 0.01, "the first minute shows what has run so far");
            }
        }
        cost.add("Airflow", 100_000L);   // the first run of the next minute closes the last
        List<TickCost.Part> parts = cost.lastMinute();
        assertEquals(List.of("Controller", "Airflow"), parts.stream().map(TickCost.Part::name).toList());
        assertEquals((59 * 2.0 + 9.0) / 1200, parts.get(0).msPerTick(), 1e-9, "2 ms once a second is 0.1 ms a tick");
        assertEquals(9.0, parts.get(0).worstMs(), 1e-9, "the longest single run");
        assertEquals(0.1, parts.get(1).msPerTick(), 1e-9);
        assertEquals(parts.get(0).msPerTick() + 0.1, TickCost.total(parts), 1e-9);
        // The next minute starts afresh, and the last one is shown until it is over.
        tick += 100;
        assertEquals(parts, cost.lastMinute());
    }

    @Test
    void timedWorkIsCountedEvenWhenItFails() {
        TickCost cost = new TickCost(() -> tick);
        Runnable fine = cost.timed("HUD", () -> { });
        Runnable failing = cost.timed("Stations", () -> { throw new IllegalStateException("boom"); });
        tick = 200;
        fine.run();
        assertThrows(IllegalStateException.class, failing::run);
        List<TickCost.Part> parts = cost.lastMinute();
        assertEquals(List.of("HUD", "Stations"), parts.stream().map(TickCost.Part::name).toList());
        assertTrue(parts.get(1).worstMs() >= 0.0);
    }
}
