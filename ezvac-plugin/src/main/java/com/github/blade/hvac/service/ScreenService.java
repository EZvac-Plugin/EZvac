package com.github.blade.hvac.service;

import com.github.blade.hvac.model.BlockKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.HangingSign;
import org.bukkit.block.data.type.WallHangingSign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Floating screens in front of device signs (2.1 Pilot 3): text displays with
 * a dark background, lit at night, that show more than a sign's four lines.
 * They have no hitbox, so clicks reach the sign behind. They are never saved
 * with the world: a chunk unloading or the server stopping takes them away,
 * and the next refresh puts them back. Main thread only.
 */
public final class ScreenService {
    /** A dark, slightly see-through panel. */
    private static final Color BACKGROUND = Color.fromARGB(0xB0, 0x10, 0x14, 0x18);

    private record Shown(UUID entity, int lines, Component text) {}

    private final boolean enabled;
    private final NamespacedKey tag;
    private final Map<BlockKey, Shown> screens = new HashMap<>();

    public ScreenService(Plugin plugin, boolean enabled) {
        this.enabled = enabled;
        this.tag = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "screen");
    }

    public boolean enabled() { return enabled; }

    /**
     * Shows this text on the sign's screen, putting the screen up first if
     * it isn't there (a chunk that unloaded took it away). Unchanged text is
     * not sent again.
     */
    public void show(BlockKey position, Block sign, Component text, int lines) {
        if (!enabled) return;
        Shown shown = screens.get(position);
        Entity entity = shown == null ? null : sign.getWorld().getEntity(shown.entity());
        if (entity instanceof TextDisplay display && display.isValid() && shown.lines() == lines) {
            if (!shown.text().equals(text)) {
                display.text(text);
                screens.put(position, new Shown(shown.entity(), lines, text));
            }
            return;
        }
        if (entity != null) entity.remove();
        Location spot = spot(sign, lines);
        if (spot == null) {
            screens.remove(position);
            return;
        }
        double scale = mount(sign.getBlockData()).scale();
        TextDisplay display = sign.getWorld().spawn(spot, TextDisplay.class, created -> {
            created.setPersistent(false);
            created.getPersistentDataContainer().set(tag, PersistentDataType.STRING, position.toString());
            created.setBillboard(Display.Billboard.FIXED);
            created.setAlignment(TextDisplay.TextAlignment.LEFT);
            created.setLineWidth(ScreenPlacement.LINE_WIDTH);
            created.setBackgroundColor(BACKGROUND);
            created.setShadowed(true);
            created.setBrightness(new Display.Brightness(15, 15));
            float s = (float) scale;
            created.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(s, s, s), new AxisAngle4f()));
            created.text(text);
        });
        screens.put(position, new Shown(display.getUniqueId(), lines, text));
    }

    /** Takes a sign's screen down, when its device is removed or its sign changes. */
    public void remove(BlockKey position) {
        Shown shown = screens.remove(position);
        if (shown == null) return;
        World world = position.world();
        Entity entity = world == null ? null : world.getEntity(shown.entity());
        if (entity != null) entity.remove();
    }

    /** Takes down every screen whose device is gone. */
    public void retainOnly(Set<BlockKey> devices) {
        for (Iterator<BlockKey> it = screens.keySet().iterator(); it.hasNext(); ) {
            BlockKey position = it.next();
            if (devices.contains(position)) continue;
            Shown shown = screens.get(position);
            World world = position.world();
            Entity entity = world == null ? null : world.getEntity(shown.entity());
            if (entity != null) entity.remove();
            it.remove();
        }
    }

    /** Takes down every screen, when EZvac stops; they would otherwise stay until their chunks unload. */
    public void removeAll() {
        for (BlockKey position : Set.copyOf(screens.keySet())) remove(position);
    }

    /** Where the screen of this sign goes, or null for a block that isn't a sign any more. */
    private static Location spot(Block sign, int lines) {
        BlockData data = sign.getBlockData();
        BlockFace facing = facing(data);
        if (facing == null) return null;
        ScreenPlacement.Spot spot = ScreenPlacement.of(mount(data), facing.getDirection().getX(),
                facing.getDirection().getZ(), lines);
        return new Location(sign.getWorld(), sign.getX() + spot.x(), sign.getY() + spot.y(), sign.getZ() + spot.z(),
                spot.yaw(), 0f);
    }

    private static ScreenPlacement.Mount mount(BlockData data) {
        if (data instanceof WallSign) return ScreenPlacement.Mount.WALL;
        if (data instanceof WallHangingSign || data instanceof HangingSign) return ScreenPlacement.Mount.HANGING;
        return ScreenPlacement.Mount.STANDING;
    }

    private static BlockFace facing(BlockData data) {
        if (data instanceof Directional directional) return directional.getFacing();
        if (data instanceof Rotatable rotatable) return rotatable.getRotation();
        return null;
    }
}
