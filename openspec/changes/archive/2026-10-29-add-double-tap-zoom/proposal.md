# Proposal: add-double-tap-zoom

## Why

**The missing capability, verified in the tree at base `821d9fc1` (2026-10-29).** The map canvas has one
gesture state machine (`app/src/main/java/com/naviveylin/ui/map/MapGestures.kt:76` `mapGestureHandler`) and it
reports pan, two-finger pan/rotate/zoom and long press only — its callback set ends at `onLongPress`
(`MapGestures.kt:57`). Nothing in the tree produces a double tap, and the screen says so in words:
`MapCanvasScreen.kt:487` reads "discrete zoom input (buttons, scroll wheel, keyboard — no double-tap zoom
exists)". Measured, `evidence/absence-on-head.log`:

```
$ git show 821d9fc1:app/src/main/java/com/naviveylin/ui/map/MapGestures.kt | grep -c onDoubleTap
0
$ git show 821d9fc1:app/src/test/java/com/naviveylin/ui/map/MapGestureComposeTest.kt | grep -c "doubleTap|doubleClick"
0
```

The zoom display path such a gesture needs already exists: `ZoomAnimation` (`core/.../ZoomAnimation.kt`)
eases the front buffer toward a committed magnification and retracks a running transition, and its own
documentation already names the input that never arrives ("repeat button taps, wheel ticks, double taps",
`ZoomAnimation.kt:22`). The discrete input set is enumerated in one place —
`openspec/specs/smooth-zoom/spec.md:11` and `:57` — and today it names the zoom buttons, the scroll wheel,
the keyboard and auto-zoom; the map canvas surface itself, which is where a phone user zooms, offers no
one-handed zoom gesture at all.

**The missing capability, one sentence.** The map canvas gesture handler reports no double-tap, so a user on
the map surface can only zoom through the right-side buttons or a two-finger pinch — the platform's
double-tap-to-zoom gesture, which the recording of item #51 asks for, is absent.

**The acceptance scenario, as the red case.** Two taps inside the platform's double-tap window and touch
slop, on the same point, are one double tap and commit one magnification level anchored on that point.
`MapGestureComposeTest` "twoTapsInsideTheDoubleTapWindowReportOneDoubleTap" fails on HEAD:
`tests="15" failures="1"` (`evidence/red-on-head.xml`, 2026-10-29T12:37:29Z — HEAD plus the declared callback,
body absent); with the pairing removed from the finished code the same case fails again
(`evidence/revert-check.xml`, `revert-check.log`, 2026-10-29T12:42:51Z), and with it present it passes
(`evidence/green-after-fix-gesture.xml`, 2026-10-29T12:43:35Z).

**Owner decision, from item #51 (`<<<ITEM_DECISION`).** `Option 3` — split the item and take the two
capabilities in sequence: the double-tap zoom (option 2, "the smaller and cheaper of the two") first, the
small-screen layout (option 1) in a run of its own. This change is that first step of the recorded order.

**Owning capability and guideline.** `smooth-zoom` owns the eased zoom animation, its anchors and the set of
discrete inputs that retrack it (`openspec/specs/smooth-zoom/spec.md` §"Eased zoom animation on discrete zoom
input", §"Geographic anchor stays fixed during zoom animation"). The rule for where the phone's display
animation pivots lives in `guidelines/MapRendering.md` §11 "Auto-Zoom" (`:517`), which this change extends by
one clause.

**Surface counted before the plan** (`evidence/surface-count.log`, the command it prints): the "which
discrete zoom inputs exist and what each anchors on" rule is asserted in 5 places —
`openspec/specs/smooth-zoom/spec.md` (2 statements: the discrete-input enumeration and the anchor
requirement), `guidelines/MapRendering.md:515`, `MapCanvasScreen.kt:487` and `MapCanvasScreenParts.kt:218`
(two comments that state the input set), and the generated catalogue prose
(`FEATURES.md:313` / `tools/feature-list/cache/map-interaction.md:2`, an area summary, left as it is).
`surface: 2 cases added, 0 rewritten, 2 comments realigned, 0 fixtures — evidence/surface-count.log`.

## What Changes

1. **The gesture.** `mapGestureHandler` reports a double tap when two taps inside the platform's
   double-tap window (`ViewConfiguration.doubleTapTimeoutMillis`) and touch slop land on one point, at the
   point the second tap lifts. The state lives in the `pointerInput` scope, inside the existing single
   gesture block, so no second competing detector is added. A drag, a pinch or a long press consumes a
   pending tap instead of pairing with it.
2. **The step.** A double tap commits the next magnification level above the recorded one, clamped at
   `MAX_MAG` (`doubleTapZoomMagnification`), and moves the centre with `ProjectionUtils.zoomAtCursor` so the
   geographic point under the tap stays under it — the scroll-wheel path's own math.
3. **The animation.** The step runs through the existing smooth-zoom transition, anchored on the tap point
   (`animateDiscreteZoom(anchor)`), exactly as the zoom buttons anchor the screen centre and the wheel the
   cursor. The frame loop's walked-step retrack keeps the anchor the input started with instead of resetting
   it to the screen centre, which is what makes the tap point stay fixed for every frame of the walk.
4. **A double tap is a manual zoom**: it disengages follow mode and suspends the drive preset, like every
   other manual zoom input (`map-modes`).

## Capabilities

- `smooth-zoom` — two ADDED requirements: "Double tap is the map's second discrete zoom-in input" (the tap
  pairing, its platform window and the taps that do not pair) and "A double tap steps one level at its own
  point" (the level step, the anchor and the maximum-magnification case). Additive; no existing requirement is
  modified.
- guideline `guidelines/MapRendering.md` §11 — one clause: the display animation pivots on the point a double
  tap landed on, beside the scroll wheel's cursor.

Not touched: the zoom buttons, the scroll wheel, the pinch commit, the walk arithmetic (`ZoomWalk`), the
persisted viewport format, and the generated catalogue prose (an area summary generated from the cache, not
a contract).

## Impact

| Area | Files |
|------|-------|
| Gesture handler | `app/src/main/java/com/naviveylin/ui/map/MapGestures.kt` (`onDoubleTap` callback, tap pairing in the gesture state machine, doc comment) |
| Screen seam | `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreenParts.kt` (`doubleTapZoomMagnification`, `fireDoubleTap`, `zoomAnimationAnchor` doc comment) |
| Wiring | `app/src/main/java/com/naviveylin/ui/map/MapCanvasMapBand.kt` (`onDoubleTap` → `fireDoubleTap`), `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (stale comment), `app/src/main/java/com/naviveylin/ui/map/MapCanvasFollowDisplay.kt` (walked-step retrack keeps the input's anchor) |
| Cases | `app/src/test/java/com/naviveylin/ui/map/MapGestureComposeTest.kt` (2 cases), `app/src/test/java/com/naviveylin/ui/map/MapCanvasDoubleTapZoomTest.kt` (2 cases, new) |
| Spec / guideline | `openspec/changes/archive/2026-10-29-add-double-tap-zoom/specs/smooth-zoom/spec.md`,
`guidelines/MapRendering.md` §11 |

No compiled native code, no JNI, no new dependency, no new screen. Android Auto is untouched: the Car App
Library templates have no touch surface of their own (`UI.md` §1), so there is no parity change to state.

## Rollback

Revert the change's commit. It is additive: the gesture callback is declared with a no-op body, so nothing
else calls it, the new pure helpers have no caller outside the wiring, and the viewport, the persisted JSON
and the walk arithmetic are unchanged — old data loads and old gestures behave exactly as before.
