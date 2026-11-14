package com.github.blade.hvac.model;

import com.github.blade.hvac.config.RefrigerationSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A fridge-freezer (BETA 1.0 Pilot 7): two compartments on one compressor. */
class FridgeFreezerTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final RefrigerationSettings SETTINGS = RefrigerationSettings.defaults();
    private static final long HOUR = 1_000L;
    private static final BlockKey SIGN = new BlockKey(WORLD, 1, 65, 0), IRON = new BlockKey(WORLD, 0, 65, 0),
            FREEZER = new BlockKey(WORLD, 0, 66, 0), FRIDGE = new BlockKey(WORLD, 0, 67, 0);

    private static FridgeFreezer both() {
        return new FridgeFreezer(SIGN, IRON, FREEZER, FRIDGE, "kitchen", 0, 37);
    }

    private static void run(Refrigerator unit, double roomF, double hours) {
        for (int i = 0; i < Math.round(hours * 10); i++) unit.step(roomF, HOUR / 10, SETTINGS);
    }

    @Test
    void itCoolsEachCompartmentToItsOwnSetpoint() {
        FridgeFreezer both = both();
        run(both, 70.0, 12.0);
        assertTrue(both.insideF() <= 0.0 + SETTINGS.hysteresisF() + 1e-6, "the freezer reaches 0 F: " + both.insideF());
        assertTrue(both.fridgeInsideF() >= 37.0 - 1e-9
                        && both.fridgeInsideF() <= 37.0 + SETTINGS.hysteresisF() + 1e-6,
                "the fridge holds 37 F, never frozen: " + both.fridgeInsideF());
        assertEquals(ClimateUnit.Kind.FRIDGE_FREEZER, both.kind());
        assertEquals(DeviceKind.FRIDGE_FREEZER, DeviceKind.of(both.kind()));
        assertInstanceOf(Freezer.class, both, "its lower barrel is a freezer, frost and all");
    }

    @Test
    void theCompressorRunsForTheFridgeAloneWithoutOvercoolingTheFreezer() {
        FridgeFreezer both = both();
        run(both, 70.0, 12.0);
        both.setFridgeSetpoint(33);
        double freezer = both.insideF();
        run(both, 70.0, 0.2);
        assertTrue(both.running(), "the fridge wants it colder, so the compressor runs");
        assertTrue(both.insideF() >= Math.min(freezer, 0.0) - 1e-9, "the freezer never goes below its setpoint");
        run(both, 70.0, 3.0);
        assertTrue(both.fridgeInsideF() <= 33.0 + SETTINGS.hysteresisF() + 1e-6, "" + both.fridgeInsideF());
    }

    @Test
    void eachDoorIsItsOwnAndOnlyTheFreezerFrosts() {
        FridgeFreezer both = both();
        run(both, 70.0, 12.0);
        double fridge = both.fridgeInsideF(), freezer = both.insideF(), frost = both.frost();
        both.fridgeOpened(70.0, SETTINGS);
        assertTrue(both.fridgeInsideF() > fridge, "opening the fridge warms the fridge");
        assertEquals(freezer, both.insideF(), 1e-9, "and not the freezer");
        assertEquals(frost, both.frost(), 1e-9, "and never frosts");
        assertTrue(both.doorOpen() && both.fridgeDoorOpen() && !both.freezerDoorOpen());
        assertEquals("Door open", both.activity());
        // Spamming the fridge door lets in no more than once.
        double once = both.fridgeInsideF();
        both.fridgeClosed();
        for (int i = 0; i < 10; i++) { both.fridgeOpened(70.0, SETTINGS); both.fridgeClosed(); }
        assertEquals(once, both.fridgeInsideF(), 1e-9);
        assertFalse(both.doorOpen());
        both.opened(70.0, SETTINGS);
        assertTrue(both.freezerDoorOpen() && !both.fridgeDoorOpen());
        both.closed();
    }

    @Test
    void itKeepsBothBarrelsColdAndOwnsTheFridgeBarrel() {
        FridgeFreezer both = both();
        run(both, 70.0, 12.0);
        assertTrue(both.keepsCold(FREEZER) && both.keepsCold(FRIDGE) && !both.keepsCold(IRON));
        assertEquals(both.insideF(), both.insideAt(FREEZER), 1e-9);
        assertEquals(both.fridgeInsideF(), both.insideAt(FRIDGE), 1e-9);
        assertTrue(Double.isNaN(both.insideAt(IRON)));
        assertEquals(List.of(FRIDGE), both.extraBlocks());
        assertEquals(SETTINGS.fridgeHeatFPerHour() + SETTINGS.freezerHeatFPerHour(), both.heatFPerHour(SETTINGS), 1e-9);
        assertThrows(IllegalArgumentException.class,
                () -> new FridgeFreezer(SIGN, IRON, FREEZER, FREEZER, "x", 0, 37), "two compartments, two barrels");
    }

    @Test
    void aDefrostRestsTheCompressorForBoth() {
        FridgeFreezer both = both();
        run(both, 70.0, 12.0);
        both.startDefrost(SETTINGS);
        run(both, 70.0, 0.2);
        assertFalse(both.running());
        assertEquals("Defrosting", both.activity());
    }

    @Test
    void itIsSavedWithBothCompartments() {
        FridgeFreezer both = both();
        run(both, 70.0, 12.0);
        both.setFridgeSetpoint(40);
        both.setFrost(0.3);
        both.setAutoDefrost(false);
        FridgeFreezer loaded = FridgeFreezer.fromMap(both.toMap());
        assertEquals(FRIDGE, loaded.fridgeBarrel());
        assertEquals(FREEZER, loaded.barrel());
        assertEquals(40, loaded.fridgeSetpoint());
        assertEquals(0, loaded.setpoint());
        assertEquals(both.fridgeInsideF(), loaded.fridgeInsideF(), 1e-9);
        assertEquals(both.insideF(), loaded.insideF(), 1e-9);
        assertEquals(0.3, loaded.frost(), 1e-9);
        assertFalse(loaded.autoDefrost());
        assertEquals("kitchen", loaded.label());
        both.setFridgeSetpoint(10);
        assertEquals(RefrigerationSettings.FRIDGE_MINIMUM_F, both.fridgeSetpoint(), "its fridge never freezes");
    }
}
