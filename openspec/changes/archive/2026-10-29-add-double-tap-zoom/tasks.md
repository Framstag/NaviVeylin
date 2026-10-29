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
      (`evidence/red-on-head.xml`, 2026-10-29T12:37:29Z).
      *(spec: `smooth-zoom` — Two taps inside the platform's window are one double tap; Taps beyond the
      platform's window are two single taps)*

## 2. Behaviour

- [x] 2.1 Pair the taps in the existing gesture state machine: `pendingTapUpMs`/`pendingTapPos` in the
      `pointerInput` scope, the platform's `doubleTapTimeoutMillis` and `touchSlop` as the window, a pending
      tap cleared by a drag, a pinch and a long press. Verify: the red case passes and every pan, pinch,
      rotation and long-press case of `MapGestureComposeTest` stays green — `tests="15" failures="0"`
      (`evidence/green-after-fix-gesture.xml`, 2026-10-29T12:43:35Z).
      *(spec: `smooth-zoom` — Two taps inside the platform's window are one double tap)*
- [x] 2.2 Add the seam `doubleTapZoomMagnification` (one level, clamped at `MAX_MAG`) and `fireDoubleTap`
      (disengage follow, `zoomAtCursor` centre, commit the level, animate around the tap point) in
      `MapCanvasScreenParts.kt`, beside `fireLongPress`, and wire the map band's `onDoubleTap` to it. Verify:
      `MapCanvasDoubleTapZoomTest` reports `tests="2" failures="0"`
      (`evidence/green-after-fix-doubletapzoom.xml`).
      *(spec: `smooth-zoom` — A double tap steps one level around the tap point; A double tap at the maximum
      magnification changes nothing)*
- [x] 2.3 Keep the input's anchor across a walked-step retrack in `MapCanvasFollowDisplay.kt` (the follow
      anchor stays the fallback). Verify: the zoom-walk cases still pass in the gate, and the tap point stays
      the animation's pivot for every frame (design D3).
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
