# Tasks — fix-routed-read-bound-case-line

The change touches no compiled file, so no build task exists here: the structural check that stands in is
`openspec validate --strict` over the change plus the documentation suites `tools/loop/gate.sh` selects for
`*.md` and `openspec/**` (`tools/loop/loop.conf`, `LOOP_GATE_SUITES`). The archive comes before the gate because
`openspec/changes/*/` is gitignored while a change is open, so the gate's pathspec owes nothing until the change
sits under `openspec/changes/archive/`.

## 1. Red case

- [x] 1.1 Write `evidence/red-probe.sh`, which extracts the scenario *A section carrying more than one question is
      split* from `openspec/specs/documentation-ownership/spec.md` and requires that it names `routed-read#bound`,
      names `sensors#routedSectionSizeRow`, says `ruler`, and says `check's condition` — and that both ids are
      declared by the suites that run them (`tools/check-doc-routes-selftest.sh` and `tools/sensor-cases.sh`). Run
      it on HEAD. Verify: `tests=1 failures=1`, the case
      `routed-read#the-case-line-names-both-halves` naming the three missing tokens
      (`evidence/red-on-head.xml`, `evidence/red-on-head.log`, 2026-10-30T07:14:08Z).
      *(spec: `documentation-ownership` — The routing cost is measured, scenario "A section carrying more than one
      question is split")*

## 2. The scenario restatement

- [x] 2.1 Restate the scenario in the delta
      `specs/documentation-ownership/spec.md`: an `- **AND**` bullet names `sensors#routedSectionSizeRow` as the
      condition a past-bound routed section files and `routed-read#bound` as the ruler and the print, and says the
      "answers more than one question" trigger is a judgement no case asserts; the Case line names both cases with
      what each asserts. Verify: the delta's requirement block carries the requirement whole — the description and
      all seven other scenarios byte-identical to `openspec/specs/documentation-ownership/spec.md:117`–`:191`,
      with only the one scenario changed (`diff -u` of the two requirement blocks shows one hunk).
      *(spec: `documentation-ownership` — The routing cost is measured; A modified requirement carries the
      scenarios its spec still has)*
- [x] 2.2 Revert-check: with the restatement merged, run `evidence/revert-check.sh`, which puts the requirement
      block back to the base commit's text — the scenario naming `routed-read#bound` alone — requires the probe to
      go red, restores the archived restatement and requires it green, `cmp -s` against its backup. Verify:
      `evidence/revert-check.log` carries the FAIL under the mutation and the PASS after the restore, and the tree
      is left byte-identical.
      *(spec: `documentation-ownership` — A rule is added to the document that owns its concern; nothing but the
      named cases may satisfy the scenario)*

## 3. Structural verification

- [x] 3.1 Validate the change: `openspec validate fix-routed-read-bound-case-line --strict`. Verify: exit 0 and
      `evidence/openspec-validate.log` carries `Change 'fix-routed-read-bound-case-line' is valid`.
- [x] 3.2 Archive the change: `openspec archive fix-routed-read-bound-case-line --yes`. Verify: the directory
      exists under `openspec/changes/archive/` as `2026-10-30-fix-routed-read-bound-case-line`, the delta has
      merged into `openspec/specs/documentation-ownership/spec.md` with the restated scenario
      (`git diff --stat` = 9 insertions, 2 deletions in that one file), and the probe reports `tests=1 failures=0`
      over the merged spec (`evidence/green-after-archive.xml`).
- [x] 3.3 Run the project's gate once, `./tools/loop/gate.sh`, from the change root after the archive. Verify:
      `evidence/gate.log` ends in
      `LOOP-GATE: tests=27 failures=0 errors=0` over the seven documentation suites the pathspec owes, the
      per-suite tallies are in `evidence/gate-tallies/` and their sum in `evidence/gate-summary.xml`, and no step
      reports a failure.
      *(spec: `documentation-ownership` — A route resolves to a section that exists)*

## 4. Evidence

- [x] 4.1 Copy every cited XML into the change's `evidence/` directory and into
      `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/711`, then run
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-30-fix-routed-read-bound-case-line --root .`.
      Verify: `evidence-check: PASS` with every `file:line` and every quoted `tests=`/`failures=` resolving
      (`evidence/evidence-check.log`).
- [x] 4.2 Leave the tree as the change leaves it: no commit, no push, no version control, no tracker write — the
      run's script owns those.
