# Design: Add search history

## Context

See proposal.md — Why. Current state: two search surfaces exist — the map search panel (`SearchPanel.kt` + `MapCanvasViewModel`) where selecting a result centers the map and opens the details sheet, and the route panel (`RoutePanel.kt` + `RoutePanelViewModel`) where selecting a result sets the start/destination field. Both surfaces already funnel selection through a single callback per ViewModel. Persistence patterns in the app: `SettingsStorage` writes a kotlinx.serialization JSON file; `FavoriteRepository` wraps JNI with a `StateFlow` facade. No Room database is currently used in the app module.

## Goals / Non-Goals

**Goals:**
- Record one history entry per committed search selection (display or routing), never per keystroke.
- Persist history as a JSON file, capped at 50 entries, oldest evicted.
- Expose history reactively (`StateFlow`) so the search panel and history view update without manual refresh.
- Keep the history view simple: a scrollable list, youngest first, tap-to-fill.

**Non-Goals:**
- No deduplication of repeated searches (each selection records its own entry; duplicates are allowed).
- No per-entry delete or clear-history UI (not requested).
- No history integration in Android Auto (deferred).
- No Room migration — history uses the same JSON-file approach as `SettingsStorage`.

## Decisions

### D1: JSON file storage via kotlinx.serialization (not Room, not SharedPreferences)

`SearchHistoryRepository` mirrors `SettingsStorage`: a `@Serializable` `SearchHistoryEntry(text, timestamp)` and a `@Serializable` `SearchHistoryData(entries)` persisted to a JSON file in the app's files dir. Rationale: consistent with the app's existing persistence, zero new dependencies, trivially testable. Alternatives considered: Room (heavier than needed for a capped list; no Room infra exists yet), SharedPreferences (string-encoded JSON is awkward, no atomic list semantics). Load on first access, save on every mutation, both on `Dispatchers.Default`.

### D2: Repository with StateFlow, injected via Hilt

`SearchHistoryRepository` is `@Singleton`, exposes `val history: StateFlow<List<SearchHistoryEntry>>` (youngest first), and offers `suspend fun record(text: String)` plus `fun entries(): List<SearchHistoryEntry>`. Hilt binding in `AppModule` (constructor-injected with `@ApplicationContext`). Rationale: matches `FavoriteRepository`/`SettingsStorage` DI style; ViewModels observe the flow.

### D3: Cap enforcement inside the repository

`record()` appends the new entry and, if size > 50, drops the oldest (first element of the oldest-first internal list). Rationale: single enforcement point, unit-testable without UI. The exposed flow is already youngest-first, so the UI never re-sorts.

### D4: Snapshot points are the existing selection callbacks

- `MapCanvasViewModel.onSearchResultSelected(entry)` — display selection (map search panel).
- `RoutePanelViewModel`'s result-selection handler — routing selection (start/destination).

Both call `historyRepository.record(queryText)` with the query text that produced the selected result. Rationale: these are exactly the "selection for routing or display" points the user specified; no new plumbing needed. The query text is read from the ViewModel's current search-query state at selection time (the text shown in the box when the result was picked).

### D5: History view as a modal bottom sheet over the search panel

"Select from history" opens a `ModalBottomSheet` (consistent with the app's sheet usage) containing a `LazyColumn` of entries, youngest first. Tapping an entry invokes `onHistoryEntrySelected(text)`, which closes the sheet and calls `onQueryChanged(text)` so the search box takes over the string. Rationale: bottom sheet matches the app's existing overlay language and keeps the search panel context visible. Alternatives considered: full-screen dialog (heavier, hides search context), inline expansion inside the search panel (complicates the stable-height sheet).

### D6: History entry recorded when selecting from history is NOT re-recorded

Selecting a history entry only fills the search box; the entry is re-recorded only if the user then selects a result again. Rationale: matches the "selection for routing or display" snapshot rule; avoids timestamp churn on mere recall.

## Risks / Trade-offs

- [JSON file corruption on crash mid-write] → Write atomically (write temp file, rename); same pattern as `SettingsStorage`.
- [History grows unbounded if cap logic regresses] → Cap enforced in one place (`record()`), covered by unit tests.
- [Duplicate entries clutter history] → Accepted trade-off (Non-Goal); user can still find recent searches at top.
- [Route panel selection text may differ from map panel text] → Both record whatever query text was active at selection; spec only requires the search text that produced the result.

## Migration Plan

No migration: new feature, no existing data. First run starts with empty history. Rollback: remove the repository call sites; the JSON file is inert if unused.

## Open Questions

None.
