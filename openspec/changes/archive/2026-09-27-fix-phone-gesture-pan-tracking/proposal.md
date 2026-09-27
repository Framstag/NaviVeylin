# Fix Phone Gesture Pan Tracking

## Why

On-device report (phone, browse/free-drive): panning feels slow and laggy even for small pans that should be served entirely from the overrun buffer without a native render.

Two independent defects, both in the phone pan path:

1. **Single-finger pan has no per-event visual tracking.** `onPan` (`MapCanvasScreen.kt`) only calls `viewModel.updateCenter()` + `viewModel.renderMap()` per touch event — it never accumulates a visual offset (`gesturePan` is written only by `onCentroidPan`). The displayed bitmap therefore only moves when a native render lands: 50 ms debounce + tile/native render + a one-frame jump. Worse, a pan **within** the overrun buffer is not tracked at all: `MapRenderer.trySubRegionBlit` computes the covered shift but emits the *same* front-buffer bitmap with the *buffer's own* center (`MapRenderer.kt` covered branch), and `submitDebounced` then drops the pending render. The shift that would move the content (`followOffsetX/Y`) is produced only by the follow-mode prediction loop and is reset to 0 whenever follow is off. Result: small pans move nothing (the map appears frozen, then jumps later), large pans move only at render cadence.

   Regression source: commit `81311eb` blitted a shifted sub-region out of the overrun buffer; commit `c7e5bc1` ("Smooth scrolling & search fix") replaced that with the no-shift emission + follow-display-offset model. That model assumed a display loop that offsets the window inside the overrun margin — true in follow mode only, so browse/free-form panning lost its blit. This contradicts `map-pan-zoom` ("During a pan gesture, the system SHALL use sub-region blit from the overrun buffer") and `canvas-overrun` ("Small pan uses sub-region blit … no native render call is made").

   A side effect: an untracked pan still advanced `viewport.centerLat/Lon` and the renderer's target center, so a later render jumps by the accumulated invisible distance; a pure pan never requests a render at gesture end at all (`onRenderRequested` only commits multi-touch changes).

2. **The pan hot path saturates the main thread.** Per touch event (120–240 Hz on modern panels) the screen writes the ~120-field `MapCanvasUiState` via `updateCenter` (full-screen recomposition of the 2635-line screen composable), calls `renderMap()` (two `Log.d` calls with double formatting, a `bufferLock` acquisition and the blit geometry), and bumps `attributionInteractionTick`. Pan latency is therefore bounded by recomposition + logging work, not by the gesture.

Fix 1 and fix 3 of the analysis: the display window shifts within the overrun margin at gesture rate with **no** native render inside the margin, and the per-event main-thread work is removed.

### Follow-up folded in 2026-09-26 — pan is dead on device

On-device report (phone, browse): after this change landed as `57c5141`, single-finger pan no longer moves the map **at all** — the gesture produces no visible change, not a slow or laggy one. The unit suite was green, but the change's on-device verification (tasks 5.4/5.5) was never run, so nothing in the pipeline could have caught it before a user did.

3. **The display window and the renderer's coverage predicate can disagree into a freeze.** The pan now moves the content only if three places agree: the gesture's live center, the per-frame display window derived in `MapCanvasScreen`, and `MapRenderer.overrunWindowCovers`. The renderer decides from its **own** frame state (`frontBuffer` plus its `screenWidth/Height`), the display from the **emitted** frame the UI holds (`renderedBitmap` plus its canvas size). When the display cannot serve the window (no usable overrun frame in hand) while the renderer still considers the requested center covered, the renderer schedules no render *and* the display applies a zero offset: the map is frozen for the whole gesture. Both sides then also drop the gesture-end render (`requestPanRecenter`/`onRenderRequested` render only for a clamped or absent window, and the renderer's covered branch discards the request), so the pan does not recover. This is the risk the design note in this change already listed ("Coverage vs clamp disagreement (map sticks at an edge)") and the failure `guidelines/MapRendering.md` Known Pitfall #12b describes.

The mechanism is not yet confirmed on a device (no device was attached during the analysis; `adb devices` empty), so the fix starts with the diagnosis task 7.1: a pan that logs `renderMap` but no landed frame means the renderer is stuck on "covered"; a pan that logs nothing means the screen considered the window covered and drew it at a zero offset. Tasks 7.2–7.6 then remove the disagreement *class*, not just the observed instance, and 7.7–7.8 close the verification gap this change left open (task 5.4 is superseded by 7.8, which must run before archive).

## What Changes

- **Display window instead of per-event renders (phone).** The displayed frame is the overrun-sized front buffer; the screen draws it with a *display offset* derived from the displayed center against the frame's own viewport, clamped to the overrun margin, reusing the existing pure helper `FollowPrediction.displayOffsetPx` (default anchor = pure center delta, already rotation-aware and margin-clamped and unit-tested). A pan inside the margin is then tracked every frame with zero render calls; the frame in hand stays sharp (it is real rendered content, not a scaled placeholder).
- **Live pan center as display-only state.** The gesture keeps the panned center in local screen state (same precedent as `gestureZoom`/`gesturePan`/`zoomAnimScale`), not in the ViewModel state: no `MapCanvasUiState` copy, no full-screen recomposition per event. The center is committed to the ViewModel at gesture end and whenever a render is requested.
- **Clamp-and-recenter rule** (mirrors follow mode): when the offset reaches the overrun margin the displayed window saturates, the live center is committed and one render is requested at a throttled cadence. The renderer's covered branch becomes a pure coverage predicate (no bitmap work, no emission) and still never drops a pending forced render.
- **Gesture-end commit**: single-finger pan commits the center, persists the viewport (spec `viewport-persist` — the pan no longer triggers a render that would have persisted it) and requests a render only if the window is saturated or the frame is invalid/absent.
- **Marker/overlay consistency**: map-anchored overlays shift by the same display offset the frame is drawn with (non-follow display), so the marker stays glued to the map content it rides (`gps-location-marker`). Follow mode keeps its anchor-center projection (the offset is already carried there).
- **Hot-path cleanup**: no per-event `renderMap()`, no per-event attribution tick, no per-event `Log.d` in `MapRenderer.requestRender`/`submitDebounced` (gated behind a debug flag); gesture-start side effects (`disengageFollowMode`, attribution tick) run once per gesture.
- **Renderer housekeeping** surfaced by the analysis: `emitFrame`'s "unchanged frame" reuse check compares against the emitted bitmap's own dimensions, and the now-unused `crop`/`extractCenterRegion` path is removed.
- **One frame source for coverage and offset (fix, 2026-09-26).** The display offset and the renderer's coverage decision SHALL describe the same frame: the predicate is evaluated against the frame state the display actually holds (dimensions *and* viewport), so the two sides cannot disagree into a freeze.
- **An unservable window counts as uncovered (fix, 2026-09-26).** When the frame in hand cannot serve the requested window (missing frame, frame without an overrun margin, viewport mismatch), the renderer SHALL render instead of relying on a shift that will not happen, and the pan SHALL fall back to the throttled commit-plus-render so the content resumes tracking the finger.
- **Bounded pan diagnostics (fix, 2026-09-26).** At most one gated diagnostic line per gesture (window served / not served, committed vs displayed center at lift) plus one line per unservable-window rejection, so a dead pan is diagnosable from `adb logcat` alone. No per-event logging: the hot-path rule is unchanged.
- **Screen-level verification of the seam (fix, 2026-09-26).** A test drives a real drag through the screen's window path and asserts the derived offset is non-zero, the frame is drawn offset, and no freeze state is reachable — the glue that the pure-function tests of the first pass left uncovered.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `map-pan-zoom` (was: "Touch-based pan", "Pan map east/south/rotated", "Pan beyond overrun buffer"): the pan tracks the finger via the overrun window; the committed viewport center is updated at gesture end and on render requests instead of on every event; no native render while the window stays inside the margin; persistence on finger lift. Follow-up: the requirement gains the no-invisible-pan guarantee — the content SHALL track the finger on every display frame, including when the frame in hand cannot serve the window (throttled fallback render), plus a screen-level acceptance scenario.
- `canvas-overrun` (was: "Sub-region blit for pan"): the pan is served by shifting the displayed window inside the overrun buffer instead of copying a sub-region bitmap per event; the frame delivered to the UI is the overrun buffer plus its display offset. Follow-up: the requirement gains the agreement rule — display offset and coverage decision SHALL be derived from the same frame, and an unservable window SHALL count as not covered.
- `map-render` (was: "Render map to Compose canvas", "Safe bitmap lifecycle for sub-region blit", "Forced overlay renders are never dropped by the blit fast-path"): the front buffer is delivered as the overrun frame with the offset that positions it; no per-event region bitmap exists any more; the covered branch stays a coverage predicate that keeps forced renders.
- `render-performance` (new requirement): the pan gesture hot path allocates nothing, copies no UI state and logs nothing per event. Follow-up: the requirement gains the bounded-diagnostics carve-out (at most one gated line per gesture, plus one per unservable-window rejection).
- `gps-location-marker` (was: "Marker projects against displayed bitmap viewport"): the blit/display offset is applied to the overlays together with the frame, including for a pan window that is clamped at the margin.

`zoom-transition-scaling`, `double-buffering`, `native-tile-data-cache`, `viewport-persist`, `render-mode-switch`: unchanged behavior (listed because the touched files implement them).

### Unchanged but touched (must not regress)

- `smooth-follow`: the follow display path keeps `FollowPrediction.displayOffsetPx` with the resolved anchor and its 500 ms clamp re-anchor; the pan path reuses the same helper with the default center anchor. Verified by `FollowAnchorFramingTest`, `MapCanvasViewModelFollowModeTest`, `MapCanvasViewModelSingleFollowCenterTest`.
- `auto-map-interaction` / `auto-map-renderer`: Android Auto is **out of scope** — `MapPanHandler`/`AutoMapRenderer` publish the displayed frame's own center and already project overlays against it; no `:auto` file is modified by this change.

## Impact

Phone (`mobile` flavor) only; no settings, persistence format, JNI or native changes.

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — single-finger pan callback (live center, gesture-start-only side effects, no per-event render), display offset derivation in the frame loop, draw call, marker overlay translation, gesture-end commit/persist.
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — `submitDebounced` covered branch (coverage predicate only), `trySubRegionBlit` → offset/coverage helper use, `emitFrame` reuse check, removal of the dead `crop`/`extractCenterRegion` path, per-event logging gated.
- `app/src/main/java/com/naviveylin/ui/map/MapGestures.kt` — pan reporting starts at the drag threshold without re-reporting the threshold jump (small dead-zone/step removal).
- `core/src/main/java/com/naviveylin/core/FollowPrediction.kt` — KDoc only: `displayOffsetPx` with the default (center) anchor is documented as the general overrun-window shift used by pan and follow. No behavior change.
- `app/src/test/java/com/naviveylin/ui/map/` — new offset/window tests, `MapRendererBlitTest` extension, `MapGestureComposeTest` pan-tracking test, marker/content consistency test. Follow-up: new `MapCanvasPanWindowTest` (screen-level drag through the window path: non-zero derived offset, offset applied to the draw, unservable window falls back to a render, no freeze state).
- `guidelines/MapRendering.md` — §1 (frame delivery), §12 (emission), §13 (blits → overrun window), Known Pitfalls #1/#12 (an overrun-sized frame in the UI is now the contract; overlays project against the displayed viewport including the offset).

Additive/compatible: no API, manifest, asset or persistence change. Rollback: revert the commit together with the spec deltas; the renderer's covered branch is the only behavior that changes shape (no emission), and the pan tracking returns to render-driven movement.

## Open Questions

None — the two open decisions (draw-side offset vs renderer-side shifted crop; local display state vs ViewModel state per event) are resolved in `design.md` D1/D2 with alternatives; the follow-up fix decisions (one frame source for coverage and offset, bounded diagnostics) are resolved in `design.md` D8/D9.

Resolved 2026-09-26: the dead-pan follow-up was first drafted as a separate change (`fix-phone-pan-display-window`) and then **folded into this change** at the reviewer's direction — the defect is in this change's own design, and its unrun device verification (task 5.4) belongs to the same change as the fix. Task 5.4 is superseded by task 7.8.
