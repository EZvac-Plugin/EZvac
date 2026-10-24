package com.github.blade.hvac.model;

import java.util.*;

/**
 * A sign that shows up to nine remote sensors, three to a page. A click
 * turns the page; auto-flip turns it every few seconds. Which page is
 * showing is never saved: a hub starts on its first page.
 *
 * <p>Pure, so paging and linking can be tested without a server.
 */
public final class SensorHub {
    public static final int MAX_SENSORS = 9;
    public static final int PER_PAGE = 3;
    /** How long an auto-flipping hub stays on each page: 5 seconds. */
    public static final long FLIP_TICKS = 100L;

    public enum LinkResult { LINKED, ALREADY_LINKED, FULL, OTHER_WORLD }

    private final BlockKey position;
    private final String label;
    private final List<BlockKey> sensors = new ArrayList<>();
    private boolean autoFlip;
    private int page;
    private long ticksOnPage;

    public SensorHub(BlockKey position, String label) {
        this.position = Objects.requireNonNull(position, "position");
        this.label = Thermostat.validateId(label);
    }

    public BlockKey position() { return position; }
    public String label() { return label; }
    public List<BlockKey> sensors() { return Collections.unmodifiableList(sensors); }
    public boolean autoFlip() { return autoFlip; }
    /** The page showing, from 0. */
    public int page() { return Math.min(page, pages() - 1); }
    /** At least one, so an empty hub still has a page to show. */
    public int pages() { return Math.max(1, (sensors.size() + PER_PAGE - 1) / PER_PAGE); }

    /** Sensors live in the hub's world; a hub shows them in the order they were linked. */
    public LinkResult link(BlockKey sensor) {
        if (!sensor.worldId().equals(position.worldId())) return LinkResult.OTHER_WORLD;
        if (sensors.contains(sensor)) return LinkResult.ALREADY_LINKED;
        if (sensors.size() >= MAX_SENSORS) return LinkResult.FULL;
        sensors.add(sensor);
        return LinkResult.LINKED;
    }

    public boolean unlink(BlockKey sensor) {
        boolean removed = sensors.remove(sensor);
        if (removed) page = page();
        return removed;
    }

    public void nextPage() {
        page = (page() + 1) % pages();
        ticksOnPage = 0;
    }

    public List<BlockKey> sensorsOnPage() {
        int from = page() * PER_PAGE;
        return sensors.subList(Math.min(from, sensors.size()), Math.min(from + PER_PAGE, sensors.size()));
    }

    public void setAutoFlip(boolean autoFlip) {
        this.autoFlip = autoFlip;
        ticksOnPage = 0;
    }

    /**
     * Advances an auto-flipping hub's clock.
     *
     * @return true when it turned the page
     */
    public boolean advanceAutoFlip(long elapsedTicks) {
        if (!autoFlip || pages() < 2 || elapsedTicks <= 0) return false;
        ticksOnPage += elapsedTicks;
        if (ticksOnPage < FLIP_TICKS) return false;
        nextPage();
        return true;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        position.write(map, "");
        map.put("label", label);
        map.put("autoFlip", autoFlip);
        List<String> linked = new ArrayList<>();
        for (BlockKey sensor : sensors) linked.add(sensor.x() + "," + sensor.y() + "," + sensor.z());
        map.put("sensors", linked);
        return map;
    }

    /** Linked sensors are saved as "x,y,z" in the hub's world; malformed entries are skipped. */
    public static SensorHub fromMap(Map<String, Object> map) {
        BlockKey position = BlockKey.read(map, "");
        Object label = map.get("label");
        SensorHub hub = new SensorHub(position, label instanceof String text && !text.isBlank() ? text : "Sensors");
        hub.autoFlip = map.get("autoFlip") instanceof Boolean flag && flag;
        if (map.get("sensors") instanceof List<?> list) {
            for (Object entry : list) {
                String[] parts = String.valueOf(entry).split(",");
                if (parts.length != 3) continue;
                try {
                    hub.link(new BlockKey(position.worldId(), Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())));
                } catch (NumberFormatException ignored) {
                    // a hand-edited entry; leave it out rather than fail the whole hub
                }
            }
        }
        return hub;
    }
}
