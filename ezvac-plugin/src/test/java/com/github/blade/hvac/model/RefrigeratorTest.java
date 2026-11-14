package com.github.blade.hvac.model;

import com.github.blade.hvac.config.RefrigerationSettings;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Fridges and freezers (BETA 1.0 Pilot 7): their inside, door, control and saving. */
class RefrigeratorTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final RefrigerationSettings SETTINGS = RefrigerationSettings.defaults();
    private static final long HOUR = 1_000L;

    private static Fridge fridge() {
        return new Fridge(new BlockKey(WORLD, 1, 65, 0), new BlockKey(WORLD, 0, 65, 0), new BlockKey(WORLD, 0, 66, 0),
                "kitchen", 37);
    }

    private static Freezer freezer() {
        return new Freezer(new BlockKey(WORLD, 1, 65, 5), new BlockKey(WORLD, 0, 65, 5), new BlockKey(WORLD, 0, 66, 5),
                "chest", 0);
    }

    /** Steps it this many in-game hours in a room this warm, a tenth of an hour at a time. */
    private static void run(Refrigerator unit, double roomF, double hours) {
        for (int i = 0; i < Math.round(hours * 10); i++) unit.step(roomF, HOUR / 10, SETTINGS);
    }

    @Test
    void aFridgeCoolsItsBarrelToItsSetpointAndHoldsThere() {
        Fridge fridge = fridge();
        fridge.step(70.0, 0, SETTINGS);
        assertEquals(70.0, fridge.insideF(), 1e-9, "it starts at the room's temperature");
        assertTrue(fridge.running());
        run(fridge, 70.0, 1.0);
        assertTrue(fridge.insideF() < 55.0, "an hour cools it a good way: " + fridge.insideF());
        run(fridge, 70.0, 6.0);
        assertTrue(fridge.insideF() >= 37.0 - 1e-9 && fridge.insideF() <= 37.0 + SETTINGS.hysteresisF() + 1e-9,
                "and it holds 37 F within its hysteresis: " + fridge.insideF());
        assertEquals("Cooling".equals(fridge.activity()) || "Holding".equals(fridge.activity()), true);
    }

    @Test
    void aFreezerGoesBelowZeroAndBothKeepTheirRanges() {
        Freezer freezer = freezer();
        run(freezer, 70.0, 10.0);
        assertTrue(freezer.insideF() <= 0.0 + SETTINGS.hysteresisF() + 1.0, "a freezer reaches 0 F: " + freezer.insideF());
        freezer.setSetpoint(40);
        assertEquals(0, freezer.setpoint(), "a deep freezer goes no warmer than 0 F");
        freezer.setSetpoint(-40);
        assertEquals(-20, freezer.setpoint());
        Fridge fridge = fridge();
        fridge.setSetpoint(10);
        assertEquals(33, fridge.setpoint(), "a fridge never freezes");
        fridge.setSetpoint(70);
        assertEquals(50, fridge.setpoint());
    }

    @Test
    void offItWarmsToTheRoomAndOpenItLetsTheRoomIn() {
        Fridge fridge = fridge();
        run(fridge, 70.0, 8.0);
        double cold = fridge.insideF();
        fridge.opened(70.0, SETTINGS);
        assertTrue(fridge.doorOpen());
        assertEquals(cold + (70.0 - cold) * SETTINGS.doorShare(), fridge.insideF(), 1e-9, "a share of the room at once");
        assertEquals("Door open", fridge.activity());
        fridge.closed();
        assertFalse(fridge.doorOpen());
        fridge.setPowered(false);
        run(fridge, 70.0, 30.0);
        assertTrue(fridge.insideF() > 60.0, "switched off, it warms toward the room: " + fridge.insideF());
        assertFalse(fridge.running());
        assertEquals("Off", fridge.activity());
    }

    @Test
    void openingItOverAndOverLetsInLittleMoreThanOnce() {
        Fridge once = fridge(), spammed = fridge();
        run(once, 70.0, 8.0);
        run(spammed, 70.0, 8.0);
        once.opened(70.0, SETTINGS);
        once.closed();
        for (int i = 0; i < 10; i++) {
            spammed.opened(70.0, SETTINGS);
            spammed.closed();
        }
        assertEquals(once.insideF(), spammed.insideF(), 1e-9, "ten quick openings, the same as one");
        // An hour shut, its cold air is back: the next opening lets in a full share again.
        double before = once.insideF();
        run(once, 70.0, 1.0);
        double rested = once.insideF();
        once.opened(70.0, SETTINGS);
        assertEquals(rested + (70.0 - rested) * SETTINGS.doorShare(), once.insideF(), 1e-9);
        assertTrue(before < 45.0);
    }

    @Test
    void openingAFreezerOverAndOverFrostsItNoMoreThanOnce() {
        Freezer once = freezer(), spammed = freezer();
        run(once, 70.0, 8.0);
        run(spammed, 70.0, 8.0);
        double start = once.frost();
        once.frostFromOpening(50.0, once.opened(70.0, SETTINGS), SETTINGS);
        once.closed();
        for (int i = 0; i < 20; i++) {
            spammed.frostFromOpening(50.0, spammed.opened(70.0, SETTINGS), SETTINGS);
            spammed.closed();
        }
        assertEquals(once.frost(), spammed.frost(), 1e-9, "twenty quick openings, the frost of one");
        assertEquals(start + SETTINGS.frostPerOpening() * Freezer.LID_FROST, once.frost(), 1e-9);
        // Shut an hour, a full opening's air (and frost) again.
        run(once, 70.0, 1.0);
        double rested = once.frost();
        once.frostFromOpening(50.0, once.opened(70.0, SETTINGS), SETTINGS);
        assertEquals(rested + SETTINGS.frostPerOpening() * Freezer.LID_FROST, once.frost(), 1e-9);
    }

    @Test
    void aDeepFreezersLidLetsInLessThanADoor() {
        Freezer deep = freezer();
        Fridge fridge = fridge();
        run(deep, 70.0, 10.0);
        run(fridge, 70.0, 10.0);
        double d = deep.insideF(), f = fridge.insideF();
        deep.opened(70.0, SETTINGS);
        fridge.opened(70.0, SETTINGS);
        assertEquals((70.0 - d) * SETTINGS.doorShare() * Freezer.LID, deep.insideF() - d, 1e-9);
        assertEquals((70.0 - f) * SETTINGS.doorShare(), fridge.insideF() - f, 1e-9);
    }

    @Test
    void aFridgeKeepsUpToThreeBarrels() {
        BlockKey b2 = new BlockKey(WORLD, 0, 67, 0), b3 = new BlockKey(WORLD, 0, 68, 0);
        Fridge tall = new Fridge(new BlockKey(WORLD, 1, 65, 0), new BlockKey(WORLD, 0, 65, 0),
                new BlockKey(WORLD, 0, 66, 0), java.util.List.of(b2, b3), "shop", 37);
        assertEquals(3, tall.barrels());
        assertEquals(java.util.List.of(b2, b3), tall.extraBlocks());
        assertTrue(tall.keepsCold(b3) && tall.keepsCold(tall.barrel()));
        assertEquals(SETTINGS.fridgeHeatFPerHour() * 3, tall.heatFPerHour(SETTINGS), 1e-9, "a fridge's worth a barrel");
        run(tall, 70.0, 6.0);
        assertEquals(tall.insideF(), tall.insideAt(b3), 1e-9, "one inside through them all");
        Fridge loaded = Fridge.fromMap(tall.toMap());
        assertEquals(java.util.List.of(b2, b3), loaded.extraBlocks(), "its barrels are saved");
        assertEquals(1, Fridge.fromMap(fridge().toMap()).barrels());
        assertThrows(IllegalArgumentException.class, () -> new Fridge(new BlockKey(WORLD, 1, 65, 0),
                new BlockKey(WORLD, 0, 65, 0), new BlockKey(WORLD, 0, 66, 0),
                java.util.List.of(b2, b3, new BlockKey(WORLD, 0, 69, 0)), "x", 37), "three at most");
    }

    @Test
    void leftOpenAFreezerWarmsEvenWithItsCompressorRunning() {
        Freezer freezer = freezer();
        run(freezer, 70.0, 10.0);
        double cold = freezer.insideF();
        freezer.opened(70.0, SETTINGS);
        double afterOpening = freezer.insideF();
        run(freezer, 70.0, 0.5);
        assertTrue(freezer.running(), "its compressor runs");
        assertTrue(freezer.insideF() > afterOpening + 5.0, "but standing open it warms: " + cold + " -> "
                + afterOpening + " -> " + freezer.insideF());
        freezer.closed();
        double warm = freezer.insideF();
        run(freezer, 70.0, 1.0);
        assertTrue(freezer.insideF() < warm, "shut, it cools again");
    }

    @Test
    void frostBuildsFromOpeningsMoreInHumidAirAndSlowsCooling() {
        Freezer dry = freezer(), humid = freezer();
        for (int i = 0; i < 5; i++) {
            dry.frostFromOpening(20.0, 1.0, SETTINGS);
            humid.frostFromOpening(90.0, 1.0, SETTINGS);
        }
        assertTrue(humid.frost() > 2 * dry.frost(), humid.frost() + " vs " + dry.frost());
        assertEquals(5 * SETTINGS.frostPerOpening() * 1.8 * Freezer.LID_FROST, humid.frost(), 1e-9,
                "a deep freezer's lid leaves half a door's frost");
        // A full ring halves its cooling.
        Freezer clean = freezer(), frosted = freezer();
        frosted.setFrost(1.0);
        frosted.setAutoDefrost(false);
        run(clean, 70.0, 1.0);
        run(frosted, 70.0, 1.0);
        assertTrue(frosted.insideF() > clean.insideF() + 5.0, frosted.insideF() + " vs " + clean.insideF());
    }

    @Test
    void aRefrigeratorsFreezerDefrostsItselfAndFoodStaysFrozenButADeepFreezerNever() {
        Freezer deep = freezer();
        run(deep, 70.0, 10.0);
        deep.setFrost(0.95);
        deep.step(70.0, 50.0, HOUR / 10, SETTINGS);
        assertFalse(deep.defrosting(), "a deep freezer never defrosts itself");
        assertFalse(deep.hasAutoDefrost());
        deep.setAutoDefrost(true);
        assertFalse(deep.autoDefrost(), "and can't be told to");
        Freezer freezer = new FridgeFreezer(new BlockKey(WORLD, 1, 65, 9), new BlockKey(WORLD, 0, 65, 9),
                new BlockKey(WORLD, 0, 66, 9), new BlockKey(WORLD, 0, 67, 9), "kitchen", 0, 37);
        run(freezer, 70.0, 10.0);
        freezer.setFrost(SETTINGS.autoDefrostAt());
        freezer.step(70.0, 50.0, HOUR / 10, SETTINGS);
        assertTrue(freezer.defrosting(), "heavy frost starts a defrost");
        assertEquals("Defrosting", freezer.activity());
        assertFalse(freezer.running(), "its compressor rests");
        double warmest = freezer.insideF();
        for (int i = 0; i < 40 && freezer.defrosting(); i++) {
            freezer.step(70.0, 50.0, HOUR / 10, SETTINGS);
            warmest = Math.max(warmest, freezer.insideF());
        }
        assertFalse(freezer.defrosting(), "and it ends");
        assertEquals(0.0, freezer.frost(), 1e-9, "with the frost gone");
        assertTrue(warmest <= SETTINGS.defrostCeilingF() + 1e-9, "food stays frozen: " + warmest);
    }

    @Test
    void autoDefrostOffLetsFrostGrowAndDefrostNowWorksEvenWhenOff() {
        Freezer freezer = freezer();
        freezer.setAutoDefrost(false);
        freezer.setFrost(0.9);
        freezer.step(70.0, 50.0, HOUR / 10, SETTINGS);
        assertFalse(freezer.defrosting(), "never by itself");
        freezer.setPowered(false);
        freezer.startDefrost(SETTINGS);
        run(freezer, 70.0, 1.0);
        assertEquals(0.0, freezer.frost(), 1e-9, "a defrost asked for runs switched off too");
        Freezer loaded = Freezer.fromMap(freezer.toMap());
        assertFalse(loaded.autoDefrost(), "auto defrost off is saved");
        Freezer saved = freezer();
        saved.setFrost(0.4);
        assertEquals(0.4, Freezer.fromMap(saved.toMap()).frost(), 1e-9, "frost is saved");
    }

    @Test
    void openItLeaksFasterThanShut() {
        Fridge shut = fridge(), open = fridge();
        run(shut, 70.0, 8.0);
        run(open, 70.0, 8.0);
        shut.setPowered(false);
        open.setPowered(false);
        open.opened(Double.NaN, SETTINGS);   // the door counts; the room's air unknown adds nothing at once
        run(shut, 70.0, 1.0);
        run(open, 70.0, 1.0);
        assertTrue(open.insideF() - shut.insideF() > 5.0, open.insideF() + " vs " + shut.insideF());
    }

    @Test
    void outOfReachItHoldsAndStops() {
        Fridge fridge = fridge();
        run(fridge, 70.0, 0.5);
        double held = fridge.insideF();
        assertTrue(fridge.running());
        fridge.step(Double.NaN, HOUR, SETTINGS);
        assertFalse(fridge.running());
        assertEquals(held, fridge.insideF(), 1e-9);
    }

    @Test
    void itSavesAndLoadsWithItsInside() {
        Freezer freezer = freezer();
        run(freezer, 70.0, 3.0);
        Freezer loaded = Freezer.fromMap(freezer.toMap());
        assertEquals(freezer.insideF(), loaded.insideF(), 1e-9);
        assertEquals(freezer.barrel(), loaded.barrel());
        assertEquals("chest", loaded.label());
        assertEquals(ClimateUnit.Kind.FREEZER, loaded.kind());
        Fridge fresh = Fridge.fromMap(fridge().toMap());
        assertTrue(Double.isNaN(fresh.insideF()), "never run, nothing saved for its inside");
        assertEquals(DeviceKind.FRIDGE, DeviceKind.of(fresh.kind()));
        assertEquals(SETTINGS.fridgeHeatFPerHour(), fresh.heatFPerHour(SETTINGS));
        assertEquals(SETTINGS.freezerHeatFPerHour(), freezer.heatFPerHour(SETTINGS));
    }
}
