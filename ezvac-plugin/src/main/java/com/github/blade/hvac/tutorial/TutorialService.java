package com.github.blade.hvac.tutorial;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.ClimateVent;
import com.github.blade.hvac.model.EquipmentUnit;
import com.github.blade.hvac.model.SystemId;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.HvacRegistry;
import com.github.blade.hvac.tutorial.TutorialHut.Spot;
import com.github.blade.hvac.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Build your first system (BETA 1.0 Pilot 10): a guided tutorial in a world of
 * its own. The player is taken to the maintainer's hut on a floating
 * platform in an empty world, each on a plot of their own, in creative with
 * what they need; glowing ghost blocks show where the heat pump's dispenser
 * goes, then the thermostat's sign, then the vents, and last its casing
 * (last, since a closed casing hides the dispenser from /hvac create and the
 * link tool); each step is done when the real thing is there. Leaving (or finishing) puts the player
 * back exactly where they were, with their inventory and game mode; their
 * things are kept on disk meanwhile, so a crash or a restart loses nothing.
 * Nothing in it touches the server's own worlds.
 */
public final class TutorialService implements Listener {
    public static final String WORLD = "ezvac_tutorial";
    /** Where plots are: one every this many blocks along x, at this height. */
    static final int PLOT_SPACING = 96, PLOT_Y = 100;
    private static final int GLOW = 0x55FFFF;
    static final long SETTLE_MILLIS = 2000L;
    /** An opening the room leaks through: red glass glowing red, as EZdebug draws it. */
    private static final Material LEAK = Material.RED_STAINED_GLASS;
    private static final int LEAK_GLOW = 0xFF4040;

    /** Where a player is in the tutorial. */
    public enum Step { DISPENSER, HEAT_PUMP, THERMOSTAT, VENTS, LINK, CASING, DONE }

    private static final class Session {
        final UUID player;
        final int plot;
        final int originX, originY, originZ;
        Step step = Step.DISPENSER;
        final Map<BlockKey, BlockDisplay> ghosts = new LinkedHashMap<>();
        boolean outsideTold;
        boolean leaksTold;
        /**
         * When ghosts start: a player who has just arrived in the world isn't ready to be shown entities,
         * and one shown too early stays hidden until they cross into another chunk.
         */
        final long drawFrom = System.currentTimeMillis() + SETTLE_MILLIS;

        Session(UUID player, int plot) {
            this.player = player;
            this.plot = plot;
            this.originX = plot * PLOT_SPACING;
            this.originY = PLOT_Y;
            this.originZ = 0;
        }
    }

    private final HvacPlugin plugin;
    private final TutorialHut hut;
    private final File saves;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** Players whose unreadable save couldn't be moved aside: their things were already cleared once. */
    private final Set<UUID> unreadableLeft = new java.util.HashSet<>();
    private BukkitTask task;
    /** The tutorial world's deletion, 10 seconds after its last player left; cancelled if someone starts. */
    private BukkitTask deletion;
    static final long DELETE_AFTER_TICKS = 200L;

    public TutorialService(HvacPlugin plugin) throws IOException {
        this.plugin = plugin;
        try (InputStream input = plugin.getResource("tutorial/hut.txt")) {
            if (input == null) throw new IOException("tutorial/hut.txt is missing from the jar");
            this.hut = TutorialHut.load(input);
        }
        this.saves = new File(plugin.getDataFolder(), "tutorial");
    }

    public void start() {
        // A world left over from a crash or a kill goes now: every tutorial starts from a fresh one.
        if (Bukkit.getWorld(WORLD) == null) for (File folder : leftoverFolders()) deleteFolder(folder);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    /** Puts everyone back where they were (EZvac stopping). */
    public void stop() {
        if (task != null) task.cancel();
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) leave(player, false);
        }
        if (deletion != null) deletion.cancel();
        deleteWorld();
    }

    public boolean inTutorial(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    // ---- starting and leaving ----

    /** Takes a player to their own copy of the hut. False, and they're told why, when it can't. */
    public boolean begin(Player player) {
        if (inTutorial(player)) {
            player.sendMessage(Component.text(Lang.text("tutorial.already"),
                    NamedTextColor.YELLOW));
            return false;
        }
        World world;
        try {
            if (deletion != null) {
                deletion.cancel();
                deletion = null;
            }
            world = world();
        } catch (RuntimeException failed) {
            plugin.getLogger().log(Level.WARNING, "Could not make the tutorial world", failed);
            player.sendMessage(Component.text(Lang.text("tutorial.no-world"), NamedTextColor.RED));
            return false;
        }
        // Things still kept from an earlier tutorial (just rejoined, before they're given back) must never
        // be written over by a new save, nor laid over what the player carries now: it doesn't start.
        if (saveFile(player.getUniqueId()).isFile()) {
            player.sendMessage(Lang.message("tutorial.kept-from-earlier"));
            plugin.getLogger().warning(player.getName() + " has things kept from an earlier tutorial in "
                    + saveFile(player.getUniqueId()) + "; the tutorial didn't start over them.");
            return false;
        }
        if (!save(player)) {
            player.sendMessage(Lang.message("tutorial.not-safe"));
            return false;
        }
        Session session = new Session(player.getUniqueId(), freePlot());
        sessions.put(player.getUniqueId(), session);
        clearPlot(world, session);
        place(world, session);
        Spot spawn = hut.spawn();
        Location arrive = at(world, session, spawn).add(0.5, 0, 0.5);
        arrive.setYaw(yaw(hut.spawnFacing()));
        if (!player.teleport(arrive)) {
            // Another plugin stopped the trip: nothing of theirs was touched, so nothing needs giving back.
            sessions.remove(player.getUniqueId());
            clearPlot(world, session);
            if (!saveFile(player.getUniqueId()).delete())
                plugin.getLogger().warning("Could not remove " + saveFile(player.getUniqueId()) + " after a cancelled tutorial");
            deleteSoonIfEmpty();
            player.sendMessage(Lang.message("tutorial.trip-stopped"));
            return false;
        }
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        player.getInventory().addItem(new ItemStack(Material.DISPENSER), new ItemStack(Material.STONE_BRICKS, 24),
                new ItemStack(Material.GLASS), new ItemStack(Material.OAK_SIGN), new ItemStack(Material.IRON_TRAPDOOR, 4));
        player.setHealth(Math.min(player.getHealth(), 20.0));
        player.setFoodLevel(20);
        player.showTitle(Title.title(Component.text(Lang.text("tutorial.title"), NamedTextColor.GOLD),
                Component.text(Lang.text("tutorial.subtitle"), NamedTextColor.GRAY)));
        say(player, "tutorial.welcome");
        announce(player, session);
        plugin.getLogger().info(player.getName() + " started the tutorial (plot " + session.plot + ").");
        return true;
    }

    /** Back where they were, with their things; the plot is cleared. */
    public void leave(Player player, boolean told) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) {
            removeGhosts(session);
            World world = Bukkit.getWorld(WORLD);
            if (world != null) clearPlot(world, session);
        }
        boolean restored = restore(player);
        deleteSoonIfEmpty();
        if (told && restored) say(player, "tutorial.back");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session == null) return;
        removeGhosts(session);
        World world = Bukkit.getWorld(WORLD);
        if (world != null) clearPlot(world, session);
        // Their things stay on disk and come back when they next join.
        deleteSoonIfEmpty();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!saveFile(player.getUniqueId()).isFile()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !inTutorial(player) && saveFile(player.getUniqueId()).isFile()) {
                if (restore(player))
                    say(player, "tutorial.back-while-away");
            }
        }, 5L);
    }

    private int freePlot() {
        int plot = 0;
        while (true) {
            int at = plot;
            if (sessions.values().stream().noneMatch(session -> session.plot == at)) return plot;
            plot++;
        }
    }

    // ---- the steps ----

    private void tick() {
        World world = Bukkit.getWorld(WORLD);
        if (world == null || sessions.isEmpty()) return;
        for (Session session : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(session.player);
            if (player == null) continue;
            if (!player.getWorld().equals(world)) {
                // Teleported away some other way: their things go back with them.
                leave(player, true);
                continue;
            }
            try {
                if (System.currentTimeMillis() >= session.drawFrom) advance(player, world, session);
            } catch (RuntimeException failed) {
                plugin.getLogger().log(Level.WARNING, "The tutorial step for " + player.getName() + " failed", failed);
            }
        }
    }

    private void advance(Player player, World world, Session session) {
        HvacRegistry registry = plugin.registry();
        BlockKey dispenser = key(world, session, hut.dispenser());
        BlockKey sign = key(world, session, hut.sign());
        switch (session.step) {
            case DISPENSER -> {
                Map<BlockKey, BlockData> ghost = new LinkedHashMap<>();
                if (!facesUp(dispenser.location().getBlock()))
                    ghost.put(dispenser, Bukkit.createBlockData("minecraft:dispenser[facing=up]"));
                showGhosts(player, world, session, ghost);
                if (ghost.isEmpty()) {
                    done(player, "tutorial.done.dispenser");
                    next(player, session, Step.HEAT_PUMP);
                }
            }
            case HEAT_PUMP -> {
                showGhosts(player, world, session, Map.of(dispenser, Bukkit.createBlockData("minecraft:dispenser[facing=up]")));
                if (registry.equipmentAt(dispenser) != null) {
                    done(player, "tutorial.done.heat-pump");
                    next(player, session, Step.THERMOSTAT);
                }
            }
            case THERMOSTAT -> {
                showGhosts(player, world, session,
                        Map.of(sign, Bukkit.createBlockData("minecraft:oak_wall_sign[facing=" + hut.signFacing() + "]")));
                if (registry.thermostatAt(sign) != null) {
                    done(player, "tutorial.done.thermostat");
                    next(player, session, Step.VENTS);
                }
            }
            case VENTS -> {
                Map<BlockKey, BlockData> ghost = new LinkedHashMap<>();
                for (Spot vent : hut.vents()) {
                    BlockKey at = key(world, session, vent);
                    if (at.location().getBlock().getType() != Material.IRON_TRAPDOOR)
                        ghost.put(at, Bukkit.createBlockData("minecraft:iron_trapdoor[half=top,facing=north]"));
                }
                showGhosts(player, world, session, ghost);
                if (ghost.isEmpty()) {
                    done(player, "tutorial.done.vents");
                    player.performCommand("hvac link-tool");
                    int tool = player.getInventory().first(Material.STICK);
                    if (tool >= 0 && tool < 9) player.getInventory().setHeldItemSlot(tool);
                    next(player, session, Step.LINK);
                }
            }
            case LINK -> {
                Thermostat thermostat = registry.thermostatAt(sign);
                if (thermostat == null) {
                    showGhosts(player, world, session, Map.of());
                    return;
                }
                SystemId system = thermostat.system();
                Map<BlockKey, BlockData> ghost = new LinkedHashMap<>();
                ghost.put(sign, Bukkit.createBlockData("minecraft:oak_wall_sign[facing=" + hut.signFacing() + "]"));
                EquipmentUnit unit = registry.equipmentAt(dispenser);
                if (unit == null || !unit.system().equals(system))
                    ghost.put(dispenser, Bukkit.createBlockData("minecraft:dispenser[facing=up]"));
                for (Spot vent : hut.vents()) {
                    BlockKey at = key(world, session, vent);
                    ClimateVent linked = registry.ventAt(at);
                    if (linked == null || !linked.system().equals(system))
                        ghost.put(at, Bukkit.createBlockData("minecraft:iron_trapdoor[half=top,facing=north]"));
                }
                boolean all = ghost.size() == 1;
                // With a vent linked the room's air is mapped, and the link tool shows where it leaks, in red.
                boolean ventLinked = ghost.size() < hut.vents().size() + (ghost.containsKey(dispenser) ? 2 : 1);
                if (ventLinked) {
                    Set<BlockKey> leaks = leaks(system);
                    // Told once the room's air is mapped, as it is: leaking, or sealed by a closed door.
                    if (!session.leaksTold && leaks != null) {
                        session.leaksTold = true;
                        if (!leaks.isEmpty())
                            say(player, "tutorial.leaks");
                        else
                            say(player, "tutorial.sealed");
                        hint(player, "tutorial.leaks-hint");
                    }
                    addLeaks(ghost, leaks);
                }
                showGhosts(player, world, session, ghost);
                if (all) {
                    done(player, "tutorial.done.linked");
                    next(player, session, Step.CASING);
                }
            }
            case CASING -> {
                Map<BlockKey, BlockData> missing = missingCasing(world, session);
                Map<BlockKey, BlockData> shown = new LinkedHashMap<>(missing);
                addLeaks(shown, leaksOf(world, session));
                showGhosts(player, world, session, shown);
                if (missing.isEmpty()) {
                    done(player, "tutorial.done.casing");
                    session.step = Step.DONE;
                    finish(player);
                }
            }
            case DONE -> {
                Map<BlockKey, BlockData> ghost = new LinkedHashMap<>();
                addLeaks(ghost, leaksOf(world, session));
                showGhosts(player, world, session, ghost);
            }
        }
    }

    /**
     * Where the system's room leaks air, as EZdebug shows it with the link tool: null until its air is mapped.
     * The tutorial draws them itself, tool or not, so a player who puts the tool away still sees them.
     */
    private Set<BlockKey> leaks(SystemId system) {
        com.github.blade.hvac.service.AirflowService.Space space = plugin.airflow().space(system);
        if (space == null) return null;
        Set<BlockKey> out = new java.util.HashSet<>();
        for (long cell : space.openings())
            out.add(new BlockKey(system.worldId(), com.github.blade.hvac.service.AirflowService.unpackX(cell),
                    com.github.blade.hvac.service.AirflowService.unpackY(cell),
                    com.github.blade.hvac.service.AirflowService.unpackZ(cell)));
        return out;
    }

    private Set<BlockKey> leaksOf(World world, Session session) {
        Thermostat thermostat = plugin.registry().thermostatAt(key(world, session, hut.sign()));
        return thermostat == null ? null : leaks(thermostat.system());
    }

    private static void addLeaks(Map<BlockKey, BlockData> ghost, Set<BlockKey> leaks) {
        if (leaks == null) return;
        for (BlockKey leak : leaks) ghost.putIfAbsent(leak, LEAK.createBlockData());
    }

    private void next(Player player, Session session, Step step) {
        session.step = step;
        announce(player, session);
    }

    private static boolean facesUp(Block block) {
        return block.getType() == Material.DISPENSER && block.getBlockData() instanceof Directional facing
                && facing.getFacing() == org.bukkit.block.BlockFace.UP;
    }

    /**
     * The casing cells still empty. Any solid block counts: the casing is how a unit is meant to look, not a
     * rule, so whatever the player builds it from is theirs. The output cell above the dispenser is left
     * alone: the heat pump's water is there by now.
     */
    private Map<BlockKey, BlockData> missingCasing(World world, Session session) {
        Map<BlockKey, BlockData> missing = new LinkedHashMap<>();
        Spot window = hut.window();
        for (Spot cell : hut.casing()) {
            BlockKey at = key(world, session, cell);
            if (!at.location().getBlock().getType().isSolid())
                missing.put(at, cell.equals(window) ? Material.GLASS.createBlockData() : Material.STONE_BRICKS.createBlockData());
        }
        return missing;
    }

    private void announce(Player player, Session session) {
        switch (session.step) {
            case DISPENSER -> {
                say(player, "tutorial.step.dispenser");
                hint(player, "tutorial.step.dispenser-hint");
            }
            case HEAT_PUMP -> {
                say(player, "tutorial.step.heat-pump");
                command(player, "/hvac create heatpump");
                hint(player, "tutorial.step.heat-pump-hint");
            }
            case THERMOSTAT -> {
                say(player, "tutorial.step.thermostat");
                command(player, "/hvac create thermostat Hut 75");
            }
            case VENTS -> say(player, "tutorial.step.vents");
            case LINK -> {
                say(player, "tutorial.step.link");
                hint(player, "tutorial.step.link-hint");
            }
            case CASING -> {
                say(player, "tutorial.step.casing");
                hint(player, "tutorial.step.casing-hint");
            }
            case DONE -> { }
        }
    }

    private void finish(Player player) {
        chime(player, true);
        player.showTitle(Title.title(Component.text(Lang.text("tutorial.done-title"), NamedTextColor.GREEN),
                Component.text(Lang.text("tutorial.done-subtitle"), NamedTextColor.GRAY)));
        say(player, "tutorial.finished");
        player.sendMessage(Component.text(Lang.text("tutorial.back-button"), NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/hvac tutorial leave"))
                .hoverEvent(HoverEvent.showText(Component.text(Lang.text("tutorial.back-hover")))));
        plugin.getLogger().info(player.getName() + " finished the tutorial.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || session.step != Step.THERMOSTAT || session.outsideTold) return;
        if (!org.bukkit.Tag.WALL_SIGNS.isTagged(event.getBlock().getType())) return;
        World world = event.getBlock().getWorld();
        BlockKey placed = BlockKey.of(event.getBlock().getLocation());
        if (placed.equals(key(world, session, hut.sign()))) return;
        // The wall block's other face is the outside: the thermostat belongs inside.
        session.outsideTold = true;
        hint(event.getPlayer(), "tutorial.thermostat-inside");
    }

    // ---- ghosts ----

    private void showGhosts(Player player, World world, Session session, Map<BlockKey, BlockData> wanted) {
        session.ghosts.entrySet().removeIf(entry -> {
            if (wanted.containsKey(entry.getKey())) return false;
            entry.getValue().remove();
            return true;
        });
        for (Map.Entry<BlockKey, BlockData> entry : wanted.entrySet()) {
            if (session.ghosts.containsKey(entry.getKey())) continue;
            BlockKey at = entry.getKey();
            BlockDisplay display = world.spawn(new Location(world, at.x(), at.y(), at.z()), BlockDisplay.class, ghost -> {
                ghost.setPersistent(false);
                ghost.setVisibleByDefault(false);
                ghost.setBlock(entry.getValue());
                ghost.setGlowing(true);
                ghost.setGlowColorOverride(Color.fromRGB(entry.getValue().getMaterial() == LEAK ? LEAK_GLOW : GLOW));
                ghost.setBrightness(new Display.Brightness(15, 15));
                boolean there = !at.location().getBlock().getType().isAir();
                float scale = there ? 1.04f : 0.9f, shift = (1f - scale) / 2f;
                ghost.setTransformation(new Transformation(new Vector3f(shift, shift, shift), new AxisAngle4f(),
                        new Vector3f(scale, scale, scale), new AxisAngle4f()));
            });
            player.showEntity(plugin, display);
            // Shown again a second later: a show the client missed is never retried by the server on its own.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!display.isValid() || !player.isOnline() || !player.getWorld().equals(display.getWorld())) return;
                player.hideEntity(plugin, display);
                player.showEntity(plugin, display);
            }, 20L);
            session.ghosts.put(at, display);
        }
    }

    private static void removeGhosts(Session session) {
        for (BlockDisplay display : session.ghosts.values()) display.remove();
        session.ghosts.clear();
    }

    // ---- deleting the world ----

    /**
     * The tutorial world is deleted 10 seconds after the last player in it leaves: a player can stay
     * as long as they like, and /hvac tutorial (or the setup) always starts from a fresh world.
     */
    private void deleteSoonIfEmpty() {
        if (!sessions.isEmpty() || Bukkit.getWorld(WORLD) == null || !plugin.isEnabled()) return;
        if (deletion != null) deletion.cancel();
        deletion = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            deletion = null;
            if (sessions.isEmpty()) deleteWorld();
        }, DELETE_AFTER_TICKS);
    }

    /** Unloads the tutorial world, sending anyone still in it to the main world, and deletes its folder. */
    private void deleteWorld() {
        World world = Bukkit.getWorld(WORLD);
        if (world == null) return;
        File folder = world.getWorldFolder();
        {
            World main = Bukkit.getWorlds().get(0);
            for (Player stray : new ArrayList<>(world.getPlayers())) stray.teleport(main.getSpawnLocation());
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().warning("Could not unload " + WORLD + "; it will be deleted at the next start.");
                return;
            }
        }
        deleteFolder(folder);
    }

    /** Where a tutorial world left by a crash can be: beside the main world, or in its dimensions folder. */
    private static List<File> leftoverFolders() {
        List<File> out = new ArrayList<>();
        out.add(new File(Bukkit.getWorldContainer(), WORLD));
        if (!Bukkit.getWorlds().isEmpty())
            out.add(new File(Bukkit.getWorlds().get(0).getWorldFolder(), "dimensions/minecraft/" + WORLD));
        return out;
    }

    /** Deletes the tutorial world's folder, and only ever a folder named ezvac_tutorial. */
    private void deleteFolder(File folder) {
        if (!folder.isDirectory() || !folder.getName().equals(WORLD)) return;
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(folder.toPath())) {
            for (java.nio.file.Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList())
                java.nio.file.Files.deleteIfExists(path);
            plugin.getLogger().info("Deleted the tutorial world.");
        } catch (IOException | RuntimeException failed) {
            plugin.getLogger().log(Level.WARNING, "Could not delete the tutorial world's folder " + folder, failed);
        }
    }

    // ---- the world and the hut ----

    private World world() {
        World world = Bukkit.getWorld(WORLD);
        if (world != null) return world;
        world = new WorldCreator(WORLD).generator(new Empty()).biomeProvider(new Plains()).generateStructures(false)
                .createWorld();
        if (world == null) throw new IllegalStateException("the server didn't make " + WORLD);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setTime(6000L);
        world.setStorm(false);
        world.setSpawnLocation(0, PLOT_Y + 2, 0);
        return world;
    }

    /** No ground at all: the hut floats. */
    private static final class Empty extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() { return false; }
        @Override
        public boolean shouldGenerateSurface() { return false; }
        @Override
        public boolean shouldGenerateCaves() { return false; }
        @Override
        public boolean shouldGenerateDecorations() { return false; }
        @Override
        public boolean shouldGenerateMobs() { return false; }
        @Override
        public boolean shouldGenerateStructures() { return false; }
    }

    /** Plains everywhere: a mild climate the hut can be heated in. */
    private static final class Plains extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) { return Biome.PLAINS; }
        @Override
        public List<Biome> getBiomes(WorldInfo info) { return List.of(Biome.PLAINS); }
    }

    private void place(World world, Session session) {
        for (int y = 0; y < hut.height(); y++)
            for (int z = 0; z < hut.length(); z++)
                for (int x = 0; x < hut.width(); x++) {
                    if (hut.isAir(x, y, z)) continue;
                    try {
                        world.getBlockAt(session.originX + x, session.originY + y, session.originZ + z)
                                .setBlockData(Bukkit.createBlockData(hut.stateAt(x, y, z)), false);
                    } catch (IllegalArgumentException unknown) {
                        // A block this Minecraft doesn't have: left out, the rest still stands.
                    }
                }
    }

    /** Clears a plot and forgets every EZvac device in it, so it starts afresh and leaves nothing behind. */
    private void clearPlot(World world, Session session) {
        int minX = session.originX - 4, minY = session.originY - 4, minZ = session.originZ - 4;
        int maxX = session.originX + hut.width() + 4, maxY = session.originY + hut.height() + 4,
                maxZ = session.originZ + hut.length() + 4;
        BlockData air = Material.AIR.createBlockData();
        for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++)
                for (int x = minX; x <= maxX; x++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) block.setBlockData(air, false);
                }
        // The devices whose blocks just went are forgotten as EZvac forgets any broken without an event,
        // now rather than next cycle, so the next player on this plot starts with none.
        HvacRegistry.PruneResult pruned = plugin.registry().pruneMissingLoadedDevices();
        for (SystemId system : pruned.invalidatedSystems()) plugin.airflow().invalidate(system);
        for (ClimateUnit unit : pruned.removedUnits()) plugin.units().release(unit);
        plugin.requestImmediateSync();
    }

    private Location at(World world, Session session, Spot spot) {
        return new Location(world, session.originX + spot.x(), session.originY + spot.y(), session.originZ + spot.z());
    }

    private BlockKey key(World world, Session session, Spot spot) {
        return new BlockKey(world.getUID(), session.originX + spot.x(), session.originY + spot.y(),
                session.originZ + spot.z());
    }

    static float yaw(String facing) {
        return switch (facing) {
            case "south" -> 0f;
            case "west" -> 90f;
            case "east" -> -90f;
            default -> 180f;
        };
    }

    // ---- the player's things ----

    private File saveFile(UUID id) {
        return new File(saves, id + ".yml");
    }

    /** Keeps where a player is, their game mode and their things on disk before the tutorial takes them. */
    private boolean save(Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        Location from = player.getLocation();
        yaml.set("world", from.getWorld().getUID().toString());
        yaml.set("x", from.getX());
        yaml.set("y", from.getY());
        yaml.set("z", from.getZ());
        yaml.set("yaw", from.getYaw());
        yaml.set("pitch", from.getPitch());
        yaml.set("gamemode", player.getGameMode().name());
        yaml.set("contents", java.util.Arrays.asList(player.getInventory().getContents()));
        yaml.set("health", player.getHealth());
        yaml.set("food", player.getFoodLevel());
        yaml.set("saturation", player.getSaturation());
        yaml.set("flying", player.isFlying());
        try {
            if (!saves.isDirectory() && !saves.mkdirs()) throw new IOException("couldn't make " + saves);
            // Written beside it and moved into place, so a crash mid-write can't leave half a file: the
            // file exists to survive exactly that.
            File file = saveFile(player.getUniqueId());
            File partial = new File(saves, file.getName() + ".tmp");
            yaml.save(partial);
            try {
                java.nio.file.Files.move(partial.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                java.nio.file.Files.move(partial.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException failed) {
            plugin.getLogger().log(Level.WARNING, "Could not keep " + player.getName() + "'s things for the tutorial", failed);
            return false;
        }
    }

    /**
     * Puts a player back from their saved place and things, and forgets the file. True when it did, or
     * there was nothing to give back. A file that can't be read is never treated as "no things": it is
     * kept aside for an admin and the player is told where their things are; they leave the tutorial's
     * creative kit and game mode behind, at spawn. Only ever called for a player leaving the tutorial.
     */
    private boolean restore(Player player) {
        File file = saveFile(player.getUniqueId());
        if (!file.isFile()) return true;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            if (!yaml.isList("contents")) throw new org.bukkit.configuration.InvalidConfigurationException("no inventory in it");
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException unreadable) {
            File aside = new File(saves, file.getName() + ".unreadable-" + System.currentTimeMillis());
            boolean kept = file.renameTo(aside);
            if (!kept) {
                // A file another program holds open can't be renamed: a copy beside it keeps it just as well.
                try {
                    java.nio.file.Files.copy(file.toPath(), aside.toPath());
                    kept = file.delete();
                } catch (IOException copyFailed) {
                    unreadable.addSuppressed(copyFailed);
                }
            }
            // Still in place, it would be read again at every join, and each time empty what they carry then.
            if (!kept && !unreadableLeft.add(player.getUniqueId())) return false;
            plugin.getLogger().log(Level.SEVERE, "Could not read " + player.getName() + "'s things from the tutorial ("
                    + file + "); " + (kept ? "kept as " + aside.getName() : "left in place") + " for an admin", unreadable);
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            if (player.getWorld().getName().equals(WORLD)) player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.sendMessage(Lang.message("tutorial.unreadable"));
            return false;
        }
        World world = null;
        try {
            world = Bukkit.getWorld(UUID.fromString(yaml.getString("world", "")));
        } catch (IllegalArgumentException ignored) {
            // a hand-edited file: the main world, below
        }
        if (world == null) world = Bukkit.getWorlds().get(0);
        Location back = new Location(world, yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"),
                (float) yaml.getDouble("yaw"), (float) yaml.getDouble("pitch"));
        player.teleport(back);
        try {
            player.setGameMode(GameMode.valueOf(yaml.getString("gamemode", "SURVIVAL")));
        } catch (IllegalArgumentException ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        List<?> contents = yaml.getList("contents", List.of());
        ItemStack[] items = new ItemStack[player.getInventory().getSize()];
        for (int i = 0; i < Math.min(items.length, contents.size()); i++)
            items[i] = contents.get(i) instanceof ItemStack stack ? stack : null;
        player.getInventory().setContents(items);
        double health = yaml.getDouble("health", 20.0);
        player.setHealth(Math.max(0.5, Math.min(health, player.getMaxHealth())));
        player.setFoodLevel(yaml.getInt("food", 20));
        player.setSaturation((float) yaml.getDouble("saturation", 5.0));
        if (player.getAllowFlight()) player.setFlying(yaml.getBoolean("flying", false));
        if (!file.delete()) plugin.getLogger().warning("Could not remove " + file + " after giving its things back");
        return true;
    }

    // ---- telling ----

    /** A line of the tutorial, by its key. */
    private static void say(Player player, String key) {
        player.sendMessage(Component.text(Lang.text(key), NamedTextColor.WHITE));
    }

    private static void hint(Player player, String key) {
        player.sendMessage(Component.text(Lang.text(key), NamedTextColor.GRAY));
    }

    private static void command(Player player, String command) {
        player.sendMessage(Component.text("  " + command, NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(Lang.text("tutorial.click-to-chat")))));
    }

    private void done(Player player, String key) {
        chime(player, false);
        player.sendMessage(Component.text("✔ " + Lang.text(key), NamedTextColor.GREEN));
    }

    /** A short rising tune; a longer one at the end. */
    private void chime(Player player, boolean end) {
        float[] notes = end ? new float[]{0.84f, 1.06f, 1.26f, 1.68f} : new float[]{1.0f, 1.26f, 1.5f};
        for (int i = 0; i < notes.length; i++) {
            float pitch = notes[i];
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, pitch);
            }, i * 3L);
        }
    }
}
