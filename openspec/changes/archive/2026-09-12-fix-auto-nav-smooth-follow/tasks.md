## 1. Shared speed/bearing derivation helper

- [x] 1.1 Extract `effectiveSpeed`/`effectiveBearing`/`movementSpeedKmH`/`movementBearing` from `FreeDrivingScreen.kt` into a shared helper (e.g. `core` `AutoPositionUtil` or an `:auto` util), refactor `FreeDrivingScreen` to use it with unchanged behavior, and verify `./gradlew :auto:testDebugUnitTest --tests "*FreeDrivingScreen*"` passes (spec: auto-smooth-follow — "Fix feed from follow-mode screens")
- [x] 1.2 Add unit tests for the shared derivation helper: GPS speed valid → used as-is; GPS speed missing → derived from distance/interval; GPS bearing missing → movement direction; negligible movement → last effective bearing/speed kept (spec: auto-smooth-follow — "Speed derived from movement…" / "Bearing derived from movement…")

## 2. NavigationScreen wiring

- [x] 2.1 In `NavigationScreen`'s GPS collect, compute effective speed + bearing via the shared helper and pass `speedKmH`/derived bearing/`timeMs` to `AutoMapRenderer.setGpsMarker`, so the extrapolation gate opens and the routing map glides like free driving (spec: auto-smooth-follow — "Fix feed from follow-mode screens"; auto/navigation-view — "Smooth follow-mode scrolling during navigation")
- [x] 2.2 Add a regression test in `NavigationScreenTest` asserting `setGpsMarker` receives the fix speed (not the NaN default) for real and derived speeds (spec: auto/navigation-view — "Smooth follow-mode scrolling during navigation")

## 3. Verification

- [x] 3.1 Verify the project builds without errors: `./gradlew :app:assembleMobileDebug` and `./gradlew :app:assembleAutomotiveDebug` (spec: auto-smooth-follow, auto/navigation-view)
- [x] 3.2 Verify all existing unit tests still pass: `./gradlew :auto:testDebugUnitTest` and `./gradlew :app:testDebugUnitTest` (spec: auto-smooth-follow, auto/navigation-view)
- [x] 3.3 Drive test on AAOS head unit / projection (or GPX replay): routing view scrolls smoothly between fixes like free driving — no per-fix snap, marker stays on the road, no surface lock errors (spec: auto-smooth-follow — "Fix feed from follow-mode screens"; auto/navigation-view — "Smooth follow-mode scrolling during navigation")
