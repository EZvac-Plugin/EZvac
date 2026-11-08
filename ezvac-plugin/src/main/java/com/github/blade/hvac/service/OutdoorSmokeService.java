package com.github.blade.hvac.service;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.model.SmokeSource;
import com.github.blade.hvac.simulation.AirQualityModel;
import com.github.blade.hvac.simulation.SmokePlume;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Smoke from fires burning out in the open (BETA 1.0 Pilot 4): lit
 * campfires, furnaces, smokers, blast furnaces and fire that no room counts
 * as its own. Sources are found cheaply: from each chunk's block entities as
 * it loads, and from blocks placed and fires lit, kept per chunk. Every few
 * seconds the burning ones are gathered into a coarse grid, so a reading only
 * adds up the few within reach. Main thread only.
 */
public final class OutdoorSmokeService implements Listener {
    /** How often what burns is looked at again, in ticks. */
    public static final long REFRESH_TICKS = 100L;
    /** The grid the burning sources are gathered into, in blocks. */
    static final int CELL = 32;

    /** A burning source and what it puts out per hour, at the reference size. */
    record Burning(int x, int y, int z, double fine, double coarse) {}

    private final AirQualitySettings settings;
    private final Function<UUID, LongOpenHashSet> roomCells;
    /** Per world, per chunk: every source position known there, burning or not. */
    private final Map<UUID, Long2ObjectOpenHashMap<LongOpenHashSet>> known = new HashMap<>();
    /** Per world, per grid cell: what burns there, as of the last refresh. */
    private Map<UUID, Long2ObjectOpenHashMap<List<Burning>>> burning = new HashMap<>();

    /**
     * @param roomCells the sources each world's rooms count as their own, packed like the airflow maps
     */
    public OutdoorSmokeService(AirQualitySettings settings, Function<UUID, LongOpenHashSet> roomCells) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.roomCells = Objects.requireNonNull(roomCells, "roomCells");
    }

    /** The kinds that burn out in the open: block-entity fires, and fire itself. */
    static boolean outdoorKind(SmokeSource source) {
        return source == SmokeSource.CAMPFIRE || source == SmokeSource.FURNACE || source == SmokeSource.SMOKER
                || source == SmokeSource.BLAST_FURNACE || source == SmokeSource.FIRE;
    }

    /** Learns the sources in every chunk already loaded, at start-up. */
    public void start(Iterable<World> worlds) {
        if (!settings.outdoorSmokeOn()) return;
        for (World world : worlds)
            if (world.getEnvironment() == World.Environment.NORMAL)
                for (Chunk chunk : world.getLoadedChunks()) index(chunk);
        refresh(worlds);
    }

    private void index(Chunk chunk) {
        World world = chunk.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        LongOpenHashSet found = null;
        for (BlockState state : chunk.getTileEntities(block -> outdoorKind(SmokeSource.of(block.getType())), false)) {
            if (found == null) found = new LongOpenHashSet();
            found.add(AirflowService.pack(state.getX(), state.getY(), state.getZ()));
        }
        Long2ObjectOpenHashMap<LongOpenHashSet> chunks = known.get(world.getUID());
        long key = chunkKey(chunk.getX(), chunk.getZ());
        if (found == null) {
            if (chunks != null) chunks.remove(key);
            return;
        }
        known.computeIfAbsent(world.getUID(), ignored -> new Long2ObjectOpenHashMap<>()).put(key, found);
    }

    private void note(Block block) {
        World world = block.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;
        known.computeIfAbsent(world.getUID(), ignored -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(chunkKey(block.getX() >> 4, block.getZ() >> 4), ignored -> new LongOpenHashSet())
                .add(AirflowService.pack(block.getX(), block.getY(), block.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (settings.outdoorSmokeOn()) index(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Long2ObjectOpenHashMap<LongOpenHashSet> chunks = known.get(event.getWorld().getUID());
        if (chunks != null) chunks.remove(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        known.remove(event.getWorld().getUID());
        burning.remove(event.getWorld().getUID());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (settings.outdoorSmokeOn() && outdoorKind(SmokeSource.of(event.getBlockPlaced().getType())))
            note(event.getBlockPlaced());
    }

    /** A fire lit, spread or struck: it is checked at the next refresh, once it burns. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (settings.outdoorSmokeOn()) note(event.getBlock());
    }

    /**
     * Looks at every known source again: what burns now and isn't any room's
     * own goes into the grid; what is gone is forgotten. Every
     * {@link #REFRESH_TICKS}.
     */
    public void refresh(Iterable<World> worlds) {
        if (!settings.outdoorSmokeOn()) {
            known.clear();
            burning = new HashMap<>();
            return;
        }
        AirQualitySettings.Sources rates = settings.sources();
        Map<UUID, Long2ObjectOpenHashMap<List<Burning>>> next = new HashMap<>();
        for (World world : worlds) {
            Long2ObjectOpenHashMap<LongOpenHashSet> chunks = known.get(world.getUID());
            if (chunks == null || chunks.isEmpty()) continue;
            LongOpenHashSet indoors = roomCells.apply(world.getUID());
            Long2ObjectOpenHashMap<List<Burning>> grid = new Long2ObjectOpenHashMap<>();
            var entries = chunks.long2ObjectEntrySet().fastIterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                long chunk = entry.getLongKey();
                if (!world.isChunkLoaded((int) (chunk >> 32), (int) chunk)) {
                    entries.remove();
                    continue;
                }
                LongIterator cells = entry.getValue().iterator();
                while (cells.hasNext()) {
                    long cell = cells.nextLong();
                    int x = AirflowService.unpackX(cell), y = AirflowService.unpackY(cell), z = AirflowService.unpackZ(cell);
                    Block block = world.getBlockAt(x, y, z);
                    SmokeSource source = SmokeSource.of(block.getType());
                    if (!outdoorKind(source)) {
                        cells.remove();
                        continue;
                    }
                    if (indoors.contains(cell) || !source.burning(block.getBlockData())) continue;
                    AirQualitySettings.Source rate = source.emission(rates);
                    grid.computeIfAbsent(chunkKey(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)),
                            ignored -> new ArrayList<>()).add(new Burning(x, y, z, rate.fine(), rate.coarse()));
                }
                if (entry.getValue().isEmpty()) entries.remove();
            }
            if (!grid.isEmpty()) next.put(world.getUID(), grid);
        }
        burning = next;
    }

    /**
     * The smoke from open fires in the air at a spot, µg/m³.
     *
     * @param y           the spot's height; NaN to ignore heights (a map's layer)
     * @param windMs      the wind there
     * @param fromDegrees where it comes from; NaN for none
     * @param wetness     0 clear to 1 raining: rain washes it out
     */
    public AirQualityModel.Air at(World world, double x, double y, double z, double windMs, double fromDegrees,
                                  double wetness) {
        if (world == null) return AirQualityModel.Air.NONE;
        Long2ObjectOpenHashMap<List<Burning>> grid = burning.get(world.getUID());
        if (grid == null) return AirQualityModel.Air.NONE;
        AirQualitySettings.OutdoorSmoke smoke = settings.outdoorSmoke();
        int reach = smoke.reachBlocks();
        int range = (reach + CELL - 1) / CELL;
        int cellX = Math.floorDiv((int) Math.floor(x), CELL), cellZ = Math.floorDiv((int) Math.floor(z), CELL);
        double fine = 0.0, coarse = 0.0;
        for (int gx = cellX - range; gx <= cellX + range; gx++)
            for (int gz = cellZ - range; gz <= cellZ + range; gz++) {
                List<Burning> here = grid.get(chunkKey(gx, gz));
                if (here == null) continue;
                for (Burning source : here) {
                    double share = SmokePlume.share(x - (source.x() + 0.5), Double.isNaN(y) ? Double.NaN
                            : y - (source.y() + 0.5), z - (source.z() + 0.5), windMs, fromDegrees, reach);
                    if (share <= 0.0) continue;
                    fine += source.fine() * share;
                    coarse += source.coarse() * share;
                }
            }
        if (fine <= 0.0 && coarse <= 0.0) return AirQualityModel.Air.NONE;
        double wash = 1.0 - 0.6 * Math.max(0.0, Math.min(1.0, wetness));
        double scale = SmokePlume.STRENGTH * smoke.strength() * wash;
        return new AirQualityModel.Air(fine * scale, coarse * scale, 0.0);
    }

    /** How many sources burn in the open in a world, as of the last refresh: for /hvac status. */
    public int burningIn(UUID world) {
        Long2ObjectOpenHashMap<List<Burning>> grid = burning.get(world);
        if (grid == null) return 0;
        int count = 0;
        for (List<Burning> here : grid.values()) count += here.size();
        return count;
    }

    static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
