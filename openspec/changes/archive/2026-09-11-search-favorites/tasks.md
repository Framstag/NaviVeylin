# search-favorites — Tasks

## 1. Core merger (spec: favorite-search)

- [x] 1.1 Create `core/src/main/java/com/naviveylin/core/search/MergedSearchResult.kt` (`entry`, `isFavorite`, `isFavoriteHit`) and `FavoriteSearchMerger.kt` with `merge(query, favorites, nativeResults)` implementing: case-insensitive name filter (query ≥ 2 chars), favorite-hit → `LocationEntry` conversion (`matchQuality = "favorite"`), native-result heart marking within 0.0001° of any favorite, dedup of native results within 0.0001° of a favorite hit, favorite hits ordered first. Verify: `:core:compileDebugKotlin` succeeds.
- [x] 1.2 Write `core/src/test/java/com/naviveylin/core/search/FavoriteSearchMergerTest.kt` covering: substring match, case-insensitive match, short-query gate (< 2 chars → no favorite search), favorite hit ordered above native results, native result near a favorite marked `isFavorite`, identical favorite+native deduped to one row, different objects both shown, empty favorites / empty native edge cases. Verify: `:core:testDebugUnitTest` passes.

## 2. Phone integration (spec: search-dialog, favorite-search)

- [x] 2.1 Change `MapCanvasUiState.searchResults` to `List<MergedSearchResult>` and update the search flow in `MapCanvasViewModel` to call `FavoriteSearchMerger.merge(query, favoritesSnapshot, searchLocations(query))` with the latest `favoriteRepository.favorites.value` snapshot. Verify: `:app:compileDebugKotlin` succeeds.
- [x] 2.2 Update `MapCanvasScreen` call site and `SearchDialog` `PlacesContent` to render favorite hits (`isFavoriteHit`) on top with a heart icon, native results with a heart icon when `isFavorite`, and show "no results" only when both lists are empty. Verify: `:app:compileDebugKotlin` succeeds.
- [x] 2.3 Wire favorite-hit selection to `onSearchResultSelected` (history + details sheet) — no new selection path. Verify: tapping a favorite hit records the query in search history and opens the details sheet (unit test + manual).
- [x] 2.4 Add/extend phone tests: ViewModel test asserting merged results (favorite hit first, heart flags) in state; Compose test for `SearchDialog` verifying heart rendering, top placement, and dedup display. Verify: `:app:testDebugUnitTest` passes.

## 3. Android Auto integration (spec: auto-search, favorite-search)

- [x] 3.1 Update `SearchScreen.runSearch` to merge favorites via `AutoEntryPoint.autoFavoritesProvider().favoriteLocations().value` snapshot with native results using the shared merger. Verify: `:auto:compileDebugKotlin` succeeds.
- [x] 3.2 Update `SearchScreenMapper` to render a heart icon on `isFavorite` rows (verify `CarGlyphs`/`Row.Builder.addIcon` availability; fall back to a text marker if no heart glyph exists). Verify: `:auto:testDebugUnitTest` passes with extended `SearchScreenMapperTest` covering heart rows.
- [x] 3.3 Add AA search-screen test: favorite hit appears above native results in the built `ItemList`. Verify: `:auto:testDebugUnitTest` passes.

## 4. Guidelines and docs

- [x] 4.1 Update `guidelines/UI.md` search-surface section: phone + AA search both include favorites (matching, prioritization, heart marking, dedup) — parity rule. Verify: doc reflects the new behavior and no stale "favorites only in suggestions" text remains.
  - Done: §6a (phone) documents typed-query favorite search (substring ≥ 2 chars, above native, heart on hits + matching natives, dedup); §6b (AA) now states the identical rules explicitly with the parity cite; empty-query "favorite rows" in §6a is the unchanged suggestions path, not stale text. Grep confirms no "favorites only in suggestions" wording remains.

## 5. Verification

- [x] 5.1 Run full unit test suite (`:core:test`, `:app:test`, `:auto:test`) and verify all existing tests still pass alongside the new ones.
- [x] 5.2 Build both flavors (`./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`) and verify compilation succeeds without warnings.
- [x] 5.3 On-device (phone): search a favorite name — verify top placement, heart on favorite hit and on native results that are favorites, dedup of identical objects, history recording on selection, empty-query suggestions unchanged. Verify: manual pass on emulator/device.
  - Verified on-device (phone): typing a favorite name shows the favorite hit on top with heart, native results matching a favorite carry the heart, identical objects deduped to one row, selection records the query in history and opens the details sheet, empty-query suggestions unchanged.
- [x] 5.4 On-device (AA emulator/head unit): same query on the `SearchTemplate` — verify favorite hit on top, heart icon rendered by the host, dedup. Verify: manual pass.
  - Verified on the AAOS emulator: the same query on the `SearchTemplate` shows the favorite hit above native results, the heart icon is rendered in the row, and overlapping favorite/native rows are deduplicated.
