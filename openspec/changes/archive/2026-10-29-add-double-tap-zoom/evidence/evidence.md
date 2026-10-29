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
  `tests="15" failures="1"`, case `twoTapsInsideTheDoubleTapWindowReportOneDoubleTap`, 2026-10-29T12:37:29Z.
  `red-on-head.log` is its console log.
- `revert-check.xml` / `revert-check.log` — the same case with the tap pairing removed from the *finished*
  code (revert-check, 2026-10-29T12:42:51Z): `tests="15" failures="1"` for the gesture suite,
  `tests="2" failures="0"` for `MapCanvasDoubleTapZoomTest`.

## The green cases

- `green-after-fix-gesture.xml` — `MapGestureComposeTest`: `tests="15" failures="0" errors="0"`
  (2026-10-29T13:14:01Z).
- `green-after-fix-doubletapzoom.xml` — `MapCanvasDoubleTapZoomTest`: `tests="2" failures="0" errors="0"`
  (same run). `green-after-fix.log` is the console log of that run. Re-run after the gate for one reason: the
  requirement names the comments cite were corrected after the gate (comment text only — no code, no test
  assertion), so this pair matches the tree as it stands. The gate was not re-run.
- Both were produced by
  `./gradlew --offline :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapGestureComposeTest"
  --tests "com.naviveylin.ui.map.MapCanvasDoubleTapZoomTest" -PforceTests --no-build-cache`.

## The gate (run once, from the change root)

- `gate.log` — `./tools/loop/gate.sh`, exit 0, wall clock 1549 s (`date +%s` before and after the run). Its last
  line is the gate's own summary, the `LOOP-GATE:` line quoted in this item's report; it names all 23 suites
  and their seconds (the compiled gate `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` 364 s,
  `tools/loop-selftest.sh` 619 s, …).
- `gate-tallies/` — the 23 per-suite JUnit tallies the gate wrote; every one reports `failures="0"`, and
  `grep -c 'failures="0"' evidence/gate-tallies/*.xml` counts them.

## The measurement

- `measure.txt` — the block `./tools/loop/measure.sh` printed before planning (newest archived change at
  that time, `LOOP-MEASURE-CHANGE 2026-10-29-fix-ui-navigation-content-zero-distance`).
- `measure-after.txt` — after the plan and the gate: `LOOP-MEASURE-CHANGE add-double-tap-zoom scenarios=4
  cases=4 unnamed=0`, `LOOP-MEASURE-GATE seconds=1546 suites=23`, `LOOP-MEASURE-SUITE cases=277
  cmd=tools/loop-selftest.sh --list`.

## The archive

- `openspec-validate.log` — `openspec validate add-double-tap-zoom --strict` → "Change 'add-double-tap-zoom'
  is valid".
- `openspec-archive.log` — `openspec archive add-double-tap-zoom --yes` → "Specs updated successfully",
  "archived as '2026-10-29-add-double-tap-zoom'", `smooth-zoom: + 2 added`.
- `evidence-check.log` — `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-29-add-double-tap-zoom
  --root .` → `evidence-check: PASS` (C1 7 §references, C2 7 tally pairs, C3 none, C4 clean, C5 330 file:line
  resolved).
