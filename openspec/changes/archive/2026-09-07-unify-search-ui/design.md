## Context

See proposal.md — Why. Current phone search is four surfaces (`SearchPanel`, `PoiSearchPanel`, `SearchHistorySheet`, `AddressBookSheet`) with three entry points. All search state already lives in `MapCanvasViewModel` (`searchQuery`/`searchResults`, `poiCategory`/`poiRadiusMeters`/`poiResults`, `openSearchPanel`/`poiSearchOpen` flags); address book has its own `AddressBookViewModel`. Compose BOM 2024.12.01 → material3 1.3.1, which ships the M3 search family (`SearchBar`, `DockedSearchBar`, `FullScreenSearchBar`, all `@ExperimentalMaterial3Api`). No BOM bump (decision).

## Goals / Non-Goals

**Goals:**
- One full-screen M3 search dialog; one search button, one menu entry, `/` key — all funnel to it
- Mode switch (Places / POIs / Contacts) with per-mode state preserved for the session
- History + favorites + current location as suggestion sources in Places mode
- Reuse existing search flows, state, and the address-book ViewModel — no native/API changes

**Non-Goals:**
- No Android Auto changes (Auto keeps its own `SearchTemplate` for contacts)
- No BOM/material3 upgrade
- No changes to result-selection behavior (details sheet, route picking, markers, viewport restore)
- No changes to history recording, cap, or persistence

## Decisions

### D1: M3 `SearchBar` (expanded) as the dialog container

Use `SearchBar` with `expanded` driven by a state flag: the input field renders at the top, and when expanded the component shows the full-screen search view (scrim + content) with the mode switch, suggestions, and results. The search button / menu entry / `/` key set `expanded = true`; back or the back affordance sets it false.

- **Alternative A (chosen)**: `SearchBar` expanded — canonical M3 search component, gives the full-screen search view, keeps the input anchored at top, handles IME insets via `windowInsets`. Also leaves the door open for a future docked search bar entry without a component swap.
- **Alternative B**: `FullScreenSearchBar` — always full-screen, no docked state. Rejected: we never show a docked bar, and `SearchBar` expanded provides the identical full-screen view with one less API to learn.
- **Alternative C**: custom `Dialog`/`ModalBottomSheet` with a text field — rejected: not the M3 search pattern, and the sheet approach is exactly what this change removes.

### D2: `SegmentedButton` for mode switch

Three mutually exclusive modes → M3 `SegmentedButton` (single-select) with icon + label segments (Places / POIs / Contacts).

- **Alternative A (chosen)**: `SegmentedButton` — the M3-recommended control for mutually exclusive options; compact, one row.
- **Alternative B**: `FilterChip` row — more flexible for many options, but reads as multi-select; rejected for exactly-three modes.
- **Alternative C**: `TabRow` — top-level navigation semantics, heavier chrome; rejected for an in-dialog mode picker.

Contacts segment is omitted from the row when `READ_CONTACTS` is not granted (same gate as today's menu entry).

### D3: Extend `MapCanvasViewModel`, don't add a `SearchViewModel`

Add `searchMode: SearchMode` (PLACES/POIS/CONTACTS) and a single `searchOpen: Boolean` flag to `MapCanvasUiState`, replacing `openSearchPanel`/`poiSearchOpen` sheet flags. All per-mode state (`searchQuery`, `searchResults`, `poiCategory`, `poiRadiusMeters`, `poiResults`, `poiSearchCenter*`, `poiSelected*`) stays in the same state object, so mode switches preserve it for free. `AddressBookViewModel` remains the owner of contacts state and is embedded as the Contacts mode content.

- **Alternative A (chosen)**: extend `MapCanvasViewModel` — follows Design.md §3 (one ViewModel per concern; state co-locates with data; no cross-VM flows for related state). Search state already lives there.
- **Alternative B**: new `SearchViewModel` — rejected: would split one concern across two VMs and need cross-VM flow plumbing for zero benefit.

### D4: Embed address book as Contacts mode

Render the existing `AddressBookSheet` content (list + filter field, driven by `AddressBookViewModel`) inside the dialog's Contacts mode instead of as a separate full-screen sheet. The permission gate moves from menu-entry visibility to Contacts-segment visibility.

- **Alternative A (chosen)**: embed — one dialog, one surface; reuses the existing VM and search provider unchanged.
- **Alternative B**: keep the full-screen sheet, open it from the dialog — rejected: user decision is one dialog; nested full-screen surfaces defeat the unification.

### D5: POI category input as searchable dropdown

Replace the `FilterChip` row with the searchable `ExposedDropdownMenuBox` category picker (the control the POI sheet used before this change): an editable field that filters the category list by typing and shows the selected category. The explicit search button stays (decision). Radius slider unchanged.

- **Alternative A (chosen)**: searchable dropdown — compact (one row), scales to any category count, built-in filtering; proven in this codebase (the pre-change POI sheet).
- **Alternative B**: `FilterChip` row — rejected: 14 categories in a horizontal scroll hide options and offer no filtering affordance; the shared search field had to double as the filter.
- **Alternative C**: icon grid (`LazyVerticalGrid`) — rejected: best discoverability but costs 3-4 rows of vertical space in a dialog that also holds the radius slider, search button, and results with embedded map.

### D6: History as `SuggestionChip` row

Recent searches render as a horizontally scrollable `SuggestionChip` row (youngest first) in the Places-mode suggestions section, replacing the "Select from history" entry + `SearchHistorySheet`. Tapping a chip fills the search box (existing "fills search box" behavior).

- **Alternative A (chosen)**: chips — M3 suggestion pattern, compact, one tap to re-run.
- **Alternative B**: list rows — rejected: chips are the M3 search-suggestion idiom and take less vertical space.

## Risks / Trade-offs

- **`SearchBar` is experimental API** → already used across the app (`@ExperimentalMaterial3Api` on `ModalBottomSheet`); no new risk class.
- **POIs mode is dense** (category field + slider + button + results + minimap) on small screens → the results region takes the remaining weight; minimap keeps its existing portrait/landscape split (`PoiResultsWithMap` unchanged).
- **IME insets in full-screen dialog** → `SearchBar` `windowInsets` + existing `navigationBarsPadding`/`imePadding` conventions; verify on device.
- **Test churn** → `SearchPanel`/`PoiSearchPanel` Compose tests rework to the dialog; new tests for mode switch, suggestions, POI explicit-button flow.
- **Mode-state retention across dialog close** → per-mode state lives in `MapCanvasUiState`, so it survives close/reopen within the session; cleared on ViewModel recreation (same as today).

## Migration Plan

Additive UI change — no API, data, or native changes. Implementation order: state (`searchMode`/`searchOpen`) → dialog shell + mode switch → Places mode (move `SearchPanel` content) → POIs mode (move `PoiSearchPanel` content) → Contacts mode (embed address book) → entry points (button, menu, `/`) → remove old sheets → tests. Rollback: revert the change; old sheets and state flags return, history/favorites data untouched.

## Open Questions

None — all decisions resolved (contacts folded in, POI explicit button, material3 1.3.1).
