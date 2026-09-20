# Design: AA follow framing and zoom parity

## Context

See `proposal.md` — Why. Prerequisite: `overlay-projects-against-displayed-frame` (in flight) publishes the displayed frame and snapshots it per render; this change assumes those invariants and does not touch them.

```
CURRENT AA FOLLOW COMMIT (per GPS fix, from FreeDrivingScreen / NavigationScreen)
  setGpsMarker(fix)                 -> prediction base = raw fix; display untouched
  setViewport(vp.lat, vp.lon, zoom, angle, zoomFrac)   <- vp = last EMITTED viewport
  reengageFollow()                  -> viewport = anchorCenterFor(RAW FIX)   [P1 target]
  -> frame commit: center jumps to the raw fix's anchor (P1), rotation re-committed for any
     heading delta (P2, forces a full render: a rotation cannot be blitted), and the new
     fractional magnification is applied in one frame (P3)

MEASURED (AAOS emulator, free driving, 100 s, mag 13, 87-95 km/h)
  |display - fix|         mean 6 m  -> 1.06 px at mag 13, 5-8 px at mag 16-18 (town speeds)
  |dbearing| per fix      mean 0.90 deg (max 2.71) = 0.90 deg/s -> ~5 px/s at 300 px radius
  committed dMag          sampled 0.11-0.17 levels = 7-12% scale, applied in ONE frame
                          -> 20-40 px at the frame edges
  display tick rate       1140 ticks / 104 s = 11 Hz (nominal 30) -> sub-pixel step at mag 13
```

Threading/lifecycle (guidelines/Design.md §4): the renderer owns `CoroutineScope(SupervisorJob() + Dispatchers.Default)`; the extrapolation loop and the debounced render loop run there, the fix path runs on the car host's main thread, and all bitmap/draw work is serialized by the static `surfaceLock`. Nothing in this change adds a lock acquisition, an allocation in the draw path, or a render inside the lock.

## Goals / Non-Goals

**Goals**

- Remove the per-fix *framing* steps: the follow render target advances with the display, the rotation is committed only when it visibly changed, and a magnification change lands across frames instead of in one.
- Cut the forced full-render rate: a fix whose centre-only change is inside the overrun margin SHALL be served by a blit.
- Keep the phone behaviour unchanged (parity is asserted, not modified).

**Non-Goals**

- The frame-consistency invariants (owned by `overlay-projects-against-displayed-frame`).
- The anchor *fraction* semantics and the host pane/inset clamping (`fix-aa-follow-blit-anchor-mismatch`, `anchor-per-surface-visible-area`).
- Rotating the canvas between commits (P4, design D4: analysed, deferred).
- Any phone, settings, persistence, JNI or native change.

## Decisions

### D1 — The follow render target is the anchor center of the DISPLAYED position (P1)

Alternatives:

1. **Alt A (chosen): `reengageFollow` anchors on `markerPosition()`** (the displayed/eased position, raw fix as the pre-first-frame fallback). One-line change at the anchor point; the commit then moves the centre by the display's own advance only, which the overrun blit serves, and the frame that commits is centred where the display already sits — so nothing about the scene moves at the commit. Matches the phone's documented rule ("the displayed position IS the follow center").
2. **Alt B: drop the per-fix commit entirely** (the phone's model — only the display loop's clamp re-anchors, fixes update the prediction only). The cleanest end state, but the per-fix block in both screens also carries the auto-zoom commit and the pan gate, so this is a screen-lifecycle rewrite; keep it as the natural follow-up if Alt A still shows steps.
3. **Alt C: keep anchoring on the raw fix and compensate in the blit** — the blit offset would have to absorb the lead *and* the new centre, i.e. re-derive the frame at a different centre than the pixels carry. Rejected: that is the same class of "two frames" confusion this whole seam was just fixed for.

Risk of Alt A: `reengageFollow`'s old rationale ("render AT the fix so the eased display is not yanked back") assumed the display is reset by a render — it is not (the commit preserves the display in follow mode), so anchoring on the display is safe; the fallback covers the pre-first-frame case.

### D2 — The heading deadband lives in the shared commit gate (P2)

Alternatives:

1. **Alt A (chosen): one rule beside `shouldCommitViewport` (`MapPanHandler.kt`)**, used by both car screens. That gate already owns "should a fix commit a viewport change", it is shared precisely so the two screens cannot drift apart, and a deadband constant sits next to the pan gate it complements.
2. **Alt B: in `AutoMapRenderer.setViewport`** (ignore a sub-threshold rotation). Rejected: `setViewport` is also the gesture path, where a small rotation must apply, and the renderer cannot distinguish a fix-driven rotation intent from a user rotation.
3. **Alt C: in each screen.** Rejected: duplicated rule, and any future third follow surface would have to re-implement it.

Threshold: the measured heading moves 0.90°/s mean, max 2.7°/fix, so a deadband of ~1.5° removes most commits while bounding the heading lag to 1.5° (the deadband is measured against the *committed* rotation, so the lag cannot accumulate). The value is a tuning constant verified on device (task 7.2), not a spec constant.

### D3 — A magnification change is applied across frames, about the follow anchor (P3)

Alternatives:

1. **Alt A (chosen): the display blit scales the overrun bitmap about the follow anchor** while the displayed magnification approaches the committed value at the display rate; the next full render lands the committed value exactly. Feasible inside the existing overrun margin: with `OVERRUN_FACTOR = 1.2`, a scale `s` still covers the surface for `s >= 1/1.2 = 0.833`, i.e. steps up to ~0.26 magnification levels are blit-able in one transition; a larger step (the 0.5-level cap) SHALL fall back to a full render at the committed magnification.
2. **Alt B: render at intermediate magnifications** (a full render per transition frame). Rejected: N extra native renders per speed change — the opposite of the goal, and the transition would then be limited by the render rate anyway.
3. **Alt C: leave the single-frame snap.** Rejected: measured 7-12% scale in one frame = 20-40 px at the frame edges — the largest single artefact left.

Trade-off to verify on device: a scaled bitmap blit resamples (soft edges) for the duration of the transition; the alternative (nearest-neighbour) shows slight jaggedness. The transition is short (a few display frames per commit) and ends with an exact native render.

### D4 — Smooth heading-up between fixes: analysed, deferred (P4)

Rotating the canvas about the follow anchor by the pending heading delta is mechanically possible within the margin: a rotation by θ exposes a corner sliver of about `r·θ` px, with `r` up to ~1000 px on a 1080x600 surface — ~15 px at the measured 0.9°/fix, inside the 108 px horizontal margin but not a clean bound at the corners, and the overlay rotation must compose with the marker's own bearing rotation. Since P2 already removes the forced render per fix and the rotation signal is ~0.9°/s (near-continuous), P4 is deferred: revisit only if the on-device result after P1-P3 still shows rotation stepping. The mechanism, its bound and its risks are recorded here so a later change does not have to re-derive them.

## Risks / Trade-offs

- **Heading lag (P2).** → Mitigation: the deadband bounds the lag at its own value; the scenario "Heading-up semantics unchanged" pins that turns still track. Tuning constant, measured in task 7.2.
- **Scale-transition resampling (P3).** → Mitigation: cap the blit-able step (≈0.26 levels) and fall back to a full render beyond it; the transition ends at an exact native render. On-device check for soft edges.
- **A blit-able centre change that is *not* blit-able** because zoom/rotation is also pending → the commit legitimately requires a full render (by requirement); the render-rate metric in task 7.1 reports both.
- **Interaction with an in-flight follow change landing first** (`fix-aa-follow-blit-anchor-mismatch`, `anchor-per-surface-visible-area`): they touch the same commit path. → Mitigation: land after them or coordinate; the anchor-fraction rules are not touched here.
- **Threshold values become folklore if untested**: both constants get a unit test pinning their intent (a sub-threshold heading change must not commit; a sub-0.26-level zoom step must be blit-able) and an on-device measurement.

## Migration Plan

No data, settings or API migration. Implementation order: P1 (target) → P2 (deadband) → P3 (transition) → diagnostics → tests → guidelines. Each step is independently revertable (P1: one call site; P2: one gate + two call sites; P3: the blit path + a cap constant). Rollback: revert `:auto` edits; the frame-consistency change stays.

## Verification

Unit tests (`:auto:testDebugUnitTest`, `asyncLoopsEnabled = false` for determinism):

1. `followReanchorUsesTheDisplayedPosition` — a fix whose display leads it commits a frame centred on the display's anchor, and the map content does not step (P1).
2. `followReanchorFallsBackToTheFixBeforeTheFirstDisplayFrame` (P1).
3. `subDeadbandHeadingChangeDoesNotCommitRotation` / `headingChangeBeyondTheDeadbandCommitsRotation` (P2).
4. `fixBelowDeadbandIsServedByABlit` — no additional full render (P2).
5. `zoomStepIsAppliedAcrossFramesAndStaysInsideTheMargin` + `zoomStepBeyondTheBlitLimitFallsBackToAFullRender` (P3).
6. Existing follow/blit/anchor-contract tests stay green.

Build gates: `:auto:testDebugUnitTest`, `:app:assembleAutomotiveDebug` + `:app:assembleMobileDebug` (compile + package, no warnings), the four per-flavor test tasks (mobile 975 / automotive 975 / auto 436 / core 231 as the current baseline). Run with `*/build/test-results/**` cleared and quote executed-task count + elapsed time (`TODO.md` §17); do not build concurrently with another session (`TODO.md` §19).

On-device (AAOS emulator, free driving + navigation view, the same 100 s window method, `adb logcat -s AutoMapRenderer`):

```
per-fix re-anchor step      the diagnostic's |disp - fix| lead must no longer appear as a commit
                            step; the frame center tracks disp (not the raw fix)
full renders vs fixes       must fall well below the current ~1.0 per fix (baseline before this
                            change: 62 renders / 100 fixes on a turning 90 km/h stretch)
rotation                    committed only beyond the deadband; the map still tracks turns
zoom                        a speed change must not scale the frame in one step (the sampled
                            dMag 0.11-0.17 must appear as a multi-frame transition)
display tick rate           unchanged (~11 Hz on the emulator; sub-pixel steps at mag 13-16)
```

## Open Questions

- Whether the **phone** should adopt the heading deadband too (parity in the other direction) — the phone commits a rotation per fix as well, and its renders are cheaper because the front buffer is scaled by Compose. Deferrable: this change is AA-scoped and the phone's behaviour is unchanged either way.
