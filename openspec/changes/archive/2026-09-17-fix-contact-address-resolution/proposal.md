# Fix contact address resolution

## Why

Address resolution from the device address book is broken again for real contacts: user report of the red "address not found" banner for addresses that the normal search box resolves correctly. Root cause is an interaction introduced by `fix-address-lookup-accuracy`: that change set `partialMatch = true` in both native search entry points, so a search no longer returns an empty result set when the street/house is missing — it fabricates an admin-region or postal-area fallback entry instead. `AddressBookResolver` treats any non-empty result set as success (early return after the form search, and an abort of the progressive string chain after the first query), then its street-evidence gate deletes exactly those street-less fallback entries and returns an empty list. Net effect: the progressive fallback chain that made contact resolution work is now dead code, and every contact whose form search yields only a region/postal-area entry lands on the banner. The phone search box is unaffected because it merges raw results and shows them as a list without a gate.

Two adjacent defects surfaced by the same report:

- Contacts whose structured components are empty and only `FORMATTED_ADDRESS` is set (typical for a second synced address book) resolve to nothing: `ContactPostalAddress.queryText` is empty and `AddressBookResolver` never reads `formatted`, so every generated query is blank while the list still shows the address.
- Duplicate postal rows: an aggregated contact whose two accounts hold the same address yields two identical `ContactPostalAddress` entries (no dedup in `ContactsRepository`). That forces the multi-address pick step for a single real address, prints the address twice in the list row, and passes a duplicate key to the picker's `LazyColumn` (`key = { it.hashCode() }`), which throws `IllegalArgumentException: Key ... was already used`.

## What Changes

- **Resolution accepts only street-evidenced results.** A search result set that contains no entry with street-token evidence (region/postal-area `partialMatch` fallback) counts as a miss. Both the form search and every string query keep the chain going until a street-evidenced result exists or all candidates are exhausted.
- **Query candidates gain the combinations the search box uses but the resolver does not build.** Add the full formatted address query (`street house PLZ city`) and the parsed-`formatted` variants to the candidate chain, so the resolver sends the same query the (known-working) phone search box sends.
- **`formatted` becomes a first-class source.** When the structured street/city components are blank or partially blank, `AddressParser` parses `FORMATTED_ADDRESS` into street/house/PLZ/city and those components feed both the form search and the string chain.
- **Duplicate postal addresses are collapsed.** Identical addresses of one contact (same normalized components after trimming, case-insensitive) SHALL appear once. The address picker uses a stable, duplicate-safe item key.
- No native/JNI change: the fix stays in the Kotlin resolver and repository. Native `partialMatch` semantics are deliberately left alone — they are correct for the search box (spec `location-search`) and the resolver must simply stop misreading them.

Additive, no breaking changes, no user-facing menu or API changes. Rollback: revert the app-module Kotlin files in the Impact list; the previous (buggy) behavior returns, no data migration involved.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `address-book-search`: the "Address resolution search" requirement gains the retry/abort contract (a street-less result set is a miss; the chain runs to exhaustion; `FORMATTED_ADDRESS` is a valid component source; resolution never reports not-found while a street-evidenced candidate exists), and the multi-address requirement gains deduplication of identical addresses.

## Impact

- **App module (`:app`)** — all changes, no native build impact:
  - `app/src/main/java/com/naviveylin/data/AddressBookResolver.kt` — street-evidence-driven retry instead of "first non-empty result set wins"; extended candidate chain built from components and `formatted`; gate applied per candidate, not per result set.
  - `app/src/main/java/com/naviveylin/data/AddressParser.kt` — helper that merges structured components with parsed `formatted` text into the effective `(street, houseNo, plz, city)` tuple (keeps existing parsing rules; one place decides which source wins).
  - `app/src/main/java/com/naviveylin/data/ContactsRepository.kt` — collapse identical postal addresses per contact before building `ContactAddressBookEntry`.
  - `app/src/main/java/com/naviveylin/ui/addressbook/AddressBookSheet.kt` — duplicate-safe picker item key (index-stable key instead of `hashCode()`).
  - `app/src/main/java/com/naviveylin/ui/addressbook/AddressBookViewModel.kt` — no behavior change expected; touched only if the dedup belongs in the view model instead of the repository (design decision).
- **Tests**:
  - `app/src/test/java/com/naviveylin/data/AddressBookResolverTest.kt` — new cases: street-less form result does not abort the chain; street-less raw result does not abort the chain; formatted-only address resolves; components-empty address with formatted text builds non-blank queries; not-found only after the whole chain.
  - New `ContactsRepositoryTest` (Robolectric, `ContentProvider` or resolver-level) for the postal-address dedup.
  - New or extended `AddressBookViewModel` / Compose test for the picker with two identical addresses (no duplicate-key crash, one row).
- **Native / JNI**: untouched. No submodule patch, no gitlink bump. `OSMScoutClient.cpp` `SetPartialMatch(true)` in `DoSearchLocationByForm` / `DoSearchLocations` stays as committed in `a4c309421`.
- **Android Auto**: no code change. The car screen shares `AddressBookSearchProvider` / `AddressBookContactsProvider`, so the fix reaches it automatically — parity is a requirement of this change (same resolution outcome and same deduplicated address list on phone and car).
- **Scope**: general — the resolver and repository are shared by phone and Android Auto; no phone-only or car-only behavior.
- **Guidelines**: `guidelines/Design.md` (§ threading: resolution and contact reading stay off the main thread on `Dispatchers.Default`; no new dispatchers), `guidelines/UI.md` (multi-address picker presentation and list row content). `guidelines/MapRendering.md` unaffected.
- **Previous specs changed**: `openspec/specs/address-book-search/spec.md` (delta in this change). `location-search` and `auto-search` are NOT modified — their requirements still describe the search-box behavior that `fix-address-lookup-accuracy` established and that stays correct.
- **Relationship to open changes**: `fix-address-lookup-accuracy` is archived before this change (it is the older change and both modify `address-book-search`; this change's delta for `### Requirement: Address resolution search` is a superset of its 8 scenarios, so it must land first or the superseded requirement would overwrite this one). That change established the native `partialMatch=true` semantics and the search-box behaviour this change builds on; its remaining on-device check was delegated to tasks 5.1/5.5 here instead of being run separately.
