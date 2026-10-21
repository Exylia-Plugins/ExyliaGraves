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

import java.util.List;
import java.util.UUID;
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
        actions.registerSync("restore_all", Permissions.ADMIN_RESTORE, (ctx, args) -> {
            Player player = ctx.player();
            UUID owner = uuid(args.string(0, ""));
            if (owner == null) return ActionResult.success();
            String name = args.string(1, "?");
            core.getMenus().close(player);
            restoreAll(player, owner, name, () -> GravesMenu.open(player, owner, name, true));
            return ActionResult.success();
        });
        actions.registerSync("admin_graves", Permissions.ADMIN_VIEW, (ctx, args) -> {
            UUID owner = uuid(args.string(0, ""));
            if (owner != null) GravesMenu.open(ctx.player(), owner, args.string(1, "?"), true);
            return ActionResult.success();
        });
        actions.registerSync("remove", Permissions.ADMIN_REMOVE, (ctx, args) -> {
            Player player = ctx.player();
            grave(player, args.string(0, ""), grave -> confirm(player, grave, GraveMessages.get().removePrompt(),
                    GraveMessages.get().removed(), (staff, target) -> core.getService().remove(staff, target)));
            return ActionResult.success();
        });
    }

    /**
     * Hands every grave a player has, on every server, back to them after a confirmation,
     * then runs {@code back} on the staff member's thread.
     */
    public void restoreAll(Player staff, UUID owner, String ownerName, Runnable back) {
        core.getGraves().ofEverywhere(owner).whenComplete((graves, failure) -> core.getTasks().runAtEntity(staff, () -> {
            Values values = Values.of().put("player", ownerName).put("count", graves == null ? 0 : graves.size());
            if (failure != null) {
                core.getMessages().send(staff, GraveMessages.get().claimFailed());
                back.run();
                return;
            }
            if (graves.isEmpty()) {
                core.getMessages().send(staff, GraveMessages.get().noGraves(), values);
                back.run();
                return;
            }
            core.getInputs().answered(staff, core.getInputs().confirm(staff, values.apply(GraveMessages.get().restorePrompt()))
                            .dangerous().open(),
                    yes -> {
                        if (Boolean.TRUE.equals(yes)) restoreEach(staff, ownerName, graves, back);
                        else back.run();
                    },
                    back);
        }));
    }

    private void restoreEach(Player staff, String ownerName, List<GraveEntity> graves, Runnable back) {
        List<CompletableFuture<Boolean>> taken = graves.stream()
                .map(grave -> core.getService().restore(staff, grave).exceptionally(failure -> false))
                .toList();
        CompletableFuture.allOf(taken.toArray(CompletableFuture[]::new)).thenRun(() -> {
            long count = taken.stream().filter(future -> Boolean.TRUE.equals(future.join())).count();
            core.getTasks().runAtEntity(staff, () -> {
                core.getMessages().send(staff, GraveMessages.get().restoredAll(),
                        Values.of().put("player", ownerName).put("count", count));
                back.run();
            });
        });
    }

    private static UUID uuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
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
