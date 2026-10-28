package com.github.blade.hvac.model;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.simulation.AirQualityIndex;
import com.github.blade.hvac.simulation.AirQualityModel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A standalone air purifier (3.2): an iron block on a tube coral block, the
 * filter, with a control sign on the iron block. Like a real one it has fan
 * speeds, Low, Medium and High, and an Auto mode that picks the speed from
 * the air it reads. The filter fills with what it catches, faster the dirtier
 * the air; when it is full the coral dies. The player breaks the dead coral
 * and places a fresh tube coral block, and the purifier starts again with a
 * new filter. It can also be made with no filter in yet (3.2 HOTFIX 1), and
 * the first tube coral placed under it is a fresh filter.
 */
public final class AirPurifier extends ClimateUnit {
    public static final Material FILTER = Material.TUBE_CORAL_BLOCK;
    public static final Material SPENT_FILTER = Material.DEAD_TUBE_CORAL_BLOCK;

    /** What sits where its filter goes, as last read; never saved. */
    public enum Filter { FRESH, SPENT, MISSING }

    /** What the player set: a fixed fan speed, or Auto. */
    public enum Mode {
        LOW, MEDIUM, HIGH, AUTO;

        public String displayName() {
            String name = name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        /** {@code low}, {@code Auto}; null when it names no mode. */
        public static Mode parse(String text) {
            if (text == null) return null;
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "low" -> LOW;
                case "medium", "med" -> MEDIUM;
                case "high" -> HIGH;
                case "auto" -> AUTO;
                default -> null;
            };
        }
    }

    /** 1 fresh .. 0 full. */
    private double filter;
    private Filter installed = Filter.FRESH;
    private Mode mode;
    /** The fan speed it runs at: its mode's, or the one Auto picked; never AUTO. Auto's isn't saved. */
    private Mode speed;

    public AirPurifier(BlockKey position, BlockKey unit, BlockKey filter, String label, Mode mode) {
        this(position, unit, filter, label, mode, true, false, 1.0, Double.NaN);
    }

    private AirPurifier(BlockKey position, BlockKey unit, BlockKey filterBlock, String label, Mode mode,
                        boolean powered, boolean running, double filter, double zoneDewPointF) {
        super(position, unit, filterBlock, label, 0, powered, running, zoneDewPointF);
        this.filter = Double.isFinite(filter) ? Math.max(0.0, Math.min(1.0, filter)) : 1.0;
        if (this.filter <= 0.0) installed = Filter.SPENT;
        setMode(mode);
    }

    @Override public Kind kind() { return Kind.AIR_PURIFIER; }
    @Override public Material partMaterial() { return FILTER; }
    @Override public boolean partRequired() { return false; }
    /** It has no setpoint: its mode decides how hard it works. */
    @Override protected int minimumSetpoint() { return 0; }
    @Override protected int maximumSetpoint() { return 0; }

    /** The tube coral block its filter is. */
    public BlockKey filterBlock() { return part(); }
    /** 1 fresh .. 0 full. */
    public double filter() { return filter; }
    public Filter installed() { return installed; }
    public boolean filterUsable() { return installed == Filter.FRESH && filter > 0.0; }
    public Mode mode() { return mode; }
    /** The fan speed it runs at, or would: Low, Medium or High. */
    public Mode speed() { return speed; }

    /** A fixed speed takes effect at once; Auto starts on Low and decides on its next cycle. */
    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.AUTO : mode;
        this.speed = this.mode == Mode.AUTO ? Mode.LOW : this.mode;
    }

    /** {@code Auto · High}, or just {@code Medium} at a fixed speed. */
    public String modeText() {
        return mode == Mode.AUTO ? "Auto · " + speed.displayName() : speed.displayName();
    }

    /** How hard it cleans at its current speed, against High. */
    public double speedFactor(AirQualitySettings.Purifier settings) {
        return switch (speed) {
            case LOW -> settings.speeds().low();
            case MEDIUM -> settings.speeds().medium();
            case HIGH, AUTO -> settings.speeds().high();
        };
    }

    @Override
    public State state() {
        if (!powered()) return State.OFF;
        if (installed == Filter.MISSING) return State.NO_FILTER;
        if (!filterUsable()) return State.FILTER_SPENT;
        return super.state();
    }

    /**
     * Follows what is in its filter's place. Fresh tube coral where a full
     * filter was is a new filter; dead coral is a full one; anything else, air
     * included, is no filter at all. Only a full filter is ever replaced: a
     * part-used one taken out and put back keeps what it had left, so a
     * Silk Touch pickaxe can't reset it.
     *
     * @return true when a new filter went in
     */
    public boolean readFilter(Material block) {
        if (block == FILTER) {
            boolean replaced = filter <= 0.0;
            if (replaced) filter = 1.0;
            installed = Filter.FRESH;
            return replaced;
        }
        if (block == SPENT_FILTER) {
            filter = 0.0;
            installed = Filter.SPENT;
        } else {
            installed = Filter.MISSING;
        }
        running = false;
        return false;
    }

    /** Reads the filter from the world; never loads a chunk. @return true when a new filter went in */
    public boolean readFilterBlock() {
        if (!filterBlock().isChunkLoaded()) return false;
        Location location = filterBlock().location();
        return location != null && readFilter(location.getBlock().getType());
    }

    @Override
    public boolean control(double humidity, ClimateSettings settings) {
        return control(lastAir(), settings.airQuality());
    }

    /**
     * It runs whenever it is on, has a usable filter and can read the air, as
     * a real purifier does: in clean air Auto idles on Low rather than
     * stopping, so it is already working when something starts to smoke.
     * Auto steps up as soon as the air passes a threshold, and down only once
     * it is clearly better again, so it doesn't flicker between two speeds.
     *
     * @return true when it started or stopped; a change of speed is not one
     */
    public boolean control(AirQualityModel.Air air, AirQualitySettings settings) {
        boolean wanted = powered() && filterUsable() && settings.enabled() && air.known();
        if (wanted && mode == Mode.AUTO) speed = autoSpeed(air, speed, settings);
        boolean changed = wanted != running;
        running = wanted;
        return changed;
    }

    /** The speed Auto picks for this air, from the one it runs at now. */
    static Mode autoSpeed(AirQualityModel.Air air, Mode now, AirQualitySettings settings) {
        AirQualitySettings.Auto auto = settings.purifier().auto();
        int aqi = AirQualityIndex.aqi(air);
        double pollen = air.pollen();
        Mode wanted = above(aqi, pollen, auto.highAboveAqi(), auto.highPollen(), 0, settings) ? Mode.HIGH
                : above(aqi, pollen, auto.mediumAboveAqi(), auto.mediumPollen(), 0, settings) ? Mode.MEDIUM
                : Mode.LOW;
        if (wanted.ordinal() >= now.ordinal()) return wanted;
        // Stepping down: only once the air is clearly under the step it is on.
        if (now == Mode.HIGH
                && above(aqi, pollen, auto.highAboveAqi(), auto.highPollen(), auto.hysteresis(), settings))
            return Mode.HIGH;
        if (now.ordinal() >= Mode.MEDIUM.ordinal()
                && above(aqi, pollen, auto.mediumAboveAqi(), auto.mediumPollen(), auto.hysteresis(), settings))
            return Mode.MEDIUM;
        return wanted;
    }

    /**
     * Whether the air is past a step, by its AQI or its pollen. With a margin
     * the AQI must fall that far under the step, and the pollen a tenth under
     * its level, before it counts as below.
     */
    private static boolean above(int aqi, double pollen, int aqiLimit, AirQualityIndex.PollenLevel pollenLevel,
                                 int margin, AirQualitySettings settings) {
        if (aqi > aqiLimit - margin) return true;
        if (pollenLevel == null || pollenLevel == AirQualityIndex.PollenLevel.LOW) return false;
        AirQualitySettings.PollenLevels levels = settings.pollenLevels();
        double threshold = switch (pollenLevel) {
            case MODERATE -> levels.moderate();
            case HIGH -> levels.high();
            case VERY_HIGH -> levels.veryHigh();
            case LOW -> 0.0;
        };
        return pollen >= threshold * (margin > 0 ? 0.9 : 1.0);
    }

    /**
     * Fills the filter with what it caught, and a little for running at all,
     * less the slower its fan.
     *
     * @param captured what this purifier took out, in µg/m³ (grains/m³ for pollen) of a space of the reference size
     * @param hours    in-game hours it ran
     * @return true when this filled the filter, which then stops the purifier
     */
    public boolean wear(AirQualityModel.Air captured, double hours, AirQualitySettings.Purifier settings) {
        if (!filterUsable()) return false;
        double load = captured.known()
                ? captured.fine() + captured.coarse() + captured.pollen() * settings.pollenWeight() : 0.0;
        double used = load / settings.filterCapacity()
                + Math.max(0.0, hours) * speedFactor(settings) / (settings.maximumLifeDays() * 24.0);
        if (!(used > 0.0)) return false;
        filter = Math.max(0.0, filter - used);
        if (filter > 0.0) return false;
        installed = Filter.SPENT;
        running = false;
        return true;
    }

    /** A full filter's coral dies; set without physics, and never loads a chunk. */
    public void showSpentFilter() {
        if (!filterBlock().isChunkLoaded()) return;
        Location location = filterBlock().location();
        if (location == null) return;
        Block block = location.getBlock();
        if (block.getType() == FILTER) block.setType(SPENT_FILTER, false);
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        writeCommon(map, "filter");
        map.remove("setpoint");    // it has none: its mode says how hard it works
        map.put("mode", mode.name());
        map.put("filterLife", filter);
        writeZone(map);
        return map;
    }

    public static AirPurifier fromMap(Map<String, Object> map) {
        Mode mode = map.get("mode") instanceof String text ? Mode.parse(text) : null;
        AirPurifier purifier = new AirPurifier(BlockKey.read(map, ""), BlockKey.read(map, "unit"),
                BlockKey.read(map, "filter"), text(map, "label", "Purifier"), mode == null ? Mode.AUTO : mode,
                bool(map, "powered", true), bool(map, "running", false),
                number(map, "filterLife", 1.0), number(map, "zoneDewPointF", Double.NaN));
        purifier.readZoneAir(map);
        return purifier;
    }
}
