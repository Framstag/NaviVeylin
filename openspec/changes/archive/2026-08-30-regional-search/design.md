# Design — Regional Search: Parent-Region Scope Expansion

## Context

See `proposal.md` — Why. Current state: `OSMScoutClient.searchLocations(query, limit, adminRegionHandle)` resolves a single default admin region (from GPS position via `resolveAdminRegion`) and passes it to `LocationStringSearchParameter::SetDefaultAdminRegion`. libosmscout's `SearchForLocationByString` then scopes the structured location/POI search to that one region. The free-text (text index) search is already unconstrained and unaffected by the region.

Key native facts:
- `AdminRegion` (`Location.h`): `parentRegionOffset`, `childrenOffsets`, `regionOffset`, `name`, `object` (ObjectFileRef).
- `LocationService::ResolveAdminRegionHierachie(region, chain)` walks the parent chain (used already in `resolveAdminRegion`).
- **`LocationIndex::VisitLocations` / `VisitRegionPOIs` recurse into children (`recursive=true` default)** — a search scoped to a region covers the region AND all its subregions in ONE pass (`LocationIndex.cpp:297-370, 418-457`).
- `AdminLevelFeatureValue::GetAdminLevel()` exposes the OSM `admin_level` tag (2=country, 4=state, 5=Regierungsbezirk, 6=county, 8=municipality, 10=suburb) when the region object carries it.
- `LocationStringSearchParameter` accepts exactly one default admin region per call.

## Goals / Non-Goals

**Goals**
- Expand the structured search scope from the single resolved region to the parent region (recursive — covers all subregions), bounded by a region-level cap.
- Cover the motivating case: a user in a kreisfreie Stadt (e.g. Dortmund, level 6) finds a street in a neighboring town (e.g. Bergkamen under Kreis Unna) — both share a Regierungsbezirk (level 5) parent.
- Show the scope region name in the search panel, not just the resolved region.
- Keep the change inside the JNI bridge; no vendored libosmscout core changes.

**Non-Goals**
- No user-configurable cap in this change — cap is a single named constant (level 5), adjustable later.
- No changes to free-text search (already unconstrained).
- No changes to `resolveAdminRegion` / handle lifecycle.

## Decisions

### D1: Expansion orchestrated in the JNI bridge, not core libosmscout
`DoSearchLocations` (OSMScoutClient.cpp) resolves the scope region and passes it via `SetDefaultAdminRegion`; the recursive search does the rest.

- **Why**: `LocationStringSearchParameter` is single-region; extending it to a region list is a core API change with upstream maintenance and CI-gate implications. The bridge already owns the search loop and result serialization.
- **Alternatives considered**: (a) core change to accept `std::vector<AdminRegionRef>` — rejected: touches vendored core, needs upstream coordination; (b) single unconstrained search + post-filter — rejected: loses the region-scoping benefit that makes incomplete queries match; (c) per-sibling search loop — rejected: N searches instead of 1, needs dedupe, and still cannot cover the kreisfreie-Stadt case.

### D2: Region level from `admin_level` feature, hierarchy depth as fallback
Level = `AdminLevelFeatureValue::GetAdminLevel()` on the region's object when present; otherwise hierarchy depth normalized to the admin_level scale (`(depth-1)*2`: root=0, country=2, state=4, Regierungsbezirk=6, county=8, city=10). Cap constant `kMaxSearchRegionLevel = 5` (Regierungsbezirk/district).

- **Why**: `admin_level` is the canonical OSM level but is not guaranteed on every region object; depth is always computable and normalizes to the same scale so one cap constant works for both sources.
- **Alternative considered**: depth only — rejected: admin_level is more accurate where present.

### D3: Scope = highest ancestor at or finer than the cap (walk-up), not sibling set
Given resolved region R:
1. Resolve the parent chain.
2. Walk up: while the parent is at or finer than the cap (level ≥ 5), climb; stop at the first parent coarser than the cap (or the root).
3. Scope = the highest fine ancestor (or R itself if no ancestor qualifies). One `SearchForLocationByString` scoped to that region covers it and ALL its subregions recursively.

- **Why**: libosmscout's region search is recursive, so the scope is a single search — simpler and faster than enumerating siblings, and no dedupe is needed (one search, no overlap). Walking up to the cap implements "one up one down" properly: from a Stadtteil (Eving) the scope reaches the Regierungsbezirk (Arnsberg), covering the city and its neighboring counties (e.g. Bergkamen under Kreis Unna). A single-level climb would stop at the city (Dortmund) and miss Bergkamen.
- **Edge cases**: chain resolution fails → scope = {R}; no ancestor at or finer than the cap (e.g. only state-level data) → scope = {R} (conservative fallback).

### D4: Single recursive search with shared breaker
For the scope region: `SearchForLocationByString` with `SetDefaultAdminRegion(scopeRegion)`, `SetLimit(limit)`, same breaker. Free-text pass runs once, unchanged, after the structured search.

- **Why**: one index walk over the parent's subtree — same order as today's unconstrained (no-GPS) search, which already scans the whole index. Off the main thread, breaker + limit bound the cost.
- **Alternative considered**: per-region limit split — rejected: unnecessary with a single recursive search.

### D5: Expansion applies within the database that resolved the handle
Scope resolution (parent-chain walk) runs only for the database that resolved the handle. Other databases in the multi-map loop search **unconstrained** — the handle's region belongs to another database, and applying it there would read foreign offsets from that db's index (garbage positions, e.g. "position beyond file end" on a second map). The name-based path (no handle) resolves the region per database and applies it normally.

- **Why**: `AdminRegion` offsets (`parentRegionOffset`, `childrenOffsets`) are database-local; walking another db's index with a foreign region ref is invalid.
- **Note**: multi-map installs (e.g. Germany + Iceland) search each map; only the map containing the position is scoped, the others run unconstrained.

### D6: Search panel shows the scope region name
New JNI method `getAdminRegionScopeName(handle)` returns the scope region's name (parent when expansion applies, else the resolved region). The ViewModel uses it for the "Searching in …" label instead of `getAdminRegionName`.

- **Why**: showing "Searching in Dortmund" while the scope covers the whole Regierungsbezirk is misleading; the label must reflect the actual scope.
- **Alternative considered**: drop the label entirely — rejected: the user asked to show the scope.

## Risks / Trade-offs

- **State-level scope for kreisfreie Städte** → bounded by the cap: Regierungsbezirk (level 5) is ~⅓ of a state (~100 municipalities in Arnsberg); one recursive index walk, off the main thread, breaker + limit.
- **`admin_level` missing or inconsistent in data** → depth fallback; worst case a region is treated one level coarser/finer than reality, only shifting the cap boundary by one step. If level-5 regions are absent, kreisfreie Städte fall back to no expansion.
- **Result relevance drops with wider scope** (matches from far subregions) → UI already shows per-result distance from map center; scope name in the panel explains the breadth.
- **No dedupe needed** — single recursive search per database cannot produce overlapping structured results.

## Migration Plan

- Pure additive behavior inside `DoSearchLocations` + one new JNI method; no signature changes to existing methods, no data migration, no settings changes.
- Rollback: revert the bridge change; behavior returns to single-region scoping.
- Verification: existing search tests (JNI fakes, ViewModel tests) must pass unchanged; host test covers the cap decision; manual smoke covers the Dortmund→Bergkamen case.

## Open Questions

- Exact default cap value (5 vs 4) — tunable constant, no spec impact.
- Whether the cap should later become a user setting — deferred; constant now.
