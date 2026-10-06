# Design

## Context

See `proposal.md` — Why. The relevant state today:

- `MapCanvasViewModel` holds the query twice: `uiState.searchQuery` (displayed text) and
  `_searchQueryFlow` (`MapCanvasViewModel.kt:1013`, trigger for the debounced pipeline at
  `MapCanvasViewModel.kt:1079-1098`). Writers: `onSearchQueryChanged` (both), `clearSearch` (both),
  `onSearchResultSelected` (UI text only, `:2362`), `closeSearch` (neither).
- `MutableStateFlow` conflates equal values, and `_searchQueryFlow` holds the last committed query
  after a result selection, so the next identical write is dropped and the pipeline never re-runs.
- `SearchHistoryRepository.record()` (`SearchHistoryRepository.kt:96`) prepends unconditionally.
- `SearchDialog.kt:406` already calls `onHistoryEntrySelected(entry.text)`; the defect is below it.
- Route panel (`RoutePanelViewModel.kt:285-319`) runs its own `searchJob` per change — unaffected.
- Android Auto (`SearchScreen.kt:247`) pushes a prefilled `SearchScreen` — unaffected.
- Constraints: `guidelines/Design.md` §3 (one truth per value; state field over derived flow), §4
  (threading: native/CPU work off main), §9 (search resilience, per-surface matching).

## Goals / Non-Goals

Goals: one search trigger that cannot lose a request; the displayed query and the running search can
never disagree; the history store holds at most one entry per text, both for new and persisted data.

Non-Goals: the route panel's search job, the AA search screen, the search pipeline's ranking/merge
behaviour, and any JNI/native change.

## Decisions

### D1 — The trigger carries the request: `SearchRequest(query, seq)`

`private data class SearchRequest(val query: String, val seq: Long)`, held in a `MutableStateFlow`
whose value is replaced by every `onSearchQueryChanged` call with `seq + 1`. The pipeline
(`debounce(300).flatMapLatest { mergeSearchResults(it.query) }`) reads `it.query`.
`uiState.searchQuery` stays the displayed text; `onHistoryEntrySelected` delegates to
`onSearchQueryChanged`, so there is exactly one writer.

- Why: an event whose text repeats is still a *different* value (`seq`), so conflation cannot drop
  it; the request is self-contained, so no writer can set the text without the search following.
- Alternative A — one-line fix (`_searchQueryFlow.value = ""` in `onSearchResultSelected`): fixes the
  symptom, leaves the mirror pair that caused it; the next writer reintroduces it. Rejected as the
  primary fix.
- Alternative B — `combine(queryFlow, tick)` with a bump on replay (precedent: the `fixQualityTicks`
  counter): keeps two mirrors of the query, so it still permits the desync class. Rejected.
- Alternative C — `MutableSharedFlow<SearchRequest>` as a pure event channel: no replay means a
  request published while the collector is restarting is lost, and `debounce` on a non-replaying
  source makes the pipeline's liveness depend on subscription timing (fragile under the test
  virtual clock). Rejected for a pipeline that must be restartable.

### D2 — History dedupe at record time, collapse at load time

`record(text)`: remove any existing entry with the same text, then prepend the new entry (inside the
existing mutex, so it stays race-free with concurrent records); the cap still trims to 50.
`load()`: collapse repeated texts keeping the newest date, preserving the youngest-first order.

- Why: `record` alone leaves already-persisted duplicates visible until each text is searched again;
  the spec requires the chip row never to show a text twice, and existing installs already have
  duplicates on disk.
- Why both places: `record` keeps the invariant from now on; `load` heals what is already stored.
- Alternative — dedupe only in the UI (distinct-by-text when mapping to chips): hides duplicates from
  the phone chip row but not from the AA recent-search rows that read the same store, and leaves the
  file wrong. Rejected.
- Alternative — a file-format version field / migration: the JSON shape is unchanged, so a version
  field buys nothing. Rejected.
- Persistence: after a collapsing load, persist only if the collapse actually changed the list, so a
  start does not rewrite the file. Text comparison is exact (the entry's text is the query the user
  typed); no case folding — folding belongs to the search, not to the record.

### D3 — Test surface

Unit-testable seams already exist: `MapCanvasViewModel.defaultDispatcher` (test dispatcher),
`SearchHistoryRepository.defaultDispatcher` (test dispatcher), `MainDispatcherRule`. No new seam is
introduced. The chip replay test drives `onSearchQueryChanged` → `onSearchResultSelected` →
`onHistoryEntrySelected` and awaits the published results with `advanceTimeBy(300)`.

## Risks / Trade-offs

- [Trigger refactor touches a path shared with shared-location intake (`processSharedLocation`
  calls `onSearchQueryChanged`)] → the public method keeps its signature and semantics; the
  same-text-from-a-shared-location case gets a spec scenario and a test.
- [Two rapid requests for the same text debounce into one run (`debounce` + `flatMapLatest`)]
  → the outcome is identical (same query, same results), so this stays acceptable and is unchanged
  from today.
- [Collapsing on load changes the list the AA recent-search rows show] → intended: both surfaces read
  one store, and the store now holds one entry per text.
- [A collapsed load that is not persisted is recomputed on every start] → deterministic and cheap
  (≤50 entries); persisting on the next record removes it.
- [Diagnosability on device] → one coordinate-free log line per search request
  (`search request: chars=<n> seq=<n>`), numbers only, per the `auto-diagnostics` rule (no
  coordinates in logs).

## Verification

- Unit (focused): `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModel*"`.
- The chip-replay test MUST fail before the fix (it asserts published results, not just the text in
  the box) and pass after; a `revert-check` mutation (drop the `seq` bump, or the UI-text reset) must
  turn it red again, then restore and run the focused suite green.
- Repository tests: repeat search moves the entry to the front; a pre-seeded duplicate file collapses
  on load with the newest date; cap still 50.
- On device (no pixel measurement applies — the claim is "rows appear", not geometry): open the
  search dialog, tap the newest recent-search chip, observe the result rows and the
  `search request: chars=6 seq=<n>` line under the app tag in logcat.
- Full both-flavor gate once before the commit (`--rerun-tasks`).

## Migration Plan

No data migration and no file-format change: the change ships as one commit; a rollback is a revert,
after which the file (deduplicated or not) still loads under the old code. Collapsed duplicates are
redundancy only, so no user-visible loss.

## Open Questions

None blocking.
