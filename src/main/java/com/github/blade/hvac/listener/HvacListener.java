package com.github.blade.hvac.listener;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.command.HvacCommand;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.ClimateUnitService;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
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
    private final ClimateUnitService units;

    public HvacListener(HvacPlugin plugin, HvacRegistry registry, AirflowService airflow,
                        DisplayService displays, HvacCommand command, ClimateUnitService units) {
        this.plugin = plugin;
        this.registry = registry;
        this.airflow = airflow;
        this.displays = displays;
        this.command = command;
        this.units = units;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        BlockKey key = BlockKey.of(event.getBlock().getLocation());
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat == null) {
            if (registry.thermometerAt(key) != null || registry.rpmMonitorAt(key) != null
                    || registry.settingsPanelAt(key) != null || registry.climateUnitAt(key) != null
                    || registry.sensorHubAt(key) != null) {
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

    /**
     * Sneak-clicking a thermostat sign switches its system off or back on,
     * like /hvac thermostat disable|enable. Only the thermostat is toggled: a
     * disabled thermostat idles its whole group, and units switched off on
     * their own stay off when it comes back. A plain click still opens the
     * sign editor for the target.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThermostatPower(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()
                || command.isTool(event.getItem())) return;
        Thermostat thermostat = registry.thermostatAt(BlockKey.of(event.getClickedBlock().getLocation()));
        if (thermostat == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("ezvac.use")) {
            player.sendMessage(Component.text("You may not switch this HVAC system.", NamedTextColor.RED));
            return;
        }
        thermostat.setEnabled(!thermostat.enabled());
        registry.markDirty();
        displays.refreshThermostat(thermostat, true);
        plugin.requestImmediateSync();
        player.sendMessage(Component.text("System '" + thermostat.group().label() + "' switched "
                + (thermostat.enabled() ? "on." : "off."), thermostat.enabled() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
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

    /**
     * Clicking a dehumidifier's or humidifier's sign steps its setpoint;
     * sneak-clicking switches it on or off. The click is cancelled, so the
     * sign editor never opens.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClimateUnitSign(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != EquipmentSlot.HAND || command.isTool(event.getItem())) return;
        BlockKey key = BlockKey.of(event.getClickedBlock().getLocation());
        ClimateUnit unit = registry.climateUnitAt(key);
        if (unit == null || !unit.position().equals(key)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        String name = unit.kind().displayName();
        if (!player.hasPermission("ezvac.use")) {
            player.sendMessage(Component.text("You may not control this " + name.toLowerCase(java.util.Locale.ROOT)
                    + ".", NamedTextColor.RED));
            return;
        }
        if (player.isSneaking()) {
            unit.setPowered(!unit.powered());
            player.sendMessage(Component.text(name + " switched " + (unit.powered() ? "on." : "off."),
                    unit.powered() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        } else {
            unit.setSetpoint(unit.nextPreset(units.climate()));
            player.sendMessage(Component.text(name + " set to " + unit.setpoint() + "%.", NamedTextColor.AQUA));
        }
        registry.markDirty();
        displays.refreshClimateUnit(unit, true);
        plugin.requestImmediateSync();
    }

    /**
     * A full dehumidifier's grate and a running humidifier's slab hold their
     * water; it never spills into the room.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClimateUnitWater(BlockFromToEvent event) {
        if (!registry.hasClimateUnits()) return;
        if (registry.climateUnitAt(BlockKey.of(event.getBlock().getLocation())) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClimateUnitBucket(PlayerBucketFillEvent event) {
        if (!registry.hasClimateUnits()) return;
        if (registry.climateUnitAt(BlockKey.of(event.getBlock().getLocation())) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDuctTool(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !command.isDuctTool(event.getItem())) return;
        event.setCancelled(true);
        command.handleDuctToolClick(event.getPlayer(), event.getClickedBlock(), event.getItem(),
                event.getPlayer().isSneaking());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSensorTool(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !command.isSensorTool(event.getItem())) return;
        event.setCancelled(true);
        command.handleSensorToolClick(event.getPlayer(), event.getClickedBlock(), event.getItem(),
                event.getPlayer().isSneaking());
    }

    /**
     * Clicking a sensor hub turns its page; sneak-clicking switches
     * auto-flip on or off. The click is cancelled, so the sign editor never
     * opens.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSensorHub(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != EquipmentSlot.HAND || command.isTool(event.getItem())) return;
        SensorHub hub = registry.sensorHubAt(BlockKey.of(event.getClickedBlock().getLocation()));
        if (hub == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("ezvac.use")) {
            player.sendMessage(Component.text("You may not use this sensor hub.", NamedTextColor.RED));
            return;
        }
        if (player.isSneaking()) {
            hub.setAutoFlip(!hub.autoFlip());
            registry.markDirty();
            player.sendMessage(Component.text("Auto-flip " + (hub.autoFlip() ? "on." : "off."),
                    hub.autoFlip() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        } else {
            hub.nextPage();
        }
        displays.refreshSensorHub(hub, true);
    }

    /**
     * Pressing a sensor shows its reading instead. The press is cancelled,
     * so the button never moves and never powers anything.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSensorPress(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != EquipmentSlot.HAND || command.isTool(event.getItem())
                || registry.sensors().isEmpty()) return;
        Sensor sensor = registry.sensorAt(BlockKey.of(event.getClickedBlock().getLocation()));
        if (sensor == null) return;
        event.setCancelled(true);
        if (event.getPlayer().hasPermission("ezvac.use")) event.getPlayer().sendMessage(command.describeSensor(sensor));
    }

    /** An arrow can still press a wooden (mangrove or birch) sensor; it powers nothing. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSensorPowered(BlockRedstoneEvent event) {
        if (registry.sensors().isEmpty() || event.getNewCurrent() <= 0) return;
        if (registry.sensorAt(BlockKey.of(event.getBlock().getLocation())) != null) event.setNewCurrent(0);
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
        removeDeviceAt(event.getBlock(), event.getPlayer());
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
    public void onFade(BlockFadeEvent event) {
        if (!inert(event.getBlock().getType(), event.getNewState().getType())) changed(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (!inert(event.getBlock().getType(), event.getNewState().getType())) changed(event.getBlock());
    }

    /**
     * A full opaque cube turning into another one - grass decaying to dirt,
     * concrete powder setting - can neither open nor close an airflow path,
     * and neither side can be an owned water or lava output. These fire by
     * the thousand per tick after a large WorldEdit fill, so they are dropped
     * before any lookup.
     */
    private static boolean inert(Material from, Material to) {
        return from.isOccluding() && to.isOccluding();
    }

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

    // Chunk streaming fires continuously while players explore. Requesting a
    // sync here drove the whole controller pipeline at 20 Hz instead of its
    // configured period; the periodic cycle reconciles newly loaded devices
    // from world state anyway, so only the airflow cache is invalidated here.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        airflow.invalidateChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        airflow.invalidateChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        airflow.invalidateWorld(event.getWorld());
        plugin.requestImmediateSync();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        airflow.invalidateWorld(event.getWorld());
        // A world loaded again starts from its weather then, not from where it was eased to before.
        plugin.weather().forget(event.getWorld());
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

    /**
     * Breaking a registered device used to be entirely silent, so a stray pickaxe
     * could unregister a thermostat with nothing to show for it. Anything that
     * belongs to a system now says what it was and which system it left.
     * Thermometers stay quiet: they have no group, so there is nothing to warn
     * about losing.
     */
    private void removeDeviceAt(Block block, Player breaker) {
        BlockKey key = BlockKey.of(block.getLocation());
        boolean removed = false;
        String notice = null;
        String warning = null;

        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat != null) {
            removed = registry.removeThermostat(key);
            if (removed) notice = "thermostat '" + thermostat.id() + "' from system '"
                    + thermostat.group().label() + "'";
        } else if (registry.equipmentAt(key) != null) {
            EquipmentUnit unit = registry.removeEquipment(key);
            removed = unit != null;
            if (removed) {
                notice = unit.type().displayName() + " from system '"
                        + unit.group().label() + "'";
                // Losing the last source leaves a system that still reads its
                // temperature but can never act on it, which is otherwise
                // invisible until someone notices the thermostat stuck on IDLE.
                if (registry.equipmentFor(unit.group()).isEmpty())
                    warning = "System '" + unit.group().label() + "' has no equipment left."
                            + " Its thermostat will stay idle until you add some.";
            }
        } else {
            ClimateVent vent = registry.removeVent(key);
            if (vent != null) {
                airflow.invalidate(vent.group());
                removed = true;
                notice = (ClimateVent.isWaterVent(block.getType()) ? "water vent" : "vent")
                        + " from system '" + vent.group().label() + "'";
            } else {
                RpmMonitor monitor = registry.rpmMonitorAt(key);
                SettingsPanel panel = registry.settingsPanelAt(key);
                if (monitor != null && registry.removeRpmMonitor(key)) {
                    removed = true;
                    notice = "RPM monitor from system '" + monitor.group().label() + "'";
                } else if (panel != null && registry.removeSettingsPanel(key)) {
                    removed = true;
                    notice = "settings panel from system '" + panel.group().label() + "'";
                } else if (registry.climateUnitAt(key) != null) {
                    ClimateUnit unit = registry.removeClimateUnit(key);
                    units.release(unit);
                    displays.invalidate(unit.position());
                    removed = true;
                    notice = unit.kind().displayName().toLowerCase(java.util.Locale.ROOT) + " '" + unit.label() + "'";
                } else if (registry.sensorAt(key) != null) {
                    Sensor sensor = registry.removeSensor(key);
                    removed = true;
                    notice = "sensor '" + sensor.label() + "'";
                } else if (registry.sensorHubAt(key) != null) {
                    SensorHub hub = registry.removeSensorHub(key);
                    removed = true;
                    notice = "sensor hub '" + hub.label() + "'";
                } else {
                    removed = registry.removeThermometer(key); // deliberately silent
                }
            }
        }
        if (!removed) return;
        displays.invalidate(key);
        plugin.requestImmediateSync();
        if (breaker == null) return;
        if (notice != null)
            breaker.sendMessage(Component.text("Removed " + notice + ".", NamedTextColor.YELLOW));
        if (warning != null)
            breaker.sendMessage(Component.text(warning, NamedTextColor.RED));
        plugin.getLogger().fine(breaker.getName() + " removed HVAC device at " + key);
    }

    private void refreshLater(BlockKey key) {
        displays.invalidate(key);
        plugin.getServer().getScheduler().runTask(plugin, () -> displays.refreshAll(true));
    }
}
