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
  from the copies kept in `evidence/` and confirm the restore is byte-identical (`diff -q`).

## 2. The rule, at its one home

- [x] 2.1 `guidelines/Build.md` §2 rule 1: append the surface measurement — `grep -l` over the cases, the
  engine, the prompts and the fixtures; the count sets the budget, the review scope and the loop's floor; the
  proposal carries `surface: … — <command>`. Verification: task 1.2's case goes green; the section still
  carries rules 1-5 in one sequence (`grep -n '^[0-9]\. \*\*' guidelines/Build.md`).
- [x] 2.2 `tools/loop/prompts/worker.md` Phase A: a fifth step stating the same pass, its search and its
  record, with the #232 measurement as the reason. Verification: task 1.1's case goes green.

## 3. The capability spec

- [x] 3.1 `openspec/changes/fix-iteration-protocol-surface-step/specs/agent-issue-loop/spec.md`: the
  requirement and its two scenarios, one case per scenario. Verification: `openspec validate
  fix-iteration-protocol-surface-step --strict` → valid; the delta merges into
  `openspec/specs/agent-issue-loop/spec.md` on archive.

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
- [x] 5.4 No build and no Gradle suite was run, and none is owed: the delta touches no compiled file
  (`guidelines/Build.md:109`). Named exclusion: the full `tools/loop-selftest.sh`, which the run's own gate
  runs once.

## 6. Evidence and archive

- [x] 6.1 `evidence/`: `red-before-fix.log`, `green-after-fix.log`, `mutation-38.1.log`, `gate-plan.log`,
  `doc-routes.log`, `loop-selftest.xml` (the tally derived from the suite's own summary lines, as
  `tools/loop/gate.sh` derives one; its `system-out` records the derivation).
- [x] 6.2 Archive the change and confirm the requirement reached
  `openspec/specs/agent-issue-loop/spec.md`.
