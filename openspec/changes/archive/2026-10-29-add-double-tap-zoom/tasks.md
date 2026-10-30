# Tasks — add-double-tap-zoom

## 1. Declared seam and red case (before the behaviour)

- [x] 1.1 Declare `MapGestureCallbacks.onDoubleTap(position)` with a no-op default body, so a case can be
      written against the surface a caller will use. Verify: the interface compiles and nothing calls it —
      `git show 821d9fc1:app/src/main/java/com/naviveylin/ui/map/MapGestures.kt | grep -c onDoubleTap` reports
      `0` (the capability was absent at HEAD, `evidence/absence-on-head.log`).
      *(spec: `smooth-zoom` — Double tap is the map's second discrete zoom-in input)*
- [x] 1.2 Write the red case `MapGestureComposeTest` "twoTapsInsideTheDoubleTapWindowReportOneDoubleTap" and
      its guard "twoTapsBeyondTheDoubleTapWindowReportNoDoubleTap". Verify on the tree without the pairing:
      `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapGestureComposeTest"
      -PforceTests --no-build-cache` reports `tests="15" failures="1"`, the failing case named
      (`evidence/red-on-head.xml`, 2026-10-29T12:37:15.857Z).
      *(spec: `smooth-zoom` — Two taps inside the platform's window are one double tap; Taps beyond the
      platform's window are two single taps)*

## 2. Behaviour

- [x] 2.1 Pair the taps in the existing gesture state machine: `pendingTapUpMs`/`pendingTapPos` in the
      `pointerInput` scope, the platform's `doubleTapTimeoutMillis` and `touchSlop` as the window, a pending
      tap cleared by a drag, a pinch and a long press. Verify: the red case passes and every pan, pinch,
      rotation and long-press case of `MapGestureComposeTest` stays green — `tests="16" failures="0"` (round 2 added one: `evidence/round2/green-gesture.xml`)
      (`evidence/green-after-fix-gesture.xml`, 2026-10-29T13:13:45.554Z).
      *(spec: `smooth-zoom` — Two taps inside the platform's window are one double tap)*
- [x] 2.2 Add the seam `doubleTapZoomMagnification` (one level, clamped at `MAX_MAG`) and `fireDoubleTap`
      (disengage follow, `zoomAtCursor` centre, commit the level, animate around the tap point) in
      `MapCanvasScreenParts.kt`, beside `fireLongPress`, and wire the map band's `onDoubleTap` to it. Verify:
      `MapCanvasDoubleTapZoomTest` reports `tests="4" failures="0"` (round 2 added two: `evidence/round2/green-gesture-and-doubletap-zoom.xml`)
      (`evidence/green-after-fix-doubletapzoom.xml`).
      *(spec: `smooth-zoom` — A double tap steps one level around the tap point; A double tap at the maximum
      magnification changes nothing)*
- [x] 2.3 Keep the input's anchor across a walked-step retrack in `MapCanvasFollowDisplay.kt` (the follow
      anchor stays the fallback). Verify: the zoom-walk cases still pass in the gate. **Not proven**: no case
      retracks a running animation with a non-centre input anchor, so "the tap point stays the pivot for every
      frame" is the design's claim, not a measured one (review round 1, note 7).
      *(spec: `smooth-zoom` — Geographic anchor stays fixed during zoom animation)*
- [x] 2.4 Realign the two comments that state the input set — `MapCanvasScreen.kt:487` ("no double-tap zoom
      exists") and the `zoomAnimationAnchor` doc in `MapCanvasScreenParts.kt`. Verify:
      `grep -n 'double tap' app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` names it as an input.
      *(spec: `documentation-ownership` — A rule has one normative home)*

## 3. Specification and guideline

- [x] 3.1 Write the delta `specs/smooth-zoom/spec.md` (two ADDED requirements, four scenarios, each naming
      one case). Verify: `openspec validate 2026-10-29-add-double-tap-zoom --strict` reports no issue (the
      delta was validated before archiving, `evidence/openspec-validate.log`), `bash tools/check-openspec-hygiene.sh`
      exits 0, and every scenario's `Case` names a case that exists in the test sources.
      *(spec: `smooth-zoom` — A double tap steps one level at its own point)*
- [x] 3.2 Extend `guidelines/MapRendering.md` §11 with the double-tap pivot, beside the scroll wheel's
      cursor. Verify: `sed -n '514,519p' guidelines/MapRendering.md` shows the clause and
      `bash tools/check-doc-routes.sh` exits 0.
      *(spec: `documentation-ownership` — A rule has one normative home)*

## 4. Gate and evidence
- [x] 4.1 Run the project's gate once from the change root — `./tools/loop/gate.sh` — because a compiled path
      changed. Verify: its summary is quoted in `evidence/gate.log`, the JUnit tallies are copied into
      `evidence/gate-tallies/`, and no suite reports a failure.
- [x] 4.2 Copy every cited XML into this change's `evidence/` and into `.pi/issue-loop/items/51/`, then check
      the pointers: `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-29-add-double-tap-zoom
      --root .` reports `evidence-check: PASS` (`evidence/evidence-check.log`); archive the change with
      `openspec archive add-double-tap-zoom --yes` (`evidence/openspec-archive.log`, "archived as
      '2026-10-29-add-double-tap-zoom'") and confirm the delta merged into
      `openspec/specs/smooth-zoom/spec.md` (+2 added, `openspec/specs/smooth-zoom/spec.md:196`, `:218`).

## 6. Review round 1, answered (2026-10-30)

- [x] 6.1 The commit requests its frame: `viewModel.renderMap()` after `updateMagnification` in
      `fireDoubleTap`, as the zoom buttons and the keyboard do, plus one case —
      `MapCanvasDoubleTapZoomTest` "double tap requests the frame its committed level needs" — that is red
      with the line commented out. Verify: `evidence/evidence.md`, "Review round 1".
- [x] 6.2 The pair is measured with the platform's own intervals: the timeout first-up→second-**down**, the
      slop between the two **down** points, plus one case —
      `MapGestureComposeTest` "aSlowSecondTapInsideTheWindowIsStillOneDoubleTap" (100 ms gap, 250 ms second
      press) — that is red with the old comparison restored. Verify: `evidence/evidence.md`, same section.
- [x] 6.3 The unasserted statement is gone from both copies of the scenario: "AND the viewport magnification
      SHALL NOT change" is replaced by "THEN no double tap SHALL be reported, so no zoom step is committed",
      which `twoTapsBeyondTheDoubleTapWindowReportNoDoubleTap` asserts.
- [x] 6.4 The stale readings are corrected: the four wall-clock stamps now quote each XML's own `testsuite`
      timestamp, `evidence-check`'s counts are quoted as its log prints them, and `MapRendering.md`'s pivot
      sentence no longer contradicts itself.
- [x] 6.5 The notes that are new scope are recorded, not silently dropped: the `Offset.Zero` anchor sentinel
      (a double tap at the canvas origin) and the two unproven claims (a non-centre retrack; follow
      disengagement after a double tap). Verify: `evidence/evidence.md`, "Review round 1", last three bullets.

## 7. Review round 2, answered (2026-10-30)

- [x] 7.1 The corrections' proof is citable: the two red runs are kept as artifacts — `evidence/round2/red-1-no-render.xml`
      (`tests="4" failures="1"`, "got mag=5.0 unchanged") and `evidence/round2/red-2-up-to-up.xml`
      (`tests="16" failures="1"`, "got [] expected:<[Offset(160.0, 235.0)]>") — together with their console logs,
      the green re-runs (`round2/green-gesture.xml` `tests="16" failures="0"`,
      `round2/green-gesture-and-doubletap-zoom.xml` `tests="4" failures="0"`) and the whole gate on the
      corrected tree (`round2/gate.log`, `BUILD SUCCESSFUL in 5m 40s`).
- [x] 7.2 The guideline sentence says what the code does — "the last anchor the display was given" — because
      `MapCanvasMapBand.kt` writes a gesture centroid even when that gesture neither zoomed nor rotated.
- [x] 7.3 `fireDoubleTap`'s comment names the scroll wheel as the recorded exception instead of claiming every
      discrete commit requests its frame.
- [x] 7.4 The requirement and its scenario say "the next whole level" and name the fractional band, and the case
      "double tap from a level past the half steps to the next whole level" pins `10.6 → 12.0`.
- [x] 7.5 The two wall-clock readings in "The measurement" are gone; the gate log is the record.
