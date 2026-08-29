# Design: Fix Cross-Database Region Hierarchy Resolution in Search Results

## Context

See proposal.md — Why. The bug: `SerializeStructuredEntries` (native bridge, `libosmscout-client-java/src/OSMScoutClient.cpp` inside the `app/src/main/cpp/libosmscout` submodule) builds the `adminRegionHierarchy` by calling `LocationService::ResolveAdminRegionHierachie(entry.adminRegion, adminRegionMap)` for **every** loaded database, merging all results into one `std::map<FileOffset, AdminRegionRef>`, then walks the parent chain by looking up `parentRegionOffset` keys. `FileOffset` values are positions inside one database's index files; when two databases (Iceland, Germany/NRW) happen to place a packed admin-region record at the same byte offset, the later pass overwrites the map entry and the walk prints the foreign region's name. The same offset-collision hazard exists in the object-reference resolution loop (coordinates/name, last-success-wins across databases) and in the free-text dedup (`seenOffsets` compares raw offsets across databases).

Reverse lookups (`getRegion`, `getAddressAt`, `resolveAdminRegion`) are coordinate-scoped per database (`GetDBGeoBox().Includes(...)`, `ReverseLookupRegion`, `DescribeLocationByAddress`) and are NOT affected — out of scope.

## Goals / Non-Goals

**Goals:**
- Every search result's object identity and region hierarchy resolve entirely within the database that produced the entry.
- No Java/Kotlin API changes; `LocationEntry` field set and `OSMScoutClient` method signatures unchanged.
- Fix stays a minimal, upstreamable patch inside the submodule's `libosmscout-client-java` — no changes to libosmscout core (a core change would be a separate upstream effort).
- Correctness also for route-panel and address-book search (they share the same native entry points).

**Non-Goals:**
- Changing `ResolveAdminRegionHierachie` or `LocationIndex` semantics upstream.
- Rendering/UI changes (details sheet, search panel show whatever the hierarchy string contains).
- Cross-database dedup or merging of results beyond what exists today.
- Performance changes beyond the (negligible) cost of carrying a database index per entry.

## Decisions

### Decision 1: Attribute each search result to its originating database

**Chosen:** In `DoSearchLocations` and `DoSearchLocationByForm`, collect results as `std::vector<ResultWithDb>` where `ResultWithDb { osmscout::LocationSearchResult::Entry entry; size_t dbIndex; }` — the index into the `databases` list captured by the `RunSynchronousJob` lambda. Pass the collection plus the captured `databases` list to `SerializeStructuredEntries`, which then uses only the owning DB for object resolution and hierarchy resolution.

- Rationale: the originating database is known exactly at the moment the search runs (per-iteration `db` in the existing loop); recording it is a few lines and removes the ambiguity everywhere downstream. This is the only approach that fixes all three hazards (object identity, hierarchy, dedup) with one mechanism.
- Threading (guidelines/Design.md §4): all work stays inside the existing `dbThread->RunSynchronousJob` critical section (background native thread); the attribution struct is local to the job, no new shared state, no main-thread involvement.

**Alternative A (re-derive owner during serialization):** in `SerializeStructuredEntries`, break the object-resolution loop at the first database that successfully reads the object and remember it; use that database for the hierarchy too.
- Pros: no change to the search loops.
- Cons: attribution is a re-derivation, not the origin; the resolution loop's first-success can itself pick the wrong database before the hierarchy needs it; the object-resolve hazard is merely made deterministic, not fixed (wrong database still possible when both databases have a readable object at the offset). Rejected.

**Alternative B (namespace map key by database):** key the merged map by `(db, FileOffset)`.
- Pros: tiny, surgical.
- Cons: the walk follows `parentRegionOffset` values of the owning database only — a namespaced map still has no way to know which namespace to walk without the owning database. Solves nothing without attribution anyway. Rejected (subsumed by Decision 1).

**Alternative C (offset validation):** detect foreign records inside `ResolveAdminRegionHierachie`-fed maps (e.g. verify the loaded record's `regionOffset` equals the position read from).
- Pros: also fixes the upstream API for other consumers.
- Cons: requires a libosmscout core change (upstream patch with cross-DB semantics upstream has no use for today: upstream is single-database focused); behavior of `LoadAdminRegion` on foreign positions is undefined, validation would be heuristic. Rejected for this change; noted as possible future upstream hardening.

### Decision 2: Resolve the region hierarchy from one database only

**Chosen:** After attribution, the hierarchy block calls `ResolveAdminRegionHierachie(entry.adminRegion, pathMap)` exactly once — against the owning database's `LocationService` — and walks `parentRegionOffset` through that single map. Map keys are then all offsets within one database's files; collisions are impossible by construction.

- Rationale: matches how the upstream demos and `LookupModule` use the API (one database in scope), so the fix is upstreamable in spirit: NaviVeylin merely restores the single-database assumption the API was written under.
- Note: the `adminRegion` object inside an entry is already owned by the originating database (search ran against that DB's index), so `ResolveAdminRegionHierachie` with an empty map and the same location service is the API's sanctioned call pattern.

### Decision 3: Scope object-reference resolution and free-text dedup per database

**Chosen:** The object-resolution loop in `SerializeStructuredEntries` iterates only the owning database (single lookup, no cross-database fallback — the entry came from that DB, so a read failure means drop, matching the existing resilience behavior). Free-text dedup `seenOffsets` becomes per-database (`std::map<dbIndex, std::set<FileOffset>>`) both when collecting hits and when filtering against structured results.

- Rationale: closes the remaining offset-collision holes of the same class; keeps the existing "skip unreadable entries" resilience (spec: location-search "Search resilient to inconsistent map data").
- Risk check: could a valid entry fail because the object lives in a different database than the index that reported it? Practically no — search runs per-DB against that DB's location index, and entries reference that DB's files. The current all-databases loop was a workaround, not a requirement.

## Risks / Trade-offs

- **[Risk] A previously-working exotic case relied on cross-database object resolution** (object referenced from DB A but actually readable only in DB B) → Mitigation: per-DB indexes and object files are generated together by libosmscout's importer; refs never span databases. On-device validation with the two-map setup verifies no regression.
- **[Risk] Submodule patch drift**: the bridge file `OSMScoutClient.cpp` already carries NaviVeylin local changes (UTF-8 validation etc.); a future submodule bump can conflict → Mitigation: keep the patch minimal and isolated to the three functions; the maintainer manages the upstream merge separately (per proposal).
- **[Risk] Free-text dedup change alters result counts** (previously wrongly deduped cross-db hits now appear) → Mitigation: that is the intended fix; verify visibly on device that coinciding-offset hits from Iceland and Germany both appear.
- **[Trade-off] Slightly larger `SerializeStructuredEntries` signature** (takes db list + attribution) — internal only, no Java surface change.

## Migration Plan

- Deployment: ship with next app update (native `.so` change; no data migration). Rollback: revert the submodule patch — old behavior returns (bug included), app remains functional.
- Upstream: the maintainer carries the patch to upstream `libosmscout` separately; this repo's submodule pin updates then, ideally dropping the local delta if upstream adopted it identically.

## Open Questions

None that would change the specs, approach, or task breakdown.
