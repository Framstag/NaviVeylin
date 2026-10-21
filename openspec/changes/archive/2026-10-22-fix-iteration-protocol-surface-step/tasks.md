# Tasks — fix-iteration-protocol-surface-step

Parent spec: `openspec/changes/fix-iteration-protocol-surface-step/specs/agent-issue-loop/spec.md`,
"A change names its surface before its plan is fixed".

## 1. Red cases — the pass is absent from both surfaces

- [x] 1.1 `tools/loop-cases.sh`: add `prompt#workerNamesTheChangeSurfaceBeforeThePlan` (the rendered worker
  task asks for the count, the `grep -l`, and the `surface:` record), register it in the case list and in the
  dispatch. Verification: `bash tools/loop-selftest.sh --case
  'prompt#workerNamesTheChangeSurfaceBeforeThePlan'` → `FAIL`, `EXIT=1`, `evidence/red-before-fix.log`
  (2026-10-22T00:44:20+02:00).
- [x] 1.2 `tools/loop-cases.sh`: add `prompt#iterationProtocolNamesTheChangeSurface` (the protocol states the
  pass and the record), registered the same way. Verification: `bash tools/loop-selftest.sh --case
  'prompt#iterationProtocolNamesTheChangeSurface'` → `FAIL`, `EXIT=1`, the same log.
- [x] 1.3 Confirm both are red against HEAD's text, not against a half-applied edit: revert the fix from
  `guidelines/Build.md` and `tools/loop/prompts/worker.md` by hand, run both cases (`FAIL`, `EXIT=1`), restore
  and run them again (`PASS`, `EXIT=0`). Verification: `evidence/red-before-fix.log`
  (2026-10-22T00:44:20+02:00) and `evidence/green-after-fix.log` (00:44:35); the pre-fix text of both files is
  `git show 80df3a3:<path>`, and the change's diff shows the restore — step 5 present in `worker.md`, the
  mutation record's replacement string absent.

## 2. The rule, at its one home

- [x] 2.1 `guidelines/Build.md` §2 rule 1: append the surface measurement — `grep -l` over the cases, the
  engine, the prompts and the fixtures; the count, not the first file the plan names, is what the plan's
  estimate is set against; the proposal carries `surface: … — <command>`. Verification: task 1.2's case goes
  green; the section still carries rules 1-5 in one sequence (`grep -n '^[0-9]\. \*\*' guidelines/Build.md`).
- [x] 2.2 `tools/loop/prompts/worker.md` Phase A: a fifth step stating the same pass, its search and its
  record, with the #232 measurement as the reason. Verification: task 1.1's case goes green.

## 3. The capability spec

- [x] 3.1 `openspec/changes/fix-iteration-protocol-surface-step/specs/agent-issue-loop/spec.md`: the
  requirement and its two scenarios, one case per scenario. Verification: `openspec validate
  fix-iteration-protocol-surface-step --strict` → valid while the change was open; the delta merged into
  `openspec/specs/agent-issue-loop/spec.md` on archive, and now lives only under
  `openspec/changes/archive/2026-10-22-fix-iteration-protocol-surface-step/specs/agent-issue-loop/spec.md`.

## 4. The invariant is falsifiable by a mutation

- [x] 4.1 `tools/loop-mutations.sh`: record `38.1` — the pass removed from `tools/loop/prompts/worker.md`.
  Verification: `bash tools/loop-mutations.sh --only '38.1'` → `RED 38.1 … , restore … green again`,
  `not-noticed: 0`, `restore-failures: 0` (`evidence/mutation-38.1.log`). The guideline half carries no
  mutation because the harness applies every record inside the engine (`$engine/${file#tools/loop/}`);
  `design.md` records that limit and the red run that stands in for it.

## 5. Verification of a delta that compiles nothing

- [x] 5.1 `bash tools/loop/gate.sh --plan --base HEAD` → the delta owes `tools/loop-selftest.sh` and
  `tools/check-doc-routes.sh`, no Gradle task (`evidence/gate-plan.log`).
- [x] 5.2 `tools/loop-selftest.sh --case <each prompt case>` → 10 of 10 `PASS`, `EXIT=0`
  (`evidence/green-after-fix.log`; a focused run is not gate evidence — the run's own gate follows).
- [x] 5.3 `bash tools/check-doc-routes.sh` → exit 0, every section reference and skill path resolves
  (`evidence/doc-routes.log`); `openspec doctor` and the artifact-hygiene rules of
  `.github/workflows/build.yml` were run for the change's own artifacts.
- [x] 5.4 The gate was run once (`./tools/loop/gate.sh`, 2026-10-22T00:51:54+02:00 → 01:05:33+02:00):
  `LOOP-GATE: tests=195 failures=0 (./gradlew test -PforceTests --no-build-cache, tools/loop-selftest.sh,
  tools/sensor-selftest.sh, tools/check-doc-routes.sh)`, `EXIT=0` (`evidence/gate.log`; the end time is the
  log file's mtime, 01:05:33 — the log carries only the start; per-suite tallies
  `evidence/gate-loop-selftest.xml` 150 cases, `gate-sensor-selftest.xml` 45, `gate-doc-routes.xml`,
  `gate-gradle.xml` `BUILD SUCCESSFUL in 3m 28s`). That run predates the last writes to
  `guidelines/Build.md` (round 2's paragraph at 01:07:07, re-wrapped at 01:09:37), so the two suites the delta
  owes were re-run after the final edit (2026-10-22T01:09:48+02:00 → 01:19:31+02:00): `tools/loop-selftest.sh`
  `all 150 cases passed`,
  `EXIT=0`, and `tools/check-doc-routes.sh` `all 120 section references resolve, all 5 skill references
  resolve`, `EXIT=0` (`evidence/suites-after-corrections.log`, tallies
  `evidence/tools_loop-selftest.sh.xml` 150 cases and `evidence/tools_check-doc-routes.sh.xml`). Those are
  the suites `gate-plan.log` names, so this delta's own roster owes no Gradle task — the gate's `*.xml`
  compiled pattern pulls the Gradle leg in once evidence tallies are in the tree, which is the gate's mapping,
  not a compiled path of this change — and task 5.2's
  10-case focused pass is still not evidence.

## 6. Evidence and archive

- [x] 6.1 `evidence/`: `red-before-fix.log`, `red-before-fix.xml`, `green-after-fix.log`, `mutation-38.1.log`
  (re-run after the corrections), `gate-plan.log`, `doc-routes.log`, `gate.log` and the gate's own tallies
  `gate-loop-selftest.xml`, `gate-sensor-selftest.xml`, `gate-doc-routes.xml`, `gate-gradle.xml`, plus the
  post-correction suite run `suites-after-corrections.log` with its tallies `tools_loop-selftest.sh.xml` and
  `tools_check-doc-routes.sh.xml`; the focused 10-case tally `loop-selftest.xml` stays as the record of task
  5.2, not as gate evidence.
- [x] 6.2 Archive the change and confirm the requirement reached
  `openspec/specs/agent-issue-loop/spec.md`.
