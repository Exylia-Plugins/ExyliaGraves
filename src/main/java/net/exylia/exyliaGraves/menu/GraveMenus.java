package net.exylia.exyliaGraves.menu;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.lib.config.Languages;
import net.exylia.lib.ui.Menus;
import net.exylia.lib.ui.PluginMenus;
import net.exylia.lib.ui.UiDefinition;
import net.exylia.lib.ui.UiEntry;
import net.exylia.lib.ui.UiSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * This plugin's screens: read once from the jar, opened cheaply.
 *
 * <p>The graves list under {@code menus/user/} is the owner's to restyle: a
 * release only adds what is missing there. The admin screens under
 * {@code menus/admin/} are the plugin's and are rewritten on every start.
 */
public final class GraveMenus {

    private static final List<String> IDS = List.of(GravesMenu.ID, GravesMenu.ADMIN_ID, AdminPlayersMenu.ID);

    private final ExyliaGraves core;
    private final PluginMenus menus;

    public GraveMenus(ExyliaGraves core, String namespace) {
        this.core = core;
        this.menus = Menus.of(core.getPlugin(), namespace);
    }

    /** Writes the packaged screens out and compiles every one. */
    public void load() {
        Languages.replace(core.getPlugin(), ExyliaGraves.class, "menus/admin");
        Languages.refresh(core.getPlugin(), ExyliaGraves.class, "menus/user");
        for (String id : IDS) {
            YamlConfiguration config = read(id);
            if (config != null) menus.load(id, config);
        }
    }

    /**
     * Reads one menu file, or reports why it could not be read.
     *
     * <p>Parsed here rather than through {@code YamlConfiguration.loadConfiguration}, which
     * swallows a broken file and hands back an empty one.
     */
    private YamlConfiguration read(String id) {
        File file = Languages.file(core.getPlugin(), id + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException failure) {
            core.getDebug().error("The menu \"" + id + "\" could not be read, so it is not"
                    + " registered. Fix " + file.getPath() + " and reload.", failure);
            return null;
        }
    }

    /** Forgets every compiled screen and reads them all again. */
    public void reload() {
        menus.unload();
        load();
    }

    /**
     * Opens a menu with its list already filled, on the player's thread.
     *
     * <p>Through {@code openNow} so the rows are in the menu before it is drawn.
     */
    public void open(Player player, String id, Map<String, Object> context, List<UiEntry> rows) {
        Optional<UiDefinition> definition = menus.definition(id);
        if (definition.isEmpty()) {
            core.getDebug().warn("Something asked to open the menu \"" + id + "\", which is not loaded.");
            return;
        }
        Map<String, List<UiEntry>> sections = Map.of(UiSection.MAIN, rows);
        if (core.getTasks().isOwnedBy(player)) {
            menus.openNow(player, definition.get(), context, sections);
            return;
        }
        core.getTasks().runAtEntity(player, () -> menus.openNow(player, definition.get(), context, sections));
    }

    /** Opens a menu whose list is read again on every redraw, from any thread. */
    public void open(Player player, String id, Map<String, Object> context, Supplier<List<UiEntry>> rows) {
        menus.open(player, id, context, rows);
    }

    /** Closes whatever menu of this plugin the player has open. */
    public void close(Player player) {
        menus.close(player);
    }
}
