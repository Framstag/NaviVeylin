# Design — Fix Phone Follow Blit Anchor Mismatch

References: spec `smooth-follow` — "Single resolved anchor across render, blit and marker" (this change); the phone follow pipeline, `gps-location-marker` — "Marker projects against displayed bitmap viewport" (in-flight `fix-phone-vehicle-anchor-framing`).

## Context

The follow pipeline has three stages that each need the anchor screen fraction:

```
preset BOTTOM_CENTER (0.5, 0.9)
        │
        ▼
resolveAnchorFraction()  ← collision-remap vs. overlay insets (VM publishResolvedAnchor)
        │
        ▼
  resolved (0.5, ~0.76)   published as MapCanvasUiState.resolvedAnchor
   │           │           │
   ▼           ▼           ▼
render target    blit offset     marker projection
followRenderTarget displayOffsetPx markerViewport
VM L483      MAPCANVASSCREEN L515   MAPCANVASSCREEN L1166
resolved ✓       RAW preset ✗         resolved ✓
```

The frame is rendered anchor-centered on the **resolved** fraction (vehicle at the anchor inside the frame), the marker projects the displayed position against the **resolved** anchor center, but the blit offset is computed against the **raw preset** (`ui.activeFollowAnchor`). The blit places the displayed position's content at the raw anchor while the marker draws at the resolved anchor — the marker and the road drift apart by `(raw − resolved)` along the travel axis (in heading-up that is exactly the reported "vehicle sits ahead of the track in the driving direction"). The same systematic offset keeps the clamped flag set, driving the 500 ms re-render path for the whole drive.

`FollowAnchorFramingTest` already pins the contract — "resolved anchor keeps the marker on the content with overlays" calls `displayOffsetPx` with the resolved fractions and asserts the marker lands on the content, unclamped. The production call site is the only place that violates it. Android Auto passes its resolved value (`followAnchor` = `clampAnchorOutOfPane` result), so AA is correct.

## D1 — Compute the blit offset against the resolved anchor

`MapCanvasScreen.kt` follow display block (~L515): pass `state.resolvedAnchor.fx/fy` to `FollowPrediction.displayOffsetPx(...)` instead of `ui.activeFollowAnchor.fx/fy` — the same resolved fractions `followRenderTarget` (VM) and the marker projection (screen) already use.

The offset formula is position-independent of WHICH anchor is passed (`drift = rot(display − frame_center) − anchor`), so this is a pure constant correction; with matching anchors the existing invariants hold exactly: aligned display → zero offset; drift ≤ overrun margin → marker glued to content at the anchor; no render churn.

Alternative A (keep the raw anchor, resolve the frame render back to raw): would place the vehicle at the raw preset inside the frame — i.e. under the routing status card or widget column, undoing the collision-remap work "Vehicle is now correctly shown above the routing state view". Rejected: the whole point of `resolveAnchorFraction` is that the frame keeps the vehicle in the *visible* map area, and the visible-area insets are the same values the blit margin is validated against.
Alternative B (remove the anchor from the offset call entirely, offset = pure drift): changes `displayOffsetPx` semantics and its callers (AA) for no benefit — the anchor term is what makes "aligned display → zero offset" true. Rejected: AA already shares this function and is correct; keep one function, one consistent anchor.

## D2 — Regression test pins the raw-vs-resolved failure mode

Add to `FollowAnchorFramingTest` (plain JUnit, no JNI — safe for the classloader rules in AGENTS.md):

1. `blit against the raw preset while frame and marker use the resolved anchor leaves the marker off the content`: simulate the production geometry (turn card top 0.19H, routing status bottom 0.18H, widget column right 0.07W, BOTTOM_CENTER preset, drift within margin): render target and marker use `resolved.fx/fy`; the blit uses `anchor.fx/fy` (raw). Assert the marker's projected screen position differs from the content position by the anchor delta, i.e. the defect.
2. Extend the existing resolved-consistency test (or assert the same geometry with resolved everywhere): marker on content, offset unclamped — the fixed behavior.

Existing tests already cover the aligned case for every preset and rotation; no VM or core changes, so `FollowPredictionTest`/`VehicleAnchorTest`/`AnchorVisibleAreaTest` stay untouched.

## Threading and lifecycle

All three anchor uses are synchronous reads of already-published state: `state.resolvedAnchor` is updated by `publishResolvedAnchor()` (VM, main thread) when the preset, canvas size or overlay insets change; the display loop (Choreographer/`withFrameNanos` on the UI thread, `MapCanvasScreen`) reads it per frame. No new state, no new dispatcher, no lifecycle change. The change is a constant swap of the anchor a per-frame read uses.

## Verification

- Unit: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FollowAnchorFramingTest"` and full `./gradlew test`.
- Build: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`.
- On-device (phone): drive in navigation mode with bottom-center placement; `adb logcat -s NaviVeylin` follow diagnostics (`fix/pred/disp/off/clamped`): `off` stays small and `clamped` rare while driving; the marker stays on the route track; no 500 ms render churn (`clamped` no longer perpetually set).
- Guidelines: add one sentence to `guidelines/MapRendering.md` §1.1 that the anchor is applied once, identically, in render target, blit offset and marker projection.

## Risk

- Low: single constant swap; the pure function and its tests are unchanged, and the test suite already asserts the resolved-consistent invariants.
- Regression risk if someone later changes `publishResolvedAnchor` to emit different fractions per stage: the D2 test (raw ≠ resolved) will start failing — intentional guard.
