package net.exylia.exyliaGraves;

import net.exylia.exyliaGraves.action.GraveActions;
import net.exylia.exyliaGraves.command.GravesAdminCommand;
import net.exylia.exyliaGraves.command.GravesCommand;
import net.exylia.exyliaGraves.command.PlayerArguments;
import net.exylia.exyliaGraves.config.GraveMessages;
import net.exylia.exyliaGraves.config.GraveSettings;
import net.exylia.exyliaGraves.listener.GravesListener;
import net.exylia.exyliaGraves.manager.GravesManager;
import net.exylia.exyliaGraves.menu.GraveMenus;
import net.exylia.exyliaGraves.placeholder.GravePlaceholders;
import net.exylia.exyliaGraves.service.GravesService;
import net.exylia.lib.action.Actions;
import net.exylia.lib.action.PluginActions;
import net.exylia.lib.config.Configs;
import net.exylia.lib.database.Databases;
import net.exylia.lib.database.PluginDatabase;
import net.exylia.lib.debug.Debug;
import net.exylia.lib.input.Inputs;
import net.exylia.lib.input.PluginInputs;
import net.exylia.lib.log.Logs;
import net.exylia.lib.notice.Notices;
import net.exylia.lib.reload.Reloads;
import net.exylia.lib.task.TaskScheduler;
import net.exylia.lib.task.Tasks;
import net.exylia.lib.text.PluginMessages;
import net.exylia.lib.text.Prefixes;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.reward.OverflowPolicy;
import net.exylia.lib.util.reward.PendingRewards;
import net.exylia.lib.util.reward.PluginRewards;
import net.exylia.lib.util.reward.Rewards;
import net.exylia.lib.util.teleport.PluginTeleports;
import net.exylia.lib.util.teleport.Teleports;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.bukkit.BukkitLamp;

/**
 * ExyliaGraves: a grave where you died, holding what you dropped, taken
 * exactly once anywhere on the network.
 *
 * <p>Not the Bukkit plugin itself: {@link ExyliaGravesPlugin} is, so it can
 * install ExyliaLib when the server lacks it. Every library view is asked for
 * with {@link #getPlugin()}, because the library keys its per-plugin views by
 * Bukkit plugin.
 */
public final class ExyliaGraves {

    /** The namespace the menu buttons' actions are written against. */
    public static final String NAMESPACE = "exyliagraves";

    private static ExyliaGraves instance;

    private JavaPlugin plugin;
    private TaskScheduler tasks;
    private PluginMessages messages;
    private Debug debug;
    private PluginInputs inputs;
    private PluginRewards rewards;
    private PluginActions actions;
    private PluginTeleports teleports;
    private Notices notices;
    private Logs logs;
    private GraveMenus menus;
    private Reloads reloads;
    private GravesManager graves;
    private GravesService service;

    /** The running instance; only valid between {@link #start} and {@link #shutdown}. */
    public static ExyliaGraves get() {
        return instance;
    }

    /** Starts everything on the Bukkit plugin that loaded this core. */
    public void start(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;

        debug = Debug.of(plugin);
        messages = PluginMessages.of(plugin);
        tasks = Tasks.of(plugin);
        inputs = Inputs.of(plugin);
        teleports = Teleports.of(plugin);
        logs = Logs.of(plugin)
                .action("grave-create", "SKELETON_SKULL")
                .action("grave-collect", "PLAYER_HEAD")
                .action("grave-loot", "IRON_SWORD")
                .action("grave-take", "HOPPER")
                .action("grave-expire", "CLOCK")
                .action("grave-delete", "LAVA_BUCKET")
                .action("grave-restore", "CHEST")
                .action("grave-remove", "BARRIER");
        // A grave's contents go into the inventory, or wait in the table for the
        // player's next join: full, dead, offline or on another server.
        rewards = Rewards.of(plugin)
                .overflow(OverflowPolicy.QUEUE)
                .pending(PendingRewards.database(plugin))
                .claimOnJoin((viewer, delivery) -> {
                    if (delivery.given() <= 0) return;
                    messages.send(viewer, GraveMessages.get().rewardsDelivered(),
                            Values.of().put("amount", delivery.given()));
                });
        notices = Notices.of(plugin);

        loadConfigs();

        PluginDatabase database = Databases.of(plugin);
        graves = new GravesManager(this, database);
        service = new GravesService(this, graves);

        actions = Actions.of(plugin, NAMESPACE);
        new GraveActions(this).registerAll();
        // After the actions: compiling a menu resolves every action its buttons name.
        menus = new GraveMenus(this, NAMESPACE);
        menus.load();

        new GravesListener(this);
        GravePlaceholders.register(this);

        var lamp = PlayerArguments.install(BukkitLamp.builder(plugin)).build();
        lamp.register(new GravesCommand());
        lamp.register(new GravesAdminCommand());

        reloads = Reloads.of(plugin)
                .step("configs", () -> {
                    Configs.reloadAll(plugin);
                    applyConfigs();
                })
                .stepAlsoOnLibraryReload("menus", menus::reload);

        // After the first tick: database futures never start before it on Paper.
        tasks.run(graves::load);
        // Expiry and the countdowns above the graves, every second.
        tasks.runTimer(20L, 20L, service::sweep);

        debug.motd();
    }

    /** Takes the floating text down. The rows and the heads stay, and are drawn again on the next start. */
    public void shutdown() {
        // A preview or a window outlives its listener otherwise, and what it shows could be taken out.
        for (org.bukkit.entity.Player online : plugin.getServer().getOnlinePlayers()) {
            Object holder = online.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof net.exylia.exyliaGraves.manager.PreviewHolder
                    || holder instanceof net.exylia.exyliaGraves.service.GraveWindows.Window) {
                online.closeInventory();
            }
        }
        if (graves != null) graves.shutdown();
        // Everything else the library owns for this plugin — menus, actions, inputs, placeholders,
        // tasks, channels — is released by the library on plugin disable.
        instance = null;
    }

    /**
     * Reads the files.
     *
     * <p>{@code config.yml} first: its {@code language} decides which folder the messages and
     * menus come from.
     */
    private void loadConfigs() {
        GraveSettings.install(Configs.define(plugin, "config", GraveSettings.class).version(1).load());
        GraveMessages.install(Configs.define(plugin, "messages", GraveMessages.class).translated().load());
        applyConfigs();
    }

    /** What has to follow every read of the files. */
    private void applyConfigs() {
        Prefixes.set(plugin, GraveMessages.get().prefix());
    }

    /** Reloads this plugin's files and menus. */
    public void reload(CommandSender sender) {
        reloads.run(sender);
    }

    public JavaPlugin getPlugin() { return plugin; }
    public TaskScheduler getTasks() { return tasks; }
    public PluginMessages getMessages() { return messages; }
    public Debug getDebug() { return debug; }
    public PluginInputs getInputs() { return inputs; }
    public PluginRewards getRewards() { return rewards; }
    public PluginActions getActions() { return actions; }
    public PluginTeleports getTeleports() { return teleports; }
    public Notices getNotices() { return notices; }
    public Logs getLogs() { return logs; }
    public GraveMenus getMenus() { return menus; }
    public GravesManager getGraves() { return graves; }
    public GravesService getService() { return service; }
}
