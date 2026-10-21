# Tasks — fix-fix-quality-dispatch-attribution

## 1. Reproduce the defect (proves the Why)

- [x] 1.1 Add the deterministic stand-in for unrelated main-dispatcher traffic to
  `MapCanvasViewModelFixQualityTest.aTickDoesNotDispatchOnTheMainDispatcher` on base `2e732f0`: one
  dispatch onto the instrumented main dispatcher per window from a `Dispatchers.IO` coroutine, assertion
  unchanged. Verification: the case fails on HEAD with the item's own message.
  Evidence: `evidence/red-TEST-com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest.xml` —
  `tests="9" failures="1"`, 2026-10-21T09:37:19Z, `the fix-quality tick dispatched on the main dispatcher
  from another thread 1 times in the second 300ms window while nothing changed (first window: 1)`.
- [x] 1.2 Record that the printed first-dispatch stack is the case's own noise coroutine, not the
  re-evaluation — the attribution defect, in one artefact. Evidence: the same XML's `<failure>` message.

## 2. The single fix (parent spec: `gps-fix-quality` — Re-evaluation stays off the main dispatcher while
   the quality is unchanged)

- [x] 2.1 `CountingMainDispatcher` records each counted dispatch with the stack that issued it and
  exposes `fromTheViewModel` / `fromElsewhere`, attributing by `MapCanvasViewModel$…` frames. Verification:
  the class compiles in `:app` (both flavors share the source set) and the case's own control sees the
  noise.
- [x] 2.2 The case asserts the re-evaluation's count is 0 over three six-tick windows (`TICK_WINDOWS`, the
  bounded sequence of the first scenario) and names the window honestly in its message. Verification: the
  scenario "Unchanged quality causes no main-dispatcher dispatch" is exercised by the case (5 forced runs,
  `tests="9" failures="0"`).
- [x] 2.3 The case provokes unrelated traffic per window and asserts the counter attributed it
  (`elsewhere >= 1`), so the attribution cannot stop discriminating unnoticed. Verification: the scenario
  "Unrelated main-dispatcher traffic is not attributed to the re-evaluation" is exercised by the same case
  — with the noise present the case is green; on HEAD the same noise made it red (task 1.1).
- [x] 2.4 Spec delta: one MODIFIED requirement, the attribution clause and the two scenarios above, each
  naming its case. Verification: `openspec validate fix-fix-quality-dispatch-attribution --strict`.

## 3. Verify

- [x] 3.1 Affected cases, forced, five times: `./gradlew :app:testMobileDebugUnitTest -PforceTests
  --no-build-cache -PnoCoverage --tests "com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest"` —
  `tests="9" failures="0"` each run. Evidence: `evidence/green-run*-…xml`.
- [x] 3.2 Neighbouring slice once, to show the class passes in company: `--tests
  "com.naviveylin.ui.map.MapCanvasViewModel*"` — green, no failures in the slice's XMLs.
- [x] 3.3 Revert-check (the falsification the change owes): reintroduce the pre-fix per-tick
  `withContext(Dispatchers.Main) { }` inside the re-evaluation loop → `aTickDoesNotDispatchOnTheMainDispatcher`
  must fail with `[window 1: 6, window 2: 6, window 3: 6]`; restore the loop, forced green again.
  Evidence: `evidence/falsification-TEST-com.naviveylin.ui.map.MapCanvasViewModelFixQualityTest.xml`.
- [x] 3.4 Scope note recorded: the delta touches test sources only, so `guidelines/Build.md` §2's scope
  block applies — the red case plus the affected cases, not the full `./gradlew test` gate. No build
  "for completeness" was run: no compiled production file changed.
- [x] 3.5 Self-verify the diff against every spec scenario and against the three XMLs (Phase F). The
  independent read-only review is the loop's own reviewer process, which runs after this worker's report
  and before the item closes — the authoring process cannot be its own reviewer.

## 4. Archive

- [x] 4.1 Archive the change; confirm the directory exists under `openspec/changes/archive/` and the
  delta merged into `openspec/specs/gps-fix-quality/spec.md`.
- [x] 4.2 Evidence copied into the change's own `evidence/` and into `.pi/issue-loop/items/87/`.
