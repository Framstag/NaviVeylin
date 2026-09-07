## 1. JNI implementation (submodule patch)

Reference spec: `specs/poi-search/spec.md` — "POI search covers all loaded maps with deterministic ordering".

- [x] 1.1 In `searchPOIsByTypes` (`libosmscout-client-java/src/OSMScoutClient.cpp:7134`), stable-partition the database iteration order so databases whose `GetDBGeoBox()` contains the search center are searched first, others after (design D1). Verify: code compiles and single-DB behavior is unchanged (one DB = same results, re-ordered)
- [x] 1.2 Remove the early `break` at `entries.size() >= limit` (line 7276) so every loaded non-basemap database contributes its radius-bounded results (spec: all loaded maps contribute; basemap skip retained, spec: basemap excluded)
- [x] 1.3 After the loop, deduplicate entries by rounded 5-decimal (lat, lon) (~1.1 m), keeping the first occurrence (the bbox-containing DB's copy, per design D3). Verify: overlapping maps do not duplicate POIs (spec: no duplicates)
- [x] 1.4 Sort the merged entries by `distance` ascending with label ascending as tie-break, then truncate to `limit` (design D2; spec: distance-ascending ordering + limit applied). Verify: result order deterministic for equal inputs

## 2. Submodule host verification

- [x] 2.1 Rebuild the submodule/Import native libs on the host if they are stale (see `ki_processing_failures.log` 2026-09-05: system absl upgrade broke the old link — sed the ninja link line to the stub path before rebuilding)
- [x] 2.2 Confirm the patch contains no Android APIs (`android/log.h`, `__android_log_print`, `ANDROID_LOG_*` — grep the changed hunk) so the CI Android-free gate passes (spec/AGENTS.md native purity)

## 3. JavaScout Maven multi-DB integration test

Reference spec: `specs/poi-search/spec.md` — same requirement, scenarios: bbox-first, all-maps, dedup, distance-asc, limit.

- [x] 3.1 Add multi-DB scenarios to `JavaScout/src/test/java/com/framstag/libosmscout/client/OSMScoutClientPoiSearchTest.java` (gated on a `-Dpoi.test.db.dir` fixture with ≥ 2 maps: one whose bbox contains the search center, one whose bbox does not, and an overlapping pair): assert containing-map results come first, all maps contribute, no duplicate coordinates, distance-ascending output, and `limit` truncation — mirroring the existing DB-driven test's `Assumptions` gating
- [x] 3.2 Create/point the two-map fixture: either two small extracts imported with the host Import tool into one lookup dir, or the dev machine's real extracts (e.g. NRW + Dortmund as in the original repro). Verify `LookupDatabases` loads both (logcat/native log "Total installed maps found: 2")
- [x] 3.3 Build the client JAR from submodule `java/` sources, install to `~/.m2` (never the Gradle-built JAR — it excludes OSMScoutClient, see ki log 2026-09-05), and run `cd JavaScout && mvn test -Dpoi.test.db.dir=<two-map-dir> -Dnative.lib.dir=<built-libs>`; verify the new multi-DB tests and all existing POI search tests pass

## 4. App regression + build gates

- [x] 4.1 Run the app unit tests `./gradlew :core:test :auto:test :app:testMobileDebugUnitTest` and verify the POI search flow tests (FakeOSMScoutClient-overridden `searchPOIs`/`searchPOIsByTypes`) stay green — no app contract change
- [x] 4.2 Verify the native change compiles for all target ABIs: `./gradlew :app:assembleMobileDebug` (arm64-v8a, armeabi-v7a, x86_64 — no `-Pandroid.injected.build.abi` filter) builds without errors

## 5. On-device verification + cleanup

- [ ] 5.1 Install the rebuilt app with the multi-map setup that reproduced the bug (e.g. stale NRW extract + new Dortmund DB). Search near the Dortmund center: active map's results appear first, list is distance-ascending, no duplicated entries (spec scenarios). Inspect `adb logcat -s NaviVeylin` for the search
- [ ] 5.2 Verify single-map behavior on device: search returns distance-ascending results, identical object set to before (plus deterministic order)
- [ ] 5.3 Close the TODO.md §10 "Multi-DB POI search" entry and note the behavior change (merge + bbox-first + dedup + distance sort) in a one-line update; append any failed approach to `ki_processing_failures.log` per apply guidance
