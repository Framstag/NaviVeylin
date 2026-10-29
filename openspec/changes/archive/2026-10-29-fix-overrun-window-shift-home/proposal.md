# Proposal — fix-overrun-window-shift-home

## Why

**The condition, verified in the tree at the run's base `87cc95de` (2026-10-29).** The overrun-window shift
rule — how far the frame in hand is drawn from the surface center to show a requested displayed point,
clamped into the frame's own overrun margin — is one helper, `FollowPrediction.Companion.displayOffsetPx`
(`core/src/main/java/com/naviveylin/core/FollowPrediction.kt:380-412`), and three paths call it:

```
app/.../ui/map/FollowBlitRules.kt:40            follow drift   (anchor = resolved follow anchor)
app/.../ui/map/PanWindowRules.kt:72             pan window     (anchor = default center)
app/.../ui/map/MapRenderer.kt:1096              coverage pred. (anchor = default center, canvas + slack)
auto/.../auto/AutoMapRendererMotion.kt:192      car blit       (anchor = resolved follow anchor)
auto/.../auto/AutoMapRendererRender.kt:84, :276 car render+marker (anchor = resolved follow anchor)
```

Two of the three meanings have nothing to do with prediction, and the pan/coverage callers pass the default
center anchor — the **absolute** overrun-window shift. A reader of the pan path has to know that a prediction
class owns the pan's geometry, and a new caller cannot tell which of the two meanings it is getting. The
item's own fix candidate (`#73`) names the move.

**Root cause, one sentence.** The helper was extracted from the follow pipeline, where it was the prediction
drift, and was then reused by the pan path and the renderer's coverage predicate, which need the absolute
window shift — so a rule that serves three callers stayed where only one of them would look for it.

**Red test** (`evidence/red-on-head.xml`, run 2026-10-29T16:53:50Z against the base tree's production
sources): the new guard `com.naviveylin.core.OverrunWindowHomeTest` reports `tests="2" failures="2"` on HEAD,
one finding per half of the rule's home:

```
OverrunWindowHomeTest > the prediction class carries no overrun window shift FAILED
  the prediction class must not declare the shift helper under its old name either expected:<0> but was:<1>
OverrunWindowHomeTest > the overrun window is the shift's home FAILED
  the shift's home must exist at .../core/src/main/java/com/naviveylin/core/OverrunWindow.kt
```

**Owning capability and guideline section.** The rule is owned by `openspec/specs/canvas-overrun/spec.md`
("Overrun window shift for pan"), and the two requirements that name the helper are
`openspec/specs/smooth-follow/spec.md` ("The follow blit offset is computed by one seam that owns the
anchor") and `openspec/specs/auto-smooth-follow/spec.md` ("Single resolved anchor in the AA follow blit") —
both text deltas below. The guideline that states the drawn-offset/clamp contract and named the helper nine
times is `guidelines/MapRendering.md` §3 "Follow mode" (bullets on the single anchor application and the
never-negative margin), §5 "Overrun window" and §14 "Android Auto renderer".

**Surface** — the command

```
git grep -l "displayOffsetPx\|DisplayOffset" -- '*.kt' '*.md'
```

reports 21 project files on the base tree (24 with the three submodule documents) and, after the change,
`shiftPx`/`OverrunWindow` in 17 of the 19 Kotlin files listed in Impact (the two `FollowPrediction` files
carry neither token once the helper and its cases are gone). The move is behaviour-preserving: the
`DisplayOffset` fields, the rotated-frame clamp and the `coerceAtLeast(0.0)` margin guard move verbatim, and
the ten geometry cases that drove the old helper move with it.

surface: 16 Kotlin files rewritten (5 phone main, 3 car main, 1 core main, 4 phone test classes, 2 car test
classes, 1 core test class), 1 guideline repointed, 3 files added (the new home, its geometry case class,
the home guard), 10 cases moved without changing an assertion, 0 cases rewritten, 0 fixtures realigned —
`git diff --stat` (17 tracked files, 48 insertions, 269 deletions) plus the three untracked files.

## What Changes

- Add `core/src/main/java/com/naviveylin/core/OverrunWindow.kt`: `object OverrunWindow` with the moved
  `DisplayOffset` result type and `shiftPx(...)` — the same parameters, the same rotation, the same
  `coerceAtLeast(0.0)` margin guard, the same `anchorX`/`anchorY` default of `0.5/0.5`, KDoc naming the three
  callers and the two specs.
- Remove `DisplayOffset` and `displayOffsetPx` from `FollowPrediction` — the class keeps prediction only.
- Update the five phone call sites, the three car call sites and the four test classes that named the old
  helper; `FollowBlitCallSiteTest`'s inline-call needle becomes `OverrunWindow.shiftPx(` so its guard still
  refuses a screen that computes the offset itself.
- Move the ten geometry cases from `FollowPrediction.Test` into `core/.../OverrunWindowTest.kt`, unchanged
  except for the call they drive.
- Add `core/.../OverrunWindowHomeTest.kt`: the prediction class carries no shift helper or result type, and
  `OverrunWindow` carries both — the red case above.
- Repoint the nine `FollowPrediction.displayOffsetPx` mentions in `guidelines/MapRendering.md`.
- Deltas: `smooth-follow` (the seam requirement's first scenario) and `auto-smooth-follow` (the resolved
  anchor requirement's first bullet) now name `OverrunWindow.shiftPx`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `smooth-follow`: "The follow blit offset is computed by one seam that owns the anchor" — the scenario that
  forbids an inline call names the helper's new home; no other text changes.
- `auto-smooth-follow`: "Single resolved anchor in the AA follow blit" — the bullet naming the blit offset
  helper names `OverrunWindow.shiftPx`; the requirement and both scenarios otherwise stand.

## Impact

Files, by module:

- `:core` — `core/src/main/java/com/naviveylin/core/FollowPrediction.kt` (helper and result type removed),
  `core/src/main/java/com/naviveylin/core/OverrunWindow.kt` (new),
  `core/src/test/java/com/naviveylin/core/FollowPredictionTest.kt` (shift cases moved out),
  `core/src/test/java/com/naviveylin/core/OverrunWindowTest.kt` (new, the moved cases),
  `core/src/test/java/com/naviveylin/core/OverrunWindowHomeTest.kt` (new, the guard).
- `:app` — `ui/map/FollowBlitRules.kt`, `ui/map/PanWindowRules.kt`, `ui/map/MapCanvasScreen.kt` (the
  `panWindowOffset` return type), `ui/map/MapCanvasMapBand.kt` (the parameter type), `ui/map/MapRenderer.kt`
  (the coverage predicate); tests `FollowAnchorFramingTest.kt`, `FollowBlitCallSiteTest.kt`,
  `MapCanvasViewModelVehicleAnchorTest.kt`, `MapPanDisplayWindowTest.kt`.
- `:auto` — `AutoMapRendererFollow.kt` (the diagnostic line's parameter type),
  `AutoMapRendererMotion.kt`, `AutoMapRendererRender.kt` (two call sites); tests
  `AutoMapRendererBlitTest.kt`, `AutoMapRendererFollowAnchorTest.kt`.
- `guidelines/MapRendering.md` — the nine mentions; `openspec/specs/{smooth-follow,auto-smooth-follow}` via
  the deltas above.

No manifest, resource, Gradle, native or JNI file changes: the helper is unchanged Kotlin in `:core`, both
variants already depend on it, and no signature's argument order or meaning moves.

**Additive or breaking.** Source-compatible inside the repository, and breaking only for a caller of the
removed symbol — there is none outside the files listed, all of which this change updates. Rollback path:
revert the commit; no persisted state, no device state and no public API is involved.

**Scope.** Both variants: the phone follow/pan/coverage paths and the car follow blit. The second debt the
item carries — that follow expresses the drift through the anchor center of the displayed position while
pan translates the overlay layer — is **not** touched here; it stays recorded for its own item, as the
item's decision of 2026-10-29 requires.
