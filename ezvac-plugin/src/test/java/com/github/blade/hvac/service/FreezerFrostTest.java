package com.github.blade.hvac.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A freezer's frost in its barrel (BETA 1.0 Pilot 7): which slots it covers. */
class FreezerFrostTest {
    @Test
    void aLittleFrostCoversNothingYet() {
        assertEquals(0, FreezerFrost.reach(0.04));
        assertEquals(1, FreezerFrost.reach(0.05));
        assertEquals(10, FreezerFrost.reach(0.5));
        assertEquals(20, FreezerFrost.reach(1.0));
    }

    @Test
    void foodAgainstTheWallFrostsLastAndIsFreedFirst() {
        boolean[] food = new boolean[FreezerFrost.RING.length];
        food[0] = true;   // beef in the first corner
        boolean[] some = FreezerFrost.covered(3, food);
        assertFalse(some[0], "the beef's corner stays clear while there's empty edge");
        assertTrue(some[1] && some[2] && some[3], "the empty edge frosts instead");
        boolean[] full = FreezerFrost.covered(20, food);
        assertTrue(full[0], "a full ring frosts the beef over too");
        boolean[] nearlyFull = FreezerFrost.covered(19, food);
        assertFalse(nearlyFull[0], "and it's freed first as the frost goes");
    }

    @Test
    void theRingIsTheTwentyEdgeSlotsAndNeverTheMiddle() {
        assertEquals(20, FreezerFrost.RING.length);
        for (int slot : FreezerFrost.RING)
            assertFalse(slot >= 10 && slot <= 16, "the middle row's inner slots never frost: " + slot);
        assertEquals(20, java.util.Arrays.stream(FreezerFrost.RING).distinct().count());
    }
}
