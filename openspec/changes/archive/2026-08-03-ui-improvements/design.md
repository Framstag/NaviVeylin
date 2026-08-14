## Context

The map screen uses Jetpack Compose with a `Canvas` for map rendering, overlaid with Compose widgets (compass, zoom controls, search button, menu). Keyboard input is handled via `Modifier.onKeyEvent`. Gestures use `Modifier.pointerInput` with `detectTransformGestures` for pinch-zoom and pan. The compass is a custom composable with animated rotation. The favorites sheet is a full-screen composable with internal navigation state. Zoom levels map to libosmscout `MagnificationLevel` values; the current max is 18.

See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Keyboard shortcuts for zoom and search on the map canvas
- Author/copyright attribution in about dialog
- About menu item in all app menus
- Max zoom level extended from 18 to 20
- Two-finger rotation gesture on the map canvas
- Favorites sheet resets to main screen on open
- Compass visual distinction between orientation modes
- Thicker compass ring

**Non-Goals:**
- Keyboard shortcuts for other actions (routing, navigation, favorites)
- Custom gesture handling library — use Compose built-in gesture detectors
- Changing the compass position or layout
- Redesigning the favorites sheet layout

## Decisions

### Keyboard shortcuts via `Modifier.onKeyEvent`

**Decision**: Add `Modifier.onKeyEvent` to the Canvas modifier chain, checking for `KeyEvent.KeyChar` `+`/`=` and `-`/`_` for zoom, and `KeyEvent.Key.Slash` (`/`) for search. Ctrl+F was avoided because the Android emulator intercepts Ctrl key combinations.

**Rationale**: Simplest approach — no external library needed. The Canvas already has `pointerInput` for gesture detection; `onKeyEvent` on the same element does not interfere with touch events.

**Alternatives considered**: `Ctrl+F` — intercepted by Android emulator. `onPreviewKeyEvent` on root Box — required `focusTarget()` which had compilation issues.

### Two-finger rotation via manual angle delta in gesture handler

**Decision**: Add rotation angle calculation inside the existing custom `awaitEachGesture` block. Compute the angle between two fingers using `atan2`, compare with previous angle, and apply the delta to the map rotation angle in `MapCanvasViewModel`.

**Rationale**: The existing gesture handler uses a custom `awaitEachGesture` with manual pointer tracking (not `detectTransformGestures`). Adding rotation as a manual angle delta calculation inside the existing two-finger pinch block avoids restructuring the entire gesture handler.

**Alternatives considered**: `detectTransformGestures` — would require replacing the entire custom gesture handler which handles long-press, drag threshold, and pinch zoom in a specific way.

### Compass mode differentiation via color and icon

**Decision**: In "always north" mode, the compass shows a prominent red "N" marker and a neutral gray body. In "follow direction" mode, the compass body uses a blue tint and shows a small directional arrow indicator.

**Rationale**: Color coding is quick to parse at a glance. Red "N" is the universal north indicator. Blue is already used for the GPS marker, creating visual consistency.

**Alternatives considered**: Shape change (square vs circle) — harder to implement with the ring. Text label ("N" vs "DIR") — too small to read at a glance.

### Compass ring thickness via `strokeWidth` parameter

**Decision**: Change the ring's `strokeWidth` from the current value to `3.dp` in the `Canvas` draw call.

**Rationale**: Single parameter change in the existing `drawCircle` or `drawArc` call. No layout or structural changes needed.

### Favorites sheet reset via `LaunchedEffect(Unit)`

**Decision**: Add a `LaunchedEffect(Unit)` in the `FavoritesSheet` composable that calls `viewModel.selectGroup(null)` on every composition. Since the sheet composable is removed from the tree when dismissed and re-created when opened, `LaunchedEffect(Unit)` runs fresh each time, resetting to the main screen.

**Rationale**: Simplest approach — no counter or key management needed. The composable lifecycle naturally provides the reset behavior.

**Alternatives considered**: Counter key on `LaunchedEffect` — more complex, unnecessary since the composable is re-created on each open.

### Zoom level 19-20 support

**Decision**: Change the max zoom constant from 18 to 20 in `MapCanvasViewModel`. Update the zoom button disabled check. Verify libosmscout supports these levels (it does — `MagnificationLevel` goes up to 20+).

**Rationale**: Pure constant change. No native code changes needed.

## Risks / Trade-offs

- [Keyboard shortcut conflicts] → `Ctrl+F` is standard for search; `+`/`-` are not used by Compose text fields on the map. Low risk.
- [Two-finger rotation conflicts with existing gestures] → `detectTransformGestures` handles rotation, pan, and zoom in one callback. Test on devices with varying touch sensitivity.
- [Compass mode colors not accessible] → Use Material 3 color tokens with sufficient contrast ratio. Test with accessibility color correction enabled.
- [Favorites sheet reset loses user progress] → The sheet only has one level of navigation (group detail). Resetting to the main screen is intentional per spec.
