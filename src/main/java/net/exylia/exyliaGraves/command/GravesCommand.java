package net.exylia.exyliaGraves.command;

import net.exylia.exyliaGraves.Permissions;
import net.exylia.exyliaGraves.menu.GravesMenu;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.bukkit.annotation.CommandPermission;

/** {@code /graves}: your graves on every server, with how long each has left. */
@Command({"graves", "grave"})
public class GravesCommand {

    @CommandPlaceholder
    @CommandPermission(Permissions.MENU)
    public void graves(Player player) {
        // No graves is the menu's own empty state, not a chat line.
        GravesMenu.open(player);
    }
}
