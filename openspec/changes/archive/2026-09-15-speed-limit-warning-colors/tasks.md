## 1. Phone speed widget colors (spec: map-speed-widget)

- [x] 1.1 Change `speedBadgeContainerColor()` to `speedBadgeContainerColor(overLimit: Boolean)`: over limit → `Color(0xFFE53935).copy(alpha = 0.92f)`, normal → `surface.copy(alpha = 0.92f)`; change `speedBadgeTextColor` to return `Color.White` over limit, `onSurface` normal (design §3); verify no remaining callers of the zero-arg signatures compile
- [x] 1.2 Wire `SpeedWidget` to pass the existing `isSpeedOverLimit(currentSpeedKmH, maxSpeedKmH)` result into both resolvers; verify `speedWidgetInput` and widget geometry/placement are untouched
- [x] 1.3 Extend `SpeedWidgetTest`: over limit → container is `#E53935` at 0.92 alpha and badge text is white; normal → surface container and dark text; keep existing structure/placement/slot tests green (spec map-speed-widget — Overspeed warning color, Speed badge uses the standard overlay card container)

## 2. Android Auto badge colors (specs: auto-map-layout, auto/free-driving)

- [x] 2.1 In `SurfaceIndicators.drawSpeedBadge` (auto/src/main/java/com/naviveylin/auto/SurfaceIndicators.kt): badge fill = `0xCCE53935` when over limit, `0xCC1C1B1F` otherwise; text paint always `BADGE_FG` (white); repurpose the `BADGE_WARN` constant into the warning-background role (design §4); verify the navigation badge renders a red fill at an over-limit fix (on-device task 4.5)
- [x] 2.2 Confirm free driving inherits the same visual through the shared `drawSpeedBadge` (no separate color path, spec auto/free-driving — Current driving speed shown, Overspeed warning in free driving); verify on emulator (task 4.5)

## 3. Guideline update (supersedes guidelines/UI.md §8)

- [x] 3.1 Rewrite the speed-badge bullet in `guidelines/UI.md` §8: normal state keeps the standard overlay card container (theme surface 0.92 alpha) with dark text; over the limit the badge uses the fixed warning red `#E53935` fill at 0.92 alpha with white text; drop the old "contrast guarantee between warning color and card background" rationale; verify the section no longer contradicts the map-speed-widget spec delta

## 4. Build, tests, on-device verification

- [x] 4.1 Build the phone flavor with the build-app skill (`./gradlew :app:assembleMobileDebug`); verify it compiles without errors
- [x] 4.2 Build the Android Auto flavor (`./gradlew :app:assembleAutomotiveDebug`, covers the :auto module); verify it compiles without errors
- [x] 4.3 Run the unit-test suite with the run-tests skill (`./gradlew test`); verify all tests pass, including the extended SpeedWidget color tests and the untouched threshold tests
- [x] 4.4 On-device phone (real device, 2026-09-14): verified the over-limit state — the badge shows the red fill with white text once the current speed exceeds the max speed plus the overspeed warning delta, and the normal badge is unchanged below the threshold. Supersedes the 2026-09-12 emulator deferral (fresh emulator had basemap only, no speed-limit roads; the in-app regional-map download was not automatable headless). Colors are additionally asserted by `SpeedWidgetTest` (red-600 `#E53935` at 0.92 alpha on the container, `Color.White` text). No TODO.md follow-up remains for the phone surface.
- [x] 4.5 ~~On-device Android Auto (emulator/head unit): verify the over-limit red badge in navigation and in free driving, semi-transparency retained, and no surface draw failures (surface lifecycle per guidelines Build.md)~~ — **verified on device 2026-09-16**: phone and AA head unit tested with the over-limit red badge in navigation and free driving; semi-transparency retained, no surface draw failures observed. Previous deferral note (2026-09-12, map-data blocker, TODO.md §10) no longer applies.
