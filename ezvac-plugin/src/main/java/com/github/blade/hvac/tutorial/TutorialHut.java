package com.github.blade.hvac.tutorial;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The tutorial's hut (BETA 1.0 Pilot 10): the maintainer's build, kept in the
 * jar as a palette of block states and run-length coded rows
 * ({@code tutorial/hut.txt}), with where the player arrives, the wall block
 * the thermostat sign goes on (and its inside face), the heat pump's
 * dispenser, and the four vents. A plain list of block states places alike
 * on every Minecraft version EZvac runs on. Pure: placing it is
 * {@link TutorialService}'s.
 */
public final class TutorialHut {
    /** A spot in the hut, relative to its lowest north-west corner. */
    public record Spot(int x, int y, int z) {
        public Spot plus(int dx, int dy, int dz) { return new Spot(x + dx, y + dy, z + dz); }
    }

    private final int width, height, length;
    private final String[] palette;
    /** Palette indexes, by y, then z, then x. */
    private final int[] blocks;
    private final Spot spawn, sign, dispenser;
    private final String spawnFacing, signFacing;
    private final List<Spot> vents;
    private final int air;

    private TutorialHut(int width, int height, int length, String[] palette, int[] blocks, Spot spawn,
                        String spawnFacing, Spot sign, String signFacing, Spot dispenser, List<Spot> vents) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = palette;
        this.blocks = blocks;
        this.spawn = spawn;
        this.spawnFacing = spawnFacing;
        this.sign = sign;
        this.signFacing = signFacing;
        this.dispenser = dispenser;
        this.vents = List.copyOf(vents);
        int found = -1;
        for (int i = 0; i < palette.length; i++) if (palette[i].equals("minecraft:air")) found = i;
        this.air = found;
    }

    public static TutorialHut load(InputStream input) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            int width = 0, height = 0, length = 0;
            Spot spawn = null, sign = null, dispenser = null;
            String spawnFacing = "north", signFacing = "south";
            List<Spot> vents = new ArrayList<>();
            String[] palette = null;
            int[] blocks = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] part = line.split(" ", 2);
                switch (part[0]) {
                    case "size" -> {
                        String[] n = part[1].split(" ");
                        width = Integer.parseInt(n[0]);
                        height = Integer.parseInt(n[1]);
                        length = Integer.parseInt(n[2]);
                    }
                    case "spawn" -> {
                        String[] n = part[1].split(" ");
                        spawn = spot(n);
                        if (n.length > 3) spawnFacing = n[3];
                    }
                    case "sign" -> {
                        String[] n = part[1].split(" ");
                        sign = spot(n);
                        if (n.length > 3) signFacing = n[3];
                    }
                    case "dispenser" -> dispenser = spot(part[1].split(" "));
                    case "vents" -> {
                        for (String one : part[1].split(";")) vents.add(spot(one.strip().split(" ")));
                    }
                    case "palette" -> {
                        palette = new String[Integer.parseInt(part[1].strip())];
                        for (int i = 0; i < palette.length; i++) palette[i] = reader.readLine().strip();
                    }
                    case "rows" -> {
                        if (palette == null || width <= 0) throw new IOException("rows before size or palette");
                        blocks = new int[width * height * length];
                        int air = -1;
                        for (int i = 0; i < palette.length; i++) if (palette[i].equals("minecraft:air")) air = i;
                        java.util.Arrays.fill(blocks, Math.max(0, air));
                        String row;
                        while ((row = reader.readLine()) != null) {
                            row = row.strip();
                            if (row.isEmpty()) continue;
                            String[] tokens = row.split(" ");
                            int y = Integer.parseInt(tokens[0]), z = Integer.parseInt(tokens[1]), x = 0;
                            for (int t = 2; t < tokens.length; t++) {
                                String[] run = tokens[t].split("\\*");
                                int id = Integer.parseInt(run[0]), count = run.length > 1 ? Integer.parseInt(run[1]) : 1;
                                for (int c = 0; c < count; c++) blocks[(y * length + z) * width + x++] = id;
                            }
                            if (x != width) throw new IOException("row " + y + " " + z + " is " + x + " long, not " + width);
                        }
                    }
                    default -> throw new IOException("unknown line: " + line);
                }
            }
            if (blocks == null || spawn == null || sign == null || dispenser == null || vents.isEmpty())
                throw new IOException("the hut is missing its blocks or a marker");
            return new TutorialHut(width, height, length, palette, blocks, spawn, spawnFacing.toLowerCase(Locale.ROOT),
                    sign, signFacing.toLowerCase(Locale.ROOT), dispenser, vents);
        }
    }

    private static Spot spot(String[] n) {
        return new Spot(Integer.parseInt(n[0]), Integer.parseInt(n[1]), Integer.parseInt(n[2]));
    }

    public int width() { return width; }
    public int height() { return height; }
    public int length() { return length; }

    /** The block state at a spot, as Minecraft writes it ("minecraft:oak_planks"). */
    public String stateAt(int x, int y, int z) {
        return palette[blocks[(y * length + z) * width + x]];
    }

    public boolean isAir(int x, int y, int z) {
        return blocks[(y * length + z) * width + x] == air;
    }

    public Spot spawn() { return spawn; }
    /** north, east, south or west. */
    public String spawnFacing() { return spawnFacing; }
    /** Where the thermostat's wall sign goes: the block in front of the wall's inside face. */
    public Spot sign() { return sign; }
    /** Which way the sign faces: into the hut. */
    public String signFacing() { return signFacing; }
    public Spot dispenser() { return dispenser; }
    public List<Spot> vents() { return vents; }

    /**
     * The heat pump's casing (as equipment.md shows it): a 3x3x3 cube with the
     * dispenser facing up in the middle of the bottom layer and its output
     * cell above it left empty; every other cell is casing.
     */
    public List<Spot> casing() {
        List<Spot> out = new ArrayList<>();
        for (int dy = 0; dy <= 2; dy++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0 && dy <= 1) continue;
                    out.add(dispenser.plus(dx, dy, dz));
                }
        return out;
    }

    /** The cell above the dispenser, where water appears while it runs: left empty. */
    public Spot output() { return dispenser.plus(0, 1, 0); }

    /** The casing cell offered as glass: the middle layer's side facing the hut, to watch the water. */
    public Spot window() {
        int dx = Integer.signum(sign.x() - dispenser.x()), dz = Integer.signum(sign.z() - dispenser.z());
        // The axis the hut lies further along; one side of the middle layer.
        if (Math.abs(sign.x() - dispenser.x()) >= Math.abs(sign.z() - dispenser.z())) dz = 0;
        else dx = 0;
        return dispenser.plus(dx, 1, dz);
    }
}
