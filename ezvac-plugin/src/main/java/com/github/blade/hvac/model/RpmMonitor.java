package com.github.blade.hvac.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A sign that shows a system's equipment: one row per kind, units of the
 * same kind together, three rows to a page like a sensor hub. It is created
 * on its own and linked to a system with the link tool; until then, or once
 * unlinked, it has no system.
 */
public final class RpmMonitor {
    private final BlockKey position;
    private SystemId system;
    private String label;
    private final Pager pager = new Pager();

    /** A monitor not linked to any system yet. */
    public RpmMonitor(BlockKey position, String label) {
        this(position, null, label);
    }

    public RpmMonitor(BlockKey position, SystemId system, String label) {
        this.position = Objects.requireNonNull(position, "position");
        this.label = Thermostat.validateId(label);
        setSystem(system);
    }

    /**
     * One row: every unit of a kind and tier in the system.
     *
     * @param rpm average speed of the running units; 0 when none runs
     */
    public record Row(EquipmentType type, Tier tier, int units, int running, double rpm) {}

    public BlockKey position() { return position; }
    /** The system it shows, or null while it is not linked. */
    public SystemId system() { return system; }
    public String label() { return label; }
    public boolean autoFlip() { return pager.autoFlip(); }

    public void setLabel(String label) { this.label = Thermostat.validateId(label); }
    public void setAutoFlip(boolean autoFlip) { pager.setAutoFlip(autoFlip); }

    /** Only HvacRegistry may relink a registered monitor. Null unlinks. */
    public void setSystem(SystemId system) {
        if (system != null && !position.worldId().equals(system.worldId()))
            throw new IllegalArgumentException("RPM monitor and system worlds differ");
        this.system = system;
    }

    /** The rows for a system's equipment: by kind as kinds are listed, then by tier, best first. */
    public static List<Row> rows(Collection<EquipmentUnit> equipment) {
        List<Row> rows = new ArrayList<>();
        Tier[] tiers = Tier.values();
        for (EquipmentType type : EquipmentType.values()) {
            for (int index = tiers.length - 1; index >= 0; index--) {
                Tier tier = tiers[index];
                int units = 0;
                int running = 0;
                double rpm = 0.0;
                for (EquipmentUnit unit : equipment) {
                    if (unit.type() != type || unit.tier() != tier) continue;
                    units++;
                    if (!unit.running()) continue;
                    running++;
                    rpm += unit.motorRpm();
                }
                if (units > 0) rows.add(new Row(type, tier, units, running, running == 0 ? 0.0 : rpm / running));
            }
        }
        return rows;
    }

    public int page(int rows) { return pager.page(rows); }
    public static int pages(int rows) { return Pager.pages(rows); }
    public void nextPage(int rows) { pager.next(rows); }
    public <T> List<T> onPage(List<T> rows) { return pager.onPage(rows); }
    public boolean advanceAutoFlip(long elapsedTicks, int rows) { return pager.advance(elapsedTicks, rows); }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        if (system != null) map.put("system", system.key());
        map.put("label", label);
        map.put("autoFlip", autoFlip());
        return map;
    }

    /**
     * Before 3.0 a monitor watched one kind of equipment in a group. It now
     * shows the whole system, so the kind it used to watch is not read.
     */
    public static RpmMonitor fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        String system = text(map.get("system"), text(map.get("group"), null));
        RpmMonitor monitor = new RpmMonitor(position, system == null ? null : SystemId.of(position, system),
                text(map.get("label"), system == null ? "RPM" : system));
        monitor.setAutoFlip(map.get("autoFlip") instanceof Boolean flag && flag);
        return monitor;
    }

    private static String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
