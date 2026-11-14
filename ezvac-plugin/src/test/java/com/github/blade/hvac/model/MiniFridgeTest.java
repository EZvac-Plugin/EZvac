package com.github.blade.hvac.model;

import com.github.blade.hvac.config.RefrigerationSettings;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A mini fridge (BETA 1.0 Pilot 7): seven slots and a chiller box, on a smaller compressor. */
class MiniFridgeTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final RefrigerationSettings SETTINGS = RefrigerationSettings.defaults();
    private static final long HOUR = 1_000L;
    private static final BlockKey SIGN = new BlockKey(WORLD, 1, 65, 0), BARREL = new BlockKey(WORLD, 0, 65, 0);

    private static MiniFridge mini() {
        return new MiniFridge(SIGN, BARREL, "desk", 37);
    }

    private static Fridge fridge() {
        return new Fridge(new BlockKey(WORLD, 1, 65, 9), new BlockKey(WORLD, 0, 65, 9), new BlockKey(WORLD, 0, 66, 9),
                "kitchen", 37);
    }

    private static void run(Refrigerator unit, double roomF, double hours) {
        for (int i = 0; i < Math.round(hours * 10); i++) unit.step(roomF, HOUR / 10, SETTINGS);
    }

    @Test
    void itCoolsToItsSetpointAtHalfAFridgesPace() {
        MiniFridge mini = mini();
        Fridge fridge = fridge();
        run(mini, 70.0, 1.0);
        run(fridge, 70.0, 1.0);
        assertTrue(mini.insideF() > fridge.insideF() + 5, "slower: " + mini.insideF() + " vs " + fridge.insideF());
        run(mini, 70.0, 10.0);
        assertTrue(mini.insideF() <= 37.0 + SETTINGS.hysteresisF() + 1e-6, "but it gets there: " + mini.insideF());
        assertEquals(SETTINGS.fridgeHeatFPerHour() / 2, mini.heatFPerHour(SETTINGS), 1e-9);
        mini.setSetpoint(10);
        assertEquals(RefrigerationSettings.FRIDGE_MINIMUM_F, mini.setpoint(), "a fridge's range");
    }

    @Test
    void theChillerBoxSitsAboutTenDegreesBelowWhileOn() {
        MiniFridge mini = mini();
        run(mini, 70.0, 12.0);
        assertEquals(mini.insideF() - MiniFridge.CHILLER_BELOW_F, mini.chillerF(), 1.5,
                "inside " + mini.insideF() + ", chiller " + mini.chillerF());
        assertTrue(mini.chillerF() < 30.0, "below freezing: " + mini.chillerF());
        assertEquals(mini.chillerF(), mini.insideAt(BARREL, MiniFridge.CHILLER), 1e-9);
        assertEquals(mini.insideF(), mini.insideAt(BARREL, MiniFridge.FIRST_FOOD), 1e-9);
        assertEquals(mini.insideF(), mini.insideAt(BARREL), 1e-9);
        mini.setPowered(false);
        run(mini, 70.0, 4.0);
        assertEquals(mini.insideF(), mini.chillerF(), 1.0, "switched off, it's no colder than the rest");
    }

    @Test
    void anOpeningLetsInMoreThanAFridgesAndItNeverFrosts() {
        MiniFridge mini = mini();
        Fridge fridge = fridge();
        run(mini, 70.0, 12.0);
        run(fridge, 70.0, 12.0);
        double miniBefore = mini.insideF(), fridgeBefore = fridge.insideF();
        mini.opened(70.0, SETTINGS);
        fridge.opened(70.0, SETTINGS);
        assertEquals((70.0 - miniBefore) * SETTINGS.doorShare() * 1.5, mini.insideF() - miniBefore, 1e-9);
        assertEquals((70.0 - fridgeBefore) * SETTINGS.doorShare(), fridge.insideF() - fridgeBefore, 1e-9);
    }

    @Test
    void itIsOneBarrelWithSevenFoodSlotsAndAChiller() {
        MiniFridge mini = mini();
        assertEquals(BARREL, mini.unit());
        assertEquals(BARREL, mini.barrel());
        assertEquals(Material.BARREL, mini.unitMaterial());
        int food = 0;
        for (int slot = 0; slot < 27; slot++) if (MiniFridge.holdsFood(slot)) food++;
        assertEquals(8, food, "seven slots and the chiller");
        assertFalse(MiniFridge.holdsFood(MiniFridge.GAP));
        assertTrue(MiniFridge.holdsFood(MiniFridge.CHILLER));
        assertEquals(DeviceKind.MINI_FRIDGE, DeviceKind.of(mini.kind()));
    }

    @Test
    void itIsSavedWithItsChiller() {
        MiniFridge mini = mini();
        run(mini, 70.0, 12.0);
        mini.setSetpoint(40);
        MiniFridge loaded = MiniFridge.fromMap(mini.toMap());
        assertEquals(40, loaded.setpoint());
        assertEquals(mini.insideF(), loaded.insideF(), 1e-9);
        assertEquals(mini.chillerF(), loaded.chillerF(), 1e-9);
        assertEquals(BARREL, loaded.barrel());
        assertEquals("desk", loaded.label());
    }
}
