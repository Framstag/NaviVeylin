# The standalone search forms — the decision 5.1 records

`design.md` D11 originally expected the transitional forms to be *removed* once the app owned its requests. During
apply that premise turned out to be false: two production callers legitimately own no request, so the forms are
kept as the **documented standalone path**. This file is the record; the wording of task 5.1 was amended to match.

## The grep that decides it

```
grep -rn 'searchLocations(\|searchLocationByForm(' app/src/main auto/src/main core/src/main --include=*.kt
```

Its non-generation hits (the line-based grep also lists three multi-line generation-carrying calls, whose
argument lists continue on the following lines: `AutoServiceModule.kt:98`, `RoutePanelViewModel.kt:394`,
`auto/SearchScreen.kt:216`):

| hit | what it is | why it owns no request |
|---|---|---|
| `AddressBookResolver.kt:137` | `searchLocationByForm(adminRegion, postalArea, location, address, FORM_LIMIT)` | the address book's contact fallback: resolved once per contact-picking pass, never superseded by typing |
| `AddressBookResolver.kt:145` | `searchLocations(query, RESULT_LIMIT, NO_ADMIN_REGION)` | same fallback, second attempt |
| `auto/NavigationSession.kt:661` | `autoSearchProvider().searchLocations(query, MAX_GEOCODE_RESULTS, null)` | the car's geocode lookup for a deep link: one call per destination, not a typable field |
| `MapCanvasViewModel.kt:2146` | the standalone `searchLocations(query)` helper | used by the focused suites and one-off lookups; it begins and ends its own request |

Each standalone *definition* carries a KDoc saying it owns no request and points at the generation-carrying
overload: the bridge override's `searchLocations(query, limit, adminRegionHandle)` and
`searchLocationByForm(…, 5 args)`, the submodule's `searchLocations(query, limit[, defaultRegion])` and
`searchPOIs(...)`, the provider's `searchLocations(query, limit, reference)`, and
`StructuredAddressSearch.resolve(query, client)`. Every surface whose search *is* superseded by typing passes a
generation instead: phone places (`SearchSlots.PLACES`), route panel (`ROUTE`), car search (`CAR`), phone POI
sheet (`POI`).

## Tallies (focused suites, 2026-10-15)

| suite | tally |
|---|---|
| `:core` `SearchGateTest` | `tests="8" failures="0" errors="0"` |
| `:app` `MapCanvasViewModelSearchCancellationTest` | `tests="6" failures="0" errors="0"` |
| `:app` `MapCanvasViewModelPoiSearchCancellationTest` | `tests="3" failures="0" errors="0"` |
| `:app` `MapCanvasViewModelFreeTextSearchTest` | `tests="6" failures="0" errors="0"` |
| `:app` `RoutePanelViewModelSearchCancellationTest` | `tests="3" failures="0" errors="0"` |
| `:app` `RoutePanelViewModelSearchHistoryTest` | `tests="3" failures="0" errors="0"` |
| `:app` `RoutePanelViewModelSearchRankingTest` | `tests="5" failures="0" errors="0"` |
| `:app` `FakeOSMScoutClientSearchRequestTest` | `tests="4" failures="0" errors="0"` |
| `:app` `AddressBookResolverTest` (the standalone form's caller) | `tests="27" failures="0" errors="0"` |
| `:auto` `SearchScreenTest` | `tests="15" failures="0" errors="0"` |

Native build: `ninja -C build osmscout_client_java` links clean; host policy test
`SearchBreakerTest` is `34 assertions in 9 test cases`.

## Left for a later change, deliberately

If the address book's contact filter or the car's geocode lookup ever becomes *superseded by user input*, give it
a slot of its own then; today neither is, and adding a slot for an unhurried lookup would be scope this change
never claimed.
