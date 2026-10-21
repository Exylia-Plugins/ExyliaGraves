# ExyliaGraves

Free, open-source graves for Paper and Folia: **dying leaves a head where you fell, holding exactly what you dropped, collected with one click and handed over exactly once anywhere on a network**.

Built so a grave can never duplicate an item: not with two players clicking the same head, not with two servers, not with staff handing it back while somebody loots it.

## Features

- **A grave where you died**: a player head with your face and a floating countdown. A death in water, lava or a hole finds the nearest free block above; a death in the void lands on the highest block of that column.
- **One click to collect**: everything goes straight back into your inventory, your armour and offhand back on (`collect.auto-equip`). What does not fit waits for you and is handed over on your next join, on any server.
- **Protection**: only the owner may open a grave for a while (or for good), optionally the killer too. Staff with `exyliagraves.bypass.protection` open any.
- **Look before you take**: sneak-click a grave, or right-click it in `/graves`, to see what it holds in a window nothing can leave.
- **Experience kept**: none, what vanilla would have dropped, or everything the player had, levels included; at the percentage you choose.
- **Item rules**: materials that never go into a grave, and materials the player keeps through death.
- **Expiry**: a grave lasts as long as you set (longer by rank with `exyliagraves.expire.<minutes>`), then drops its contents where it stood or deletes them. The owner is told, wherever they are.
- **Limits by rank**: `exyliagraves.limit.<n>` graves standing at once; dying past it tips the oldest out where it stands.
- **/graves**: every grave you have on every server of the network, with live countdowns, and an optional paid teleport (refunded if it does not happen, refused in combat).
- **Back on your feet**: after respawning your compass points to your newest grave and you are told where it is.
- **Notices**: the owner hears when somebody loots their grave or it expires, online on any server or on their next join.
- **Staff tools**: anybody's graves on any server (`/gravesadmin list`), look inside, teleport, hand back to the owner or remove; hand back all of a player's graves at once (`/gravesadmin restore`). Every grave created, collected, looted, expired, handed back or removed goes to the ExyliaLib audit log (`/exylialib logs`) with its contents.
- **Plays well with others**: a death another plugin owns (arenas through ExyliaLib sessions, ExyliaSurvivalCore duel rooms) gets no grave; regions and plugins that keep the inventory are respected.
- **Placeholders**: `%exyliagraves_count%`, `%exyliagraves_latest_world%`, `%exyliagraves_latest_x%`, `_y`, `_z`, `%exyliagraves_latest_time%`.
- **Six languages**: English, Spanish, Portuguese, French, Russian and Turkish, every message and menu editable.

## How it stays dupe-free

- The drops leave the death event and go into **one database row, and only there**. There is no container holding a grave's items; a preview is a copy every click and drag is cancelled in.
- A grave is taken **by deleting its row**. Only the call whose delete removed it gets the items, so two players, two servers, an expiry and a staff action racing for one grave hand it over once.
- A grave whose write failed is removed again before its items go back on the floor; one whose write might have landed is left standing rather than dropped on top of it.
- Contents are handed over **by player id** through ExyliaLib's reward store, so a player who dies, quits or switches servers between the click and the delivery still gets everything, once.
- A head is only ever removed on the server it stands on; a grave taken from another server is taken down there through Redis.

## Requirements

- Paper or Folia 1.21+, Java 21
- [ExyliaLib](https://github.com/DiGround-s/ExyliaLib/releases). If it is missing, the plugin downloads the latest release on first start and asks for a restart.
- For a network: every server on the same database (MySQL, MariaDB, PostgreSQL or MongoDB) in `plugins/ExyliaGraves/database.yml`, ideally with Redis enabled there too so a grave taken on one server disappears at once where it stands.

Migrating from ExyliaSurvivalCore's graves module: the table is still `sc_graves`, so graves standing when you switch are kept.

## Updates

ExyliaLib keeps both itself and this plugin up to date from their GitHub releases: a new version is downloaded in the background and applied on the next restart. A new major version waits for you to install it by hand, and a release that needs a newer ExyliaLib waits until the library has updated. Turn it off or tune it under `plugin-updates` in `plugins/ExyliaLib/config.yml`; `/exylialib updates` shows what is pending.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/graves` | `exyliagraves.menu` | your graves on every server |
| `/gravesadmin list <player>` | `exyliagraves.admin.view` | somebody's graves, to look inside, teleport, hand back or remove |
| `/gravesadmin restore <player>` | `exyliagraves.admin.restore` | hands all of a player's graves back to them |
| `/gravesadmin reload` | `exyliagraves.admin.reload` | reloads the files |

Aliases: `/grave`, `/graveadmin`.

## Permissions

- `exyliagraves.use`: get a grave when you die (everybody by default).
- `exyliagraves.menu`, `exyliagraves.preview`, `exyliagraves.teleport`: on by default.
- `exyliagraves.limit.<n>`: graves standing at once (largest wins; `graves.max-per-player` without one).
- `exyliagraves.expire.<minutes>`: how long their graves last (largest wins; `graves.expire-time` without one).
- `exyliagraves.teleport.free`, `exyliagraves.bypass.protection`: operators by default.
- `exyliagraves.admin.view`, `admin.restore`, `admin.remove`, `admin.reload`; `exyliagraves.admin` for all of them; `exyliagraves.*` for everything.

## License

GPL-3.0. See [LICENSE](LICENSE).
