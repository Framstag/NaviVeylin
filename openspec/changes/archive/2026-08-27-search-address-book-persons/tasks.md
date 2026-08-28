## 1. Shared foundation (`:core` + manifest)

- [x] 1.1 Declare `READ_CONTACTS` in `app/src/main/AndroidManifest.xml` and verify the manifest merges cleanly (`./gradlew :app:processMobileDebugMainManifest`)
- [x] 1.2 Add `ContactPostalAddress` and `ContactAddressBookEntry` models in `:core` (address components + contact name) and verify a unit test constructs/normalizes blank components (spec: `address-book-search`)
- [x] 1.3 Add `AddressBookContactsProvider` fun interface in `:core` (mirrors `AutoSearchProvider` pattern) and verify it compiles in both `:app` and `:auto` (spec: `address-book-search` — list of persons with addresses)
- [x] 1.4 Add `AddressBookSearchProvider` fun interface in `:core` (`resolveAddress(address): List<LocationEntry>`) and verify it compiles in both modules (spec: `address-book-search` — address resolution)

## 2. App-side providers (`:app` Hilt)

- [x] 2.1 Implement `AddressBookContactsProvider` via `ContactsContract.CommonDataKinds.StructuredPostal` (contacts with ≥1 non-blank postal address, display name) and verify a Robolectric unit test with a shadow contacts cursor returns only contacts with addresses (spec: `address-book-search`)
- [x] 2.2 Implement `AddressBookSearchProvider`: query from address components → `searchLocations` (structured) → free-text fallback → ranking, and verify a unit test with `FakeOSMScoutClient` covers found / not-found / fallback cases (spec: `address-book-search`)
- [x] 2.3 Register both providers in a Hilt module and verify `:app:assembleMobileDebug` compiles (specs: both)

## 3. Permission flow + visibility (phone)

- [x] 3.1 Add rationale-once flag (`address_book_rationale_shown`) to app `SharedPreferences` with a small store class and verify a unit test covers set/read (spec: `address-book-permission` — decision remembered)
- [x] 3.2 Show the first-start rationale dialog in `MapCanvasScreen` (why access is needed, optional, deniable; "Continue" → request, "Not now" → no request; flag set in both cases) and verify `:app:assembleMobileDebug` compiles (spec: `address-book-permission`)
- [x] 3.3 Wire `RequestPermission()` launcher for `READ_CONTACTS` after rationale acknowledgment and verify a Robolectric test: no request before acknowledgment, request after (spec: `address-book-permission`)
- [x] 3.4 Add `addressBookAvailable` to `MapCanvasUiState`; update from permission launcher result, first composition, and `ON_RESUME`; verify a ViewModel test covers grant → available, deny → hidden, later grant on resume → available (spec: `address-book-permission` — visibility)
- [x] 3.5 Add "Address book" entry to `MapMenu` shown only when `addressBookAvailable` and verify a Compose UI test: entry present when granted, absent when denied (spec: `address-book-search` — phone entry; `address-book-permission`)

## 4. Address-book search sheet (phone)

- [x] 4.1 Add `AddressBookViewModel` (Hilt): contact list state, name filter (case-insensitive), empty state; verify unit tests for filter + no-contacts-with-addresses (spec: `address-book-search`)
- [x] 4.2 Add `AddressBookSheet` full-screen composable (searchable person list, follows `FavoritesSheet` pattern) and verify `:app:assembleMobileDebug` compiles (spec: `address-book-search`)
- [x] 4.3 Implement selection flow: 1 address → resolve; N addresses → address pick step before resolution; verify unit tests for both branches (spec: `address-book-search` — multi-address requirement)
- [x] 4.4 Hand off resolution result (`LocationEntry`) to `MapCanvasViewModel` (`selectedLocation` + `showDetailsSheet`), close the sheet, and verify a ViewModel test: successful resolution opens details, failed resolution shows "not found" message without crash (spec: `address-book-search` — resolution + details)

## 5. Android Auto

- [x] 5.1 Add `AddressBookScreen` in `:auto` (`ListTemplate` of contacts with addresses + search input callback with debounce) and verify `:auto` unit tests cover list rendering + empty state (spec: `address-book-search` — AA entry)
- [x] 5.2 Implement selection flow: multi-address pick via second `ListTemplate`, resolve via `AddressBookSearchProvider`, push existing `DetailsScreen`; verify `:auto` unit tests cover single/multi-address and not-found (spec: `address-book-search`)
- [x] 5.3 Add permission-gated "Address book" entry to `RootScreen` (check `READ_CONTACTS` on `carContext` when building the root list) and verify `RootScreenTest` covers entry present/absent (spec: `address-book-search` — AA entry; `address-book-permission` — visibility)

## 6. Integration verification

- [x] 6.1 Run `./gradlew test` — all new unit tests pass, existing tests still pass (specs: all)
- [x] 6.2 Build `./gradlew :app:assembleMobileDebug` and `./gradlew :app:assembleAutomotiveDebug` without errors (rules: build verification)
- [x] 6.3 Manual smoke check on device/emulator: first start shows rationale once, deny → entry hidden, grant in Settings → entry appears without re-ask, search + select person resolves to details sheet (specs: both capabilities)
