## What Changes

Users want to navigate to people from their device address book. Today the app can only search the offline OSM database (location search, POI search, free-text search) — there is no way to pick a contact and resolve their postal address to a map location.

This change adds an address-book person search:

- **Contacts permission flow**: on first use, a rationale dialog explains why the app wants to read the address book, states that access is optional and can be denied, and only afterwards triggers the actual Android runtime permission request (`READ_CONTACTS`). The user's decision is remembered so the dialog is not shown again on later starts.
- **Permission-driven visibility**: the address-book menu entry and all its functionality are only available while `READ_CONTACTS` is granted. If the user denies, the entry is hidden. If the user later grants access (e.g. via system settings), the entry and functionality become available again silently — no rationale dialog, no re-request, no restart needed.
- **Menu entry (phone + Android Auto)**: a new "Address book" entry in the map menu (phone) and in the car screen root list (AA) opens the person search.
- **Searchable person list**: a searchable list of contacts that have a postal address. Typing filters by name; selecting a person is only possible when they have at least one address.
- **Address resolution + details**: selecting a person resolves their address via the existing location search backend and shows the resulting OSM object in the existing details view (centered map, structured description, favorite actions).

## Capabilities

### New Capabilities
- `address-book-permission`: Runtime `READ_CONTACTS` permission flow with a pre-request rationale dialog shown on first use. The dialog explains why access is requested, states that it is optional and can be denied, and only then triggers the system permission request. The user's decision (granted/denied) is remembered so the rationale is not shown again on subsequent starts; a later denial does not crash or degrade the rest of the app. The permission state is the single source of truth for feature availability: while granted, the address-book menu entry and functionality are available; while denied, they are hidden. If the user grants access later (e.g. via system settings), the entry and functionality become available again silently — without a rationale dialog, re-request, or app restart.
- `address-book-search`: Address-book person search reachable from a new menu entry on both the phone map screen and the Android Auto car screen. The entry and all search functionality are only shown while `READ_CONTACTS` is granted and disappear when it is denied; they reappear silently when the permission is granted again. Opens a searchable list of contacts that have at least one postal address; typing filters by person name. Selecting a person resolves their address through the existing location search backend and shows the resulting OSM object in the existing details view.

### Modified Capabilities
- *(none — no existing NaviVeylin capability has spec-level behavior changes)*

## Impact

| Component | Impact |
|-----------|--------|
| `app/src/main/AndroidManifest.xml` | **Modify** — declare `READ_CONTACTS` permission |
| `app/src/main/java/.../ui/map/MapCanvasScreen.kt` | **Modify** — add "Address book" menu entry; wire rationale dialog + permission request; open person search sheet |
| `app/src/main/java/.../ui/map/MapCanvasViewModel.kt` | **Modify** — add address-book state (permission status, person list, selected person), contact query + address resolution calls; re-check permission on resume so later grants/revocations update menu visibility |
| `app/src/main/java/.../ui/addressbook/` (new) | **Create** — person search sheet composable + ViewModel |
| `app/src/main/java/.../data/` (new) | **Create** — contacts repository wrapping `ContactsContract` queries; persisted permission-decision store (SharedPreferences/DataStore) |
| `auto/src/main/java/.../RootScreen.kt` | **Modify** — add "Address book" entry to car screen root list |
| `auto/src/main/java/.../AddressBookScreen.kt` (new) | **Create** — AA `ListTemplate`/`SearchTemplate` person list; selection hands off to existing details/navigation pipeline |
| `app/src/main/cpp/libosmscout/libosmscout-client-java/.../OSMScoutClient.java` | **No change expected** — address resolution reuses existing `searchLocations()`/location search backend |
| `openspec/specs/address-book-permission/spec.md` | **Create** — permission flow spec |
| `openspec/specs/address-book-search/spec.md` | **Create** — search + details spec |
