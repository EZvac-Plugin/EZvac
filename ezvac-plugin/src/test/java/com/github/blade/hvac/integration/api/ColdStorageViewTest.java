package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.ColdStorage;
import com.github.blade.hvac.config.RefrigerationSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.Freezer;
import com.github.blade.hvac.model.Fridge;
import com.github.blade.hvac.model.FridgeFreezer;
import com.github.blade.hvac.model.MiniFridge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A refrigeration appliance's view carries what a web panel needs to show it whole (API 1.12). */
class ColdStorageViewTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final RefrigerationSettings SETTINGS = RefrigerationSettings.defaults();

    private static BlockKey at(int x, int y) { return new BlockKey(WORLD, x, y, 0); }

    @Test
    void aRefrigeratorShowsItsFridgeAndItsFreezersFrost() {
        FridgeFreezer both = new FridgeFreezer(at(1, 65), at(0, 65), at(0, 66), at(0, 67), "kitchen", 0, 40);
        for (int i = 0; i < 50; i++) both.step(70.0, 100, SETTINGS);
        both.setFrost(0.3);
        ColdStorage cold = ApiViews.climateUnit(both, null).cold().orElseThrow();
        assertEquals(40, cold.fridgeSetpointF().orElseThrow());
        assertEquals(both.fridgeInsideF(), cold.fridgeInsideF().orElseThrow(), 1e-9);
        assertEquals(0.3, cold.frost().orElseThrow(), 1e-9);
        assertTrue(cold.autoDefrost(), "a refrigerator's freezer defrosts itself");
        assertEquals(2, cold.barrels());
        assertFalse(cold.doorOpen());
    }

    @Test
    void aDeepFreezerAFridgeAndAMiniFridgeShowWhatTheyHave() {
        Freezer deep = new Freezer(at(1, 70), at(0, 70), at(0, 71), "chest", -10);
        deep.startDefrost(SETTINGS);
        ColdStorage d = ApiViews.climateUnit(deep, null).cold().orElseThrow();
        assertTrue(d.defrosting());
        assertFalse(d.autoDefrost(), "a deep freezer never defrosts itself");
        assertTrue(d.fridgeSetpointF().isEmpty());
        Fridge tall = new Fridge(at(1, 75), at(0, 75), at(0, 76), List.of(at(0, 77), at(0, 78)), "shop", 37);
        ColdStorage f = ApiViews.climateUnit(tall, null).cold().orElseThrow();
        assertEquals(3, f.barrels());
        assertTrue(f.frost().isEmpty(), "a fridge never frosts");
        MiniFridge mini = new MiniFridge(at(1, 80), at(0, 80), "desk", 37);
        for (int i = 0; i < 50; i++) mini.step(70.0, 100, SETTINGS);
        assertEquals(mini.chillerF(), ApiViews.climateUnit(mini, null).cold().orElseThrow().chillerF().orElseThrow(), 1e-9);
        assertTrue(ApiViews.climateUnit(new com.github.blade.hvac.model.Humidifier(at(1, 90), at(0, 90), at(0, 91),
                "study", 45), null).cold().isEmpty(), "only refrigeration has it");
    }
}
