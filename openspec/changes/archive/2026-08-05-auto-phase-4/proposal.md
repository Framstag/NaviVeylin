# Proposal: Android Auto Phase 4 — Deep Linking & Cross-Device Continuity

## Why

Phases 1–3 give the car screen navigation, search, favorites, and a rendered map — but only when the user drives the flow *from the car*. There is no way to hand a destination from the phone to the car, and the session does not robustly handle connection/disconnection mid-navigation. The TODO lists this as Phase 4: "Deep linking & cross-device continuity — seamless handoff between phone and car."

## What Changes

- **Deep links from phone → car navigation start.** Add a deep-link entry point (`DeepLinkActivity`) with `androidx.car.app.category.NAVIGATION` intent filters (`geo:`, Google Maps URLs, `text/plain` share). When the user opens such a link while Android Auto is connected, the templated app launches on the car and `NavigationSession` receives the intent via `onCreateScreen()`/`onNewIntent()`, parses it, and starts navigation to the destination.
- **Intent parsing.** New pure `DeepLinkParser` in `:auto` handling `geo:` URIs, Google Maps URL query formats (`q=`, `daddr=`, `destination=`), `EXTRA_TEXT`/`EXTRA_QUERY`. Coordinate destinations navigate directly; address queries are geocoded via `AutoSearchProvider.searchLocations()`.
- **Car-only route calculation fallback.** `NavigationViewModel.navigateTo()` currently fails with "Navigation not ready" when the phone UI is not open (`routePanelViewModel == null`). Add a direct JNI route-calculation path so navigation can start from the car session without the phone UI, using GPS from `LocationService`.
- **Mid-navigation connect sync.** `NavigationSession.onCreateScreen()` returns `NavigationScreen` immediately when already navigating (no root-screen flash). Deep link while navigating re-routes to the new destination.
- **Lifecycle hardening.** Cancel session coroutine scope on `onDestroy`; fix leaks.
- **TODO.md update.** Mark Phase 4 done, correct the 4.1 row (`CarAppExtras` class does not exist in androidx.car.app — the real mechanism is the NAVIGATION-category deep link).

## Capabilities

### New Capabilities
- `auto-deep-links`: Phone → car destination handoff via NAVIGATION-category deep links (`geo:`, maps URLs, text share), including intent parsing, geocoding of address queries, and navigation start from the car session.
- `auto-cross-device-sync`: Navigation state continuity across phone and car — car-only navigation start without phone UI, immediate `NavigationTemplate` when connecting mid-navigation, car display follows phone nav start/stop, deep-link re-route while navigating, session lifecycle cleanup.

### Modified Capabilities
- (none — no changes to existing spec-level behavior; the shared `NavigationState` contract is unchanged)

## Impact

### Files to create
| File | Purpose |
|------|---------|
| `app/src/main/java/com/naviveylin/DeepLinkActivity.kt` | Deep-link entry point, forwards intent to `MainActivity` |
| `auto/src/main/java/com/naviveylin/auto/DeepLinkParser.kt` | Pure parser: intent/URI → destination coords or query |
| `auto/src/test/java/com/naviveylin/auto/DeepLinkParserTest.kt` | Parser unit tests |
| `app/src/test/java/com/naviveylin/navigation/NavigationViewModelDirectRouteTest.kt` | Fallback route path tests |

### Files to modify
| File | Change |
|------|--------|
| `app/src/main/AndroidManifest.xml` | Register `DeepLinkActivity` with NAVIGATION-category intent filters |
| `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` | Handle deep links in `onCreateScreen`/`onNewIntent`, initial screen from current state, `onDestroy` cleanup |
| `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt` | Inject `LocationService`, start GPS on init, direct-route fallback in `navigateTo()` |
| `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` | Override `calculateRouteWithProfile`/`startNavigationWithVehicle` for fallback tests |
| `TODO.md` | Phase 4 status + corrected deep-link description |

### Dependencies
- `androidx.car.app:app:1.7.0` — already declared (Session.onNewIntent, category constant)
- No new dependencies; no native changes

### Risks
| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| `CarAppExtras` (TODO 4.1 text) does not exist in androidx.car.app | Certain | Use documented NAVIGATION-category deep link + `Session.onNewIntent`; update TODO |
| Car-only navigation has no GPS fix yet | Medium | Clear `errorMessage` on car screen ("GPS signal required"); existing error overlay shows it |
| Deep-link Activity opens on phone too (not just car) | Certain (by design) | Forwards to `MainActivity`; shared state shows navigation on both surfaces |
