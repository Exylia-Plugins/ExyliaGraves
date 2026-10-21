package net.exylia.exyliaGraves.service;

import net.exylia.exyliaGraves.config.GraveSettings;
import net.exylia.exyliaGraves.database.GraveEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveRulesTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID KILLER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private static GraveEntity grave(long expiresAt, long protectedUntil) {
        return new GraveEntity("g", OWNER.toString(), "Owner", KILLER.toString(), null, "AIR", null, 0,
                System.currentTimeMillis(), expiresAt, protectedUntil, 0L);
    }

    @Test
    void experienceFollowsTheGameCurve() {
        assertEquals(0, Experience.total(0, 0f));
        assertEquals(7, Experience.atLevel(1));
        assertEquals(352, Experience.atLevel(16));
        assertEquals(1395, Experience.atLevel(30));
        assertEquals(1507, Experience.atLevel(31));
        assertEquals(1628, Experience.atLevel(32));
        // The bar at level 30 holds 112 points.
        assertEquals(Experience.atLevel(30) + 56, Experience.total(30, 0.5f));
        assertEquals(Experience.atLevel(31), Experience.atLevel(30) + Experience.toNext(30));
    }

    @Test
    void protectionLetsOnlyTheOwnerAndTheKillerIn() {
        GraveEntity grave = grave(-1L, System.currentTimeMillis() + 60_000L);
        assertTrue(grave.canOpen(OWNER, false));
        assertTrue(grave.canOpen(KILLER, true));
        assertFalse(grave.canOpen(KILLER, false));
        assertFalse(grave.canOpen(STRANGER, true));
    }

    @Test
    void foreverProtectedAndOpenGraves() {
        assertFalse(grave(-1L, -1L).canOpen(STRANGER, true));
        assertFalse(grave(-1L, -1L).canOpen(KILLER, true));
        assertTrue(grave(-1L, -1L).canOpen(OWNER, false));
        assertTrue(grave(-1L, 0L).canOpen(STRANGER, false));
    }

    @Test
    void everyTakeMovesTheRevisionOn() {
        GraveEntity legacy = grave(-1L, 0L);
        // A row from before revisions holds an empty column, and is compared as one.
        assertNull(legacy.storedRevision());
        GraveEntity taken = legacy.withContents(null, 5);
        assertEquals(1L, taken.storedRevision());
        assertEquals(2L, taken.withContents(null, 0).revision());
        assertEquals(5, taken.experience());
    }

    @Test
    void clicksFallBackToTheirDefaults() {
        GraveSettings.Collect collect = new GraveSettings.Collect(true, "preview", "nonsense", "NONE", null, true, true);
        assertEquals(GraveSettings.Use.PREVIEW, collect.use(true, false));
        assertEquals(GraveSettings.Use.COLLECT, collect.use(true, true));
        assertEquals(GraveSettings.Use.NONE, collect.use(false, false));
        assertEquals(GraveSettings.Use.COLLECT, collect.use(false, true));
    }

    @Test
    void expiry() {
        assertEquals(-1L, grave(-1L, 0L).remaining());
        assertFalse(grave(-1L, 0L).isExpired());
        assertTrue(grave(System.currentTimeMillis() - 1L, 0L).isExpired());
    }
}
