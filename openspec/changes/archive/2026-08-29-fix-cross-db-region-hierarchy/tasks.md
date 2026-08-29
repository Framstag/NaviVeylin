# Tasks: Fix Cross-Database Region Hierarchy Resolution in Search Results

Parent spec: `specs/osmscout-jni/spec.md` (requirements: "Search result region hierarchy scoped to originating database", "Search result object identity scoped to originating database", "Free-text dedup scoped to originating database"). Design: `design.md` — Decision 1 (DB attribution), Decision 2 (single-DB hierarchy), Decision 3 (per-DB object resolution + dedup).

## 1. Preparation

- [x] 1.1 Confirm submodule baseline before edits: `git submodule status` shows `f92286fcdb06e45054dd32bc844279286c79eaf6` for `app/src/main/cpp/libosmscout` and `git -C app/src/main/cpp/libosmscout status` is clean — verify no uncommitted local changes exist before patching
- [x] 1.2 Record the pre-fix repro (baseline evidence): with Iceland + Germany/NRW installed, search "Rosenweg 20 Bergkamen", open details, capture the area row showing "Bergkamen" plus an Iceland region name — verify the symptom is reproducible before the fix (screenshot + `adb logcat -s NaviVeylin` excerpt)

## 2. Native Bridge Implementation (submodule patch — `libosmscout-client-java/src/OSMScoutClient.cpp`)

- [x] 2.1 Add DB attribution to search results: introduce `struct ResultWithDb { LocationSearchResult::Entry entry; size_t dbIndex; }`; change `DoSearchLocations` (~L3090) and `DoSearchLocationByForm` (~L2981) to record the loop's `db` index per entry instead of appending bare entries; extend `SerializeStructuredEntries` to receive the attributed results and the captured `databases` list — verify by reading the diff (attribution present in both loops, signature updated consistently)
- [x] 2.2 Scope object-reference resolution (spec: "Search result object identity scoped to originating database"): in `SerializeStructuredEntries`, resolve each entry's `objRef` (coordinates, object name, object type) only against the owning database's `Database`; keep the existing skip-on-unreadable/zero-coordinate behavior — verify unit-testable contract by code inspection + on-device scenario 5.2 (single-map behavior unchanged)
- [x] 2.3 Scope region hierarchy resolution (spec: "Search result region hierarchy scoped to originating database"): call `ResolveAdminRegionHierachie(entry.adminRegion, map)` exactly once against the owning database's `LocationService`, then walk `parentRegionOffset` through that single map — verify by code inspection (only one invocation, no cross-database merge) + on-device scenario 5.1
- [x] 2.4 Scope free-text dedup per database (spec: "Free-text dedup scoped to originating database"): key `seenOffsets` by `dbIndex` both while collecting free-text hits and while filtering against structured results — verify by code inspection (no cross-db offset comparison remains)

## 3. Unit Tests

- [x] 3.1 Extract the hierarchy-path walk into a pure, host-compilable static helper `BuildHierarchyPath(rootRef, chainMap)` (deps limited to `osmscout/location/Location.h` — no database or file I/O) — verify it compiles standalone with the project's libosmscout include path on the host (g++/clang++)
- [x] 3.2 Add host unit test `app/src/test/cpp/hierarchy_walk_test.cpp`: root-only chain yields just the root name; full parent chain joins names with `/` in root→parent order; missing intermediate offset yields the resolvable prefix only; a map entry whose key equals the parent offset yields the correct name (single-database map — no foreign contamination possible) — verify the test passes (`g++ -I app/src/main/cpp/libosmscout/libosmscout/include ... && ./hierarchy_walk_test`)
- [x] 3.3 Run the full existing unit-test suite and verify it stays green: `./gradlew test` — no existing test may fail (regression guard; JNI stub + FakeOSMScoutClient path must remain untouched)

## 4. Build Verification

- [x] 4.1 Verify the bridge compiles for the iteration ABI: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` completes without errors
- [x] 4.2 Verify all target ABIs compile: `./gradlew :app:assembleMobileDebug` (arm64-v8a, armeabi-v7a, x86_64) completes without errors
- [x] 4.3 Verify the AAOS flavor still builds (native code is shared): `./gradlew :app:assembleAutomotiveDebug` completes without errors

## 5. On-Device Verification (spec scenarios)

- [x] 5.1 Multi-map scenario ("Multi-map install resolves hierarchy from the correct map" / "Coinciding file offsets do not poison the hierarchy"): install Iceland + Germany/NRW maps, search "Rosenweg 20 Bergkamen", open details — area row SHALL show only German hierarchy (e.g. "Bergkamen/Kreis Unna/Arnsberg/Nordrhein-Westfalen/Deutschland"), no Iceland name; compare against baseline 1.2 — verify visually + `adb logcat -s NaviVeylin`
- [x] 5.2 Single-map scenario ("Single-database behavior unchanged"): on a Germany/NRW-only install search the same address and verify the hierarchy is identical to the pre-fix output — verify visually
- [x] 5.3 Free-text scenario ("Coinciding offsets across databases both returned"): run a free-text query that hits objects in both maps and verify hits from both maps appear (no valid hit silently dropped) — verify in the search panel result list
- [x] 5.4 Shared-surface regression: search a location via the route panel and via the address book and verify their `adminRegionHierarchy` display is correct (both use the fixed native path) — verify visually + logcat

## 6. Wrap-Up

- [x] 6.1 Validate the change artifacts: `openspec validate` (or `openspec status --change fix-cross-db-region-hierarchy --json`) reports all artifacts done and the change valid
- [ ] 6.2 Document the submodule patch for upstream: keep the diff minimal and isolated to the three functions; write a short comment block in `OSMScoutClient.cpp` above the attribution struct explaining the cross-database offset-collision rationale (upstreamable); note in the commit message that upstream merge is managed separately — verify the diff touches only `OSMScoutClient.cpp`
- [x] 6.3 Update AGENTS.md / README.md only if the submodule pin or documented native conventions change (expected: no change; verify no stale statements about search resolution remain)
