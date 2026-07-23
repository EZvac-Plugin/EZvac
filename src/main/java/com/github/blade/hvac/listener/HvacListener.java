package com.github.blade.hvac.listener;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.command.HvacCommand;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** World-event integration and invalidation boundary. */
public final class HvacListener implements Listener {
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final AirflowService airflow;
    private final DisplayService displays;
    private final HvacCommand command;

    public HvacListener(HvacPlugin plugin, HvacRegistry registry, AirflowService airflow,
                        DisplayService displays, HvacCommand command) {
        this.plugin = plugin;
        this.registry = registry;
        this.airflow = airflow;
        this.displays = displays;
        this.command = command;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        BlockKey key = BlockKey.of(event.getBlock().getLocation());
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat == null) {
            if (registry.thermometerAt(key) != null || registry.rpmMonitorAt(key) != null
                    || registry.settingsPanelAt(key) != null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text(
                        "That HVAC display is read-only.", NamedTextColor.YELLOW));
                refreshLater(key);
            }
            return;
        }
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission("ezvac.admin")) {
            event.getPlayer().sendMessage(Component.text(
                    "You may not edit this thermostat.", NamedTextColor.RED));
            refreshLater(key);
            return;
        }
        String line = event.line(2) == null ? ""
                : PlainTextComponentSerializer.plainText().serialize(event.line(2));
        Matcher matcher = NUMBER.matcher(line);
        if (matcher.find()) {
            try {
                thermostat.setTargetF(Double.parseDouble(matcher.group()), plugin.settings());
                registry.markDirty();
                event.getPlayer().sendMessage(Component.text(String.format(
                        java.util.Locale.US, "Target set to %.1f F.", thermostat.targetF()), NamedTextColor.GREEN));
                plugin.requestImmediateSync();
            } catch (IllegalArgumentException exception) {
                event.getPlayer().sendMessage(Component.text(
                        "Line 3 must contain a finite target temperature.", NamedTextColor.RED));
            }
        }
        refreshLater(key);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSettingsPanel(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != EquipmentSlot.HAND) return;
        SettingsPanel panel = registry.settingsPanelAt(
                BlockKey.of(event.getClickedBlock().getLocation()));
        if (panel == null) return;
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission("ezvac.use")) {
            event.getPlayer().sendMessage(Component.text(
                    "You may not control this HVAC system.", NamedTextColor.RED));
            return;
        }
        OperatingProfile profile = event.getPlayer().isSneaking()
                ? panel.profile().previous() : panel.profile().next();
        panel.setProfile(profile);
        registry.markDirty();
        displays.refreshSettingsPanel(panel, true);
        plugin.requestImmediateSync();
        event.getPlayer().sendMessage(Component.text("HVAC profile set to "
                + profile.name() + ".", profile == OperatingProfile.ECO ? NamedTextColor.GREEN
                : profile == OperatingProfile.TURBO ? NamedTextColor.GOLD : NamedTextColor.AQUA));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDuctTool(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !command.isDuctTool(event.getItem())) return;
        event.setCancelled(true);
        command.handleDuctToolClick(event.getPlayer(), event.getClickedBlock(), event.getItem(),
                event.getPlayer().isSneaking());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getBlockData() instanceof Openable)
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> airflow.invalidateNear(event.getClickedBlock().getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegisteredDispenser(BlockDispenseEvent event) {
        if (registry.equipmentAt(BlockKey.of(event.getBlock().getLocation())) != null)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        airflow.invalidateNear(event.getBlock().getLocation());
        removeDeviceAt(event.getBlock(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        changed(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        airflow.invalidateNear(event.getBlock().getLocation());
        removeDeviceAt(event.getBlock(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) { changed(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) { changed(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) { changed(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        airflow.invalidateNear(event.getBlock().getLocation());
        removeDeviceAt(event.getBlock(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (event.getBlock().getBlockData() instanceof Openable)
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> airflow.invalidateNear(event.getBlock().getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            airflow.invalidateNear(block.getLocation());
            removeDeviceAt(block, null);
            airflow.invalidateNear(block.getRelative(event.getDirection()).getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            airflow.invalidateNear(block.getLocation());
            removeDeviceAt(block, null);
            airflow.invalidateNear(block.getRelative(event.getDirection()).getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            airflow.invalidateNear(block.getLocation());
            removeDeviceAt(block, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            airflow.invalidateNear(block.getLocation());
            removeDeviceAt(block, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) {
        event.getBlocks().forEach(state -> {
            BlockKey key = BlockKey.of(state.getLocation());
            if (registry.releaseOutputOwnership(key)) plugin.requestImmediateSync();
            airflow.invalidateNear(state.getLocation());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        BlockKey key = BlockKey.of(event.getBlock().getLocation());
        if (registry.releaseOutputOwnership(key)) plugin.requestImmediateSync();
        airflow.invalidateNear(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        BlockKey key = BlockKey.of(target.getLocation());
        if (registry.releaseOutputOwnership(key)) plugin.requestImmediateSync();
        airflow.invalidateNear(target.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        airflow.invalidateChunk(event.getChunk());
        plugin.requestImmediateSync();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        airflow.invalidateChunk(event.getChunk());
        plugin.requestImmediateSync();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        airflow.invalidateWorld(event.getWorld());
        plugin.requestImmediateSync();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        airflow.invalidateWorld(event.getWorld());
        plugin.requestImmediateSync();
    }

    private void changed(Block block) {
        if (block == null) return;
        var location = block.getLocation();
        airflow.invalidateNear(location);
        if (!registry.equipment().isEmpty() || !registry.pendingCleanup().isEmpty()) {
            BlockKey key = BlockKey.of(location);
            if (registry.releaseOutputOwnership(key)) plugin.requestImmediateSync();
        }
    }

    private void removeDeviceAt(Block block, String playerName) {
        BlockKey key = BlockKey.of(block.getLocation());
        boolean removed = false;
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat != null) removed = registry.removeThermostat(key);
        else if (registry.equipmentAt(key) != null) removed = registry.removeEquipment(key) != null;
        else {
            ClimateVent vent = registry.removeVent(key);
            if (vent != null) { airflow.invalidate(vent.group()); removed = true; }
            else removed = registry.removeThermometer(key) || registry.removeRpmMonitor(key)
                    || registry.removeSettingsPanel(key);
        }
        if (!removed) return;
        displays.invalidate(key);
        plugin.requestImmediateSync();
        if (playerName != null) plugin.getLogger().fine(playerName + " removed HVAC device at " + key);
    }

    private void refreshLater(BlockKey key) {
        displays.invalidate(key);
        plugin.getServer().getScheduler().runTask(plugin, () -> displays.refreshAll(true));
    }
}
