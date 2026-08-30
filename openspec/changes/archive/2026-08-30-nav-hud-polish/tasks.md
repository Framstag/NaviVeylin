# Tasks — nav-hud-polish

## 1. Speed widget stability (spec: map-speed-widget)

- [x] 1.1 Add `reserveLimitSpace: Boolean = false` param to `SpeedWidget`; render an invisible 40dp placeholder (with 6dp top spacing, tag `speedLimitPlaceholder`) below the badge when no limit is known and the flag is set, and verify the file compiles
- [x] 1.2 Add Compose tests: placeholder displayed when `reserveLimitSpace = true` and max unknown; sign displayed (no placeholder) when max known; existing `limitSignHiddenWhenMaxUnknown` (default flag) still passes, and verify `./gradlew test` passes
- [x] 1.3 Reserve the badge width for the widest speed value: render an invisible `"999 km/h"` text (tag `speedBadgeMaxText`) inside the badge Box with `contentAlignment = Center`, and verify the file compiles
- [x] 1.4 Add Compose tests: badge width unchanged when the speed value changes (48 → 120 km/h via mutable state); invisible max-width text present, and verify `./gradlew test` passes
- [x] 1.5 Add `reserveSlotWhenHidden: Boolean = false` param: when set and no current-speed data is available, render an invisible structure with the same footprint as the visible widget (tag `speedWidgetSlot`, transparent badge, reserved sign slot) instead of nothing, and verify the file compiles
- [x] 1.6 Add Compose tests: slot reserved (invisible, no `speedWidget`/`speedBadge`/`speedLimitSign` tags) when `reserveSlotWhenHidden = true` and speed NaN; no slot by default, and verify `./gradlew test` passes

## 2. Navigation layout: compass above speed widget (spec: map-speed-widget)

- [x] 2.1 Restructure the nav right-side column in `MapCanvasScreen`: `Spacer(weight)` → compass → 8dp spacer → `SpeedWidget(reserveLimitSpace = true)`, bottom-anchored above the routing status, and verify `./gradlew :app:compileMobileDebugKotlin` passes
- [x] 2.2 Verify follow-mode placements (compass above speed, top-right) are unchanged
- [x] 2.3 Center the indicator cluster on a common axis: change the nav and follow-landscape right-side columns to `horizontalAlignment = CenterHorizontally`; wrap the follow-portrait compass + speed widget in a centered inner column (location/zoom stays right-aligned), and verify `./gradlew :app:compileMobileDebugKotlin` passes
- [x] 2.4 Always render the nav speed widget (passing NaN when no data) with `reserveSlotWhenHidden = true` so the compass does not shift when the widget appears or disappears, and verify `./gradlew :app:compileMobileDebugKotlin` passes

## 3. Full-width turn hints (spec: nav-hints-layout)

- [x] 3.1 Remove the asymmetric `padding(start = 0.dp, end = 16.dp)` from the `NextTurnOverlay` card (keep top/bottom 8dp) and verify the file compiles
- [x] 3.2 Mark the superseded `nav-hints-layout` requirements ("Navigation hints avoid on-map buttons", "Navigation hints start right of the toaster button") as REMOVED in the delta spec — during navigation the action columns and toaster are hidden and the compass/speed cluster is bottom-anchored, so the new "full width" requirement is operative

## 4. Routing status covers the bottom (spec: nav-hints-layout)

- [x] 4.1 Remove `navigationBarsPadding` from the navigation overlay Column in `MapCanvasScreen` and verify the file compiles
- [x] 4.2 Give `NavigationStateOverlay` square bottom corners (`topStart/topEnd = 12dp`, `bottomStart/bottomEnd = 0dp`), add `navigationBarsPadding` to the card content Column, update the off-route clip shape to match, and verify `./gradlew :app:compileMobileDebugKotlin` passes

## 5. Next-turn typography (spec: next-turn-overlay)

- [x] 5.1 Bump `NextTurnOverlay` fonts: distance `titleLarge` → `headlineSmall`, instruction/destination `titleMedium` → 18sp, next-next row `bodyMedium` → 16sp (next-next stays smaller than next-turn), and verify the file compiles

## 6. Verification

- [x] 6.1 Run `./gradlew test` and verify all tests pass
- [x] 6.2 Run `./gradlew :app:assembleMobileDebug` and verify the build succeeds
- [x] 6.3 Run `openspec validate nav-hud-polish --type change` and verify the change is valid
