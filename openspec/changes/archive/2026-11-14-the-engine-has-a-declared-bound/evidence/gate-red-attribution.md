# Gate red attribution — item #890, phase F (tail worker, 2026-11-14)

Gate line handed to the tail, never re-run:
`tests=1058 failures=1 errors=0 … — failing: tools/loop/tests/loop-selftest.sh --from-git 2d2af2af964c8bc9a76bdc26b9bfa347f2de59fc`

## The failing step, named

- **Suite:** `tools/loop/tests/loop-selftest.sh --from-git 2d2af2af964c8bc9a76bdc26b9bfa347f2de59fc`
- **Tally (this gate, run=586962):** `tests="441" failures="1" errors="0" time="853"` — system-out
  `loop-selftest: 1 of 441 cases failed — 851.48 s`. Carried here as
  `tools_loop_tests_loop-selftest.sh_--from-git_2d2af2af964c8bc9a76bdc26b9bfa347f2d.xml` (the same copy is in
  `.pi/issue-loop/items/890/`).
- **Case:** `FAIL phase#aResultOverTheBoundIsCutAndNamed` — `evidence/gate.log:1162` and `:1249`
  `[PHASE-BOUND: cut at 4000 characters — the phase state is printed first, so a cut takes the work package's
  tail; …]: 'PKG-LINE-001' not found in: LOOP-PHASE: A — root cause and the red test`, `:1289`
  `FAIL phase#aResultOverTheBoundIsCutAndNamed`. The phase state fills the 4 000-character bound, so the cut takes
  the work package's head. The case lives in `tools/loop/tests/loop-cases.sh` but measures `tools/loop/phase.sh`'s
  cut — loop tooling, not this change's declarations.

## Red before this work

- `evidence/gate-red-before-item-862-gate-run.log` — item #862's gate, **mtime 2026-11-13T23:53:00Z**, i.e. before
  this change existed (this item's first tree write, `red-on-HEAD.xml`, is 2026-11-14T12:35Z): same suite leg,
  base `fcbe328dd1ed75c0863bc43ad7de0f8a734f638e` — which `git merge-base --is-ancestor … 2d2af2af` confirms is an
  **ancestor of this run's base** — `:1146`/`:1232` the same `'PKG-LINE-001' not found in … cut at 4000
  characters` reason, `:1272` `FAIL phase#aResultOverTheBoundIsCutAndNamed`, `:1328`
  `loop-selftest: 1 of 427 cases failed`, `:1900` `LOOP-GATE: tests=1067 failures=1`. The same leg was waived at
  items #866 and #880 with this case named
  (`…/archive/2026-11-14-fix-a-withheld-volume-prints-its-share/evidence/gate-red-attribution.md`,
  `…/archive/2026-11-14-report-a-stale-map-install/evidence/gate-red-attribution.md`).
- `evidence/gate-red-before-item-890-block-names-gate-run.log` — the full gate of
  `2026-11-14-fix-a-block-names-its-stop-class`, archived **2026-11-14T08:39:57+0100**, hours before this item
  started: `:1384` the same `'PKG-LINE-001' not found in … cut at 4000 characters` reason, `:1511`
  `FAIL phase#aResultOverTheBoundIsCutAndNamed`, `:1567` `loop-selftest: 1 of 427 cases failed`, `:2149`
  `LOOP-GATE: tests=1068 failures=1 … failing: tools/loop/tests/loop-selftest.sh --from-git fcbe328d…`. Four more
  archived changes of this round record the same case as the tree's pre-existing red, not their delta's
  (`fix-a-block-names-its-stop-class/tasks.md` 7.3, `fix-a-hup-case-ends-on-its-own-clock/tasks.md`,
  `fix-a-landing-merges-over-a-difference-it-cannot-place/tasks.md`,
  `fix-the-checks-a-child-runs-by-hand-are-bounded/tasks.md`).
- The case's inputs did not move between the ancestor base and this run's base: across
  `fcbe328dd1ed…..2d2af2af…`, `git log` counts **0 (zero) commits** for `tools/loop/phase.sh`,
  `tools/loop/prompts/worker.md`, `tools/loop/tests/loop-selftest.sh`, `tools/pi-child-events.sh`,
  `tools/tooling.json`, and `git diff fcbe328d 2d2af2af -- tools/loop/tests/loop-cases.sh` carries **no line**
  matching `case_phase_aResultOverTheBoundIsCutAndNamed`, `phase_fixture` or `PKG-LINE`. The bound the assertion
  measures is `LOOP_TOOL_RESULT_MAX_CHARS=4000` at the run base itself: `tools/loop/loop.conf:450` in
  `git show 2d2af2af:tools/loop/loop.conf`, introduced by commit `419d482c`, an ancestor of the base.

## Red after this work / outside this change's paths

- Stays red at **this** item's gate: the `--from-git` XML above, `failures="1"`, run=586962;
  `evidence/gate.log:1289`.
- The change's delta is six paths (`evidence/measure.txt`, `paths=6 insertions=527 deletions=3`):
  `AGENTS.md`, `guidelines/Backlog.md`, `guidelines/BuildGate.md`, `tools/loop/layout.tsv`,
  `tools/loop/shared.tsv`, `tools/loop/tests/loop-cases.sh`. Of the case's declared inputs
  (`tools/loop/phase.sh tools/loop/prompts/worker.md tools/loop/loop.conf tools/loop/lib.sh tools/loop/state.sh
  tools/loop/tests/loop-cases.sh tools/loop/tests/loop-selftest.sh tools/pi-child-events.sh tools/tooling.json`)
  only `loop-cases.sh` intersects — `git diff --name-only 2d2af2af -- <the other eight>` prints nothing — and its
  whole diff is three hunks, `@@ -410,6 +410,14 @@ LOOP_SHELL_CASES=(`, `@@ -1130,6 +1138,14 @@
  loop_run_shell_case() {` and `@@ -15825,6 +15841,499 @@ case_driver_everyCaseDeclaresTheFilesItCanReach() {`:
  the new `driver#` rows and their dispatch. `case_phase_aResultOverTheBoundIsCutAndNamed` (line 17520) and
  `phase_fixture` are untouched, and the bound, the prompt and the phase engine are byte-identical to the base.
- The change's own region is green in the same gate: `evidence/gate.log:1096`
  `PASS driver#everyEngineScriptIsInTheTable`, `:1106` `PASS driver#theSharedNamesAreDeclared`, all ten cases of
  `evidence/focused-cases.log` `PASS`, and the gate line's only failing step is the `--from-git` leg above
  (`--case driver#everyCaseDeclaresTheFilesItCanReach` and `loop-mutations.sh --check` green).

Waived clause: `LOOP-GATE-WAIVED`, suite: `LOOP-GATE-WAIVED-SUITE` — both in
`.pi/issue-loop/items/890/worker-tail-report.md`.
