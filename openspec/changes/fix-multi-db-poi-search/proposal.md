## Why

POI search with multiple maps installed returns results from the wrong map. The JNI
`searchPOIsByTypes` iterates every database in DBThread order (arbitrary filesystem
scan order from `MapManager::LookupDatabases`) and stops as soon as the cumulative
result count reaches the limit — so a stale or adjacent map loaded earlier shadows
the map the user is actually looking at (observed: old NRW extract results displaced
the new Dortmund DB's results until the NRW map was removed). The active map concept
exists only in the Kotlin layer (`MapCanvasViewModel.initMap`/`currentMapKey`); the
native DBThread has no notion of an active map.

## What Changes

- **JNI `searchPOIsByTypes` (submodule `libosmscout-client-java/src/OSMScoutClient.cpp`)**:
  - **Order databases by bounding-box containment first**: databases whose bbox
    contains the search center are searched before databases that do not, so the
    map the user is looking at dominates the results. No API change — the DB's
    own `GetBoundingBox()` is used.
  - **Ask all databases, then merge**: remove the early `break` at the result
    limit; every loaded (non-basemap) database contributes its radius-bounded
    results.
  - **Deduplicate** across overlapping databases (e.g. Germany + NRW both
    containing Dortmund) by approximate coordinate equality.
  - **Sort by distance ascending** from the search center (tie-break by label),
    truncate to `limit`.
- **App layer**: no Kotlin/UI change — `MapCanvasViewModel.performPoiSearch` keeps
  calling `client.searchPOIs(category, lat, lon, radius, MAX_POI_RESULTS)`; the
  result list already shows distance, so sorted merged output renders correctly.
- **Spec**: extend `openspec/specs/poi-search/spec.md` with a requirement covering
  multi-map result coverage, deterministic distance ordering, and deduplication.

No Android Auto impact: POI search is phone-only today.

## Capabilities

### New Capabilities

(none — the behavior change is a delta to the existing POI search capability)

### Modified Capabilities

- `poi-search`: add a requirement that POI search covers all loaded maps (not just
  the first map in DB load order), returns results sorted by distance from the
  search center, and never lists the same object twice when maps overlap.

## Impact

- **Submodule** (`app/src/main/cpp/libosmscout/libosmscout-client-java/`):
  `src/OSMScoutClient.cpp` — `searchPOIsByTypes` loop rewritten (bbox pre-sort,
  no early break, dedup, distance sort). Existing local submodule work already
  touches this file; a **minimal, upstreamable patch** (per AGENTS.md native/JNI
  convention) is the intent. CI gate requires libosmscout stay Android-free —
  this patch is pure C++, no Android APIs.
- **App module**: none (no Kotlin changes; `PoiEntry.distance` already populated
  by `BuildPoiEntry`, displayed in the results list).
- **Tests**: submodule JavaScout Maven tests / native-side coverage for bbox
  ordering, merge, dedup, sort; app unit tests unchanged (FakeOSMScoutClient
  bypasses the native loop — cannot exercise the merge).
- **Specs**: `openspec/specs/poi-search/spec.md` (delta requirement).
- **Guidelines**: `guidelines/MapRendering.md` n/a; `guidelines/Design.md` n/a —
  no new component, threading unchanged (search already runs off the main thread).
- **Rollback**: revert submodule patch; behavior returns to first-DB-wins. Additive
  fix of an existing behavior — no breaking changes.
