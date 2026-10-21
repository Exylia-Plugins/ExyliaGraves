package net.exylia.exyliaGraves.command;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.exyliaGraves.Permissions;
import net.exylia.exyliaGraves.config.GraveMessages;
import net.exylia.exyliaGraves.database.GraveEntity;
import net.exylia.exyliaGraves.menu.GravesMenu;
import net.exylia.lib.player.ExyliaPlayer;
import net.exylia.lib.text.Values;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Staff tools over anybody's graves, online, offline or on another server.
 *
 * <p>Every one of them takes a grave the way a player does, by deleting its row,
 * so staff handing a grave back while a looter clicks it hands it over once.
 */
@Command({"gravesadmin", "graveadmin"})
public class GravesAdminCommand {

    @CommandPlaceholder
    @CommandPermission(Permissions.ADMIN_VIEW)
    public void usage(CommandSender sender) {
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
        ExyliaGraves core = ExyliaGraves.get();
        core.getGraves().ofEverywhere(target.id()).whenComplete((graves, failure) -> core.getTasks().runAtEntity(admin, () -> {
            Values values = Values.of().put("player", target.name()).put("count", graves == null ? 0 : graves.size());
            if (failure != null) {
                core.getMessages().send(admin, GraveMessages.get().claimFailed());
                return;
            }
            if (graves.isEmpty()) {
                core.getMessages().send(admin, GraveMessages.get().noGraves(), values);
                return;
            }
            core.getInputs().answered(admin, core.getInputs().confirm(admin, values.apply(GraveMessages.get().restorePrompt()))
                            .dangerous().open(),
                    yes -> {
                        if (Boolean.TRUE.equals(yes)) restoreAll(core, admin, target, graves);
                    },
                    null);
        }));
    }

    private static void restoreAll(ExyliaGraves core, Player admin, ExyliaPlayer target, List<GraveEntity> graves) {
        List<CompletableFuture<Boolean>> taken = graves.stream()
                .map(grave -> core.getService().restore(admin, grave).exceptionally(failure -> false))
                .toList();
        CompletableFuture.allOf(taken.toArray(CompletableFuture[]::new)).thenRun(() -> {
            long count = taken.stream().filter(future -> Boolean.TRUE.equals(future.join())).count();
            core.getTasks().runAtEntity(admin, () -> core.getMessages().send(admin, GraveMessages.get().restoredAll(),
                    Values.of().put("player", target.name()).put("count", count)));
        });
    }

    @Subcommand("reload")
    @CommandPermission(Permissions.ADMIN_RELOAD)
    public void reload(CommandSender sender) {
        ExyliaGraves core = ExyliaGraves.get();
        core.reload(sender);
        core.getMessages().send(sender, GraveMessages.get().reloaded());
    }
}
