package net.exylia.exyliaGraves.menu;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.exyliaGraves.Permissions;
import net.exylia.exyliaGraves.config.GraveMessages;
import net.exylia.exyliaGraves.config.GraveSettings;
import net.exylia.exyliaGraves.database.GraveEntity;
import net.exylia.exyliaGraves.manager.GravesManager;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Where somebody's graves are, on every server of the network, and how long they have.
 *
 * <p>Read from the table rather than from this server's graves, so a player who
 * died on another server still finds that grave, and which server it is on. The
 * countdowns are lambdas, so they keep ticking while the screen is open.
 */
public final class GravesMenu {

    /** The player's own list, as {@link GraveMenus} registers it. */
    public static final String ID = "menus/user/graves";

    /** Staff's view onto anybody's graves. */
    public static final String ADMIN_ID = "menus/admin/graves";

    private GravesMenu() {
        throw new AssertionError("No instances.");
    }

    /** A player's own graves. */
    public static void open(Player player) {
        open(player, player.getUniqueId(), player.getName(), false);
    }

    /** Somebody's graves, with the staff actions. */
    public static void open(Player viewer, UUID owner, String ownerName, boolean admin) {
        ExyliaGraves core = ExyliaGraves.get();
        core.getGraves().ofEverywhere(owner).whenComplete((graves, failure) -> core.getTasks().runAtEntity(viewer, () -> {
            if (!viewer.isOnline()) return;
            if (failure != null) {
                core.getMessages().send(viewer, GraveMessages.get().claimFailed());
                return;
            }
            String actions = actions(viewer, admin);
            List<UiEntry> rows = new ArrayList<>(graves.size());
            for (GraveEntity grave : graves) rows.add(row(core.getGraves(), grave, actions));
            Values context = Values.of()
                    .put("owner_name", ownerName)
                    .put("owner_id", owner.toString())
                    .put("graves", graves.size())
                    .put("restore_all", admin && !graves.isEmpty() && viewer.hasPermission(Permissions.ADMIN_RESTORE));
            core.getMenus().open(viewer, admin ? ADMIN_ID : ID, context.map(), rows);
        }));
    }

    private static UiEntry row(GravesManager manager, GraveEntity grave, String actions) {
        String server = grave.location() == null || grave.location().server() == null
                ? manager.server() : grave.location().server();
        return UiEntry.of(grave.id())
                .withFormatted("grave_id", grave.id())
                .withFormatted("grave_owner", grave.ownerName())
                .withFormatted("grave_items", grave.count())
                .withFormatted("grave_experience", grave.experience())
                .withFormatted("grave_server", server)
                .withFormatted("grave_world", grave.worldName() == null ? "?" : grave.worldName())
                .withFormatted("grave_x", grave.blockX())
                .withFormatted("grave_y", grave.blockY())
                .withFormatted("grave_z", grave.blockZ())
                .with("grave_actions", actions)
                .with("grave_time_left", () -> GravesManager.timeLeft(grave))
                .with("grave_protection", () -> GravesManager.protection(grave))
                .with("grave_age", () -> GravesManager.time(System.currentTimeMillis() - grave.createdAt()))
                .build();
    }

    /** The click prompts this viewer gets, each on its own line, or nothing. */
    private static String actions(Player viewer, boolean admin) {
        GraveMessages text = GraveMessages.get();
        List<String> lines = new ArrayList<>();
        if (admin || (GraveSettings.get().teleport().enabled() && viewer.hasPermission(Permissions.TELEPORT))) {
            lines.add(text.menuTeleport());
        }
        if (admin || viewer.hasPermission(Permissions.PREVIEW)) lines.add(text.menuPreview());
        if (admin && viewer.hasPermission(Permissions.ADMIN_RESTORE)) lines.add(text.menuRestore());
        if (admin && viewer.hasPermission(Permissions.ADMIN_REMOVE)) lines.add(text.menuRemove());
        lines.removeIf(line -> line == null || line.isBlank());
        return lines.isEmpty() ? "" : "<nl><nl>" + String.join("<nl>", lines);
    }
}
