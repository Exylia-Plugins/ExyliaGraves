package net.exylia.exyliaGraves.listener;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.exyliaGraves.Permissions;
import net.exylia.exyliaGraves.config.GraveMessages;
import net.exylia.exyliaGraves.config.GraveSettings;
import net.exylia.exyliaGraves.manager.GravesManager;
import net.exylia.exyliaGraves.manager.PreviewHolder;
import net.exylia.exyliaGraves.service.GravesService;
import net.exylia.lib.text.Values;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/**
 * The events a grave has to answer.
 *
 * <p>The death is taken at {@code HIGH}, on purpose one step before
 * {@code HIGHEST}, where death plugins scatter whatever is still in the drop
 * list: by then this has emptied it.
 */
public final class GravesListener implements Listener {

    private final ExyliaGraves core;
    private final GravesManager manager;
    private final GravesService service;

    public GravesListener(ExyliaGraves core) {
        this.core = core;
        this.manager = core.getGraves();
        this.service = core.getService();
        core.getPlugin().getServer().getPluginManager().registerEvents(this, core.getPlugin());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        service.onDeath(event);
    }

    /** Not ignoreCancelled: a death cancelled after {@code HIGH} must still drop what was set aside. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathSettled(PlayerDeathEvent event) {
        service.onDeathSettled(event);
    }

    /** A click on the head collects it; sneaking looks inside instead. Main hand only. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !manager.isGrave(block)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        boolean preview = player.isSneaking() && GraveSettings.get().collect().sneakToPreview()
                && (player.hasPermission(Permissions.PREVIEW) || player.hasPermission(Permissions.ADMIN_VIEW));
        service.collect(player, block, preview);
    }

    /** A grave is not a block you mine. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!manager.isGrave(event.getBlock())) return;
        event.setCancelled(true);
        core.getMessages().send(event.getPlayer(), GraveMessages.get().cannotBreak());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(manager::isGrave);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(manager::isGrave);
    }

    /** A wither, an enderman or falling sand does not get to break or take a grave either. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (manager.isGrave(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (manager.isGrave(event.getBlock())) event.setCancelled(true);
    }

    /** Water or lava flowing into the head would wash it off as an item. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (manager.isGrave(event.getToBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (touchesGrave(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (touchesGrave(event.getBlocks())) event.setCancelled(true);
    }

    private boolean touchesGrave(List<Block> blocks) {
        for (Block block : blocks) {
            if (manager.isGrave(block)) return true;
        }
        return false;
    }

    /** Nothing moves in a preview, from either inventory, by any click. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreviewClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreviewDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewHolder) event.setCancelled(true);
    }

    /** Again last, over any plugin in between that un-cancelled the click. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreviewClickLast(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreviewDragLast(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewHolder) event.setCancelled(true);
    }

    /** Back on their feet: where the newest grave is, and the compass pointed at it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GraveSettings.Respawn settings = GraveSettings.get().respawn();
        if (!settings.pointCompass() && !settings.remind()) return;
        manager.latest(player.getUniqueId()).ifPresent(grave -> {
            Location where = grave.block();
            if (where == null) return;
            // A tick later: the respawn location is only settled once the event is over.
            core.getTasks().runAtEntity(player, () -> {
                if (settings.pointCompass() && where.getWorld().equals(player.getWorld())) {
                    player.setCompassTarget(where);
                }
                if (settings.remind()) {
                    core.getMessages().send(player, GraveMessages.get().respawnReminder(), Values.of()
                            .put("x", grave.blockX()).put("y", grave.blockY()).put("z", grave.blockZ())
                            .put("world", grave.worldName()));
                }
            });
        });
    }

    /** The picture is drawn where the world is, and only there. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.chunkLoaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        manager.in(event.getChunk()).forEach(manager::hide);
    }
}
