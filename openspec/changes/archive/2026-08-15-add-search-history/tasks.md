# Tasks: Add search history

## 1. Data Layer

- [x] 1.1 Create `SearchHistoryEntry` (`@Serializable`, fields: `text: String`, `timestamp: Long`) and `SearchHistoryData` (`@Serializable`, field: `entries: List<SearchHistoryEntry>`) in `app/src/main/java/com/naviveylin/data/SearchHistoryRepository.kt` (spec: History entry content)
- [x] 1.2 Implement `SearchHistoryRepository` (`@Singleton`, `@Inject` with `@ApplicationContext`): load JSON file on first access, expose `StateFlow<List<SearchHistoryEntry>>` youngest-first, `suspend fun record(text: String)` appends entry with current timestamp and evicts oldest when size exceeds 50, atomic write (temp file + rename) on `Dispatchers.Default` (spec: History capped at 50 entries, History persists across restarts)
- [x] 1.3 Register `SearchHistoryRepository` in `app/src/main/java/com/naviveylin/di/AppModule.kt` (constructor injection)

## 2. ViewModel Integration

- [x] 2.1 Inject `SearchHistoryRepository` into `MapCanvasViewModel`; call `record(searchQuery)` inside `onSearchResultSelected` before/after the existing selection handling (spec: History entry recorded on result selection — display)
- [x] 2.2 Inject `SearchHistoryRepository` into `RoutePanelViewModel`; call `record(queryText)` in the result-selection handler for start/destination (spec: History entry recorded on result selection — routing)
- [x] 2.3 Expose history state from `MapCanvasViewModel` (collect repository `StateFlow` into `MapCanvasUiState` or pass flow through) and add `onHistoryEntrySelected(text: String)` that sets the search query via `onSearchQueryChanged` (spec: History selection fills search box)

## 3. UI

- [x] 3.1 Add "Select from history" convenience entry to `SearchPanel.kt`, shown when query is empty alongside "Current Location" and "Select Favorite", hidden while typing, restored on clear; add `onSelectFromHistory: () -> Unit` callback (spec: Select from history entry on empty search box)
- [x] 3.2 Create history view composable (e.g. `SearchHistorySheet.kt` in `ui/map/`): `ModalBottomSheet` with `LazyColumn` of entries youngest first, scrollable, each row shows search text + date; tap invokes `onEntrySelected(text)` (spec: History view lists entries youngest first)
- [x] 3.3 Wire in `MapCanvasScreen.kt`: `showSearchHistory` state; "Select from history" opens the sheet; entry selection closes sheet and calls `viewModel.onHistoryEntrySelected(text)` (spec: History selection fills search box)

## 4. Tests

- [x] 4.1 Unit tests for `SearchHistoryRepository`: record appends with timestamp, cap at 50 evicts oldest, persistence round-trip (save → reload), youngest-first ordering (spec: History entry content, History capped at 50 entries, History persists across restarts)
- [x] 4.2 ViewModel test: `onSearchResultSelected` records an entry; typing alone records nothing (spec: History entry recorded on result selection)
- [x] 4.3 ViewModel test: `onHistoryEntrySelected` fills the search query (spec: History selection fills search box)

## 5. Verification

- [x] 5.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — build compiles without errors
- [x] 5.2 Run `./gradlew test` — existing tests still pass
