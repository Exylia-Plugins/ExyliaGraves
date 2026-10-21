package net.exylia.exyliaGraves.placeholder;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.exyliaGraves.database.GraveEntity;
import net.exylia.exyliaGraves.manager.GravesManager;
import net.exylia.lib.placeholder.Placeholders;

import java.util.Optional;
import java.util.function.Function;

/**
 * {@code %exyliagraves_...%}: a player's graves on this server, read from memory.
 *
 * <pre>
 * %exyliagraves_count%        how many graves they have standing here
 * %exyliagraves_latest_world% the world of their newest grave, empty without one
 * %exyliagraves_latest_x%     its block coordinates, likewise _y and _z
 * %exyliagraves_latest_time%  how long it has left
 * </pre>
 */
public final class GravePlaceholders {

    private GravePlaceholders() {
        throw new AssertionError("No instances.");
    }

    public static void register(ExyliaGraves core) {
        GravesManager graves = core.getGraves();
        Placeholders.group(core.getPlugin(), "exyliagraves")
                .describe("A player's graves on this server and where the newest one is")
                .add("count", request -> String.valueOf(graves.of(request.requireViewer().getUniqueId()).size()))
                .add("latest_world", request -> latest(graves, request.requireViewer().getUniqueId(), GraveEntity::worldName))
                .add("latest_x", request -> latest(graves, request.requireViewer().getUniqueId(), grave -> String.valueOf(grave.blockX())))
                .add("latest_y", request -> latest(graves, request.requireViewer().getUniqueId(), grave -> String.valueOf(grave.blockY())))
                .add("latest_z", request -> latest(graves, request.requireViewer().getUniqueId(), grave -> String.valueOf(grave.blockZ())))
                .add("latest_time", request -> latest(graves, request.requireViewer().getUniqueId(), GravesManager::timeLeft))
                .register();
    }

    private static String latest(GravesManager graves, java.util.UUID owner, Function<GraveEntity, String> read) {
        Optional<GraveEntity> grave = graves.latest(owner);
        return grave.map(read).orElse("");
    }
}
