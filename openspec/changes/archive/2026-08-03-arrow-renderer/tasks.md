## 1. Shared Utility Consolidation

- [x] 1.1 Extract `formatDistance()` into shared top-level function in `NavigationArrowRenderer.kt`
- [x] 1.2 Update `NextTurnOverlay.kt` to import shared `formatDistance` instead of private one
- [x] 1.3 Update `RouteSummaryDialog.kt` to import shared `formatDistance` instead of private one
- [x] 1.4 Update `NavigationStateOverlay.kt` to import shared `formatDistance` instead of private one

## 2. NavigationArrowRenderer — Core Composable

- [x] 2.1 Create `NavigationArrowRenderer.kt` with `NavSymbol` sealed interface (`TurnArrow`, `LaneArrow`, `Roundabout`)
- [x] 2.2 Implement turn arrow Canvas paths for all 12 `TurnType` variants (SHARP_LEFT through MOTORWAY_ENTER)
- [x] 2.3 Implement lane arrow Canvas paths for all 18 `LaneTurn` variants (LEFT through UNKNOWN)
- [x] 2.4 Implement compound lane arrows (LEFT_AND_STRAIGHT, STRAIGHT_AND_RIGHT, etc.) as offset dual paths
- [x] 2.5 Expose `@Composable fun NavigationArrow(symbol: NavSymbol, size: Dp, color: Color, modifier: Modifier)` entry point
- [x] 2.6 Extract angle/position math into pure testable functions

## 3. Roundabout Renderer

- [x] 3.1 Implement roundabout circle drawing with `Canvas.drawCircle`
- [x] 3.2 Implement exit marker placement at configurable angles around circumference
- [x] 3.3 Implement evenly-spaced exit fallback when angle data absent
- [x] 3.4 Implement selected exit highlighting (primary color, larger marker)
- [x] 3.5 Implement entry direction indicator (gap or distinct marker)
- [x] 3.6 Wire `Roundabout` data class into `NavigationArrow` composable

## 4. Update NextTurnOverlay

- [x] 4.1 Replace `turnTypeToIcon()` Text with `NavigationArrow(TurnArrow(...))` composable
- [x] 4.2 Replace `laneTurnToArrow()` Text with `NavigationArrow(LaneArrow(...))` composable
- [x] 4.3 Remove private `turnTypeToIcon()`, `laneTurnToArrow()`, `formatDistance()` functions
- [x] 4.4 Remove unused Unicode string constants

## 5. Update RouteSummaryDialog

- [x] 5.1 Replace `turnTypeToIcon()` Text with `NavigationArrow(TurnArrow(...))` composable
- [x] 5.2 Remove private `turnTypeToIcon()` and `formatDistance()` functions
- [x] 5.3 Remove unused Unicode string constants

## 6. Build & Verify

- [x] 6.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` to verify compilation
- [x] 6.2 Run `./gradlew test` to verify unit tests pass
- [x] 6.3 Verify no regressions in navigation UI rendering
