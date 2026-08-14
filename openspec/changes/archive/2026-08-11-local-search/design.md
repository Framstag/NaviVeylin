# Design — Admin-Region-Scoped Local Search

## Context

See `proposal.md` — motivation and scope. Current state:

- App search: `MapCanvasViewModel` debounces query (300ms), calls `OSMScoutClient.searchLocations(query, limit)` per keystroke (JNI → `LocationStringSearchParameter` free-text search, transliterate matcher, limit 20).
- libosmscout core already supports the needed feature: `LocationStringSearchParameter::SetDefaultAdminRegion(AdminRegionRef)` — used as fallback when the search string does not imply a region (see `libosmscout-client-qt` `SearchModule.cpp`).
- No existing JNI API resolves an admin region from a coordinate. Available building blocks: `LocationService::VisitAdminRegions(AdminRegionVisitor)` (walks the region hierarchy, visitor controls child traversal), `Database::GetWayByOffset`/`GetAreaByOffset` (load region boundary geometry from `AdminRegion::object`), `AdminRegion::regionOffset`/`dataOffset` as stable identifiers.
- GPS state already flows into the ViewModel: `locationService.location` (StateFlow) is classified into `GpsFixQuality` (NONE/POOR/GOOD) with 5s freshness + 50m accuracy thresholds (`MapCanvasViewModel.kt`).
- JNI pattern precedent: native objects exposed to Kotlin via integer handles (route handles in `calculateRouteAsync`/`cancelRoute`).
- No point-in-ring helper exists in libosmscout's public utils — a small ray-casting test is needed.

## Goals / Non-Goals

**Goals:**
- Scoped search: when a GOOD GPS fix exists, resolve the admin region at the position and use it as default admin region for native search, so incomplete addresses/POIs match.
- Region resolved once per position, reused across keystrokes of a search session, re-resolved only after significant movement.
- All scoping logic unit-testable in Kotlin (thresholds, caching, fallback); native side stays a thin resolver.
- No change to vendored libosmscout core.

**Non-Goals:**
- No waiting/blocking for a fix when search opens (no "establish fix first" flow). If no usable fix, search is unconstrained.
- No UI changes (no region indicator, no settings toggle).
- No changes to route-panel search semantics beyond inheriting the scoped results.
- No address-reverse-geocoding feature beyond region resolution (no street/POI description at position).

## Decisions

### D1: Two native calls — `resolveAdminRegion` + handle-parameterized `searchLocations`
Kotlin first calls `long resolveAdminRegion(lat, lon)` (returns an opaque handle, 0 if no region found), then passes the handle to `searchLocations(String query, int limit, long adminRegionHandle)`. `void releaseAdminRegion(long handle)` frees it.

- Rationale: resolution is expensive (index walk + geometry), search is per-keystroke. Separating them lets the ViewModel cache the region and enforce the movement threshold in Kotlin, where it is unit-testable. Handle (not name/offset) avoids a second native region re-lookup per search and mirrors the existing route-handle pattern.
- Alternatives considered:
  - `searchLocations(query, limit, adminRegionName)` — C++ would need name→region resolution per search (extra search pass, ambiguity on name collisions). Rejected.
  - `searchLocations(query, limit, lat, lon)` — resolves internally per keystroke; movement-threshold logic would live in C++ and be untestable in Kotlin. Rejected.
  - Single "current region slot" in `ClientData` — works (one client), but implicit shared state; handle is explicit and future-proof (route panel could pass different regions later). Rejected for explicitness.

### D2: Coordinate→admin-region resolution via `LocationDescriptionService::ReverseLookupRegion`
Native `resolveAdminRegion` runs in the existing `DBThread::RunSynchronousJob` context:

1. Skip databases whose bounding box (`DBInstance::GetDBGeoBox`) does not include the coordinate.
2. Call `LocationDescriptionService::ReverseLookupRegion(coord, result)` — libosmscout's canonical reverse lookup; it walks the location index with its own containment machinery and returns the chain of admin regions containing the coordinate (country → state → … → city).
3. Pick the deepest region (longest parent chain via `ResolveAdminRegionHierachie`), matching how client-qt picks the highest-admin-level region for its default search region.
4. Store the `AdminRegionRef` in a `std::map<int64_t, AdminRegionRef>` in `ClientData`; return the handle.
5. Handle lifetime managed by Kotlin (`releaseAdminRegion`), also released on `close()`.

- Rationale: `ReverseLookupRegion` is the API libosmscout itself uses (client-qt `LookupModule::requestRegionLookup`) — battle-tested containment over region boundary geometry. A first implementation hand-rolled a `VisitAdminRegions` walk + ray-casting point-in-ring test; it returned no region on real maps, so it was replaced.
- Alternative considered: reusing `LocationDescriptionService` reverse lookup and extracting the region — that IS this decision. The rejected alternative was the hand-rolled geometry walk (unreliable, duplicate logic).

### D3: GPS gating reuses existing quality classification
Scoped search only when `GpsFixQuality == GOOD` (fix ≤ 5s old, accuracy ≤ 50m — existing thresholds in `MapCanvasViewModel`). POOR/NONE → unconstrained search (current behavior).

- Rationale: single source of truth for "usable fix"; avoids duplicating freshness/accuracy logic. Thresholds are already product-tested values.
- Alternative considered: separate stricter thresholds for search. Rejected — no evidence search needs different precision; revisit if region resolution mis-scopes in practice.

### D4: Region cache + movement threshold in ViewModel
ViewModel keeps `resolvedAdminRegionHandle` + the coordinate it was resolved at. On each debounced query: if handle exists and distance(position, resolvedCoord) ≤ movement threshold (500m), reuse; else re-resolve (or clear when fix is no longer GOOD). Region resolution failure (handle 0) falls back to unconstrained search. Handle released on clear/panel close/ViewModel clear.

- Rationale: matches spec requirement "Admin region follows user movement" — stable within a typing session, follows significant moves. 500m is generous for typical city districts; cheap constant to tune.
- Alternative considered: re-resolve per keystroke — wastes native work, violates the spec's stability scenario. Rejected.

### D5: JNI stubs/fakes updated in lockstep
`FakeOSMScoutClient.kt` (test override surface) and any signature references updated when the native methods change; host stub `.so` unchanged (symbols are loaded by name, no signature baked in).

### D6: Region name via handle lookup, not a second resolution
A new `String getAdminRegionName(long handle)` looks the resolved ref up in the native handle store and returns `AdminRegion::name` (null for unknown handle or empty name). The ViewModel fetches the name once when a handle is resolved and caches it alongside the handle; it is exposed to the UI via `MapCanvasUiState.searchAdminRegionName` and cleared when the handle is released.

- Rationale: name is fetched once per resolution (not per keystroke); no second index walk. The handle store lookup is a cheap map access.
- Alternative considered: `resolveAdminRegion` returning a small `AdminRegionInfo` object (handle + name) — one JNI round trip instead of two, but requires a new Java value class and JNI object construction. Rejected for minimal JNI surface; the extra call happens once per region change, not per query.

## Risks / Trade-offs

- [Region resolution cost (full index walk + geometry loads)] → Runs once per significant movement in the DB thread, not per keystroke; region index is small (hundreds of entries); bounding-box pre-check prunes most geometry loads.
- [Boundary ambiguity: GPS on a district border may pick the wrong region] → Acceptable: default region is a fallback, explicit queries still win; 500m threshold prevents flapping; worst case = results of the neighbor region, no crash.
- [Regions with only node/relation geometry (no area/way rings)] → Resolver handles node refs (treat as point, unlikely to contain), skips unreadable geometry (mirrors existing "resilient to inconsistent map data" requirement); no crash on missing objects.
- [Native handle leaks] → Released on close() and when replaced/cleared in ViewModel; small count (≤ 1 live at a time).
- [Behavior change for existing searches: scoped results differ when fix present] → Only when fix is GOOD; unconstrained otherwise; explicit region in query still wins (spec: default region is fallback only).

## Migration Plan

- Feature-flagged off initially? No — behavior only activates with a GOOD GPS fix, which is a strict superset condition; rollback is a one-line revert of the ViewModel wiring.
- Land in order: (1) JNI resolver + handle plumbing, (2) ViewModel caching/gating with fakes, (3) tests. Each step keeps the app compiling and search working unconstrained.

## Open Questions

- Whether a "wait briefly for a fix at search open" flow is wanted (user mentioned "oder dieser hergestellt werden kann"). Current design: no blocking wait; last-known-fix reuse only via the existing freshness rule. Can be added later without spec change.
- Movement threshold value (500m) — tune after field testing; constant in ViewModel.
