# Tasks: UI Improvements

## 1. Next-turn overlay typography and wrapping

- [x] 1.1 In `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt`, split the next-turn description into two lines: line 1 = `instruction.shortDescription` (fallback `instruction.description`), line 2 = `instruction.streetName` (only when non-empty, bold). Keep `maxLines=2` + `TextOverflow.Ellipsis` per line. (spec: next-turn-overlay)
- [x] 1.2 Bump next-turn description style from `bodyLarge` to `titleMedium`. (spec: next-turn-overlay)
- [x] 1.3 Bump next-next description style from `bodySmall` to `bodyMedium` (stays smaller than next-turn). (spec: next-turn-overlay)
- [x] 1.4 Add unit test for the generic/destination split helper (empty street name → single line; non-empty → two lines). (spec: next-turn-overlay)

## 2. Current road name emphasis

- [x] 2.1 In `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt`, change road name style from `bodyMedium` to `titleMedium`, add `textAlign = TextAlign.Center` and `fillMaxWidth`. (spec: current-road-info)

## 3. Expandable routing status card

- [x] 3.1 Add `onClick: () -> Unit = {}` parameter to `NavigationStateOverlay`; make the card clickable. (spec: navigation-status-details)
- [x] 3.2 Create `app/src/main/java/com/naviveylin/ui/navigation/NavigationDetailsOverlay.kt`: full-screen scrim + Surface, header with road name + stats row (same layout as status card), `LazyColumn` of `navState.instructions` styled like `RouteSummaryDialog` steps (turn icon, `formatDistance(distanceTo)`, description), current step highlighted with `primaryContainer` background + bold text, `LazyListState(initialFirstVisibleItemIndex = currentStepIndex)` + `LaunchedEffect(currentStepIndex)` re-scroll, close button + `BackHandler`. (spec: navigation-status-details)
- [x] 3.3 In `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`, add `showNavDetails` state, pass `onClick` to `NavigationStateOverlay`, compose `NavigationDetailsOverlay` when `showNavDetails && navState.isNavigating`. (spec: navigation-status-details)
- [x] 3.4 Add Compose UI test for `NavigationDetailsOverlay`: current step highlighted and first visible item. (spec: navigation-status-details)

## 4. Verification

- [x] 4.1 Run `./gradlew :app:compileDebugKotlin` — build compiles without errors.
- [x] 4.2 Run `./gradlew test` — existing unit tests still pass (incl. `RoutePanelComposeTest`).
