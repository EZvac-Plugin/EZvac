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
 * Floating screens in front of device signs (2.1), drawn as a status strip
 * since BETA 1.0: a header display in the colour of what the device is doing,
 * an opaque body display under it, and the big reading as a third display at
 * twice the size, floating just in front of the body. They are lit at night,
 * have no hitbox, so clicks reach the sign behind, and are never saved with
 * the world: a chunk unloading or the server stopping takes them away, and the
 * next refresh puts them back. Main thread only.
 */
public final class ScreenService {
    /** The body's panel: dark and opaque, so the sign behind never shows through. */
    private static final Color BODY = Color.fromARGB(0xFF, 0x10, 0x14, 0x18);
    /** The big reading has no panel of its own: it is drawn over the body. */
    private static final Color CLEAR = Color.fromARGB(0, 0, 0, 0);

    /** A screen's displays, and what they show: big is null on a screen without a big reading. */
    private record Shown(UUID header, UUID body, UUID big, int bodyLines, ScreenText.Tone tone,
                         Component headerText, Component bodyText, Component bigText) {}

    private final boolean enabled;
    private final NamespacedKey tag;
    private final Map<BlockKey, Shown> screens = new HashMap<>();

    public ScreenService(Plugin plugin, boolean enabled) {
        this.enabled = enabled;
        this.tag = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "screen");
    }

    public boolean enabled() { return enabled; }

    /**
     * Shows this screen in front of the sign, putting it up first if it isn't
     * there (a chunk that unloaded took it away). Unchanged parts are not sent
     * again; a screen that changes size, or gains or loses its big reading, is
     * put up afresh.
     */
    public void show(BlockKey position, Block sign, ScreenText screen) {
        if (!enabled) return;
        Component headerText = screen.header(), bodyText = screen.body(), bigText = screen.bigText();
        int bodyLines = screen.bodyLines();
        World world = sign.getWorld();
        Shown shown = screens.get(position);
        if (shown != null && shown.header() != null && intact(world, shown) && shown.bodyLines() == bodyLines
                && (shown.big() == null) == (bigText == null)) {
            if (!shown.headerText().equals(headerText)) display(world, shown.header()).text(headerText);
            if (!shown.tone().equals(screen.tone()))
                display(world, shown.header()).setBackgroundColor(panel(screen.tone()));
            if (!shown.bodyText().equals(bodyText)) display(world, shown.body()).text(bodyText);
            if (bigText != null && !bigText.equals(shown.bigText())) display(world, shown.big()).text(bigText);
            screens.put(position, new Shown(shown.header(), shown.body(), shown.big(), bodyLines, screen.tone(),
                    headerText, bodyText, bigText));
            return;
        }
        if (shown != null) take(world, shown);
        BlockData data = sign.getBlockData();
        BlockFace facing = facing(data);
        if (facing == null) {
            screens.remove(position);
            return;
        }
        ScreenPlacement.Mount mount = mount(data);
        ScreenPlacement.Layout layout = ScreenPlacement.layout(mount, facing.getDirection().getX(),
                facing.getDirection().getZ(), bodyLines, bigText != null);
        float scale = (float) mount.scale();
        UUID header = spawn(sign, position, layout.header(), scale, headerText, panel(screen.tone()),
                TextDisplay.TextAlignment.LEFT, ScreenPlacement.LINE_WIDTH);
        UUID body = spawn(sign, position, layout.body(), scale, bodyText, BODY, TextDisplay.TextAlignment.CENTER,
                ScreenPlacement.LINE_WIDTH);
        UUID big = bigText == null ? null : spawn(sign, position, layout.big(), scale * 2f, bigText, CLEAR,
                TextDisplay.TextAlignment.CENTER, ScreenPlacement.LINE_WIDTH);
        screens.put(position, new Shown(header, body, big, bodyLines, screen.tone(), headerText, bodyText, bigText));
    }

    /**
     * Shows a mini thermostat's panel on its wall button (BETA 1.0 Pilot 2):
     * the body and the big reading, no header. Unchanged parts aren't sent
     * again.
     */
    public void showMini(BlockKey position, Block button, MiniScreen screen) {
        if (!enabled) return;
        Component bodyText = screen.body(), bigText = screen.bigText();
        World world = button.getWorld();
        Shown shown = screens.get(position);
        if (shown != null && shown.header() == null && intact(world, shown)) {
            if (!shown.bodyText().equals(bodyText)) display(world, shown.body()).text(bodyText);
            if (!bigText.equals(shown.bigText())) display(world, shown.big()).text(bigText);
            screens.put(position, new Shown(null, shown.body(), shown.big(), MiniScreen.BODY_LINES, null, null,
                    bodyText, bigText));
            return;
        }
        if (shown != null) take(world, shown);
        BlockFace facing = facing(button.getBlockData());
        if (facing == null) {
            screens.remove(position);
            return;
        }
        ScreenPlacement.Layout layout = ScreenPlacement.mini(facing.getDirection().getX(), facing.getDirection().getZ(),
                MiniScreen.BODY_LINES);
        float scale = (float) ScreenPlacement.Mount.BUTTON.scale();
        UUID body = spawn(button, position, layout.body(), scale, bodyText, BODY, TextDisplay.TextAlignment.CENTER,
                MiniScreen.LINE_WIDTH);
        UUID big = spawn(button, position, layout.big(), scale * 2f, bigText, CLEAR, TextDisplay.TextAlignment.CENTER,
                MiniScreen.LINE_WIDTH);
        screens.put(position, new Shown(null, body, big, MiniScreen.BODY_LINES, null, null, bodyText, bigText));
    }

    private static Color panel(ScreenText.Tone tone) {
        return Color.fromARGB(0xFF, tone.rgb() >> 16 & 0xFF, tone.rgb() >> 8 & 0xFF, tone.rgb() & 0xFF);
    }

    private UUID spawn(Block sign, BlockKey position, ScreenPlacement.Spot spot, float scale, Component text,
                       Color background, TextDisplay.TextAlignment alignment, int lineWidth) {
        Location at = new Location(sign.getWorld(), sign.getX() + spot.x(), sign.getY() + spot.y(),
                sign.getZ() + spot.z(), spot.yaw(), 0f);
        return sign.getWorld().spawn(at, TextDisplay.class, created -> {
            created.setPersistent(false);
            created.getPersistentDataContainer().set(tag, PersistentDataType.STRING, position.toString());
            created.setBillboard(Display.Billboard.FIXED);
            created.setAlignment(alignment);
            created.setLineWidth(lineWidth);
            created.setDefaultBackground(false);
            created.setBackgroundColor(background);
            created.setShadowed(true);
            created.setBrightness(new Display.Brightness(15, 15));
            created.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
            created.text(text);
        }).getUniqueId();
    }

    private static TextDisplay display(World world, UUID id) {
        return (TextDisplay) world.getEntity(id);
    }

    /** True while every display of the screen is still there. */
    private static boolean intact(World world, Shown shown) {
        return (shown.header() == null || valid(world, shown.header())) && valid(world, shown.body())
                && (shown.big() == null || valid(world, shown.big()));
    }

    private static boolean valid(World world, UUID id) {
        return world.getEntity(id) instanceof TextDisplay display && display.isValid();
    }

    private static void take(World world, Shown shown) {
        if (world == null) return;
        for (UUID id : new UUID[] {shown.header(), shown.body(), shown.big()}) {
            Entity entity = id == null ? null : world.getEntity(id);
            if (entity != null) entity.remove();
        }
    }

    /** True while this sign's screen is up. */
    public boolean showing(BlockKey position, Block sign) {
        Shown shown = screens.get(position);
        return shown != null && intact(sign.getWorld(), shown);
    }

    /** Takes a sign's screen down, when its device is removed or its sign changes. */
    public void remove(BlockKey position) {
        Shown shown = screens.remove(position);
        if (shown != null) take(position.world(), shown);
    }

    /** Takes down every screen whose device is gone. */
    public void retainOnly(Set<BlockKey> devices) {
        for (Iterator<BlockKey> it = screens.keySet().iterator(); it.hasNext(); ) {
            BlockKey position = it.next();
            if (devices.contains(position)) continue;
            take(position.world(), screens.get(position));
            it.remove();
        }
    }

    /** Takes down every screen, when EZvac stops; they would otherwise stay until their chunks unload. */
    public void removeAll() {
        for (BlockKey position : Set.copyOf(screens.keySet())) remove(position);
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
