## Context

See `proposal.md` — Why. Relevant current state:

- `MapCanvasViewModel`'s follow collector computes the map angle per GPS fix (`MapCanvasViewModel.kt`, follow branch, ~lines 936-953): with a valid smoothed bearing and follow-direction orientation it applies the 2° deadband against the rendered angle and the 90°/frame rate limit; otherwise it falls back to `lastUsedAngle` (or 0.0 when that is still NaN).
- `lastUsedAngle` is a plain `var` in the ViewModel, written after every angle computation regardless of orientation mode, and never reset by `onSetNavOrientation`/`onSetFreeFormOrientation` (those only reset `viewport.angle` to 0.0).
- The fallback exists because of durable requirements in `gps-render-coalescing` ("Course unavailable", "Keep last valid course bearing"): at a standstill or after a history reset the map must not spin or snap to north **in follow direction**. Those requirements say nothing about the north-up branch, which is how the unconditional fallback passed review.
- `guidelines/MapRendering.md` §6 documents the angle conventions, normalization and the `angle = −bearing` follow rule; the fallback branch is not described there.
- `guidelines/Design.md` §3 (state) / §4 (threading): the change is a pure state-derivation fix in the existing main-thread GPS collector; no new state owner, dispatcher or lifecycle.

## Goals / Non-Goals

**Goals:**

- Make the orientation mode the deciding factor for the angle fallback, so the mode's own requirement ("north-up keeps the map at 0°") cannot be overridden by a stale follow-direction angle.
- Keep the follow-direction guarantees intact: no spin, no snap to north, no dependence on bearing quality at a standstill.
- Make the north-up branch testable (today it has no test).

**Non-Goals:**

- Changing the deadband, the per-render rate limit, the render throttle or the course-over-ground pipeline (all durable specs stay as they are).
- Changing how the marker arrow derives its direction (`markerBearing` path is untouched).
- Reworking the `lastUsedAngle`/`lastUsedBearing`/`viewport.angle` trio into a single state holder — that is a larger refactor of the follow collector and is not needed to close this defect.
- Android Auto's navigation rotation (`AANavigationController`) — separate controller, checked only as a parity spot check.

## Decisions

### D1 — Branch on orientation before the fallback

The angle fallback is evaluated only when the active orientation is follow direction:

```
angle = when {
    isNorthUp                                   -> 0.0
    !smoothedAngle.isNaN()                      -> deadband/rate-limited smoothed angle
    !lastUsedAngle.isNaN()                      -> lastUsedAngle
    else                                        -> 0.0
}
```

Alternative A (reset `lastUsedAngle` to `Double.NaN`/0 on every orientation change): works for the toggle case but leaves the branch itself mode-blind — the first fix after any other path that sets a follow-direction angle while north-up is active would still rotate the map. Rejected: it fixes the symptom, not the rule.

Alternative B (fall back to the committed `viewport.angle` instead of a separate variable): removes `lastUsedAngle` entirely, but a north-up period commits 0 to the viewport, so returning to follow direction without a bearing would hold 0 — contradicting the `gps-render-coalescing` "SHALL NOT snap back to North-Up" guarantees. Rejected unless the viewport angle is kept mode-aware, which re-introduces the same bookkeeping.

Alternative C (let the caller pass an explicit fallback angle per mode): equivalent behavior, but adds a parameter to an internal helper for a single call site. Rejected as unnecessary indirection.

### D2 — Write `lastUsedAngle` only from follow-direction angles

With D1, the north-up branch produces 0.0; storing that into `lastUsedAngle` would erase the last driving direction and make the "returning to follow direction" scenario jump to north. `lastUsedAngle` is therefore updated only when the committed angle came from the follow-direction path (smoothed bearing or the follow-direction fallback).

Alternative (store every committed angle and derive the north-up case at read time): same result with an extra condition at the read site; rejected as less explicit than writing only the follow-direction angle.

### D3 — Verification

- New ViewModel follow tests (the missing coverage): north-up with a valid bearing keeps `angle == 0` across consecutive fixes; north-up selected mid-drive after a heading-up period keeps 0 for later fixes (the reported defect); follow direction without an available bearing keeps the last angle (guards the durable requirements); north-up → follow direction without a bearing keeps the last follow-direction angle (D2).
- `OrientationLogicTest` (pure `computeMapAngle`) stays unchanged and must keep passing; `gps-bearing-smoothing` deadband/rate-limit tests (`MapCanvasViewModelFollowModeTest`) must keep passing.
- On-device: toggle to "always north" while driving and confirm the map stops rotating (`adb logcat -s NaviVeylin` map/angle logs show a constant angle) and that the compass needle then shows north; toggle back and confirm follow direction resumes.
- Threading/lifecycle: unchanged — the computation stays in the existing `viewModelScope` GPS collector on the main thread; no native calls involved (`guidelines/Design.md` §4).

## Risks / Trade-offs

- [A north-up commit of a constant angle could stop the follow loop from re-rendering positions] → the position change (`> 5 m`) and magnification paths are independent of `angleChanged`; the north-up angle stays equal to the committed one, so `angleChanged` is false and no extra full render is triggered (this is the desired behavior — it also removes spurious rotation renders while north-up is active).
- [Scoping the fallback could reintroduce a standstill spin in follow direction] → the follow-direction branch is untouched; the new test duplicates the existing "Vehicle is crawling or stationary" scenario.
- [Mode switches during an in-flight render could commit a mixed frame] → unchanged from today: the renderer evaluates each job against its snapshotted viewport (`gps-render-coalescing`, "Render job uses snapshotted viewport"); no epoch bump is involved in this fix.
- [`lastUsedAngle` becomes stale-by-design across a north-up period (may be minutes old)] → acceptable and intended: it is the last driving direction, which is exactly what follow direction should resume with when no new bearing exists; the smoothed bearing overwrites it as soon as the vehicle moves 40 m.

## Migration Plan

1. Apply D1 + D2 in the follow collector; add the tests; run the suite (`run-tests` skill) and build the phone flavors (`build-app` skill).
2. Update `guidelines/MapRendering.md` §6 with the mode-scoping rule for the fallback.
3. On-device: free driving and navigation — north-up selected mid-drive stops rotation; follow direction still rotates, does not spin at a standstill; the compass (change `compass-always-north-phone`) shows north in both modes.
4. Rollback: revert the branch change (the previous unconditional fallback); no persisted state or settings schema is touched.

## Open Questions

None.
