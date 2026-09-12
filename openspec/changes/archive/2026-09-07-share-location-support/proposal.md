## Why

Other apps can share geo information (a raw coordinate or an address) with NaviVeylin today — the manifest declares the intent filters (`geo:`, Google Maps URLs, `text/plain` share) and `DeepLinkActivity` forwards the intent — but the phone app drops it: `MainActivity` never reads the forwarded intent. The car-side flow works (`NavigationSession.handleDeepLink` parses and navigates), the phone side is a dead end. Sharing a location to NaviVeylin on the phone does nothing visible.

## What Changes

- `MainActivity` consumes the forwarded deep-link/share intent (`onCreate` + `onNewIntent`, `launchMode="singleTask"`): parse → route to the map.
- New `SharedLocationHandler` (Hilt, activity-scoped `StateFlow<SharedLocationRequest?>`) carries the parsed request from `MainActivity` to `MapCanvasViewModel`; it queues until the map screen is up (no maps installed → `MainScreen` shown first).
- `MapCanvasViewModel` processes a shared coordinate through the existing long-press candidate pipeline: `getDescriptionCandidates(lat, lon, 16)` (fixed high zoom, not the current viewport) → `CandidatePickerSheet` ("What's here?") → `onCandidateSelected` → `LocationDetailsSheet` (Show / Fav / Route). No candidates → details sheet opens directly on the raw coordinate (label = `EXTRA_SUBJECT` or `"lat, lon"`). Map centers on the shared coordinate.
- Shared text without coordinates triggers the existing search flow (address search via libosmscout), same as the car-side geocode path.
- `DeepLinkParser` (in `:auto`, shared with the car flow) is extended: OSM URLs (`mlat`/`mlon`, `#map=zoom/lat/lon`), Apple/Waze/Here URLs (`ll=`), DMS and hemisphere coordinate formats, `maps.app.goo.gl` short-link resolution (HTTP redirect; on failure the raw text falls through to search).
- Additive behavior fix — no manifest filter changes (filters already exist), no native/JNI changes, no new dependencies. Rollback: revert the `MainActivity`/`SharedLocationHandler` wiring and the parser extension; the car-side flow and the current (dead) phone behavior return.

## Capabilities

### New Capabilities

- `share/location-receiving`: the phone app receives shared geo information (coordinate or address) from other apps, disambiguates a raw coordinate through the candidate picker, and offers Show / Fav / Route on the result.

### Modified Capabilities

- `auto-deep-links`: "Deep link parsed into a destination" gains the extended formats (OSM, Apple/Waze, DMS, short-link resolution); "Deep-link activity forwards to phone app" changes from forwarding-and-dropping to forwarding-and-consuming (the phone surface now shows the candidate/details flow instead of ignoring the intent).

## Impact

- `app/src/main/AndroidManifest.xml` — `MainActivity` gains `launchMode="singleTask"` (intent filters unchanged; `DeepLinkActivity` stays the exported entry point).
- `app/src/main/java/com/naviveylin/MainActivity.kt` — `onNewIntent` + `onCreate` intent handling: parse via `DeepLinkParser`, write to `SharedLocationHandler`.
- `app/src/main/java/com/naviveylin/share/SharedLocationHandler.kt` — **new**: Hilt activity-scoped `StateFlow<SharedLocationRequest?>`.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — collect the handler; extract the candidate-flow body from `onLongPress` into a shared method; fixed zoom 16; `updateCenter`; no-candidates → details on raw coordinate.
- `auto/src/main/java/com/naviveylin/auto/DeepLinkParser.kt` — extended formats (OSM, Apple/Waze, DMS, hemisphere, `goo.gl` resolution hook).
- Tests: `SharedLocationParserTest` (new formats + garbage), `SharedLocationHandlerTest`, `MapCanvasViewModel` share-flow tests, existing `DeepLinkParserTest` extended.
- Guidelines: `guidelines/UI.md` does not document deep links or the share entry — no guideline change.
- Scope: phone (`:app` + shared parser in `:auto`). Android Auto / AAOS unaffected — the car-side deep-link flow (`NavigationSession.handleDeepLink`) is unchanged and continues to auto-navigate; the phone-side flow is additive on the same entry point.
