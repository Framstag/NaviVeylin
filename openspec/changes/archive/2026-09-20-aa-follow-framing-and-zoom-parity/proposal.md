# Proposal

## Why

The Android Auto follow map still moves in per-fix steps after `overlay-projects-against-displayed-frame` removed the marker/map misalignment (frame consistency, landed). What remains is **framing**, not correctness: the AA follow path re-anchors the render target on each raw GPS fix, commits the heading on each fix, and applies a new auto-zoom magnification in a single frame. Measured on the AAOS emulator (100 s, mag 13, 87-95 km/h, free driving):

```
per-fix re-anchor scene step    |display - fix| = 6 m mean -> 1.06 px at mag 13, but the same
                                lead at mag 16-18 (20-50 km/h, where auto-zoom picks a fine
                                scale) is 4-8x larger -> 5-8 px of whole-map step per fix in
                                town: the map visibly steps 1x/s while the marker stays glued
zoom snap                       auto-zoom commits the interpolated target as one step
                                (sampled dMag 0.11-0.17 = 7-12% scale) -> 20-40 px at the
                                frame edges, in a single frame: the biggest single artefact
rotation committed per fix      mean |dbearing| 0.90 deg/fix = 0.90 deg/s, max 2.71 deg
                                -> ~5 px/s of tangential motion at 300 px from the anchor:
                                near-continuous and small, BUT a rotation cannot be served by
                                a blit, so every fix forces a full native render (~1 render/s)
                                and each sub-degree change is a discrete step at the edges
```

Ranking that falls out of the numbers: the **zoom snap** is the largest single artefact, the **per-fix re-anchor step** is the most frequent visible one (town speeds), and the **rotation** is mainly a *churn* cost (~1 full render/s for a 0.9 deg/s signal) with a small 1 Hz twitch.

The phone already specifies and implements all three: `smooth-follow`/`smooth-zoom` ("the displayed (eased predicted) position IS the follow center ... a fix never re-commits the center on its own"; "animate the displayed map from the current displayed scale toward the target magnification ... compose with the smooth-follow display extrapolation"), and `auto/navigation-view` already requires "scroll the navigation map smoothly to follow the vehicle between GPS fixes while navigating in follow mode (**no per-fix snap**)". The AA renderer does not honour that parity: its fix path runs `setViewport(...)` + `reengageFollow()` on every fix, and `reengageFollow` anchors the target on the raw fix (`anchorCenterFor(gpsMarkerLat, gpsMarkerLon)`), so the frame target and the committed heading/zoom step once per second.

Why now: the follow seam has just been made frame-consistent; the remaining per-fix stepping is the last thing that reads as "the map jumps", and fixing it inside the follow changes that are still in flight would collide with their scope (`fix-aa-follow-blit-anchor-mismatch`, `anchor-per-surface-visible-area`), which is why this is a separate change.

## What Changes

- **P1 — the follow render target is the displayed position (parity with the phone's single follow center).** `reengageFollow` SHALL anchor on the displayed (eased predicted) position — the same point the extrapolation loop commits and blits to — falling back to the raw fix only before the first display frame exists. The per-fix commit then no longer moves the target: a fix updates the prediction and the commit becomes a centre-only change that the overrun blit serves. This removes the 5-8 px whole-map step per fix at city speeds.
- **P2 — the heading commit gets a deadband (AA).** The car screens SHALL re-commit the viewport rotation only when the heading changed by more than a small threshold; below it the fix commits a centre-only change (blit-able) and the previous rotation stands. The measured heading moves ~0.9 deg/s — smooth enough — so this is primarily a **churn** fix (it removes most of the ~1 forced full render per second, for a signal that a blit could otherwise serve) with a secondary effect on the sub-degree twitch at the frame edges. **Breaking for the rotation's timing, not for its semantics**: heading-up still tracks the heading, with a bounded lag of at most the deadband.
- **P3 — zoom changes are applied as a transition, not in one frame (parity with `smooth-zoom`).** When the auto-zoom (or turn-zoom) target magnification changes, the displayed map SHALL move from the displayed scale to the target across frames instead of snapping at the next commit. The AA overrun blit already scales nothing, so the transition SHALL be served by drawing the overrun buffer with the intermediate magnification about the follow anchor (bounded by the overrun margin) or by the next full render, whichever the display loop can serve first.
- **P4 — smooth heading-up between fixes: analysed, deferred (see design D4).** Rotating the canvas about the follow anchor by the pending heading delta is mechanically possible inside the overrun margin, but it exposes corner slivers of unmapped surface and resamples the bitmap at sub-degree angles. Not implemented here; the design records the mechanism, the bounds and the risks so a later change can pick it up if P1-P3 leave visible rotation stepping.
- **Scope**: `:auto` only (the car renderer + the two car screens). The phone already implements P1/P3; its behaviour SHALL NOT change — the change is a parity assertion, not a phone change.
- **Not breaking for the phone, settings, persistence or JNI**: no new settings, no data change, no native change (Kotlin-only inside `:auto`). Rollback: revert the `:auto` edits; the frame-consistency fix in `overlay-projects-against-displayed-frame` stays.
- **Docs**: `guidelines/MapRendering.md` §1.1 (AA follow) — extend the single-follow-center rule to the AA target and document the rotation deadband and the zoom transition. Same paragraph the follow changes touch; coordinate before landing.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `auto-smooth-follow`: **Display center extrapolation** / **Sub-region blit on viewport change** — the follow render target SHALL be the displayed position (never the raw fix), so a fix commits a centre-only change that the overrun blit serves; and the commit SHALL NOT step the map when the display has not moved.
- `auto-speed-zoom`: **speed-to-magnification lookup with linear interpolation** — the interpolated target SHALL be applied as a display transition across frames (parity with the phone's `smooth-zoom`), not as a single-frame scale step at the next commit.
- `auto/navigation-view`: **"scroll the navigation map smoothly to follow the vehicle between GPS fixes (no per-fix snap)"** — pin the parity scenarios (the target anchored on the displayed position; the heading deadband; the zoom transition) for the navigation view and the free-driving view.
- Previous specifications changed: `auto-smooth-follow`, `auto-speed-zoom`, `auto/navigation-view` (all existing). Nothing renamed or removed. `smooth-zoom` / `smooth-follow` (phone) are referenced for parity and stay unchanged.

## Impact

- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — `reengageFollow` (P1), the commit cadence / a rotation deadband at the follow commit (P2), the mag transition in the extrapolation blit + `renderFrame` (P3), plus the follow diagnostic (deadband and transition state).
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt`, `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — the fix paths (`shouldCommitViewport` use, the heading handed to the commit) for P2; `MapPanHandler.shouldCommitViewport` itself if the deadband lives there (shared by both screens — preferred, one rule).
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt`, `FreeDrivingScreenTest.kt`, `NavigationScreenTest.kt` — new regression tests.
- `guidelines/MapRendering.md` §1.1.
- Modules/gates: `:auto:testDebugUnitTest`, `:app:assembleAutomotiveDebug` (+ mobile) — no native/JNI change, no submodule patch, no bridge override.
- Coordination: `overlay-projects-against-displayed-frame` (in flight, 18/23) is the prerequisite — this change assumes the displayed-frame publication and the frame snapshot are in place; `fix-aa-follow-blit-anchor-mismatch` / `anchor-per-surface-visible-area` own the anchor *fraction* and must not be re-litigated here.
