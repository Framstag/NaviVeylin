# Proposal: Correctly handle back gesture

## Why

Modern Android (API 29+) uses an edge-swipe back gesture (finger from right screen edge to left) as the primary system back affordance. NaviVeylin currently has **zero** `BackHandler`/`OnBackPressedDispatcher` usage: full-screen sheets (favorites management, search panel) ignore the gesture entirely, so swiping back from the favorites sheet exits the app instead of closing the sheet. Users expect back to close the topmost dialog/sheet, never the app.

## What Changes

- Add predictive-back support (`androidx.activity` `BackHandler` / `OnBackPressedDispatcher` integration) to the map screen.
- Back gesture/button closes the favorites management sheet (full-screen) and returns to the map — including from group-detail sub-screens (navigate back to main grid first, then close sheet).
- Back gesture/button closes the search panel overlay.
- Back gesture/button closes any open dialog (about, route summary, favorite picker, permission rationale, details sheet, location options) — most already dismiss via `onDismissRequest`; verify and fill gaps.
- Back gesture/button on the base map screen (no overlay open) keeps default behavior (app backgrounding) — unchanged.
- No behavior change for `AlertDialog`/`ModalBottomSheet` components that already dismiss correctly via `onDismissRequest`.

## Capabilities

### New Capabilities
- `back-gesture`: System back gesture/button handling for the map screen — closes the topmost open sheet or dialog instead of exiting the app; falls through to default behavior when nothing is open.

### Modified Capabilities
- `fav-management-ui`: "Close favorites sheet" requirement extended — sheet SHALL also close via system back gesture/button, not only the toolbar back button.
- `map-canvas-screen`: Overlay/sheet/dialog stack SHALL respond to system back by dismissing the topmost layer.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — add `BackHandler` wiring for favorites sheet, search panel, and dialog flags.
- `app/src/main/java/com/naviveylin/ui/favorites/FavoritesSheet.kt` — back from group-detail sub-screen returns to main grid before closing sheet.
- `app/src/main/java/com/naviveylin/ui/search/SearchPanel.kt` (or wherever search panel lives) — back closes panel.
- `app/build.gradle.kts` — ensure `androidx.activity:activity-compose` dependency present (for `BackHandler`).
- AndroidManifest — verify `android:enableOnBackInvokedCallback` for predictive-back animation (API 33+).
- Tests: Compose tests for back-gesture dismissal of favorites sheet and search panel.
