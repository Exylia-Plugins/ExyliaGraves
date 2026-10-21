package net.exylia.exyliaGraves.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A window onto what a grave holds that nothing can leave.
 *
 * <p>The listener cancels every click and drag while one is on top, the
 * player's own inventory included, so not even a shift-click or a double-click
 * collecting matching stacks moves an item. The window is a copy and is never
 * written anywhere: the grave's row is the only place its items are.
 */
public final class PreviewHolder implements InventoryHolder {

    /** Six rows: a death drops at most 41 stacks (36 slots, armour and offhand). */
    private static final int SIZE = 54;

    private Inventory inventory;

    private PreviewHolder() {
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Opens a read-only copy of these contents, on the viewer's thread. */
    public static void open(Player viewer, Component title, ItemStack[] contents) {
        PreviewHolder holder = new PreviewHolder();
        holder.inventory = Bukkit.createInventory(holder, SIZE, title);
        if (contents != null) {
            int slot = 0;
            for (ItemStack item : contents) {
                if (item == null || item.getType().isAir()) continue;
                if (slot >= SIZE) break;
                holder.inventory.setItem(slot++, item.clone());
            }
        }
        viewer.openInventory(holder.inventory);
    }
}
