package net.exylia.exyliaGraves.action;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.exyliaGraves.Permissions;
import net.exylia.exyliaGraves.config.GraveMessages;
import net.exylia.exyliaGraves.database.GraveEntity;
import net.exylia.exyliaGraves.menu.GravesMenu;
import net.exylia.lib.action.ActionResult;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.text.Values;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The {@code exyliagraves:*} actions the screens bind.
 *
 * <p>A button only names a grave. Whether the clicker may act on it is asked
 * again here, against the row as it is stored: owning it, or the staff
 * permission for that action.
 */
public final class GraveActions {

    private final ExyliaGraves core;

    public GraveActions(ExyliaGraves core) {
        this.core = core;
    }

    public void registerAll() {
        PluginActions actions = core.getActions();
        actions.registerSync("teleport", (ctx, args) -> {
            Player player = ctx.player();
            grave(player, args.string(0, ""), grave -> {
                core.getMenus().close(player);
                core.getService().teleport(player, grave);
            });
            return ActionResult.success();
        });
        actions.registerSync("preview", (ctx, args) -> {
            Player player = ctx.player();
            grave(player, args.string(0, ""), grave -> core.getService().preview(player, grave));
            return ActionResult.success();
        });
        actions.registerSync("restore", Permissions.ADMIN_RESTORE, (ctx, args) -> {
            Player player = ctx.player();
            grave(player, args.string(0, ""), grave -> confirm(player, grave, GraveMessages.get().restorePrompt(),
                    GraveMessages.get().restored(), (staff, target) -> core.getService().restore(staff, target)));
            return ActionResult.success();
        });
        actions.registerSync("remove", Permissions.ADMIN_REMOVE, (ctx, args) -> {
            Player player = ctx.player();
            grave(player, args.string(0, ""), grave -> confirm(player, grave, GraveMessages.get().removePrompt(),
                    GraveMessages.get().removed(), (staff, target) -> core.getService().remove(staff, target)));
            return ActionResult.success();
        });
    }

    /** Reads the grave a button names and hands it over on the player's thread, or says it is gone. */
    private void grave(Player player, String id, Consumer<GraveEntity> then) {
        if (id.isBlank()) return;
        core.getGraves().find(id).whenComplete((found, failure) -> core.getTasks().runAtEntity(player, () -> {
            if (failure != null) {
                core.getMessages().send(player, GraveMessages.get().claimFailed());
            } else if (found.isEmpty()) {
                core.getMessages().send(player, GraveMessages.get().gone());
                GravesMenu.open(player);
            } else {
                then.accept(found.get());
            }
        }));
    }

    /** Asks staff to confirm, runs the action, and returns them to that player's graves. */
    private void confirm(Player staff, GraveEntity grave, String prompt, String done,
                         BiFunction<Player, GraveEntity, CompletableFuture<Boolean>> action) {
        Values values = Values.of().put("player", grave.ownerName()).put("count", 1);
        Runnable back = () -> GravesMenu.open(staff, grave.owner(), grave.ownerName(), true);
        core.getMenus().close(staff);
        core.getInputs().answered(staff, core.getInputs().confirm(staff, values.apply(prompt)).dangerous().open(),
                yes -> {
                    if (!Boolean.TRUE.equals(yes)) {
                        back.run();
                        return;
                    }
                    action.apply(staff, grave).whenComplete((won, failure) -> core.getTasks().runAtEntity(staff, () -> {
                        core.getMessages().send(staff, failure != null ? GraveMessages.get().claimFailed()
                                : Boolean.TRUE.equals(won) ? done : GraveMessages.get().gone(), values);
                        back.run();
                    }));
                },
                back);
    }
}
