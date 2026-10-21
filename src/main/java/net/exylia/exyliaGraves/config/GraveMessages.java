package net.exylia.exyliaGraves.config;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.ConfigFile;

import java.util.List;

/** The messages of ExyliaGraves, in {@code lang/<code>/messages.yml}. */
@Comment("Messages sent by ExyliaGraves.")
@Comment("")
@Comment("Colours use palette tokens such as {primary}, {error} or {highlight},")
@Comment("so recolouring the server never means editing this file.")
@Comment("")
@Comment("A message may open with an effect tag, which is an instruction and never")
@Comment("reaches the screen: [sound:NAME|volume|pitch] plays a sound to the player.")
@Comment("An empty message is not sent at all.")
public record GraveMessages(
        @Comment("Opens every chat line through %prefix%.")
        String prefix,

        @Comment("")
        @Comment("Dying and collecting. %x% %y% %z% %world% where, %time_left% until it expires,")
        @Comment("%items% how many stacks, %experience% how many points, %player% whose grave.")
        String created,
        String collected,
        String looted,
        String protectedGrave,
        String protectedGraveForever,
        String gone,
        String cannotBreak,
        String claimFailed,
        String rewardsDelivered,
        String respawnReminder,

        @Comment("")
        @Comment("Left for the owner, shown wherever they are on the network or on their next join.")
        String lootedNotice,
        String expiredDropped,
        String expiredDeleted,

        @Comment("")
        @Comment("Looking inside a grave without taking anything.")
        String previewTitle,
        String cannotPreview,

        @Comment("")
        @Comment("Teleporting to a grave. %cost% is formatted in the currency.")
        String teleported,
        String teleportDisabled,
        String notEnoughMoney,
        String teleportRefunded,
        String inCombat,
        String serverUnreachable,
        String worldNotLoaded,
        String teleportFailed,
        String noPermission,

        @Comment("")
        @Comment("Who may open a grave, as the hologram and the menus show it.")
        String hologramProtected,
        String hologramOpen,
        String hologramForever,
        String hologramProtectedFor,

        @Comment("")
        @Comment("The lines above a grave, top to bottom. %player% whose grave, %items% how many")
        @Comment("stacks, %time_left% until it expires, %protection% who may open it.")
        List<String> hologramLines,

        @Comment("")
        @Comment("The action lines of a grave in /graves; empty when the action is not available.")
        String menuTeleport,
        String menuPreview,
        String menuRestore,
        String menuRemove,

        @Comment("")
        @Comment("Admin commands.")
        List<String> adminUsage,
        String restorePrompt,
        String restored,
        String restoredAll,
        String removePrompt,
        String removed,
        String noGraves,
        String reloaded) {

    public GraveMessages() {
        this("{primary}&lGRAVES {letters_black}•<reset>",
                "[sound:BLOCK_BELL_RESONATE|0.8|0.6]%prefix% {letters}Your things are in a grave at {highlight}%x%, %y%, %z% {letters_black}({letters}%world%{letters_black}) {letters}for {highlight}%time_left% ⌚{letters}.",
                "[sound:BLOCK_CHEST_OPEN|1.0|1.2]%prefix% {success}Grave collected{letters_black}: {highlight}%items% {letters}item(s) and {highlight}%experience% {letters}experience.",
                "[sound:BLOCK_CHEST_OPEN|1.0|1.2]%prefix% {success}You looted {highlight}%player%{success}'s grave{letters_black}: {highlight}%items% {letters}item(s).",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}This is {highlight}%player%{error}'s grave. {letters}It opens to everybody in {highlight}%time_left% ⌚{letters}.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}This is {highlight}%player%{error}'s grave. {letters}Only they can open it.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}That grave is gone.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}Click the grave to collect it; it cannot be broken.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}The grave could not be opened right now. {letters}It is still there; try again in a moment.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {success}%amount% {letters}item(s) from your graves were handed to you.",
                "%prefix% {letters}Your grave is at {highlight}%x%, %y%, %z% {letters_black}({letters}%world%{letters_black}){letters}. {letters_black}Your compass points to it.",
                "%prefix% {warning}%player% {letters}looted your grave in {highlight}%world%{letters}: {highlight}%items% {letters}item(s).",
                "%prefix% {warning}One of your graves expired and its {highlight}%items% {warning}item(s) are on the ground where it stood.",
                "%prefix% {warning}One of your graves expired and its {highlight}%items% {warning}item(s) are gone.",
                "{primary}&lGRAVE {letters_black}» {highlight}%player%",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}You cannot look inside that grave.",
                "[sound:ENTITY_ENDERMAN_TELEPORT|1.0|1.2]%prefix% {success}Taking you to your grave. {letters_black}Cost: {highlight}%cost%{letters_black}.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}You cannot teleport to a grave; you have to walk.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}Teleporting to a grave costs {highlight}%cost%{error}.",
                "%prefix% {warning}The teleport did not happen, so {highlight}%cost% {warning}was refunded.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}You cannot teleport while in combat. {letters}Wait {highlight}%time% ⌚{letters}.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}That server cannot be reached right now.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}That world is not loaded.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}The teleport failed.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {error}You do not have permission to do that.",
                "{error}Only the owner can open it",
                "{success}Anybody can open it",
                "{info}forever",
                "{warning}Protected {letters_black}» {info}%time_left% ⌚",
                List.of(
                        "{primary}&lGRAVE {letters_black}» {highlight}%player%",
                        "{letters}%items% {letters_black}item(s) {letters_black}· {info}%time_left% ⌚",
                        "%protection%"),
                "{warning}➥ Left-click {letters_black}» {letters}teleport",
                "{warning}➥ Right-click {letters_black}» {letters}look inside",
                "{warning}➥ Shift-left-click {letters_black}» {letters}hand back to the owner",
                "{error}➥ Shift-right-click {letters_black}» {letters}remove it",
                List.of("%prefix% {letters}Usage{letters_black}:",
                        " {letters_black}▎ {highlight}/gravesadmin list <player>",
                        " {letters_black}▎ {highlight}/gravesadmin restore <player>",
                        " {letters_black}▎ {highlight}/gravesadmin reload"),
                "{warning}Hand %count% grave(s) back to %player%?",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {success}Grave handed back to {highlight}%player%{success}.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {success}%count% {letters}grave(s) handed back to {highlight}%player%{letters}.",
                "{error}Remove %player%'s grave and everything in it?",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {letters}%player%{letters}'s grave was removed.",
                "[sound:ENTITY_VILLAGER_NO|1.0|1.0]%prefix% {highlight}%player% {error}has no graves standing.",
                "[sound:ENTITY_EXPERIENCE_ORB_PICKUP|1.0|1.2]%prefix% {success}Configuration, messages and menus reloaded.");
    }

    private static ConfigFile<GraveMessages> file;

    /** Keeps the loaded file so {@link #get()} reads the current snapshot. */
    public static void install(ConfigFile<GraveMessages> loaded) {
        file = loaded;
    }

    /** The current snapshot, or the defaults before the file loaded: never {@code null}. */
    public static GraveMessages get() {
        return file == null ? new GraveMessages() : file.get();
    }
}
