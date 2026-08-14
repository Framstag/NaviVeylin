## Context

Map screen (`MapCanvasScreen.kt`) has a top-right overlay `Column` with: menu (⋮), search (🔍), favorites (❤️), location options (⚙️), and zoom controls. Orientation settings (`freeFormNorthUp`, `navNorthUp`) live in `MapCanvasUiState` and are toggled via the location options bottom sheet. GPS location flows through `LocationService` → `MapCanvasViewModel`. No visual north indicator exists today.

See `proposal.md` — Why for motivation, `specs/` for full requirements.

## Goals / Non-Goals

**Goals:**
- Animated compass composable that rotates to show north
- Colored ring indicating GPS fix quality (light red/yellow/green)
- Short press → re-center on location + enable follow mode
- Long press → toggle orientation mode (north-up ↔ follow direction)
- Compass mode stays in sync with existing orientation settings
- Positioned below menu, above search in the overlay column

**Non-Goals:**
- Not a standalone navigation instrument (no bearing readout, no heading numbers)
- No haptic feedback for mode toggle
- No compass calibration UI
- No persistent compass mode separate from existing orientation settings

## Decisions

### Decision: Custom Canvas composable for compass rendering

Use Compose `Canvas` with `drawRotate` + `drawCircle` + `drawLine` to render the compass needle and ring, rather than a pre-drawn image or an icon.

**Rationale:** Canvas gives smooth rotation animation via `animateFloatAsState`, full control over colors, and avoids asset scaling issues. A single composable with ~50 lines of drawing code replaces a vector asset + tinting logic.

**Alternatives considered:**
- **Vector drawable + `Image`**: Cannot animate rotation smoothly without wrapping in a `RotatedBox` or `Modifier.rotate` — same complexity, less control over ring colors.
- **Pre-rendered PNG**: Inflexible for color changes, density issues.

### Decision: `animateFloatAsState` for compass rotation

Use `animateFloatAsState(rotationDegrees, tweenSpec(300ms))` to drive the compass needle rotation.

**Rationale:** Built into Compose, zero boilerplate, automatically cancels/restarts on target change. 300ms tween matches spec requirement and feels responsive without disorienting the user.

**Alternatives considered:**
- `Animatable` + `LaunchedEffect`: More control but unnecessary — no need for custom easing or interruption logic.
- `RotationTransition` via `updateTransition`: Overkill for a single animated value.

### Decision: GPS fix quality derived from `Location.getAccuracy()`

Compute fix quality in `MapCanvasViewModel` and expose as a `StateFlow<GpsFixQuality>` enum (`NONE`, `POOR`, `GOOD`).

- `NONE`: location is null or >5s stale
- `POOR`: accuracy > 50m
- `GOOD`: accuracy ≤ 50m

**Rationale:** `Location.getAccuracy()` is available on all Android API 26+ devices. No need for `GpsStatus.NmeaListener` or raw satellite count — accuracy is the user-visible metric.

**Alternatives considered:**
- Raw satellite count via `GpsStatus`: More accurate but requires extra permission and listener plumbing. Not worth complexity for a visual indicator.

### Decision: Long press via `combinedClickable` modifier

Use `Modifier.combinedClickable(onLongClick, onClick)` from `foundation` to handle short press vs long press.

**Rationale:** `combinedClickable` is the standard Compose approach, handles timing internally, and avoids reimplementing gesture detection. The existing map canvas uses raw `pointerInput` for pan/zoom — compass is a separate button so no conflict.

**Alternatives considered:**
- Custom `pointerInput` with timeout: Duplicates logic that `combinedClickable` already provides.
- `LongPressGestureDetector` wrapper: More verbose, no benefit.

### Decision: Compass mode toggles existing `freeFormNorthUp`/`navNorthUp` state

Long press on compass calls the same `onSetFreeFormOrientation`/`onSetNavOrientation` callbacks that the location options bottom sheet uses.

**Rationale:** Single source of truth. The bottom sheet and compass both write to the same `MapCanvasUiState` fields — they stay in sync automatically. No new state fields needed for mode.

**Alternatives considered:**
- Separate `compassMode` state field: Would require sync logic between compass mode and orientation settings. Unnecessary indirection.

### Decision: Re-center on short press calls `onToggleFollowMode(true)` + `updateCenter`

Short press enables follow mode and centers on current GPS location. If no location, show snackbar.

**Rationale:** Re-uses existing follow mode and center update machinery. No new navigation logic.

## Risks / Trade-offs

- **[Animation jank]** Compose animation on low-end devices may stutter during map render → Mitigation: animation runs on the Compose UI thread independently of map rendering (native thread). Test on armeabi-v7a devices.
- **[Long press conflict]** `combinedClickable` long press delay (default ~500ms) may feel slow → Mitigation: 500ms is standard Android long press duration. Users are accustomed to it.
- **[GPS accuracy variance]** `getAccuracy()` can fluctuate rapidly, causing ring color flicker → Mitigation: apply a 2-second debounce/debounce on fix quality changes in the ViewModel.
- **[No GPS fix on first launch]** Ring shows red until first fix → Acceptable. Red indicates "no fix" which is accurate.

## Migration Plan

1. Add `GpsFixQuality` enum and computed state to `MapCanvasViewModel`
2. Create `CompassButton.kt` composable with Canvas rendering + animation
3. Wire compass into `MapCanvasScreen.kt` overlay column (below menu, above search)
4. Wire short press → re-center + follow mode
5. Wire long press → toggle orientation (reuse existing callbacks)
6. Test: rotation animation, ring colors, mode toggle, re-center

Rollback: Remove compass composable from overlay column, revert `MapCanvasViewModel` changes.

## Open Questions

- Should the compass needle have a distinct shape (arrow vs line with N marker)? A simple line with a red tip is the most recognizable compass indicator — confirm during review.
