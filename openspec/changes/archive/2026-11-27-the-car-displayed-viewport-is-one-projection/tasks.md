# Tasks — the car's displayed viewport is one projection

Spec: `auto-smooth-follow`, the requirement "Single resolved anchor in the AA follow blit" (delta
`specs/auto-smooth-follow/spec.md`). Scope: compiled Kotlin changes, so the gate runs the changed cases and then
the module's suite (`guidelines/Build.md` §2, the scope block). Blocked by the phone change
`the-displayed-viewport-is-one-projection`, so the car copies the phone's shape.

## 1. The derivation

- [x] 1.1 `auto/src/main/java/com/naviveylin/auto/AutoMapRendererFollow.kt`: add the derived displayed viewport
  beside `anchorCenterFor` — the frame viewport shifted by the clamped overrun offset — reading the resolved
  anchor once. *Verified*: `./gradlew :auto:compileDebugKotlin` — compiled inside the case run below
  (`:auto:compileDebugKotlin` executed, `BUILD SUCCESSFUL`).
- [x] 1.2 `AutoMapRendererFollow.kt`: document that `anchorCenterFor` is the derivation's input and not a second
  projection. *Verified*: the doc comment names the derivation.

## 2. The three call sites

- [x] 2.1 `AutoMapRendererMotion.kt:193` and `:219`: the blit reads the derived offset and the commit reads the
  derivation's input. *Verified*: `auto#theCarOverlayOffsetIsTheFramesShift`.
- [x] 2.2 `AutoMapRendererRender.kt:84`, `:154` and `:276`: the render-path blit, the render-path commit and the
  marker's draw offset read the same derived value. *Verified*: the same case.
- [x] 2.3 `tools/look.sh 'OverrunWindow.shiftPx'` names one main-source site after the change.
  *Verified*: the look's own output.

## 3. The cases

- [x] 3.1 `auto/src/test/java/com/naviveylin/auto/`: the case `auto#theCarOverlayOffsetIsTheFramesShift` — the
  marker's draw offset and the blit offset are the same derived value, read once, and a site handed the raw
  preset reddens it. *Verified*: red on the base, green after tasks 1–2. Red on `39aa5107` (the base):
  `tests="1" failures="1"` — `evidence/red-on-head-displayed-viewport.xml`, `found 3` shift sites where the case
  asserts one. Green after the change: `tests="1" failures="0"` — `evidence/green-displayed-viewport.xml`; green
  again after the three main-source files were restored byte-for-byte from the kept copies:
  `tests="1" failures="0"` — `evidence/green-after-restore.xml`.
- [x] 3.2 `AutoMapRendererBlitTest` and `AutoMapRendererFollowAnchorTest`: unchanged and still green, which is
  what proves the shipped scenarios moved no behaviour. *Verified*: both cases' own runs.

## 4. Verification

- [x] 4.1 `bash tools/check-change-readiness.sh openspec/changes/the-car-displayed-viewport-is-one-projection`
  prints `LOOP-CHANGE-READY: ok`. *Verified*: printed on 2026-11-27.
- [x] 4.2 `openspec validate the-car-displayed-viewport-is-one-projection --strict` passes. *Verified*: printed
  `Change 'the-car-displayed-viewport-is-one-projection' is valid`.
- [x] 4.3 The gate runs the changed cases and then the module's suite; a red run is attributed before it is
  re-run (`guidelines/BuildEvidence.md` §4d). *Verified*: `./tools/loop/gate.sh`, its tally copied to
  `evidence/gate.log` after the archive.
- [x] 4.4 `architecture/src/test/kotlin/com/naviveylin/architecture/StructureReportTest.kt`: the census literal
  moves by one classified source file (`files=861` in the summary and in the `report.files` assertion), because the
  new case file is a source file of the three modules. *Verified*: `StructureReportTest > greenRunPrintsFigures`
  green in the case run below (it ran as a dependency of `:auto:testDebugUnitTest`).

Route:

LOOP-ROUTE: guidelines/MapCar.md §14
