package net.exylia.exyliaGraves.command;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.exyliaGraves.Permissions;
import net.exylia.exyliaGraves.action.GraveActions;
import net.exylia.exyliaGraves.config.GraveMessages;
import net.exylia.exyliaGraves.menu.AdminPlayersMenu;
import net.exylia.exyliaGraves.menu.GravesMenu;
import net.exylia.lib.player.ExyliaPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

/**
 * Staff tools over anybody's graves, online, offline or on another server.
 *
 * <p>Every one of them takes a grave the way a player does, by deleting its row,
 * so staff handing a grave back while a looter clicks it hands it over once.
 */
@Command({"gravesadmin", "graveadmin"})
public class GravesAdminCommand {

    private final GraveActions actions;

    public GravesAdminCommand(GraveActions actions) {
        this.actions = actions;
    }

    /** Players get the online players to pick from; the console gets the usage. */
    @CommandPlaceholder
    @CommandPermission(Permissions.ADMIN_VIEW)
    public void usage(CommandSender sender) {
        if (sender instanceof Player admin) {
            AdminPlayersMenu.open(admin);
            return;
        }
        ExyliaGraves.get().getMessages().send(sender, GraveMessages.get().adminUsage(), null);
    }

    /** Somebody's graves, to look inside, go to, hand back or remove. */
    @Subcommand("list")
    @CommandPermission(Permissions.ADMIN_VIEW)
    public void list(Player admin, ExyliaPlayer target) {
        GravesMenu.open(admin, target.id(), target.name(), true);
    }

    /** Hands every grave a player has, on every server, back to them after a confirmation. */
    @Subcommand("restore")
    @CommandPermission(Permissions.ADMIN_RESTORE)
    public void restore(Player admin, ExyliaPlayer target) {
        actions.restoreAll(admin, target.id(), target.name(), () -> { });
    }

    @Subcommand("reload")
    @CommandPermission(Permissions.ADMIN_RELOAD)
    public void reload(CommandSender sender) {
        ExyliaGraves core = ExyliaGraves.get();
        core.reload(sender);
        core.getMessages().send(sender, GraveMessages.get().reloaded());
    }
}
