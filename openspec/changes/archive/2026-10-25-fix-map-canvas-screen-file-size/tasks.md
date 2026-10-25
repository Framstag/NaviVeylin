# Tasks — fix-map-canvas-screen-file-size

No requirement changes, so no task changes a spec; each task names the shipped requirement that holds the claim
it verifies. Parent requirements: `backlog-sensors` — "A source file's size is a declared check"
(`openspec/specs/backlog-sensors/spec.md:412`, the bound and the fingerprint the item closes under) and
`unit-test-suite-runtime` — every test class of the module executes (the focused suite and the gate).

## 1. Before the split — the numbers the delta must move

- [x] 1.1 Take the red number with the row's own measurement rather than a second copy of it:
  `SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[{"state":"OPEN","condition":{"path":"app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt"}}]' bash tools/sensors/metrics/source-size.sh`
  → `"measurement":"3384","verdict":"violation"` under fingerprint `ac5ac114…`, base `635a4da3`, clean tree,
  2026-10-25T19:01:59Z (`evidence/red-probe-head.log`). Parent: `backlog-sensors` — the row files one item per
  path, over the bound, and closes it when the path drops under.
- [x] 1.2 Take the block boundaries the split moves, from the tree rather than from memory: the band wrappers
  are the five `zIndex(MapLayer.…)` lines (`grep -c 'zIndex(MapLayer' MapCanvasScreen.kt` → 5), the follow loop
  is the `LaunchedEffect(Unit)` that opens the `while (isActive)` frame loop, and the tail is everything after
  the screen function's closing brace (679 lines). Parent: `documentation-ownership` — a moved block is what
  makes a `file:line` claim dangle, so the boundaries are counted before they move.
- [x] 1.3 Count the surface before the plan was fixed: 28 source files name `MapCanvasScreen`, 2 test files name
  the file (one reads it), 1 spec/guideline/tool mention (`guidelines/UI.md:1297`, the `MapLayer` enum — which
  does not move), and 7 test comments plus 2 guideline sentences name the file for code that does move;
  measurement in `evidence/surface.log`. Parent: `documentation-ownership`.

## 2. The split, one block per step, each step compiled

- [x] 2.1 Move the file foot (679 lines) into `MapCanvasScreenParts.kt`, widening the `private` top-level
  declarations the screen calls to `internal` (a `private` top-level declaration is visible to its own file
  only). Verified by `./gradlew :app:compileMobileDebugKotlin` with no error.
- [x] 2.2 Move the `MENU`, `MODAL` and `SNACKBAR` band contents into `MapCanvasScreenModalBands.kt` and the
  `CHROME` band content into `MapCanvasScreenChromeBand.kt`, leaving each band's `zIndex`/`testTag` wrapper in
  the screen and threading the written values as setters and states. Verified by the same compile.
- [x] 2.3 Move the `MAP` band content into `MapCanvasMapBand.kt` the same way (30 `MutableState` parameters, 99
  renamed references). Verified by the same compile.
- [x] 2.4 Move the follow display loop into `MapCanvasFollowDisplay.kt` (29 `MutableState` parameters, 78 renamed
  references, the two constants it alone used moved with it, its own `TAG`). Verified by the same compile, and
  by `grep` that no log line's printed text changed: the two interpolations inside the loop are `${…State.value}`
  so the message is the same string as before.
- [x] 2.5 Realign the shipped case that pins the seam's call site: `FollowBlitCallSiteTest` reads
  `MapCanvasFollowDisplay.kt` instead of `MapCanvasScreen.kt`; its assertions are unchanged. Realign the 7 test
  comments and the 2 guideline sentences that name the file for code that moved. Parent:
  `documentation-ownership` — a guideline's `file:line` claim is what a split can dangle.

## 3. Verification

- [x] 3.1 Take the green number with the row's own probe: `"measurement":"1109","verdict":"clear"` under the
  same fingerprint, 2026-10-25T19:07:13Z (`evidence/green-probe.log`). Parent: `backlog-sensors`.
- [x] 3.2 Account for what the split moved rather than only for the line count: `wc -l` of the six files, the
  `git diff --stat` (2 500 deletions, 225 insertions in the screen — the rest of the file's lines reappear
  verbatim in the new files), the 208 `State.value` references and the 44 delegate pairs
  (`evidence/line-accounting.log`). Parent: `unit-test-suite-runtime` — no member, no case and no statement may
  vanish into a move.
- [x] 3.3 Run the focused suite over the module package the change touches:
  `./gradlew :app:testMobileDebugUnitTest -PjvmTestsOnly -PforceTests --tests 'com.naviveylin.ui.map.*'` — 837
  cases in 114 XMLs, none failing, including the two cases that read the screen's sources
  (`evidence/focused-tally.log`, `evidence/TEST-com.naviveylin.ui.map.FollowBlitCallSiteTest.xml`,
  `evidence/TEST-com.naviveylin.ui.map.MapMenuBackOrderComposeTest.xml`). Parent: `unit-test-suite-runtime`.
- [x] 3.4 Run the project's gate once, exactly as the run's profile states it (`./tools/loop/gate.sh`) — the
  change touches `app/src/main`, so the compiled-path command is owed, and the change's markdown pulls the
  documentation gates with it. Its numbers are quoted in the report and its XMLs copied into `evidence/`.
  Parent: `unit-test-suite-runtime`, `backlog-sensors`.

## 4. Archive

- [x] 4.1 Re-check the evidence pointers mechanically:
  `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/2026-10-25-fix-map-canvas-screen-file-size --root .`
  — every `file:line`, every quoted `tests=`/`failures=` and every `/tmp` pointer resolves.
- [x] 4.2 Archive the change and confirm it lands under `openspec/changes/archive/` with no capability delta
  (`.openspec.yaml` declares `skip_specs: true`, so no spec file changes).
- [x] 4.3 Leave the tree as the change leaves it: no commit, no push, no version control, no tracker write —
  the run's script owns those.
