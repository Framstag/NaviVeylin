## Why

Phone search is scattered across four surfaces with three entry points: a free-text location sheet (`SearchPanel`), a POI sheet (`PoiSearchPanel`), a full-screen address-book sheet (`AddressBookSheet`), and a separate history sheet (`SearchHistorySheet`). POI search and address book are buried in the map menu, location search lives on the search button, and each surface has different chrome. Users must know where each kind of search lives before they can find anything.

## What Changes

- **One unified search dialog** (Material 3 `SearchBar` full-screen pattern, material3 1.3.1 — no BOM bump) replaces `SearchPanel`, `PoiSearchPanel`, `SearchHistorySheet`, and the phone `AddressBookSheet`. The dialog has a single search field and a `SegmentedButton` mode switch:
  - **Places** (default): free-text location search (existing `searchQuery`/`searchResults` flow, admin-region scoping, debounce, disambiguation, distance). Empty query shows suggestions: recent searches (chips), favorites (rows), current location.
  - **POIs**: category `FilterChip` row + radius slider + explicit search button (kept per decision) + results with embedded minimap (existing `PoiResultsWithMap`).
  - **Contacts**: address-book person search, embedded as a mode; mode hidden unless `READ_CONTACTS` is granted (same gate as today).
- **One search button**: the existing magnifying-glass button in `MapActionColumn` opens the unified dialog (both orientations).
- **One menu entry**: `MapMenu` "Search POIs" entry becomes a single "Search" entry opening the same dialog; the "Address book" menu entry is removed (folded into the dialog's Contacts mode). Android Auto menu entries are **unchanged** — Auto keeps its own `SearchTemplate` for contacts.
- **History as suggestions**: the separate "Select from history" flow and `SearchHistorySheet` are replaced by recent-search chips shown in the dialog's suggestions; recording behavior (on result selection, 50-entry cap, persistence) is unchanged.
- **`/` keyboard shortcut** opens the unified dialog.
- **BREAKING (UI only)**: the bottom-sheet search surfaces are removed; `stable-search-sheet` requirements are superseded by the full-screen dialog (no sheet height to stabilize). No API, data, or native changes.

## Capabilities

### New Capabilities

- `search-dialog`: Unified Material 3 search dialog on the phone map screen — one entry point (search button, menu entry, `/` key), mode switch between Places / POIs / Contacts, and suggestion sources (history, favorites, current location).

### Modified Capabilities

- `location-search`: Search panel becomes the Places mode of the unified dialog; sheet → full-screen dialog; convenience entries become the suggestions section; region-scope name display moves into the dialog.
- `poi-search`: POI search becomes the POIs mode of the unified dialog; menu entry replaced by the unified "Search" entry; explicit search button and embedded minimap retained.
- `address-book-search`: Phone menu entry removed — address book becomes the Contacts mode of the unified dialog (permission gate unchanged). Android Auto entry unchanged.
- `map-menu`: "Search POIs" entry becomes "Search"; "Address book" entry removed.
- `map-canvas-screen`: Search button opens the unified dialog instead of the location sheet.
- `keyboard-shortcuts`: `/` key opens the unified dialog.
- `search-history`: "Select from history" entry and separate history view replaced by recent-search suggestions in the dialog; recording/cap/persistence unchanged.

### Removed Capabilities

- `stable-search-sheet`: Superseded — the unified dialog is full-screen, so fixed-sheet-height requirements no longer apply.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/SearchDialog.kt` (new) — unified dialog: `SearchBar` input, `SegmentedButton` mode switch, suggestions, per-mode content
- `app/src/main/java/com/naviveylin/ui/map/SearchPanel.kt` — merged into dialog (Places mode + suggestions)
- `app/src/main/java/com/naviveylin/ui/map/PoiSearchPanel.kt` — merged into dialog (POIs mode)
- `app/src/main/java/com/naviveylin/ui/map/SearchHistorySheet.kt` — removed
- `app/src/main/java/com/naviveylin/ui/addressbook/AddressBookSheet.kt` — embedded as Contacts mode (keeps its ViewModel)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — `MapMenu` entries, `MapActionColumn` search button, sheet flags → dialog flag
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `searchMode` state, `searchOpen` flag replacing `showSearchPanel`/`poiSearchOpen`; per-mode state retained
- `app/src/main/res/values/strings.xml` + `values-de/strings.xml` — new strings (mode labels, suggestions header)
- Tests: rework `SearchPanel`/`PoiSearchPanel` Compose tests to the dialog; new tests for mode switch, suggestions, POI explicit-button flow
- Guidelines: `guidelines/UI.md` — search surface rules updated in the same change

**Scope**: phone only. Android Auto search surfaces (contacts `SearchTemplate`) are untouched; `cross-variant-ui-parity` unaffected because Auto has no location/POI search to mirror.

**Rollback**: additive UI change — no API, data, or native changes. Revert restores the previous sheets; history/favorites data unaffected.
