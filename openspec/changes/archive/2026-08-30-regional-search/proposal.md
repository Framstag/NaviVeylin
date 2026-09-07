# Regional Search — Sibling-Region Expansion for Scoped Search

## Why

Users search for local things (streets, POIs, addresses) without always knowing region borders. Today, when a GPS fix is available, search is scoped to the single admin region containing the position: a query like "Hauptstraße 12" only matches inside that one region, so a match in a neighboring subregion of the same parent region (e.g. the next town over) is missed. Expanding the scope to the parent region's subregions — bounded by a region-level cap so the result set stays manageable — makes local search find what users actually mean.

## What Changes

- When a default admin region is in effect (resolved from GPS position), the search scope SHALL expand from that single region to the region plus its sibling subregions (all subregions of the parent region).
- The expansion SHALL be bounded by a maximum region level: regions above the cap (too coarse, e.g. whole state/country) SHALL NOT be included, keeping search data and result volume manageable.
- The expansion SHALL apply to the structured location/POI search that currently honors the default admin region. Fully qualified queries and the free-text (text index) search SHALL keep their current behavior.
- The JNI bridge SHALL resolve the parent region and its subregions, run the scoped search per included region, and merge/deduplicate results.
- No UI change: the search panel keeps showing the resolved region name; the expanded scope is transparent to the user.

## Capabilities

### New Capabilities

*(none)*

### Modified Capabilities

- `location-search`: The "Search scoped by current admin region" requirement changes — the scope SHALL include the current admin region plus its sibling subregions (subregions of the parent region), bounded by a maximum region level cap, instead of the single region alone. Fallback behavior without a usable GPS fix and precedence of explicit region qualifiers in the query SHALL remain unchanged.

## Impact

- **JNI bridge** (`app/src/main/cpp/libosmscout/libosmscout-client-java/`):
  - `OSMScoutClient.cpp`: resolve parent region + subregions from the admin region handle (via `LocationIndex`/`LocationService` region traversal), filter by region level cap, run `SearchForLocationByString` per included region with `SetDefaultAdminRegion`, merge + dedupe results
  - Region level derived from the region's `admin_level` feature or hierarchy depth; cap configurable (constant or setting)
  - `OSMScoutClient.java`: signature unchanged (handle-based API already passes the resolved region); possibly a new overload if the cap must be passed per call
- **ViewModel** (`MapCanvasViewModel.kt`): unchanged call site; region resolution logic stays as-is
- **Tests**: JNI fakes/stubs updated if signatures change; ViewModel tests for expanded-scope behavior; native-side merge/dedupe covered by existing search tests
- **Performance note**: search cost scales with the number of included sibling regions; the level cap bounds this. Sibling enumeration is a cheap index walk, run off the main thread like existing searches
- **No new dependencies**; libosmscout core `LocationStringSearchParameter` stays single-region — expansion is orchestrated in the JNI bridge
