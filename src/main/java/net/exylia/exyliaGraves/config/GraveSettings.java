package net.exylia.exyliaGraves.config;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Languages;
import net.exylia.lib.config.Time;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Settings stored in {@code config.yml}.
 *
 * <p>Read as fields of the loaded snapshot, never looked up by key: the material
 * lists are asked for every stack of every death.
 */
public record GraveSettings(
        @Comment("Language of this plugin's menus and messages. 'default' follows the language set in")
        @Comment("ExyliaLib's config.yml; en, es, pt, fr, ru or tr set this plugin alone. Each one is a folder under")
        @Comment("lang/ you can edit.")
        String language,

        @Comment("")
        @Comment("How long a grave stands, who may open it, and how many a player may have.")
        Graves graves,

        @Comment("")
        @Comment("What goes into a grave.")
        Contents contents,

        @Comment("")
        @Comment("Collecting a grave.")
        Collect collect,

        @Comment("")
        @Comment("Going back to a grave from /graves.")
        Teleport teleport,

        @Comment("")
        @Comment("The floating text above every grave. Its lines are in the messages file.")
        Hologram hologram,

        @Comment("")
        @Comment("After the player respawns.")
        Respawn respawn) {

    public GraveSettings() {
        this(Languages.DEFAULT, new Graves(), new Contents(), new Collect(), new Teleport(), new Hologram(),
                new Respawn());
    }

    /** What a grave keeps of the dying player's experience. */
    public enum ExperienceMode {
        /** Nothing: the experience drops as orbs, as it would without graves. */
        NONE,
        /** What vanilla would have dropped as orbs (at most 100 points). */
        VANILLA,
        /** All of the experience the player had, levels included. */
        ALL
    }

    /** The {@code graves} section. */
    public record Graves(
            @Comment("How long a grave stands before it is over: 30m, 2h, 1d. -1 = until collected.")
            @Comment("exyliagraves.expire.<minutes> gives a rank longer; the largest number a player holds wins.")
            @Time(Time.Unit.MINUTES) int expireTime,

            @Comment("What happens to the contents when a grave expires: DROP them where it stood, or DELETE them.")
            String onExpire,

            @Comment("How long only the owner may open it. 0 = anybody from the start, -1 = only ever the owner.")
            @Comment("exyliagraves.protection.<minutes> gives a rank longer; the largest number a player holds wins.")
            @Time(Time.Unit.MINUTES) int protectionTime,

            @Comment("Whether the player who made the kill may open it while it is protected.")
            boolean killerCanLoot,

            @Comment("How many graves one player may have standing on this server. Dying with this")
            @Comment("many tips the oldest one out where it stands. -1 = no limit.")
            @Comment("exyliagraves.limit.<number> raises it; the largest number a player holds wins.")
            int maxPerPlayer,

            @Comment("Worlds where nobody gets a grave, by name.")
            List<String> disabledWorlds) {

        public Graves() {
            this(60, "DROP", 15, true, 5, List.of());
        }

        /** Whether an expired grave drops what it held rather than losing it. */
        public boolean dropsOnExpire() {
            return onExpire == null || !onExpire.trim().equalsIgnoreCase("DELETE");
        }

        public boolean isDisabled(String world) {
            for (String name : disabledWorlds) {
                if (name != null && name.equalsIgnoreCase(world)) return true;
            }
            return false;
        }
    }

    /** The {@code contents} section. */
    public record Contents(
            @Comment("NONE, VANILLA (what would have dropped as orbs) or ALL (every point the player had).")
            String experience,

            @Comment("Of that experience, the percentage the grave keeps (0-100). The rest is lost.")
            @Comment("exyliagraves.experience.<percent> gives a rank more; the largest number a player holds wins.")
            int experiencePercent,

            @Comment("Materials that never go into a grave: they drop on the floor as usual.")
            List<String> ignoredMaterials,

            @Comment("Materials the player keeps through death, in their inventory.")
            List<String> keptMaterials) {

        public Contents() {
            this("VANILLA", 100, List.of(), List.of());
        }

        public ExperienceMode experienceMode() {
            try {
                return ExperienceMode.valueOf(experience == null ? "VANILLA" : experience.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return ExperienceMode.VANILLA;
            }
        }

        public Set<Material> ignored() {
            return materials(ignoredMaterials);
        }

        public Set<Material> kept() {
            return materials(keptMaterials);
        }

        private static Set<Material> materials(List<String> names) {
            Set<Material> set = EnumSet.noneOf(Material.class);
            for (String name : names) {
                Material material = name == null ? null : Material.matchMaterial(name.trim());
                if (material != null) set.add(material);
            }
            return set;
        }
    }

    /** What a click on a grave does. */
    public enum Use {
        /** Opens its window: the real contents, a stack per click, the same window for everybody looking. */
        OPEN,
        /** Takes everything at once. */
        COLLECT,
        /** Shows a copy nothing can leave. Needs {@code exyliagraves.preview}. */
        PREVIEW,
        /** Nothing; the click goes on as if the grave were not there. */
        NONE;

        static Use parse(String name, Use fallback) {
            try {
                return name == null ? fallback : valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return fallback;
            }
        }
    }

    /** The {@code collect} section. */
    public record Collect(
            @Comment("Whether the owner's armour and offhand go straight back on, into empty slots,")
            @Comment("when they collect their grave themselves.")
            boolean autoEquip,

            @Comment("What each click on a grave does:")
            @Comment("  OPEN    its window, where everybody looking sees the same contents and takes a stack per click")
            @Comment("  COLLECT everything at once")
            @Comment("  PREVIEW a copy nothing can leave (needs exyliagraves.preview)")
            @Comment("  NONE    nothing")
            String rightClick,
            String sneakRightClick,
            String leftClick,
            String sneakLeftClick,

            @Comment("Whether several players may have the same grave open at once, all seeing the same")
            @Comment("contents as they change. false = one at a time.")
            boolean sharedWindow,

            @Comment("Whether the window has a button that takes everything left in one click.")
            boolean takeAllButton) {

        public Collect() {
            this(true, "OPEN", "COLLECT", "OPEN", "COLLECT", true, true);
        }

        /** What this click does, by the hand that clicked and whether the player was sneaking. */
        public Use use(boolean right, boolean sneaking) {
            if (right) return sneaking ? Use.parse(sneakRightClick, Use.COLLECT) : Use.parse(rightClick, Use.OPEN);
            return sneaking ? Use.parse(sneakLeftClick, Use.COLLECT) : Use.parse(leftClick, Use.OPEN);
        }
    }

    /** The {@code teleport} section. */
    public record Teleport(
            @Comment("Whether /graves lets a player go to one of theirs. Needs exyliagraves.teleport.")
            boolean enabled,

            @Comment("What that teleport costs. 0 = free. exyliagraves.teleport.free skips it.")
            @Comment("exyliagraves.teleport.cost.<amount> makes it cheaper for a rank; the smallest number a player holds wins.")
            double cost,

            @Comment("The ExyliaLib currency id the cost is paid in (vault, gems, an item or xp currency...).")
            @Comment("Empty = ExyliaLib's default currency.")
            String currency) {

        public Teleport() {
            this(false, 0.0, "");
        }

        /** The currency id to charge in, or {@code null} for the default one. */
        public String currencyId() {
            return currency == null || currency.isBlank() ? null : currency.trim();
        }
    }

    /** The {@code hologram} section. */
    public record Hologram(
            @Comment("Whether a grave has floating text above it.")
            boolean enabled,

            @Comment("How far above the block the text floats.")
            double offset) {

        public Hologram() {
            this(true, 1.4);
        }
    }

    /** The {@code respawn} section. */
    public record Respawn(
            @Comment("Whether the player's compass points to their newest grave after they respawn.")
            boolean pointCompass,

            @Comment("Whether they are reminded where their newest grave is after they respawn.")
            boolean remind) {

        public Respawn() {
            this(true, true);
        }
    }

    private static ConfigFile<GraveSettings> file;

    /** Keeps the loaded file so the accessors read the current snapshot. */
    public static void install(ConfigFile<GraveSettings> loaded) {
        file = loaded;
    }

    /** The current snapshot, or the defaults before the file loaded: never {@code null}. */
    public static GraveSettings get() {
        return file == null ? new GraveSettings() : file.get();
    }
}
