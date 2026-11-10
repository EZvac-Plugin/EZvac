package com.github.blade.hvac.debug;

import com.github.blade.hvac.command.LinkTool;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateVent;
import com.github.blade.hvac.model.EquipmentUnit;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.RpmMonitor;
import com.github.blade.hvac.model.SettingsPanel;
import com.github.blade.hvac.model.SystemId;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.HvacRegistry;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.HangingSign;
import org.bukkit.block.data.type.WallHangingSign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * EZdebug, built into EZvac (BETA 1.0 Pilot 5): while an admin holds the
 * link tool with a thermostat's system selected, they see that system as
 * EZvac does. Its equipment glows, its signs are framed, its vents are
 * marked and puff while they work; the edge of the space its air fills is
 * dotted in; and the openings it leaks through glow red, through walls.
 * Only the player holding the tool sees any of it, and nothing is saved
 * with the world.
 *
 * <p>The EZdebug+ addon shows all this and more; while it's installed and
 * switched on, this stands down. Main thread only.
 */
public final class EzDebug implements Listener {
    /** How often it is drawn, in ticks. */
    static final long PERIOD_TICKS = 10L;
    /** How far from the player anything is drawn, in blocks. */
    static final double RADIUS = 48.0;
    /** The most specks of a room's edge drawn for one player at a time, nearest first. */
    static final int MOST_EDGE_SPECKS = 700;
    /** How near the player a room's edge is dotted in, in blocks. */
    static final double EDGE_RADIUS = 24.0;
    /** The selection's colour, as EZdebug+ draws it. */
    static final int SELECTED = 0xFF3B3B;
    private static final int COOLING = 0x3BD8FF, HEATING = 0xFF9A2E, EDGE = 0xF2F2F2;

    private final Plugin plugin;
    private final HvacRegistry registry;
    private final AirflowService airflow;
    private final LinkTool tool;
    private final BooleanSupplier on;
    private final BooleanSupplier replaced;
    /** Each viewer's glowing displays, by the block they're on. */
    private final Map<UUID, Map<BlockKey, BlockDisplay>> glows = new HashMap<>();
    private BukkitTask task;
    private long tick;

    /**
     * @param on       whether it is switched on ({@code ezdebug.enabled})
     * @param replaced whether EZdebug+ is installed and switched on, so this stands down
     */
    public EzDebug(Plugin plugin, HvacRegistry registry, AirflowService airflow, LinkTool tool, BooleanSupplier on,
                   BooleanSupplier replaced) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.tool = Objects.requireNonNull(tool, "tool");
        this.on = Objects.requireNonNull(on, "on");
        this.replaced = Objects.requireNonNull(replaced, "replaced");
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, PERIOD_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (UUID viewer : new ArrayList<>(glows.keySet())) clear(viewer);
    }

    /** Whether it is showing anything right now: on, and not replaced by EZdebug+. */
    public boolean active() {
        return on.getAsBoolean() && !replaced.getAsBoolean();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    /** How many glowing displays are up for a player: for tests and /hvac stats. */
    public int glowing(UUID viewer) {
        Map<BlockKey, BlockDisplay> mine = glows.get(viewer);
        return mine == null ? 0 : mine.size();
    }

    void refresh() {
        tick += PERIOD_TICKS;
        boolean active = active();
        Set<UUID> seen = new HashSet<>();
        if (active)
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                SystemId system = selected(player);
                if (system == null) continue;
                seen.add(player.getUniqueId());
                draw(player, system);
            }
        for (UUID viewer : new ArrayList<>(glows.keySet())) if (!seen.contains(viewer)) clear(viewer);
    }

    /** The system a player's link tool has selected, when they hold it and may use it; null otherwise. */
    private SystemId selected(Player player) {
        if (!player.hasPermission("ezvac.admin")) return null;
        var held = player.getInventory().getItemInMainHand();
        if (!tool.isTool(held)) return null;
        LinkTool.Selection selection = tool.selection(held);
        if (selection == null || selection.thermostat() == null) return null;
        Thermostat thermostat = registry.thermostatAt(selection.thermostat());
        return thermostat == null ? null : thermostat.system();
    }

    private void draw(Player player, SystemId system) {
        World world = player.getWorld();
        if (!world.getUID().equals(system.worldId())) {
            clear(player.getUniqueId());
            return;
        }
        Location eye = player.getLocation();
        Map<BlockKey, Integer> wanted = new HashMap<>();
        Set<BlockKey> leaks = new HashSet<>();
        Thermostat thermostat = registry.thermostatFor(system);
        OperatingMode mode = thermostat == null ? OperatingMode.IDLE : thermostat.mode();
        // Its equipment glows; its signs are framed; its vents are marked and puff while they work.
        for (EquipmentUnit unit : registry.equipmentFor(system))
            if (near(eye, unit.position(), RADIUS)) wanted.put(unit.position(), SELECTED);
        for (Thermostat each : registry.thermostats())
            if (each.system().equals(system) && near(eye, each.position(), RADIUS)) frame(player, each.position(), each.mini());
        for (SettingsPanel panel : registry.settingsPanels())
            if (system.equals(panel.system()) && near(eye, panel.position(), RADIUS)) frame(player, panel.position(), false);
        for (RpmMonitor monitor : registry.rpmMonitors())
            if (system.equals(monitor.system()) && near(eye, monitor.position(), RADIUS)) frame(player, monitor.position(), false);
        for (ClimateVent vent : registry.ventsFor(system)) {
            if (!near(eye, vent.position(), RADIUS)) continue;
            marker(player, vent.position());
            if (mode != OperatingMode.IDLE) puff(player, vent.position(), mode == OperatingMode.COOLING ? COOLING : HEATING);
        }
        // Its space: the edge dotted in near the player, the leaks glowing through walls.
        AirflowService.Space space = airflow.space(system);
        if (space != null) {
            for (long cell : space.openings()) {
                BlockKey at = new BlockKey(system.worldId(), AirflowService.unpackX(cell), AirflowService.unpackY(cell),
                        AirflowService.unpackZ(cell));
                if (near(eye, at, RADIUS)) leaks.add(at);
            }
            edge(player, space.edge());
        }
        show(player, wanted, leaks);
    }

    /** The specks of a room's edge nearest the player, faint, a little inside its walls. */
    private void edge(Player player, long[] edge) {
        Location eye = player.getLocation();
        List<long[]> near = new ArrayList<>();
        double most = EDGE_RADIUS * EDGE_RADIUS;
        for (long cell : edge) {
            double dx = AirflowService.unpackX(cell) + 0.5 - eye.getX(), dy = AirflowService.unpackY(cell) + 0.5 - eye.getY(),
                    dz = AirflowService.unpackZ(cell) + 0.5 - eye.getZ();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= most) near.add(new long[]{cell, (long) distance});
        }
        near.sort((a, b) -> Long.compare(a[1], b[1]));
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(EDGE), 0.55f);
        // Twinkling: a different share of the farther ones each time, so a big room isn't a solid fog.
        for (int i = 0; i < Math.min(MOST_EDGE_SPECKS, near.size()); i++) {
            long cell = near.get(i)[0];
            if (i > MOST_EDGE_SPECKS / 2 && (cell + tick / PERIOD_TICKS) % 2 != 0) continue;
            player.spawnParticle(Particle.DUST, AirflowService.unpackX(cell) + 0.5, AirflowService.unpackY(cell) + 0.5,
                    AirflowService.unpackZ(cell) + 0.5, 1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    /** Makes a player's glowing displays match what this draw wants: equipment copies and red leaks. */
    private void show(Player player, Map<BlockKey, Integer> equipment, Set<BlockKey> leaks) {
        Map<BlockKey, BlockDisplay> mine = glows.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        Set<BlockKey> wanted = new HashSet<>(equipment.keySet());
        wanted.addAll(leaks);
        for (Iterator<Map.Entry<BlockKey, BlockDisplay>> it = mine.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockKey, BlockDisplay> entry = it.next();
            if (wanted.contains(entry.getKey()) && entry.getValue().isValid()) continue;
            entry.getValue().remove();
            it.remove();
        }
        for (BlockKey at : wanted) {
            if (mine.containsKey(at)) continue;
            BlockDisplay display = equipment.containsKey(at) ? copy(at) : leak(at);
            if (display == null) continue;
            player.showEntity(plugin, display);
            mine.put(at, display);
        }
    }

    private void clear(UUID viewer) {
        Map<BlockKey, BlockDisplay> mine = glows.remove(viewer);
        if (mine != null) for (BlockDisplay display : mine.values()) display.remove();
    }

    /** A copy of a piece of equipment, a hair larger, glowing red. */
    private BlockDisplay copy(BlockKey at) {
        World world = at.world();
        if (world == null || !at.isChunkLoaded()) return null;
        Block block = world.getBlockAt(at.x(), at.y(), at.z());
        if (block.getType().isAir()) return null;
        return spawn(world, at, block.getBlockData(), 1.02f);
    }

    /** A glowing red pane of glass filling an opening the room leaks through. */
    private BlockDisplay leak(BlockKey at) {
        World world = at.world();
        if (world == null || !at.isChunkLoaded()) return null;
        return spawn(world, at, Material.RED_STAINED_GLASS.createBlockData(), 0.96f);
    }

    private BlockDisplay spawn(World world, BlockKey at, BlockData data, float scale) {
        float shift = (1f - scale) / 2f;
        return world.spawn(new Location(world, at.x(), at.y(), at.z()), BlockDisplay.class, display -> {
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setBlock(data);
            display.setGlowing(true);
            display.setGlowColorOverride(Color.fromRGB(SELECTED));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(new Transformation(new Vector3f(shift, shift, shift), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
    }

    private static boolean near(Location eye, BlockKey at, double radius) {
        double dx = eye.getX() - (at.x() + 0.5), dy = eye.getY() - (at.y() + 0.5), dz = eye.getZ() - (at.z() + 0.5);
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** A dotted red frame round a sign's board, or a box round a mini thermostat's button. */
    private static void frame(Player player, BlockKey at, boolean mini) {
        World world = at.world();
        if (world == null || !at.isChunkLoaded()) return;
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(SELECTED), 0.6f);
        Block block = world.getBlockAt(at.x(), at.y(), at.z());
        if (mini || !Tag.ALL_SIGNS.isTagged(block.getType())) {
            for (BoxFrame.Point point : BoxFrame.points())
                player.spawnParticle(Particle.DUST, at.x() + point.x(), at.y() + point.y(), at.z() + point.z(), 1,
                        0.0, 0.0, 0.0, 0.0, dust);
            return;
        }
        BlockData sign = block.getBlockData();
        SignFrame.Mount mount = sign instanceof WallSign ? SignFrame.Mount.WALL
                : sign instanceof WallHangingSign || sign instanceof HangingSign ? SignFrame.Mount.HANGING
                : SignFrame.Mount.STANDING;
        BlockFace facing = sign instanceof Directional directional ? directional.getFacing()
                : sign instanceof Rotatable rotatable ? rotatable.getRotation() : BlockFace.SOUTH;
        for (SignFrame.Point point : SignFrame.of(mount, facing.getDirection().getX(), facing.getDirection().getZ()))
            player.spawnParticle(Particle.DUST, at.x() + point.x(), at.y() + point.y(), at.z() + point.z(), 1,
                    0.0, 0.0, 0.0, 0.0, dust);
    }

    /** A bright red speck just above a vent, so every vent of the system can be found. */
    private static void marker(Player player, BlockKey at) {
        player.spawnParticle(Particle.DUST, at.x() + 0.5, at.y() + 1.05, at.z() + 0.5, 2, 0.08, 0.0, 0.08, 0.0,
                new Particle.DustOptions(Color.fromRGB(SELECTED), 1.4f));
    }

    /**
     * A working vent's puff, easy to see: a cloud on the vent and a short
     * column rising out of it, cyan while its system cools, orange while it heats.
     */
    static void puff(Player player, BlockKey at, int rgb) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), 1.8f);
        player.spawnParticle(Particle.DUST, at.x() + 0.5, at.y() + 0.7, at.z() + 0.5, 10, 0.3, 0.25, 0.3, 0.0, dust);
        Particle.DustOptions column = new Particle.DustOptions(Color.fromRGB(rgb), 1.2f);
        for (int i = 1; i <= 4; i++)
            player.spawnParticle(Particle.DUST, at.x() + 0.5, at.y() + 0.8 + i * 0.35, at.z() + 0.5, 2, 0.08, 0.05,
                    0.08, 0.0, column);
    }
}
