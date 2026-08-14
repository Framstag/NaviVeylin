## 1. ViewModel State & Lifecycle

- [x] 1.1 Add `showSummaryDialog: Boolean` and `activeStepIndex: Int?` to `RoutePanelUiState` (defaults: `false`, `null`)
- [x] 1.2 Add `showSummaryDialog()` method to `RoutePanelViewModel` — sets `showSummaryDialog = true`
- [x] 1.3 Add `dismissSummaryDialog()` method to `RoutePanelViewModel` — sets `showSummaryDialog = false`
- [x] 1.4 Modify `onSuccess()` in `RoutePanelViewModel` — after setting route state to `Done`, call `showSummaryDialog()`
- [x] 1.5 Add `setActiveStepIndex(index: Int)` method for future active navigation mode

## 2. RouteSummaryDialog Composable

- [x] 2.1 Create `app/src/main/java/com/naviveylin/ui/route/RouteSummaryDialog.kt`
- [x] 2.2 Implement `RouteSummaryDialog` composable with `AlertDialog` (Material 3) — title "Route Summary", close button
- [x] 2.3 Add route statistics section — display total distance (formatted km/m) and estimated time from `RouteEntry`
- [x] 2.4 Add scrollable step list using `LazyColumn` — iterate `instructions`, render each with description + distance
- [x] 2.5 Add "Start Navigation" `FilledButton` at bottom — wired to `onStartNavigation` (no-op lambda for now)
- [x] 2.6 Add close (X) `IconButton` in dialog title — wired to `onDismiss`
- [x] 2.7 Implement active navigation mode: when `activeStepIndex` is non-null, highlight that step with `primaryContainer` background + bold text, auto-scroll via `LazyListState.animateScrollToItem()`

## 3. Wire Dialog into RoutePanel

- [x] 3.1 In `RoutePanel.kt`, read `showSummaryDialog` from `RoutePanelViewModel.uiState`
- [x] 3.2 When `showSummaryDialog` is true, compose `RouteSummaryDialog` on top of the route panel content
- [x] 3.3 Pass `onDismiss` → `viewModel.dismissSummaryDialog()`, `onStartNavigation` → no-op lambda
- [x] 3.4 Remove inline instruction list from route panel body — instructions now live in the summary dialog
- [x] 3.5 Ensure route panel remains visible behind the dialog (don't dismiss the sheet when dialog shows)

## 4. Build & Verify

- [x] 4.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — verify compilation
- [x] 4.2 Run `./gradlew test` — verify existing tests still pass
- [x] 4.3 Manual smoke test: calculate a route → verify summary dialog appears with stats + steps + Start Nav button → dismiss → route panel still visible with route intact
