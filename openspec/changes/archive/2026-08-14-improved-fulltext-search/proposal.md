# Proposal: Improved fulltext search

## Why

The `added-distance-to-fulltext-search` branch of the upstream libosmscout repo
(`78c2e668`) contains fixes to the free-text search that eliminate garbage
result entries, plus a distance display for search results relative to the map
center. NaviVeylin's vendored submodule (`app/src/main/cpp/libosmscout`, at
`735290d7f`) does not yet contain those native fixes, and the Android UI shows
no distance information at all, so users cannot judge how far a result is from
their current map view without selecting it.

## What Changes

- Apply upstream free-text search fixes from the `added-distance-to-fulltext-search`
  branch to the vendored libosmscout submodule (`OSMScoutClient.cpp`):
  - Drop free-text index refs that cannot be resolved to an object (`refNone` /
    stale index keys) instead of serializing empty `(0,0)` entries.
  - Drop entries whose object resolves to invalid `(0,0)` coordinates.
  - Fix `freeTextEntries.resize(limit - results.size())` padding: `resize()` with
    a larger size pads the vector with default-constructed entries that get
    serialized as garbage results. Only shrink, never grow.
- Show the straight-line (haversine) distance from the current map center to
  each search result, in kilometers, right-aligned in a smaller font than the
  result's primary label.
- Distance applies to search result lists in the map search panel and in the
  route panel (start/destination picking), which both render
  `List<LocationEntry>`.
- Reuse existing haversine helpers (`MapRenderer.haversineDistance`,
  `MapCanvasViewModel.haversine`) — no new geometry code; add a small shared
  km-formatting helper mirroring upstream `LocationSearchRanker.formatDistanceKm`
  (one decimal below 10 km, whole km above, "km" suffix).
- No JNI Java API change: `LocationEntry` already carries `lat`/`lon`, and
  distance is computed client-side from the map center. The search API
  (`searchLocations(query, limit, adminRegionHandle)`,
  `NO_ADMIN_REGION`) is already present in the local submodule.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `location-search`: search result entries SHALL display the distance from the
  current map center to the result location, right-aligned in a smaller font
  than the primary label, formatted in km.
- `search-free-text`: free-text results SHALL NOT contain garbage entries —
  entries that cannot be resolved to a real object (unresolvable ref, `(0,0)`
  coordinates, or padding artifacts) SHALL be omitted from the result list.

## Impact

- `app/src/main/cpp/libosmscout/` (submodule) — cherry-pick/merge the
  `OSMScoutClient.cpp` search fixes from branch `78c2e668`. The local submodule
  carries NaviVeylin-local commits (JNI `AttachCurrentThread` casts, render
  logging), so a plain submodule pointer bump may lose local changes; merging
  the specific hunks is safer. Also adds the upstream `SearchReproTest`
  regression test (free-text entries must have non-empty label and non-`(0,0)`
  coordinates).
- `app/src/main/java/com/naviveylin/ui/map/SearchPanel.kt` — per-result
  right-aligned distance label (smaller font).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — expose
  current map center (from `ViewportState.centerLat/centerLon`) to the search
  panel result rendering.
- `app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` +
  `RoutePanelViewModel.kt` — distance label in route search results; route panel
  needs access to the current map center (passed down or read from shared
  state).
- New shared helper for haversine distance + km formatting (extracted from the
  private copies in `MapRenderer.kt` / `MapCanvasViewModel.kt`).
- Tests: unit test for km formatting (sub-km precision, rounding, unit suffix)
  and haversine values for known coordinate pairs.
