package com.github.blade.hvac.tutorial;

import com.github.blade.hvac.tutorial.TutorialHut.Spot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The tutorial's hut, as the jar carries it (BETA 1.0 Pilot 10). */
class TutorialHutTest {
    private static TutorialHut hut() throws IOException {
        try (InputStream input = TutorialHutTest.class.getResourceAsStream("/tutorial/hut.txt")) {
            assertNotNull(input, "tutorial/hut.txt is in the jar");
            return TutorialHut.load(input);
        }
    }

    @Test
    void theHutLoadsWithItsMarkers() throws IOException {
        TutorialHut hut = hut();
        assertEquals(30, hut.width());
        assertEquals(9, hut.height());
        assertEquals(30, hut.length());
        assertEquals(new Spot(12, 2, 20), hut.spawn());
        assertEquals("north", hut.spawnFacing());
        assertEquals(new Spot(23, 2, 7), hut.dispenser());
        assertEquals(4, hut.vents().size());
    }

    @Test
    void theMarkersAreLeftForThePlayerToBuild() throws IOException {
        TutorialHut hut = hut();
        Spot d = hut.dispenser();
        assertTrue(hut.isAir(d.x(), d.y(), d.z()), "the lodestone marker is gone: the dispenser goes there");
        Spot o = hut.output();
        assertTrue(hut.isAir(o.x(), o.y(), o.z()), "the output cell is empty");
        for (Spot cell : hut.casing()) assertTrue(hut.isAir(cell.x(), cell.y(), cell.z()), "casing " + cell + " is empty");
        for (Spot vent : hut.vents()) assertTrue(hut.isAir(vent.x(), vent.y(), vent.z()), "vent " + vent + " is empty");
        Spot spawn = hut.spawn();
        assertTrue(hut.isAir(spawn.x(), spawn.y(), spawn.z()) && hut.isAir(spawn.x(), spawn.y() + 1, spawn.z()),
                "room to stand at the spawn");
        assertFalse(hut.isAir(spawn.x(), spawn.y() - 1, spawn.z()), "ground under the spawn");
        assertEquals("minecraft:grass_block[snowy=false]", hut.stateAt(spawn.x(), spawn.y() - 1, spawn.z()),
                "the gold marker became grass");
        // Neither the stray corner block nor any marker block is left.
        for (int y = 0; y < hut.height(); y++)
            for (int z = 0; z < hut.length(); z++)
                for (int x = 0; x < hut.width(); x++) {
                    String state = hut.stateAt(x, y, z);
                    assertFalse(state.startsWith("minecraft:gold_block") || state.startsWith("minecraft:lodestone"),
                            state + " at " + x + " " + y + " " + z);
                }
        assertTrue(hut.isAir(0, 8, 0), "the stray smooth stone in the corner is gone");
    }

    @Test
    void theSignGoesOnTheInsideOfTheWall() throws IOException {
        TutorialHut hut = hut();
        Spot sign = hut.sign();
        assertEquals("south", hut.signFacing(), "facing into the hut");
        assertTrue(hut.isAir(sign.x(), sign.y(), sign.z()), "the sign's spot is free");
        // A sign facing south hangs on the block to its north: the wall's smooth stone.
        assertEquals("minecraft:smooth_stone", hut.stateAt(sign.x(), sign.y(), sign.z() - 1));
        // Inside: the hut's walls run z 4 to 10, so the sign's spot is between them.
        assertTrue(sign.z() > 4 && sign.z() < 10);
    }

    @Test
    void theCasingIsTheCubeAroundTheDispenser() throws IOException {
        TutorialHut hut = hut();
        List<Spot> casing = hut.casing();
        assertEquals(25, casing.size(), "27 cells less the dispenser and its output");
        assertEquals(25, new HashSet<>(casing).size());
        assertFalse(casing.contains(hut.dispenser()));
        assertFalse(casing.contains(hut.output()));
        assertTrue(casing.contains(hut.window()), "the glass window is a casing cell");
        assertEquals(hut.dispenser().y() + 1, hut.window().y(), "in the middle layer");
        assertTrue(hut.window().x() < hut.dispenser().x(), "on the side facing the hut");
    }

    @Test
    void aBrokenFileSaysSo() {
        String rows = "size 2 1 1\nspawn 0 0 0\nsign 0 0 0\ndispenser 0 0 0\nvents 0 0 0\npalette 1\nminecraft:air\nrows\n0 0 0\n";
        IOException short_ = assertThrows(IOException.class,
                () -> TutorialHut.load(new ByteArrayInputStream(rows.getBytes(StandardCharsets.UTF_8))));
        assertTrue(short_.getMessage().contains("long"));
        assertThrows(IOException.class, () -> TutorialHut.load(new ByteArrayInputStream("size 1 1 1\n".getBytes())));
        assertEquals(180f, TutorialService.yaw("north"));
        assertEquals(0f, TutorialService.yaw("south"));
    }
}
