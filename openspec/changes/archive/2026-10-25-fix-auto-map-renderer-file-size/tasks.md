# Tasks — fix-auto-map-renderer-file-size

No requirement changes, so no task changes a spec; each task names the shipped requirement that holds the
claim it verifies. Parent requirements: `backlog-sensors` — "A source file's size is a declared check" (the
bound and the fingerprint the item closes under) and `unit-test-suite-runtime` — "the module's test classes
execute" (the 880-case suite and the 79-member roster the split keeps whole).

## 1. Before the split — the two numbers the delta must move

- [x] 1.1 Take the red number with the row's own measurement rather than a second copy of it:
  `SENSORS_THRESHOLD=1500 SENSORS_CONDITIONS='[{"state":"OPEN","condition":{"path":"auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt"}}]' bash tools/sensors/metrics/source-size.sh`
  → `"measurement":"2307","verdict":"violation"` under fingerprint `499ec362…` on base `d846c741`, clean tree
  (`evidence/red-probe-head.log`). Parent: `backlog-sensors` — the row files one item per path, over the
  bound, and closes it when the path drops under.
- [x] 1.2 Record HEAD's member roster as the invariant the split keeps: 79 member functions of the class
  (`evidence/member-roster.log`, first half). Parent: `unit-test-suite-runtime` — every test class of the
  module executes, and no member may vanish into a move.
- [x] 1.3 Count the surface before the plan was fixed: 35 files name `AutoMapRenderer`, 8 spec mentions (none
  names a file), 13 guideline mentions (all `AutoMapRenderer.<member>`), 0 tool mentions; one comment in
  `:core` names the file (`VehicleMarkerGeometry.kt:8`). Parent: `documentation-ownership` — a guideline's
  `file:line` claim is what a split can dangle.

## 2. The split

- [x] 2.1 Move the loop machinery into `AutoMapRendererLoops.kt` as extension functions on
  `AutoMapRenderer`, dedented with the receiver prefix and nothing else changed. Verified by 2.3's compile.
- [x] 2.2 Move the motion, follow, render, overlay-reader and marker groups into
  `AutoMapRendererMotion.kt`, `AutoMapRendererFollow.kt`, `AutoMapRendererRender.kt`,
  `AutoMapRendererOverlays.kt` and `AutoMapRendererMarkers.kt`, and lift the nested `MarkerDrawObjects` to a
  top-level `internal class` in the markers file (nothing outside the file named it — `grep -rn
  MarkerDrawObjects` outside it → 0). Verified by 2.3 and 2.4.
- [x] 2.3 Make the class's own fields and the companion's shared members `internal` so an extension in another
  file of the same module can read them, and import the companion tunables where a topic file reads one. Kept
  as members: the 21 members a MockK double stubs or verifies (`design.md`, "The constraint that decides which
  members stay"). Verified by 2.5's probe and 3.1.
- [x] 2.4 Realign the one comment that named the file's old location
  (`core/src/main/java/com/naviveylin/core/VehicleMarkerGeometry.kt:8` names
  `auto/.../AutoMapRendererMarkers.kt` now). Verified by `grep -rn 'AutoMapRenderer\.kt' guidelines
  openspec/specs tools` leaving only true statements — it now prints nothing.
- [x] 2.5 Compile the source sets: `./gradlew :auto:compileDebugUnitTestKotlin -PjvmTestsOnly --offline` →
  `BUILD SUCCESSFUL`, no `e:` and no `w:` line, so the split adds no warning the warning rule would owe a fix
  for (`guidelines/Build.md` §4, "Warnings").

## 3. Verification — scoped by what changed (production sources of `:auto` and `:core`)

- [x] 3.1 Run the affected cases: `./gradlew :auto:testDebugUnitTest -PjvmTestsOnly -PforceTests --tests
  'com.naviveylin.auto.*'` → 91 XMLs, 880 cases, `failures=0 errors=0`; each XML copied to `evidence/` at the
  moment it was produced (`evidence/focused-tally.log`). Parent: `unit-test-suite-runtime`.
- [x] 3.2 The roster and the accounting: the union of the class's own members and the extension functions
  against 1.2's 79 → `diff` empty, and 1 164 code lines before and after, multiset identical once the
  receiver prefix, the visibility flip and one qualified nested type are normalised away
  (`evidence/member-roster.log`, `evidence/line-accounting.log`, re-runnable:
  `python3 evidence/line-accounting.py evidence/AutoMapRenderer-head.kt .` from the change root's project
  root). Parent: `unit-test-suite-runtime`.
- [x] 3.3 Falsify the design's one constraint — the members MockK intercepts must stay members — by moving
  `committedViewportCenter` into a topic file and running its case: `CarMapViewportTest` fails with
  `io.mockk.MockKException: no answer found for AutoMapRenderer(#1).getOverrunLat$auto()`
  (`evidence/member-dispatch-probe.log`), then the function is restored and 3.1 is green again
  (`TODO.md` §113 — a new invariant is mutated once and the named case must fail).
- [x] 3.4 Take the green number with the same probe as 1.1 → `"measurement":"1009","verdict":"clear"` under
  the same fingerprint (`evidence/green-probe.log`): the row's clearing pass is what closes the item.
  Parent: `backlog-sensors`.
- [ ] 3.5 Run the project's gate once (`./tools/loop/gate.sh`): the loop profile counts `*/src/*` as a
  compiled path, so the gate owes `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` plus the
  suites the `openspec/changes/**` paths pull in. **The run is red**: `:app` mobile reported
  `2109 tests completed, 2 failed` (`NavigationEngineRerouteTest.aConfirmedRerouteWritesTheLatencyDiagnosticLine`,
  `BasemapSectionComposeTest.installedShowsDeleteButton`), while `:auto`, `:core`,
  `:osmscout-client-java` and `:app` automotive completed. Left **unchecked** rather than talked green: both
  classes pass alone and the whole `:app` mobile suite is green standalone (`evidence/attribution.log`,
  `guidelines/Build.md` §4d — quote both numbers, name the residual, do not claim the run). Parent:
  `build-test-gate`.

## 4. Evidence and archive

- [x] 4.1 Copy every cited XML into `openspec/changes/fix-auto-map-renderer-file-size/evidence/` and the run's
  `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/299`, and quote the gate's tallies in
  `evidence/evidence.md`, each beside the `gate-*.xml` that carries it. Parent: `build-test-gate` (a green
  claim has a test result in the tree).
- [x] 4.2 Pointers mechanically: `bash .pi/skills/fix-loop/scripts/evidence-check.sh
  openspec/changes/fix-auto-map-renderer-file-size --root .` → `evidence-check: PASS`.- [x] 4.3 `openspec validate fix-auto-map-renderer-file-size --strict` → "is valid"; `.openspec.yaml` declares
  `skip_specs: true` with the reason in `design.md` (no requirement changes, so no delta is invented).
- [x] 4.4 Archive the change and confirm the directory exists under `openspec/changes/archive/` — archived as
  `2026-10-25-fix-auto-map-renderer-file-size`, `skip_specs: true`, so no capability spec is touched and there
  is nothing to merge into one.
- [x] 4.5 Do not commit, do not push, do not touch the tracker: the run's script owns git and the labels.
