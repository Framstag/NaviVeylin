# Proposal

## Why

A magnification the app commits while no zoom animation is pending never reaches the map: the render
pipeline keeps using the magnification of the last **completed** walk forever.

`ZoomWalk.renderMag` (`core/src/main/java/com/naviveylin/core/ZoomWalk.kt:140`) returns the walk's
`step` whenever it is not NaN, and `step` survives the walk's own end: `onFrameLanded` clears only
`target` (`:126-135`), `request` lands the same way (`:100-108`), and `step` is cleared only by
`cancel()` (`:157`), which only `beginZoomWalk` (`MapCanvasViewModel.kt:4511`) and `cancelZoomWalk`
(`:4521`) call — i.e. zoom input. Every other writer of `viewport.magnification` therefore writes a
value the renderer ignores: the route overview fit (`fitViewportToRoute`, `MapCanvasViewModel.kt:4338`),
the navigation preset on follow engage (`onToggleFollowMode`, `:3546`), and the mode presets.

**Evidence — measured on the phone (Pixel 8, release `2026-10-11-1`, logcat tags `MapCanvasVM` /
`MapRenderer` / `TileCache`; captures in `.pi/logs/route-nav-start/`):**

- `18:24:10.398 fitViewportToRoute mag=10.0 coveredPx=1023` → `18:24:10.448 executeRender start
  mag=17.756` — the fit's magnification was discarded, the frame rendered at the walk's stale step.
- `18:24:11.431` / `18:24:11.440 renderMap mag=17.756 (viewport 15.0)` — the navigation preset (15.0)
  likewise; every frame the screen received (`front buffer emitted mag=17.756` →
  `frontBufferFlow: new bitmap`) was at 17.756, and ~30 further `renderMap` calls over 15 s stayed at
  17.756 with no `zoomWalk step=` advance — no walk was pending.
- Screen measurement at that moment: route-paint pixels **0** (`40-phone-live.png`, navigation overlay
  showed zoom `15` and `18,5 km`); after one re-center render (same walk state, camera back on the
  vehicle) **47854** route pixels over x 0.00..0.99, y 0.43..0.77 (`41-phone-after-recenter.png`). This
  is the reported symptom: the route is not visible in navigation mode until something else renders.
- The same flow on the emulator with a clean fix and no prior walk renders `renderMap mag=10.0
  (viewport 10.0)` — fit and render agree, and the nav frame carries the route. The disagreement, not
  the route geometry, is the defect.
- **Test gap** (`core/src/test/java/com/naviveylin/core/ZoomWalkTest.kt`): `:177` covers a *fresh* walk
  ("renderMag falls back to the committed magnification with no walk"), `:152` a *cancelled* one; no
  case covers a walk that has **landed** followed by a magnification committed by another path.

**Contradicted requirement** — `smooth-zoom`, "Eased zoom animation on discrete zoom input": *the
system SHALL animate the displayed map from the current displayed scale toward the target
magnification whenever the magnification changes … The requested target magnification SHALL be
recorded immediately when the animation starts; the displayed magnification reaches it across the
transition.* With a stale step the change is not rendered at all, so the displayed magnification never
reaches it.

**Single resulting fix** — the walk pins the render magnification only while it is pending: `renderMag`
falls back to the committed viewport magnification when no walk is pending. One predicate, in the one
place that owns the pin; the display path already behaves this way (`advanceZoomWalk` publishes
`zoomWalkStepMag = NaN` once the walk is inactive, `MapCanvasViewModel.kt:4539-4541`), so the render path
is brought back in line with it. The alternative — making the fit and the presets cancel the walk —
is not the fix: it needs every present and future magnification writer to remember a call the invariant
should not depend on, and the measured frames were pinned while no walk was pending at all (design
records both).

## What Changes

- **A landed walk no longer pins the render pipeline.** `ZoomWalk.renderMag` returns the committed
  magnification whenever no walk is pending; a walk's `step` is only honoured while that walk is
  active. The zoom animation's own behaviour (steps, retracking, landing exactly on the target, the
  persisted target) is unchanged.
- **The rule gets its home in the guideline that owns the walk** (`guidelines/MapRendering.md` §11,
  the walk entry): the pin lives exactly as long as the walk, so a camera fit, a preset or any other
  later magnification commit is rendered at the committed value.
- **The regression gets a host case** that is red on HEAD: a walk that has landed at magnification A,
  then a committed magnification B, must render B (`core` unit case, plus an `app`-level case that a
  route fit committed while no walk is pending renders at the fit's magnification).

Not in scope:

- **A walk for magnification writers that currently bypass the zoom funnel.** `fitViewportToRoute`,
  `onToggleFollowMode` and the mode presets write `viewport.magnification` directly instead of going
  through `commitMagnification(target, walk)` (`MapCanvasViewModel.kt:4498`), so a commit larger than
  the frame's window still lands in one frame rather than being walked. That is a second, independent
  finding: with this fix the commit is *rendered* at the committed value (the defect of this change),
  but the *transition* to it is unsmoothed. Recorded, not solved here.
- **The car surface.** `AutoMapRenderer` runs its own walk fields (`zoomWalkTarget`,
  `advanceZoomWalk`), so Android Auto / AAOS are unaffected — phone-only, no parity change.
- **The off-road fix seen in the same session** (`Abseits der Straße` in the navigation overlay): a
  separate observation about the test device's position quality, not a code defect.

## Capabilities

### New Capabilities

None — the change restores the magnification behaviour an existing capability already specifies.

### Modified Capabilities

- `smooth-zoom`: "Eased zoom animation on discrete zoom input" (MODIFIED) — the requirement gains the
  scope of the walk's pin: the render magnification is pinned only while a walk is pending, and a
  magnification committed while none is pending SHALL be rendered at the committed value. Two
  scenarios are added, each naming the case that exercises it.

## Impact

| Where | What changes |
|---|---|
| `core/src/main/java/com/naviveylin/core/ZoomWalk.kt` | `renderMag` falls back whenever the walk is not active (one predicate); the KDoc states the pin's scope |
| `core/src/test/java/com/naviveylin/core/ZoomWalkTest.kt` | the red-on-HEAD case: a landed walk followed by another path's magnification renders that magnification |
| `app/src/test/java/com/naviveylin/ui/map/` | a case that a route-fit commit made while no walk is pending is rendered at the fit's magnification (the collector/renderer is already injectable: `setMapRendererForTest`, `MapRendererBlitTest`/`MapCanvasViewModelFollowModeTest` are the neighbours) |
| `guidelines/MapRendering.md` §11 (the walk entry, lines 476-491) | the pin's scope: it lives exactly as long as the walk |
| `openspec/specs/smooth-zoom/spec.md` | via the delta in this change |

No native/JNI change: the libosmscout submodule, `:osmscout-client-java` and the CMake inputs are
untouched. No manifest, resource, permission, Gradle or dependency change. No persisted format change
(`ZoomWalk.persistMag` is unchanged).

**Additive, not breaking.** No API, no state shape and no stored value changes; a session in which no
walk has landed renders exactly as today. Rollback: revert the one-commit delta (the `:core` predicate
plus the guideline line and the two test cases) — nothing migrates and no device or stored state
depends on it.

**Surface scope:** phone. The shared primitive is instantiated by the phone map only; Android Auto's
renderer carries its own walk, so no car requirement is affected.

**Guidelines referenced:** `guidelines/MapRendering.md` §11 (Auto-Zoom — the walk) is edited in this
change; `guidelines/Design.md` §12's "one source of truth per signal" is the rule the fix follows
(the pin has one owner instead of a cancel obligation spread over every writer). No `UI.md`,
`Logging.md` or `Build.md` change: no log line, control or build input is touched, and the verification
uses the existing coordinate-free tags.

**Prior state:** this defect is not in `TODO.md` and is not owned by an in-flight change. It is filed by
this change; no `TODO.md` entry is created, because the finding is closed by the change itself.
`publish-requested-route-start` (in flight) touches the route's *published start position*, a different
subject; the emulator evidence above was taken with that change unapplied and does not depend on it.
