package net.exylia.exyliaGraves.service;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.exyliaGraves.config.GraveMessages;
import net.exylia.exyliaGraves.config.GraveSettings;
import net.exylia.exyliaGraves.database.GraveEntity;
import net.exylia.exyliaGraves.manager.GravesManager;
import net.exylia.lib.log.Logs;
import net.exylia.lib.text.Values;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The window a grave opens into: its real contents, a stack per click, the
 * same window for everybody looking.
 *
 * <h2>Not a container</h2>
 * Every click in it is cancelled before Bukkit moves anything. A click asks for
 * a stack; the row is rewritten without it through {@link GravesManager#take},
 * and only a write that landed hands the stack over, by the player's id through
 * the reward store. The window is then redrawn from that row, so everybody
 * looking sees the same contents and nothing is ever in two places.
 *
 * <h2>One write at a time</h2>
 * A grave has one window, touched on the thread that owns its block. A click
 * while a write is in flight is ignored. A viewer whose click arrives on another
 * thread walked away with it open, and it is closed for them.
 */
public final class GraveWindows {

    /** Five rows of contents and a row of buttons; a death drops at most 41 stacks. */
    private static final int SIZE = 54;
    private static final int ITEM_SLOTS = 45;
    private static final int EXPERIENCE_SLOT = 48;
    private static final int TAKE_ALL_SLOT = 50;

    private final ExyliaGraves core;
    private final GravesManager manager;
    private final GravesService service;

    /** The window of every grave somebody is looking into, by grave id. */
    private final Map<String, Window> open = new ConcurrentHashMap<>();

    GraveWindows(ExyliaGraves core, GravesManager manager, GravesService service) {
        this.core = core;
        this.manager = manager;
        this.service = service;
    }

    /** One grave's window. */
    public static final class Window implements InventoryHolder {
        private final String id;
        private final Location where;
        private final Inventory inventory;
        private final AtomicBoolean busy = new AtomicBoolean();
        /** Stacks each player other than the owner took, told to the owner when they close it. */
        private final Map<UUID, Integer> taken = new ConcurrentHashMap<>();
        /** The grave as the window shows it. */
        private volatile GraveEntity grave;

        private Window(GraveEntity grave, Component title) {
            this.id = grave.id();
            this.where = grave.block();
            this.grave = grave;
            this.inventory = Bukkit.createInventory(this, SIZE, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /** Opens a grave's window to a player who may open it. On the block's thread. */
    void open(Player player, GraveEntity grave) {
        if (grave.block() == null) return;
        Window window = open.get(grave.id());
        if (window != null && !window.inventory.getViewers().isEmpty()) {
            if (!GraveSettings.get().collect().sharedWindow()) {
                core.getMessages().send(player, GraveMessages.get().windowBusy());
                return;
            }
        } else {
            window = new Window(grave, core.getMessages().item(GraveMessages.get().previewTitle(),
                    Values.of().put("player", grave.ownerName())));
            open.put(grave.id(), window);
            render(window, grave);
        }
        player.openInventory(window.inventory);
    }

    /** A click in a window, already cancelled: decides what, if anything, it takes. */
    public void click(InventoryClickEvent event, Window window) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != window.inventory) return;
        if (!core.getTasks().isOwnedBy(window.where)) {
            player.closeInventory();
            return;
        }
        GraveEntity grave = window.grave;
        ItemStack[] items = grave.items();
        int slot = event.getSlot();
        if (slot == TAKE_ALL_SLOT && GraveSettings.get().collect().takeAllButton()) {
            if (!window.busy.compareAndSet(false, true)) return;
            // Won: the grave is gone and its window closes with it.
            service.collect(player, grave).whenComplete((won, failure) -> window.busy.set(false));
        } else if (slot == EXPERIENCE_SLOT && grave.experience() > 0) {
            write(window, player, grave, items, 0, List.of(), grave.experience());
        } else if (slot < ITEM_SLOTS && items != null && slot < items.length && !GravesService.isEmpty(items[slot])) {
            ItemStack stack = items[slot];
            boolean equips = grave.owner().equals(player.getUniqueId()) && GraveSettings.get().collect().autoEquip();
            if (!fits(player.getInventory(), stack, equips)) {
                core.getMessages().send(player, GraveMessages.get().inventoryFull());
                return;
            }
            ItemStack[] rest = items.clone();
            rest[slot] = null;
            // The first stack the window had no room for moves into the hole, where it can be reached.
            for (int i = ITEM_SLOTS; i < rest.length; i++) {
                if (!GravesService.isEmpty(rest[i])) {
                    rest[slot] = rest[i];
                    rest[i] = null;
                    break;
                }
            }
            write(window, player, grave, rest, grave.experience(), List.of(stack), 0);
        }
    }

    /** Rewrites the grave to hold only this, and hands over the difference if the write lands. */
    private void write(Window window, Player player, GraveEntity grave, ItemStack[] items, int experience,
                       List<ItemStack> given, int points) {
        if (!window.busy.compareAndSet(false, true)) return;
        manager.take(grave, items, experience).whenComplete((taken, failure) ->
                core.getTasks().runAtLocation(window.where, () -> {
                    window.busy.set(false);
                    if (failure != null) {
                        core.getTasks().runAtEntity(player,
                                () -> core.getMessages().send(player, GraveMessages.get().claimFailed()));
                        return;
                    }
                    if (taken.isEmpty()) {
                        // It moved on: shown as it stands. One that is gone closed its window already.
                        GraveEntity now = manager.get(window.id);
                        if (now != null) render(window, now);
                        return;
                    }
                    GraveEntity after = taken.get();
                    if (!after.owner().equals(player.getUniqueId()) && !given.isEmpty()) {
                        window.taken.merge(player.getUniqueId(), given.size(), Integer::sum);
                    }
                    handOver(player, after, given, points);
                    // Emptied: it comes down, and its window closes with it.
                    if (after.count() == 0 && after.experience() <= 0) manager.claim(after);
                    else render(window, after);
                }));
    }

    /** Gives a player what a landed take took. Must not fail: it is already out of the row. */
    private void handOver(Player player, GraveEntity grave, List<ItemStack> given, int points) {
        boolean own = grave.owner().equals(player.getUniqueId());
        core.getLogs().record("grave-take", player.getUniqueId(), player.getName(), grave.owner(), grave.ownerName(),
                Logs.items(given) + (points > 0 ? (given.isEmpty() ? "" : ", ") + points + " xp" : ""),
                grave.block(), List.of(Logs.Group.of("Grave", given)));
        List<ItemStack> stacks = new ArrayList<>(given.size());
        for (ItemStack item : given) stacks.add(item.clone());
        Runnable here = () -> {
            List<ItemStack> rest = own && GraveSettings.get().collect().autoEquip() && core.getPlugin().isEnabled()
                    ? GravesService.equip(player.getInventory(), stacks) : stacks;
            core.getRewards().deliver(player.getUniqueId(), GravesService.contents(rest, points));
            player.playSound(player, points > 0 ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.ENTITY_ITEM_PICKUP,
                    0.6f, 1.2f);
        };
        // Gone or dead before their thread ran: it waits in the reward store.
        Runnable away = () -> core.getRewards().deliver(player.getUniqueId(), GravesService.contents(stacks, points));
        core.getTasks().runAtEntity(player, here, away);
    }

    /** Whether a stack goes into this inventory without waiting in the reward store. */
    static boolean fits(PlayerInventory inventory, ItemStack stack, boolean equips) {
        if (inventory.firstEmpty() >= 0) return true;
        if (equips) {
            EquipmentSlot slot = GravesService.slotFor(stack);
            if (slot != null && GravesService.isEmpty(inventory.getItem(slot))) return true;
        }
        int room = 0;
        for (ItemStack held : inventory.getStorageContents()) {
            if (held != null && held.isSimilar(stack)) room += Math.max(0, held.getMaxStackSize() - held.getAmount());
        }
        return room >= stack.getAmount();
    }

    /** Draws the grave into its window: a stack keeps its slot as others are taken. */
    private void render(Window window, GraveEntity grave) {
        window.grave = grave;
        Inventory inventory = window.inventory;
        ItemStack[] items = grave.items();
        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            ItemStack item = items != null && slot < items.length ? items[slot] : null;
            inventory.setItem(slot, GravesService.isEmpty(item) ? null : item.clone());
        }
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        for (int slot = ITEM_SLOTS; slot < SIZE; slot++) inventory.setItem(slot, filler);

        GraveMessages text = GraveMessages.get();
        Values values = Values.of().put("items", grave.count()).put("experience", grave.experience());
        if (grave.experience() > 0) {
            inventory.setItem(EXPERIENCE_SLOT, button(Material.EXPERIENCE_BOTTLE, text.windowExperienceName(),
                    text.windowExperienceLore(), values));
        }
        if (GraveSettings.get().collect().takeAllButton()) {
            inventory.setItem(TAKE_ALL_SLOT, button(Material.CHEST, text.windowTakeAllName(),
                    text.windowTakeAllLore(), values));
        }
    }

    private ItemStack button(Material material, String name, List<String> lore, Values values) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(core.getMessages().item(name, values));
            meta.lore(lore.stream().map(line -> core.getMessages().item(line, values)).toList());
        });
        return item;
    }

    /** A grave stopped standing: its window closes for everybody looking. Any thread. */
    void gone(GraveEntity grave) {
        Window window = open.remove(grave.id());
        if (window == null) return;
        for (HumanEntity viewer : List.copyOf(window.inventory.getViewers())) {
            core.getTasks().runAtEntity(viewer, () -> {
                if (viewer.getOpenInventory().getTopInventory().getHolder() == window) viewer.closeInventory();
            });
        }
    }

    /** Somebody closed a window: the owner hears what they took, and the last one out lets it go. */
    public void closed(Player player, Window window) {
        Integer took = window.taken.remove(player.getUniqueId());
        if (took != null) {
            GraveEntity grave = window.grave;
            core.getNotices().send(grave.owner(), "looted", GraveMessages.get().lootedNotice(), Map.of(
                    "player", player.getName(),
                    "items", took,
                    "world", grave.worldName() == null ? "?" : grave.worldName()));
        }
        // The viewer closing is still counted while the event runs.
        if (window.inventory.getViewers().size() <= 1) open.remove(window.id, window);
    }
}
