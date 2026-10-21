package net.exylia.exyliaGraves.database;

import net.exylia.lib.database.Column;
import net.exylia.lib.database.Id;
import net.exylia.lib.database.Indexed;
import net.exylia.lib.database.Table;
import net.exylia.lib.util.teleport.ExyliaLocation;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * What somebody dropped when they died, kept where they died.
 *
 * <h2>The row is the items</h2>
 * The drops leave the death event and go here, and here only. There is no
 * window onto a grave — collecting it is one click that deletes this row and
 * acts on whether the delete removed anything — so the items are never in two
 * places, not even for a tick.
 *
 * @param originalBlock the material that stood where the grave's block now
 *                      stands, put back when it goes; {@code "AIR"} for most
 * @param killerUuid    who made the kill, or {@code null} for a fall or a mob
 */
@Table("sc_graves")
public record GraveEntity(
        @Id(length = 36) String id,
        @Indexed @Column(length = 36) String ownerUuid,
        @Column(length = 64) String ownerName,
        @Column(length = 36) String killerUuid,
        @Column(length = Column.UNBOUNDED) ExyliaLocation location,
        @Column(length = 64) String originalBlock,
        @Column(length = Column.UNBOUNDED) ItemStack[] items,
        @Column int experience,
        @Column("created_at") long createdAt,
        @Column long expiresAt,
        @Column long protectedUntil) {

    public GraveEntity(String server, UUID owner, String ownerName, UUID killer, Location where, String originalBlock,
                       ItemStack[] items, int experience, long expiresAt, long protectedUntil) {
        this(UUID.randomUUID().toString(),
                owner.toString(),
                ownerName,
                killer == null ? null : killer.toString(),
                ExyliaLocation.of(server, where),
                originalBlock,
                items,
                experience,
                System.currentTimeMillis(),
                expiresAt,
                protectedUntil);
    }

    public UUID owner() {
        return UUID.fromString(ownerUuid);
    }

    /** Whether the grave's time is up. */
    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    /** How long it has left, or {@code -1} when it never expires. */
    public long remaining() {
        return expiresAt <= 0 ? -1L : Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    /** Whether only the owner may open it right now. */
    public boolean isProtected() {
        return protectedUntil < 0 || System.currentTimeMillis() < protectedUntil;
    }

    /**
     * Whether this player may open it.
     *
     * @param who         who is asking
     * @param killerCanLoot whether the killer is let in during protection
     */
    public boolean canOpen(UUID who, boolean killerCanLoot) {
        if (who.toString().equals(ownerUuid)) return true;
        if (!isProtected()) return true;
        // Protected for good means the owner's alone, killer or not.
        if (protectedUntil < 0) return false;
        return killerCanLoot && killerUuid != null && killerUuid.equals(who.toString());
    }

    /** How many stacks are in it. */
    public int count() {
        if (items == null) return 0;
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) count++;
        }
        return count;
    }

    /** The block it stands on, when its world is loaded. */
    public Location block() {
        return location == null ? null : location.toBukkitLocation();
    }

    /** The world it stands in, by name, whether or not that world is loaded here. */
    public String worldName() {
        return location == null ? null : location.world();
    }

    public int blockX() {
        return location == null ? 0 : (int) Math.floor(location.x());
    }

    public int blockY() {
        return location == null ? 0 : (int) Math.floor(location.y());
    }

    public int blockZ() {
        return location == null ? 0 : (int) Math.floor(location.z());
    }
}
