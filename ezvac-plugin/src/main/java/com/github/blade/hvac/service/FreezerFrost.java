package com.github.blade.hvac.service;

import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.Freezer;
import com.github.blade.hvac.model.MiniFridge;
import com.github.blade.hvac.model.Refrigerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A freezer's frost, seen in its barrel (BETA 1.0 Pilot 7): it grows in from
 * the corners round the edge of the barrel, one slot for every twentieth of
 * a full ring. An empty slot it reaches fills with Frost; food already there
 * is frosted over, looking like ice where the server can show that (26.x;
 * glinting on 1.21.x), and stays where it is until a defrost frees it. The
 * middle row's inner seven slots never frost. Nothing frosted can be taken,
 * dragged or pulled out by a hopper, and none of it ever leaves a freezer:
 * a barrel that breaks or blows up drops its food freed, and frost found
 * anywhere else melts away. Main thread only.
 */
public final class FreezerFrost implements Listener {
    /** The barrel's edge, corners first, then creeping along: rows of 9, top 0-8, middle 9-17, bottom 18-26. */
    static final int[] RING = {0, 8, 18, 26, 1, 7, 19, 25, 9, 17, 2, 6, 20, 24, 3, 5, 21, 23, 4, 22};
    static final String FROSTED_LINE = "❄ Frosted over: defrost to free it";

    private final Plugin plugin;
    private final HvacRegistry registry;
    private final NamespacedKey frostKey, frostedKey, modelKey, glintKey, wallKey;
    /** ItemMeta's item model (1.21.4 and newer), looked up once; null on 1.21.1. */
    private final Method setModel, getModel;

    public FreezerFrost(Plugin plugin, HvacRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.frostKey = new NamespacedKey(plugin, "frost");
        this.frostedKey = new NamespacedKey(plugin, "frosted");
        this.modelKey = new NamespacedKey(plugin, "frosted_model");
        this.glintKey = new NamespacedKey(plugin, "frosted_glint");
        this.wallKey = new NamespacedKey(plugin, "fridge_wall");
        Method set = null, get = null;
        try {
            set = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
            get = ItemMeta.class.getMethod("getItemModel");
        } catch (NoSuchMethodException older) {
            // 1.21.1: frosted food glints instead.
        }
        this.setModel = set;
        this.getModel = get;
    }

    /** Every second: each loaded freezer's barrel shows its frost, and a defrosting one drips. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::showAll, 20L, 20L);
    }

    private void showAll() {
        for (var unit : registry.refrigerators()) {
            if (unit instanceof Freezer freezer) {
                show(freezer);
                if (freezer.defrosting()) drip(freezer);
            }
            if (unit instanceof MiniFridge mini) showWalls(mini);
        }
    }

    /**
     * A mini fridge's barrel (BETA 1.0 Pilot 7): its walls fill every slot
     * but the seven for food and the chiller box, the gap before the chiller
     * saying what it is. Anything found where a wall goes is moved to a free
     * food slot, or dropped by the barrel.
     */
    public void showWalls(MiniFridge mini) {
        Inventory inventory = barrel(mini);
        if (inventory == null) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (MiniFridge.holdsFood(slot)) continue;
            ItemStack item = inventory.getItem(slot);
            boolean gap = slot == MiniFridge.GAP;
            if (isWall(item) && gap == isGap(item)) continue;
            if (item != null && !item.getType().isAir() && !isWall(item)) {
                int free = -1;
                for (int food = MiniFridge.FIRST_FOOD; food <= MiniFridge.LAST_FOOD && free < 0; food++) {
                    ItemStack there = inventory.getItem(food);
                    if (there == null || there.getType().isAir()) free = food;
                }
                if (free >= 0) inventory.setItem(free, item);
                else if (inventory.getLocation() != null)
                    inventory.getLocation().getWorld().dropItemNaturally(inventory.getLocation().add(0.5, 1.1, 0.5), item);
            }
            inventory.setItem(slot, wallItem(gap));
        }
    }

    ItemStack wallItem(boolean gap) {
        ItemStack wall = new ItemStack(gap ? Material.CYAN_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        wall.editMeta(meta -> {
            meta.displayName(Component.text(com.github.blade.hvac.lang.Lang.text(gap ? "fridge.chiller-box" : "fridge.wall"),
                    gap ? NamedTextColor.AQUA : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (gap) meta.lore(List.of(Component.text(com.github.blade.hvac.lang.Lang.text("fridge.chiller-1"), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(com.github.blade.hvac.lang.Lang.text("fridge.chiller-2"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(wallKey, PersistentDataType.BYTE, (byte) (gap ? 2 : 1));
        });
        return wall;
    }

    public boolean isWall(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wallKey, PersistentDataType.BYTE);
    }

    private boolean isGap(ItemStack item) {
        Byte kind = item == null || !item.hasItemMeta() ? null
                : item.getItemMeta().getPersistentDataContainer().get(wallKey, PersistentDataType.BYTE);
        return kind != null && kind == 2;
    }

    /** The barrel of a freezer or mini fridge, if its chunk is loaded and it is still a barrel. */
    private Inventory barrel(Refrigerator freezer) {
        BlockKey key = freezer.barrel();
        if (!key.isChunkLoaded()) return null;
        Location location = key.location();
        if (location == null) return null;
        return location.getBlock().getState(false) instanceof Barrel barrel ? barrel.getInventory() : null;
    }

    /**
     * How many of the ring's slots a freezer's frost covers: one for every
     * full twentieth, so a little frost covers nothing yet.
     */
    static int reach(double frost) {
        return (int) Math.floor(frost * RING.length + 1e-9);
    }

    /**
     * Which ring slots frost covers: the empty ones (and those already
     * Frost) first, in ring order, and food against the wall only once
     * there's no empty edge left. So food frosts last and is freed first.
     *
     * @param holdsFood for each ring position, true where food (frosted or not) lies
     * @return for each ring position, true where it's covered
     */
    static boolean[] covered(int reach, boolean[] holdsFood) {
        boolean[] covered = new boolean[holdsFood.length];
        int left = reach;
        for (int i = 0; i < holdsFood.length && left > 0; i++)
            if (!holdsFood[i]) { covered[i] = true; left--; }
        for (int i = 0; i < holdsFood.length && left > 0; i++)
            if (holdsFood[i]) { covered[i] = true; left--; }
        return covered;
    }

    /** Brings a freezer's barrel in line with its frost. */
    public void show(Freezer freezer) {
        Inventory inventory = barrel(freezer);
        if (inventory == null) return;
        boolean[] ring = new boolean[inventory.getSize()];
        boolean[] holdsFood = new boolean[RING.length];
        for (int i = 0; i < RING.length && RING[i] < ring.length; i++) {
            ItemStack item = inventory.getItem(RING[i]);
            holdsFood[i] = item != null && !item.getType().isAir() && !isFrost(item);
        }
        boolean[] covered = covered(reach(freezer.frost()), holdsFood);
        for (int i = 0; i < RING.length && RING[i] < ring.length; i++) {
            int slot = RING[i];
            ring[slot] = true;
            ItemStack item = inventory.getItem(slot);
            boolean empty = item == null || item.getType().isAir();
            if (covered[i]) {
                if (empty) inventory.setItem(slot, frostItem());
                else if (!isFrost(item) && !isFrosted(item)) inventory.setItem(slot, frostOver(item));
            } else if (!empty) {
                if (isFrost(item)) inventory.setItem(slot, null);
                else if (isFrosted(item)) inventory.setItem(slot, thaw(item));
            }
        }
        // Anything frosted that ended up off the ring (never by itself) is freed.
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot < ring.length && ring[slot]) continue;
            ItemStack item = inventory.getItem(slot);
            if (isFrost(item)) inventory.setItem(slot, null);
            else if (isFrosted(item)) inventory.setItem(slot, thaw(item));
        }
    }

    /** Takes every bit of frost out of an inventory and frees what it held: before it drops, or as EZvac stops. */
    public void clear(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isFrost(item) || isWall(item)) inventory.setItem(slot, null);
            else if (isFrosted(item)) inventory.setItem(slot, thaw(item));
        }
    }

    /** As EZvac stops: no frost, and no mini fridge's walls, are left behind for a server without it. */
    public void clearAll() {
        for (var unit : registry.refrigerators())
            if (unit instanceof Freezer || unit instanceof MiniFridge) {
                Inventory inventory = barrel(unit);
                if (inventory != null) clear(inventory);
            }
    }

    private void drip(Freezer freezer) {
        BlockKey unit = freezer.unit();
        if (!unit.isChunkLoaded()) return;
        Location location = unit.location();
        if (location == null) return;
        location.getWorld().spawnParticle(Particle.DRIPPING_WATER, location.add(0.5, 0.02, 0.5), 3, 0.3, 0.0, 0.3, 0.0);
    }

    ItemStack frostItem() {
        ItemStack frost = new ItemStack(Material.ICE);
        frost.editMeta(meta -> {
            meta.displayName(Component.text(com.github.blade.hvac.lang.Lang.text("fridge.frost"), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(com.github.blade.hvac.lang.Lang.text("fridge.frost-lore"), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(frostKey, PersistentDataType.BYTE, (byte) 1);
        });
        return frost;
    }

    public boolean isFrost(ItemStack item) {
        return item != null && item.getType() == Material.ICE && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(frostKey, PersistentDataType.BYTE);
    }

    public boolean isFrosted(ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(frostedKey, PersistentDataType.BYTE);
    }

    /** Food frosted over: it looks like ice (or glints), says so, and stays exactly what it was underneath. */
    ItemStack frostOver(ItemStack item) {
        ItemStack frosted = item.clone();
        frosted.editMeta(meta -> {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(frostedKey, PersistentDataType.BYTE, (byte) 1);
            if (setModel != null) {
                try {
                    Object model = getModel.invoke(meta);
                    data.set(modelKey, PersistentDataType.STRING, model == null ? "" : model.toString());
                    setModel.invoke(meta, NamespacedKey.minecraft("ice"));
                } catch (ReflectiveOperationException | RuntimeException failed) {
                    glint(meta, data);
                }
            } else {
                glint(meta, data);
            }
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.text(frostedLine(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return frosted;
    }

    private void glint(ItemMeta meta, PersistentDataContainer data) {
        // 1: no override; 2: forced on; 3: forced off. Items frosted before BETA 1.0 say only 1 or 2.
        byte was = !meta.hasEnchantmentGlintOverride() ? (byte) 1 : meta.getEnchantmentGlintOverride() ? (byte) 2 : (byte) 3;
        data.set(glintKey, PersistentDataType.BYTE, was);
        meta.setEnchantmentGlintOverride(true);
    }

    /** Frosted food freed, exactly as it was before. */
    ItemStack thaw(ItemStack item) {
        ItemStack freed = item.clone();
        freed.editMeta(meta -> {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            String model = data.get(modelKey, PersistentDataType.STRING);
            if (model != null && setModel != null) {
                try {
                    setModel.invoke(meta, model.isEmpty() ? null : NamespacedKey.fromString(model));
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Leave it as it is.
                }
            }
            Byte glint = data.get(glintKey, PersistentDataType.BYTE);
            if (glint != null && glint == 1) meta.setEnchantmentGlintOverride(null);
            else if (glint != null && glint == 3) meta.setEnchantmentGlintOverride(false);
            if (meta.lore() != null) {
                List<Component> lore = new ArrayList<>(meta.lore());
                lore.removeIf(line -> { String plain = PlainTextComponentSerializer.plainText().serialize(line);
                    return FROSTED_LINE.equals(plain) || frostedLine().equals(plain); });
                meta.lore(lore.isEmpty() ? null : lore);
            }
            data.remove(frostedKey);
            data.remove(modelKey);
            data.remove(glintKey);
        });
        return freed;
    }

    // ---- Nothing frosted moves ----

    /** The frosted line in the server's language (BETA 1.1); {@link #FROSTED_LINE} is the English one. */
    static String frostedLine() {
        return com.github.blade.hvac.lang.Lang.text("fridge.frosted");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (isWall(current)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) player.sendActionBar(com.github.blade.hvac.lang.Lang.message("fridge.wall-click"));
            return;
        }
        boolean locked = isFrost(current) || isFrosted(current);
        // A number key swapping a hotbar item onto it, or the cursor gathering every match, counts too.
        if (!locked && event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            ItemStack cursor = event.getCursor();
            for (ItemStack item : event.getView().getTopInventory().getContents())
                if (isFrosted(item) && cursor != null && item.getType() == cursor.getType()) locked = true;
        }
        if (!locked) return;
        event.setCancelled(true);
        tell(event.getWhoClicked());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        for (int raw : event.getRawSlots()) {
            if (raw >= top.getSize()) continue;
            ItemStack there = top.getItem(raw);
            if (isFrost(there) || isFrosted(there) || isWall(there)) {
                event.setCancelled(true);
                tell(event.getWhoClicked());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (isFrost(event.getItem()) || isFrosted(event.getItem()) || isWall(event.getItem())) event.setCancelled(true);
    }

    private static void tell(HumanEntity who) {
        if (who instanceof Player player) player.sendActionBar(com.github.blade.hvac.lang.Lang.message("fridge.locked"));
    }

    // ---- None of it ever leaves a freezer ----

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        clearBarrel(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) clearBarrel(block);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) clearBarrel(block);
    }

    private void clearBarrel(Block block) {
        if (block.getType() != Material.BARREL) return;
        if (block.getState(false) instanceof Barrel barrel) clear(barrel.getInventory());
    }

    /** Frost a player somehow carried off melts, and frosted food they hold is freed. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        clear(event.getPlayer().getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        clear(event.getPlayer().getInventory());
    }
}
