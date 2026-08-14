# Design: Android Auto Phase 4 — Deep Linking & Cross-Device Continuity

## Overview

Three flows, one shared `NavigationState`:

1. **Deep link (phone → car):** `DeepLinkActivity` (app) catches NAVIGATION-category intents. Android Auto delivers the same intent to `NavigationSession` (`onCreateScreen` on fresh session, `onNewIntent` when active). `DeepLinkParser` extracts coords or a query; the session calls `NavigationViewModel.navigateTo()`, geocoding address queries via `AutoSearchProvider`.
2. **Car-only route calc:** `NavigationViewModel.navigateTo()` falls back to a direct `OSMScoutClient.calculateRouteWithProfile()` call when the phone `RoutePanelViewModel` is not wired, so navigation works with the phone UI closed.
3. **State sync:** session picks the initial screen from `state.value.isNavigating`; the existing state collector keeps car screen in lockstep with phone start/stop.

## Components

### `app/src/main/java/com/naviveylin/DeepLinkActivity.kt` (new)
- Exported activity, translucent theme, `excludeFromRecents`.
- `onCreate`: forward original intent (action/data/EXTRA_TEXT/EXTRA_QUERY) to `MainActivity`, then `finish()`.
- Manifest intent filters (in `app/src/main/AndroidManifest.xml`):
  - `VIEW` + `androidx.car.app.category.NAVIGATION` + `geo` scheme
  - `VIEW` + `androidx.car.app.category.NAVIGATION` + `https/http` + hosts `maps.google.com`, `www.google.com`
  - `SEND` + `androidx.car.app.category.NAVIGATION` + `text/plain`

### `auto/src/main/java/com/naviveylin/auto/DeepLinkParser.kt` (new)
Pure Kotlin, no Android dependencies in the core parse functions:

```kotlin
data class DeepLinkDestination(val lat: Double?, val lon: Double?, val query: String?)
object DeepLinkParser {
    fun parse(intent: Intent): DeepLinkDestination?   // delegates to parseUri + extras
    fun parseUri(uriString: String?): DeepLinkDestination?  // pure, unit-tested
    fun parseCoordinates(text: String): Pair<Double, Double>? // "48.8566, 2.3522"
}
```

Formats:
- `geo:lat,lon` → coords; `geo:0,0?q=lat,lon(label)` → coords + label; `geo:0,0?q=query` → query
- `https://maps.google.com/?q=lat,lon` / `?q=query` / `?daddr=lat,lon` / `?destination=lat,lon` (also `http`, `www.google.com/maps`, `maps.app.goo.gl` — any host with `q`/`daddr`/`destination` param)
- `google.navigation:q=...` scheme
- `Intent.EXTRA_TEXT` → coordinate pair if parseable, else query
- `Intent.EXTRA_QUERY` → query

### `auto/.../NavigationSession.kt` (modified)
- `onCreateScreen(intent)`: screen = `NavigationScreen` if `state.value.isNavigating` else `RootScreen`; then `handleDeepLink(intent)`; start observing.
- `onNewIntent(intent)`: `handleDeepLink(intent)` (works when already navigating → re-route).
- `handleDeepLink`: parse → coords: `navigationViewModel.navigateTo(lat, lon)`; query: `entryPoint.autoSearchProvider().searchLocations(query, 1)` on `Dispatchers.Default` → first hit → `navigateTo`, none → `navigationViewModel` error message ("No matching location found for …").
- `onDestroy()`: `scope.cancel()`, `stopObserving()`.
- `getNavigationScreen()` helper caches one `NavigationScreen` per session.

### `app/.../navigation/NavigationViewModel.kt` (modified)
- Constructor gains `LocationService` + `@ApplicationContext Context`; `init` starts location updates (permission-guarded no-op) so GPS is available without the phone UI.
- `navigateTo()`: resolve start position = `state.value.position` else `locationService.location.value`; when `routePanelViewModel == null`, call new `startDirectRoute(startLat, startLon, destLat, destLon)`:
  ```kotlin
  viewModelScope.launch(Dispatchers.Default) {
      val profile = RoutingProfile(Vehicle.CAR)
      client.calculateRouteWithProfile(startLat, startLon, destLat, destLon, profile, object : RouteCallback {
          onSuccess → launch(Main) { startNavigation(route, Vehicle.CAR, forceFollowMode = false) }
          onError → launch(Main) { _state.value = _state.value.copy(errorMessage = ...) }
          onCancel/onProgress → no-op
      })
  }
  ```
- When `routePanelViewModel != null`, keep existing UI path unchanged.

## Tests

| Test | Module | Covers |
|------|--------|--------|
| `DeepLinkParserTest` | :auto | geo URIs (coords / query / labeled), Google Maps URL q/daddr/destination, `google.navigation:`, EXTRA_TEXT coords + address, EXTRA_QUERY, malformed inputs, null |
| `NavigationViewModelDirectRouteTest` | :app | fallback path: no RoutePanelViewModel → direct route calc → `isNavigating == true`; no GPS → error message. Uses extended `FakeOSMScoutClient` overriding `calculateRouteWithProfile` (invokes `onSuccess` with a fake `RouteEntry`) + `startNavigationWithVehicle` (returns null) |

## Risks

- `Session.onNewIntent` delivery requires host support; fallback = `onCreateScreen` intent (new session) — both covered.
- Route calc callback threads: `calculateRouteWithProfile` may invoke callbacks on a binder thread — all state mutation re-dispatched to `viewModelScope.launch(Dispatchers.Main)`.
- `RouteEntry` fake needs a non-zero `routeHandle`; `startNavigation` with handle 0 is a no-op.
