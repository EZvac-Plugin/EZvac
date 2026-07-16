package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
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

    public void tickHum(Collection<EquipmentUnit> running, long elapsedTicks) {
        if (!settings.audioEnabled() || elapsedTicks <= 0) return;
        ticksSinceHum += elapsedTicks;
        if (ticksSinceHum < settings.humRefreshTicks()) return;
        ticksSinceHum %= settings.humRefreshTicks();
        for (EquipmentUnit unit : running) {
            playNearby(unit, Sound.BLOCK_BEACON_AMBIENT, settings.humVolume(), settings.humPitch());
        }
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
        if (!unit.position().isChunkLoaded()) return;
        Location block = unit.position().location();
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
