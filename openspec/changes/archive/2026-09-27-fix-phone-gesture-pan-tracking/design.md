# Design — Fix Phone Gesture Pan Tracking

References: specs `map-pan-zoom` — "Touch-based pan", `canvas-overrun` — "Sub-region blit for pan", `map-render` — "Render map to Compose canvas"/"Safe bitmap lifecycle for sub-region blit"/"Forced overlay renders are never dropped", `render-performance` — "Pan hot path stays off the frame budget", `gps-location-marker` — "Marker projects against displayed bitmap viewport" (all in this change). Guidelines: `guidelines/MapRendering.md` §1, §1.1, §12, §13, Known Pitfalls.

## Context — the model we adopt: one displayed center

Today the phone has two unrelated ways to make the map move without a render:

```
follow mode:   FollowPrediction.displayOffsetPx(anchorCenter(displayPos), frameVp)  → followOffsetX/Y → draw
browse pan:    nothing (updateCenter + renderMap per event → content moves only when a render lands)
```

The pan path has no display side at all, so small pans cannot move. We unify on the model follow mode already uses, expressed once:

```
displayedCenter   (follow: anchorCenter(displayPos, resolvedAnchor)
                   pan:    live panned center)
                        │
                        ▼
        displayOffset = FollowPrediction.displayOffsetPx(displayedCenter, frameViewport, …, anchor = center)
                        │  rotated delta, clamped to the overrun margin
        ┌───────────────┴────────────────┐
        ▼                                ▼
  draw the overrun frame at −offset   overlays shift by the same offset
```

The frame delivered to the UI stays the **overrun-sized front buffer with its own viewport** (as the follow path already requires since `c7e5bc1`); nothing is copied or re-rendered per event. A pan inside the margin is therefore pure draw work. When the offset saturates at the margin the window stops, the live center is committed and one throttled re-render is requested — the same clamp-and-recenter rule follow mode uses.

## D1 — Where the window shift is applied: draw-side display offset

**Chosen:** the screen derives the display offset for the frame on screen and applies it in `drawFrontFrame` (the parameter pair that `followOffsetX/Y` already occupies). The map-anchored overlay gets the same offset.

- No bitmap work per pan event (the pan can run at touch rate on a 120 Hz panel).
- The frame in hand is real rendered content — the shifted window is pixel-exact, not a scaled placeholder.
- One value, derived from the frame on screen, cannot describe a different frame.

**Alternative A — restore the pre-`c7e5bc1` shifted sub-region blit** (`Bitmap.createBitmap` region + `Canvas` compose + `_frameFlow` emission per pan event). Works, but allocates two bitmaps and copies the visible area per touch event (120–240/s) on the thread that also drives the gesture; that is the cost profile we are removing, and the tile cache/`emitFrame` reuse path already exists to avoid exactly these copies.

**Alternative B — accumulate `gesturePan` in `onPan` and let the Canvas `graphicsLayer` translate** (the mechanism `onCentroidPan` uses). Instant, allocation-free, but the gesture layer transform is already owned by the zoom/rotation handoff (`zoomAnimScale`, `rotationZ`, `rotationHold`), and a layer translate moves the drawn map without moving the overlay basis, so the marker would need a second, differently-derived correction. It also cannot be composed with the overrun-margin clamp (the layer does not know the margin). Rejected as the primary mechanism: it duplicates the offset in a second, unclamped coordinate space.

## D2 — Ownership of the live pan center: display-only screen state

**Chosen:** `onPan` keeps the panned center in local screen state (`panDisplayLat/Lon`, `remember { mutableStateOf }`), exactly like `gestureZoom`/`gesturePan`/`zoomAnimScale`, and commits it to the ViewModel (`updateCenter`) when a render is requested and at gesture end (plus `saveViewport()`).

- The pan hot path no longer copies the ~120-field `MapCanvasUiState` and no longer recomposes the 2635-line screen per event.
- Only the draw/overlay layer reads the live center, so invalidation stays inside the draw scope.
- The committed viewport (persistence, POIs, re-center button, route panel) sees a coherent value, not a per-event stream.

**Alternative A — status quo (ViewModel state per event).** Keeps one source of truth, but the copy + `collectAsState` invalidation per touch event is defect 2 of the proposal.

**Alternative B — a dedicated `MutableStateFlow<DisplayCenter>` in the ViewModel**, collected only by the draw. Avoids the giant state copy, but adds a second source of truth that must be kept coherent with `viewport` (which one wins during a forced render?), and it makes the "display-only" nature of the value less obvious than the existing local-state pattern. Rejected for this change; can be revisited if other consumers ever need the live center.

## D3 — Covered render requests: coverage predicate, no emission

**Chosen:** `MapRenderer.submitDebounced`'s covered branch keeps the geometry (rotated delta + margin + `BLIT_COVER_SLACK_PX`), drops a pending non-forced render and returns — no `emitFrame`, no bitmap work. The rendering side only decides *whether a render is needed*; the display side owns *where the window sits*.

**Alternative A — publish the requested center with the frame** (`FrameState.displayCenter`) so the screen derives the offset from the published frame instead of from `state.renderViewport` + live center. Strictly satisfies "the offset is published with the frame", but the emission itself becomes a new `FrameState` + a `_uiState` write per pan event (the frame collector publishes `renderedBitmap`/`renderViewport`) — i.e. it re-introduces the per-event state churn of D2. Rejected: with the offset derived on the UI thread from the frame that is on screen, frame/offset mismatch is impossible without cross-thread state.

**Alternative B — the renderer computes and publishes the pan offset itself** (it has `screenWidth/Height`, `dpi`, the frame center and the requested center). Same per-event emission cost as A, and it splits the offset computation across two modules while the draw side still needs it. Rejected.

## D4 — Offset math: reuse `FollowPrediction.displayOffsetPx`

**Chosen:** call the existing pure helper with the default (center) anchor — its documented degradation is exactly the pan case ("with the default center anchor the drift equals the absolute offset"). It is already rotation-aware, margin-clamped, and covered by tests.

**Alternative A — new helper in `core`** (`OverrunWindow.shiftPx`). Clean naming, but duplicates the math until follow mode is migrated — two implementations of the same clamp is precisely the class of bug this project keeps hitting (renderer vs display divergence).

**Alternative B — move/rename `displayOffsetPx` to `core/OverrunWindow.kt` and use it from both paths.** The right end state; deferred because the follow pipeline is being edited by the in-flight change `fix-phone-follow-blit-anchor-mismatch` (same call site). Recorded in `TODO.md` by this change (task 6.2). The renderer's covered check SHALL be expressed through the same helper (plus the 32 px slack) so coverage and clamp cannot disagree.

## D5 — Gesture-start side effects and hot-path logging

**Chosen:** `disengageFollowMode()` and `attributionInteractionTick++` run once per gesture (first pan or centroid event), not per event. The per-event `Log.d` calls in `MapRenderer.requestRender`/`submitDebounced` are removed from the hot path and gated behind a single `DEBUG_RENDER_HOT_PATH` constant (false by default). Gesture-level diagnostics (one line per gesture end) stay.

**Alternative — keep the logs and filter logcat on device.** Rejected: two formatted log lines per touch event on the UI thread is measurable jank, and the same information is available once per gesture.

## D6 — Renderer housekeeping

**Chosen:** fix the `emitFrame` "unchanged frame" reuse check to compare the emitted bitmap's own dimensions (it compares `screenWidth/Height`, which no longer identifies the emitted frame) and remove the now-unreachable `crop`/`extractCenterRegion` path once no caller passes `crop = true`.

**Alternative — leave both.** Rejected: the reuse check can re-emit a stale crop after a canvas-size change (fold/orientation), and unreachable bitmap code is a trap for the next change. `render-performance` — "Frame emission only on change" still holds (no new copy for an unchanged frame).

## D7 — Marker/overlay offset

**Chosen:** for the non-follow display, the map-anchored overlay is shifted by the same `displayOffsetX/Y` the frame is drawn with (a translation of the overlay layer — no scaling, so marker/accuracy sizes are unaffected). In follow mode nothing changes: the overlay projection already uses `anchorCenter(displayPos, resolvedAnchor)`, which includes the drift, and translating there would double-apply.

**Alternative — derive a "displayed viewport" (geo center shifted by the clamped offset via `screenToGeoRotated`) and pass it as the overlay viewport for both modes.** More uniform and removes the double-application hazard entirely, but it rewrites the verified follow projection, and the in-flight follow change touches the same call site. Deferred to `TODO.md` with the same rationale as D4.

## D8 — One frame source for the coverage decision (follow-up fix, 2026-09-26)

The device report (pan moves nothing at all in browse) is the failure mode this design had listed as a risk: the display and the renderer can disagree about whether the frame in hand can serve the requested window, and when they disagree in the "renderer says covered, display cannot shift" direction the map freezes for the whole gesture — and the gesture-end render is dropped for the same reason, so it does not recover.

**Chosen:** the coverage predicate is evaluated against the frame state the **display** holds — dimensions *and* viewport — and a window the display cannot serve is reported as **not covered**. Concretely: the state that drives the predicate (frame bitmap dimensions, frame viewport, canvas size) SHALL be the same frame state the screen derives the offset from, and the "can this window be shifted at all" test on the display side (overrun margin present, frame viewport known) is part of the predicate rather than a private screen-side condition. The screen keeps its throttled commit-plus-render fallback so the finger keeps moving the map whenever the window cannot be served.

**Alternative A — keep the two derivations and make them "agree by construction" by sharing one helper (today's shape).** They already share `FollowPrediction.displayOffsetPx` and the slack constant; that is why the unit tests pass while the device freezes. Sharing the *math* is not sharing the *frame*: the two sides can still read different frames (emitted copy vs internal front buffer, stale viewport, dimension change). Rejected as insufficient.

**Alternative B — let the display ask: the screen, not the renderer, decides whether a render is needed.** One decision point, no predicate duplication. Rejected because the renderer must keep the request path for other callers (follow re-anchor, forced overlay renders, `renderFollowFrameAt`) and because it would move the request cadence into the composable, against the existing debounce ownership (D3).

**Alternative C — unify on one derived displayed viewport (the deferred alternative in D4/D7).** The structurally clean end state: frame, offset, coverage and overlay projection all derive from one "displayed viewport" value. Larger change, rewrites the verified follow path; still deferred (`TODO.md` §78), and this fix must not depend on it.

## D9 — Bounded pan diagnostics (follow-up fix, 2026-09-26)

**Chosen:** the pan path emits at most one gated line per gesture (window served / not served, committed versus displayed center at lift) plus one gated line per unservable-window rejection, under the existing `MapCanvasScreen` tag and the existing debug gate. Rationale: the investigation of this defect had to reason from code alone because `onPan` logs nothing on the single-finger path while `onCentroidPan` logs — the two stuck states (renderer dropped the request / display applied a zero offset) are indistinguishable in today's logcat.

**Alternative — log per pan event.** Rejected: violates `render-performance` — "Pan hot path stays off the frame budget"; 120 Hz × two formatted lines is exactly the work this change removed.

**Alternative — no new logs, diagnose from existing renderer lines only.** Rejected: the renderer's lines are also gated and only appear for renders that ran, so an entirely-skipped request is invisible; that is the case being diagnosed.

## Threading model and lifecycle

- Gesture callbacks (`onPan`), the frame loop (`withFrameNanos`) and the draw run on the **main thread**; the display offset is a per-frame derivation from already-collected state plus the live center, so frame, offset and overlay basis are always the same generation — no cross-thread publication needed.
- Render jobs stay on the renderer scope (`Dispatchers.Default`, created in `MapCanvasViewModel`); the change does not add or move dispatchers and does not change the double-buffer/epoch rules.
- Lifecycle: the live center is screen-local state and dies with the screen; the committed viewport is written at gesture end (and on render requests), so `viewport-persist` and `onPause`/`ON_PAUSE` behavior is unchanged. Leaving the screen mid-gesture loses only the uncommitted remainder of the gesture, same as today's unrendered pan.
- The renderer's covered predicate stays side-effect free apart from dropping a pending non-forced render (existing rule); no new locking — `bufferLock` is not entered by the covered path any more.

## Files

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — pan callback, gesture-start gating, live center state, per-frame offset derivation, draw call, overlay translation, gesture-end commit/persist/recenter, throttled render request on clamp.
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — covered branch → predicate, helper use, logging gate, `emitFrame` reuse check, dead-path removal.- `app/src/main/java/com/naviveylin/ui/map/MapGestures.kt` — pan reporting without re-reporting the drag-threshold jump.
- `core/src/main/java/com/naviveylin/core/FollowPrediction.kt` — KDoc only.
- `app/src/test/java/com/naviveylin/ui/map/*` — new/extended tests (below).
- `guidelines/MapRendering.md`, `TODO.md` — documentation and follow-up debt.

## Verification

- Unit / Robolectric: new `MapPanDisplayWindowTest` (offset = pan delta inside the margin, rotation-aware, clamped at the margin, zero when the display center equals the frame center), `MapRendererBlitTest` (covered pan schedules no render **and emits no frame**; forced renders survive; emission reuse across a dimension change), `MapGestureComposeTest` (single-finger pan reports deltas, one render request at gesture end, none mid-gesture), marker/content consistency test for the pan offset. Existing suites that must stay green: `FollowAnchorFramingTest`, `MapCanvasViewModelFollowModeTest`, `MapCanvasViewModelSingleFollowCenterTest`, `RotationHandoffTest`, `MapCanvasZoomAnimationRulesTest`, `TileCacheRenderTest`, `MapRendererRotatedRenderTest`.
- **Follow-up fix (2026-09-26):** new `MapCanvasPanWindowTest` — a screen-level drag through the window path (non-zero derived offset directed against the drag, the frame drawn at that offset, an unservable window falling back to a throttled render, and no state in which the renderer reports covered while the display applies a zero offset). `MapRendererBlitTest` gains the unservable-window case (predicate must report not covered).
- Build: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`, then the full `./gradlew test`.
- On-device (phone, `adb logcat -s NaviVeylin`, `MapRenderer`/`MapCanvasScreen` tags): a slow full-screen pan inside the margin produces **no** render lines; a drag beyond the margin produces renders at the debounce cadence only; the marker stays glued to the map content (also while the window is saturated); a small pan then a zoom shows no jump; rotated (heading-up) pan follows the finger; the viewport is persisted on lift (relaunch shows the panned position); pinch/rotation/zoom behavior is unchanged; CPU/main-thread profile during a drag shows no per-event logging or UI-state copy.
- **Follow-up fix (2026-09-26):** the on-device check runs **after** tasks 7.1–7.7 and repeats the drag checks above plus the reported case (browse, single-finger drag moves the content on the first gesture, no frozen gesture). Task 5.4 is superseded by task 7.8; this change does not archive until that device check has run.
- Android Auto regression: no `:auto` file is modified; `./gradlew :auto:testDebugUnitTest` (incl. `MapPanHandlerTest`) stays green.

## Risks

- **Double-applied offset** (draw + overlay or pan + follow): guarded by the marker/content consistency test and by keeping follow's projection path untouched.
- **Coverage vs clamp disagreement** (map sticks at an edge): both sides use the same helper and the same slack constant (D4); the existing `MapRendererBlitTest` boundary test is extended. **Materialized 2026-09-26 as a dead pan** — sharing the math was not enough, because the two sides can still read different frames; the follow-up fix (D8) makes the predicate read the display's frame and treat an unservable window as not covered.
- **Saturated window with uncovered pixels**: the clamp keeps the drawn window inside the buffer by construction; the `BLIT_COVER_SLACK_PX` reserve is preserved so a render is requested before the buffer truly ends.
- **Pan while follow is engaged**: disengagement happens once, before the first offset derivation, so the framing base is the displayed frame (existing behavior, now once per gesture instead of per event).
- **`emitFrame` reuse fix**: a stricter condition can only cause an extra emission on a dimension change; `render-performance` "emission only on change" is re-verified by the existing tests.
- **Dead-zone change in `MapGestures`**: reporting from the threshold without an extra step alters per-event deltas by < 12 px once per gesture; verified by the gesture tests and on device.
- **A fix that only patches the observed instance** (2026-09-26): the reported freeze has more than one candidate cause (dimension source, viewport staleness, renderer-side frame state), and a targeted patch could leave the next variant in place. Guarded by making the *agreement* the requirement (D8) and by the new screen-level test, which must fail before the fix and pass after it.
