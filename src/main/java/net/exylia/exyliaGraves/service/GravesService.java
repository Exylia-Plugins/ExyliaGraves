package net.exylia.exyliaGraves.service;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.exyliaGraves.Permissions;
import net.exylia.exyliaGraves.config.GraveMessages;
import net.exylia.exyliaGraves.config.GraveSettings;
import net.exylia.exyliaGraves.database.GraveEntity;
import net.exylia.exyliaGraves.manager.GravesManager;
import net.exylia.exyliaGraves.manager.PreviewHolder;
import net.exylia.lib.economy.Economy;
import net.exylia.lib.log.Logs;
import net.exylia.lib.session.DeathDrops;
import net.exylia.lib.session.Sessions;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.PermissionNumbers;
import net.exylia.lib.util.combat.Combat;
import net.exylia.lib.util.reward.RewardEntry;
import net.exylia.lib.util.reward.Rewards;
import net.exylia.lib.util.teleport.ExyliaLocation;
import net.exylia.lib.util.teleport.TeleportResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Dying into a grave, and getting it back.
 *
 * <h2>The drops go one way</h2>
 * The death event's drop list is emptied here, at {@code HIGH}, so a plugin that
 * scatters what is left at {@code HIGHEST} finds nothing to scatter. What went
 * into the row is exactly what would have hit the floor. A death another plugin
 * owns — a player held in an arena through {@link Sessions}, a duel declared
 * through {@link DeathDrops} — gets no grave: those drops are that plugin's.
 *
 * <h2>Collecting is one click and one delete</h2>
 * Collecting claims the row; if the claim wins, the items and the experience
 * go to the player by their id through the reward store — into their
 * inventory, or queued for their next join when they are full, dead (a grave
 * can stand in lava), gone or on another server. Taking a stack at a time goes
 * through {@link GraveWindows}, by the same rule: only a write that landed hands
 * anything over, and nothing is handed over twice.
 */
public final class GravesService {

    /** How far up from the death point a free block is looked for. */
    private static final int SEARCH_UP = 6;

    private final ExyliaGraves core;
    private final GravesManager manager;
    private final GraveWindows windows;

    public GravesService(ExyliaGraves core, GravesManager manager) {
        this.core = core;
        this.manager = manager;
        this.windows = new GraveWindows(core, manager, this);
        manager.onGone(windows::gone);
    }

    public GraveWindows windows() {
        return windows;
    }

    // -------------------------------------------------------------- dying

    /**
     * The first half of a death: the drops leave the list at {@code HIGH}.
     *
     * <p>Nothing is stored yet. A plugin listening later may still cancel the
     * death, keep the inventory or the level, or keep items through
     * {@code getItemsToKeep}; {@link #onDeathSettled} reads the event as it
     * finally stands at {@code MONITOR} and stores only what really left the
     * player. Does nothing when the player may not have a grave, when the world
     * is out, when the inventory is kept anyway, when another plugin owns the
     * death, or when there is nothing to keep.
     */
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        pending.remove(player.getUniqueId());
        if (event.getKeepInventory() || !player.hasPermission(Permissions.USE)) return;
        if (Sessions.holder(player).isPresent() || DeathDrops.claimedBy(player).isPresent()) return;
        GraveSettings settings = GraveSettings.get();
        World world = player.getWorld();
        if (settings.graves().isDisabled(world.getName())) return;

        Set<Material> ignored = settings.contents().ignored();
        Set<Material> keptMaterials = settings.contents().kept();
        List<ItemStack> kept = new ArrayList<>();
        List<ItemStack> stay = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir() || ignored.contains(drop.getType())) continue;
            if (keptMaterials.contains(drop.getType())) stay.add(drop);
            else kept.add(drop.clone());
        }
        int experience = experience(event, player, settings.contents());
        if (kept.isEmpty() && experience <= 0 && stay.isEmpty()) return;

        // Kept through death: out of the drops, back in the inventory on respawn.
        if (!stay.isEmpty()) {
            event.getItemsToKeep().addAll(stay);
            event.getDrops().removeAll(stay);
        }
        if (kept.isEmpty() && experience <= 0) return;

        Location where = spot(player.getLocation());
        if (where == null) return;

        // Ours now. Whatever scatters the drop list later must find nothing of what the grave holds.
        Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack drop = drops.next();
            if (drop != null && !drop.getType().isAir() && !ignored.contains(drop.getType())) drops.remove();
        }
        if (experience > 0) {
            event.setDroppedExp(0);
            if (settings.contents().experienceMode() == GraveSettings.ExperienceMode.ALL) {
                event.setNewExp(0);
                event.setNewLevel(0);
                event.setNewTotalExp(0);
            }
        }
        pending.put(player.getUniqueId(), new Pending(where, kept, experience));
    }

    /** What the first half of a death took, waiting for the event to settle. */
    private record Pending(Location where, List<ItemStack> items, int experience) {
    }

    /** Deaths between {@code HIGH} and {@code MONITOR}, by player; one event at a time on each player's thread. */
    private final java.util.Map<UUID, Pending> pending = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The second half, at {@code MONITOR} and whether or not it was cancelled:
     * stores the grave for what really left the player.
     *
     * <p>A cancelled death left everything where it was. A kept inventory kept
     * the items, a kept level the experience, and a stack added to
     * {@code getItemsToKeep} after this plugin looked stays with the player;
     * none of those go into the grave too.
     */
    public void onDeathSettled(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Pending taken = pending.remove(player.getUniqueId());
        if (taken == null || event.isCancelled()) return;
        List<ItemStack> kept = new ArrayList<>(event.getKeepInventory() ? List.of() : taken.items());
        for (ItemStack stays : event.getItemsToKeep()) {
            for (Iterator<ItemStack> it = kept.iterator(); it.hasNext(); ) {
                if (it.next().equals(stays)) {
                    it.remove();
                    break;
                }
            }
        }
        int experience = event.getKeepLevel() ? 0 : taken.experience();
        if (kept.isEmpty() && experience <= 0) return;

        GraveSettings settings = GraveSettings.get();
        Location where = taken.where();
        World world = where.getWorld();
        long now = System.currentTimeMillis();
        int expireMinutes = raised(player, Permissions.EXPIRE_PREFIX, settings.graves().expireTime());
        long expiresAt = expireMinutes > 0 ? now + TimeUnit.MINUTES.toMillis(expireMinutes) : -1L;
        int protect = settings.graves().protectionTime();
        long protectedUntil = protect < 0 ? -1L : protect == 0 ? 0L : now + TimeUnit.MINUTES.toMillis(protect);
        UUID killer = player.getKiller() == null ? null : player.getKiller().getUniqueId();

        GraveEntity grave = new GraveEntity(manager.server(), player.getUniqueId(), player.getName(), killer, where,
                GravesManager.restoredAs(where.getBlock()), kept.toArray(ItemStack[]::new), experience,
                expiresAt, protectedUntil);

        // Over the limit: the oldest one is tipped out where it stands. Graves
        // already being taken, or still being written, are not counted.
        int limit = raised(player, Permissions.LIMIT_PREFIX, settings.graves().maxPerPlayer());
        if (limit > 0) {
            List<GraveEntity> mine = manager.of(player.getUniqueId());
            while (mine.size() >= limit) {
                expire(mine.remove(0), true);
            }
        }

        manager.add(grave).thenAccept(stored -> {
            if (stored == GravesManager.Written.NO) {
                // A grave nothing stored is a grave nobody could collect: the
                // items go where they were going anyway. Only said when the
                // store is sure there is no row; an unsure one is left standing
                // rather than tipped out on top of a row that may hold it.
                core.getTasks().runAtLocation(where, () -> drop(where, kept, experience));
                return;
            }
            core.getLogs().record("grave-create", player.getUniqueId(), player.getName(), null, null,
                    describe(grave), where, List.of(Logs.Group.of("Grave", kept)));
            core.getTasks().runAtEntity(player, () -> core.getMessages().send(player, GraveMessages.get().created(),
                    Values.of()
                            .put("items", kept.size())
                            .put("x", where.getBlockX())
                            .put("y", where.getBlockY())
                            .put("z", where.getBlockZ())
                            .put("world", world.getName())
                            .put("time_left", GravesManager.timeLeft(grave))));
        });
    }

    /**
     * A configured number a permission can only raise: {@code -1} (no limit, never
     * expires) stays, and a node below the configured value is ignored.
     */
    private static int raised(Player player, String prefix, int configured) {
        if (configured < 0) return configured;
        return Math.max(configured, PermissionNumbers.highest(player, prefix, configured));
    }

    /** The experience a grave keeps, by the configured mode and percentage. */
    private static int experience(PlayerDeathEvent event, Player player, GraveSettings.Contents contents) {
        if (event.getKeepLevel()) return 0;
        int percent = Math.max(0, Math.min(100, contents.experiencePercent()));
        long points = switch (contents.experienceMode()) {
            case NONE -> 0;
            case VANILLA -> event.getDroppedExp();
            case ALL -> Experience.total(player.getLevel(), player.getExp());
        };
        return (int) Math.min(Integer.MAX_VALUE, points * percent / 100);
    }

    /**
     * Where the head goes.
     *
     * <p>The block the player died in, when it can be stood in; otherwise the
     * nearest one above it, which is what a death in a hole or a lake wants.
     * A death in the void goes on the highest block of that column. A block
     * that already holds a grave, or that something else owns, is never it.
     * {@code null} when there is no room at all, which sends the drops to the
     * floor as they would have gone.
     */
    private Location spot(Location death) {
        World world = death.getWorld();
        if (world == null) return null;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int y = death.getBlockY();
        if (y < minY) y = Math.max(minY, world.getHighestBlockYAt(death.getBlockX(), death.getBlockZ()) + 1);
        y = Math.max(minY, Math.min(maxY, y));
        for (int up = 0; up <= SEARCH_UP; up++) {
            int at = y + up;
            if (at > maxY) break;
            Block block = world.getBlockAt(death.getBlockX(), at, death.getBlockZ());
            if (GravesManager.isFree(block) && !manager.isGrave(block)
                    && net.exylia.lib.block.Blocks.at(block.getLocation()) == null) {
                return block.getLocation();
            }
        }
        return null;
    }

    // --------------------------------------------------------- collecting

    /**
     * Somebody clicked a grave.
     *
     * @param use what that click is set to do
     * @return whether the click was on a grave at all and did something, so the
     *         caller can cancel the interaction
     */
    public boolean use(Player player, Block block, GraveSettings.Use use) {
        if (use == GraveSettings.Use.NONE) return false;
        GraveEntity grave = manager.at(block).orElse(null);
        if (grave == null) return false;
        switch (use) {
            case PREVIEW -> preview(player, grave);
            case OPEN -> {
                if (allowed(player, grave)) windows.open(player, grave);
            }
            default -> {
                if (allowed(player, grave)) collect(player, grave);
            }
        }
        return true;
    }

    /** Whether this player may open this grave, telling them why not when they may not. */
    private boolean allowed(Player player, GraveEntity grave) {
        if (mayOpen(player, grave)) return true;
        long until = grave.protectedUntil();
        core.getMessages().send(player, until < 0
                        ? GraveMessages.get().protectedGraveForever()
                        : GraveMessages.get().protectedGrave(),
                Values.of()
                        .put("player", grave.ownerName())
                        .put("time_left", GravesManager.time(Math.max(0L, until - System.currentTimeMillis()))));
        return false;
    }

    /**
     * Takes everything left in a grave for a player who may open it.
     *
     * @return completes with whether this call took it
     */
    public CompletableFuture<Boolean> collect(Player player, GraveEntity grave) {
        return manager.claim(grave).whenComplete((won, failure) -> {
            if (failure != null) {
                core.getTasks().runAtEntity(player, () -> core.getMessages().send(player, GraveMessages.get().claimFailed()));
            } else if (!Boolean.TRUE.equals(won)) {
                core.getTasks().runAtEntity(player, () -> core.getMessages().send(player, GraveMessages.get().gone()));
            } else {
                deliver(player, grave);
            }
        });
    }

    /** Whether this player may open this grave right now. */
    public static boolean mayOpen(Player player, GraveEntity grave) {
        return player.hasPermission(Permissions.BYPASS_PROTECTION)
                || grave.canOpen(player.getUniqueId(), GraveSettings.get().graves().killerCanLoot());
    }

    /** Shows a copy of what a grave holds; nothing can be taken out of it. */
    public void preview(Player player, GraveEntity grave) {
        boolean staff = player.hasPermission(Permissions.ADMIN_VIEW);
        if (!staff && (!player.hasPermission(Permissions.PREVIEW) || !mayOpen(player, grave))) {
            core.getMessages().send(player, GraveMessages.get().cannotPreview());
            return;
        }
        PreviewHolder.open(player, core.getMessages().item(GraveMessages.get().previewTitle(),
                Values.of().put("player", grave.ownerName())), grave.items());
    }

    /**
     * Hands a grave's contents to whoever won it. Any thread.
     *
     * <p>The row is already gone, so this must not fail: everything goes by the
     * player's id, and only the message, the sound and putting armour back on
     * need them to be here.
     */
    private void deliver(Player player, GraveEntity grave) {
        boolean own = grave.owner().equals(player.getUniqueId());
        List<ItemStack> items = grave.items() == null ? List.of() : Arrays.asList(grave.items());
        core.getLogs().record(own ? "grave-collect" : "grave-loot", player.getUniqueId(), player.getName(),
                grave.owner(), grave.ownerName(), describe(grave), grave.block(),
                List.of(Logs.Group.of("Grave", items)));

        Values values = Values.of().put("items", grave.count())
                .put("experience", grave.experience())
                .put("player", grave.ownerName());
        Runnable here = () -> {
            List<ItemStack> rest = own && GraveSettings.get().collect().autoEquip() && core.getPlugin().isEnabled()
                    ? equip(player.getInventory(), stacks(grave)) : stacks(grave);
            core.getRewards().deliver(player.getUniqueId(), contents(rest, grave.experience()));
            core.getMessages().send(player, own ? GraveMessages.get().collected() : GraveMessages.get().looted(), values);
        };
        // Gone or dead before their thread ran: everything waits in the reward store.
        Runnable away = () -> core.getRewards().deliver(player.getUniqueId(), contents(stacks(grave), grave.experience()));
        core.getTasks().runAtEntity(player, here, away);

        if (!own) {
            core.getNotices().send(grave.owner(), "looted", GraveMessages.get().lootedNotice(), java.util.Map.of(
                    "player", player.getName(),
                    "items", grave.count(),
                    "world", grave.worldName() == null ? "?" : grave.worldName()));
        }
    }

    /**
     * Puts armour and the offhand item straight back on, into empty slots only.
     * On the player's thread.
     *
     * @return what is left to deliver
     */
    static List<ItemStack> equip(PlayerInventory inventory, List<ItemStack> stacks) {
        if (stacks.isEmpty()) return stacks;
        List<ItemStack> rest = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            EquipmentSlot slot = slotFor(stack);
            if (slot != null && isEmpty(inventory.getItem(slot))) {
                inventory.setItem(slot, stack);
            } else {
                rest.add(stack);
            }
        }
        return rest;
    }

    static EquipmentSlot slotFor(ItemStack stack) {
        EquipmentSlot slot = stack.getType().getEquipmentSlot();
        return switch (slot) {
            case HEAD, CHEST, LEGS, FEET, OFF_HAND -> slot;
            default -> null;
        };
    }

    static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private static List<ItemStack> stacks(GraveEntity grave) {
        List<ItemStack> stacks = new ArrayList<>();
        if (grave.items() != null) {
            for (ItemStack item : grave.items()) {
                if (item != null && !item.getType().isAir()) stacks.add(item.clone());
            }
        }
        return stacks;
    }

    /** Items and experience, as rewards the store can hold for later. */
    static List<RewardEntry> contents(List<ItemStack> stacks, int experience) {
        List<RewardEntry> owed = new ArrayList<>();
        for (ItemStack item : stacks) {
            owed.add(RewardEntry.item(Rewards.snapshot(item)).fixedAmount(item.getAmount()).build());
        }
        if (experience > 0) owed.add(RewardEntry.experience(experience).build());
        return owed;
    }

    static String describe(GraveEntity grave) {
        return Logs.items(grave.items() == null ? List.of() : Arrays.asList(grave.items()))
                + (grave.experience() > 0 ? ", " + grave.experience() + " xp" : "");
    }

    // ---------------------------------------------------------------- staff

    /**
     * Hands a grave back to its owner, on whichever server it stands.
     *
     * @return completes with whether this call took it
     */
    public CompletableFuture<Boolean> restore(CommandSender staff, GraveEntity seen) {
        return claimCurrent(seen).thenApply(grave -> {
            if (grave == null) return false;
            core.getLogs().record("grave-restore", staff, grave.owner(), grave.ownerName(), describe(grave));
            core.getRewards().deliver(grave.owner(), contents(stacks(grave), grave.experience()));
            return true;
        });
    }

    /**
     * Removes a grave and everything in it, on whichever server it stands. The
     * staff log keeps what it held.
     *
     * @return completes with whether this call took it
     */
    public CompletableFuture<Boolean> remove(CommandSender staff, GraveEntity seen) {
        return claimCurrent(seen).thenApply(grave -> {
            if (grave == null) return false;
            core.getLogs().record("grave-remove", staff, grave.owner(), grave.ownerName(), describe(grave));
            return true;
        });
    }

    /**
     * Takes a grave as it stands now rather than as staff saw it: stacks taken
     * from its window since then are not handed out a second time.
     *
     * @return completes with the grave this call took, or {@code null}
     */
    private CompletableFuture<GraveEntity> claimCurrent(GraveEntity seen) {
        return manager.find(seen.id()).thenCompose(found -> found
                .map(grave -> manager.claim(grave).thenApply(won -> Boolean.TRUE.equals(won) ? grave : null))
                .orElseGet(() -> CompletableFuture.completedFuture(null)));
    }

    // ------------------------------------------------------------ expiring

    /** Sends every grave whose time is up on its way, and refreshes the countdowns. */
    public void sweep() {
        boolean drop = GraveSettings.get().graves().dropsOnExpire();
        for (GraveEntity grave : manager.expired()) expire(grave, drop);
        manager.refreshText();
    }

    /**
     * Ends a grave, tipping it out or losing it.
     *
     * <p>Through the same claim as a collect, so a grave being opened at this
     * exact moment is not also dropped on the floor. A delete that failed
     * leaves the grave standing and is tried again later.
     */
    private void expire(GraveEntity grave, boolean drop) {
        manager.claim(grave).whenComplete((won, failure) -> {
            if (failure != null) {
                manager.retryLater(grave);
                return;
            }
            if (!Boolean.TRUE.equals(won)) return;
            if (drop) tipOut(grave);
            core.getLogs().record(drop ? "grave-expire" : "grave-delete", null, null, grave.owner(), grave.ownerName(),
                    describe(grave), grave.block(), List.of());
            core.getNotices().send(grave.owner(), "expired",
                    drop ? GraveMessages.get().expiredDropped() : GraveMessages.get().expiredDeleted(),
                    java.util.Map.of("items", grave.count()));
        });
    }

    /**
     * Puts an expired grave's contents on the floor where it stood.
     *
     * <p>A chunk that is not loaded is loaded in the background first, never
     * on the spot, which would stall the thread for a disk read. A world that
     * is gone from this server has no floor, so its owner is handed the lot.
     */
    private void tipOut(GraveEntity grave) {
        Location where = grave.block();
        if (where == null) {
            core.getRewards().deliver(grave.owner(), contents(stacks(grave), grave.experience()));
            return;
        }
        List<ItemStack> items = stacks(grave);
        Runnable tip = () -> drop(where, items, grave.experience());
        World world = where.getWorld();
        if (world.isChunkLoaded(grave.blockX() >> 4, grave.blockZ() >> 4)) {
            core.getTasks().runAtLocation(where, tip);
            return;
        }
        world.getChunkAtAsync(grave.blockX() >> 4, grave.blockZ() >> 4).thenAccept(chunk -> {
            if (core.getTasks().isOwnedBy(where)) tip.run();
            else core.getTasks().runAtLocation(where, tip);
        });
    }

    /** Puts items and experience on the floor. Must run where the block is. */
    private static void drop(Location where, List<ItemStack> items, int experience) {
        World world = where.getWorld();
        if (world == null) return;
        Location centre = where.clone().add(0.5, 0.5, 0.5);
        for (ItemStack item : items) {
            world.dropItemNaturally(centre, item);
        }
        if (experience > 0) {
            world.spawn(centre, ExperienceOrb.class, orb -> orb.setExperience(experience));
        }
    }

    // --------------------------------------------------------- teleporting

    /**
     * {@code /graves} → a grave: goes there, on whichever server it stands,
     * for a price.
     *
     * <p>Charged before the teleport starts, and refunded when it does not
     * arrive: a warmup cancelled by moving or a hit, a server that cannot be
     * reached. Staff with {@code admin.view} go to anybody's, for free.
     */
    public void teleport(Player player, GraveEntity grave) {
        boolean staff = player.hasPermission(Permissions.ADMIN_VIEW);
        GraveSettings.Teleport settings = GraveSettings.get().teleport();
        if (!staff) {
            if (!grave.owner().equals(player.getUniqueId())) return;
            if (!settings.enabled()) {
                core.getMessages().send(player, GraveMessages.get().teleportDisabled());
                return;
            }
            if (!player.hasPermission(Permissions.TELEPORT)) {
                core.getMessages().send(player, GraveMessages.get().noPermission());
                return;
            }
            if (Combat.isTagged(player)) {
                core.getMessages().send(player, GraveMessages.get().inCombat(),
                        Values.of().put("time", GravesManager.time(Combat.remaining(player).toMillis())));
                return;
            }
        }
        ExyliaLocation at = grave.location();
        if (at == null) {
            core.getMessages().send(player, GraveMessages.get().gone());
            return;
        }
        UUID id = player.getUniqueId();
        BigDecimal cost = staff || player.hasPermission(Permissions.TELEPORT_FREE)
                ? BigDecimal.ZERO : BigDecimal.valueOf(Math.max(0.0, settings.cost()));
        boolean charged = cost.signum() > 0;
        String currency = settings.currencyId();
        Values values = Values.of().put("cost", Economy.format(currency, cost));
        // Runs on the player's own thread (the action hops there), so item and xp currencies can be taken.
        if (charged && (!Economy.isAvailable() || !Economy.of(currency == null ? "" : currency).charge(id, cost))) {
            core.getMessages().send(player, GraveMessages.get().notEnoughMoney(), values);
            return;
        }
        ExyliaLocation above = new ExyliaLocation(at.server(), at.world(),
                grave.blockX() + 0.5, grave.blockY() + 1.0, grave.blockZ() + 0.5, at.yaw(), at.pitch());
        core.getTeleports().to(player, above)
                .then(result -> {
                    if (result.isSuccess()) {
                        core.getMessages().send(player, GraveMessages.get().teleported(), values);
                        return;
                    }
                    if (charged) {
                        if (Economy.of(currency == null ? "" : currency).deposit(id, cost).isSuccess()) {
                            core.getMessages().send(player, GraveMessages.get().teleportRefunded(), values);
                        } else {
                            core.getDebug().error("Could not refund " + cost + " to " + player.getName()
                                    + " for a grave teleport that did not happen.");
                        }
                    }
                    explain(player, result);
                })
                .start();
    }

    /** The endings the library has no line of its own for. */
    private void explain(Player player, TeleportResult result) {
        GraveMessages text = GraveMessages.get();
        switch (result) {
            case CROSS_SERVER_UNAVAILABLE -> core.getMessages().send(player, text.serverUnreachable());
            case WORLD_NOT_FOUND -> core.getMessages().send(player, text.worldNotLoaded());
            case NO_SAFE_LOCATION, FAILED, TARGET_NOT_FOUND -> core.getMessages().send(player, text.teleportFailed());
            default -> {
            }
        }
    }
}
