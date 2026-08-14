## Context

See `proposal.md` for motivation. Current state:

- `NavigationViewModel` stores `currentSpeedKmH` from `onCurrentSpeed(double)` callback in `NavigationState`
- `MapCanvasViewModel` handles follow mode: on each GPS location update, re-centers map and calls `renderMap()`
- `MapCanvasUiState` has `followMode` (Boolean) and `mapAngle` (Double) but no auto-zoom fields
- `ZoomControls` composable calls `viewModel.zoomIn()` / `viewModel.zoomOut()` which update `viewport.magnification`
- `MapCanvasScreen` handles pinch-zoom and scroll-wheel zoom gestures, calls `viewModel.updateMagnification(newMag)`
- `NextTurnOverlay` displays next instruction with distance — turn distance available from `NavigationState.nextInstruction`
- No auto-zoom logic exists in NaviVeylin Kotlin code
- JavaScout reference implementation exists at `app/src/main/cpp/libosmscout/JavaScout/` for logic patterns

## Goals / Non-Goals

**Goals:**
- Speed-based auto-zoom in `MapCanvasViewModel` follow-mode handler
- Turn-aware zoom boost using distance from next route instruction
- Speed spike rejection (filter > 150 km/h)
- Manual zoom suspension with band-change re-engagement
- Auto-zoom toggle in navigation UI
- Smooth zoom transitions (max 1 level per position update)
- All logic in Kotlin — no JNI/C++ changes

**Non-Goals:**
- Animated zoom transitions (instant snap per update is acceptable)
- Per-vehicle profile customization in v1 (single mapping for all vehicles)
- Speed-dependent map rotation (handled separately by follow-mode bearing)
- Changes to `libosmscout-client-java` JNI layer or C++ code

## Decisions

### Decision 1: Auto-zoom lives in MapCanvasViewModel, not NavigationViewModel

**Chosen:** `MapCanvasViewModel` owns the auto-zoom computation. It reads speed from `NavigationViewModel.state`, turn distance from `NavigationViewModel.state.nextInstruction`, and applies zoom via the existing `updateMagnification()` / `renderMap()` path.

**Rationale:** `MapCanvasViewModel` already owns the follow-mode loop, viewport state, and render pipeline. Adding auto-zoom there keeps all map-view logic in one place. `NavigationViewModel` is the data source (speed, turn distance) — it should not control rendering.

**Alternatives considered:**
- Auto-zoom in `NavigationViewModel`: would need renderer dependency, mixing concerns
- Separate `AutoZoomViewModel`: over-engineered for this scope

### Decision 2: Speed-to-magnification lookup table with linear interpolation

**Chosen:** Same approach as JavaScout — a `SpeedZoomLevel` data class array with linear interpolation between breakpoints.

```kotlin
private data class SpeedZoomLevel(val speedKmH: Double, val magnification: Double)

private val SPEED_ZOOM_TABLE = listOf(
    SpeedZoomLevel(0.0,   17.0),   // stationary
    SpeedZoomLevel(6.0,   16.5),   // slow jog
    SpeedZoomLevel(15.0,  16.0),   // cycling / slow city
    SpeedZoomLevel(30.0,  15.0),   // city driving
    SpeedZoomLevel(60.0,  14.0),   // suburban
    SpeedZoomLevel(90.0,  13.0),   // highway
    SpeedZoomLevel(130.0, 12.0),   // very fast
)
```

**Rationale:** Simple, predictable, easy to tune. A formula is harder to reason about. The table makes mapping explicit without code changes.

**Alternatives considered:**
- Logarithmic formula: harder to tune, less intuitive
- Fixed zoom per band (no interpolation): visible jumps at boundaries

### Decision 3: Manual zoom suspension with band-change re-engagement

**Chosen:** When user manually zooms, auto-zoom suspends. Auto-zoom target still computed but not applied. When speed crosses a table row boundary, auto-zoom re-engages.

```kotlin
private var autoZoomSuspended = false
private var lastSpeedBandIndex = -1

// In follow-mode handler:
if (autoZoomEnabled && !autoZoomSuspended) {
    applyZoom(targetMag)
} else if (autoZoomSuspended && currentBandIndex != lastSpeedBandIndex) {
    autoZoomSuspended = false
    applyZoom(targetMag)
}
```

**Rationale:** User might zoom in to inspect an intersection. Auto-zoom snapping back immediately would frustrate. But if speed changes significantly (highway→city), old zoom is inappropriate and auto-zoom should re-engage.

**Alternatives considered:**
- Permanent override until manual re-enable: annoying, requires extra tap
- Time-based decay: arbitrary, unpredictable

### Decision 4: Turn zoom boost as magnification floor

**Chosen:** Turn zoom applies a minimum floor on target magnification. Speed-based zoom computes the base target, then turn boost raises it if the floor is higher.

```kotlin
val speedTarget = computeSpeedZoom(filteredSpeed)
val turnFloor = computeTurnBoost(turnDistanceMeters)
val finalTarget = maxOf(speedTarget, turnFloor)
```

**Rationale:** Simple composition of two independent concerns. Speed zoom handles the general case, turn zoom only overrides when it needs more detail. No complex state machine needed.

**Alternatives considered:**
- Separate turn-zoom state machine: more complex, harder to reason about
- Turn zoom replaces speed zoom entirely: loses speed-appropriate zoom during turns

### Decision 5: Speed spike filter in MapCanvasViewModel

**Chosen:** `MapCanvasViewModel` maintains `lastValidSpeedKmH` and filters incoming speed values before any zoom computation.

```kotlin
private var lastValidSpeedKmH = 20.0  // default

private fun filterSpeed(rawSpeedKmH: Double): Double {
    if (rawSpeedKmH >= 0 && rawSpeedKmH <= 150.0) {
        lastValidSpeedKmH = rawSpeedKmH
    }
    return lastValidSpeedKmH
}
```

**Rationale:** Filter at the point of use (MapCanvasViewModel) rather than in NavigationViewModel, since NavigationViewModel's `currentSpeedKmH` is the raw engine value that other consumers (speed display) may want unmodified.

**Alternatives considered:**
- Filter in NavigationViewModel: would affect speed display, which should show raw value
- Filter in a shared utility: unnecessary abstraction for one filter rule

### Decision 6: Auto-zoom toggle in NavigationStateOverlay

**Chosen:** Add auto-zoom toggle button to `NavigationStateOverlay` composable, near the stop-navigation button. Toggle state flows through `MapCanvasUiState.autoZoomEnabled`.

**Rationale:** The navigation overlay is visible during navigation and already contains controls. Adding the toggle there is discoverable and doesn't clutter the map.

**Alternatives considered:**
- Part of follow-mode button cycle (follow → follow+auto → off): more complex UX, less explicit
- Settings screen only: not discoverable during navigation

## Data Flow

```
NavigationEngine
    │
    ├── onCurrentSpeed(speedKmH)  →  NavigationViewModel._state.currentSpeedKmH
    │
    └── onPositionEstimate(pos)   →  NavigationViewModel._state.position
                                      →  MapCanvasViewModel._navPosition (via positionFlow)

MapCanvasViewModel follow-mode loop (per location update):
    1. Read speed from NavigationViewModel.state.currentSpeedKmH
    2. Apply speed spike filter → filteredSpeed
    3. Compute speed-based target: computeSpeedZoom(filteredSpeed)
    4. Read turn distance from NavigationViewModel.state.nextInstruction.distance
    5. Compute turn floor: computeTurnBoost(turnDistance)
    6. Final target = max(speedTarget, turnFloor)
    7. If autoZoomSuspended && band changed → re-engage
    8. If !autoZoomSuspended → smooth toward target (max ±1/update)
    9. Apply via updateMagnification() + renderMap()
```

## State Additions

### MapCanvasUiState additions
```kotlin
val autoZoomEnabled: Boolean = true
```

### MapCanvasViewModel additions
```kotlin
private data class SpeedZoomLevel(val speedKmH: Double, val magnification: Double)

private val SPEED_ZOOM_TABLE: List<SpeedZoomLevel> = ...
private var autoZoomSuspended: Boolean = false
private var lastSpeedBandIndex: Int = -1
private var lastValidSpeedKmH: Double = 20.0
private var currentTargetMag: Double = 15.0
```

## Risks / Trade-offs

- **[Zoom oscillation at speed boundaries]** Speed hovering at a table boundary could cause mag to flip. → Mitigated by smooth transition (max ±1/update) and band-change detection (only re-engages on crossing, not every update).
- **[GPS speed noise]** Noisy speed readings cause jittery zoom. → The engine's SpeedAgent already smooths speed; we use engine-reported speed, not raw GPS deltas.
- **[Manual zoom during high-speed turn]** User zooms in to see turn, speed drops (turning), band changes, auto-zoom re-engages and zooms out. → Acceptable trade-off. Band-change heuristic works for sustained speed changes, not momentary dips.
- **[Pedestrian use]** Walking speed (~5 km/h) maps to mag 16.5-17, very zoomed in. → Correct for pedestrian navigation where individual buildings and paths matter.
- **[Turn distance from next instruction]** If route instructions are sparse, turn distance may be stale. → Acceptable; turn boost only applies when distance is available and within range.
