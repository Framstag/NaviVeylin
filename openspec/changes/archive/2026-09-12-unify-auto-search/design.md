# Design: unify-auto-search

## Context

See proposal.md — Why. The phone search is unified (Places / POIs / Contacts modes + suggestions); the Android Auto `SearchTemplate` shows nothing on an empty query. This change makes the empty query carry mode rows (POI search, contacts) and recent searches, with typing taking over for places search. Specs: `auto-search` (empty-query scenario), `auto-search-suggestions` (new), `address-book-search` (AA entry also from search template).

Current state: `SearchScreen` (auto module) builds a `SearchTemplate` with `setShowKeyboardByDefault(true)`, debounced search-as-you-type via `SearchCallbackImpl`, and an item list only when `lastQuery` is non-blank or results exist. `SearchHistoryScreen` already loads history from `autoSearchHistoryProvider` and pushes `SearchScreen(initialQuery = ...)` on tap. `RootScreen` gates the address-book row on `READ_CONTACTS`.

## Goals / Non-Goals

**Goals**
- Empty query on the AA `SearchTemplate` shows mode rows + recent searches.
- Contacts row gated on `READ_CONTACTS` (parity with phone Contacts mode).
- No-results state keeps mode rows so the driver can pivot without clearing the field.
- Suggestion building lives in `SearchScreenMapper` (testable, existing pattern).

**Non-Goals**
- Folding root-screen entries (Search / POIs / Favorites / Address book) into search — root stays as-is (additive change).
- Favorites or current-location suggestion rows — follow-up.
- Phone search dialog changes.

## Decisions

### D1: Suggestions live in `SearchScreen.onGetTemplate()`, not a separate screen

The `SearchTemplate` is the search surface; suggestions are just an item list rendered on empty query. No new screen, no navigation change.

- **Alternative**: a separate hub `ListTemplate` screen (L1) — rejected: it is a menu, not a unified surface; the user chose the single-surface approach.

### D2: History tap pushes a new `SearchScreen` with `initialQuery` — not inline search

The `SearchTemplate` API has no way to set the field text after construction (`setInitialSearchText` only applies at build time; the host owns the field afterwards). Running the search inline would show results for a query that is not in the field — confusing. Pushing `SearchScreen(initialQuery = query)` builds a fresh template with the field prefilled and the search running — the same mechanism `SearchHistoryScreen` already uses.

- **Alternative**: inline `runSearch(query)` on the current screen — rejected: field text cannot be updated post-construction, leaving field and results out of sync.

### D3: Contacts row visibility gated on `READ_CONTACTS`

Same `ContextCompat.checkSelfPermission` check as `RootScreen.hasAddressBookPermission()`. Hidden row, not an error-on-tap — parity with the phone's Contacts mode gate.

- **Alternative**: always show the row, show an error on tap — rejected: inconsistent with the phone and the root list, and the permission is static per session.

### D4: History loaded async, mode rows shown first

Load history from `autoSearchHistoryProvider` on `Dispatchers.Default` in `init` (same pattern as `SearchHistoryScreen`). Until loaded, the template shows only the mode rows; history rows are appended on load + `invalidate()`. No blocking, no loading row needed for the mode rows themselves.

### D5: Suggestion building in `SearchScreenMapper`

`SearchScreenMapper` gains functions to build the suggestion rows (mode rows, history rows) and the no-results rows. Keeps `SearchScreen` thin and the logic unit-testable — `SearchScreenMapperTest` already exists.

### D6: Labels — new action-phrase strings, parity deviation documented

New strings: "Search POIs near me", "Search contacts", "Recent searches" (English + German). These are action phrases, not the phone's mode labels ("Places" / "POIs" / "Contacts") — the AA root list already deviates ("Points of interest" vs "POIs"), and action phrases are the established AA pattern (Google Maps). Deviation is documented in `guidelines/UI.md`; `cross-variant-ui-parity` is unaffected because the elements are new, not shared.

### D7: Keyboard stays shown by default

`setShowKeyboardByDefault(true)` is kept: typing is the primary action, and the suggestion rows are scrollable below the field. Verified on host (see R1).

## Risks / Trade-offs

- **[R1] Host may not render the item list on an empty query** — the key platform risk. The API allows `setItemList` with an empty query and Google Maps shows recents this way, but emulated hosts / AAOS may differ.
  → **Mitigation**: verify on emulator/head unit as the first implementation task (spike). If a host refuses, fall back to showing mode rows only in the no-results state (spec change would be required — flagged early, before the rest of the work).
- **[R2] Keyboard may obscure suggestion rows** with `setShowKeyboardByDefault(true)`.
  → **Mitigation**: rows are scrollable; verify on host. If unusable, flip to `setShowKeyboardByDefault(false)` (behavioral spec unaffected — suggestions still shown).
- **[R3] History tap pushes a screen, growing the stack**.
  → **Mitigation**: acceptable — `SearchHistoryScreen` already does this; back returns to the search surface with prior state intact.

## Migration Plan

Additive UI change, no data or native migration. Rollback: revert `SearchScreen` / `SearchScreenMapper` / strings — the template returns to its current empty-query behavior.

## Open Questions

None blocking. Host rendering behavior (R1) is verified by a spike task before the main implementation; if it fails, the spec delta for `auto-search` is revisited with the user.
