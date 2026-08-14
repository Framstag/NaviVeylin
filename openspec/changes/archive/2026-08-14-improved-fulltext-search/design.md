# Design: Improved fulltext search

## Context

See proposal.md — Why. The vendored libosmscout submodule
(`app/src/main/cpp/libosmscout`, pinned at `735290d7f`) already carries the
current search JNI API (`searchLocations(query, limit, adminRegionHandle)`,
`NO_ADMIN_REGION`, admin-region resolution) but lacks four free-text search
fixes from upstream branch `added-distance-to-fulltext-search` (`78c2e668`),
and no distance information is shown in the Android search UI. The branch
computes distance client-side from `LocationEntry.lat/lon` (already exposed),
so no JNI API change is required. Map center is available in the app as
`ViewportState.centerLat/centerLon`, tracked by `MapCanvasViewModel`.

## Goals / Non-Goals

**Goals:**
- Fix garbage free-text entries at the native layer (unresolvable refs, (0,0)
  coordinates, resize padding), matching upstream `78c2e668`.
- Show km distance from map center, right-aligned in smaller font, in both the
  map search panel and route panel search results.
- Keep the change small: reuse existing haversine implementations; no JNI API
  or `LocationEntry` changes.

**Non-Goals:**
- Consolidating every private haversine copy in the app (`MapRenderer.kt`,
  `MapCanvasViewModel.kt`) into the new shared helper. Only the search UI uses
  the shared helper; existing call sites stay untouched to avoid unrelated
  churn and test breakage.
- Porting the upstream JavaScout UI changes (`SearchOverlay`, `style.css`,
  `LocationSearchRanker`) — desktop-only, not built by this project's CMake.
- Porting the upstream `SearchReproTest` — it needs a live native library and
  map data; Android unit tests run against a symbol-less stub .so.
- Changing the Favorites sheet search (local list filter, not
  `LocationEntry` results).

## Decisions

### D1: Acquire native fixes via submodule merge, not hunk cherry-pick
Merge upstream commit `78c2e668` into the submodule (fetch + merge), resolve
conflicts in `OSMScoutClient.cpp` keeping NaviVeylin-local Android changes
(AttachCurrentThread casts, render-logging comment) and taking the branch's
four search-fix hunks, then bump the submodule pointer in the parent repo.

Rationale: keeps the submodule on the upstream line so future merges stay
small. The merge brings JavaScout-only changes (desktop UI, CSS, tests) that
are inert here — NaviVeylin's CMake builds only the client-java library, never
the JavaScout module.

Alternatives considered:
- Manual hunk cherry-pick: smaller blast radius, but permanently diverges the
  submodule and loses upstream continuity; rejected.
- Pointer bump to `78c2e668` as-is: drops the NaviVeylin-local commits
  (AttachCurrentThread casts, render logging); rejected.

### D2: Compute distance in the composable layer, not the ViewModel
Pass the current map center (`viewport.centerLat/centerLon` from
`MapCanvasUiState`) into `SearchPanel` and `RoutePanel` as parameters; each
result row computes haversine distance + formats it via a shared helper at
composition time.

Rationale: the spec requires distance "against the current map center at
display time". Computing in the composable keeps that guarantee trivially
correct (no snapshot/staleness), and the result list is small (≤ 20 entries).
Recomputation on recomposition/scroll is negligible (a few trig calls per row);
a `remember(entry, centerLat, centerLon)` cache is available if profiling ever
shows a need.

Alternatives considered:
- Precompute in the ViewModel when search completes, storing a formatted
  string in UiState: bakes in a stale center (search completes seconds after
  the keystroke) and stores derived display strings; rejected.
- RoutePanelViewModel reading viewport state itself: duplicates state that
  `MapCanvasViewModel` already owns and keeps; pass-down from
  `MapCanvasScreen` (which composes both sheets) is simpler.

### D3: New shared distance helper
Add `app/src/main/java/com/naviveylin/util/DistanceFormat.kt` with
`haversineDistanceMeters(lat1, lon1, lat2, lon2)` and
`formatDistanceKm(meters)`. Formatting mirrors upstream
`LocationSearchRanker.formatDistanceKm`: `< 10 km` → one decimal (`"0.5 km"`),
`>= 10 km` → whole km (`"12 km"`), `Locale.ROOT`, "km" suffix. Pure Kotlin —
plain-JUnit testable without Robolectric.

### D4: Route panel gets map center via parameters
`MapCanvasScreen` already holds both sheets and the live viewport; pass
`centerLat`/`centerLon` into `RoutePanel` (and `SearchPanel`) from
`MapCanvasUiState.viewport`. No new VM state, no storage reads.

## Risks / Trade-offs

- [Submodule merge conflicts in `OSMScoutClient.cpp` (NaviVeylin-local Android
  edits overlap the branch's changes)] → Fix hunks are localized (entry
  serialization, result truncation); resolve keeping local Android casts and
  logging, verify with a native build (`:app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`).
- [Merge pulls in unrelated JavaScout/stylesheet changes] → Inert; JavaScout
  module is not built by this project. Accepted.
- [Padding fix changes result count semantics: list may be shorter than the
  limit when free-text yields few valid entries] → This is the intended
  behavior (spec: no padding entries); no consumer relies on fixed-size lists
  (search panel renders whatever it gets).
- [Native fixes untestable in unit tests (stub .so has no symbols)] →
  Covered by the Android-side formatting tests only; native verification is a
  manual device check with a map that has a text index. Accepted; noted in
  tasks.

## Migration Plan

- Submodule: merge upstream commit, bump pointer in parent repo (`git add
  app/src/main/cpp/libosmscout`). No data migration.
- Rollback: revert the submodule pointer bump and the UI change; both are
  independent of app data.

## Open Questions

None — all decisions above are resolvable without changing the specs or task
breakdown.
