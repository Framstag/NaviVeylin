# Tasks — fix address lookup accuracy

Specs: `location-search` (full formatted address resolution, structured-over-free-text), `address-book-search` (resolution accuracy deltas), `auto-search` (car screen deltas).

## 1. Native search semantics (submodule)

- [x] 1.1 `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — `DoSearchLocationByForm`: set `param.SetPartialMatch(true)` so a missing house number falls back to street/region results (spec: location-search "House number missing from index falls back to street").
- [x] 1.2 Same file — `DoSearchLocations`: `param.SetPartialMatch(true)` (spec: location-search "Postal code inside query does not block"); sort merged results so structured (`idx`) entries rank above free-text (`txt`) entries when native rank is equal (spec: location-search "Structured matches above free-text" — satisfied by the existing structured-before-free-text serialization order; verified).
- [x] 1.3 Submodule: add regression test for the postal-code-in-query case (host test suite), commit on `naviveylin-local`, bump the gitlink in the main repo. Commit `a4c309421`.

## 2. App-side address parsing and search (Kotlin)

- [x] 2.1 New `app/src/main/java/com/naviveylin/data/AddressParser.kt`: free-text → `(street, houseNo, plz, city)`; strips PLZ inside the street string; handles comma and space-separated component orders (spec: address-book-search "Postal code in street field parses correctly").
- [x] 2.2 New shared `StructuredAddressSearch` helper (app `data/`): when a query parses as an address, run `searchLocationByForm` and merge/rank its results ahead of raw string results (spec: location-search "Full formatted address resolution").
- [x] 2.3 `MapCanvasViewModel.mergeSearchResults`: use `StructuredAddressSearch` (spec: location-search full-address scenarios).
- [x] 2.4 `AutoServiceModule.provideAutoSearchProvider`: use `StructuredAddressSearch` (spec: auto-search full-address scenarios).
- [x] 2.5 `AddressBookResolver`: house-number parsing via `AddressParser` (PLZ-in-street edge), `buildQueries` adds "street house plz" and "street plz" before city-only; gate city-only auto-selection to street/postal-evidenced results (spec: address-book-search "Wrong-location free-text result not selected"); `rank()` gives `boundary_administrative` ≥ POI base score.

## 3. Tests

- [x] 3.1 New `AddressParserTest` covering PLZ-in-street, comma formats, leading/trailing house numbers.
- [x] 3.2 Extend `AddressBookResolverTest`: PLZ-in-street address, house-missing → street fallback, city-only gate, ranking weights.
- [x] 3.3 `MapCanvasViewModel` search tests: full-formatted-address query triggers form search; structured-over-free-text ordering — new `MapCanvasViewModelStructuredAddressSearchTest`.
- [x] 3.4 Run `./gradlew test` (all modules) — all existing + new tests green. (The 3 zoom-test failures first seen at HEAD `fe9ae77` were NOT pre-existing/unrelated: they were stale phone-side assertions plus a test-determinism race, resolved 2026-09-13 by `smooth-decimal-auto-zoom` — TODO.md §14.)
- [x] 3.5 Native host verification: `LocationLookup`/`LocationLookupForm` against `arnsberg-regbez/v27` — full query with/without PLZ returns house/street (with `partialMatch=true`), form with missing house returns street fallback; "Witten" ordering verified via `AddressBookResolverTest.admin region outranks poi for city-only ranking`.

## 4. Final verification

- [x] 4.1 `./gradlew :app:assembleMobileDebug` and `:app:assembleAutomotiveDebug` (arm64-v8a) compile clean with the new JNI code. Full 3-ABI matrix runs in CI.
- [x] 4.2 On-device: search box full address + contact flow on emulator with NRW map; logcat `NaviVeylin`/`AddressBookResolver` shows form hit. **Delegated** (2026-09-13) to `fix-contact-address-resolution` tasks 5.1 and 5.5 — that change supersedes this one's resolver-side assumptions (`address-book-search`) and its on-device checklist covers exactly this surface (contact flow with per-candidate `AddressBookResolver` logcat; unified-search-dialog parity for the same address). No device session has been run yet for either change; the verification is carried once, there.

## 5. Supersession and archive order

- [x] 5.1 Archive-order contract: this change MUST be archived before `fix-contact-address-resolution`. Both modify `address-book-search`, and this change's delta for `### Requirement: Address resolution search` (8 scenarios) is a strict subset of that change's delta for the same requirement (14 scenarios, same names, plus the dedup rewrite of `Selecting a person with multiple addresses`). Archiving in reverse order would silently overwrite the superseded requirement and drop those scenarios.
- [x] 5.2 Uniquely owned deltas: this change also modifies `location-search` and `auto-search` (search-box side, native `SetPartialMatch(true)` in submodule commit `a4c309421`); `fix-contact-address-resolution` touches neither. Do NOT archive with `--skip-specs`.
- [x] 5.3 `fix-contact-address-resolution` deliberately leaves native `partialMatch` semantics alone — they are correct for the search box; that change only fixes the resolver misreading them. No native/JNI revert is part of this change's close-out.
