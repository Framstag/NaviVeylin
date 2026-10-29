# Design — fix-overrun-window-shift-home

## D1 — Where the shift rule lives

**The rule, restated.** `DisplayOffset(rawX, rawY, clampedX, clampedY)` plus one function: project a displayed
position against the frame's own viewport with `ProjectionUtils.geoToScreen` (which bakes no rotation),
rotate the delta by the viewport angle, subtract the anchor's rotated-frame offset, and clamp both axes into
`(bitmap − canvas)/2`, coerced at zero.

**Chosen:** a new `core/src/main/java/com/naviveylin/core/OverrunWindow.kt` — `object OverrunWindow` with
`data class DisplayOffset` and `fun shiftPx(...)`. The body moves verbatim; only the receiver and the name
change. Rationale: the name states what is being computed (an overrun-window shift, not a prediction), the
home sits beside the other pure geometry in `:core`, and the pan/coverage callers no longer read a class
whose documented subject is between-fix extrapolation.

**Alternative A — keep it on `FollowPrediction` and only rename the function to `shiftPx`.** Eliminated by
the finding itself: `FollowPrediction`'s documented subject is the between-fix speed model, and two of the
three callers (`PanWindowRules.offsetFor`, `MapRenderer.overrunWindowCovers`) never touch a prediction — a
renamed function on that class still tells a reader of the pan path that the pan's geometry is a prediction
concern. The item's own fix candidate names the neutral home for exactly this reason.

**Alternative B — put it on `core/ProjectionUtils`.** Eliminated: `ProjectionUtils` is the Mercator
vocabulary (scale factors, `geoToScreen`, rotated viewports, drag→center conversion) shared with the native
side's formula. The shift carries a display decision — a bitmap/canvas margin, a clamp, an anchor fraction —
and none of `ProjectionUtils`' functions knows what a canvas or an overrun margin is.

## D2 — The names the call sites speak

**Chosen:** `OverrunWindow.shiftPx(...)` for the function, `DisplayOffset` for the result type, and the
parameter names unchanged.

The function name is the item's fix candidate and describes the value: a shift in pixels. The result type
keeps `DisplayOffset` because the value *is* a display offset — the name is not what misled; the class it
lived on was — and every field (`rawX`, `rawY`, `clampedX`, `clampedY`, `clamped`) is read by call sites and
by diagnostics.

**Alternative A — rename the type to `Shift`, with `shift`/`clamped` fields.** Eliminated as churn with no
reader gain: it rewrites 24 call-site expressions and the KDoc of four files to say what the new object's
name already says, in a change whose whole claim is that nothing about the value changes.

**Alternative B — split the two meanings into two functions (`driftPx` with an anchor, `shiftPx` without).**
Eliminated for now, and recorded as the second debt's problem, not this change's: the two meanings are one
computation with a defaulted parameter, and splitting it would have to decide which one the follow display
loop uses while the frame is anchor-centered — a behaviour question the item explicitly defers to the
overlay-unification half. The default (`0.5/0.5` = the absolute shift) keeps today's contract.

## D3 — The move lands alone, without the overlay unification

**Chosen:** this change takes the item's Option 1 (the move), and the second debt — follow expressing the
drift through the anchor center of the displayed position while pan translates the overlay layer by the
clamped offset — is re-filed as its own item.

**Alternative A — land the overlay unification first.** Eliminated by the record:
`openspec/changes/archive/2026-09-27-fix-phone-gesture-pan-tracking/design.md:98` defers it as a larger
change that "rewrites the verified follow path … must not depend on" this work, and the item's decision of
2026-10-29 states that the two halves must not share one verified path.

**Alternative B — land both in one change.** Eliminated for the same reason plus its cost: ~30 files, two
capability deltas and a behaviour change on a path three archived fixes already repaired.

**Alternative C — retire the item as a latent-risk note.** Eliminated: the item carries an
owner-supplied fix candidate, the move cannot change behaviour, and the retired `TODO.md` §78 pointer would
stay dead.

## Threading and lifecycle

None introduced. `OverrunWindow.shiftPx` is a pure function over doubles and ints, exactly as
`FollowPrediction.displayOffsetPx` was; the phone call sites keep running where they run today (the follow
display loop on the main thread, `MapRenderer.overrunWindowCovers` on the caller's thread under
`bufferLock` for its frame snapshot) and the car call sites keep the `surfaceLock` discipline they already
hold. No new component, no dispatcher, no lifecycle owner.

## Verification

- **Source-level guard (the red case):** `core: OverrunWindowHomeTest` — the prediction class declares no
  shift helper and no `DisplayOffset`, and the new home declares both. `tests="2" failures="2"` on the base
  tree, green after the move (`evidence/red-on-head.xml`, `evidence/green-core.xml`).
- **Geometry (moved, not rewritten):** `core: OverrunWindowTest` — the ten cases that drove
  `displayOffsetPx` (zero shift when aligned, geo delta inside the margin, clamp at the margin, rotation
  with the viewport angle, the anchor-drift cases, and the two margin-less orientation handoffs). Their
  assertions are unchanged; only the receiver and the function name differ.
- **Call sites:** `app: FollowAnchorFramingTest`, `MapPanDisplayWindowTest`,
  `MapCanvasViewModelVehicleAnchorTest`, `FollowBlitCallSiteTest`; `auto: AutoMapRendererBlitTest`,
  `AutoMapRendererFollowAnchorTest` — the same contracts, now through the new home.
- **No on-device measurement is owed, and none is claimed.** The change moves a pure function and renames
  its callers; no projection, fit, clamp value, thread or frame cadence changes, so a device run could only
  reproduce the pre-change numbers. That is stated here rather than implied by a green suite: the property
  the item names is a code-organisation property, and the guard above is its measurement.
- **Gate:** the full both-flavor gate (`./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache`) plus
  the shell checks `tools/loop/gate.sh --base 87cc95de` selects for the touched paths.
