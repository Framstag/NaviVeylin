## Context

See proposal.md — Why. Current state: `MapScreen` (root map view) already implements pan via `PanModeListener` on its `MapController` + `onScroll`/`onScale` in its `SurfaceCallback`. `NavigationScreen` (routing) and `FreeDrivingScreen` (free driving) both build a `NavigationTemplate` with no pan wiring: no `PanModeListener`, no `onScroll`/`onScale`, and their GPS handlers commit a viewport + `reengageFollow()` on every fix. The renderer already supports the flow: `setViewport` flips `followMode` off, `reengageFollow()` re-engages without snapping, `setGpsMarker` updates marker-only when follow is off, and the extrapolation loop gates off when follow is off. `AutoZoomController.suspend()` already exists (used by the zoom buttons). `ProjectionUtils` (in `:core`) has the pan/zoom math, shared with `MapScreen`.

## Goals / Non-Goals

**Goals:**
- Pan + pinch-zoom on both `NavigationTemplate` screens (routing, free driving) via the host's pan mode.
- While panned: follow off, auto-zoom suspended, heading-up rotation frozen; GPS fixes update the marker only.
- On pan exit: follow re-engages smoothly (no snap).
- Unit-testable pan logic (the screens' anonymous `SurfaceCallback`s are not directly testable).

**Non-Goals:**
- Refactor `MapScreen` to use the new shared handler (it works; its pane-offset quirks differ).
- A "resume guidance" action (Google Maps AA style) — see Decision 4.
- Pan on `DetailsScreen`/`MapScreen` "show location" flows (already pannable via `MapController` where applicable).

## Decisions

### D1: Shared `MapPanHandler` class (new file `MapPanHandler.kt`)

Both screens need identical pan logic: pan-mode enter/exit, scroll→viewport, scale→zoom. Extract into one class implementing `PanModeListener`:

```kotlin
class MapPanHandler(
    private val mapRenderer: AutoMapRenderer,
    private val autoZoomController: AutoZoomController,
    private val surfaceSize: () -> Pair<Int, Int>   // width, height (screen fields)
) : PanModeListener {
    var panning: Boolean = false; private set
    override fun onPanModeChanged(panMode: Boolean) { ... }
    fun onScroll(distanceX: Float, distanceY: Float) { ... }
    fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) { ... }
}
```

- **Enter** (`panMode == true`): `panning = true`; `autoZoomController.suspend()`; `mapRenderer.setViewport(vp.lat, vp.lon, vp.zoom, vp.angle, vp.zoom.toDouble())` — the MapScreen pattern; `setViewport` flips `followMode` off.
- **Exit** (`panMode == false`): `panning = false`; `mapRenderer.reengageFollow()` (smooth, no snap).
- **onScroll**: gate on `panning`; `ProjectionUtils.dragDeltaToNewCenterRotated(dx, dy, vp.angle, vp.zoom, w, h, vp.lat, vp.lon, mapRenderer.projectionDpi)` → `setViewport`. Throttled `DiagnosticsLog` mirror (MapScreen's `GESTURE_LOG_INTERVAL_MS` pattern) so on-device verification can see pan events in the file-backed log.
- **onScale**: gate on `panning`; jitter threshold (`abs(scaleFactor - 1f) < 0.01`); `mapRenderer.zoomStep(scaleFactor)`; negative focus → screen center; `ProjectionUtils.zoomAtCursor(...)` → `setViewport(..., newFraction)`.

Alternatives: (a) duplicate the ~60 lines in both screens — rejected, drift risk and untestable; (b) put the logic on `AutoMapRenderer` — rejected, the renderer is surface/rendering-focused and already large; the handler composes renderer + zoom controller, matching the existing `AutoZoomController` composition style.

### D2: `PanModeListener` on the `NavigationTemplate` via the factories

`NavigationTemplateFactory.buildNavigationTemplate` and `buildFullScreenTemplate` gain `panModeListener: PanModeListener? = null` → `builder.setPanModeListener(...)`. Both screens pass their `MapPanHandler` (it implements `PanModeListener`). `setPanModeListener` is `RequiresCarApi(2)` — car-app 1.7.0 (pinned) supports it; no version gate.

### D3: `Action.PAN` in both map action strips

- `NavigationScreenActions.navigationMapActionStrip`: `Action.PAN` + route-description action (PAN leftmost).
- Free-driving strip (built inline in `FreeDrivingScreen.buildTemplate`): `Action.PAN` + exit action (PAN leftmost).

`Action.PAN` is a built-in icon action — satisfies the car-app 1.7.0 map-strip constraint (icon-only). The host renders the pan affordance only when the listener is registered AND the PAN action is present (docs: "If the app does not include the PAN button in the map ActionStrip, the host will not enter pan mode").

### D4: Re-engage follow on pan exit (not a "resume" button)

`onPanModeChanged(false)` → `reengageFollow()`: the extrapolation loop eases the display back to the fix (no snap). Alternative considered: stay panned with an explicit "resume guidance" action (Google Maps AA). Rejected: car-app has no built-in resume affordance; a custom action would compete with PAN/route-list/exit in a strip capped at 4; the PAN toggle semantics (enter/exit) map naturally to follow off/on. Revisit if on-device testing shows the snap-back is jarring.

### D5: Gate the GPS viewport commit on `!panning` — the critical difference between the two screens

Both screens' GPS handlers commit `setViewport(...)` + `reengageFollow()` per fix. While panning this must NOT run, or every fix re-engages follow and yanks the map back.

- **NavigationScreen**: gate the angle computation on `!panning` (`if (!panning && !navNorthUp && bearing >= 0)`). With auto-zoom suspended (`onSpeed` returns null in-band) and angle null, the existing `if (angle != null || zoom != null)` commit block is skipped naturally — no extra gate needed.
- **FreeDrivingScreen**: heading-up is ALWAYS computed (`headingAngleRadians(bearing)`), so `angle != null` is almost always true and the commit block would run. It needs an explicit `!panning` gate on the commit block (or on the angle computation). This is the one place the two screens differ.

`setGpsMarker` already handles marker-only updates when `followMode` is off — no change there.

### D6: `SurfaceCallback` delegation

Both screens' `registerSurfaceCallback` add:

```kotlin
override fun onScroll(distanceX: Float, distanceY: Float) = panHandler.onScroll(distanceX, distanceY)
override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) = panHandler.onScale(focusX, focusY, scaleFactor)
```

The handler reads `surfaceWidth`/`surfaceHeight` via the `surfaceSize` lambda (fields updated in `onSurfaceAvailable`).

## Risks / Trade-offs

- **Host may not forward gestures during active turn-by-turn** (host-dependent; docs say pan mode works with `NavigationTemplate`, but AAOS/AA hosts vary) → on-device verification task (emulator/head unit); the PAN button still toggles pan mode and rotary/touchpad input is the documented path.
- **ETA card / instruction panel occlusion during pan** → docs say the host hides other UI in pan mode; if a host does not, pan still works within the visible area (the street-name label already respects visible/stable bounds).
- **Auto-zoom re-engage during pan** — a speed-band change while panning clears the suspension, but the gated commit block drops the zoom until pan exit → no jump during pan; zoom applies on the next fix after exit. Acceptable.
- **Pan exit snap-back** — `reengageFollow` is smooth, but if the vehicle moved far while panned, the ease-back is a long glide → mitigation: the extrapolation loop's correction is per-frame and bounded; revisit with a "resume" action (D4) if on-device testing shows it is disorienting.
- **Strip crowding** — navigation strip: PAN + route-list; free driving: PAN + exit. Both within the 4-action cap. PAN leftmost is a guess; cosmetic, tunable on device.

## Migration Plan

Feature addition — no data migration. Rollback: revert the change; removing the PAN action + listener restores the pre-change behavior (no pan affordance, follow always on).

## Open Questions

- Host pan-mode behavior on real AAOS/AA hardware (gesture forwarding during turn-by-turn, ETA-card hiding) — deferred to the on-device verification task; does not change specs or approach.
- PAN button position in the strips (leftmost vs rightmost) — cosmetic, tunable on device.
