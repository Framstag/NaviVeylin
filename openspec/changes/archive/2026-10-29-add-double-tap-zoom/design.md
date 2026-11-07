# Design — add-double-tap-zoom

## Root of the work

The capability is absent, not broken: the map canvas gesture handler reports pan, two-finger
pan/rotate/zoom and long press and nothing else (`MapGestures.kt:57`, `MapGestureComposeTest` has no
double-tap case). The zoom *display* half already exists and already names the missing input
(`ZoomAnimation.kt:22`). So the work is one input added to one state machine, wired to the transition the
buttons and the wheel already use.

## D1 — Where the gesture is detected: the existing single state machine, not a second detector

`MapGestures.kt` states its own reason: "A single pointerInput block handles all gestures to avoid conflicts
between competing detectors" (`MapGestures.kt:68`). The alternative — a sibling `pointerInput { detectTapGestures(onDoubleTap = …) }` —
was eliminated by that: the gesture block consumes the down event while it waits (`change.consume()`,
`MapGestures.kt:146`), and a competing `detectTapGestures` awaits an *unconsumed* down, so the two detectors
would race for the same press depending on modifier order. The state the pairing needs (the last tap's up
time and point) therefore lives in the `pointerInput` scope, before `awaitEachGesture`, so it survives from
one gesture to the next and is dropped when the block is keyed anew.

The pairing uses the platform's own numbers — `ViewConfiguration.doubleTapTimeoutMillis` and `touchSlop` —
never literals, the same rule the long-press timeout already follows in this file. A drag, a pinch (drag or
second finger) and a long press clear the pending tap, so a third tap can never complete a pair with a
gesture that was not a tap.

## D2 — What a double tap commits: one level, clamped, anchored with the wheel's math

The step is `round(recorded magnification) + 1`, clamped at `MAX_MAG` (`doubleTapZoomMagnification`), because
`zoomIn()` and `zoom-controls` already define a discrete zoom step as one whole level; a pinch can leave the
recorded magnification fractional, and a double tap must land on a level, not on the pinch remainder.

The centre is committed with `ProjectionUtils.zoomAtCursor` — the scroll-wheel path's own call
(`MapCanvasMapBand.kt:514`) — which keeps the geographic point under the tap pixel. The alternative (commit
the level and let the display scale drift around the screen centre) was eliminated by the acceptance
scenario: the point under the finger would slide away, which is the failure `smooth-zoom`'s anchor requirement
forbids for every other discrete input.

At `MAX_MAG` the function returns before it touches the viewmodel: no step exists, so neither the centre nor
the display may move (the `zoom-controls` rule for the disabled zoom-in button, expressed for a gesture that
has no disabled state).

## D3 — The transition: the existing eased animation, anchored on the tap point

`animateDiscreteZoom(pos)` starts or retracks `ZoomAnimation` exactly as the buttons and the wheel do, so the
200-300 ms ease-out, the retrack-without-snap rule and the render-crossfade handoff apply unchanged. The
alternative (a bespoke double-tap animation) was eliminated by the recorded owner decision, which names the
existing smooth-zoom animation path as the place to wire it, and by `smooth-zoom` — "Retracking on rapid zoom
input": a repeat double tap retracks the running transition.

**The one repair this needs.** The frame loop retargeted a running transition on the *screen centre* whenever
the walked magnification changed (`MapCanvasFollowDisplay.kt`, stepped-magnification branch). For the buttons
and for auto-zoom that is the same pixel their commit used, so the reset was invisible; for an input that
anchors elsewhere — a double tap, and the scroll wheel before it — it moved the pivot one frame after the
input set it, so the frame in hand scaled around the centre while the committed centre kept the tap point
fixed. The retrack now keeps the animation's own anchor (`zoomAnchorState`, the pixel the input passed in)
and falls back to the follow/centre anchor only when no anchor is held. This is the mechanism, not a second
feature: without it the requirement's "for every frame of the transition" is false.

## D4 — Follow mode and the drive preset

A double tap calls `disengageFollowMode()` and then `updateMagnification`, which is the path every manual
zoom takes: in `FREE_DRIVE`/`NAVIGATION` it suspends auto-zoom (`MapCanvasViewModelCamera.kt:128`), and
`map-modes` states that a manual zoom suspends the drive preset. The alternative (an explicit mode reset in
the double-tap path) was eliminated as a second implementation of a rule that already has one home.

## Blast radius

- One gesture state machine gains one branch and one callback (default no-op, so no other implementer
  changes); one composable passes the callback to the existing seam function.
- No render, walk, epoch, persistence or native code is touched; `ZoomWalk`, `ZoomAnimation` and
  `ProjectionUtils` are used, not changed.
- The frame loop's retrack anchor is one expression; a zoom animation started by any input now keeps the
  anchor that input passed, which for the buttons and auto-zoom is the same pixel as before.
- The `smooth-zoom` capability gains two requirements; `guidelines/MapRendering.md` §11 gains one clause.
  Android Auto is unaffected (no touch surface of its own).

## Rollback

Revert the commit. The callback is declared with a no-op body, so the screen wiring is the only caller of the
new helpers; the gesture branch's state is local to the `pointerInput` scope.

## Verification

- Red case: `MapGestureComposeTest` "twoTapsInsideTheDoubleTapWindowReportOneDoubleTap" —
  `tests="15" failures="1"` on HEAD (`evidence/red-on-head.xml`), and red again with the pairing removed from
  the finished code (`evidence/revert-check.xml`), green with it (`evidence/green-after-fix-gesture.xml`).
- Affected cases: `MapCanvasDoubleTapZoomTest` (the step, the anchor, the maximum) —
  `tests="2" failures="0"` (`evidence/green-after-fix-doubletapzoom.xml`); the gesture suite's pan, pinch,
  rotation and long-press cases stay green (`tests="15" failures="0"`).
- Gate: `./tools/loop/gate.sh` once, from the change root, quoted in `evidence/gate.log`.
- On device (not run in this budgeted item): the double tap is a touch gesture with no coordinate in any log
  line; the diagnostics that already exist for the zoom walk (`MapCanvasViewModel` "zoomWalk request/step")
  carry target and step magnification only, which is the identity form `Logging.md` §1 allows.
