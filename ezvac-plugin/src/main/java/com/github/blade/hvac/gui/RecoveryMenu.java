package com.github.blade.hvac.gui;

import com.github.blade.hvac.StartupNotices;
import com.github.blade.hvac.service.RegistrySalvage;
import com.github.blade.hvac.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.github.blade.hvac.gui.ControlMenus.click;
import static com.github.blade.hvac.gui.ControlMenus.gray;
import static com.github.blade.hvac.gui.ControlMenus.item;

/**
 * /hvac quicksettings while EZvac is stopped on hvac.yml (BETA 1.0): why it
 * stopped, salvaging the file (an unreadable one only), and the backups in
 * plugins/EZvac/backups, newest first, any this EZvac can read restorable.
 * Each action takes a click, then a second one on the same item. Once one
 * worked, the menu only says to restart. Admins only; main thread only.
 */
public final class RecoveryMenu implements Listener {
    static final int STATUS = 4, SALVAGE = 11, BY_HAND = 15, CLOSE = 26;
    /** The newest backups, in the bottom row. */
    static final int[] BACKUP_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25};
    private static final String ADMIN = "ezvac.admin";

    private static final class Menu implements InventoryHolder {
        Inventory inventory;
        List<RegistrySalvage.Backup> backups = List.of();
        /** Clicked once: the next click on this slot does it. -1 when nothing is armed. */
        int armed = -1;

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private final Plugin plugin;
    private final StartupNotices notices;

    public RecoveryMenu(Plugin plugin, StartupNotices notices) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.notices = Objects.requireNonNull(notices, "notices");
    }

    public void open(Player player) {
        if (!player.hasPermission(ADMIN)) return;
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 27, Component.text(Lang.text("menu.recovery.title")));
        draw(menu);
        player.openInventory(menu.inventory);
    }

    private void draw(Menu menu) {
        Inventory inventory = menu.inventory;
        menu.backups = RegistrySalvage.backups(plugin.getDataFolder());
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        inventory.setItem(CLOSE, item(Material.BARRIER, Component.text(Lang.text("menu.recovery.close"), NamedTextColor.RED), List.of()));
        boolean fixed = notices.fixed();
        List<Component> status = new ArrayList<>();
        if (fixed) status.add(gray(Lang.text("menu.recovery.fixed-restart")));
        else {
            status.add(gray(Lang.text("menu.recovery.nothing-runs")));
            status.addAll(wrap(notices.reason()));
        }
        inventory.setItem(STATUS, item(fixed ? Material.LIME_DYE : Material.RED_DYE, Component.text(fixed
                ? Lang.text("menu.recovery.fixed") : Lang.text("menu.recovery.stopped"), fixed ? NamedTextColor.GREEN : NamedTextColor.RED),
                status));
        if (!fixed) {
            inventory.setItem(SALVAGE, notices.salvageable()
                    ? action(menu, SALVAGE, Material.ANVIL, Lang.text("menu.recovery.salvage"), List.of(
                    gray(Lang.text("menu.recovery.salvage-1")), gray(Lang.text("menu.recovery.salvage-2")),
                    gray(Lang.text("menu.recovery.salvage-3"))))
                    : item(Material.STRUCTURE_VOID, Component.text(Lang.text("menu.recovery.salvage"), NamedTextColor.DARK_GRAY),
                    List.of(gray(Lang.text("menu.recovery.newer-1")), gray(Lang.text("menu.recovery.newer-2")),
                            gray(Lang.text("menu.recovery.newer-3")))));
            int shown = 0;
            for (RegistrySalvage.Backup backup : menu.backups) {
                if (shown == BACKUP_SLOTS.length) break;
                int slot = BACKUP_SLOTS[shown++];
                inventory.setItem(slot, backup.readable()
                        ? action(menu, slot, Material.PAPER, Lang.text("menu.recovery.restore-named", com.github.blade.hvac.lang.Lang.arg("name", backup.name())), List.of(
                        gray(Lang.text("menu.recovery.restore-1")), gray(Lang.text("menu.recovery.restore-2")),
                        gray(Lang.text("menu.recovery.restore-3")),
                        gray(Lang.text("menu.recovery.restore-4"))))
                        : item(Material.MAP, Component.text(backup.name(), NamedTextColor.DARK_GRAY),
                        List.of(gray(Lang.text("menu.recovery.unreadable")))));
            }
            if (menu.backups.isEmpty())
                inventory.setItem(BACKUP_SLOTS[0], item(Material.MAP, Component.text(Lang.text("menu.recovery.no-backups"), NamedTextColor.GRAY),
                        List.of(gray(Lang.text("menu.recovery.no-backups-lore")))));
        }
        inventory.setItem(BY_HAND, item(Material.WRITABLE_BOOK, Component.text(Lang.text("menu.recovery.by-hand"), NamedTextColor.AQUA), List.of(
                gray(Lang.text("menu.recovery.by-hand-1")), gray(Lang.text("menu.recovery.by-hand-2")),
                gray(Lang.text("menu.recovery.by-hand-3")))));
    }

    /** An item done by a click, then a second one on it. */
    private static ItemStack action(Menu menu, int slot, Material material, String name, List<Component> lore) {
        List<Component> lines = new ArrayList<>(lore);
        lines.add(menu.armed == slot
                ? Component.text(Lang.text("menu.recovery.click-again"), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                : Component.text(Lang.text("menu.recovery.click-asks"), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        return item(material, Component.text(name, menu.armed == slot ? NamedTextColor.RED : NamedTextColor.GOLD), lines);
    }

    /** A long reason in lore-sized lines. */
    static List<Component> wrap(String text) {
        List<Component> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > 42 && line.length() > 0) {
                out.add(gray(line.toString()));
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(gray(line.toString()));
        return out;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != menu.inventory
                || !player.hasPermission(ADMIN)) return;
        int slot = event.getRawSlot();
        if (slot == CLOSE) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (notices.fixed()) return;
        RegistrySalvage.Backup backup = backupAt(menu, slot);
        boolean actionable = slot == SALVAGE && notices.salvageable() || backup != null && backup.readable();
        if (!actionable) {
            menu.armed = -1;
            draw(menu);
            return;
        }
        if (menu.armed != slot) {
            menu.armed = slot;
            click(player, true);
            draw(menu);
            return;
        }
        menu.armed = -1;
        boolean worked = slot == SALVAGE ? notices.salvage(player) : notices.restore(player, backup.name());
        click(player, worked);
        draw(menu);
    }

    private static RegistrySalvage.Backup backupAt(Menu menu, int slot) {
        for (int i = 0; i < BACKUP_SLOTS.length && i < menu.backups.size(); i++)
            if (BACKUP_SLOTS[i] == slot) return menu.backups.get(i);
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }
}
