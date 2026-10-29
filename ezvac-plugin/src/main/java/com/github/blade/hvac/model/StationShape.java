package com.github.blade.hvac.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * The tower a weather station's sign hangs on (BETA 1.0), from the bottom up:
 * a cross of four waxed copper blocks round an iron block; an iron block;
 * the waxed cut copper the sign hangs on; then three iron blocks with a waxed
 * lightning rod on the right, the left and the right, as seen facing the
 * sign. The mirror image (left, right, left) counts too.
 */
public final class StationShape {
    public enum Part {
        COPPER("a waxed copper block"), IRON("an iron block"), CUT_COPPER("waxed cut copper"),
        ROD("a waxed lightning rod");

        private final String description;
        Part(String description) { this.description = description; }
        public String description() { return description; }
    }

    /**
     * One block of the tower. A rod's {@code faceX}/{@code faceZ} is the way
     * it points, out from the iron it's on; zero for every other part.
     */
    public record Block(int x, int y, int z, Part part, int faceX, int faceZ) {}

    /** Waxed lightning rods arrived in 1.21.9; before that a rod never oxidises, so a plain one does. */
    private static final boolean WAXED_RODS = Material.matchMaterial("WAXED_LIGHTNING_ROD") != null;

    private StationShape() {}

    /**
     * The tower behind a wall sign at (x, y, z) whose front faces
     * (facingX, facingZ), one of the four horizontal directions.
     */
    public static List<Block> blocks(int x, int y, int z, int facingX, int facingZ, boolean mirrored) {
        if (Math.abs(facingX) + Math.abs(facingZ) != 1) throw new IllegalArgumentException("a horizontal facing");
        // The cut copper the sign hangs on, and the right-hand side of someone facing the sign.
        int cx = x - facingX, cz = z - facingZ;
        int rightX = facingZ, rightZ = -facingX;
        if (mirrored) { rightX = -rightX; rightZ = -rightZ; }
        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block(cx, y - 2, cz, Part.IRON, 0, 0));
        blocks.add(new Block(cx + 1, y - 2, cz, Part.COPPER, 0, 0));
        blocks.add(new Block(cx - 1, y - 2, cz, Part.COPPER, 0, 0));
        blocks.add(new Block(cx, y - 2, cz + 1, Part.COPPER, 0, 0));
        blocks.add(new Block(cx, y - 2, cz - 1, Part.COPPER, 0, 0));
        blocks.add(new Block(cx, y - 1, cz, Part.IRON, 0, 0));
        blocks.add(new Block(cx, y, cz, Part.CUT_COPPER, 0, 0));
        for (int level = 1; level <= 3; level++) {
            int side = level == 2 ? -1 : 1;
            blocks.add(new Block(cx, y + level, cz, Part.IRON, 0, 0));
            blocks.add(new Block(cx + rightX * side, y + level, cz + rightZ * side, Part.ROD,
                    rightX * side, rightZ * side));
        }
        return blocks;
    }

    /** Whether a block is right for that part of the tower. Any waxed copper, whatever its colour. */
    public static boolean fits(Material type, Part part) {
        String name = type.name();
        return switch (part) {
            case IRON -> type == Material.IRON_BLOCK;
            case COPPER -> name.equals("WAXED_COPPER_BLOCK") || name.matches("WAXED_(EXPOSED|WEATHERED|OXIDIZED)_COPPER");
            case CUT_COPPER -> name.matches("WAXED_(EXPOSED_|WEATHERED_|OXIDIZED_)?CUT_COPPER");
            case ROD -> name.matches("WAXED_(EXPOSED_|WEATHERED_|OXIDIZED_)?LIGHTNING_ROD")
                    || !WAXED_RODS && type == Material.LIGHTNING_ROD;
        };
    }

    /** Whether waxed rods exist on this server, for what a missing rod is called. */
    public static boolean waxedRods() { return WAXED_RODS; }
}
