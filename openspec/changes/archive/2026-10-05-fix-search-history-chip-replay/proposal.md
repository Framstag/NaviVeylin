# Proposal

## Why

Tapping a recent-search chip in the phone search dialog can do nothing: the search box fills with the
chip's text but no search runs, so the user sees an empty results area. It reproduces for the query
that was most recently committed — search "Bochum", select a result, reopen the dialog, tap the
"Bochum" chip: nothing. The AA car surface is unaffected (it pushes a prefilled `SearchScreen`), so
the two surfaces disagree on the same input.

Cause: the query lives in two places in `MapCanvasViewModel` — `uiState.searchQuery` (what the UI
shows) and `_searchQueryFlow` (what triggers the search). `onSearchResultSelected` clears the first
and leaves the second at the committed text; the next chip tap then writes an *equal* value into the
`MutableStateFlow`, which conflates it away, so the debounced search pipeline never re-runs. Typing
works because every keystroke is a different value. This violates `guidelines/Design.md` §3 ("where
one value drives several layers, resolve it once in a single source and fan out — one truth, no
divergence"; "prefer a state field over a separate flow for derived signals").

The requirement that a chip *runs* the search was never written for the phone (the car spec has it:
`auto-search-suggestions` — "Tapping a recent-search row SHALL run the search for that query"), so no
test guarded it; the existing test asserts only that the search box gets the text.

Adjacent defect in the same flow: `SearchHistoryRepository.record()` appends without deduplication,
so searching the same text twice yields two identical chips.

## What Changes

- The search trigger becomes a single self-contained request (query text + monotonic sequence) owned
  by `MapCanvasViewModel`, so no writer can desync the visible query from the search that runs, and a
  request that repeats the previous query text still runs. `uiState.searchQuery` stays the displayed
  text.
- Tapping a recent-search chip in the phone dialog fills the search box **and** runs the search,
  producing the same results as typing the text (parity with Android Auto).
- `SearchHistoryRepository.record()` moves an existing entry for the same text to the front instead of
  appending a duplicate, and the history list is collapsed on load so already-persisted duplicates
  disappear without a data migration.
- Requirement deltas in `search-history` (chip selection replays the search; no duplicate entries for
  the same text) and in `location-search` (a repeated query text still runs and publishes results).

Not in scope: the route panel's own search job (already correct), the AA search path (already
correct), and the choice of trigger mechanism beyond this fix.

## Capabilities

### New Capabilities

None. The behaviour belongs to capabilities that already own it.

### Modified Capabilities

- `search-history`: "History selection fills search box" is replaced by a requirement that chip
  selection also RUNS the search for the entry's text, with the phone/AA parity statement; a new
  requirement states that re-searching an existing text moves that entry to the front instead of
  adding a duplicate.
- `location-search`: a new requirement states that a search request that repeats the previously
  requested query text SHALL still run and publish its results (the trigger contract the chip relies
  on; today the second request is silently dropped).

## Impact

Additive behaviour change; no wire format, database, or JNI contract changes.

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — search trigger type, search
  pipeline, `onSearchQueryChanged`, `onHistoryEntrySelected`, `onSearchResultSelected`, `clearSearch`.
- `app/src/main/java/com/naviveylin/data/SearchHistoryRepository.kt` — `record()` move-to-front,
  duplicate collapse on `load()`. Same JSON shape (`SearchHistoryData`/`SearchHistoryEntry`), so no
  migration; existing files keep duplicates on disk until the next load collapses them.
- `app/src/main/java/com/naviveylin/ui/map/SearchDialog.kt` — no change expected (the chip already
  calls `onHistoryEntrySelected(entry.text)`); touched only if the call site needs the explicit
  request.
- `auto/` — no change: `SearchScreen.kt` already pushes a prefilled screen, and the store stays the
  single source for both surfaces (`guidelines/Design.md` §8 cross-variant rules, §9 persistence).
- Tests: `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelSearchHistoryTest.kt` (chip replay
  after a committed selection, dedupe ordering), `MapCanvasViewModelFreeTextSearchTest.kt` (repeated
  identical query re-runs), `app/src/test/java/com/naviveylin/data/` history repository tests
  (move-to-front, load collapse).
- Guidelines: `guidelines/Design.md` §3 (one truth per value, state field over derived flow) and §9
  (search) — no guideline text change; the fix restores what §3 already requires.
- No native/submodule change: nothing in the JNI bridge is touched.

Rollback: revert the commit. Search behaviour returns to the previous state; the history JSON file
stays readable in both directions, and collapsed duplicates are only a loss of redundancy.
