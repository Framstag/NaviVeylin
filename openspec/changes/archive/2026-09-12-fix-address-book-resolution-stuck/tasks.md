# Tasks: fix-address-book-resolution-stuck

## 1. ViewModel recovery paths (spec: `address-book-search` — resolution-error recovery)

- [x] 1.1 Clear `resolutionError` in `onQueryChanged` so typing after a failed resolution restores the filtered list (design D2; spec "Editing the query clears the not-found state")
- [x] 1.2 Clear `resolutionError` in `start()`/`loadContacts()` so re-entering Contacts mode starts clean (design D2; spec "Reopening the search clears the not-found state" — `LaunchedEffect(searchMode)` in `MapCanvasScreen` re-fires on mode switch)
- [x] 1.3 Clear `resolutionError` in `consumeResolved()` for state symmetry (design D2)

## 2. Sheet presentation: inline banner instead of list replacement (spec: `address-book-search` — "Address not found", "Contact list stays reachable after failure")

- [x] 2.1 Rework `AddressBookSearchContent` `when` chain: when `resolutionError` is true render the existing `R.string.address_book_not_found` message as a non-blocking banner and continue to the normal list/empty/address-picker branches instead of replacing them (design D1)
- [x] 2.2 Verify the multi-address picker (`selectedContact`) and resolving states are untouched by the banner (no interaction with `selectedContact` or `isResolving`)

## 3. Unit tests for recovery (spec: `address-book-search` scenarios; same class as `unresolved address shows error`)

- [x] 3.1 Test: after a failed resolution, `onQueryChanged("B")` clears `resolutionError` and `filteredContacts` returns to the query-filtered list (`AddressBookViewModelTest`)
- [x] 3.2 Test: after a failed resolution, `start()`/`loadContacts()` clears `resolutionError` and shows contacts again
- [x] 3.3 Test: after a failed resolution, `consumeResolved()` clears `resolutionError`
- [x] 3.4 Test: error state does not hide `selectedContact` (multi-address pick survives across a failed sibling resolution)

## 4. Build and regression verification

- [x] 4.1 Build the mobile debug APK — `build-app` skill (`:app:assembleMobileDebug`); verify compile without errors and no new warnings in `AddressBookViewModel`/`AddressBookSheet`
- [x] 4.2 Run the unit-test suite — `run-tests` skill; verify `AddressBookViewModelTest` (incl. new 3.x tests) and all existing tests pass
- [x] 4.3 On-device verification (phone/emulator, Contacts mode): pick a contact whose address does not resolve, verify the not-found banner appears but the contact list remains visible; type in the search field and verify the banner clears and the list filters; leave and re-enter Contacts mode and verify no stale error
