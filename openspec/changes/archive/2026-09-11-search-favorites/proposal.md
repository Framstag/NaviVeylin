# search-favorites

## Why

The search dialog only surfaces favorites when the query is empty (suggestion rows). Typing a query hides them entirely — favorites cannot be searched, and search results that happen to be favorites are indistinguishable from ordinary results. Users expect favorites to be first-class search targets.

## What Changes

- **Favorites become searchable in Places mode** (phone): typing a query also matches favorites by name (case-insensitive substring). Matching favorites are shown **on top** of native location results, each marked with a heart icon.
- **Native results marked as favorites**: a native search result whose coordinates match an existing favorite (within ~11 m, the same tolerance as `findFavoriteByLocation`) is shown with a heart icon.
- **Deduplication**: when a favorite hit and a native result refer to the same object (same coordinates within tolerance), only the favorite hit is shown. Different objects are both shown.
- **Selection behavior**: tapping a favorite hit behaves like any search result — records the query in search history and opens the details sheet.
- **Android Auto parity**: the AA `SearchTemplate` search-as-you-type applies the same merge — favorites matching the query appear on top, marked with a heart icon, deduplicated against native results.
- **Shared merge logic**: a new `FavoriteSearchMerger` in the `core` module (pure Kotlin, unit-tested) is consumed by both the phone ViewModel and the AA search screen, so the two variants cannot drift.
- **Additive, non-breaking**: no API, data, or native/JNI changes. Empty-query suggestions (all favorites listed) are unchanged.

## Capabilities

### New Capabilities

- `favorite-search`: Favorites are searchable by name in the Places search mode; favorite hits are prioritized above native results, marked with a heart, deduplicated against identical native results, and behave like normal search results on selection. Applies to both the phone search dialog and the Android Auto `SearchTemplate` (parity).

### Modified Capabilities

- `search-dialog`: The "Typing hides suggestions" behavior is extended — typing now also searches favorites, and the Places results list can contain favorite hits on top of native results.
- `auto-search`: The AA search template's results list can contain favorite hits on top of native results, marked with a heart.

## Impact

- `core/src/main/java/com/naviveylin/core/search/FavoriteSearchMerger.kt` — new: pure merge logic (fav hits, heart marking, dedup, ordering) + `MergedSearchResult` data class
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — search flow calls the merger; `searchResults` in `MapCanvasUiState` becomes `List<MergedSearchResult>`; favorite hits selected via `onSearchResultSelected` (history + details)
- `app/src/main/java/com/naviveylin/ui/map/SearchDialog.kt` — `PlacesContent` renders favorite hits on top with heart icons; native results hearted when `isFavorite`; "no results" only when both lists empty
- `auto/src/main/java/com/naviveylin/auto/SearchScreen.kt` — `runSearch` merges favorites (via `AutoEntryPoint.autoFavoritesProvider()`) with native results
- `auto/src/main/java/com/naviveylin/auto/SearchScreenMapper.kt` — heart icon on favorite rows
- `auto/src/main/res/values/strings.xml` + `values-de/strings.xml` — heart content description (if a new string is needed)
- Specs: `openspec/specs/search-dialog/spec.md`, `openspec/specs/auto-search/spec.md` (deltas in this change)
- Guidelines: `guidelines/UI.md` — search-surface parity rules updated in the same change
- Tests: `FavoriteSearchMergerTest` (new, core), phone ViewModel/Compose tests for the merged results, `SearchScreenMapperTest` extension for heart rows
- On-device verification: phone — search a favorite name, confirm top placement + heart + dedup; AA emulator/head unit — same behavior on the `SearchTemplate`

**Scope**: both phone search dialog and Android Auto search template (parity requirement). POIs and Contacts modes are unaffected — favorites are a Places-mode concept.

**Rollback**: additive UI/logic change — revert merger wiring in ViewModel/SearchScreen and the dialog/mapper rendering; no data or native impact.
