package com.github.blade.hvac.service;

import com.github.blade.hvac.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The reworked RPM monitor (3.0): its rows, their text, and its pages. */
class RpmMonitorRowsTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");

    private static EquipmentUnit unit(int x, EquipmentType type, boolean running, double rpm) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("worldUuid", WORLD.toString());
        map.put("x", x); map.put("y", 64); map.put("z", 0);
        map.put("system", "house");
        map.put("equipmentType", type.name());
        map.put("running", running);
        map.put("operatingMode", running ? "COOLING" : "IDLE");
        map.put("motorRpm", rpm);
        return EquipmentUnit.fromMap(map);
    }

    @Test
    void oneRowPerKindInTheOrderKindsAreListed() {
        List<RpmMonitor.Row> rows = RpmMonitor.rows(List.of(
                unit(0, EquipmentType.FURNACE, false, 0),
                unit(1, EquipmentType.HEAT_PUMP, true, 3000),
                unit(2, EquipmentType.HEAT_PUMP, true, 2000),
                unit(3, EquipmentType.HEAT_PUMP, false, 0)));
        assertEquals(List.of(new RpmMonitor.Row(EquipmentType.HEAT_PUMP, 3, 2, 2500),
                new RpmMonitor.Row(EquipmentType.FURNACE, 1, 0, 0)), rows, "the average is of the running units");
        assertTrue(RpmMonitor.rows(List.of()).isEmpty());
    }

    @Test
    void aRowShowsTheKindTheCountAndTheSpeed() {
        assertEquals("Heat pump ×4 2,950",
                DisplayService.rowText(new RpmMonitor.Row(EquipmentType.HEAT_PUMP, 4, 2, 2950), 200));
        assertEquals("Furnace off", DisplayService.rowText(new RpmMonitor.Row(EquipmentType.FURNACE, 1, 0, 0), 200),
                "no ×1 for a single unit");
        String narrow = DisplayService.rowText(new RpmMonitor.Row(EquipmentType.POOL_HEAT_PUMP, 2, 1, 3100),
                SignText.SIGN_WIDTH);
        assertEquals("Pool HP ×2 3,100", narrow, "the kind is shortened before anything else");
        assertTrue(SignText.width(narrow) <= SignText.SIGN_WIDTH);
    }

    @Test
    void pagesTurnByClickAndByAutoFlip() {
        RpmMonitor monitor = new RpmMonitor(new BlockKey(WORLD, 0, 64, 0), "Fans");
        assertEquals(1, RpmMonitor.pages(0), "an empty monitor still has a page");
        assertEquals(2, RpmMonitor.pages(4));
        List<String> rows = List.of("a", "b", "c", "d");
        assertEquals(List.of("a", "b", "c"), monitor.onPage(rows));
        monitor.nextPage(rows.size());
        assertEquals(List.of("d"), monitor.onPage(rows));
        monitor.nextPage(rows.size());
        assertEquals(0, monitor.page(rows.size()), "it wraps around");

        assertFalse(monitor.advanceAutoFlip(Pager.FLIP_TICKS, rows.size()), "auto-flip is off by default");
        monitor.setAutoFlip(true);
        assertFalse(monitor.advanceAutoFlip(Pager.FLIP_TICKS - 1, rows.size()));
        assertTrue(monitor.advanceAutoFlip(1, rows.size()));
        assertEquals(1, monitor.page(rows.size()));
        assertFalse(monitor.advanceAutoFlip(Pager.FLIP_TICKS, 3), "one page never flips");
        assertEquals(0, monitor.page(2), "a page past the end is read as the last");
    }
}
