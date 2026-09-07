# Tasks: Enlarge phone navigation overlays for driver-seat readability

Parent specs: `map-speed-widget` (specs/map-speed-widget/spec.md), `next-turn-overlay` (specs/next-turn-overlay/spec.md). Design: design.md.

## 1. SpeedWidget: badge container + sizes (spec: map-speed-widget — Speed badge uses the standard overlay card container, Minimum readable size for speed text, Minimum readable size for the max-speed sign)

- [x] 1.1 In `SpeedWidget.kt`, change the badge background from `Color(0xCC1C1B1F)` to the standard overlay card container: `MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)` with `RoundedCornerShape(12.dp)` (same as the turn card / routing status; keep the transparent background when no speed, and `Color.Transparent` for the reserved slot); badge speed text style `titleMedium` → `headlineSmall` (24sp) bold; keep `MAX_SPEED_TEXT` width reservation and the `badgeColor` overspeed rule unchanged; verify compile via `./gradlew :app:compileMobileDebugKotlin`
- [x] 1.2 In `SpeedWidget.kt`, enlarge the max-speed sign: circle `size(40.dp)` → `size(56.dp)`, border 4dp → 5dp, digits `titleMedium` → `titleLarge` (22sp) bold; enlarge the invisible placeholder to the same 56dp footprint; update the KDoc constants if any reference the old sizes; verify compile
- [x] 1.3 Extend `SpeedWidgetTest.kt` (app/src/test/java/com/naviveylin/ui/map/): assert the badge text font size >= 24sp (text semantics), the sign circle size == 56dp and digits >= 22sp (`fetchSemanticsNode().size`), and the placeholder == 56dp; keep the width-stability, overspeed-color, hidden-state, and slot-reservation cases green; add a case that the badge no longer uses a fixed dark background (assert container uses the theme card — e.g. background semantics or a dedicated testTag + captured pixel check); verify `./gradlew :app:testMobileDebugUnitTest --tests "*SpeedWidgetTest*"` passes

## 2. NextTurnOverlay: driver-seat font sizes (spec: next-turn-overlay — Driver-seat readable font sizes)

- [x] 2.1 In `NextTurnOverlay.kt`: distance `headlineSmall` (24sp) → `headlineLarge` (32sp) bold; description/destination `18.sp` → `titleLarge` (22sp); next-next texts `16.sp` → `20.sp`; turn arrow 48dp → 64dp; next-next arrow 28dp → 36dp; keep `maxLines = 2` + ellipsis and the card container unchanged; verify compile
- [x] 2.2 Add `NextTurnOverlayTest.kt` (app/src/test/java/com/naviveylin/ui/map/ or ui/navigation/): Compose test asserting distance font size >= 32sp, description/destination >= 22sp, next-next >= 20sp and smaller than the primary, icons >= 64/36dp, and wrap+ellipsis on a long description; verify `./gradlew :app:testMobileDebugUnitTest --tests "*NextTurnOverlayTest*"` passes

## 3. Guidelines update

- [x] 3.1 Update `guidelines/UI.md` (phone map section): speed badge SHALL use the standard overlay card container (surface 0.92, rounded 12) and the driver-seat minimum sizes (speed 24sp, sign 56dp, turn distance 32sp, description 22sp, next-next 20sp); note phone-only (AA uses template scale)

## 4. Build and regression verification

- [x] 4.1 Verify full build compiles without errors: `./gradlew :app:assembleMobileDebug` (build-app skill)
- [x] 4.2 Verify existing tests still pass: `./gradlew test` (run-tests skill), including `SpeedWidgetTest`, `RoutePanelComposeTest`, and navigation tests
- [x] 4.3 Verify no `:auto` module changes and no AA/spec parity regressions (labels unchanged — code review diff check)

## 5. On-device verification (phone)

- [x] 5.1 Navigate with a realistic instruction set (multi-turn route): confirm the turn distance (32sp) and description (22sp) are readable from the driver seat; long description wraps with ellipsis and the card does not cover the routing status
- [x] 5.2 Confirm the speed badge is readable on a light map and a dark map (dark mode), including overspeed state (red/error text on the card background); the speed limit sign is legible; the widget column is not clipped in portrait and landscape (badge + 56dp sign fit above the routing status card)
