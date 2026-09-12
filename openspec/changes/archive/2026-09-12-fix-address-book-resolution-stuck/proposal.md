# Fix: address-book resolution error gets stuck

## Why

In phone Contacts mode, one failed address resolution latches `resolutionError = true`
and the sheet's state machine renders the "not found" message *instead of* the contact
list. Nothing clears the latch — not query edits, not re-entering the mode, not reopening
the dialog (the `AddressBookViewModel` is activity-scoped) — so the user is permanently
stuck on the error and the contact list never returns until process death. Because OSM
coverage of exact postal addresses is uneven, "not found" is a common outcome, so this
bricks the feature for many users on first failure.

## What Changes

- **Reset the error on query change** (option A): typing in the search field clears
  `resolutionError`, so the filtered contact list returns immediately — same behavior
  the AA `AddressBookScreen` already has (`notFound = false` in `onSearchTextChanged`).
- **Keep the list visible on failure** (option B): a failed resolution no longer replaces
  the contact list. The "not found" message becomes a transient inline banner above the
  still-interactive list instead of a full-screen state.
- `loadContacts()`/`start()` also clear `resolutionError` (re-entering Contacts mode
  recovers), and `consumeResolved()` clears it for state symmetry.
- New unit tests covering the recovery paths (query change after failure, reload after
  failure, error shown as non-blocking message with the list still visible).

Additive bugfix — no breaking changes, no public API changes. Rollback: revert the
change; behavior returns to the latched state (original bug).

## Capabilities

### Modified Capabilities

- **`address-book-search`** — requirement "Address resolution search" gains recovery
  scenarios: after a failed resolution the contact list SHALL remain reachable, and
  editing the query or reloading SHALL clear the not-found state. Phone UI behavior
  aligned to the Android Auto screen's existing behavior.

## Impact

- `app/src/main/java/com/naviveylin/ui/addressbook/AddressBookViewModel.kt` — reset
  `resolutionError` in `onQueryChanged`, `loadContacts`, `consumeResolved`.
- `app/src/main/java/com/naviveylin/ui/addressbook/AddressBookSheet.kt` — error rendered
  as an inline banner above the list (state precedence: list branches no longer blocked
  by `resolutionError`).
- `app/src/test/java/com/naviveylin/ui/addressbook/AddressBookViewModelTest.kt` — new
  recovery tests.
- No native/JNI changes, no changes to `AddressBookResolver` (resolution logic itself is
  fine), no `:core`/`:auto` module changes.
- Guidelines: UI.md — sheet state machine and transient-error conventions; no parity gap
  introduced (phone now matches AA).
