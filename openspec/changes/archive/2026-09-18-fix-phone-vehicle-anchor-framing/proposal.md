## Why

The phone half of the vehicle-anchor feature (in-flight change `vehicle-position-presets`, landed in commit `eeb8c9e`) never shifts the follow-mode render target. Its own spec delta requires "the map render target SHALL be shifted so the vehicle's geographic position projects to the anchor", but the phone only shifts the *drawn* (blitted) bitmap by an offset that the 1.2x overrun buffer cannot contain, and it applies the anchor twice. Symptoms reported on device (phone, free driving and navigation): a grey/black uncovered strip across part of the map with the map content pushed out of view for non-center anchors, the marker drifting off the road/center and snapping back on the next GPS resync, and re-center/re-engage not returning to the configured anchor. With both anchors left at the `center/center` default the extra offset is zero, so the defect is invisible in default configuration and in every unit test.

## What Changes

- Phone follow mode SHALL render each frame with a viewport centered on the **anchor center** of the displayed (predicted) vehicle position — the same model `AutoMapRenderer.anchorCenterFor()` already uses on Android Auto — instead of centering the frame on the vehicle and compensating at draw time.
- The follow blit/display offset SHALL be the prediction **drift only** (the offset `FollowPrediction.displayOffsetPx` already returns with anchor parameters). The extra anchor subtraction in `MapCanvasScreen` is removed so the anchor is applied exactly once; this removes the "vehicle pushed 2x the anchor offset" and the uncovered strip.
- The GPS marker overlay SHALL project against the **displayed frame's viewport** (which is the anchor-centered frame) and SHALL NOT derive its own viewport; the marker then rides the same projection as the map content and cannot be clamped differently from it (no drift-then-snap mismatch).
- Re-center and follow re-engage SHALL commit the anchor-centered render target (`recenterInBrowse`, the navigation re-center action and the re-engage restore path), fixing "anchor restored after manual pan or recenter" on the phone.
- Viewport-state semantics become explicit: `MapCanvasUiState.viewport.center*` stays "the geo position at the screen center" (gesture math, mini-map, route fit keep working unchanged); the anchor-shifted render target is a separate renderer input, not a redefinition of the viewport.
- New regression tests: phone follow render target is anchor-centered for routing and free-driving anchors; the drawn frame never leaves the overrun margin for any of the 15 presets; the marker's screen position equals the content offset at the anchor; re-center/re-engage restores the anchor; rotation (heading-up) keeps the marker at the anchor.
- In-flight change `vehicle-position-presets` tasks 4.1 and 4.2 (marked complete but not implemented) are corrected and re-verified; its on-device phone task (7.4) verifies this change.
- Additive, **not breaking**: with the default `center/center` anchors the framing is identical to today, and the change only makes the non-center presets behave as specified. Rollback: revert the phone render-target/offset change (defaults then reproduce the pre-change framing) or reset both anchors to `center/center`.

## Capabilities

### New Capabilities

- None — the anchor capability itself is being introduced by the in-flight change `vehicle-position-presets`; this change only delivers its phone side correctly.

### Modified Capabilities

- `smooth-follow`: the follow-mode framing requirement changes from "scroll the displayed viewport to the predicted position by blitting the front buffer" to "keep the vehicle at the active anchor by rendering the frame at the anchor center and blitting only the prediction drift, bounded by the overrun margin"; adds the anchor selection (routing vs free driving) and the anchor-restored-on-recenter contract to the durable spec.
- `gps-location-marker`: the marker-projection requirement is tightened — the marker projects against the displayed frame's viewport (which is anchor-centered in follow mode) and shares one projection source with the map content; a derived/anchored projection at overlay time is not allowed.

## Impact

- **Code (phone only)**:
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — follow commit path (`prepareViewport` + viewport state) computes the anchor-centered render target from the active anchor; `recenterInBrowse` and the navigation re-center/re-engage path do the same; the render target is exposed to the screen alongside `renderViewport`.
  - `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — follow offset = drift only (drop the second anchor subtraction, lines ~486-493); the follow marker overlay projects against `state.renderViewport` without an extra `anchorCenter` call (lines ~1090-1108).
  - `core/src/main/java/com/naviveylin/core/VehicleAnchor.kt` / `FollowPrediction.displayOffsetPx` — unchanged contracts; the docstrings are corrected to state which side renders the anchor-centered frame.
  - Reference implementation, unchanged: `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` (`anchorCenterFor`).
- **Tests**: `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelVehicleAnchorTest.kt` (render target per mode), a new screen/renderer-level assertion that the follow offset stays inside the overrun margin for all 15 presets, `MapRendererBlitTest`/follow tests for marker-vs-content alignment, re-center/re-engage tests. Existing `core/.../FollowPredictionTest` cases stay valid (they assume the anchor-centered frame this change now produces).
- **Guidelines**: `guidelines/MapRendering.md` — §1/§2 gain the follow-framing rule ("the anchor is applied to the render target, never to the blit offset; the blit carries the prediction drift only and SHALL stay inside the overrun margin so no uncovered strip can appear") and the viewport-vs-render-target distinction. `guidelines/UI.md` unchanged (the picker documentation already exists).
- **Interplay**: in-flight change `vehicle-position-presets` (spec deltas for `smooth-follow`, tasks 4.1/4.2/7.4, and the pending archive) must be reconciled with this change; `auto-pan-during-navigation` touches the AA re-engage path only and is unaffected.
- **Native/JNI**: none — pure Kotlin viewport math; no submodule patch, no bridge-module override.
- **Scope**: phone only (free driving + navigation follow mode). Android Auto already implements the anchor-centered render target and is the parity reference; no AA behavior change.
