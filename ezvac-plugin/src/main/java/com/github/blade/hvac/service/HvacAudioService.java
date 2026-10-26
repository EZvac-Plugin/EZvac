package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.SoundSettings;
import com.github.blade.hvac.config.SoundSettings.Event;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.ClimateVent;
import com.github.blade.hvac.model.Dehumidifier;
import com.github.blade.hvac.model.Humidifier;
import com.github.blade.hvac.model.SystemId;
import com.github.blade.hvac.model.Thermostat;
import org.bukkit.Particle;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.EquipmentUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Spatial finite sound events; it never issues category-wide stopSound calls.
 * Every sound comes from {@link SoundSettings} and plays at its block, so the
 * client fades it with distance as it does any block sound.
 */
public final class HvacAudioService {
    /** Vents closer than this to one already playing stay quiet, so a duct run sounds like one stream of air. */
    static final int VENT_CLUSTER_BLOCKS = 6;

    private final HvacSettings settings;
    private final SoundSettings sounds;
    private final HvacRegistry registry;
    private long ticksSinceHum;
    private long ticksSinceVents;

    public HvacAudioService(HvacSettings settings, SoundSettings sounds, HvacRegistry registry) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void applyTransitions(HvacController.CycleResult result) {
        if (!settings.audioEnabled()) return;
        for (EquipmentUnit unit : result.started())
            play(unit.position(), unit.type() == EquipmentType.FURNACE ? Event.FURNACE_START : Event.EQUIPMENT_START);
        for (EquipmentUnit unit : result.stopped())
            play(unit.position(), unit.type() == EquipmentType.FURNACE ? Event.FURNACE_STOP : Event.EQUIPMENT_STOP);
    }

    /** A unit clicks on and off; a dehumidifier gurgles as it starts emptying a full tank. */
    public void applyUnitTransitions(ClimateUnitService.Changes changes) {
        if (!settings.audioEnabled()) return;
        for (ClimateUnit unit : changes.started()) {
            play(unit.unit(), Event.UNIT_START);
            if (unit instanceof Dehumidifier) play(unit.unit(), Event.DEHUMIDIFIER_START);
        }
        for (ClimateUnit unit : changes.stopped()) play(unit.unit(), Event.UNIT_STOP);
        for (Dehumidifier unit : changes.emptying()) play(unit.grate(), Event.DEHUMIDIFIER_EMPTYING);
    }

    /** How often a running humidifier's slab bubbles. */
    public static final long BUBBLE_PERIOD_TICKS = 10L;
    /** Bubbling is heard every this many bubble periods, like soul sand's column. */
    private static final int BUBBLE_SOUND_EVERY = 6;
    private int bubbleCount;

    /**
     * Bubbles rising from each running humidifier's waterlogged slab, the way
     * water over soul sand does, and now and then its sound. The particles
     * live because the slab holds water. Only units with a player nearby.
     */
    public void bubble(Collection<ClimateUnit> running) {
        boolean sound = settings.audioEnabled() && ++bubbleCount % BUBBLE_SOUND_EVERY == 0;
        double radiusSquared = settings.audioRadiusBlocks() * settings.audioRadiusBlocks();
        for (ClimateUnit unit : running) {
            if (!(unit instanceof Humidifier humidifier) || !humidifier.slab().isChunkLoaded()) continue;
            Location block = humidifier.slab().location();
            if (block == null || block.getWorld() == null) continue;
            Location source = block.clone().add(0.5, 0.6, 0.5);
            boolean watched = false;
            for (Player player : block.getWorld().getPlayers())
                if (withinRadius(player.getLocation().distanceSquared(source), radiusSquared)) { watched = true; break; }
            if (!watched) continue;
            block.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, source, 4, 0.2, 0.05, 0.2, 0.02);
            if (sound) play(humidifier.slab(), Event.HUMIDIFIER_BUBBLES);
        }
    }

    public void tickHum(Collection<EquipmentUnit> running, Collection<ClimateUnit> units, long elapsedTicks) {
        if (!settings.audioEnabled() || elapsedTicks <= 0) return;
        ticksSinceHum += elapsedTicks;
        if (ticksSinceHum < settings.humRefreshTicks()) return;
        ticksSinceHum %= settings.humRefreshTicks();
        for (EquipmentUnit unit : running) play(unit.position(), Event.EQUIPMENT_HUM);
        // Humidifiers bubble instead.
        for (ClimateUnit unit : units)
            if (unit instanceof Dehumidifier) play(unit.unit(), Event.DEHUMIDIFIER_HUM);
    }

    /**
     * Air moving through the vents of running systems: only vents near a
     * player, one per cluster. Pools are left out, and so are water vents.
     */
    public void tickVents(Collection<EquipmentUnit> running, long elapsedTicks) {
        if (!settings.audioEnabled() || elapsedTicks <= 0 || sounds.get(Event.VENT_AIRFLOW).isEmpty()) return;
        ticksSinceVents += elapsedTicks;
        if (ticksSinceVents < sounds.ventRefreshTicks()) return;
        ticksSinceVents %= sounds.ventRefreshTicks();
        Set<SystemId> blowing = new HashSet<>();
        for (EquipmentUnit unit : running) blowing.add(unit.system());
        blowing.removeIf(system -> {
            Thermostat thermostat = registry.thermostatFor(system);
            return thermostat != null && thermostat.pool();
        });
        if (blowing.isEmpty()) return;
        for (BlockKey vent : onePerCluster(ventsNearPlayers(blowing), VENT_CLUSTER_BLOCKS))
            play(vent, Event.VENT_AIRFLOW);
    }

    /** Air vents of these systems within hearing of any player, in a fixed order; from the registry's vent grid. */
    private TreeSet<BlockKey> ventsNearPlayers(Set<SystemId> blowing) {
        TreeSet<BlockKey> near = new TreeSet<>();
        int radius = (int) Math.ceil(settings.audioRadiusBlocks());
        double radiusSquared = settings.audioRadiusBlocks() * settings.audioRadiusBlocks();
        int shift = registry.ventCellShift();
        for (World world : Bukkit.getWorlds()) {
            Long2ObjectOpenHashMap<List<ClimateVent>> cells = registry.ventCells(world.getUID());
            if (cells == null) continue;
            for (Player player : world.getPlayers()) {
                Location at = player.getLocation();
                for (int cellX = (at.getBlockX() - radius) >> shift; cellX <= (at.getBlockX() + radius) >> shift; cellX++)
                    for (int cellZ = (at.getBlockZ() - radius) >> shift; cellZ <= (at.getBlockZ() + radius) >> shift; cellZ++) {
                        List<ClimateVent> bucket = cells.get(HvacRegistry.cellKey(cellX, cellZ));
                        if (bucket == null) continue;
                        for (ClimateVent vent : bucket) {
                            BlockKey position = vent.position();
                            if (!blowing.contains(vent.system()) || near.contains(position)) continue;
                            double dx = position.x() + 0.5 - at.getX();
                            double dy = position.y() + 0.5 - at.getY();
                            double dz = position.z() + 0.5 - at.getZ();
                            if (withinRadius(dx * dx + dy * dy + dz * dz, radiusSquared) && isAirVent(position))
                                near.add(position);
                        }
                    }
            }
        }
        return near;
    }

    private static boolean isAirVent(BlockKey position) {
        if (!position.isChunkLoaded()) return false;
        Location location = position.location();
        return location != null && location.getBlock().getType() == ClimateVent.AIR_VENT;
    }

    /**
     * Keeps the first of every group of nearby vents: walking them in order,
     * a vent within {@code spacing} blocks of one already kept is skipped.
     */
    static List<BlockKey> onePerCluster(Collection<BlockKey> ordered, int spacing) {
        List<BlockKey> kept = new ArrayList<>();
        long spacingSquared = (long) spacing * spacing;
        for (BlockKey vent : ordered) {
            boolean near = false;
            for (BlockKey other : kept) {
                if (!other.worldId().equals(vent.worldId())) continue;
                long dx = vent.x() - other.x(), dy = vent.y() - other.y(), dz = vent.z() - other.z();
                if (dx * dx + dy * dy + dz * dz <= spacingSquared) { near = true; break; }
            }
            if (!near) kept.add(vent);
        }
        return kept;
    }

    private void play(BlockKey position, Event event) {
        List<SoundSettings.Cue> cues = sounds.get(event);
        if (cues.isEmpty() || !position.isChunkLoaded()) return;
        Location block = position.location();
        if (block == null) return;
        Location source = block.clone().add(0.5, 0.5, 0.5);
        World world = source.getWorld();
        if (world == null) return;
        double radiusSquared = settings.audioRadiusBlocks() * settings.audioRadiusBlocks();
        for (Player player : world.getPlayers()) {
            if (!withinRadius(player.getLocation().distanceSquared(source), radiusSquared)) continue;
            for (SoundSettings.Cue cue : cues)
                player.playSound(source, cue.sound(), SoundCategory.BLOCKS, cue.volume(), cue.pitch());
        }
    }

    static boolean withinRadius(double distanceSquared, double radiusSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0.0
                && Double.isFinite(radiusSquared) && radiusSquared >= 0.0
                && distanceSquared <= radiusSquared;
    }
}
