# Design: search-fixes

## Context

See proposal.md — Why. Current state: `searchLocations` (JNI, `OSMScoutClient.cpp` L2140) runs `LocationService::SearchForLocationByString` over every open map database, then resolves each result's `ObjectFileRef` (node/area/way) to extract coordinates and OSM name. Resolution failure today is silent (lat/lon stay 0.0) and the entry is still returned; a stale search index can instead abort the process because `Area::Read`/`Way::Read`/`Node::Read` pass an out-of-range type id to `TypeConfig::Get*TypeInfo`, whose `assert` fires inside `DataFile<N>::ReadData` (no exception, process dies). The libosmscout core is a git submodule pinned at `bebd534` and rebuilt per-ABI via CMake — core changes are local to this repo until upstreamed.

## Goals / Non-Goals

**Goals**
- Search must never abort the process, regardless of map-data/index consistency.
- All five user-facing defects fixed with minimal, targeted changes.
- Fixes in the Kotlin layer where the defect is UI/state logic; native patch only for the crash.

**Non-Goals**
- No search-index rebuild / repair tooling (out of scope — see Open Questions).
- No changes to the details-sheet or favorites UI beyond the new-group failure path.
- No map data format changes; submodule patch is behavior-compatible with valid data.

## Decisions

### D1: Fix crash in libosmscout `*::Read` by bounds-checking the type id and throwing `IOException`
`Area::Read` (Area.cpp L185-187), `Way::Read`, `Node::Read` read a raw type id from the file and immediately call `typeConfig.GetAreaTypeInfo/WayTypeInfo/NodeTypeInfo(id)`, which asserts `id <= types.size()`. Patch each to validate `id` (0 or `> typeConfig.Get*TypeCount()`) and `throw IOException` instead of calling the assert path. `DataFile<N>::ReadData` already catches `IOException` and returns false (DataFile.h L174-183), so `Database::GetAreaByOffset`/`GetWayByOffset`/`GetNodeByOffset` return false — no API change, valid data reads are byte-identical.
- *Alternatives considered*: (a) Removing/softening the assert in `TypeConfig::Get*TypeInfo` — rejected: the assert is a cross-cutting invariant guard used by all readers; silently returning a null `TypeInfoRef` would defer the failure to a null-deref elsewhere. (b) Pre-validating offsets in JNI before reading — rejected: the type id is only known after reading the file header; duplicating the parse in JNI is fragile. (c) try/catch in JNI around the native call — rejected: the assert aborts the process; no catchable exception ever reaches JNI.

### D2: JNI `searchLocations` skips entries whose object cannot be resolved
The per-entry lookup loop already tests `database->GetAreaByOffset(...)` etc. but ignores the result, so failures yield entries with lat=lon=0. Track a per-entry resolved flag inside the loop and exclude unresolved entries (and entries with no valid `ObjectFileRef`) when building the Java `LocationEntry[]`. No crash, no zero-coordinate results.
- *Alternative considered*: returning the entry with lat/lon 0 and letting UI handle it — rejected: selecting such an entry would center the map on (0,0); skipping is strictly better and matches spec ("entries whose object cannot be resolved SHALL be omitted").

### D3: Follow-mode deactivation lives in `onSearchResultSelected`
Set `followMode = false` in `_uiState` before `updateCenter(entry.lat, entry.lon)` (MapCanvasViewModel.kt L858-870). No other path touches follow mode; the GPS collector checks `followMode` before re-centering, so this alone prevents the viewport yank.
- *Alternative considered*: a `isSearchSelectionActive` flag that blocks follow until user pans — rejected: follow mode is an explicit user preference; turning it off on selection is the simplest, least stateful behavior the user asked for.

### D4: Group auto-create at repository level, not in C++
`FavoriteLocationService::AddFavorite` intentionally returns false for a missing group. Instead of changing native semantics (which would need a full native rebuild and touches all ABIs), make `FavoriteRepository.addFavorite` create the group first when the name is not in the current group set, then add the favorite; if group creation fails (name exists by the time of the call), return false so the caller shows an error. This single change fixes all callers: details-sheet "Add to Favorites" with a new group (MapCanvasViewModel L1041) and the FavoritesSheet map-location dialog, which today falls back to a group name ("Favorites") that may not exist either.
- *Alternative considered*: auto-create inside `FavoriteLocationService::AddFavorite` — rejected: changes the service contract ("false if group not found"), requires native rebuild of all 3 ABIs, and hides duplicate-name conflicts at the wrong layer.

### D5: Route panel layout/visibility fixes are pure Compose changes
- Swap button: `RoutePanel.kt` L114-128 — keep the button between the fields (vertically centered by the column flow) but change the container row to `Arrangement.End` with end padding.
- Convenience entries: `RouteSearchResults` (L343) already receives `query` — wrap the "Current Location" and "Select Favorite" rows + dividers in `if (query.isEmpty())`.
- *Alternative considered*: moving entries into the ViewModel state — rejected: purely presentational gating; no state change needed.

## Risks / Trade-offs

- [Submodule patch diverges from upstream `bebd534`] → Keep the patch minimal (bounds check + throw) and upstreamable; document it in the submodule; on next submodule bump, re-apply or land upstream first.
- [`IOException` on invalid ids also fires during normal render reads if a map is corrupt] → Desired: rendering then skips bad objects instead of crashing, same graceful path; no behavior change for valid maps.
- [Skipping unresolved entries silently hides data] → Accepted trade-off for crash-freedom; remaining results are unaffected (spec).
- [Follow mode now persists off after selecting a result] → Matches existing persist pattern in `onToggleFollowMode`; if undesired, flip to transient (see Open Questions).
- [Auto-created group appears in `favoriteGroups` after `refreshState()`] → `FavoriteRepository.addGroup` already refreshes + persists; ordering addGroup → addFavorite is atomic enough (single-threaded via `Dispatchers.Default`).

## Migration Plan

1. Patch submodule files (`Area.cpp`, `Way.cpp`, `Node.cpp`); build all 3 ABIs via `./gradlew :app:assembleDebug` (or per-ABI for iteration).
2. Kotlin/JNI changes build together; no data migration — favorites JSON format unchanged.
3. Rollback: revert the submodule patch commit and Kotlin changes; behavior returns to current (crashy) state — no schema/data changes to unwind.

## Open Questions

- Should follow-mode deactivation on search-result selection persist across app restarts, or be transient (restored on next launch)? Current design persists (consistent with `onToggleFollowMode`). Answering later does not change the specs or the code structure (one flag write).
- Should we later add index-vs-data consistency validation at database open (e.g., detect and flag stale search indexes) or an automatic search-index rebuild? Out of scope for this change; the graceful-skip path makes it non-urgent.
