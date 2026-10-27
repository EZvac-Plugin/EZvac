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
        return unit(x, type, running, rpm, Tier.TWO_STAGE);
    }

    private static EquipmentUnit unit(int x, EquipmentType type, boolean running, double rpm, Tier tier) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("worldUuid", WORLD.toString());
        map.put("x", x); map.put("y", 64); map.put("z", 0);
        map.put("system", "house");
        map.put("equipmentType", type.name());
        map.put("running", running);
        map.put("operatingMode", running ? "COOLING" : "IDLE");
        map.put("motorRpm", rpm);
        map.put("tier", tier.number());
        return EquipmentUnit.fromMap(map);
    }

    @Test
    void oneRowPerKindAndTierInTheOrderKindsAreListedBestTierFirst() {
        List<RpmMonitor.Row> rows = RpmMonitor.rows(List.of(
                unit(0, EquipmentType.FURNACE, false, 0, Tier.SINGLE_STAGE),
                unit(1, EquipmentType.HEAT_PUMP, true, 3000),
                unit(2, EquipmentType.HEAT_PUMP, true, 2000),
                unit(3, EquipmentType.HEAT_PUMP, false, 0),
                unit(4, EquipmentType.HEAT_PUMP, true, 3500, Tier.VARIABLE)));
        assertEquals(List.of(new RpmMonitor.Row(EquipmentType.HEAT_PUMP, Tier.VARIABLE, 1, 1, 3500),
                new RpmMonitor.Row(EquipmentType.HEAT_PUMP, Tier.TWO_STAGE, 3, 2, 2500),
                new RpmMonitor.Row(EquipmentType.FURNACE, Tier.SINGLE_STAGE, 1, 0, 0)), rows,
                "the average is of the running units");
        assertTrue(RpmMonitor.rows(List.of()).isEmpty());
    }

    private static String row(EquipmentType type, Tier tier, int units, int running, double rpm, int width) {
        return DisplayService.rowText(new RpmMonitor.Row(type, tier, units, running, rpm), width, "★", "☆");
    }

    @Test
    void aRowShowsTheKindItsStarsTheCountAndTheSpeed() {
        assertEquals("Heat pump ★★★ ×4 2,950", row(EquipmentType.HEAT_PUMP, Tier.VARIABLE, 4, 2, 2950, 300));
        assertEquals("Furnace ★☆☆ off", row(EquipmentType.FURNACE, Tier.SINGLE_STAGE, 1, 0, 0, 300),
                "no ×1 for a single unit");
        assertEquals("Furnace ★★★ ×2 on", row(EquipmentType.FURNACE, Tier.VARIABLE, 2, 1, 715, 300),
                "a furnace's blower speed says nothing, so it is on or off");
        for (EquipmentType type : EquipmentType.values())
            for (Tier tier : Tier.values())
                for (int units = 1; units <= 9; units++) {
                    String text = row(type, tier, units, 1, 4_150, SignText.SIGN_WIDTH);
                    assertTrue(SignText.width(text) <= SignText.SIGN_WIDTH, text);
                    assertTrue(text.contains(tier.stars("★", "☆")), "the stars always fit: " + text);
                    assertTrue(text.endsWith(type.fixedSpeed() ? " on" : "4.2k")
                            || text.endsWith("4,150"), "and so does the speed: " + text);
                }
        assertEquals("HP ★★☆ ×4 2.9k", row(EquipmentType.HEAT_PUMP, Tier.TWO_STAGE, 4, 2, 2940, SignText.SIGN_WIDTH),
                "the kind is shortened first, then the speed");
        assertEquals("HP [*][*][ ] off", DisplayService.rowText(new RpmMonitor.Row(EquipmentType.HEAT_PUMP,
                Tier.TWO_STAGE, 1, 0, 0), SignText.SIGN_WIDTH, "[*]", "[ ]"), "a server's own glyphs");
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
