package com.github.blade.hvac.service;

import com.github.blade.hvac.model.BlockKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Draws {@link Panel}s on sign faces (BETA 1.0 Pilot 7, the new look): a
 * display entity for each part, laid flat on the board in front of the sign,
 * and an invisible interaction box over each button, which is how a button
 * is clicked. Only parts that changed are sent again; bars move smoothly to a
 * new fill, as the client interpolates them, and icons that turn are turned
 * a quarter at a time the same way, for players near enough to see. Nothing
 * is saved with the world. Main thread only.
 */
public final class PanelScreens {
    /**
     * A text display's background, for a single space at one times the size:
     * 5 font pixels wide from -2, 10 tall from its foot (seen in game). Fills are such a space,
     * stretched.
     */
    static final double FILL_LEFT = -2.0, FILL_W = 5.0, FILL_H = 10.0;
    /** Where a line's glyphs start above a text display's foot, in font pixels. */
    static final double GLYPH_TOP = 9.0;
    /** A font pixel, in blocks, at a display scale of one. */
    static final double FONT_PIXEL = 0.025;
    /** How far in front of the board each kind of part floats, in blocks, so none flickers through another. */
    static final float BOARD_Z = 0.0f, FILL_Z = 0.003f, BAR_Z = 0.005f, TEXT_Z = 0.008f, ICON_Z = 0.012f;
    /** How long a bar takes to move to its new fill, in ticks. */
    static final int BAR_TICKS = 12;
    /** How near a player must be for icons to turn, in blocks. */
    static final double ANIMATE_RANGE = 24.0;

    /** A part's entity and what it showed. */
    private record Drawn(UUID entity, Panel.Part part, UUID label) {}

    /** A screen: where its board is, its parts, and its buttons' boxes. */
    private static final class Shown {
        final Panel.Size size;
        final ScreenPlacement.Mount mount;
        final ScreenPlacement.Spot centre;
        final double facingX, facingZ;
        final Map<String, Drawn> parts = new HashMap<>();
        final Map<UUID, String> hits = new HashMap<>();
        /** Each turning icon's angle in quarter turns, and the tick it turns next. */
        final Map<String, long[]> turning = new HashMap<>();

        Shown(Panel.Size size, ScreenPlacement.Mount mount, ScreenPlacement.Spot centre, double facingX, double facingZ) {
            this.size = size;
            this.mount = mount;
            this.centre = centre;
            this.facingX = facingX;
            this.facingZ = facingZ;
        }

        double pixel() { return mount.scale() / 40.0; }
    }

    /** A button clicked: which screen, and what it does. */
    public record Hit(BlockKey position, String action) {}

    private final Plugin plugin;
    private final NamespacedKey tag;
    private final Map<BlockKey, Shown> screens = new HashMap<>();
    private final Map<UUID, BlockKey> hitOwners = new HashMap<>();

    public PanelScreens(Plugin plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "screen");
    }

    /**
     * Shows a panel in front of a sign (or a mini thermostat's button), putting
     * it up first if it isn't there; parts that didn't change aren't sent.
     */
    public void show(BlockKey position, Block block, ScreenPlacement.Mount mount, double facingX, double facingZ,
                     Panel panel) {
        World world = block.getWorld();
        Shown shown = screens.get(position);
        if (shown != null && (shown.size != panel.size() || shown.mount != mount || !intact(world, shown))) {
            take(world, shown);
            shown = null;
        }
        if (shown == null) {
            shown = new Shown(panel.size(), mount, ScreenPlacement.centre(mount, facingX, facingZ), facingX, facingZ);
            screens.put(position, shown);
            spawnBoard(block, position, shown);
        }
        Map<String, Panel.Part> wanted = new HashMap<>();
        for (Panel.Part part : panel.parts()) wanted.put(part.id(), part);
        for (Iterator<Map.Entry<String, Drawn>> it = shown.parts.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Drawn> entry = it.next();
            if (entry.getKey().equals("~board") || wanted.containsKey(entry.getKey())) continue;
            remove(world, shown, entry.getValue());
            shown.turning.remove(entry.getKey());
            it.remove();
        }
        for (Panel.Part part : panel.parts()) {
            Drawn before = shown.parts.get(part.id());
            if (before != null && before.part().equals(part)) continue;
            if (before != null && update(world, shown, before, part)) continue;
            if (before != null) remove(world, shown, before);
            shown.parts.put(part.id(), spawn(block, position, shown, part));
        }
    }

    // ---- Drawing ----

    private void spawnBoard(Block block, BlockKey position, Shown shown) {
        Panel.Fill board = new Panel.Fill("~board", 0, 0, shown.size.width, shown.size.height, Panel.BOARD);
        UUID id = text(block, position, shown, Component.text(" "), board.argb(), false,
                fillTransform(shown, board.x(), board.y(), board.w(), board.h(), BOARD_Z));
        shown.parts.put("~board", new Drawn(id, board, null));
    }

    private Drawn spawn(Block block, BlockKey position, Shown shown, Panel.Part part) {
        return switch (part) {
            case Panel.Fill f -> new Drawn(text(block, position, shown, Component.text(" "), f.argb(), false,
                    fillTransform(shown, f.x(), f.y(), f.w(), f.h(), FILL_Z)), f, null);
            case Panel.Label l -> new Drawn(text(block, position, shown, l.text(), 0, true, labelTransform(shown, l)), l, null);
            case Panel.Bar b -> {
                UUID track = text(block, position, shown, Component.text(" "), b.trackArgb(), false,
                        fillTransform(shown, b.x(), b.y(), b.w(), b.h(), FILL_Z));
                UUID fill = text(block, position, shown, Component.text(" "), b.argb(), false, barTransform(shown, b));
                yield new Drawn(fill, b, track);
            }
            case Panel.Tick t -> new Drawn(text(block, position, shown, Component.text(" "), t.argb(), false,
                    tickTransform(shown, t)), t, null);
            case Panel.Icon i -> {
                shown.turning.remove(i.id());
                yield new Drawn(icon(block, position, shown, i), i, null);
            }
            case Panel.Button b -> {
                UUID face = text(block, position, shown, Component.text(" "), b.argb(), false,
                        fillTransform(shown, b.x(), b.y(), b.w(), b.h(), FILL_Z));
                Panel.Label label = buttonLabel(b);
                UUID text = text(block, position, shown, label.text(), 0, true, labelTransform(shown, label));
                UUID hit = hitBox(block, position, shown, b);
                shown.hits.put(hit, b.action());
                hitOwners.put(hit, position);
                yield new Drawn(face, b, text);
            }
        };
    }

    /** A part changed in a way its entity can show without being put up again: true when done. */
    private boolean update(World world, Shown shown, Drawn before, Panel.Part part) {
        if (part.getClass() != before.part().getClass()) return false;
        Entity entity = world.getEntity(before.entity());
        if (entity == null || !entity.isValid()) return false;
        switch (part) {
            case Panel.Fill f -> {
                Panel.Fill was = (Panel.Fill) before.part();
                if (was.x() != f.x() || was.y() != f.y() || was.w() != f.w() || was.h() != f.h()) return false;
                ((TextDisplay) entity).setBackgroundColor(argb(f.argb()));
            }
            case Panel.Label l -> {
                Panel.Label was = (Panel.Label) before.part();
                TextDisplay display = (TextDisplay) entity;
                if (!was.text().equals(l.text())) display.text(l.text());
                display.setTransformation(labelTransform(shown, l));
            }
            case Panel.Bar b -> {
                Panel.Bar was = (Panel.Bar) before.part();
                if (was.x() != b.x() || was.y() != b.y() || was.w() != b.w() || was.h() != b.h()) return false;
                TextDisplay fill = (TextDisplay) entity;
                if (was.argb() != b.argb()) fill.setBackgroundColor(argb(b.argb()));
                // The client moves the fill there over the next ticks: a smooth bar.
                fill.setInterpolationDelay(0);
                fill.setInterpolationDuration(BAR_TICKS);
                fill.setTransformation(barTransform(shown, b));
            }
            case Panel.Tick t -> {
                Panel.Tick was = (Panel.Tick) before.part();
                if (was.cx() != t.cx() || was.cy() != t.cy() || was.r() != t.r() || was.angle() != t.angle()
                        || was.len() != t.len() || was.thick() != t.thick()) return false;
                ((TextDisplay) entity).setBackgroundColor(argb(t.argb()));
            }
            case Panel.Icon i -> {
                Panel.Icon was = (Panel.Icon) before.part();
                if (was.cx() != i.cx() || was.cy() != i.cy() || was.size() != i.size() || was.item() != i.item()
                        || was.dim() != i.dim()) return false;
                if (i.turnsPerSecond() <= 0.0) shown.turning.remove(i.id());
            }
            case Panel.Button b -> {
                Panel.Button was = (Panel.Button) before.part();
                if (was.x() != b.x() || was.y() != b.y() || was.w() != b.w() || was.h() != b.h()) return false;
                ((TextDisplay) entity).setBackgroundColor(argb(b.argb()));
                Entity text = before.label() == null ? null : world.getEntity(before.label());
                if (!(text instanceof TextDisplay display)) return false;
                Panel.Label label = buttonLabel(b);
                display.text(label.text());
                display.setTransformation(labelTransform(shown, label));
                for (Map.Entry<UUID, String> hit : shown.hits.entrySet())
                    if (hit.getValue().equals(was.action())) hit.setValue(b.action());
            }
        }
        shown.parts.put(part.id(), new Drawn(before.entity(), part, before.label()));
        return true;
    }

    private static Panel.Label buttonLabel(Panel.Button b) {
        return new Panel.Label(b.id() + "~text", b.x(), (int) Math.round(b.y() + (b.h() - 7 * Panel.SMALL) / 2.0), b.w(),
                Component.text(b.label(), TextColor.color(b.textRgb())), Panel.SMALL, Panel.Align.CENTER);
    }

    private UUID text(Block block, BlockKey position, Shown shown, Component text, int background, boolean shadow,
                      Transformation transformation) {
        return block.getWorld().spawn(at(block, shown), TextDisplay.class, created -> {
            tag(created, position);
            created.setBillboard(Display.Billboard.FIXED);
            created.setAlignment(TextDisplay.TextAlignment.LEFT);
            created.setLineWidth(400);
            created.setDefaultBackground(false);
            created.setBackgroundColor(argb(background));
            created.setShadowed(shadow);
            created.setBrightness(new Display.Brightness(15, 15));
            created.setTransformation(transformation);
            created.text(text);
        }).getUniqueId();
    }

    private UUID icon(Block block, BlockKey position, Shown shown, Panel.Icon icon) {
        return block.getWorld().spawn(at(block, shown), ItemDisplay.class, created -> {
            tag(created, position);
            created.setItemStack(new ItemStack(icon.item()));
            created.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            created.setBrightness(icon.dim() ? new Display.Brightness(4, 4) : new Display.Brightness(15, 15));
            created.setTransformation(iconTransform(shown, icon, 0));
        }).getUniqueId();
    }

    /** A button's box: invisible, as big as the button, standing in the board's plane where the button is. */
    private UUID hitBox(Block block, BlockKey position, Shown shown, Panel.Button b) {
        double px = shown.pixel();
        double localX = (b.x() + b.w() / 2.0 - shown.size.width / 2.0) * px;
        double localBottom = (shown.size.height / 2.0 - (b.y() + b.h())) * px;
        double length = Math.hypot(shown.facingX, shown.facingZ);
        double fx = length == 0 ? 0 : shown.facingX / length, fz = length == 0 ? 1 : shown.facingZ / length;
        // Seen from the front, the board's right is the facing turned a quarter clockwise.
        double rightX = fz, rightZ = -fx;
        Location at = new Location(block.getWorld(), block.getX() + shown.centre.x() + rightX * localX,
                block.getY() + shown.centre.y() + localBottom, block.getZ() + shown.centre.z() + rightZ * localX);
        return block.getWorld().spawn(at, Interaction.class, created -> {
            tag(created, position);
            created.setInteractionWidth((float) (Math.max(b.w(), 1) * px));
            created.setInteractionHeight((float) (b.h() * px));
            created.setResponsive(true);
        }).getUniqueId();
    }

    private void tag(Entity entity, BlockKey position) {
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(tag, PersistentDataType.STRING, position.toString());
    }

    private static Location at(Block block, Shown shown) {
        return new Location(block.getWorld(), block.getX() + shown.centre.x(), block.getY() + shown.centre.y(),
                block.getZ() + shown.centre.z(), shown.centre.yaw(), 0f);
    }

    private static Color argb(int argb) {
        return Color.fromARGB(argb >>> 24, argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF);
    }

    // ---- Where each part goes, in the board's own frame: x to the right, y up, z out of the board ----

    static Transformation fillTransform(Shown shown, double x, double y, double w, double h, float z) {
        double px = shown.pixel();
        float sx = (float) (w * px / (FILL_W * FONT_PIXEL)), sy = (float) (h * px / (FILL_H * FONT_PIXEL));
        double left = (x - shown.size.width / 2.0) * px, bottom = (shown.size.height / 2.0 - (y + h)) * px;
        float tx = (float) (left - FILL_LEFT * FONT_PIXEL * sx);
        return new Transformation(new Vector3f(tx, (float) bottom, z), new AxisAngle4f(), new Vector3f(sx, sy, 1f),
                new AxisAngle4f());
    }

    private static Transformation barTransform(Shown shown, Panel.Bar b) {
        // A bar at nothing is a sliver, never gone: the client can only move what is there.
        double w = Math.max(0.05, b.w() * b.fraction());
        return fillTransform(shown, b.x(), b.y(), w, b.h(), BAR_Z);
    }

    static Transformation labelTransform(Shown shown, Panel.Label l) {
        double px = shown.pixel();
        float scale = (float) (shown.mount.scale() * l.scale());
        double raw = Panel.width(l.text());   // font pixels at one times the size
        double unit = FONT_PIXEL * scale;
        double tx = switch (l.align()) {
            case LEFT -> (l.x() - shown.size.width / 2.0) * px - (1.0 - raw / 2.0) * unit;
            case CENTER -> (l.x() + l.w() / 2.0 - shown.size.width / 2.0) * px - unit;
            case RIGHT -> (l.x() + l.w() - shown.size.width / 2.0) * px - (1.0 + raw / 2.0) * unit;
        };
        double ty = (shown.size.height / 2.0 - l.y()) * px - GLYPH_TOP * unit;
        return new Transformation(new Vector3f((float) tx, (float) ty, TEXT_Z), new AxisAngle4f(),
                new Vector3f(scale, scale, scale), new AxisAngle4f());
    }

    private static Transformation tickTransform(Shown shown, Panel.Tick t) {
        double px = shown.pixel();
        float sx = (float) (t.thick() * px / (FILL_W * FONT_PIXEL)), sy = (float) (t.len() * px / (FILL_H * FONT_PIXEL));
        double rad = Math.toRadians(t.angle());
        double cx = (t.cx() + Math.sin(rad) * t.r() - shown.size.width / 2.0) * px;
        double cy = (shown.size.height / 2.0 - (t.cy() - Math.cos(rad) * t.r())) * px;
        // The stretched space's own middle, turned with it, must land on the tick's place.
        Quaternionf turn = new Quaternionf().rotateZ((float) -rad);
        Vector3f middle = new Vector3f((float) ((FILL_LEFT + FILL_W / 2.0) * FONT_PIXEL * sx),
                (float) (FILL_H / 2.0 * FONT_PIXEL * sy), 0f);
        turn.transform(middle);
        return new Transformation(new Vector3f((float) cx - middle.x, (float) cy - middle.y, BAR_Z), turn,
                new Vector3f(sx, sy, 1f), new Quaternionf());
    }

    private static Transformation iconTransform(Shown shown, Panel.Icon i, long quarters) {
        double px = shown.pixel();
        double cx = (i.cx() - shown.size.width / 2.0) * px, cy = (shown.size.height / 2.0 - i.cy()) * px;
        float size = (float) (i.size() * px);
        Quaternionf turn = new Quaternionf().rotateZ((float) (-quarters * Math.PI / 2.0));
        return new Transformation(new Vector3f((float) cx, (float) cy, ICON_Z), turn, new Vector3f(size, size, size),
                new Quaternionf());
    }

    // ---- Animation ----

    /**
     * Turns the icons that turn, a quarter at a time, the client easing each
     * quarter: for screens a player is near. Called every tick.
     */
    public void animate(long tick) {
        for (Map.Entry<BlockKey, Shown> entry : screens.entrySet()) {
            Shown shown = entry.getValue();
            World world = entry.getKey().world();
            if (world == null || !entry.getKey().isChunkLoaded()) continue;
            boolean anyTurning = false;
            for (Drawn drawn : shown.parts.values())
                if (drawn.part() instanceof Panel.Icon icon && icon.turnsPerSecond() > 0.0) anyTurning = true;
            if (!anyTurning) continue;
            Location centre = new Location(world, entry.getKey().x() + 0.5, entry.getKey().y() + 0.5, entry.getKey().z() + 0.5);
            if (world.getNearbyPlayers(centre, ANIMATE_RANGE).isEmpty()) continue;
            for (Drawn drawn : shown.parts.values()) {
                if (!(drawn.part() instanceof Panel.Icon icon) || icon.turnsPerSecond() <= 0.0) continue;
                long[] state = shown.turning.computeIfAbsent(icon.id(), ignored -> new long[] {0L, tick});
                if (tick < state[1]) continue;
                int ticks = (int) Math.max(2, Math.min(40, Math.round(5.0 / icon.turnsPerSecond())));
                state[0]++;
                state[1] = tick + ticks;
                if (world.getEntity(drawn.entity()) instanceof ItemDisplay display) {
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(ticks);
                    display.setTransformation(iconTransform(shown, icon, state[0]));
                }
            }
        }
    }

    /** A button's face lights up for a moment when it's pressed. */
    public void flash(BlockKey position, String action) {
        Shown shown = screens.get(position);
        World world = position.world();
        if (shown == null || world == null) return;
        for (Drawn drawn : shown.parts.values()) {
            if (!(drawn.part() instanceof Panel.Button button) || !button.action().equals(action)) continue;
            if (!(world.getEntity(drawn.entity()) instanceof TextDisplay face)) continue;
            face.setBackgroundColor(argb(Panel.PRESSED));
            String id = button.id();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Shown now = screens.get(position);
                Drawn current = now == null ? null : now.parts.get(id);
                if (current != null && current.part() instanceof Panel.Button b
                        && world.getEntity(current.entity()) instanceof TextDisplay display)
                    display.setBackgroundColor(argb(b.argb()));
            }, 4L);
        }
    }

    // ---- Clicks ----

    /** The button whose box this is, or null. */
    public Hit hit(Entity entity) {
        BlockKey position = hitOwners.get(entity.getUniqueId());
        Shown shown = position == null ? null : screens.get(position);
        String action = shown == null ? null : shown.hits.get(entity.getUniqueId());
        return action == null ? null : new Hit(position, action);
    }

    // ---- Taking down ----

    public boolean showing(BlockKey position, World world) {
        Shown shown = screens.get(position);
        return shown != null && intact(world, shown);
    }

    private boolean intact(World world, Shown shown) {
        for (Drawn drawn : shown.parts.values()) {
            Entity entity = world.getEntity(drawn.entity());
            if (entity == null || !entity.isValid()) return false;
            if (drawn.label() != null) {
                Entity label = world.getEntity(drawn.label());
                if (label == null || !label.isValid()) return false;
            }
        }
        for (UUID hit : shown.hits.keySet()) {
            Entity entity = world.getEntity(hit);
            if (entity == null || !entity.isValid()) return false;
        }
        return true;
    }

    private void remove(World world, Shown shown, Drawn drawn) {
        kill(world, drawn.entity());
        kill(world, drawn.label());
        if (drawn.part() instanceof Panel.Button button) {
            for (Iterator<Map.Entry<UUID, String>> it = shown.hits.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, String> hit = it.next();
                if (!hit.getValue().equals(button.action())) continue;
                kill(world, hit.getKey());
                hitOwners.remove(hit.getKey());
                it.remove();
            }
        }
    }

    private void take(World world, Shown shown) {
        if (world != null) {
            for (Drawn drawn : shown.parts.values()) {
                kill(world, drawn.entity());
                kill(world, drawn.label());
            }
            for (UUID hit : shown.hits.keySet()) kill(world, hit);
        }
        for (UUID hit : shown.hits.keySet()) hitOwners.remove(hit);
    }

    private static void kill(World world, UUID id) {
        if (id == null) return;
        Entity entity = world.getEntity(id);
        if (entity != null) entity.remove();
    }

    public void remove(BlockKey position) {
        Shown shown = screens.remove(position);
        if (shown != null) take(position.world(), shown);
    }

    public void retainOnly(Set<BlockKey> devices) {
        for (BlockKey position : Set.copyOf(screens.keySet())) if (!devices.contains(position)) remove(position);
    }

    public void removeAll() {
        for (BlockKey position : Set.copyOf(screens.keySet())) remove(position);
    }

    /** True when a player is close enough to click a button there (a little past reach). */
    public static boolean inReach(Player player, BlockKey position) {
        Location at = position.location();
        return at != null && at.getWorld() == player.getWorld()
                && player.getLocation().distanceSquared(at.add(0.5, 0.5, 0.5)) <= 8.0 * 8.0;
    }
}
