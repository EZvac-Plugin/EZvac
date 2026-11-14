package com.github.blade.hvac.model;

import com.github.blade.hvac.config.RefrigerationSettings;
import com.github.blade.hvac.simulation.ThermalModel;

import java.util.List;
import java.util.Map;

/**
 * A refrigerator (BETA 1.0 Pilot 7; a fridge-freezer): an iron block with the
 * control sign on its side, a freezer barrel on top of it, and a fridge
 * barrel on top of that. Its freezer is an everyday one, -5 to 10 F (0 by
 * default), opening like a door and defrosting itself as frost builds (that
 * can be switched off); its fridge holds a fridge's range. One compressor
 * cools both: it runs while either wants it, and each compartment is held at
 * its own setpoint. A defrost rests the compressor, so the fridge warms a
 * little meanwhile.
 *
 * <p>The model is pure, so it can be tested without a server.
 */
public final class FridgeFreezer extends Freezer {
    /** The fridge barrel, on top of the freezer barrel. */
    private final BlockKey fridgeBarrel;
    private int fridgeSetpoint;
    /** The fridge's inside, in F; NaN until first stepped. */
    private double fridgeInsideF;
    /** Whether the compressor is cooling the fridge, with its own hysteresis; never saved. */
    private boolean fridgeCooling;
    /** Players with the fridge barrel open; never saved. */
    private int fridgeViewers;
    /** The fridge's cold air ready to spill out, as a fridge's: see {@link Refrigerator}. Never saved. */
    private double fridgeSpill = 1.0;

    public FridgeFreezer(BlockKey position, BlockKey unit, BlockKey freezerBarrel, BlockKey fridgeBarrel, String label,
                         int freezerSetpointF, int fridgeSetpointF) {
        this(position, unit, freezerBarrel, fridgeBarrel, label, freezerSetpointF, true, false, Double.NaN,
                fridgeSetpointF, Double.NaN);
    }

    private FridgeFreezer(BlockKey position, BlockKey unit, BlockKey freezerBarrel, BlockKey fridgeBarrel, String label,
                          int freezerSetpointF, boolean powered, boolean running, double freezerInsideF,
                          int fridgeSetpointF, double fridgeInsideF) {
        super(position, unit, freezerBarrel, label, freezerSetpointF, powered, running, freezerInsideF);
        this.fridgeBarrel = java.util.Objects.requireNonNull(fridgeBarrel, "fridgeBarrel");
        if (!fridgeBarrel.worldId().equals(unit.worldId()))
            throw new IllegalArgumentException("fridge-freezer parts are in different worlds");
        if (fridgeBarrel.equals(freezerBarrel) || fridgeBarrel.equals(unit) || fridgeBarrel.equals(position))
            throw new IllegalArgumentException("a fridge-freezer's fridge barrel is one of its other blocks");
        setFridgeSetpoint(fridgeSetpointF);
        this.fridgeInsideF = Double.isFinite(fridgeInsideF) ? ThermalModel.clampTemperature(fridgeInsideF) : Double.NaN;
    }

    @Override public Kind kind() { return Kind.FRIDGE_FREEZER; }
    @Override public int minimumF() { return RefrigerationSettings.REFRIGERATOR_FREEZER_MINIMUM_F; }
    @Override public int maximumF() { return RefrigerationSettings.REFRIGERATOR_FREEZER_MAXIMUM_F; }
    /** An everyday freezer: it defrosts itself, and its door is a door, not a chest freezer's lid. */
    @Override public boolean hasAutoDefrost() { return true; }
    @Override protected double doorFactor() { return 1.0; }
    @Override protected double openingFrostFactor() { return 1.0; }

    /** Its freezer's barrel is {@link #barrel()}; this is the fridge's, above it. */
    public BlockKey fridgeBarrel() { return fridgeBarrel; }
    public int fridgeSetpoint() { return fridgeSetpoint; }
    public double fridgeInsideF() { return fridgeInsideF; }
    public boolean fridgeDoorOpen() { return fridgeViewers > 0; }
    /** Whether the freezer's door is open. */
    public boolean freezerDoorOpen() { return super.doorOpen(); }

    public void setFridgeSetpoint(int setpointF) {
        fridgeSetpoint = Math.max(RefrigerationSettings.FRIDGE_MINIMUM_F,
                Math.min(RefrigerationSettings.FRIDGE_MAXIMUM_F, setpointF));
    }

    @Override public List<BlockKey> extraBlocks() { return List.of(fridgeBarrel); }

    /** Both compartments warm the room: a fridge's and a freezer's worth. */
    @Override
    public double heatFPerHour(RefrigerationSettings settings) {
        return settings.fridgeHeatFPerHour() + settings.freezerHeatFPerHour();
    }

    @Override public boolean doorOpen() { return super.doorOpen() || fridgeViewers > 0; }

    @Override
    public double insideAt(BlockKey barrel) {
        return fridgeBarrel.equals(barrel) ? fridgeInsideF : super.insideAt(barrel);
    }

    @Override
    public boolean keepsCold(BlockKey barrel) {
        return fridgeBarrel.equals(barrel) || super.keepsCold(barrel);
    }

    /**
     * The fridge barrel was opened: as a fridge's, its cold air spills out and
     * the room's comes in, and it leaks faster until shut. It never frosts.
     *
     * @return how much of a full opening's air came in, 0 to 1
     */
    public double fridgeOpened(double roomF, RefrigerationSettings settings) {
        fridgeViewers++;
        double letIn = fridgeSpill;
        if (Double.isFinite(roomF) && Double.isFinite(fridgeInsideF))
            fridgeInsideF += (roomF - fridgeInsideF) * settings.doorShare() * letIn;
        fridgeSpill = 0.0;
        return letIn;
    }

    public void fridgeClosed() {
        if (fridgeViewers > 0) fridgeViewers--;
    }

    @Override
    public void shut() {
        super.shut();
        fridgeViewers = 0;
    }

    @Override
    protected boolean needsCooling(RefrigerationSettings settings) {
        boolean freezer = super.needsCooling(settings);
        fridgeCooling = Double.isFinite(fridgeInsideF) && (fridgeCooling ? fridgeInsideF > fridgeSetpoint
                : fridgeInsideF > fridgeSetpoint + settings.hysteresisF());
        return freezer || fridgeCooling;
    }

    @Override
    protected void stepCompartments(double roomF, double hours, RefrigerationSettings settings) {
        if (!Double.isFinite(fridgeInsideF)) fridgeInsideF = ThermalModel.clampTemperature(roomF);
        boolean open = fridgeViewers > 0;
        if (!open) fridgeSpill = Math.min(1.0, fridgeSpill + hours);
        double leak = settings.leakPerHour() * (open ? settings.doorLeakFactor() : 1.0);
        fridgeInsideF += (roomF - fridgeInsideF) * (1.0 - Math.exp(-leak * hours));
        if (!running) fridgeCooling = false;
        if (fridgeCooling) fridgeInsideF = Math.max(Math.min(fridgeInsideF, fridgeSetpoint),
                fridgeInsideF - settings.pullDownFPerHour() * pullFactor(settings) * hours);
        fridgeInsideF = ThermalModel.clampTemperature(fridgeInsideF);
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        fridgeBarrel.write(map, "fridgeBarrel");
        map.put("fridgeSetpoint", fridgeSetpoint);
        if (Double.isFinite(fridgeInsideF)) map.put("fridgeInsideF", fridgeInsideF);
        return map;
    }

    public static FridgeFreezer fromMap(Map<String, Object> map) {
        Saved saved = Saved.read(map, "Refrigerator", 0);
        FridgeFreezer unit = new FridgeFreezer(saved.position(), saved.unit(), saved.barrel(),
                BlockKey.read(map, "fridgeBarrel"), saved.label(), saved.setpoint(), saved.powered(), saved.running(),
                saved.insideF(), (int) Math.round(number(map, "fridgeSetpoint", 37)),
                number(map, "fridgeInsideF", Double.NaN));
        unit.readFrost(map);
        return unit;
    }
}
