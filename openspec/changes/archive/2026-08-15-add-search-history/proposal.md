# Proposal: Add search history

## Why

Users repeatedly search for the same locations (home, work, frequent POIs) but the app offers no way to recall a previous search. Every search must be typed from scratch. A search history lets users re-run past searches with one tap, reducing friction for repeat destinations.

## What Changes

- Record a history entry whenever a search result is **selected** — either for display (map search panel: center map + details sheet) or for routing (route panel: set start/destination). Typing alone never records an entry.
- Each history entry stores the search text and the selection date.
- History is capped at 50 entries; when full, the oldest entry is dropped.
- The search panel shows a third convenience entry, "Select from history", when the search box is empty (alongside the existing "Current Location" and "Select Favorite" entries).
- "Select from history" opens a scrollable history view listing entries youngest first. Selecting an entry closes the view and takes over the search string (fills the search box).
- History persists across app restarts.

## Capabilities

### New Capabilities

- `search-history`: storage of search selections (text + date, capped at 50, oldest dropped), the "Select from history" convenience entry on an empty search box, and the scrollable history view (youngest first) whose selection fills the search box.

### Modified Capabilities

None. The existing `location-search` "Convenience entries on empty query" requirement is extended by the new `search-history` capability's own requirement; no existing requirement changes.

## Impact

- **New file** `app/src/main/java/com/naviveylin/data/SearchHistoryRepository.kt` — persistence (JSON file via kotlinx.serialization, mirroring `SettingsStorage`), 50-entry cap, `StateFlow` exposure.
- `app/src/main/java/com/naviveylin/ui/map/SearchPanel.kt` — third convenience entry "Select from history" on empty query; history view composable (scrollable list, youngest first).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — record history entry in `onSearchResultSelected` (display selection); expose history state; handle history-entry selection (fill search box).
- `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` — record history entry when a route start/destination result is selected (routing selection).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — wire history view open/close state.
- `app/src/main/java/com/naviveylin/di/AppModule.kt` — Hilt binding for `SearchHistoryRepository`.
- Tests: unit tests for cap/eviction and persistence round-trip; ViewModel tests for record-on-selection and history-selection behavior.
