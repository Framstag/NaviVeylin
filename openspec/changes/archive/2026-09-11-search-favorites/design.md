# search-favorites — Design

## Context

See proposal.md — Why. Current state: the phone search dialog (`SearchDialog.kt` → `PlacesContent`) shows favorites only as empty-query suggestion rows; typed queries run `client.searchLocations()` (native) and produce `List<LocationEntry>` with no favorite awareness. The AA `SearchTemplate` (`SearchScreen.kt`) is native-only via `AutoSearchProvider`. Both variants already have reactive favorites access: `MapCanvasViewModel` holds `favoriteRepository.favorites: StateFlow<Map<String, List<FavoriteLocation>>>`, and `AutoEntryPoint.autoFavoritesProvider()` exposes the same shape to the `:auto` module. `LocationEntry` (JNI bridge) has no `isFavorite` field; `FavoriteLocation` stores name + lat + lon only (no object reference).

## Goals / Non-Goals

**Goals:**
- One shared, unit-tested merge algorithm consumed by both variants (parity by construction)
- Zero native/JNI changes — favorites stay name+coords, identity stays coordinate-based
- Favorite hits on top, heart-marked; native results heart-marked when they match a favorite; dedup of identical objects
- Fav-hit selection reuses the existing search-result path (history + details)

**Non-Goals:**
- No changes to POIs or Contacts modes (favorites are a Places-mode concept)
- No changes to empty-query suggestions (all favorites listed — unchanged)
- No object-reference persistence for favorites (would need JNI serialization changes; out of scope)
- No changes to the in-flight `unify-auto-search` change (AA empty-query suggestions) — orthogonal

## Decisions

### D1: Shared `FavoriteSearchMerger` in the `core` module

A pure-Kotlin merger in `core/src/main/java/com/naviveylin/core/search/`:

```kotlin
data class MergedSearchResult(
    val entry: LocationEntry,
    val isFavorite: Boolean,     // native result near a favorite, or a favorite hit
    val isFavoriteHit: Boolean   // came from the favorites list → top section
)

class FavoriteSearchMerger(private val toleranceDegrees: Double = 0.0001) {
    fun merge(query: String, favorites: List<FavoriteLocation>,
              nativeResults: List<LocationEntry>): List<MergedSearchResult>
}
```

Logic: (1) `favorites.filter { it.name.contains(query, ignoreCase = true) }` → converted to `LocationEntry` (`label = name`, `lat`, `lon`, `matchQuality = "favorite"` — the pattern already used in `onFavoriteSelected`); (2) each native result marked `isFavorite` when within tolerance of **any** favorite; (3) native results within tolerance of a favorite **hit** are suppressed (dedup); (4) output ordered favorite hits first, then native.

- **Alternative A (chosen)**: shared merger in `core`. `:auto` cannot depend on `:app`, but both need identical behavior; `core` is the shared module (`AutoEntryPoint` already lives there). Single implementation, unit-tested once, parity guaranteed.
- **Alternative B**: merge in `MapCanvasViewModel` + duplicate in `SearchScreen`. No new module surface, but two implementations of the same rule set — drift risk, and the parity spec scenario becomes untestable in one place.
- **Alternative C**: merge in the Compose layer (filter `favoriteGroups` in `PlacesContent`). Zero ViewModel churn, but logic in UI, untestable without Compose tests, and not shareable with AA (no Compose there).

### D2: `MergedSearchResult` wrapper instead of extending `LocationEntry`

- **Alternative A (chosen)**: wrapper data class carrying `entry` + two booleans. Explicit, no JNI change, trivially unit-testable.
- **Alternative B**: add `isFavorite` to `LocationEntry` in the JNI bridge. Requires a submodule patch (per AGENTS.md native-change rules: minimal, upstreamable) and touches `OSMScoutClient.cpp` serialization — heavy for a UI concern.
- **Alternative C**: parallel lists (`favoriteHits` + native + a coordinate set for marking). Implicit coupling between lists; easy to drift; harder to test as a unit.

### D3: Object identity = coordinates within 0.0001° (~11 m)

- **Alternative A (chosen)**: proximity with the same tolerance as `FavoriteRepository.findFavoriteByLocation` (0.0001°). Consistent with existing app logic (`isSelectedLocationFavorite` uses it), no data model change.
- **Alternative B**: label match. Fragile — duplicate names, renamed favorites, case/whitespace variance.
- **Alternative C**: store `objectFileOffset` in `FavoriteLocation.attributes` at creation. Would give true object identity, but requires JNI serialization of attributes, and favorites created from long-press have no object reference anyway. Rejected as out of scope; noted as a future enhancement.

### D4: Fav-hit selection routes through `onSearchResultSelected`

- **Alternative A (chosen)**: favorite hits are `LocationEntry`s; tapping one calls the existing `onSearchResultSelected(entry)` — records the query in `SearchHistoryRepository` (query non-blank) and opens the details sheet. Matches "it's a search result now" semantics; zero new selection code.
- **Alternative B**: route through `onFavoriteSelected` (the empty-query suggestion path) — full `getDescription`/bbox dance, but no history recording. Would need a new history call added; diverges from native-result behavior.

### D5: Threading and reactivity

The merger is pure Kotlin (no IO, no allocation-heavy hot path — runs per debounced query). Phone: called inside the existing search flow after `searchLocations(query)` (already on `defaultDispatcher` via `withContext`), with the latest `favoriteRepository.favorites.value` snapshot. AA: called in `SearchScreen.runSearch` after the provider call (already on `ioDispatcher`), with `autoFavoritesProvider().favoriteLocations().value` snapshot. No new dispatchers, no lifecycle changes — both call sites are existing coroutines. Favorites are read as a snapshot at search time; a favorite added mid-typing appears on the next debounced query, which is acceptable for a search surface.

## Risks / Trade-offs

- [Two distinct objects within ~11 m treated as identical] → Acceptable: same tolerance as existing favorite-identity logic; a favorite hit wins, which is the desired bias. Documented in spec (dedup scenario).
- [Favorites list grows large → merge cost per keystroke] → Filtering is a linear `contains` scan over names; native search dominates the cost. No measurable impact at realistic favorite counts.
- [AA row heart icon availability] → `Row.Builder.addIcon` supports a leading icon; `CarGlyphs` already provides icon assets. If a heart glyph is unavailable, fall back to a text marker — verify on emulator/head unit (task).
- [`searchResults` type change ripples through phone UI state] → `MapCanvasUiState.searchResults` becomes `List<MergedSearchResult>`; `MapCanvasScreen` and existing tests touch the type. Contained to the search path; no other consumers of `searchResults` exist.

## Migration Plan

Additive change. Rollback: revert merger wiring in `MapCanvasViewModel`/`SearchScreen` and the dialog/mapper rendering; `searchResults` type reverts. No data or native migration.

## Verification

- **Unit**: `FavoriteSearchMergerTest` (core) — match/case-insensitivity, ordering, heart marking of native results, dedup identical vs both-when-different, short-query gate. Phone: ViewModel test asserting merged results in state; Compose test for heart rendering + top placement. AA: `SearchScreenMapperTest` extension for heart rows.
- **On-device**: phone — search a favorite name, verify top placement, heart, dedup, history recording on selection. AA emulator/head unit — same on `SearchTemplate`, verify host renders the heart icon.
- **Build**: `:core:test`, `:app:test`, `:auto:test`; `assembleMobileDebug` + `assembleAutomotiveDebug` compile gates.

## Open Questions

None — all decisions resolved with the user during exploration (marking scope, dedup rule, AA parity, query gate, history recording).
