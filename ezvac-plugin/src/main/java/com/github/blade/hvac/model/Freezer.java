package com.github.blade.hvac.model;

import com.github.blade.hvac.config.RefrigerationSettings;

import java.util.Map;

/**
 * A deep freezer (BETA 1.0 Pilot 7): a chest freezer, holding its barrel at
 * -20 to 0 F, -10 by default. Its barrel opens on top like a chest freezer's
 * lid, so the cold air stays in: an opening lets in far less than a fridge's
 * door, and leaves half the frost. Frost builds up inside it, each time it's
 * opened (more in humid air) and slowly while it runs; it takes room in the
 * barrel and slows its cooling. A deep freezer never defrosts by itself, as
 * a real chest freezer doesn't: Defrost now clears it. A refrigerator's
 * freezer (its subclass) is an everyday frost-free one, defrosting itself.
 */
public sealed class Freezer extends Refrigerator permits FridgeFreezer {
    /** Its frost, 0 none to 1 a full ring round the barrel. */
    private double frost;
    private boolean autoDefrost = hasAutoDefrost();
    /** How much of a fridge door's air its lid lets in: cold air sinks and stays in a chest freezer. */
    static final double LID = 0.4;
    /** How much of a door's frost an opening of its lid leaves. */
    static final double LID_FROST = 0.5;
    /** Ticks of defrosting left; above 0 only while it defrosts. */
    private long defrostTicks;

    public Freezer(BlockKey position, BlockKey unit, BlockKey barrel, String label, int setpointF) {
        this(position, unit, barrel, label, setpointF, true, false, Double.NaN);
    }

    protected Freezer(BlockKey position, BlockKey unit, BlockKey barrel, String label, int setpointF,
                      boolean powered, boolean running, double insideF) {
        super(position, unit, barrel, label, setpointF, powered, running, insideF);
    }

    @Override public Kind kind() { return Kind.FREEZER; }
    @Override public int minimumF() { return RefrigerationSettings.FREEZER_MINIMUM_F; }
    @Override public int maximumF() { return RefrigerationSettings.FREEZER_MAXIMUM_F; }
    @Override public double heatFPerHour(RefrigerationSettings settings) { return settings.freezerHeatFPerHour(); }

    public double frost() { return frost; }
    public boolean autoDefrost() { return autoDefrost && hasAutoDefrost(); }

    /** Whether it can defrost itself: a refrigerator's freezer can, a deep freezer never does. */
    public boolean hasAutoDefrost() { return false; }

    @Override protected double doorFactor() { return LID; }

    /** How much of the frost an opening brings it keeps: half for a deep freezer's lid. */
    protected double openingFrostFactor() { return LID_FROST; }
    public boolean defrosting() { return defrostTicks > 0; }
    public long defrostTicks() { return defrostTicks; }

    public void setAutoDefrost(boolean on) { this.autoDefrost = on && hasAutoDefrost(); }

    /** For tests and the admin command: its frost straight away, 0 to 1. */
    public void setFrost(double frost) {
        this.frost = Double.isFinite(frost) ? Math.max(0.0, Math.min(1.0, frost)) : 0.0;
    }

    /** How much more frost humid air brings: 1 at 50% RH, twice at 100%, a third in very dry air. */
    static double humidityFactor(double humidity) {
        if (!Double.isFinite(humidity)) return 1.0;
        return Math.max(0.3, Math.min(2.0, humidity / 50.0));
    }

    /**
     * It was opened: the air that came in leaves frost behind, more when it's
     * humid, and only as much as came in, so opening it again and again frosts
     * it no more than once.
     *
     * @param letIn how much of a full opening's air came in, from {@link #opened}
     */
    public void frostFromOpening(double humidity, double letIn, RefrigerationSettings settings) {
        if (defrosting() || !(letIn > 0)) return;
        setFrost(frost + settings.frostPerOpening() * humidityFactor(humidity) * Math.min(1.0, letIn)
                * openingFrostFactor());
    }

    /**
     * Starts a defrost now: its compressor rests, its heater melts the frost,
     * and food stays frozen. Works switched off too, so frosted food is never
     * locked away for longer than one defrost.
     */
    public void startDefrost(RefrigerationSettings settings) {
        defrostTicks = settings.defrostTicks();
    }

    @Override
    protected boolean advanceFrost(double hours, double humidity, long elapsedTicks, RefrigerationSettings settings) {
        if (defrosting()) {
            setFrost(frost - (double) Math.max(0L, elapsedTicks) / settings.defrostTicks());
            defrostTicks -= Math.max(0L, elapsedTicks);
            if (defrostTicks <= 0) {
                defrostTicks = 0;
                frost = 0.0;
            }
            return true;
        }
        if (running()) setFrost(frost + settings.frostPerRunningHour() * hours * humidityFactor(humidity));
        if (autoDefrost() && switchedOn() && frost >= settings.autoDefrostAt()) {
            startDefrost(settings);
            return true;
        }
        return false;
    }

    @Override
    protected double pullFactor(RefrigerationSettings settings) {
        return 1.0 - settings.frostSlowdown() * frost;
    }

    @Override
    public String activity() {
        return defrosting() ? "Defrosting" : super.activity();
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        if (frost > 0.0) map.put("frost", frost);
        if (hasAutoDefrost()) map.put("autoDefrost", autoDefrost);
        if (defrostTicks > 0) map.put("defrostTicks", defrostTicks);
        return map;
    }

    public static Freezer fromMap(Map<String, Object> map) {
        Saved saved = Saved.read(map, "Deep-freezer", -10);
        Freezer freezer = new Freezer(saved.position(), saved.unit(), saved.barrel(), saved.label(), saved.setpoint(),
                saved.powered(), saved.running(), saved.insideF());
        freezer.readFrost(map);
        return freezer;
    }

    /** Its frost and defrost, as saved. */
    protected void readFrost(Map<String, Object> map) {
        setFrost(number(map, "frost", 0.0));
        autoDefrost = hasAutoDefrost() && bool(map, "autoDefrost", true);
        defrostTicks = Math.max(0L, (long) number(map, "defrostTicks", 0.0));
    }
}
