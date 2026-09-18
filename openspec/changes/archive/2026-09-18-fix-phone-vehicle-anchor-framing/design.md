## Context

See `proposal.md` — Why. Relevant current state:

- The phone follow pipeline applies the vehicle anchor at **draw time**: `MapCanvasViewModel` commits a vehicle-centered viewport (`prepareViewport(smoothedLat, smoothedLon, ...)`, `MapCanvasViewModel.kt` follow collector) and `MapCanvasScreen` shifts the drawn bitmap by `FollowPrediction.displayOffsetPx(...)` minus a second anchor offset (`MapCanvasScreen.kt` follow display block, ~lines 479-493).
- `FollowPrediction.displayOffsetPx` documents and implements the *drift-from-anchor* semantics: it assumes the displayed frame is already anchor-centered ("the bitmap already contains the vehicle at the anchor pixel"), and clamps to `(bitmap - canvas) / 2` — 0.10 of the surface at `MapRenderer.DEFAULT_CANVAS_OVERRUN = 1.2`.
- The required screen shift for a non-center anchor is up to `0.4 * surface` (the `0.1 / 0.9` preset columns/rows) — four times the clamp margin, so the offset saturates immediately: the drawn bitmap leaves the surface and the uncovered area is filled with `surfaceColor` (guidelines/MapRendering.md §1: "Both paths MUST ALWAYS deliver a screen-sized frame").
- Android Auto already implements the target model: `AutoMapRenderer.anchorCenterFor()` renders at the anchor center, `renderFrame()` blits only the drift, and the marker projects against the same frame viewport. It is the working reference.
- `guidelines/MapRendering.md` §2 (bitmap lifecycle) and §1 (render pipeline) constrain the fix: the frame that reaches Compose stays an independent screen-covering copy, and the frame's geo center is part of the emitted `RenderViewport`.
- `guidelines/Design.md` §4 (threading), §6 (rendering pipeline), §7 (follow mode & GPS) shape the approach; the change adds no new component or dispatcher.

## Goals / Non-Goals

**Goals:**

- One place applies the anchor: the render target. Blit offset and marker projection then need no anchor knowledge at all.
- The follow contract becomes testable without a device: given mag/DPI/angle/surface size, the committed render target is the anchor center of the vehicle, and the drawn offset is bounded by the overrun margin for every preset.
- Keep the existing durable behavior intact: the marker still rides the displayed frame, extrapolation and easing are unchanged, `center/center` reproduces today's framing exactly.

**Non-Goals:**

- Changing the 15-preset model, the picker UIs, the settings schema, or the per-mode (routing vs free-driving) selection rule.
- Changing Android Auto rendering — it is the reference implementation; only a parity check task is added.
- Changing the overrun factor (`DEFAULT_CANVAS_OVERRUN`), the tile path selection, or the native render pipeline.
- Introducing an inset-aware framing (compensating the navigation status bar or the turn overlay by shrinking the map area). The user picks the anchor; the overlay is their problem to avoid, and the preset grid already provides the needed positions.

## Decisions

### D1 — Apply the anchor to the render target, not to the blit

`MapCanvasViewModel` computes `anchorCenter(displayedLat, displayedLon, activeAnchor, mag, screenW, screenH, dpi, angle)` (existing helper, `core/VehicleAnchor.kt`) and passes that as the render target to `MapRenderer.prepareViewport(...)`; the follow branch in `MapCanvasScreen` uses `offset.clampedX/Y` **as is** (delete the second anchor subtraction), and the follow marker overlay projects against `state.renderViewport` with no `anchorCenter` call.

Alternative A (keep the vehicle-centered frame, enlarge the overrun so the anchor fits): needs `overrun ≈ 1 + 2*0.4 = 1.8` per axis, i.e. ~3.2x the pixels of a screen-sized render for every full follow render, plus the tile cache would have to store much larger tiles. Rejected: cost is paid on every render to fix a framing constant.

Alternative B (keep the vehicle-centered frame, shift the visible crop window inside a larger buffer): identical cost problem — the buffer must extend `0.4 * surface` beyond the vehicle in the anchor direction, so it is the same 1.8x requirement expressed as a crop. Rejected for the same reason.

Alternative C (shift only on frame commit and let the clamp handle the rest, i.e. today's code minus the double subtraction): the offset is still permanently saturated for edge presets, so the map is pinned at the buffer edge, the re-render request loop runs continuously, and the marker (unclamped) slides off the frame. Rejected: it is a partial fix that keeps the visible defects.

Why D1 is safe: the anchor presets are deliberately bounded to `0.1..0.9`, i.e. exactly the overrun margin, so the visible window always lies inside the rendered frame — no overrun increase is needed (this invariant is already documented on the enum).

### D2 — Viewport state keeps the "geo position at the screen center" meaning

`MapCanvasUiState.viewport.center*` remains the geo position at the screen center, which in follow mode **is** the anchor center: the follow collector commits the anchor-centered render target there (and to `prepareViewport`). Gesture commits (`rotateZoomAtFocalPoint`), the mini-map, the route-fit helpers and `browseDrifted` all read the point under the screen center and therefore keep working unchanged, and a pan immediately after re-engage starts from the frame the user is looking at (task 4.3). `state.renderViewport` (emitted frame) stays the frame geometry for the marker and the display loop.

Alternative A (keep `viewport.center` at the smoothed vehicle position in follow mode, apply the anchor only inside the renderer): this was the first draft of this design and it is wrong once the frame is anchor-centered — the geo position at the screen center is then the anchor center, so storing the vehicle position makes `viewport.center` a lie of up to 0.4 screen. The first pan after re-engage would jump by that offset, the mini-map would be centered off-view, and every consumer would need a "is follow active?" branch. Rejected during implementation (design correction, no spec impact).

Alternative B (drop the anchor from the emitted viewport and re-derive it in the screen from `viewport` + anchor): re-introduces a second derivation of the same fact — exactly the defect class this change removes (guidelines/Design.md §4: "One source of truth per data signal; consumers never re-derive"). Rejected.

### D3 — Marker projection: anchor centre of the displayed position

The follow marker projects against the anchor centre **of the displayed (eased predicted) position** (frame magnification and angle, marker geo from the display loop), i.e. the viewport for which the displayed position projects to the anchor fraction. That is the only projection that lands the marker on the map content: the frame is anchor-centered on *its own* position and then blitted by the drift, so a marker projected against the frame's own centre would sit ahead of the content by exactly the blit offset (the drift-then-snap symptom reported on device). In browse (follow off) the marker keeps projecting against the displayed frame's viewport, where no offset is applied.

Alternative A (project against the frame's centre, `state.renderViewport`): simplest and self-consistent at frame-commit time (display == frame position), but between frames the marker leaves the content by the drift and snaps back at the next commit. This is what `eeb8c9e` does today and it is the reported defect — rejected. Note: `AutoMapRenderer.drawGpsMarker()` projects against `viewportLat/Lon` (the frame target) in the same way, so the AA surface has the same between-fix displacement; that is a pre-existing AA issue outside this phone-scoped change and is recorded in `TODO.md`.

Alternative B (clamp the marker with the same margin as the blit): visually correct while clamped, but the marker would then be held at the buffer edge while the road under it keeps moving — it hides the framing problem instead of removing it. Rejected.

### D4 — Re-center and re-engage commit the anchor-centered target

`recenterInBrowse`, the navigation re-center action, `onToggleFollowMode(true)`/re-engage and the clamped-follow recovery path all commit through the same helper as D1, so no path can produce a vehicle-centered frame while an anchor is configured.

Alternative (let the next GPS fix repair the framing): produces a visible jump at re-engage, and never repairs it when the vehicle is stationary (no new fix). Rejected.

### D5 — Test strategy: viewport math at the ViewModel level, drawing bound at the screen level

- ViewModel tests assert the committed render target (`renderViewport`/prepared target) equals `anchorCenter(vehicle, activeAnchor, ...)` for both modes and under rotation.
- A screen/renderer-level test asserts `|followOffset| <= (bitmap - canvas) / 2` for all 15 presets and both orientations — the regression test for the uncovered strip (the visible symptom was a `surfaceColor` band).
- A consistency test asserts the marker's projected screen position equals the content offset at the anchor (one projection source).
- Threading/lifecycle: no new component, dispatcher or lifecycle owner. The render target is computed on the ViewModel's existing main-thread GPS collector (pure math, no native calls); the native render stays on the renderer's background scope with its conflated queue. `guidelines/Design.md` §4 (no native on main, debounced GPS-driven renders) is unaffected.

## Risks / Trade-offs

- [Re-anchoring changes the emitted `renderViewport` for non-center anchors, so any test or consumer that assumed `renderViewport.center == vehicle` breaks] → make the assertion explicit in the tests, and keep `center/center` bit-identical (regression gate: existing follow/overrun tests must pass unchanged).
- [Follow mode commits the anchor center into `viewport.center` (the geo position at the screen center), so consumers reading `viewport.center` as "the vehicle position" see the anchor offset] → in follow mode nothing may assume the vehicle is at the screen center (that is the point of the feature); gesture commits and the mini-map need the geo position at the screen center, which is exactly what is committed. Verified by the re-engage/pan test (task 4.3).
- [First pan after re-engage could jump if a consumer uses `viewport.center` as the point under the screen center while follow committed an anchor-shifted frame] → D2 keeps `viewport.center` = geo at screen center and D4 commits the render target separately; add a test that a pan after re-engage starts from the displayed frame (no jump).
- [Heading-up rotation: `anchorCenter` must use the committed angle, otherwise the anchor drifts by the rotation compensation] → pass the same normalized angle that is committed to the frame; test at 0°, ±45°, 90°, 180°.
- [Overrun invariant silently broken by a future preset added outside `0.1..0.9`] → the enum documents the invariant and the new margin test asserts it for every entry, so adding a preset outside the margin fails the build.
- [The in-flight `vehicle-position-presets` change still claims tasks 4.1/4.2 complete and carries its own `smooth-follow` delta] → this change's tasks correct those tasks and state the required archive order (see Migration Plan); the two deltas add differently named requirements, so archiving either order is safe.

## Migration Plan

1. Land the phone fix (render target + offset + marker projection) with the new tests; existing tests must stay green with default anchors.
2. Correct the in-flight `vehicle-position-presets` tasks 4.1/4.2 (implementation now actually lands in `MapCanvasViewModel`) and let its 7.4 on-device phone verification cover this change; archive it after this change (its `smooth-follow` requirement and this change's "Anchor-centered follow framing" requirement are complementary, not duplicates).
3. On-device verification (phone, GPX replay + a real drive): free driving and navigation with bottom-center and far-right anchors — no uncovered strip, the vehicle stays at the anchor, no drift-then-snap of the marker, re-center restores the anchor; `adb logcat -s NaviVeylin` follow diagnostics show the committed center tracking the anchor center, and `MapCanvasScreen`/`MapCanvasViewModel` follow logs show the offset always inside the margin.
4. Rollback: revert the phone render-target change (defaults then reproduce the pre-change framing) or reset both anchors to `center/center` in the settings sheet — both are single-step and need no data migration.

## Open Questions

None.
