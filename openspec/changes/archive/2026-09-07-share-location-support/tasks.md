## 1. Parser move and extension (auto-deep-links: "Deep link parsed into a destination")

- [x] 1.1 Move `DeepLinkParser` + `DeepLinkDestination` from `:auto` to `:core` (`com.naviveylin.core`), update `NavigationSession` imports, move `DeepLinkParserTest` to `:core` tests — verify `:core` and `:auto` test suites pass and the car-side deep-link flow compiles unchanged
- [x] 1.2 Extend parser with OSM URLs (`?mlat=&mlon=`, `#map=zoom/lat/lon`) — verify new `DeepLinkParserTest` scenarios pass (spec: OSM URL scenarios)
- [x] 1.3 Extend parser with Apple/Waze/Here `ll=` URLs — verify new test scenarios pass (spec: Apple/Waze scenario)
- [x] 1.4 Extend parser with DMS (`48°51'23.8"N 2°21'8.0"E`) and hemisphere (`48.8566N 2.3522E`) coordinate text — verify new test scenarios pass, including negative-coordinate and letter-variant edge cases (spec: DMS/hemisphere scenarios)
- [x] 1.5 Add short-link resolution hook: parser stays synchronous and pure; resolution is an injectable step (resolver lambda/interface) that follows redirects with a ~5 s timeout and degrades to raw-text query on failure — verify unit tests cover resolved and unresolvable short links (spec: short-link scenarios)

## 2. Phone-side plumbing (share/location-receiving: "Shared location received while app running")

- [x] 2.1 Create `SharedLocationRequest` model and `SharedLocationParser` in `:app` (`share/` package): maps `Intent` → request (coordinate + label from `EXTRA_SUBJECT` fallback, or query text), delegates format parsing to `DeepLinkParser`, resolves `maps.app.goo.gl` on `Dispatchers.IO` — verify `SharedLocationParserTest` covers coordinate, address, subject-label, and unresolvable-short-link inputs
- [x] 2.2 Create `SharedLocationHandler` (`@Singleton`, `MutableStateFlow<SharedLocationRequest?>`, consume-once) — verify `SharedLocationHandlerTest` covers write, collect, and clear-after-consume
- [x] 2.3 Set `MainActivity` `launchMode="singleTask"` in the manifest and wire `onCreate` + `onNewIntent` to parse via `SharedLocationParser` and write to the handler — verify the app builds and a second share while running reaches `onNewIntent` (logcat `NaviVeylin` tag)

## 3. MapCanvasViewModel share flow (share/location-receiving: candidate picker, address search, map centering)

- [x] 3.1 Extract the `onLongPress` candidate-flow body into a shared `showCandidatesFor(lat, lon, zoom, label)` method — verify existing long-press tests still pass and long-press behavior is unchanged
- [x] 3.2 Collect the handler in `MapCanvasViewModel.init`; on a coordinate request call `showCandidatesFor(lat, lon, SHARE_CANDIDATE_ZOOM=16)` after `updateCenter`; no candidates → details sheet on the raw coordinate — verify new unit tests cover candidates-found, no-candidates, and map-centering paths (spec: candidate picker + centering requirements)
- [x] 3.3 On a query-text request trigger the existing search flow — verify unit test covers the query dispatch (spec: "Shared address triggers search")

## 4. Verification

- [x] 4.1 Build `:app` and `:auto` (all flavors) and run the full unit test suite — verify no compile errors and all existing + new tests pass
- [x] 4.2 On-device check (phone emulator): share a `geo:` coordinate and a Google Maps link from another app — verify candidate picker opens, selection opens details with Show/Fav/Route, no-candidates case opens raw-coordinate details, address text triggers search, and a second share while the app is running is processed without restart; inspect `adb logcat -s NaviVeylin` for the share path
