# Design — add-mapgen-map-source

## Context

See `proposal.md` for motivation. The current state that constrains the design:

- **One source, hard-coded.** `app/src/main/java/com/naviveylin/di/MapDownloadModule.kt:26-29` holds the provider name, base URI and `latest.php?fromVersion=%1&toVersion=%2&locale=%3` template; `provideDefaultMapProvider()` is a singleton the ViewModel injects. The provider row in `MapManagerScreen.kt:290-320` is a static `R.string.provider_karry` label.
- **The two protocols are structurally different.** A provider listing is a JSON array parsed natively (`AvailableMapEntry::FromJsonArray`, `libosmscout-client/src/osmscoutclient/AvailableMapEntry.cpp:60`) into entries carrying a `serverDirectory` and a size, and the file set is the fixed `MapDownloadService::MapFiles()` list. A repository exposes a *recursive* region index (`names.json`), per-database metadata (`db.json`: `typeConfigVersion`, `output.boundingBox`, `output.files[name]={size,crc32}`), and version slots `v<typeConfigVersion>` — the client must know its own database format version to address a slot (`Documentation/MapRepository.md` §1.2, §1.3, §2).
- **Upstream already has a repository client — for the basemap only.** `app/src/main/cpp/libosmscout/libosmscout-client-java/java/.../BasemapManager.java` (1158 lines) reads `basemap/index.json`, then `db.json`, downloads the slot's files, verifies each with `CRC32`, installs atomically and exposes `isUpdateAvailable()`; it uses `java.net.http.HttpClient` and hand-rolled JSON (`jsonInt`, `jsonLong`, `jsonString`). The app's override `osmscout-client-java/.../BasemapManager.java` (1015 lines) replaces it with the karry tar.gz path, which this app needs and keeps.
- **No regional repository client exists in any layer** (no `names.json` reader in C++, `libosmscout-client`, the bridge or the app).
- **`:core` is the shared pure-logic module.** Android library, `compose = false`, no HTTP, no `java.net` use; `:app` and `:auto` both depend on it; it already depends on `:osmscout-client-java`; it owns `BasemapReloadNotifier`, the cross-variant invalidation signal. It has **no** `kotlinx-serialization` plugin; `:app` does.
- **The bridge's only way to register a finished download is private.** `MapDownloadManager.nativeRegisterMapDirectory` is private; the public surface is `getInstalledMaps()`, `deleteMap(path)` and the builder's `withMapLookupDirectories`.
- **Installed map identity is the directory name**, derived from the display name (`MapStorageManager.targetDirForMap`: lowercase, spaces to hyphens). Nothing records which source produced a directory. `MapManagerScreen.kt:509-599` builds its own tree from an entry's `path` segments, synthesizing directories from path prefixes.
- **Basemap reload is already a solved seam**: `OSMScoutClient.setBasemapLookupDirectory(String)` and `reloadBasemap()` are public native methods.

## Goals / Non-Goals

**Goals**

- One implementation of the repository protocol, testable on the JVM without Robolectric, shared by any surface that needs it later.
- The smallest possible new native-facing surface: no C++ change, no submodule patch, no new JNI function.
- Source selection as a single seam, so a future third source is a new implementation of one interface, not a new branch in the ViewModel.
- Deterministic, integrity-checked installs: a repository directory is either complete and verified or absent.

**Non-Goals**

- Contributing the regional repository client upstream, or reconciling the basemap divergence in the submodule.
- Update checking of any kind (see the spec `map-repository-source` — "The repository source performs no update check").
- Changing the built-in provider's listing format, its fixed file set, or its tar.gz basemap.
- Making the car surfaces select a source; the source is a phone-surface setting, and the car reacts only to the shared reload signal.

## Decisions

### D1 — The protocol lives in Kotlin (`:core`), the native seam stays one method

**Chosen:** region-index and metadata models, URL planning, CRC-32 verification, source/provenance rules and the source registry interfaces live in `:core` (`com.naviveylin.core.mapsource`). The bridge gains exactly one public method — `MapDownloadManager.registerMapDirectory(String)` — wrapping the existing private JNI call; deletion reuses the existing `deleteMap(path)`.

Alternatives considered:

| option | why not |
|---|---|
| Protocol in the bridge's Java (upstream's basemap precedent) | `names.json` nests arbitrarily; the bridge has no JSON library and upstream hand-rolls flat lookups only. A recursive parser by hand is the worst-maintenance part of the change, and it cannot be unit-tested without the JNI stub. |
| Upstream submodule patch (C++ `MapDownloadService` + bridge) | Needs a native rebuild per ABI, tracks a divergence from upstream, and buys nothing the app cannot do in Kotlin — the download loop is already Java/Kotlin-side. |
| Everything in `:app` | Duplicates the protocol for any future surface, and `:core` is already the module both variants share; the parsing is pure and belongs where the fast JVM tests live. |

Consequence: `core/build.gradle.kts` gains the `kotlinx-serialization` plugin and dependency (the `Design.md` §2 "adding a dependency" recipe), and `:core` must stay free of `java.net` imports — the fetcher arrives through an interface (D2).

### D2 — The protocol layer is pure; I/O is injected

**Chosen:** `:core` defines the HTTP seam as a narrow suspend interface (fetch a text document, fetch a file into a sink while streaming CRC-32). `:app` implements it with `HttpURLConnection` (the spec's HTTP policy). CRC-32 stays in `:core` (`java.util.zip.CRC32`, JVM-pure) and is computed **while** bytes stream to disk, so a file is read once.

Alternatives: `java.net.http.HttpClient` (rejected — Android policy and desugaring, spec `map-download-infrastructure`); OkHttp (rejected — a new third-party dependency for one code path); doing HTTP inside `:core` (rejected — it makes the interface untestable as plain JVM tests and drags Android into the protocol layer).

### D3 — Registering a completed download

**Chosen:** add `registerMapDirectory(String path)` to the override `osmscout-client-java/.../MapDownloadManager.java`, exposing the existing `nativeRegisterMapDirectory`. Repository downloads call it after a verified install; the provider path continues to use its own internal call.

Alternatives: a new JNI function (rejected — nothing native is missing); registering through the builder (rejected — the client is already built, and rebuilding it on every download is exactly what the existing "Newly downloaded map visible immediately" requirement forbids); calling `setMapLookupDirectories` on a running client (no such public API exists).

### D4 — Provenance is a marker file in the installed directory

**Chosen:** after a successful install — regional or basemap — the app writes `.source.json` into that directory: source identity, base URL (repository only) and the installed database version. A directory without a marker is attributed to the built-in provider, which is the migration rule for everything installed before this change. The basemap directory gets the same marker, so "which source installed this basemap" is answerable offline.

Alternatives: an `AppSettings` map `dirName -> source` (rejected — a settings reset or wipe silently under-deletes on the next switch, and the record would not survive a copied directory); both marker and settings cache (deferred: the marker is the truth and the settings copy buys only a scan).

### D5 — Directory naming: repository databases use the index id path

**Chosen:** a repository database installs into `<mapsRoot>/<leaf id path joined by "-">`, e.g. `europe-germany-berlin`, computed from the index's identifiers; the built-in provider keeps its display-name-derived naming unchanged. The row label is always the localized display name from the index.

Rationale: `names.json` names are localized, so a display-name-derived directory would change identity when the app language changes, and the spec forbids the resulting "installed map becomes not-installed" behaviour. The built-in provider's naming is existing behaviour and its entries carry a server-side layout string, not an index identity, so re-deriving it would break already-installed maps.

### D6 — One database format version

**Chosen:** the override `MapDownloadManager` gets upstream's public constant `DATABASE_FORMAT_VERSION` (27), replacing the two inline `"27"` substitutions in `fetchAvailableMaps`; `:core` reads that constant to build a repository's version-slot URL, so the listing bounds and the slot URL cannot drift. A unit test asserts the slot URL and the listing substitution carry the same value.

Alternative: a `:core` constant (rejected — two constants would drift; the native side's `FILE_FORMAT_VERSION` is what the constant mirrors, and the bridge is the layer that already mirrors it).

### D7 — Two basemap clients, selected by the active source

**Chosen:** the override `BasemapManager` (tar.gz listing + extraction) stays exactly as it is for the built-in provider. A new Kotlin repository-basemap client in `:core` reads `basemap/index.json`, picks the newest version this client can read, and installs the slot through the same verified-download path as a regional database. `BasemapViewModel` asks the source registry which one the active source needs. Reload after install or delete uses the existing `setBasemapLookupDirectory` + `reloadBasemap`.

Alternatives: port upstream's `BasemapManager` as a second Java class (rejected — duplicates metadata parsing and CRC verification in a layer that cannot unit-test it, and arrives with `java.net.http`); replace the tar.gz client with a repo client (rejected — the built-in provider publishes no repository layout).

Verification that the two clients stay separate: the repository path must not read an HTML listing, and the tar.gz path must not read `index.json` — one test case per direction (spec `basemap-download` — "A tar.gz archive is not used by a repository source").

### D8 — Source switch is a destructive, confirmed, IO-bound operation

**Chosen:** selecting a different source opens the confirmation dialog computed from the *previous* source's inventory (count and total size, basemap included); confirming runs the deletion on the IO dispatcher: delete each directory and unregister it, delete the basemap, persist the new selection, fire `BasemapReloadNotifier`, log one coordinate-free line, and refresh the installed list. Cancelling changes nothing and does not persist. A switch with nothing to delete skips the dialog.

Alternatives: delete lazily on first use of the new source (rejected — the spec requires the switch to remove the data, and lazy deletion leaves two sources' data on disk with no visible owner); delete in the UI layer without persistence ordering (rejected — a crash between delete and persist would leave data deleted while the old source stays active).

### D9 — Threading and lifecycle

- The protocol client is a suspend/`Flow`-based object in `:core`; it never touches the main thread (Design.md §4). Its `:core` tests assert that the planning and verification steps take no dispatcher from the caller.
- `MapManagerViewModel` keeps its existing shape (`StateFlow<MapManagerUiState>`, IO-dispatched work) and gains: the active source, the repository URL draft, the test outcome, the per-leaf metadata cache, and the pending-switch summary. Metadata probes are deduplicated per leaf while in flight.
- Nothing native runs on the main thread; the new bridge call is invoked from the IO dispatcher like the existing registration call.
- A pending switch is dropped if the ViewModel is cleared before confirmation; the selection is persisted only after the deletion completes.

### D10 — Testability

- `:core` JVM tests: index parsing (nesting, localization fallback, unsupported schema), slot URL planning (id path + version), metadata parsing, CRC-32 verification (including a deliberately corrupted file), provenance read/write and the "no marker = built-in provider" rule, the switch plan (counts, sizes, basemap inclusion).
- `:app` tests: the source registry and ViewModel state transitions with a fake fetcher; the `HttpURLConnection` implementation against a JDK `com.sun.net.httpserver` server (no new test dependency).
- `revert-check` for each new invariant the change introduces (the verification rules in `Build.md` §2/§4): the confirmation gate, the "no marker means built-in provider" rule, the CRC-32 refusal path, and the "type configuration last" ordering.

### D11 — Verification on device (measurement, not inspection)

- Provider row, URL field, test outcome and the confirmation dialog: a Compose UI-dump geometry assertion plus a `pixel-check` screenshot verdict for the dialog's presence/absence across the four cases in the specs.
- Repository listing and download: the coordinate-free diagnostics stream (source identity, leaf count, per-file bytes and verification outcome, deleted directories and bytes on a switch), read with the `Build.md` §10 logcat recipes against a local repository served on the host.
- A switch with a live car session: verify the car view drops the deleted basemap without a restart (the `BasemapReloadNotifier` path), on an emulator with a head-unit session.
- Not measurable on a stationary emulator: nothing in this change depends on motion, so no case is left unproven by device limits.

## Risks / Trade-offs

| risk | mitigation |
|---|---|
| A recursive hand-written JSON parser would be the fragile part — avoided by D1, but the `:core` serialization dependency is new build surface | Keep models minimal (index, metadata, marker), keep them in one file per document, and use explicit `@SerialName` mappings so a schema addition does not silently re-shape them |
| The database format version constant can drift from the native `FILE_FORMAT_VERSION` | D6's single constant plus a test asserting slot URL and listing substitution agree; a drift also shows up as an empty listing, which the existing error path reports |
| Deleting on switch is destructive | Confirmation naming count and size; marker provenance so attribution is offline and auditable; partly-failed deletion reported and non-blocking, and the directory is left attributed to its own source rather than orphaned |
| A car session rendering a basemap that a switch deleted | `BasemapReloadNotifier` fired by the switch; spec `basemap-ui` — "Basemap status clears when its source's data is deleted" |
| Two basemap clients could be conflated (a repository install overwritten by a tar.gz install on the same directory) | One marker per basemap directory records the installing source; each client installs atomically and replaces the directory wholesale, so a mixed directory cannot arise |
| Lazy probing hides a leaf's size until expanded | Accepted by design (spec `map-repository-source`); each probed leaf is cached for the session, and a failed probe states why rather than staying blank |
| A user's repository may publish very large databases | Progress and cancellation go through the existing listener and foreground-service path, so a large download behaves like a provider download |
| `:app`'s tree-building code synthesizes directories from path segments | Repository leaves emit their index id path as those segments, so the existing tree code needs no new shape; a test asserts a nested index produces the expected parent chain |

## Migration Plan

1. Land the additive pieces first: `:core` protocol + `MapDownloadModule` registry with the built-in provider as the default, and the `registerMapDirectory` bridge method. Nothing user-visible changes; existing installations are attributed to the built-in provider by the missing marker.
2. Land the settings fields (selected source, repository URL) with defaults that preserve today's behaviour.
3. Land the UI: interactive selector, URL field, test action, installed-row source label.
4. Land the switch path (confirmation, deletion, unregister, reload signal) last, because it is the only destructive step.

**Rollback:** every step is additive and the settings fields default to the built-in provider; reverting the change's commits restores the previous behaviour with no data migration. Maps installed from a repository are removed by switching back to the built-in provider through the same dialog, and the marker file makes any remaining directory identifiable by hand. Marker files left in karry directories are inert.

## Open Questions

- Which name an index node falls back to when the user's language is absent (first entry of the node's `names` map, or `en` when present). Any deterministic choice satisfies the spec; picking `en` when present is the friendlier default and can be settled in implementation.
- Whether the basemap manifest's change time should be displayed in the basemap section. Deferrable: it is presentation only and no requirement depends on it, since this change adds no update check.
