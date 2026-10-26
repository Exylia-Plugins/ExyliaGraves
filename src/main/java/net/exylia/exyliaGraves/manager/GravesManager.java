package net.exylia.exyliaGraves.manager;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.exyliaGraves.config.GraveMessages;
import net.exylia.exyliaGraves.config.GraveSettings;
import net.exylia.exyliaGraves.database.GraveEntity;
import net.exylia.lib.database.PluginDatabase;
import net.exylia.lib.database.Repository;
import net.exylia.lib.hologram.Hologram;
import net.exylia.lib.hologram.HologramConfig;
import net.exylia.lib.hologram.Holograms;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.TimeFormats;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Every grave standing on this server, where it stands, and the one way one is taken.
 *
 * <p>A grave is taken by deleting its row, and only whoever is told the delete
 * removed something gets the items. A stack is taken by rewriting the row
 * without it. Both only land while the row's revision is still the one the
 * caller read, so a copy that is behind a take never hands that take out again. Two players clicking the same head on the
 * same tick, the sweep expiring it while its owner opens it, staff on another
 * server handing it back while a looter clicks it: every race is decided by the
 * database once.
 *
 * <h2>Asked on every block event</h2>
 * Whether a block is a grave is asked for every break, piston and exploded
 * block on the server, and which graves are in a chunk for every chunk load
 * and unload. Both are one map lookup by world and a packed block or chunk
 * key, and answer at once when there are no graves at all.
 *
 * <h2>The picture follows the chunk</h2>
 * The head and the floating text are only a picture of the row. They are drawn
 * when a chunk loads and the text is forgotten when it unloads. A grave taken
 * while its chunk is not loaded cannot have its head removed yet, so the head
 * waits in a list for that chunk and comes down the moment it loads; a restart
 * in between leaves that one head standing, and a click on it says it is gone.
 *
 * <h2>Taken elsewhere</h2>
 * A grave taken from another server (staff handing it back, a second server
 * sharing the table) reaches this one through {@link PluginDatabase#onRemoteChange}
 * when Redis is on, and its picture comes down at once; without Redis, the first
 * click on it finds the row gone and takes it down then.
 */
public final class GravesManager {

    /** How long an expiry whose delete failed waits before it is tried again. */
    private static final long RETRY_MS = 60_000L;

    /** Once a second, so the countdown ticks; the library only resends lines that changed. */
    private static final long REFRESH_TICKS = 20L;

    /** How a write ended. */
    public enum Written {
        /** The row is there. */
        YES,
        /** Nothing was written, and what the row would have held is the caller's to hand back. */
        NO,
        /** The write may have landed and could not be undone; nothing is handed back. */
        UNKNOWN
    }

    private final ExyliaGraves core;
    private final Repository<GraveEntity> repo;
    private final String server;

    /** Every grave, by id. */
    private final Map<String, GraveEntity> graves = new ConcurrentHashMap<>();

    /** Grave ids by world name, then by packed block position. */
    private final Map<String, Map<Long, String>> byBlock = new ConcurrentHashMap<>();

    /** Grave ids by world name, then by chunk key. */
    private final Map<String, Map<Long, Set<String>>> byChunk = new ConcurrentHashMap<>();

    /** Heads of graves already taken, by world name and chunk key, waiting for their chunk to load. */
    private final Map<String, Map<Long, List<GraveEntity>>> undrawLater = new ConcurrentHashMap<>();

    /** The floating text above each grave that is drawn right now. */
    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();

    /** Graves whose delete is in flight. */
    private final Set<String> claiming = ConcurrentHashMap.newKeySet();

    /**
     * Graves whose first write has not answered yet. Nobody may take one: if
     * the write then fails, the items go back on the floor, and a take that
     * won in between would have handed them over as well.
     */
    private final Set<String> writing = ConcurrentHashMap.newKeySet();

    /** When an expiry that failed may be tried again, by grave id. */
    private final Map<String, Long> retryAt = new ConcurrentHashMap<>();

    /** Told about every grave that stops standing here, however it went. */
    private volatile Consumer<GraveEntity> onGone = grave -> {
    };

    public GravesManager(ExyliaGraves core, PluginDatabase database) {
        this.core = core;
        this.repo = database.repository(GraveEntity.class);
        this.server = core.getTeleports().serverId();
        database.onRemoteChange(GraveEntity.class, change -> {
            if (change.wholeTable()) {
                List.copyOf(graves.values()).forEach(this::recheck);
                return;
            }
            GraveEntity grave = change.id() == null ? null : graves.get(String.valueOf(change.id()));
            if (grave != null) recheck(grave);
        });
    }

    /** Reads this server's graves and draws the ones whose chunk is loaded. */
    public void load() {
        // Only this server's graves: the table is shared across the network
        // and a grave dug on another server has no block here to draw.
        repo.findAll().thenApply(all -> all.stream()
                .filter(grave -> grave.location() != null && core.getTeleports().isHere(grave.location()))
                .toList()).whenComplete((stored, failure) -> {
            if (failure != null) {
                core.getDebug().error("Could not read the graves; none are standing until a restart.", failure);
                return;
            }
            for (GraveEntity grave : stored) {
                if (!index(grave)) {
                    // Two graves on one block, from before a block could hold only one:
                    // kept so it still expires, and still shows in /graves.
                    graves.put(grave.id(), grave);
                }
            }
            core.getDebug().success("Loaded " + stored.size() + " grave(s).");
            for (GraveEntity grave : stored) {
                Location where = grave.block();
                if (where == null || !where.getWorld().isChunkLoaded(grave.blockX() >> 4, grave.blockZ() >> 4)) {
                    continue;
                }
                core.getTasks().runAtLocation(where, () -> {
                    if (where.isChunkLoaded()) draw(grave);
                });
            }
        });
    }

    /** A row another server changed: still there, or taken there, in which case it comes down here. */
    private void recheck(GraveEntity grave) {
        repo.findFresh(grave.id()).thenAccept(row -> {
            if (row.isPresent()) {
                graves.replace(grave.id(), row.get());
                return;
            }
            if (claiming.contains(grave.id())) return;
            forget(grave);
            takeDown(grave);
        });
    }

    /** @return whether the grave now holds its block; {@code false} when another grave already does */
    private boolean index(GraveEntity grave) {
        String world = grave.worldName();
        if (world == null) {
            graves.put(grave.id(), grave);
            return true;
        }
        String holder = byBlock.computeIfAbsent(world, name -> new ConcurrentHashMap<>())
                .putIfAbsent(blockKey(grave), grave.id());
        if (holder != null && !holder.equals(grave.id())) return false;
        graves.put(grave.id(), grave);
        byChunk.computeIfAbsent(world, name -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey(grave), key -> ConcurrentHashMap.newKeySet())
                .add(grave.id());
        return true;
    }

    private void forget(GraveEntity grave) {
        if (graves.remove(grave.id()) != null) onGone.accept(grave);
        retryAt.remove(grave.id());
        String world = grave.worldName();
        if (world == null) return;
        Map<Long, String> blocks = byBlock.get(world);
        if (blocks != null) blocks.remove(blockKey(grave), grave.id());
        Map<Long, Set<String>> chunks = byChunk.get(world);
        if (chunks != null) {
            chunks.computeIfPresent(chunkKey(grave), (key, ids) -> {
                ids.remove(grave.id());
                return ids.isEmpty() ? null : ids;
            });
        }
    }

    // ------------------------------------------------------------- writing

    /**
     * Stores a new grave and draws it.
     *
     * <p>A write that failed without an answer may still have committed, and
     * the row it leaves holds the items the caller is about to tip onto the
     * floor. So the row is removed first, and only a removal that answered lets
     * the caller drop them.
     *
     * @return completes with how the write ended; only {@link Written#NO}
     *         means the items are the caller's to drop
     */
    public CompletableFuture<Written> add(GraveEntity grave) {
        if (!index(grave)) return CompletableFuture.completedFuture(Written.NO);
        writing.add(grave.id());
        return repo.save(grave).handle((ignored, failure) -> {
            if (failure == null) {
                writing.remove(grave.id());
                drawn(grave);
                return CompletableFuture.completedFuture(Written.YES);
            }
            core.getDebug().error("Could not store the grave " + grave.id(), failure);
            return repo.delete(grave.id()).handle((removed, undo) -> {
                writing.remove(grave.id());
                if (undo == null) {
                    forget(grave);
                    return Written.NO;
                }
                core.getDebug().error("Could not remove the grave " + grave.id() + " after its write failed,"
                        + " so whether it holds anything is unknown; it is left standing.", undo);
                // Left standing instead: collecting a grave is a delete, so one
                // whose row never landed hands nothing over, and one whose row
                // did is still its owner's to collect.
                drawn(grave);
                return Written.UNKNOWN;
            });
        }).thenCompose(Function.identity());
    }

    /** Draws a grave on the thread that owns the block it stands on. */
    private void drawn(GraveEntity grave) {
        Location where = grave.block();
        if (where != null) core.getTasks().runAtLocation(where, () -> draw(grave));
    }

    /**
     * Takes a grave, if it is still there, on whichever server it stands.
     *
     * <p>The one way a grave ever leaves. Whoever is answered {@code true}
     * owns the items; the block and the text come down with it.
     *
     * @return completes with whether this caller took it; completes
     *         exceptionally when the database could not be asked, in which
     *         case the grave is still standing
     */
    public CompletableFuture<Boolean> claim(GraveEntity grave) {
        if (writing.contains(grave.id()) || !claiming.add(grave.id())) {
            return CompletableFuture.completedFuture(false);
        }
        return deleteIf(grave).whenComplete((removed, failure) -> {
            claiming.remove(grave.id());
            if (failure != null) {
                core.getDebug().error("Could not remove the grave " + grave.id(), failure);
                return;
            }
            if (!removed) {
                // Removed elsewhere, or a take moved it on since the caller read it.
                recheck(grave);
                return;
            }
            if (graves.containsKey(grave.id())) {
                forget(grave);
                takeDown(grave);
            }
        });
    }

    /** Deletes the row only while it is at the revision this copy was read at. */
    private CompletableFuture<Boolean> deleteIf(GraveEntity grave) {
        if (grave.revision() == 0) {
            // A row from before revisions: stamped first, by the same compare-and-set, so it can be named.
            GraveEntity stamped = grave.withRevision(1);
            return repo.updateIf(stamped, "revision", null)
                    .thenCompose(won -> won ? deleteIf(stamped) : CompletableFuture.completedFuture(false));
        }
        return repo.where("id", grave.id()).where("revision", grave.revision()).delete()
                .thenApply(removed -> removed > 0);
    }

    /**
     * Takes part of a grave: rewrites its row to hold only this, while it is
     * still at the revision this copy was read at.
     *
     * <p>Whoever is answered with the new grave owns what it no longer holds.
     * Never a row that is gone, never one being taken or still being written.
     *
     * @return completes with the grave as it now stands, or empty when the take
     *         did not land; exceptionally when the database could not be asked
     */
    public CompletableFuture<Optional<GraveEntity>> take(GraveEntity grave, ItemStack[] items, int experience) {
        if (writing.contains(grave.id()) || claiming.contains(grave.id())) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        GraveEntity next = grave.withContents(items, experience);
        return repo.updateIf(next, "revision", grave.storedRevision()).thenApply(won -> {
            if (!won) {
                recheck(grave);
                return Optional.empty();
            }
            graves.replace(grave.id(), next);
            return Optional.of(next);
        });
    }

    /** Who to tell when a grave stops standing here; on whatever thread it went. */
    public void onGone(Consumer<GraveEntity> listener) {
        onGone = listener;
    }

    /** Leaves an expiry that failed alone for a while, instead of failing again every second. */
    public void retryLater(GraveEntity grave) {
        retryAt.put(grave.id(), System.currentTimeMillis() + RETRY_MS);
    }

    // ------------------------------------------------------------- reading

    /** A grave on any server of the network, by id. */
    public CompletableFuture<Optional<GraveEntity>> find(String id) {
        GraveEntity here = graves.get(id);
        return here != null ? CompletableFuture.completedFuture(Optional.of(here)) : repo.findFresh(id);
    }

    /** A grave standing on this server as it was last written, or {@code null}. */
    public GraveEntity get(String id) {
        return graves.get(id);
    }

    /** The grave standing on a block, if one is. */
    public Optional<GraveEntity> at(Block block) {
        if (graves.isEmpty()) return Optional.empty();
        Map<Long, String> blocks = byBlock.get(block.getWorld().getName());
        String id = blocks == null ? null : blocks.get(block.getBlockKey());
        return id == null ? Optional.empty() : Optional.ofNullable(graves.get(id));
    }

    public boolean isGrave(Block block) {
        if (graves.isEmpty()) return false;
        Map<Long, String> blocks = byBlock.get(block.getWorld().getName());
        return blocks != null && blocks.containsKey(block.getBlockKey());
    }

    /** One player's graves on this server that are not being taken, oldest first. */
    public List<GraveEntity> of(UUID owner) {
        String id = owner.toString();
        List<GraveEntity> mine = new ArrayList<>();
        for (GraveEntity grave : graves.values()) {
            if (grave.ownerUuid().equals(id) && !claiming.contains(grave.id()) && !writing.contains(grave.id())) mine.add(grave);
        }
        mine.sort(Comparator.comparingLong(GraveEntity::createdAt));
        return mine;
    }

    /** One player's newest grave on this server. */
    public Optional<GraveEntity> latest(UUID owner) {
        List<GraveEntity> mine = of(owner);
        return mine.isEmpty() ? Optional.empty() : Optional.of(mine.get(mine.size() - 1));
    }

    /** One player's graves on every server of the network, oldest first. */
    public CompletableFuture<List<GraveEntity>> ofEverywhere(UUID owner) {
        return repo.where("ownerUuid", owner.toString()).find().thenApply(found -> found.stream()
                .sorted(Comparator.comparingLong(GraveEntity::createdAt))
                .toList());
    }

    /** Every grave whose time is up and that is not waiting out a failed try. */
    public List<GraveEntity> expired() {
        long now = System.currentTimeMillis();
        List<GraveEntity> over = new ArrayList<>();
        for (GraveEntity grave : graves.values()) {
            if (grave.isExpired() && !writing.contains(grave.id()) && retryAt.getOrDefault(grave.id(), 0L) <= now) over.add(grave);
        }
        return over;
    }

    /** Every grave standing in a chunk. */
    public List<GraveEntity> in(Chunk chunk) {
        if (graves.isEmpty()) return List.of();
        Map<Long, Set<String>> chunks = byChunk.get(chunk.getWorld().getName());
        Set<String> ids = chunks == null ? null : chunks.get(chunk.getChunkKey());
        if (ids == null || ids.isEmpty()) return List.of();
        List<GraveEntity> here = new ArrayList<>(ids.size());
        for (String id : ids) {
            GraveEntity grave = graves.get(id);
            if (grave != null) here.add(grave);
        }
        return here;
    }

    /** The server this manager's graves stand on. */
    public String server() {
        return server;
    }

    public int size() {
        return graves.size();
    }

    // ------------------------------------------------------------- drawing

    /**
     * A chunk has loaded: heads of graves taken while it was away come down,
     * and the graves standing in it are drawn. On the chunk's own thread.
     */
    public void chunkLoaded(Chunk chunk) {
        Map<Long, List<GraveEntity>> waiting = undrawLater.get(chunk.getWorld().getName());
        List<GraveEntity> stale = waiting == null ? null : waiting.remove(chunk.getChunkKey());
        if (stale != null) stale.forEach(this::undraw);
        in(chunk).forEach(this::draw);
    }

    /**
     * Puts the head and the text where the grave stands.
     *
     * <p>Must be called where the block is ticked. The head is skinned with the
     * owner's face, which is what makes a field of graves readable from a
     * distance. A grave taken in the meantime is not drawn back.
     */
    public void draw(GraveEntity grave) {
        if (!graves.containsKey(grave.id())) return;
        Location where = grave.block();
        if (where == null) return;
        Block block = where.getBlock();
        if (block.getType() != Material.PLAYER_HEAD) {
            // Somebody built here since the head went (WorldEdit, a reset, a crash):
            // their block wins, and the grave is only text until it is collected.
            if (!isFree(block)) {
                core.getDebug().warn("The grave " + grave.id() + " at " + grave.worldName() + " " + grave.blockX()
                        + " " + grave.blockY() + " " + grave.blockZ() + " has a " + block.getType()
                        + " where its head was; the block is left alone.");
                text(grave);
                return;
            }
            block.setType(Material.PLAYER_HEAD, false);
            if (block.getState() instanceof Skull skull) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(grave.owner()));
                skull.update(true, false);
            }
        }
        text(grave);
    }

    /** Whether a block can be replaced by a head without eating anything. */
    public static boolean isFree(Block block) {
        Material type = block.getType();
        if (type.isAir()) return true;
        return type == Material.WATER || type == Material.LAVA
                || type == Material.SHORT_GRASS || type == Material.TALL_GRASS
                || type == Material.FERN || type == Material.LARGE_FERN || type == Material.SNOW
                || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS || type == Material.DEAD_BUSH;
    }

    /**
     * What goes back when the head comes down: a water or lava source stays a
     * source, a waterlogged plant becomes water, anything else becomes air. Never
     * flowing fluid turned into a new source, nor half a tall plant.
     */
    public static String restoredAs(Block block) {
        Material type = block.getType();
        if ((type == Material.WATER || type == Material.LAVA)
                && block.getBlockData() instanceof org.bukkit.block.data.Levelled fluid && fluid.getLevel() == 0) {
            return type.name();
        }
        if (type == Material.SEAGRASS || type == Material.TALL_SEAGRASS) return Material.WATER.name();
        return Material.AIR.name();
    }

    /** Draws or refreshes the floating text. */
    private void text(GraveEntity grave) {
        GraveSettings.Hologram config = GraveSettings.get().hologram();
        if (!config.enabled()) return;
        Location where = grave.block();
        if (where == null) return;
        Values values = values(grave);
        List<String> lines = GraveMessages.get().hologramLines().stream().map(values::apply).toList();

        Hologram shown = holograms.get(grave.id());
        if (shown == null) {
            HologramConfig look = new HologramConfig(
                    true, HologramConfig.Kind.TEXT, lines, "", "", false, 48.0, 0, 0, 0,
                    new HologramConfig.Properties(), new HologramConfig.Refresh(REFRESH_TICKS, true));
            Location above = where.clone().add(0.5, config.offset(), 0.5);
            holograms.put(grave.id(), Holograms.show(core.getPlugin(), holoId(grave), above, look, Map.of()));
        } else {
            shown.lines(lines);
        }
    }

    /**
     * Takes a taken grave's picture down: now, on the block's thread, when its
     * chunk is loaded; otherwise the moment the chunk loads.
     */
    private void takeDown(GraveEntity grave) {
        hide(grave);
        Location where = grave.block();
        if (where == null) return;
        World world = where.getWorld();
        int chunkX = grave.blockX() >> 4;
        int chunkZ = grave.blockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            long chunk = Chunk.getChunkKey(chunkX, chunkZ);
            undrawLater.computeIfAbsent(world.getName(), name -> new ConcurrentHashMap<>())
                    .computeIfAbsent(chunk, key -> new CopyOnWriteArrayList<>())
                    .add(grave);
            // Loaded between the check and the list: its load already ran, so drain it here.
            if (!world.isChunkLoaded(chunkX, chunkZ)) return;
            core.getTasks().runAtLocation(where, () -> {
                Map<Long, List<GraveEntity>> waiting = undrawLater.get(world.getName());
                List<GraveEntity> stale = waiting == null ? null : waiting.remove(chunk);
                if (stale != null) stale.forEach(this::undraw);
            });
            return;
        }
        core.getTasks().runAtLocation(where, () -> undraw(grave));
    }

    /** Takes the head and the text down, putting back what was there. On the block's thread. */
    private void undraw(GraveEntity grave) {
        Location where = grave.block();
        if (where != null && where.isChunkLoaded()) {
            Block block = where.getBlock();
            // A new grave may already stand on the block this one left.
            if (block.getType() == Material.PLAYER_HEAD && !isGrave(block)) {
                Material original = Material.getMaterial(grave.originalBlock() == null ? "AIR" : grave.originalBlock());
                block.setType(original == null ? Material.AIR : original, false);
            }
        }
        hide(grave);
    }

    /** Takes only the text down, for a chunk going away. */
    public void hide(GraveEntity grave) {
        if (holograms.remove(grave.id()) != null) {
            Holograms.remove(core.getPlugin(), holoId(grave));
        }
    }

    /** Refreshes every drawn grave's text, for the timer; drops all of it when holograms were turned off. */
    public void refreshText() {
        boolean enabled = GraveSettings.get().hologram().enabled();
        for (String id : List.copyOf(holograms.keySet())) {
            GraveEntity grave = graves.get(id);
            if (grave == null) continue;
            if (enabled) text(grave);
            else hide(grave);
        }
    }

    private Values values(GraveEntity grave) {
        return Values.of()
                .put("player", grave.ownerName())
                .put("items", grave.count())
                .put("time_left", timeLeft(grave))
                .put("protection", protection(grave));
    }

    /** How long a grave has left, as players read it. */
    public static String timeLeft(GraveEntity grave) {
        long left = grave.remaining();
        return left < 0 ? GraveMessages.get().hologramForever() : time(left);
    }

    /**
     * Who may open a grave, as the hologram and the menu show it: open, owner
     * only for good, or protected with the time until it opens.
     */
    public static String protection(GraveEntity grave) {
        GraveMessages text = GraveMessages.get();
        if (!grave.isProtected()) return text.hologramOpen();
        if (grave.protectedUntil() < 0) return text.hologramProtected();
        long left = Math.max(0L, grave.protectedUntil() - System.currentTimeMillis());
        return Values.of("time_left", time(left)).apply(text.hologramProtectedFor());
    }

    /** A duration written out in full: {@code "1h 5m 3s"}. */
    public static String time(long millis) {
        return TimeFormats.render(millis / 1000.0, TimeFormats.Style.FULL);
    }

    private static String holoId(GraveEntity grave) {
        return "grave-" + grave.id();
    }

    private static long blockKey(GraveEntity grave) {
        return Block.getBlockKey(grave.blockX(), grave.blockY(), grave.blockZ());
    }

    private static long chunkKey(GraveEntity grave) {
        return Chunk.getChunkKey(grave.blockX() >> 4, grave.blockZ() >> 4);
    }

    /** Takes every picture down. The rows stay; they are redrawn on enable. */
    public void shutdown() {
        for (GraveEntity grave : List.copyOf(graves.values())) hide(grave);
        graves.clear();
        byBlock.clear();
        byChunk.clear();
        undrawLater.clear();
        claiming.clear();
        writing.clear();
        retryAt.clear();
    }
}
