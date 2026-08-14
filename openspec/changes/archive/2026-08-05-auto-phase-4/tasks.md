# Tasks: Android Auto Phase 4

## Deep link parsing

- [x] 1.1 Create `DeepLinkParser` in `:auto` with `parse(Intent)`, `parseUri(String?)`, `parseCoordinates(String)` supporting geo URIs, Google Maps URL query params (`q`, `daddr`, `destination`), `google.navigation:`, `EXTRA_TEXT`, `EXTRA_QUERY`
- [x] 1.2 Create `DeepLinkParserTest` covering coords, queries, labeled coords, malformed and null inputs

## Deep link entry point (phone side)

- [x] 1.3 Create `DeepLinkActivity` in `:app` that forwards the intent to `MainActivity` and finishes
- [x] 1.4 Register `DeepLinkActivity` in `app/src/main/AndroidManifest.xml` with `androidx.car.app.category.NAVIGATION` intent filters: `geo` scheme, `https/http` maps hosts, `text/plain` SEND

## Session handling

- [x] 1.5 Modify `NavigationSession.onCreateScreen(intent)` to return `NavigationScreen` immediately when already navigating, else `RootScreen`; then handle deep link intent
- [x] 1.6 Override `NavigationSession.onNewIntent(intent)` to handle deep links while the session is active (re-route when navigating)
- [x] 1.7 Implement `handleDeepLink`: coordinates → `navigateTo`; query → geocode via `AutoSearchProvider`, navigate to first result, surface error when no match
- [x] 1.8 Override `NavigationSession.onDestroy()` to cancel the coroutine scope and stop observing (leak fix)

## Car-only route calculation

- [x] 1.9 Inject `LocationService` (+ `@ApplicationContext Context`) into app `NavigationViewModel`; start location updates in `init`
- [x] 1.10 Add `startDirectRoute()` fallback in `navigateTo()` used when `routePanelViewModel` is null: `calculateRouteWithProfile` with `RoutingProfile(Vehicle.CAR)`, start navigation on success, error message on failure/no GPS

## Tests & build

- [x] 1.11 Extend `FakeOSMScoutClient` with `calculateRouteWithProfile` (invoke `onSuccess` with fake `RouteEntry`) and `startNavigationWithVehicle` (return null)
- [x] 1.12 Create `NavigationViewModelDirectRouteTest` for fallback success + no-GPS error paths
- [x] 1.13 Run `./gradlew :auto:testDebugUnitTest :app:testDebugUnitTest` and fix failures
- [x] 1.14 Run `./gradlew :app:assembleDebug` to verify the build

## TODO.md

- [x] 1.15 Update `TODO.md`: mark Phase 4 complete, correct 4.1 description (deep links via NAVIGATION category — `CarAppExtras` class does not exist in androidx.car.app), note DHU testing still blocked
