package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.Dehumidifier;
import com.github.blade.hvac.model.Humidifier;
import org.bukkit.Particle;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.EquipmentUnit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Objects;

/** Spatial finite sound events; it never issues category-wide stopSound calls. */
public final class HvacAudioService {
    private final HvacSettings settings;
    private long ticksSinceHum;

    public HvacAudioService(HvacSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public void applyTransitions(HvacController.CycleResult result) {
        if (!settings.audioEnabled()) return;
        for (EquipmentUnit unit : result.started()) playStartup(unit);
        for (EquipmentUnit unit : result.stopped()) playShutdown(unit);
    }

    /** A unit clicks on and off; a dehumidifier gurgles as it starts emptying a full tank. */
    public void applyUnitTransitions(ClimateUnitService.Changes changes) {
        if (!settings.audioEnabled()) return;
        for (ClimateUnit unit : changes.started()) {
            playNearby(unit.unit(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.4f, 1.6f);
            if (unit instanceof Dehumidifier) playNearby(unit.unit(), Sound.BLOCK_BEACON_ACTIVATE, 0.2f, 1.2f);
        }
        for (ClimateUnit unit : changes.stopped())
            playNearby(unit.unit(), Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF, 0.4f, 1.4f);
        for (Dehumidifier unit : changes.emptying())
            playNearby(unit.grate(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.8f, 0.8f);
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
            if (sound) playNearby(humidifier.slab(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.35f, 1.0f);
        }
    }

    public void tickHum(Collection<EquipmentUnit> running, Collection<ClimateUnit> units, long elapsedTicks) {
        if (!settings.audioEnabled() || elapsedTicks <= 0) return;
        ticksSinceHum += elapsedTicks;
        if (ticksSinceHum < settings.humRefreshTicks()) return;
        ticksSinceHum %= settings.humRefreshTicks();
        for (EquipmentUnit unit : running) {
            playNearby(unit, Sound.BLOCK_BEACON_AMBIENT, settings.humVolume(), settings.humPitch());
        }
        // Smaller and quieter than a heat pump: a higher, softer hum. Humidifiers bubble instead.
        for (ClimateUnit unit : units)
            if (unit instanceof Dehumidifier)
                playNearby(unit.unit(), Sound.BLOCK_BEACON_AMBIENT, settings.humVolume() * 0.4f, settings.humPitch() * 1.6f);
    }

    private void playStartup(EquipmentUnit unit) {
        if (unit.type() == EquipmentType.FURNACE) {
            playNearby(unit, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.5f, 1.8f);
            playNearby(unit, Sound.BLOCK_DISPENSER_FAIL, 0.4f, 1.5f);
            playNearby(unit, Sound.ITEM_FIRECHARGE_USE, 0.6f, 0.5f);
        } else {
            playNearby(unit, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.5f, 1.8f);
            playNearby(unit, Sound.BLOCK_PISTON_EXTEND, 0.4f, 0.5f);
            playNearby(unit, Sound.BLOCK_BEACON_ACTIVATE, 0.3f, 0.5f);
        }
    }

    private void playShutdown(EquipmentUnit unit) {
        if (unit.type() == EquipmentType.FURNACE) {
            playNearby(unit, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.25f, 0.6f);
        } else {
            playNearby(unit, Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF, 0.5f, 1.5f);
            playNearby(unit, Sound.BLOCK_BEACON_DEACTIVATE, 0.3f, 0.5f);
        }
    }

    private void playNearby(EquipmentUnit unit, Sound sound, float volume, float pitch) {
        playNearby(unit.position(), sound, volume, pitch);
    }

    private void playNearby(BlockKey position, Sound sound, float volume, float pitch) {
        if (!position.isChunkLoaded()) return;
        Location block = position.location();
        if (block == null) return;
        Location source = block.clone().add(0.5, 0.5, 0.5);
        World world = source.getWorld();
        if (world == null) return;
        double radiusSquared = settings.audioRadiusBlocks() * settings.audioRadiusBlocks();
        for (Player player : world.getPlayers()) {
            if (withinRadius(player.getLocation().distanceSquared(source), radiusSquared))
                player.playSound(source, sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }

    static boolean withinRadius(double distanceSquared, double radiusSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0.0
                && Double.isFinite(radiusSquared) && radiusSquared >= 0.0
                && distanceSquared <= radiusSquared;
    }
}
