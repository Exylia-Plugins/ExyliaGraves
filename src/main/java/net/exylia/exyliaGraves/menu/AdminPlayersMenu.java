package net.exylia.exyliaGraves.menu;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The staff hub of {@code /gravesadmin}: the players online here, each opening their graves.
 *
 * <p>The list is a lambda, so players joining or leaving show up while it is open.
 */
public final class AdminPlayersMenu {

    public static final String ID = "menus/admin/players";

    private AdminPlayersMenu() {
        throw new AssertionError("No instances.");
    }

    public static void open(Player viewer) {
        ExyliaGraves.get().getMenus().open(viewer, ID, Map.of(), AdminPlayersMenu::rows);
    }

    private static List<UiEntry> rows() {
        return Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(player -> UiEntry.of(player.getUniqueId().toString())
                        .with("player_id", player.getUniqueId().toString())
                        .with("player_name", player.getName())
                        .build())
                .toList();
    }
}
