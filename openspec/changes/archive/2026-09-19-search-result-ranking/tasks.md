# Tasks

## 1. Native bridge: per-attribute quality (submodule)

- [x] 1.1 Add the new fields to `LocationEntry.java` in the submodule (`app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/LocationEntry.java`): `adminRegionMatchQuality`, `postalAreaMatchQuality`, `locationMatchQuality`, `addressMatchQuality`, `poiMatchQuality`, `hasHouseNumber`, `matchedName`; verify with a plain-JUnit reflection test in `:osmscout-client-java` that the fields exist and default to null/false (spec: search-result-ranking — "Perfect match classification uses native per-attribute quality"; design D2)
- [x] 1.2 Write the five quality strings, `hasHouseNumber` and `matchedName` in `SerializeStructuredEntries` (`src/OSMScoutClient.cpp`, where the collapsed string is built) for the string search and the form search; verify the submodule compiles (design D2)
- [x] 1.3 Write empty qualities and `matchedName` = hit label in the free-text serialization loop (`src/OSMScoutClient.cpp:3787`); verify the submodule compiles and no `.cpp` warning is emitted (spec: search-free-text — "Exact free-text hit is a close match"; design D2, D8)
- [x] 1.4 Confirm the free-text loop no longer claims `matchQuality = "match"` unconditionally for hits whose name did not match exactly; verify by reading the emitted values against a marisa hit in an on-device run (task 7.3) (spec: search-free-text; design D2)
- [x] 1.5 Commit the submodule change on `naviveylin-local` and bump the gitlink in the main repo; verify `git status` shows a clean submodule and the gitlink points at the new commit (AGENTS.md — submodule pinning)

## 2. Core ranker

- [x] 2.1 Add a single haversine implementation to `:core` (`core/src/main/java/com/naviveylin/core/`) and make `app/src/main/java/com/naviveylin/util/DistanceFormat.kt` delegate to it; verify the existing distance unit tests stay green with no behavior change (guidelines/Design.md §4 — one source of truth)
- [x] 2.2 Implement query attribute extraction: classify the parsed query into named criteria (city/admin region, postal area, street/location, house number, POI name) using `AddressParser`, and classify entry attributes as criteria or context (admin region + postal area are context) (spec: search-result-ranking — "Query attributes classified as criteria or context"; design D1)
- [x] 2.3 Implement the perfect/close classification from per-attribute quality and the extra-criterion rule; unit-test the matrix: city-only query, city+street, street query against a house-level entry, named postal code vs entry postal area, unnamed region, transliterated spelling (`ss`/`ß`), missing quality fields, and a coordinate query (whose result is the exact answer) (spec: search-result-ranking — both classification requirements; design D2, D11)
- [x] 2.4 Implement the tiered ordering (perfect by distance, then close by quality then distance, deterministic label tie-break); unit-test each ordering key and the stability for equal entries (spec: search-result-ranking — "Result ordering by tier then distance")
- [x] 2.5 Implement the reference-point rule (fix, else fallback center, else no distance) and the degradation path when the reference or the quality data is absent; unit-test all three reference cases and the no-reference ordering (spec: search-result-ranking — "Distance reference for ordering and display", "Degradation without per-attribute quality"; design D4, D11)
- [x] 2.6 Add the marking decision (favorite, perfect, both, neither) as a pure function next to the ranker; unit-test the four combinations (spec: search-result-ranking — "Perfect-match marking and cross-surface parity"; design D5)

## 3. Phone and route-panel wiring

- [x] 3.1 Raise the candidate fetch count to the shared constant (60) in `MapCanvasViewModel.searchLocations` and rank the candidate set with the `:core` ranker before the display list is built; keep the displayed maximum at 20; verify with a `FakeOSMScoutClient` test that a perfect match placed beyond the first 20 candidates is displayed (spec: search-result-ranking — "Candidate set larger than displayed list", location-search — "Suggestions-while-type"; design D3)
- [x] 3.2 Supply the reference point on the phone: last known fix, else the viewport center; pass the same value to the ranker and to the displayed distance; verify with unit tests for both cases (spec: location-search — "Result distance display"; design D4)
- [x] 3.3 Apply the same candidate count, ranking and reference in `RoutePanelViewModel` / `RoutePanel.kt`; verify the route-panel search tests cover the new order and distance reference (spec: location-search — "Distance shown in route panel search"; design D4)
- [x] 3.4 Update the existing unit tests that assert the old map-center distance or the native order; verify the whole `:app` unit-test suite passes with real execution (not an up-to-date no-op) and quote the executed-task count (TODO.md §17 — up-to-date masking)
- [x] 3.5 Leave `AddressBookResolver` on its current quality-based selection (optionally forwarding the larger candidate set), so resolution outcomes do not change; verify `AddressBookResolver` tests stay green unmodified (design D9)

## 4. Marking

- [x] 4.1 Add the shared marking artwork to `:core` covering none/favorite/perfect/favorite+perfect, drawn on canvas like `ManeuverSymbols`, with a cache; unit-test that the four combinations produce distinct, non-null bitmaps (spec: search-result-ranking — "Perfect-match marking and cross-surface parity"; design D5)
- [x] 4.2 Render the marking in the phone result row (`SearchDialog.kt` `SearchResultItem`) next to the existing favorite heart, and set `contentDescription` from string resources naming both facts; add the strings to `values/` and `values-de/` (spec: location-search — "Result item is marked when perfect"; design D5)
- [x] 4.3 Add a Compose test for the phone result row asserting that a favorite+perfect row shows the composite marking and exposes both facts through the accessibility label (spec: location-search — "Perfect match is marked"; guidelines/Design.md §11)
- [x] 4.4 Verify the German translation completeness test and the hardcoded-string gate pass with the new resources (guidelines/Build.md; project i18n gates)

## 5. Android Auto surface

- [x] 5.1 Pass the candidate count, ranked list and reference point through `AutoSearchProvider` / `AutoServiceModule` (`provideAutoSearchProvider`), using `AutoLocationProvider.position().value` as the fix and the current car viewport center as the fallback; verify with `:auto` unit tests for both cases (spec: auto-search — "Car uses the same reference rule as the phone"; design D4, D12)
- [x] 5.2 Render the marking glyph in `SearchScreenMapper.buildResultRow` via `CarIcon` while keeping the favorite glyph for favorite rows, and add the distance text line; verify with mapper unit tests for the four marking combinations and for rows carrying both facts (spec: auto-search — "Perfect match marked on the car", "Search results displayed as list"; design D5)
- [x] 5.3 Apply the displayed maximum of 20 to the ranked candidate set on the car surface; verify with a mapper test that a perfect match beyond the backend's own first 20 still appears (spec: auto-search — "Search result limit")
- [x] 5.4 Verify the car search reads the position inside the existing background search coroutine and adds no main-thread work and no new provider lifecycle (`NavigationSession` keeps owning start/stop) (guidelines/Design.md §4, §8; design D12)

## 6. Guidelines and documentation

- [x] 6.1 Update `guidelines/UI.md` §6a and §6b: result-row decoration, the perfect-match marking and its composition with the favorite heart, the car's glyph-only constraint, and the distance reference rule (spec: search-result-ranking — marking parity; config rule "update the guideline in the same change")
- [x] 6.2 Update `README.md` / `AGENTS.md` only if the search pipeline description there is affected; verify no stale statement about the search result order remains

## 7. Verification

- [x] 7.1 Build both flavors for all three ABIs and verify the build compiles without errors or warnings (`./gradlew :app:assembleDebug`, then the per-ABI mobile and automotive builds) (config rule: build verification)
- [x] 7.2 Run the full unit-test suites for `:app`, `:auto`, `:core` and `:osmscout-client-java` after clearing previous test results, and verify green with real execution (quote executed-task count and elapsed time) (TODO.md §17)
- [x] 7.3 On-device phone check: query "Waltrop" (and a street query in a named city) with a GPS fix and without one; verify the exact match is first and marked, distances match the order, and logcat shows the expected candidate count and timing (spec: search-result-ranking — ordering and reference scenarios)
- [x] 7.4 On-device Automotive check (AAOS AVD, x86_64 build installed with `adb install -r -t`): same query on the car template; verify identical order and marking as the phone, distance line present, and no surface-lifecycle regression (spec: auto-search — parity scenarios; guidelines/Design.md §8)
- [x] 7.5 Measure the per-query native cost with the larger candidate set and verify the debounce-plus-search budget is acceptable; lower `CANDIDATE_LIMIT` to 40 if it is not (design D3 risk mitigation)
- [x] 7.6 Record any leftover defects detected but not caused by this change in `TODO.md`, and log failed approaches with timestamps in `ki_processing_failures.log` (config apply guidance)

## 8. Verification follow-ups

Work added by the verification pass (`/openspec-verify-change`) — each item closes an issue that pass raised.

- [x] 8.1 Give each candidate source its own budget at the native boundary, so a full page of structured results no longer deletes the free-text candidates before ranking (`OSMScoutClient.cpp` truncation + `freeTextLimitReached`), and verify the native library compiles for all three ABIs (spec: search-free-text — "Free-text hit competes with structured candidates"; design D8)
- [x] 8.2 Make the address ranker read the per-attribute qualities instead of the collapsed `matchQuality`, falling back to it only for an older native library, and verify the address/address-book tests stay green (spec: search-result-ranking — "Perfect match classification uses native per-attribute quality"; design D6, `TODO.md` #35)
- [x] 8.3 Add a cross-surface order test (phone vs Android Auto pipeline, same query and reference) and a fix-versus-panned-map-center order test (spec: search-result-ranking — "Same query on both surfaces", "Fix available while the map is panned elsewhere")
- [x] 8.4 Log the candidate count and the native elapsed time per query, so the candidate-set cost budget is measurable on the next device pass (design D3)
- [x] 8.5 Record the host-testability limitation of the JNI serialization in design.md (verification notes)
