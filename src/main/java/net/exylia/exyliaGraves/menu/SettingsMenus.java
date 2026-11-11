package net.exylia.exyliaGraves.menu;

import net.exylia.exyliaGraves.ExyliaGraves;
import net.exylia.exyliaGraves.config.GraveSettings;
import net.exylia.lib.config.ConfigEdits;
import net.exylia.lib.ui.ClickKind;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings an admin really tunes, chosen and explained by hand: a hub with one card per
 * theme, each opening a page. Everything else stays in {@code config.yml}.
 *
 * <p>Every page is {@code menus/admin/settings_<page>}; its values are {@code %<page>_<key>%}
 * (the YAML key with dots and dashes as underscores) and its buttons run
 * {@code exyliagraves:setting <page> <key>}.
 */
public final class SettingsMenus {

    public static final String HUB = "menus/admin/settings";

    /** The hub card values: page id to the keys its card shows. */
    private static final Map<String, List<String>> PAGES = new LinkedHashMap<>();

    static {
        PAGES.put("graves", List.of("graves.expire-time", "graves.max-per-player"));
        PAGES.put("contents", List.of("contents.experience", "contents.experience-percent"));
        PAGES.put("collect", List.of("collect.right-click", "collect.shared-window"));
        PAGES.put("more", List.of("teleport.enabled", "teleport.cost"));
    }

    private SettingsMenus() {
        throw new AssertionError("No instances.");
    }

    private static ConfigEdits edits() {
        return ConfigEdits.of(ExyliaGraves.get().getPlugin(), GraveSettings::file);
    }

    public static void openHub(Player admin) {
        Map<String, Object> context = new LinkedHashMap<>();
        PAGES.forEach((id, keys) -> context.putAll(edits().placeholders(id + "_", keys)));
        ExyliaGraves.get().getMenus().open(admin, HUB, context);
    }

    public static void openPage(Player admin, String id) {
        if (!PAGES.containsKey(id)) return;
        ExyliaGraves.get().getMenus().open(admin, HUB + "_" + id, new LinkedHashMap<>(edits().placeholders(id + "_")));
    }

    public static void click(Player admin, String page, String key, ClickKind click) {
        if (!PAGES.containsKey(page)) return;
        edits().card(admin, key, click, () -> openPage(admin, page));
    }
}
