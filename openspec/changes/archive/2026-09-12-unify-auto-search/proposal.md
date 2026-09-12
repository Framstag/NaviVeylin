## Why

The phone search was unified into a single dialog (Places / POIs / Contacts modes with suggestions); Android Auto was explicitly left untouched. On the car screen, search is still scattered: free-text search (`SearchTemplate`), POI search, address book, and search history are separate root entries, and history is only reachable from the map screen. The `SearchTemplate` shows nothing on an empty query — a dead surface that could carry the same mode branching and suggestions the phone dialog provides.

## What Changes

- **`SearchScreen` (AA) shows suggestions on an empty query** instead of an empty list:
  - **"Search POIs near me"** row → pushes `PoiSearchScreen` (existing category picker → results around GPS).
  - **"Search contacts"** row → pushes `AddressBookScreen`, shown only while `READ_CONTACTS` is granted (same gate as the phone's Contacts mode and the root list entry).
  - **Recent searches** — history rows loaded from the shared `autoSearchHistoryProvider` (same JSON store as the phone, so phone searches appear on the car screen). Tapping a history row runs the search inline on the current `SearchTemplate` (sets the query + debounced search) — no screen push needed.
- **Typing replaces suggestions**: as soon as the user types, the mode/history rows are replaced by places search results (existing behavior unchanged).
- **No-results state keeps mode branching**: when a query returns no results, the "No results found" row is followed by the mode rows, so the driver can pivot to POI/contacts search without clearing the field (mirrors the phone's always-visible mode switch).
- **State preservation via the screen stack**: `SearchScreen` keeps `lastQuery`/`lastResults` as instance fields; pushing `PoiSearchScreen`/`AddressBookScreen` keeps `SearchScreen` alive, so back-navigation restores the query and results — no explicit per-mode state needed.
- **Root screen unchanged**: the root list keeps its Search / POIs / Favorites / Address book entries (additive change, Google Maps AA pattern). Folding root entries into search is a possible follow-up, not part of this change.
- **Additive, non-breaking**: no API, data, or native changes; existing search-as-you-type behavior and result rows are untouched.

## Capabilities

### New Capabilities

- `auto-search-suggestions`: Empty-query suggestions on the Android Auto `SearchTemplate` — mode rows (POI search, contacts) and recent-search history, with typing taking over for places search.

### Modified Capabilities

- `auto-search`: The "Empty query shows no results" scenario is replaced — an empty query now shows mode rows and recent searches instead of an empty list.
- `address-book-search`: The Android Auto address book becomes reachable from the search template's suggestions in addition to the root list entry (permission gate unchanged).

## Impact

- `auto/src/main/java/com/naviveylin/auto/SearchScreen.kt` — empty-query suggestions list (mode rows + history), contacts row permission-gated, history tap runs search inline, no-results rows
- `auto/src/main/java/com/naviveylin/auto/SearchScreenMapper.kt` — suggestion-row building helpers (mode rows, history rows) kept testable
- `auto/src/main/res/values/strings.xml` + `values-de/strings.xml` — new strings ("Search POIs near me", "Search contacts", "Recent searches")
- Specs: `openspec/specs/auto-search/spec.md` (empty-query scenario replaced), `openspec/specs/address-book-search/spec.md` (AA entry also from search template)
- Tests: `SearchScreenMapperTest` extended for suggestion rows; new `SearchScreen` template tests (empty query → suggestions, permission-gated contacts row, history tap → search); German string completeness check
- Guidelines: `guidelines/UI.md` — search-surface rules for the Auto variant updated in the same change
- On-device verification: emulator/head unit — confirm the host renders the item list on an empty query (the key platform risk) and that the keyboard does not obscure the suggestion rows

**Scope**: Android Auto only. Phone search dialog untouched. `cross-variant-ui-parity` unaffected — suggestion rows reuse existing AA labels ("Points of interest", "Address book").

**Rollback**: additive UI change — revert `SearchScreen`/`SearchScreenMapper`/strings; no data or native impact.
