# Tasks: unify-auto-search

Specs: `auto-search-suggestions` (new), `auto-search` (empty-query scenario), `address-book-search` (AA entry from search template). Design: see design.md — D2 (history tap pushes `SearchScreen(initialQuery=...)`), D5 (suggestion building in `SearchScreenMapper`), R1 (host rendering spike first).

## 1. Spike: host rendering of empty-query item list

- [x] 1.1 Verify on emulator/head unit that the car host renders a `SearchTemplate` item list when the query is empty (temporarily set an item list on empty query in `SearchScreen`), and that suggestion rows are not obscured by the keyboard; record the result in the change notes — if the host refuses to render, stop and revisit the `auto-search` spec delta with the user before continuing

## 2. Strings

- [x] 2.1 Add "Search POIs near me", "Search contacts", "Recent searches" to `auto/src/main/res/values/strings.xml` and `values-de/strings.xml` and verify both files contain all three keys (German completeness check)

## 3. Suggestion building in SearchScreenMapper

- [x] 3.1 Add suggestion-row builders to `SearchScreenMapper` (mode rows, history rows, no-results rows) and verify new unit tests in `SearchScreenMapperTest` cover: mode row labels, contacts-row permission gating, history row content, no-results rows appended after the "No results found" row

## 4. SearchScreen wiring

- [x] 4.1 Wire empty-query suggestions into `SearchScreen.onGetTemplate()`: mode rows always, contacts row only with `READ_CONTACTS` (same check as `RootScreen`), history rows loaded async from `autoSearchHistoryProvider` (mode rows shown until loaded), and verify the template shows suggestions on empty query and places results once the user types (spec: auto-search-suggestions — mode rows, recent searches, typing replaces suggestions)
- [x] 4.2 Wire history-row tap to push `SearchScreen(initialQuery = query)` (design D2 — same mechanism as `SearchHistoryScreen`) and verify the pushed template shows the prefilled query with results
- [x] 4.3 Wire no-results state: append mode rows below the "No results found" row when a query returns no results, and verify the template shows both (spec: auto-search-suggestions — no-results state keeps mode rows)

## 5. Tests

- [x] 5.1 Add/extend `SearchScreen` template tests (Robolectric): empty query shows mode rows + history, contacts row hidden without `READ_CONTACTS`, typing replaces suggestions, clearing restores them, no-results shows mode rows, history tap pushes a screen with the query — and verify the full `:auto:test` suite passes
- [x] 5.2 Verify German translation completeness for the new strings (existing GermanRenderingTest pattern) and that no new string is missing from `values-de`

## 6. Guidelines

- [x] 6.1 Update `guidelines/UI.md` search-surface rules for the Auto variant: empty-query suggestions, action-phrase labels ("Search POIs near me" vs phone "POIs" mode label — documented parity deviation), and verify the doc reflects the new behavior

## 7. Build and on-device verification

- [x] 7.1 Build `:auto` (and the app) and verify compilation succeeds without warnings
- [x] 7.2 Verify on emulator/head unit: empty query shows mode rows + history, contacts row appears only with `READ_CONTACTS`, typing searches places, no-results keeps mode rows, history tap prefills and searches, back from POI/contacts search restores the previous query and results (screen-stack state preservation)
