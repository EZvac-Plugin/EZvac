package com.github.blade.hvac.model;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.config.RefrigerationSettings;
import com.github.blade.hvac.simulation.ThermalModel;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fridge or a freezer (BETA 1.0 Pilot 7): an iron block, its compressor,
 * with the control sign on its side and a barrel on top, which it keeps cold.
 * Its setpoint is a temperature in F. Its inside closes on the air around it
 * through its walls, faster while the barrel stands open, and a running
 * compressor pulls it down; the heat it pulls out warms the room it stands
 * in. Food never spoils in EZvac itself: that is an addon's (EZclimates).
 *
 * <p>The model is pure, so it can be tested without a server.
 */
public abstract sealed class Refrigerator extends ClimateUnit permits Fridge, Freezer {
    /** The barrel it keeps cold. */
    public static final Material BARREL = Material.BARREL;

    /** Its inside, in F; NaN until first stepped, when it starts at the room's temperature. */
    private double insideF;
    /** Players with its barrel open right now; never saved. */
    private int viewers;
    /**
     * How much cold air is ready to spill out when it is next opened, 0 to 1:
     * all of it after it has stood shut a while, little straight after it was
     * opened, so opening it again and again lets in no more than once. Never saved.
     */
    private double spill = 1.0;

    protected Refrigerator(BlockKey position, BlockKey unit, BlockKey barrel, String label, int setpoint,
                           boolean powered, boolean running, double insideF) {
        super(position, unit, barrel, label, setpoint, powered, running, Double.NaN);
        this.insideF = Double.isFinite(insideF) ? ThermalModel.clampTemperature(insideF) : Double.NaN;
    }

    @Override public Material partMaterial() { return BARREL; }

    public BlockKey barrel() { return part(); }

    public double insideF() { return insideF; }

    /** Whether anyone has its barrel open. */
    public boolean doorOpen() { return viewers > 0; }

    /** The setpoint's range in F, by kind. */
    public abstract int minimumF();
    public abstract int maximumF();

    @Override protected int minimumSetpoint() { return minimumF(); }
    @Override protected int maximumSetpoint() { return maximumF(); }

    /** What a running one adds to its room's air, F per in-game hour at the reference size. */
    public abstract double heatFPerHour(RefrigerationSettings settings);

    /** It works by its own inside, never by humidity: see {@link #step}. */
    @Override
    public boolean control(double humidity, ClimateSettings settings) { return false; }

    /**
     * Its barrel was opened: its cold air spills out and the room's comes in,
     * a share of the gap at once (less if it was opened only a moment ago,
     * the spill building up again over an in-game hour), and it leaks faster
     * until it is shut again.
     */
    public void opened(double roomF, RefrigerationSettings settings) {
        viewers++;
        if (Double.isFinite(roomF) && Double.isFinite(insideF))
            insideF += (roomF - insideF) * settings.doorShare() * spill;
        spill = 0.0;
    }

    /** One player shut it (or left it open and went). */
    public void closed() {
        if (viewers > 0) viewers--;
    }

    /** Nobody has it open: after a restart, or when its chunk unloads. */
    public void shut() { viewers = 0; }

    /**
     * One step: it decides whether to run, starting {@code hysteresisF}
     * above its setpoint and running down to it; its inside closes on the
     * room through its walls (faster while open); and a running compressor
     * pulls it down, never below its setpoint. Off, it only warms.
     *
     * @param roomF the air it stands in: its room's temperature, or the reading there; NaN when unknown
     * @return true when whether it runs changed
     */
    public boolean step(double roomF, long elapsedTicks, RefrigerationSettings settings) {
        return step(roomF, Double.NaN, elapsedTicks, settings);
    }

    /**
     * The same, knowing the air's relative humidity (NaN when unknown), which
     * a freezer's frost builds from.
     */
    public boolean step(double roomF, double humidity, long elapsedTicks, RefrigerationSettings settings) {
        if (!Double.isFinite(roomF)) {
            // Out of reach (its chunk unloaded): it holds where it was, and stops as equipment does.
            boolean was = running;
            running = false;
            return was;
        }
        if (!Double.isFinite(insideF)) insideF = ThermalModel.clampTemperature(roomF);
        double stepHours = Math.max(0L, elapsedTicks) / ThermalModel.MINECRAFT_TICKS_PER_HOUR;
        // A freezer defrosting runs its heater instead of its compressor.
        boolean defrostingNow = advanceFrost(stepHours, humidity, elapsedTicks, settings);
        boolean wanted = !defrostingNow && switchedOn() && settings.enabled()
                && (running ? insideF > setpoint() : insideF > setpoint() + settings.hysteresisF());
        boolean changed = wanted != running;
        running = wanted;
        double hours = Math.max(0L, elapsedTicks) / ThermalModel.MINECRAFT_TICKS_PER_HOUR;
        // Shut, its cold air builds back up: all of it within an in-game hour.
        if (!doorOpen()) spill = Math.min(1.0, spill + hours);
        double leak = settings.leakPerHour() * (doorOpen() ? settings.doorLeakFactor() : 1.0);
        insideF += (roomF - insideF) * (1.0 - Math.exp(-leak * hours));
        if (defrostingNow && insideF < settings.defrostCeilingF())
            insideF = Math.min(settings.defrostCeilingF(), insideF + settings.defrostHeatFPerHour() * hours);
        if (running) insideF = Math.max(Math.min(insideF, setpoint()),
                insideF - settings.pullDownFPerHour() * pullFactor(settings) * hours);
        insideF = ThermalModel.clampTemperature(insideF);
        return changed;
    }

    /**
     * A freezer's frost for this step: built up, or melting away while it
     * defrosts. Fridges never frost.
     *
     * @return true while it is defrosting
     */
    protected boolean advanceFrost(double hours, double humidity, long elapsedTicks, RefrigerationSettings settings) {
        return false;
    }

    /** How much of its compressor's pull it gets: less for a frosted-up freezer. */
    protected double pullFactor(RefrigerationSettings settings) {
        return 1.0;
    }

    /** Cooling, holding (at its setpoint and resting), or off, for its screen. */
    public String activity() {
        if (!switchedOn()) return "Off";
        if (doorOpen()) return "Door open";
        return running ? "Cooling" : "Holding";
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        writeCommon(map, "barrel");
        if (Double.isFinite(insideF)) map.put("insideF", insideF);
        return map;
    }

    /** What both kinds read back: their parts, label, setpoint, power and inside. */
    protected record Saved(BlockKey position, BlockKey unit, BlockKey barrel, String label, int setpoint,
                           boolean powered, boolean running, double insideF) {
        static Saved read(Map<String, Object> map, String fallbackLabel, int fallbackSetpoint) {
            return new Saved(BlockKey.read(map, ""), BlockKey.read(map, "unit"), BlockKey.read(map, "barrel"),
                    text(map, "label", fallbackLabel), (int) Math.round(number(map, "setpoint", fallbackSetpoint)),
                    bool(map, "powered", true), bool(map, "running", false), number(map, "insideF", Double.NaN));
        }
    }
}
