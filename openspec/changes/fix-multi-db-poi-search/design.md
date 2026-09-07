## Context

See proposal.md — Why. Current state that shapes the approach:

- `MapCanvasViewModel.performPoiSearch` → `client.searchPOIs(category, lat, lon, radius, 100)` →
  JNI `searchPOIsByTypes` (`libosmscout-client-java/src/OSMScoutClient.cpp:7134`).
- The JNI loop iterates `data->dbThread`'s `databases` list, skips the basemap,
  and `break`s at `entries.size() >= limit` (line 7276). DB order = filesystem
  scan order from `MapManager::LookupDatabases` (arbitrary, non-deterministic).
- `DBInstance` carries a cached `dbBox` (`GeoBox`) accessible via `GetDBGeoBox()`
  (`libosmscout-client/include/osmscoutclient/DBInstance.h:57,97`) — bbox
  containment is ready-made, no new native state.
- `PoiEntry.distance` is already populated by `BuildPoiEntry`
  (`OSMScoutClient.cpp:7128`) — the distance sort key exists.
- `GetPOIsInRadius` (POIService) does not sort; per-DB result order is
  DB-internal (index/offset order).
- The search runs inside `dbThread->RunSynchronousJob` under `g_searchMutex`/breaker
  — threading unchanged; the Kotlin side already runs it off the main thread.
- Submodule `libosmscout-client-java` is already dirty with local work (search_scope.h,
  PoiEntry, RouteInstruction, OSMScoutClient.cpp edits) — this patch continues the
  existing local-patch pattern, written minimal/upstreamable.

## Goals / Non-Goals

**Goals:**
- POI search results come from the map(s) the user is looking at, deterministically.
- All loaded maps contribute; results merged, deduplicated, distance-sorted, truncated.
- Minimal, upstreamable submodule patch; no app-layer Kotlin changes.

**Non-Goals:**
- No "active map" plumbing (no new JNI API, no passing `mapPath` from
  `MapCanvasViewModel.initMap`). Bbox-containment heuristics replace it.
- No UI changes (result list already renders label/type/distance).
- No per-DB search API. No behavior change for the single-map case (sorted order
  is a superset of today's unsorted order for one DB — first-DB-wins with one DB
  is still first-DB-only, just re-ordered).
- No change to routing/search-locations paths.

## Decisions

### D1: Bbox-containment pre-sort instead of active-map API (B)
Partition the DB iteration order: databases whose `GetDBGeoBox()` contains the
search center first, others after (stable partition, preserve relative order
within each group). Search center is the current map-center lat/lon.

Rationale: zero API change; the DB bbox is cached (`dbBox`); directly fixes the
observed repro (NRW bbox does not contain the Dortmund search center → searched
last). The Kotlin "active map" (`currentMapKey`) deliberately stays viewport-only.

Alternatives considered:
- **Pass active map path through the API** (new param/overload): most precise,
  but touches Java API + JNI signature + app call site — bigger surface, and the
  active-map selection lives in app state the native layer should not depend on.
- **Sort DBs by distance center→bbox-center**: works but weaker than containment
  (a DB whose bbox encloses the center but whose center is far away would rank
  low). Rejected.

### D2: Ask all databases, then merge; distance sort; truncate (A)
Remove the `entries.size() >= limit` early `break`. After the DB loop, sort
`std::vector<PoiEntry>` by `distance` ascending, with label ascending as the
deterministic tie-break, then resize to `limit`.

Rationale: `PoiEntry.distance` already exists; radius bounds per-DB work, so
"ask all" costs little for typical 1–3 maps (basemap still skipped). Correct
ordering is a strict improvement over today's arbitrary DB-internal order.

Alternatives considered:
- **Keep break, only reorder DBs** (pure B): minimal, but within the containing
  group the first DB still fills the limit, and overlapping containing maps stay
  arbitrary. Rejected — user chose B+A.
- **Sort in Kotlin after JNI returns**: would need the full merged set returned
  (no break) plus Kotlin sort — same native merge work, worse locality. Rejected.

### D3: Dedup by approximate coordinate equality
After merge (before sort or during final truncation), drop entries whose
(lat, lon) round to the same 5-decimal coordinate (~1.1 m at the equator) and
matching... — dedup key is rounded (lat, lon) only; keep the **first** occurrence,
i.e. the one from the bbox-containing DB searched first (D1 order). Coordinates
come from `Node::GetCoords` / bbox center for way/area (`BuildPoiEntry:7120-7124`).

Rationale: overlapping extracts (e.g. Germany + NRW) store the same OSM object —
same node coords or same way/area bbox center. Rounding to 5 decimals tolerates
tiny reprojection differences. Keeping the bbox-first occurrence preserves the
"map the user looks at wins" property.

Alternatives considered:
- **Dedup by label + coords**: labels differ across extract versions (ref vs
  name resolution) — under-dedups. Rejected.
- **Object identity (file offset)**: not exposed on PoiEntry; adds surface. Rejected.

Tie note: dedup happens before the final sort, so "keep first" = first after
bbox pre-sort = the containing map's copy. Sort then orders the survivors; the
sort tie-break (label ascending) only matters for equidistant-but-distinct POIs.

### D4: Patch location and testability
Patch lives in `searchPOIsByTypes` only (`OSMScoutClient.cpp`), pure C++ — no
Android APIs, no libosmscout core changes → CI "Android-free" gate unaffected;
upstreamable as-is to the Framstag repo.

Tests:
- **Native behavior** (the merge/dedup/sort/bbox logic) cannot be exercised by
  app unit tests — `FakeOSMScoutClient` overrides the JNI methods and bypasses
  the loop. Coverage options, in order of preference:
  1. Extract the collect→dedup→sort sequence into a small free function or
     static helper in `OSMScoutClient.cpp` taking `(std::vector<PoiEntry>&,
     const GeoCoord& center)` — unit-testable in a host `test/` build if the
     submodule gains one, or via JavaScout Maven test with a real DB.
  2. JavaScout Maven test (submodule `JavaScout/src/test/`) loading two small
     synthetic DBs (one containing the center, one not; one overlapping) —
     closest to the real path, runs on host.
  3. On-device verification with the known repro (NRW + Dortmund extracts,
     search near Dortmund center → Dortmund results first).
- **Regression safety**: existing app tests (MapCanvasViewModel POI search flow
  via FakeOSMScoutClient) must stay green — no app contract change.

Recommendation: 2 (JavaScout Maven test) as the primary automated gate + 3
(on-device) as final verification. 1 only if the submodule test build already
supports host-side unit tests — check `libosmscout-client-java` test layout
(JavaScout Maven tests exist per AGENTS.md; a native-only helper test would be
new infrastructure, avoid unless JavaScout path is insufficient).

## Risks / Trade-offs

- [Overlapping DBs with genuinely separate POIs at the same coords] → dedup key
  rounds to 5 decimals (~1 m): two distinct objects at the same spot are
  vanishingly rare; acceptable.
- [Distance sort changes the order users saw with one DB] → single-DB output was
  DB-internal order (unspecified); sorted order is strictly more useful since the
  list displays distance.
- [Search latency grows with map count ("ask all")] → bounded by radius; typical
  installs are 1–3 maps; breaker/abort still applies per iteration. If it ever
  matters, the bbox pre-sort makes containing-map hits dominate.
- [Submodule dirty tree adds merge-conflict surface with upstream] → patch kept
  local to one function (+ optional helper); documented as minimal/upstreamable
  per AGENTS.md.
- [Dedup "keep first" depends on D1 order] → deliberate: containment-first
  defines which copy wins; if D1 changed, dedup semantics follow automatically.

## Migration Plan

Submodule patch shipped with the app (submodule bump in the app build). Rollback:
revert the submodule patch and rebuild — behavior returns to first-DB-wins. No
data migration, no schema change, no app-code change.

## Open Questions

- Exact dedup rounding (5 decimals ≈ 1.1 m) and the label tie-break are parameter
  choices; both are constants in the JNI file, trivially tunable during testing.
- Whether to add a native-only helper unit test (option 1) depends on the
  submodule's existing host-test infrastructure — decide during tasks, doesn't
  change the design.
