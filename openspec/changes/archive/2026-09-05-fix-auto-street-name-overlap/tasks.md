# Tasks: Fix Auto Street-Name Overlap with Host ETA Card

## 1. Label geometry (spec: auto/navigation-view, auto/free-driving)

- [x] 1.1 Extend `StreetNameLabel.geometry` to take `visibleBounds: Rect` and `bottomReserveDp: Float = 0f`; compute the bottom edge as `min(stable.bottom, visible.bottom) - reserve`, falling back to the surface bottom only when both rects are empty; verify with new unit tests in `StreetNameLabelTest.kt` (min-bottom logic, empty-bounds fallback, reserve applied)
- [x] 1.2 Cap the label width at ~360 dp and ellipsize the text (`TextUtils.ellipsize`, `TruncateAt.END`) so the text cannot overflow the pill; verify with unit tests that a long name is truncated and the pill width stays within the cap
- [x] 1.3 Update `StreetNameLabel.draw` to forward the new parameters; verify existing `StreetNameLabelTest.kt` cases still pass

## 2. Screen wiring (spec: auto/navigation-view, auto/free-driving)

- [x] 2.1 In `NavigationScreen.kt`, track `visibleArea` as a separate rect (remove the `stableArea.set(visibleArea)` fallback in `onVisibleAreaChanged`); pass both rects plus a ~24 dp reserve to `StreetNameLabel.draw` while navigating; verify with a screen test that the visible area is tracked independently and the label receives both bounds
- [x] 2.2 In `FreeDrivingScreen.kt`, apply the same separate visible-area tracking (no reserve — no ETA card); verify with a screen test that the label receives both bounds and reserve 0
- [x] 2.3 Verify the `:auto` module compiles: `./gradlew :auto:compileDebugKotlin` (or the build-app skill) succeeds without errors

## 3. Verification

- [x] 3.1 Run the `:auto` unit test suite (`./gradlew :auto:testDebugUnitTest` or the run-tests skill) and verify all tests pass, including the new geometry/screen tests
- [x] 3.2 On-device check (AA emulator or head unit): navigate with a travel estimate and verify the street-name label sits entirely above the host ETA card; free-driving view unchanged; inspect `adb logcat -s NaviVeylin` for surface geometry
- [x] 3.3 Tune the reserve value (24 vs 32 dp) against the emulator/head unit if the label still touches the ETA card; update the constant and re-run the unit tests

## 4. Host ETA card fallback (design D5 — on-device finding: the head unit delivers both areas as the full surface, so no canvas anchor clears the card)

- [x] 4.1 Add `StreetNameLabel.isMapLabelSafe(stable, visible, surfaceHeight, density)` — true when at least one delivered area clears the surface bottom by the safety margin (~48 dp); verify with unit tests (both empty → false, both full surface → false, visible/stable clears → true, below-margin clearance → false)
- [x] 4.2 `NavigationScreen` draws the map label only when safe; when unsafe, `buildTravelEstimate` sets `setTripText(streetName)` on the travel estimate (via `tripTextFor(mapLabelSafe, streetName)`); verify with unit tests (safe → no trip text, unsafe + name → trip text, unsafe + no name → no trip text)
- [x] 4.3 Invalidate the template when the safety state flips (`onVisibleAreaChanged`/`onStableAreaChanged`) and when the street name changes while unsafe, so the card text follows the road; verify the `:auto` module compiles
- [x] 4.4 Run the `:auto` unit test suite and verify all tests pass
- [x] 4.5 On-device check: on the head unit that delivers full-surface areas, the street name appears inside the host ETA card and no half-covered label is drawn on the map; on a host with correct geometry the map label is used and the card carries no trip text
