package net.exylia.exyliaGraves.service;

/** Experience points from a level and the progress bar, by the game's own curve. */
final class Experience {

    private Experience() {
        throw new AssertionError("No instances.");
    }

    /**
     * Every point a player holds.
     *
     * <p>Not {@code Player#getTotalExperience}, which counts what was ever earned
     * and forgets what enchanting spent.
     *
     * @param level    the level reached
     * @param progress how far into the next level, 0 to 1
     */
    static long total(int level, float progress) {
        if (level <= 0) return Math.round(Math.max(0f, progress) * toNext(0));
        return atLevel(level) + Math.round(Math.max(0f, Math.min(1f, progress)) * toNext(level));
    }

    /** The points it takes to reach a level from zero. */
    static long atLevel(int level) {
        long l = level;
        if (l <= 16) return l * l + 6 * l;
        if (l <= 31) return Math.round(2.5 * l * l - 40.5 * l + 360);
        return Math.round(4.5 * l * l - 162.5 * l + 2220);
    }

    /** The points from a level to the next one. */
    static long toNext(int level) {
        if (level <= 15) return 2L * level + 7;
        if (level <= 30) return 5L * level - 38;
        return 9L * level - 158;
    }
}
