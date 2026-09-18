# Tasks

## 1. Displayed-frame bookkeeping

- [x] 1.1 Add one internal accessor on `AutoMapRenderer` that returns the frame currently on the surface (centre, magnification, rotation) from the committed overrun fields, falling back to the pending render target when no overrun bitmap exists (spec: gps-location-marker — Marker projects against displayed bitmap viewport; design D1). Verify with a new unit test that the accessor returns the pending target before the first render and the committed centre after `renderFrame()` has run.
- [x] 1.2 Confirm the accessor and every overlay read happen inside the existing `surfaceLock` critical section that owns the lock/draw/unlock (design D1, guidelines/Design.md §4 threading model). Verify by inspection of the call sites plus a green `:auto:testDebugUnitTest` (no new lock acquisition, no allocation in the draw path).

## 2. Overlay projection uses the displayed frame

- [x] 2.1 Project the vehicle marker (`markerScreenPosition`) against the displayed frame's centre, magnification and rotation instead of the pending render target (spec: gps-location-marker — Marker projects against displayed bitmap viewport; auto-map-renderer — GPS position marker on car map). Verify with the new test `markerStaysOnItsContentWhileAFollowReanchorIsPending`, which fails on the pre-change code.
- [x] 2.2 Project the destination pin (`drawDestinationMarker`) against the same displayed frame (spec: gps-location-marker — Destination pin shares the displayed frame). Verify that the new `drawDestinationMarker`/pin test pins both overlays to one frame and fails on the pre-change code.
- [x] 2.3 Stop the marker/pin from picking up a pending rotation or zoom before the frame carrying it is committed (spec: gps-location-marker — Marker projects against displayed bitmap viewport; design D3). Verify with the new test `markerUsesTheDisplayedFrameRotation`, which fails on the pre-change code.

## 3. Follow blit frame-of-reference

- [x] 3.1 Compute the follow-mode blit offset in `renderFrame` against the displayed vehicle position instead of the frame centre, so the offset carries the vehicle's drift from its anchor and not the anchor displacement (spec: auto-map-renderer — Map re-renders on viewport change; auto-smooth-follow — Sub-region blit on viewport change; design D2). Verify with the new test `followBlitServedForANonCenterAnchor` (`BOTTOM_CENTER`, centre change inside the overrun region, overrun bitmap identity unchanged, no full render), which fails on the pre-change code.
- [x] 3.2 Re-check the extrapolation clamp branch against the new frame-of-reference: the clamp still re-anchors the pending target on the displayed position, stays throttled, and does not double-apply the anchor (spec: auto-smooth-follow — Sub-region blit on viewport change). Verify that the existing clamp/blit tests plus `paneBandBlitOffsetUsesTheResolvedAnchor` and the bottom-band anchor tests stay green.
- [x] 3.3 Snapshot the frame parameters once per native render and publish that snapshot as the committed frame, so the displayed frame is always described by the parameters the pixels were rendered with — the render runs outside `surfaceLock`, so the pending target can move while it is in flight (a fix re-anchor, the loop's clamp, an auto-zoom commit) (spec: gps-location-marker — Frame stays consistent when the target moves during a render; design D1). Verify with `committedFrameIsLabeledWithTheRenderedParameters` (new `FakeAutoRenderClient.onRender` hook), which fails on the pre-change code and passes with the snapshot.
- [x] 3.4 Publish each frame's blit offset under `surfaceLock` together with the frame it describes (both `blitToSurface` and `drawToSurface` assigned it outside the lock), so a concurrent render cannot leave an overlay drawn with another frame's offset (spec: gps-location-marker — the offset is published with its frame). Verify the existing marker/blit contract tests stay green (`markerRidesTheBlittedContentWithABlitOffset`) plus the full `:auto` suite; the interleaving itself is not deterministically testable without a second thread, so it is fixed by construction and documented at both assignment sites.

## 4. Diagnostics

- [x] 4.1 Extend the throttled follow diagnostic with the displayed frame's centre/magnification/rotation next to the pending target and with the full-render/blit counters (design D4). Verify on-device that one `adb logcat -s AutoMapRenderer` follow line shows both frames and the counters.

## 5. Unit tests

- [x] 5.1 Add the three failing-first regression tests named in tasks 2.1, 2.2, 2.3 and 3.1 to `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt`, using `asyncLoopsEnabled = false` and the existing mocked-surface helpers (design — Verification). Verify each test fails against the pre-change renderer and passes after it.
- [x] 5.2 Keep the existing marker/blit/anchor contract tests green (`markerRidesTheBlittedContentWithABlitOffset`, `paneBandBlitOffsetUsesTheResolvedAnchor`, the host-pane and bottom-band anchor tests). Verify with a full `:auto:testDebugUnitTest` run.
- [x] 5.3 Run the suite with `auto/build/test-results/testDebugUnitTest` cleared first and quote the executed-task count and elapsed time in the evidence, per the `TODO.md` §17 up-to-date-masking blind spot. Verify the reported run shows real execution (not a ~5 s "success").

## 6. Build and suite verification

- [x] 6.1 Run `:auto:testDebugUnitTest` through the `run-tests` skill and verify the run reports `BUILD SUCCESSFUL` with real execution time.
- [x] 6.2 Build `:app:assembleAutomotiveDebug -Pandroid.injected.build.abi=arm64-v8a` and `:app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` through the `build-app` skill and verify both compile with no warnings.
- [x] 6.3 Verify the four per-flavor test tasks still pass (`:app:testMobileDebugUnitTest :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest :core:testDebugUnitTest`) and there are no regressions in phone rendering or navigation code.

## 7. On-device verification (AA emulator / head unit)

- [x] 7.1 Free driving with a GPX replay at speed: count the full-render lines against the fix lines over a ≥60 s window and verify the per-fix full native render is gone (baseline measured before the change: 105 fixes / 101 full renders in 104 s at 50.6 km/h); full renders SHALL appear only at the overrun-margin cycle. — verified on-device 2026-09-18 (AAOS, GPX replay): per-fix full render gone; full renders only at the overrun-margin cycle
- [x] 7.2 Verify the vehicle marker holds its road/track pixel while a fix-driven re-anchor renders (no per-fix pop relative to the map content), with a screen recording or a marker-position logcat trace plus the 4.1 diagnostic line. — verified on-device 2026-09-18: marker holds its road pixel through fix-driven re-anchor renders, no per-fix pop
- [x] 7.3 Repeat 7.1/7.2 in the navigation view, where the heading rotates on most fixes, and verify the marker does not rotate or scale before the frame that carries the rotation is committed. — verified on-device 2026-09-18: navigation view — marker keeps orientation/scale until the committed frame carries the rotation
- [x] 7.4 Surface lifecycle: switch screens while following (surface released/recreated), background the app, and drive again — verify no overlay is drawn against a stale frame, no `lockCanvas` failure appears, and no marker is left behind on the surface. — verified on-device 2026-09-18: screen switch + background + drive again — no stale-frame overlay, no `lockCanvas` failure, no leftover marker

## 8. Documentation

- [x] 8.1 Update `guidelines/MapRendering.md` §1.1 (AA follow) with the displayed-frame rule for overlays and the anchor-independent blit rule, merging into the single paragraph the in-flight `fix-aa-follow-blit-anchor-mismatch` / `anchor-per-surface-visible-area` changes also edit (proposal — What Changes). Verify the paragraph reads consistently with those changes' wording after both land.
- [x] 8.2 Record the out-of-scope findings in `TODO.md` for later changes: the per-fix re-anchor targets the raw fix rather than the display position (owned by the in-flight vehicle-jumps changes), `reengageFollow` depends on a preceding `setViewport` having requested the render, and the extrapolation loop measured ~11 Hz rather than the nominal 30 Hz. Verify each entry names the code location and a suggested owner.
- [x] 8.3 Log the five failed attempts on this seam and the missing runtime evidence (the follow diagnostic never printed the displayed frame) in `ki_processing_failures.log` with a timestamp, per the apply guidance. Verify the entry names the symptom, the wrong hypothesis and the observable that finally discriminated it.
