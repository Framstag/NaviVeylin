# Proposal — add-mapgen-map-source

## Why

The app can obtain map data from exactly one place: the hard-coded karry.cz provider (`MapDownloadModule.kt:26-29`), whose listing carries no integrity information and whose file set is assumed fixed (`MapDownloadService::MapFiles()`). Meanwhile upstream libosmscout defines a *map repository* protocol produced by `mapgen` — a region index with localized names (`names.json`), per-database metadata with a byte size and CRC-32 per file (`db.json`), and version slots keyed by the database format version — and already ships a client for it (the submodule's `BasemapManager`), which this app's override replaces with the legacy karry tar.gz path.

A user who runs their own `mapgen` repository, or who wants integrity-checked downloads, therefore cannot use the app at all; and a self-hosted source means users bring their own base URL.

## What Changes

- **Map source becomes a user-visible choice** between the existing karry.cz provider and a libosmscout **mapgen repository** at a user-supplied base URL. The choice persists across restarts; the static provider label in the map manager's provider row becomes a real selector.
- **New repository protocol client** (Kotlin, in `:core`): fetch `names.json`, render its region tree with localized names, probe a leaf's `db.json` on demand (lazy size/metadata), download the files the metadata names, and **verify every file's CRC-32** before it is installed. The version slot is the client's own database format version.
- **Basemap support per source**: the karry variant keeps the existing tar.gz client unchanged; the repository variant reads `basemap/index.json` and installs the `basemap/v<version>/` slot through the same verified-download path. Both clients coexist and the active source picks one.
- **Installed maps record their source** in a marker file (`.source.json`: source id, base URL, version) inside the map directory. Switching source **deletes the other variant's maps and the other variant's basemap**, behind a confirmation dialog that names the count and total size of what will disappear.
- **Custom repository URL is testable**: a `[Test]` action fetches `names.json`, validates the schema version and reports the region/leaf counts, so a user can verify a self-hosted URL before relying on it.
- **Not in this change**: any update check (comparing an installed database's `generatedAt` against the server's, or an "update available" indicator for the repository variant). That is a separate change.

## Capabilities

### New Capabilities

- `map-source-selection`: which map source is active (karry.cz or a repository URL), how that choice is persisted and validated, how an installed map records the source it came from, and what switching source deletes.
- `map-repository-source`: consuming a libosmscout mapgen repository — region index with localized names, on-demand database metadata, integrity-verified download of a database's files into app storage, and the equivalent for the repository's basemap slot.

### Modified Capabilities

- `map-download-infrastructure`: provider wiring (a source registry instead of one hard-coded provider), the protocol client's module home and its dependency, one new public bridge entry point for registering a downloaded map directory, and the HTTP policy extended to the repository path.
- `map-download-ui`: the provider row becomes an interactive source selector with the custom-URL field and its `[Test]` action; the switch confirmation dialog; repository entries in the tree (lazy metadata, no size until probed).
- `basemap-discovery`: probing is per active source — HTML archive listing for karry, `basemap/index.json` for the repository.
- `basemap-download`: installation is per source — archive download and extraction for karry, version-slot file download with CRC-32 verification for the repository.
- `basemap-ui`: no update control is offered for a source that publishes no comparable installed version (the repository variant, because no update check is added in this change).

## Impact

**Modules and files**

- `:core` (new): repository protocol — region index and metadata models, URL planning, CRC-32 verification, source/provenance model. `core/build.gradle.kts` gains the `kotlinx-serialization` plugin and dependency.
- `:osmscout-client-java` (local override only, **no submodule patch**): `MapDownloadManager` gains a public `registerMapDirectory(path)`/unregister entry point exposing the existing private native call; the hard-coded `27`/`"27"` database-format version becomes one constant. `BasemapManager` keeps its tar.gz implementation and is joined, not replaced, by the repository path.
- `:app`: `di/MapDownloadModule.kt` (source registry, `BasemapManager` selection), `data/MapStorageManager.kt` (directory naming per source), `data/SettingsStorage.kt` (selected source + repository URL), `ui/mapmanager/MapManagerViewModel.kt`, `MapManagerScreen.kt`, `BasemapViewModel.kt`, `BasemapSection.kt`, `service/MapDownloadService.kt` (unchanged contract, repository downloads join it), `res/values*/strings.xml` (selector, URL field, Test, confirmation dialog) plus German translations.
- Deletion and reload fire the existing `:core` `BasemapReloadNotifier` so a live car session drops a deleted basemap instead of rendering it.

**Specs** — the two new capabilities above and the five modified ones.

**Guidelines** — `Design.md` §2 (new dependency recipe, screen/sheet recipes), §4 (threading of the protocol client), §5 (native boundary: override module, not the submodule); `Build.md` §6/§7 (test constraints, coverage); `Logging.md` §1-§2 (what a download/diagnostic line may carry, identity not coordinates); `MapRendering.md` §15a/§16 for the basemap stylesheet and load path (unchanged behaviour, referenced by the basemap deltas).

**Change class and rollback** — *additive*: no existing behaviour is removed, and switching back to karry.cz restores the previous state. Rollback is reverting the change's commits; maps downloaded from the repository source are removed by the switch-back path in the same change, and the marker file makes any leftover directory identifiable by hand. No storage format of existing data changes (settings gain optional fields, defaulting to karry.cz).

**Native/JNI** — no submodule patch. The only native-facing change is a local override in the bridge module: a new public Java method exposing the already existing JNI function `nativeRegisterMapDirectory`. The repository protocol itself is Kotlin, calls the native layer only for register/unregister.

**Out of scope** — update check for the repository variant (installed vs `generatedAt`, update badge); any change to the karry tar.gz basemap client's behaviour.
