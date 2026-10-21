# ExyliaGraves

Graves where a player died, kept in the database and collected exactly once across a network. Free and open source (GPL-3.0), public at `Exylia-Plugins/ExyliaGraves`. Extracted from ExyliaSurvivalCore's `graves` module; the table name `sc_graves` stays so shared databases keep their graves.

## Releasing

- Every finished change is released, without asking and without `/exylia-release`: bump `version` in `build.gradle` (patch for fixes, minor for features, major for breaking commands or config), commit `chore: release <version>`, tag that commit `v<version>`, and push the branch and the tag.
- The release workflow runs only on `v*` tags and expects the tag to match the `build.gradle` version; it publishes the GitHub release and the fixed-name `ExyliaGraves.jar` that servers auto-update from, then mirrors it to Modrinth (skipped while `MODRINTH_TOKEN` or the `MODRINTH_PROJECT_ID` variable is unset). A push to the default branch without a tag only builds.

## ExyliaLib

- Always the newest: `build.gradle` resolves the latest GitHub release (or a newer one in mavenLocal) at build time, and that version becomes plugin.yml's `exylia-lib`. `-PexyliaLibVersion` pins one for a single build.
- `ExyliaGravesPlugin` is the Bukkit entry point and touches no ExyliaLib type: without ExyliaLib it runs `bootstrap/ExyliaLibMissing` (logs where to download it; nothing is downloaded without the owner) and disables itself until the restart. With it, it hands over to the core, `ExyliaGraves`.

## Grave safety (read before touching `manager/` or `service/`)

- A grave's items live only in its row. They leave only through `GravesManager.claim` (deletes the row) or `GravesManager.take` (rewrites it holding less), both conditional on the row's `revision` being the one the caller read; only a landed answer may hand anything over. Never re-save a grave row: a save after another server's delete recreates it. Always claim the freshest copy (`manager.find`), never one a menu read earlier.
- Contents go out through `PluginRewards.deliver` by UUID, with `runAtEntity(player, work, retired)` so a player gone before their thread runs is still paid.
- Previews (`PreviewHolder`) and windows (`GraveWindows.Window`): every click and drag is cancelled at `LOWEST` and again at `HIGHEST`. A window acts on a click only after cancelling it, and only through `take`/`claim`. Never give either a writable path.
- Each server indexes and draws only graves whose location names its own server id. A head is removed only for a grave this server indexes; remote deletes arrive through `onRemoteChange`.
- Deaths held by `Sessions` or claimed through `DeathDrops` (duel rooms) are left alone.
