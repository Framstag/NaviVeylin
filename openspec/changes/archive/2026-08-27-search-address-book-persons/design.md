## Context

See `proposal.md` for motivation. Current state that shapes the approach:

- `:core` module is shared by `:app` and `:auto` (which depends on `:core` + `:osmscout-client-java`, **not** on `:app`). Shared logic must live in `:core`, surfaced to `:auto` via provider interfaces implemented in `:app` with Hilt (existing pattern: `AutoSearchProvider`).
- Phone search already runs through `MapCanvasViewModel.searchLocations(query)` → `OSMScoutClient.searchLocations(query, 20, adminRegionHandle)` (structured search). Free-text search over the MARISA text index exists as a fallback (spec: `search-free-text`).
- Search results open the existing full-screen details view via `MapCanvasUiState.showDetailsSheet` + `selectedLocation` (spec: `enhanced-details-sheet`).
- Location permission precedent exists in `MapCanvasScreen`: `rememberLauncherForActivityResult(RequestPermission())` + rationale dialog. The contacts flow mirrors it, with a one-time rationale.
- Map menu (`MapMenu`) and car root list (`RootScreen` in `:auto`) are the two places menu entries are added.

## Goals / Non-Goals

**Goals:**
- Shared, permission-guarded contacts access usable from both `:app` and `:auto`.
- One-time first-start rationale dialog, then standard `READ_CONTACTS` request; decision remembered.
- Menu entries appear/disappear reactively with the permission state (checked on resume and on permission result — no polling).
- Address resolution reuses the existing offline search pipeline; resolved object opens the existing details view.

**Non-Goals:**
- No contacts sync, caching, or storage — contacts are read on demand and never persisted.
- No reverse geocoding of arbitrary coordinates to contacts.
- No new native/JNI work (`OSMScoutClient.searchLocations` and the free-text index are sufficient).
- No changes to how search results themselves open details (address-book objects flow through the same sheet).

## Decisions

### D1: Contacts access as a provider interface in `:core`, implemented in `:app`

Add `AddressBookContactsProvider` (fun interface, like `AutoSearchProvider`) in `:core`:

```kotlin
fun interface AddressBookContactsProvider {
    fun contactsWithAddresses(): List<ContactAddressBookEntry> // contact name + list of postal addresses
}
```

Implemented in `:app` (Hilt) via `ContactsContract.CommonDataKinds.StructuredPostal` joined with `ContactsContract.Contacts` display names; filters to contacts with ≥1 non-blank postal address. `:auto` consumes the provider for its `AddressBookScreen`.

- **Alternative considered**: `:auto` queries `ContactsContract` directly (it has a `Context`). Rejected: duplicates the query + permission logic in two modules and diverges from the existing `AutoSearchProvider` pattern.
- **Alternative considered**: new shared module just for contacts. Rejected: `:core` already exists and is the established sharing point; a new module adds build wiring for one small class.

### D2: Address resolution as a provider too (`AddressBookSearchProvider`), reusing the search pipeline

```kotlin
fun interface AddressBookSearchProvider {
    fun resolveAddress(address: ContactPostalAddress): List<LocationEntry>
}
```

Implementation in `:app` (Hilt) wraps the existing search pipeline: a structured form search first (`OSMScoutClient.searchLocationByForm` — new JNI method calling `LocationService::SearchForLocationByForm` with city/postal area/street/house number, returning house-level results with precise coordinates when the index has them; retried without the postal area if the postal code blocks), then a string-search fallback chain (`searchLocations(query, 50, handle)` with progressively looser queries: street+city, without house number, street alone, city alone). Candidates are ranked by: object type (house-level `address` > `place`/street > `poi`), native `matchQuality` (`match` > `candidate`), house-number token in the label, street/city token overlap, and postal-code match in the result's region/postal area; results outside the contact's city are penalized. The phone UI and the AA screen both consume this — one resolution strategy, no duplication.

- **Alternative considered**: each caller re-implements query building + fallback against `OSMScoutClient` directly. Rejected: resolution quality (query construction, fallback, ranking) would drift between phone and AA.
- **Alternative considered**: resolution inside `:core`. Rejected: `:core` has no `OSMScoutClient` access pattern for this and the search pipeline (admin-region handle, free-text index) lives behind the `:app`-side client usage; the provider boundary keeps `:core` free of JNI plumbing.

### D3: Rationale-once flag in SharedPreferences (`:app`)

Persist `address_book_rationale_shown` in the existing app `SharedPreferences` (no DataStore/Room — one boolean). Flow in `MapCanvasScreen`:

1. On first composition: if `READ_CONTACTS` granted → nothing (entry visible). If not granted and flag not set → show rationale dialog.
2. Rationale dialog: explains why, states optional/deniable; "Continue" → set flag, launch `RequestPermission()`; "Not now" → set flag, no request.
3. After that, the flag prevents the dialog forever; the system remembers the permission decision. A later grant in Settings is picked up by a permission re-check on resume.

- **Alternative considered**: re-check `shouldShowRequestPermissionRationale()` like the location flow. Rejected: that API only distinguishes "first ask" from "don't ask again" and would show the dialog again after a plain denial; the spec requires exactly-once rationale regardless of outcome.
- **Alternative considered**: trigger rationale only when the user opens the address-book entry. Rejected: the menu entry is hidden without permission (spec `address-book-permission`), so there would be no way to reach it; the user explicitly asked for a first-start dialog.

### D4: Permission state drives visibility via a resume check + permission callback

`MapCanvasViewModel` exposes `addressBookAvailable: Boolean` in `MapCanvasUiState`. It is updated from three places: permission-launcher result, `ON_RESUME` lifecycle event (user returning from Settings), and first composition. `MapMenu` and the AA `RootScreen` render the "Address book" entry only when `true`.

On AA: `RootScreen` checks `ContextCompat.checkSelfPermission(carContext, READ_CONTACTS)` when building its root list (the car app service runs in the host app's process, so the permission is shared). The `:auto` `AddressBookScreen` additionally guards its own queries.

- **Alternative considered**: an app-wide flow that pushes permission changes. Rejected: overkill — the resume check is where Settings-driven changes are guaranteed to surface (same approach as the existing location permission handling).

### D5: Phone UI as a full-screen sheet; selection flows into the existing details sheet

New `ui/addressbook/AddressBookSheet.kt` + `AddressBookViewModel` (Hilt), opened from the map menu (full-screen sheet pattern already used by `FavoritesSheet`). State machine in the sheet: contact list (search-filtered) → (contact with 1 address: resolve immediately; with N addresses: address pick list) → resolving → resolution result `LocationEntry` → hand off to `MapCanvasViewModel` (set `selectedLocation` + `showDetailsSheet = true`; close address-book sheet). No new details UI — the existing `enhanced-details-sheet` view renders the resolved object, including "Show on map", favorites, and navigation actions.

### D6: AA screen with `SearchTemplate` + address picker, hand-off to existing `DetailsScreen`

New `AddressBookScreen.kt` in `:auto`: a `SearchTemplate` (the car-app template that provides a search input — `ListTemplate` has none) listing contacts with addresses, filtered by name as the driver types with debounce (same approach as `SearchScreen`). Selection → single address resolves immediately; multiple addresses open a second `ListTemplate` (`AddressBookAddressPickerScreen`) to pick one → `AddressBookSearchProvider.resolveAddress` → push existing `DetailsScreen`. Entry added to `RootScreen` root list, permission-gated.

## Risks / Trade-offs

- **OSM coverage of exact postal addresses is uneven** → Many contacts will not resolve. Mitigation: free-text fallback + ranking; spec already requires a friendly "not found" message (no error dialog).
- **`READ_CONTACTS` is a Play-restricted permission** → Play Console review may require a declaration and could flag the app. Mitigation: permission is optional, feature fully gated behind it, rationale dialog states the purpose, contacts are never uploaded or stored (on-device only, read on demand).
- **Contacts quality varies (labels, blank fields, duplicates)** → Query construction normalizes fields and drops blank components; multi-address contacts get an explicit pick step.
- **Rationale-once flag lost on reinstall/clear-data** → Dialog reappears; acceptable and consistent with "per installation" in the spec.
- **AAOS head units may have empty or no contacts database** → Empty state is spec'd; entry still shows (permission granted) and the screen explains no contacts with addresses exist.
- **Free-text fallback depends on text index presence** → Without an index the structured fallback already defined by `search-free-text` applies; no new failure mode.

## Migration Plan

Feature addition — no data migration, no schema change, no existing behavior change. Rollback: revert the change; `READ_CONTACTS` stays in the merged manifest only as long as the code references it (strip the declaration on rollback). No versioning/state file impact.

## Open Questions

None blocking. (Whether AAOS units commonly carry address books is deferred — the empty state covers it.)
