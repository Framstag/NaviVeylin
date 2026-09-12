# Design: address-book resolution error recovery

## Context

See `proposal.md` — Why. Current state that shapes the approach:

- `AddressBookViewModel` (app module) drives the phone Contacts mode; `resolutionError`
  latches on a failed resolve and is cleared nowhere (except by a *new* resolve attempt,
  which the UI blocks because the error branch replaces the list).
- The sheet state machine (`AddressBookSheet.kt`) is a `when` chain where
  `state.resolutionError` outranks the contact-list branches.
- The AA screen (`AddressBookScreen.kt` in `:auto`) already resets its `notFound` flag
  in `onSearchTextChanged` — the phone side is the parity outlier.
- Resolution logic itself (`AddressBookResolver`) is not implicated; it already returns
  an empty list on failure, which is the correct input to the UI problem.

## Goals / Non-Goals

**Goals:**
- A failed resolution never leaves the phone Contacts mode unusable (list unreachable,
  error un-clearable).
- Typing in the search field always restores the filtered list — parity with AA.
- The "not found" message still informs the user (spec `address-book-search` —
  "Address not found" SHALL inform without crash or dialog).
- Recovery paths covered by unit tests so the latch cannot regress.

**Non-Goals:**
- No change to resolution quality (`AddressBookResolver` ranking/fallback).
- No change to the AA screen (already correct; only spec parity text).
- No new UI surface — reuse the existing sheet and its inline-message style.

## Decisions

### D1: Failed resolution keeps the list visible; error is a transient inline banner

The `resolutionError` branch becomes an *overlay* hint, not a replacement state: when the
last resolution failed, the sheet renders the list (or current filter/empty state) with
the "could not resolve" message shown above it; any subsequent interaction clears it.
Concretely: the `when` chain renders a small banner (existing string
`R.string.address_book_not_found`) when `resolutionError` is true, *and* continues to the
normal list/empty branches below it.

- **Alternative considered** (status quo): full-screen error text with no reset path.
  Rejected — this is the reported bug.
- **Alternative considered** (option C, retry/back button on the error view): explicit
  affordance, but duplicates the recovery path the query edit already provides, adds a
  permanent UI element, and still hides the list behind the state until pressed.
- Rationale for choosing inline-over-replace: the user's existing mental model — "type,
  list filters" — is also the recovery; nothing is blocked, so no extra state to
  coordinate. Matches Material guidance for transient errors (persistent errors get
  persistent UI; this one resolves the moment the user acts).

### D2: Reset `resolutionError` at every recovery entry point (option A)

Clear the latch in:
- `onQueryChanged` — typing always restores the list (spec scenario "Editing the query
  clears the not-found state").
- `start()`/`loadContacts()` — re-entering Contacts mode (mode switch, dialog reopen)
  starts clean (spec scenario "Reopening the search clears the not-found state"). Note
  the existing `LaunchedEffect(state.searchMode)` in `MapCanvasScreen` re-fires on mode
  switch, so this point is actually reached in practice; it just never cleared the latch
  until now.
- `consumeResolved()` — symmetry: the success path's cleanup should leave no stale error
  either.
- `onPersonSelected` — selecting a person is a recovery interaction; the
  multi-address picker opens clean (design D1: "any subsequent interaction
  clears it"). Single-address selection routes through `resolve()`, which
  already clears at entry.

- **Alternative considered**: clear only at the start of the next `resolve()`. Rejected —
  the error branch blocks the list, so the next `resolve()` cannot be triggered from the
  UI; that is exactly the dead end that made the latch sticky. Recovery must live at
  entry points reachable from the error state (typing, reload), not behind it.
- **Alternative considered**: keep `resolutionError` but add an `acknowledgeError()`
  call. Rejected — an extra VM method and UI button for something the query edit already
  covers (D1 + D2 cover the same path more cheaply).

### Threading and lifecycle

No model changes. State is produced on the main thread via `MutableStateFlow` copies
(existing pattern); `resolve()` keeps its `viewModelScope.launch` +
`withContext(defaultDispatcher)` shape (search on background, state write on main).
`onQueryChanged`/`loadContacts` are pure state updates on the main thread — already the
case, unchanged. VM stays activity-scoped (`hiltViewModel()` in `MapCanvasScreen`), which
is fine now that recovery does not depend on VM recreation; `loadContacts` re-fetches
contacts on demand (no caching — unchanged).

## Risks / Trade-offs

- **Banner + list both visible could double-render on small screens** → banner is
  dismissible-by-action (any query edit), single-line Material text style, positioned
  above the list; no layout machinery added.
- **Spec parity drift** → spec scenarios are implementation-neutral ("not-found
  indication SHALL be cleared on query edit"); AA already satisfies them, phone will
  after this change.
- **Over-clearing** (error reset even when a contact is mid-pick) → harmless: the
  multi-address picker state (`selectedContact`) is orthogonal and untouched; the banner
  only reflects the *last resolution attempt*.

## Migration Plan

Pure bugfix; no data, schema, or API changes. Rollback: revert the view-model and sheet
edits — behavior reverts to the latched state. No versioning impact. After rollback the
spec delta must be reverted/archived together with the code (spec describes post-fix
behavior).

## Open Questions

None. Both recovery points and the presentation decision are confirmed (options A + B).
