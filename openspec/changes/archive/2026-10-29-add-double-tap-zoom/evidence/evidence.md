# Evidence — add-double-tap-zoom (issue #51, item 51)

Read in this order. Every number below is the output of a command, quoted beside it.

## The absence, at the base commit

`absence-on-head.log` — `git show 821d9fc1:app/src/main/java/com/naviveylin/ui/map/MapGestures.kt | grep -c
onDoubleTap` → `0`; the same for a double-tap case in `MapGestureComposeTest` → `0`; and
`MapCanvasScreen.kt` at HEAD says "no double-tap zoom exists" (`:487`).

`surface-count.log` — the "which discrete inputs exist and what each anchors on" rule was asserted in 5
places before the change (spec 2 statements, guideline, 2 comments, catalogue prose).

## The red case

- `red-on-head.xml` — HEAD plus the declared `onDoubleTap` callback (body absent):
  `tests="15" failures="1"`, case `twoTapsInsideTheDoubleTapWindowReportOneDoubleTap`, 2026-10-29T12:37:15.857Z.
  `red-on-head.log` is its console log.
- `revert-check.xml` / `revert-check.log` — the same case with the tap pairing removed from the *finished*
  code (revert-check, 2026-10-29T12:42:37.361Z): `tests="15" failures="1"` for the gesture suite,
  `tests="2" failures="0"` for `MapCanvasDoubleTapZoomTest`.

## The green cases

- `green-after-fix-gesture.xml` — `MapGestureComposeTest`: `tests="15" failures="0" errors="0"`
  (2026-10-29T13:13:45.554Z).
- `green-after-fix-doubletapzoom.xml` — `MapCanvasDoubleTapZoomTest`: `tests="2" failures="0" errors="0"`
  (same run). `green-after-fix.log` is the console log of that run. The classes hold 16 and 4 cases now: round 2
  added one and two (`round2/green-gesture.xml`, `round2/green-gesture-and-doubletap-zoom.xml`).
- Both were produced by
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapGestureComposeTest"
  --tests "com.naviveylin.ui.map.MapCanvasDoubleTapZoomTest" -PforceTests --no-build-cache`.

## The gate (run once, from the change root)

- `gate.log` — `./tools/loop/gate.sh`, exit 0. Its last
  line is the gate's own summary, the `LOOP-GATE:` line quoted in this item's report; it names all 23 suites
  and their seconds (the compiled gate `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` 364 s,
  `tools/loop-selftest.sh` 619 s, …).
- `gate-tallies/` — the 23 per-suite JUnit tallies the gate wrote; every one reports `failures="0"`, and
  `grep -c 'failures="0"' evidence/gate-tallies/*.xml` counts them.

## The measurement

- `measure.txt` — the block `./tools/loop/measure.sh` printed before planning (newest archived change at
  that time, `LOOP-MEASURE-CHANGE 2026-10-29-fix-ui-navigation-content-zero-distance`).
- `measure-after.txt` — after the plan and the gate: `LOOP-MEASURE-CHANGE add-double-tap-zoom scenarios=4
  cases=4 unnamed=0`, `LOOP-MEASURE-SUITE cases=277
  cmd=tools/loop-selftest.sh --list`. The gate is its own record (`round2/gate.log`); a second reading of its
  wall clock here would be a number no command prints (round 2, note 4).

## The archive

- `openspec-validate.log` — `openspec validate add-double-tap-zoom --strict` → "Change 'add-double-tap-zoom'
  is valid".
- `openspec-archive.log` — `openspec archive add-double-tap-zoom --yes` → "Specs updated successfully",
  "archived as '2026-10-29-add-double-tap-zoom'", `smooth-zoom: + 2 added`.
- `evidence-check.log` — `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-29-add-double-tap-zoom
  --root .` → `evidence-check: PASS` (C1 §references 7 checked, 5 exempted as archived pointers; C2 test
  tallies 21 pairs confirmed, 0 drift; C3 no /tmp pointer; C4 clean; C5 file:line 513 resolved and in range).
  Re-run after round 2 raised the tally count from 12 to 21.

## Review round 1 (2026-10-30) — the findings and what was done with them

The round was announced on 2026-10-29 (`review-round-1-requested.md`) and never answered in that run;
it was graded on 2026-10-30, against the branch as it stands. Verdict `FAIL`, three blocking findings,
seven notes. Each is answered here, and the corrections are commits on the same branch.

- **BLOCKING, `MapCanvasScreenParts.kt` — the commit asked for no frame.** `fireDoubleTap` committed the
  level and the centre and never called `renderMap`, which is the only entry into `MapRenderer` and the
  only path that asks the walk for its first step; the double tap would have eased toward a level no frame
  carries. **Fixed**: `viewModel.renderMap()` after `updateMagnification`, as the zoom buttons and the
  keyboard do, with one new case — `MapCanvasDoubleTapZoomTest`
  "double tap requests the frame its committed level needs" asserts the renderer is asked (`currentMag`
  moves), and it is red with the line commented out ("got mag=5.0 unchanged").
- **BLOCKING, `MapGestures.kt` — the window measured first-up→second-up.** `ViewConfiguration`'s
  double-tap timeout runs first-up→second-**down**, so a legitimate second press lost its own duration
  from the window, and the slop compared the second tap's down point with the first tap's **up** point.
  **Fixed**: the pair is measured with `down.uptimeMillis` against the first tap's up, and the slop between
  the two down points; one new case — `MapGestureComposeTest` "aSlowSecondTapInsideTheWindowIsStillOneDoubleTap"
  (100 ms between the taps, a 250 ms second press: up→up 350 ms, up→down 100 ms) — is red with the old
  comparison restored ("got [] expected:<[Offset(160.0, 235.0)]>").
- **BLOCKING, an unasserted scenario statement.** The merged scenario "Taps beyond the platform's window
  are two single taps" carried "AND the viewport magnification SHALL NOT change", which no case observes:
  `MapGestureComposeTest` holds no view model. **Fixed subtractively** — the statement is replaced by
  "THEN no double tap SHALL be reported, so no zoom step is committed", which the existing case does assert,
  in the shipped spec and in this change's delta alike.
- **NOTE, stale timestamps.** The green, red and revert-check stamps quoted in `proposal.md`, `tasks.md` and
  this file were wall-clock readings that matched no file; they now quote each XML's own `testsuite`
  timestamp. Same for the stale `evidence-check` counts (C2 7→12, C5 330→390, and C1's checked/exempted split).
- **NOTE, `MapRendering.md` wording.** "A retracked step keeps the pivot of the input that started the
  animation, never the center" contradicted the same bullet's "the center for the buttons and the keyboard";
  reworded to name the input's own pivot and say which inputs have the center.
- **NOTE, `Offset.Zero` as "no anchor"** (`MapCanvasFollowDisplay.kt`): a double tap landing on the canvas
  origin would be read as "no anchor" and the next walked-step retrack would pivot on the centre. Not fixed
  here: `zoomAnchorState` is one state with six readers across three composables, and a sentinel change is
  its own change with its own cases; recorded so the next reader meets it as a known bound, not a discovery.
- **NOTE, `tasks.md` 2.3 / spec's manual-zoom clause UNPROVEN**: no case retracks with a non-centre input
  anchor and no case asserts follow disengagement after a double tap. The task and the clause now say
  "unproven" rather than "verified"; the cases are new scope.
- **NOTE, the shipped enumerations.** `smooth-zoom`'s requirement list still names "zoom buttons, scroll
  wheel, keyboard plus/minus" as the discrete inputs. Extending it needs a `MODIFIED` requirement, which is
  a spec change of its own; this change's delta says "no existing requirement is modified" and stays true.

## Round 2 (2026-10-30): the corrections' own artifacts

The three corrections and their cases were re-measured on the corrected tree, and every reading below is a
file in `round2/`:

- `round2/red-1-no-render.xml`, `round2/red-1-no-render.log` — the corrected tree with
  `MapCanvasScreenParts.kt`'s `viewModel.renderMap()` commented out: `MapCanvasDoubleTapZoomTest`
  `tests="4" failures="1"`, the failing case being "double tap requests the frame its committed level needs"
  (`the double tap asks the renderer for a frame, got mag=5.0 unchanged`). Restored, the same class is
  `tests="4" failures="0"`.
- `round2/red-2-up-to-up.xml`, `round2/red-2-up-to-up.log` — the corrected tree with the pair compared
  first-up→second-up (the pre-correction comparison): `MapGestureComposeTest` `tests="16" failures="1"`, the
  failing case being "aSlowSecondTapInsideTheWindowIsStillOneDoubleTap"
  (`the window is measured up→down, not up→up, got [] expected:<[Offset(160.0, 235.0)]>`). Restored, the same
  class is `tests="16" failures="0"` (`round2/green-gesture.xml`).
- `round2/green-gesture-and-doubletap-zoom.xml` — `MapCanvasDoubleTapZoomTest` `tests="4" failures="0"` on the
  corrected tree.
- `round2/gate.log` — the whole gate on the corrected tree,
  `./gradlew --offline test -PforceTests -PjvmTestsOnly --no-build-cache` → `BUILD SUCCESSFUL in 5m 40s`;
  `:app` mobile 2145 / automotive 2145 / `:auto` 886 / `:core` 643 / `:osmscout-client-java` 112, all
  `failures=0 errors=0` (the branch's tree is 11 days behind `main`'s, hence the slightly lower counts).

The document checks were run on the same tree: `tools/check-doc-routes.sh` 155 section references,
`tools/check-todo-references.sh` 64 citations, `tools/check-openspec-hygiene.sh` consistent,
`tools/check-compose-sandbox-rule.sh` 71 classes, `tools/gen-feature-list.sh --check-classification` exit 0.

## Round 2 (2026-10-30): the notes

- The `MapRendering.md` sentence now says a retracked step keeps **the last anchor the display was given**, which
  is what the code does: `MapCanvasMapBand.kt` writes the anchor of a gesture centroid even when that gesture
  neither zoomed nor rotated (`note 1`), so "the input that started the animation" overstated it.
- The comment in `fireDoubleTap` names the scroll wheel as the recorded exception to "a commit requests its
  frame" instead of claiming every input does it (`note 2`) — the wheel's own gap is outside this change.
- The requirement and its scenario now say **the next whole level** and name the fractional band
  (`10.4 → 11`, `10.6 → 12`, the zoom button's snapped step), and the case
  "double tap from a level past the half steps to the next whole level" pins `10.6 → 12.0` (`note 3`).
- The two wall-clock readings in "The measurement" are gone; the gate's own `LOOP-GATE` line is the record
  (`note 4`).
