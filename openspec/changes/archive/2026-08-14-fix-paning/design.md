## Context

See proposal.md — Why. Single-finger pan in `MapCanvasScreen.onPan` uses the north-up `ProjectionUtils.dragDeltaToNewCenter()`; the two-finger path (`onCentroidPan`) already uses the rotation-aware `dragDeltaToNewCenterRotated(dx, dy, s.viewport.angle, ...)`. The rotation-aware variant is a pure screen-space rotation of the drag delta (geo = R(angle) · screen in the east-north frame), matching the native MercatorProjection convention, and is verified to equal the north-up result at angle 0 (existing `ProjectionUtilsTest` cases).

## Goals / Non-Goals

**Goals:**
- Make single-finger pan move the map exactly under the finger at any viewport angle, consistent with two-finger pan.
- Keep the fix minimal and testable at the `ProjectionUtils` and call-site level.

**Non-Goals:**
- No changes to render paths (tile blit vs full render), gesture dispatch, or rotation gesture behavior.
- No native/C++ or ABI changes.
- No API removal: `dragDeltaToNewCenter` stays (still used by JavaScout's `MapInteractionHandler` and existing tests).

## Decisions

**D1: Pass the committed viewport angle (`s.viewport.angle`), not the live gesture rotation.**
Single-finger pan (`onPan`) fires independently of the multi-touch rotation/zoom gesture; `gestureRotation`/`gestureZoom` only accumulate in the multi-touch branch. The correct frame at pan time is the viewport's current committed angle. Two-finger pan already does exactly this — mirror it.
- Alternative: track a live total angle (angle + gestureRotation) in `onPan`. Rejected: `onPan` has no access to an in-progress multi-touch rotation (gestures are mutually exclusive), so it would only ever see 0 accumulated rotation and add nothing.

**D2: Reuse `dragDeltaToNewCenterRotated` as-is; no new projection code.**
The helper already exists in `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` and matches the native projection convention. Swapping the call site is the entire behavioral change.
- Alternative: inline rotation math at the call site. Rejected: duplicates tested logic and diverges from the two-finger path.

**D3: Test at both levels.**
- `ProjectionUtilsTest`: the rotated-variant cases at 90° already exist; the fix changes no math, so the contract test is the call-site behavior.
- Call-site coverage: `onPan` is a thin callback inside a Compose `Canvas` pointerInput — hard to unit test directly. Cover it via the `ProjectionUtils` behavior (which the callback delegates to) plus, where feasible, a test that the pan callback selects the rotated variant at non-zero angle.

## Risks / Trade-offs

- [Behavioral divergence between gesture paths if `onPan`/`onCentroidPan` drift apart] → Both now delegate to the same rotation-aware helper; the single shared code path keeps them in lockstep.
- [Sign mismatch if the angle sign convention changes in native code] → `dragDeltaToNewCenterRotated` already matches the native MercatorProjection convention and is exercised by existing tests; any future convention change must update this helper once, not per call site.
- [Test gap: `onPan` callback itself not directly unit-tested] → Acceptable: it is a pure delegation to `ProjectionUtils`; the math is covered by `ProjectionUtilsTest`. Manual QA step verifies finger-follow on a rotated map.

## Migration Plan

- Single commit; behavior change ships with the app. No data migration, no config.
- Rollback: revert the `onPan` call-site swap; behavior returns to north-up panning (the current buggy state).
