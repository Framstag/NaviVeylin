## 1. State & ViewModel

- [x] 1.1 Add `SearchMode` enum (PLACES/POIS/CONTACTS) and `searchMode: SearchMode = PLACES` + `searchOpen: Boolean = false` to `MapCanvasUiState`; replace `openSearchPanel`/`poiSearchOpen` flags (spec: search-dialog — mode switch, entry points). Verify: `:app:compileMobileDebugKotlin` compiles; existing usages of the removed flags updated.
- [x] 1.2 Add ViewModel functions: `openSearch()`, `closeSearch()`, `setSearchMode(mode)`; keep `onSearchPanelOpened()` behavior (region resolution eager-start) wired to `openSearch()` (spec: location-search — region scoping). Verify: unit test asserts state transitions and that per-mode state survives mode switches.

## 2. Dialog shell & mode switch

- [x] 2.1 Create `ui/map/SearchDialog.kt` with M3 `SearchBar` (expanded driven by `searchOpen`), back affordance + `BackHandler`, IME-inset handling (spec: search-dialog — full-screen dialog behavior). Verify: Compose test opens dialog, back dismisses, map hidden.
- [x] 2.2 Add `SegmentedButton` mode switch (Places/POIs/Contacts; Contacts segment omitted without `READ_CONTACTS`) (spec: search-dialog — mode switch). Verify: Compose test asserts three segments, Contacts hidden without permission, mode switch preserves per-mode state.

## 3. Places mode

- [x] 3.1 Move `SearchPanel` content into the dialog's Places mode: search field wiring (`onQueryChanged`), debounced results, region-scope name line, result items with disambiguation + distance (spec: location-search — suggestions-while-type, result item display, scope region name). Verify: existing `SearchPanel` Compose tests re-targeted to the dialog pass.
- [x] 3.2 Build the suggestions section for empty query: recent-search `SuggestionChip` row (youngest first, horizontal scroll), favorite rows, "Current Location" row gated on GPS (spec: search-dialog — Places mode suggestions; search-history — chips). Verify: Compose test asserts suggestions visible on empty query, hidden while typing, restored on clear; chip tap fills search box.

## 4. POIs mode

- [x] 4.1 Move `PoiSearchPanel` content into the dialog's POIs mode: category `FilterChip` row (single-select, tap-again clears), radius slider, explicit search button (disabled without category), results list, error/empty states (spec: poi-search — category and radius selection; search-dialog — POIs mode search flow). Verify: Compose test asserts no auto-search on selection, button disabled without category, search runs on tap.
- [x] 4.2 Embed `PoiResultsWithMap` (portrait/landscape split, markers, current position) in POIs mode (spec: poi-search — results map embedded in the search sheet). Verify: existing `PoiSearchPanel` Compose tests re-targeted pass; on-device check portrait + landscape.

## 5. Contacts mode

- [x] 5.1 Embed the address-book person search (list + filter field, `AddressBookViewModel`) as the dialog's Contacts mode; keep `READ_CONTACTS` gate (spec: address-book-search — phone entry; search-dialog — Contacts mode). Verify: Compose test asserts contacts list filters by name, clears restore full list; on-device check with and without permission.

## 6. Entry points & removal of old surfaces

- [x] 6.1 Wire entry points: search button (`MapActionColumn`) and `/` key open the dialog; `openSearchPanel` shared-location signal opens it too (spec: search-dialog — entry points; keyboard-shortcuts — `/` key). Verify: Compose test asserts all three entry points open the dialog.
- [x] 6.2 Replace `MapMenu` "Search POIs" entry with "Search" (opens dialog); remove "Address book" entry (spec: map-menu — entries). Verify: Compose test asserts menu shows Search, no Search POIs / Address book entries.
- [x] 6.3 Delete `SearchHistorySheet.kt`; remove `showSearchPanel`/`showSearchHistory`/`poiSearchOpen` sheet composition from `MapCanvasScreen`; render `SearchDialog` from `searchOpen` (spec: map-canvas-screen — back dismisses topmost overlay). Verify: `:app:compileMobileDebugKotlin` compiles; no references to deleted files remain.

## 7. Strings & i18n

- [x] 7.1 Add strings to `app/src/main/res/values/strings.xml` + `values-de/strings.xml`: mode labels (Places/POIs/Contacts), suggestions header, search placeholder (spec: i18n-l10n — all user-facing text translatable). Verify: German translation completeness test passes; no hardcoded UI strings in `SearchDialog.kt`.

## 8. Tests

- [x] 8.1 Rework `SearchPanel`/`PoiSearchPanel` Compose tests to the unified dialog; add tests: mode switch state, suggestions visibility, POI explicit-button flow, Contacts permission gating (spec: search-dialog — all requirements). Verify: `./gradlew :app:testMobileDebugUnitTest` passes.
- [x] 8.2 Add ViewModel unit tests for `searchMode`/`searchOpen` transitions and per-mode state retention (spec: search-dialog — mode switch preserves state). Verify: new tests pass in `:app:testMobileDebugUnitTest`.

## 9. Build & verification

- [x] 9.1 Build mobile debug APK and verify no compile errors or warnings (spec: all). Verify: `./gradlew :app:assembleMobileDebug` succeeds.
- [x] 9.2 On-device verification (closed per user instruction: verified via unit/compose tests + both-flavor builds; manual device pass deferred): open dialog from button, menu, `/` key; switch modes; run a POI search with explicit button; check back dismisses; check Contacts mode with and without permission; check IME insets and landscape (spec: search-dialog, poi-search, address-book-search).
- [x] 9.3 Update `guidelines/UI.md` search-surface rules to the unified dialog (spec: cross-variant-ui-parity — phone-only scope noted). Verify: guideline diff reviewed; no Auto search rules touched.

## 10. Regression

- [x] 10.1 Verify existing search behaviors unchanged: result selection centers map + marker, route-panel search reuse, follow-mode deactivation, viewport restore on POI close, history recording on selection (spec: location-search, poi-search, search-history). Verify: existing tests pass + on-device spot check.
- [x] 10.2 Full unit test suite green (spec: all). Verify: `./gradlew test` passes.
