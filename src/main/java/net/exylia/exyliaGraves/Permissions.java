package net.exylia.exyliaGraves;

/**
 * The permission nodes this plugin checks, as plugin.yml declares them.
 */
public final class Permissions {

    /** Get a grave when you die. Without it the drops fall as they always did. */
    public static final String USE = "exyliagraves.use";

    /** List your graves with {@code /graves}. */
    public static final String MENU = "exyliagraves.menu";

    /** Look inside a grave you may open, without taking anything. */
    public static final String PREVIEW = "exyliagraves.preview";

    /** Teleport to your graves from {@code /graves}, when teleporting is on. */
    public static final String TELEPORT = "exyliagraves.teleport";

    /** Teleport to them for free. */
    public static final String TELEPORT_FREE = "exyliagraves.teleport.free";

    /** How many graves a player may have standing: {@code exyliagraves.limit.<n>}, the largest wins. */
    public static final String LIMIT_PREFIX = "exyliagraves.limit.";

    /** How many minutes their graves last: {@code exyliagraves.expire.<minutes>}, the largest wins. */
    public static final String EXPIRE_PREFIX = "exyliagraves.expire.";

    /** How many minutes only the owner may open their graves: {@code exyliagraves.protection.<minutes>}, the largest wins. */
    public static final String PROTECTION_PREFIX = "exyliagraves.protection.";

    /** The percentage of experience their graves keep: {@code exyliagraves.experience.<percent>}, the largest wins. */
    public static final String EXPERIENCE_PREFIX = "exyliagraves.experience.";

    /** What teleporting to a grave costs them: {@code exyliagraves.teleport.cost.<amount>}, the smallest wins. */
    public static final String TELEPORT_COST_PREFIX = "exyliagraves.teleport.cost.";

    /** Open anybody's grave, protected or not. */
    public static final String BYPASS_PROTECTION = "exyliagraves.bypass.protection";

    /** Anybody's graves: list, look inside and teleport to them. */
    public static final String ADMIN_VIEW = "exyliagraves.admin.view";

    /** Hand a grave's contents back to its owner. */
    public static final String ADMIN_RESTORE = "exyliagraves.admin.restore";

    /** Remove a grave and everything in it. */
    public static final String ADMIN_REMOVE = "exyliagraves.admin.remove";

    /** Reload the files. */
    public static final String ADMIN_RELOAD = "exyliagraves.admin.reload";

    private Permissions() {
        throw new AssertionError("No instances.");
    }
}
