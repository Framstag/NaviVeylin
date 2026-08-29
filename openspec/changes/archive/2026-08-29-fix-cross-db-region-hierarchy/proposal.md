# Fix Cross-Database Region Hierarchy Resolution in Search Results

## Why

With multiple maps installed (e.g. Iceland + Germany/Nordrhein-Westfalen), a search result's admin region hierarchy can resolve parent regions from the **wrong map's database**. Searching "Rosenweg 20 Bergkamen" shows the correct city "Bergkamen" followed by an Iceland region name. `FileOffset` values are only meaningful inside their own database's files; the JNI bridge merges hierarchy resolutions from every loaded database into a single map keyed by raw offset, so coinciding offsets resolve some hierarchy levels from the wrong map.

## What Changes

- **DB attribution for search results**: `DoSearchLocations` and `DoSearchLocationByForm` record which database produced each result entry instead of merging them into a flat vector without provenance.
- **Scoped object resolution**: `SerializeStructuredEntries` resolves each result's object reference (coordinates, name, type) only against the database that produced the entry (currently iterates all databases last-wins — same latent offset-collision hazard).
- **Scoped region hierarchy**: the admin region hierarchy path is resolved only against the originating database's `LocationService`; the map keyed by `FileOffset` never mixes entries from different databases (root cause of the Iceland-name bug).
- **Per-database free-text dedup**: cross-map free-text hit dedup (`seenOffsets`) currently dedups by raw `FileOffset` across databases — restrict to the same database to avoid dropping valid hits from another map that coincide by offset.

No Java/Kotlin API changes; `LocationEntry` fields and method signatures stay identical (fix is internal to the native bridge). Not breaking. Rollback: revert the native submodule patch.

## Capabilities

### Modified Capabilities

- `osmscout-jni` — the JNI bridge now guarantees that every search result's object identity and admin region hierarchy are resolved entirely within the database that produced the entry, never from another loaded map.

## Impact

- **Files modified** (native bridge, inside the `app/src/main/cpp/libosmscout` submodule):
  - `libosmscout-client-java/src/OSMScoutClient.cpp` — `DoSearchLocations` (~L3090), `DoSearchLocationByForm` (~L2981), `SerializeStructuredEntries` (~L2666), free-text dedup blocks
  - No changes to libosmscout core (`LocationIndex.cpp`, `LocationService` etc.) — consumed via existing APIs (`ResolveAdminRegionHierachie`, offset-based object reads)
- **Submodule policy**: the fix lands in the NaviVeylin-patched `libosmscout-client-java` inside the submodule — minimal, upstreamable patch; upstream merge managed separately by the maintainer of this repo (see design.md).
- **Affected apps**: phone search, route-panel search, address-book search (all share `OSMScoutClient.searchLocations`/`searchLocationByForm`); Android Auto uses the same native search primitives, so the AA result path benefits identically.
- **Not affected**: reverse lookups (`getRegion`, `getAddressAt`, `resolveAdminRegion`) — coordinate-scoped per database already.
- **Verification**: unit tests in the bridge's search fixtures plus on-device check with the two-map setup (Iceland + Germany/NRW) described in the bug report.
