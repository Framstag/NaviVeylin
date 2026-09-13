# Fix address lookup accuracy

## Why

Offline address lookup fails in multiple cases: either an address is not found at all, or it resolves to a location in the same city but several kilometers away. Example: "Erbstollenstraße 10, 58454 Witten" returns nothing when the postal code is part of the typed query (`SearchForLocationByString` requires every token to be consumed by the region → location → address chain, and the unconsumed PLZ token drops the whole result set), and city-only fallbacks surface free-text bus-stop hits ("Witten-Bommern Bf" ~6 km, "Wittenbrinkshof" ~27 km away) instead of the requested street/house.

## What Changes

- Set `partialMatch = true` in the JNI search calls so a failed full chain returns the best available street/region candidate instead of an empty result, and demote free-text entries below structured matches for address-like queries.
- Add a shared free-text address parser (`AddressParser`) that extracts street, house number, postal code, and city; the phone search dialog, the Android Auto search provider, and the contact resolver use it to run a structured form search ahead of the raw string query.
- Fix `AddressBookResolver` house-number parsing (postal code appended to street), extend its fallback chain with "street + house + PLZ" and "street + PLZ" variants, and gate the city-only fallback so free-text noise can never be auto-selected as the resolved location.
- Adjust ranking: structured admin-region results score at least as high as POI hits; free-text entries without street-token evidence cannot win contact resolution.

Additive — no breaking changes, no user-facing API/menu changes. Rollback: revert the submodule gitlink bump and the app-side Kotlin changes; existing behavior is restored.

## Capabilities

### Modified Capabilities

- `location-search` — Places search resolves full formatted addresses (street + house number + postal code + city in one query).
- `address-book-search` — address resolution becomes accurate for house numbers absent from the index and for postal-code-appended street fields; city-only fallbacks no longer select far-away free-text results.
- `auto-search` — the car search box resolves full formatted addresses the same way as the phone.

## Impact

- **Native (submodule patch, upstreamable)**: `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — `DoSearchLocations` and `DoSearchLocationByForm`: enable partial matches so surplus tokens (postal codes) no longer zero out results; prioritize structured over free-text entries. Submodule commit on `naviveylin-local`, then bump the gitlink in the main repo.
- **App module**: new `data/AddressParser.kt`; `MapCanvasViewModel.mergeSearchResults` (phone search dialog), `di/AutoServiceModule.provideAutoSearchProvider` (car search), `data/AddressBookResolver` (contacts).
- **Tests**: `AddressParserTest`, `AddressBookResolverTest` extensions, `MapCanvasViewModel` search-scope tests, ranking tests; native regression test in the submodule for the postal-code-in-query case.
- **Guidelines**: no guideline document changes required (search behavior documented in specs, not `guidelines/`).
- **Specs**: requirement deltas in `location-search`, `address-book-search`, `auto-search`.
- **Scope**: phone and Android Auto both (shared native chain + shared provider); parity required.
