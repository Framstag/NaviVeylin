# Proposal

## Why

A map database or basemap installed from the libosmscout mapgen repository is never compared with the
repository it came from, so a newer database is only ever noticed by deleting the map and downloading it
again — `map-repository-source` states that refusal as a requirement ("The repository source performs no
update check"), and `TODO.md` §160 was deferred for it. The repository now publishes exactly the
comparison basis this needs — each version slot's `db.json` carries `generatedAt`, and the basemap
manifest carries a change time per version (`Documentation/MapRepository.md` §6, "Client update check
contract") — and the app already stores that metadata next to every repository installation it makes.
The gap is therefore a comparison, its state and its controls, not new data or a new protocol.

## What Changes

- **Daily automatic check.** On the app-start path, at most once every 24 hours, the app probes the active
  repository source for newer data. The check needs no screen to be open and shows nothing by itself.
- **Manual check.** One control in the map manager screen runs the same check on demand, so the state can
  be refreshed without waiting for the next day.
- **Comparison per data kind.** An installed repository database is compared with its own version slot's
  metadata (the metadata the app stores inside the installed directory); an installed repository basemap
  is compared with the repository's basemap manifest. Newer server metadata means an update is available;
  a slot the repository no longer publishes means the installation is kept and reported as no longer
  offered, never as an error.
- **Updates section.** A new section of the map manager screen lists every updatable item — count, the
  last-checked time, per item the name, the download size and an [Update] action, and one [Update all]
  action for the whole list. The section lists the basemap beside the regional databases.
- **Update installs non-destructively.** An update is fetched into a sibling directory, verified against
  the metadata it came from, and swapped in only when complete; the installed map stays installed and
  usable until then, and an interrupted or failed update leaves it usable. The updated data becomes the
  rendered data without an app restart.
- **Update all is a queue.** One download at a time, each cancellable, a failure reported per map without
  stopping the rest.
- **Scope: the repository source only.** The built-in provider's regional maps keep today's behavior and
  report no update state, because the app records no comparable baseline when that provider installs a
  map. The repository basemap's existing refusal to report an update state is replaced, since its manifest
  is comparable.
- Not breaking. No stored data becomes unreadable and no user action is removed; the only spec statements
  removed are the two that forbid this feature.

## Capabilities

### New Capabilities

- `map-updates`: detecting newer repository map data — the daily and the manual check, what each
  installation is compared against and for every source kind, the update state an installation can be in,
  the update list and its controls, the queue behind [Update all], and the non-destructive replacement of
  an installed database and basemap. It also owns the deliberate phone-only surface of that UI.

### Modified Capabilities

- `map-repository-source`: its requirement "The repository source performs no update check" is replaced by
  the repository's own comparison contract — probe the client's own version slot only, a newer
  `generatedAt` means an update, a slot that is not published means the local installation is kept.
- `map-download-ui`: its "Section ordering" requirement gains the updates section in the screen's fixed
  order.
- `basemap-discovery`: its "Report basemap version for updates" requirement drops the repository carve-out,
  so a repository source reports the state its manifest supports instead of "no update state".

## Impact

**Modules and files**

- `:core` — new checker and update-state types under `core/src/main/java/com/naviveylin/core/mapsource/`
  (comparison of the installed metadata with `RepositorySource.metadataOf`, the basemap manifest compare,
  and the result vocabulary), plus the regional equivalent of the slot swap that
  `core/src/main/java/com/naviveylin/core/mapsource/RepositoryBasemap.kt` already performs for the basemap.
  Built on existing pieces: `RegionIndexParser`, `MapSourceMarker`, `DatabaseMetadataParser`,
  `DatabaseDownloader`, `RepositoryUrlPlanner`. No Android API in this module.
- `:app` — `NaviVeylinApp.kt` (the check's trigger on the app-start path), `data/SettingsStorage.kt` (the
  last-check time and the persisted result set), `data/MapSourceRegistry.kt` (exposes the checker),
  `data/MapStorageManager.kt` (matching an installed directory to the index leaf it came from),
  `ui/mapmanager/MapManagerViewModel.kt` (update state, manual check, the update queue),
  `ui/mapmanager/MapManagerScreen.kt` and a new `ui/mapmanager/MapUpdatesSection.kt`, and new labels in
  `app/src/main/res/values/strings.xml` (and the existing locale variants).
- Tests: `:core` unit tests for the comparison and the swap, `:app` ViewModel tests for the queue and the
  state, Compose tests for the section; one revert-checked case for the swap, plus one on-device
  measurement for the rendered-data change.
- No new dependency and no new Android permission: the check is an HTTP GET through the existing
  `HttpUrlFetcher`, and it runs while the app is in the foreground, so no background-execution exemption
  and no foreground service is involved.

**Native / JNI**

- None. No submodule patch and no bridge override: the update reuses the existing database re-open entry
  point and the existing repository download path.

**Scope and parity**

- The change is a general repository-source feature, not an Android Auto feature. The check runs
  process-wide, so a car session reads the same state, but the map manager screen and its update controls
  exist only in the phone variant — an accepted deviation recorded per `cross-variant-ui-parity` and
  `UI.md` §1 (the car has no map-manager surface, and the car map keeps rendering whatever is installed).

**Guidelines affected**

- `MapRendering.md` §5 ("Epoch Rules") states the invalidation contract for *basemap* download, update and
  delete; a replaced regional database is the same contract on a larger object and the wording widens.
- `Design.md` §9 ("Data, persistence & search") gains the update state and where the check lives.
- `UI.md` §1 ("Cross-variant UI parity") records the phone-only updates surface as an accepted deviation.
- `Logging.md` §1–§3 apply unchanged to the check's new lines: identity only, no coordinates.

**Open risk carried into design**

- A regional database is replaced while the native client may hold it open, and possibly while a
  navigation session is active. The moves themselves are precedented (delete already unlinks files under
  an open database, `openDatabases` closes and reopens the whole set, the basemap swap already runs
  against a live client), but the navigating case is not proven. Design must name the verification that
  settles it — a case that fails before the change plus an on-device run — instead of a guard chosen up
  front; if the evidence shows a session cannot survive the swap, the fallback is to refuse an update
  while a navigation session owns that database and to say so in the UI.

**Rollback**

- Additive and revertible: revert the change and the check, the section and the queue are gone. The two
  new settings fields are ignored by the previous build (`SettingsStorage` reads with
  `ignoreUnknownKeys`), an abandoned `.incoming` directory is inert to the installed map, and no installed
  database is modified by the check itself.
