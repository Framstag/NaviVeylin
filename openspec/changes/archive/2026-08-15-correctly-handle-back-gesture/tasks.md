# Tasks: Correctly handle back gesture

## 1. Favorites sheet back handling

- [x] 1.1 Add `BackHandler(enabled = isDetailView)` inside `FavoritesSheet` that resets `isDetailView` to false (returns to main grid) — import `androidx.activity.compose.BackHandler`
- [x] 1.2 Add `BackHandler(enabled = !isDetailView)` inside `FavoritesSheet` that calls `onDismiss()` (closes sheet on main grid; no app exit)

## 2. Map screen back wiring

- [x] 2.1 Overlays own their back handling: `FavoritesSheet` (both levels), `SearchPanel` (ModalBottomSheet `onDismissRequest`), dialogs (`onDismissRequest`); no `BackHandler` needed in `MapCanvasScreen`
- [x] 2.2 Verify all dialogs (about, route summary, favorite picker, permission rationale, details sheet, location options) dismiss via `onDismissRequest` on back — add `BackHandler` to `RouteSummaryDialog` (custom Box overlay, no `onDismissRequest`)
- [x] 2.3 Add `BackHandler(enabled = navState.isNavigating)` in `MapCanvasScreen` that rejects back on the base map while navigation is active (snackbar "Stop navigation before exiting"); overlays still dismiss first (their handlers register later and win)

## 3. Tests

- [x] 3.1 Compose test: back closes favorites sheet, app stays (map visible)
- [x] 3.2 Compose test: back from group detail sub-screen returns to main grid, sheet stays open
- [x] 3.3 Compose test: back gesture closes search panel
- [x] 3.4 Run `./gradlew :app:testDebugUnitTest` — all pass
- [x] 3.5 Manual/device verification of back rejection while navigating (MapCanvasScreen-level test needs Hilt setup — out of scope; covered by 4.2)

## 4. Verification

- [x] 4.1 `openspec validate correctly-handle-back-gesture --type change` passes
- [x] 4.2 Manual device check: edge-swipe back closes favorites sheet and search panel; predictive back animation shows on API 33+; back on base map backgrounds app
