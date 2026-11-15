# Tasks — the file-set leg runs the cheapest file's records

## 1. The red case

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: the case `mutation#theFileSetLegScopesByTheCheapestFile`, registered in
      `LOOP_SHELL_CASES` and in the dispatcher arm, declaring
      `tools/loop/tests/loop-mutations-selftest.sh tools/loop/tests/loop-mutations.sh` in its first line. It reads
      `scoped_file` out of the self-test, counts the declared set per file by the same `@`-separated fields leg 7
      derives its own set from, asserts the pick is the file that owns the fewest records of any file owning at least
      two, and names a pick the declaration gives fewer than two records to rather than running it.
      **Verified** red on the base commit `8f84a8b8` with the self-test file as the base writes it:
      `evidence/red-on-HEAD.log` and its JUnit carrier `evidence/red-on-HEAD.xml` (`tests=1 failures=1`,
      2026-11-15T21:01:35Z), the assertion answering `expected 'tools/loop/phase.sh', got 'tools/loop/state.sh'` and
      naming `tools/loop/state.sh`'s 39 records.
- [x] 1.2 The cheap declaration check the delta owes first — `tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach`.
      **Verified** `PASS driver#everyCaseDeclaresTheFilesItCanReach`, 2026-11-15T21:01:49Z
      (`evidence/declaration-check.log`).

## 2. The fix

- [x] 2.1 `tools/loop/tests/loop-mutations-selftest.sh:144` — `scoped_file="tools/loop/phase.sh"`, the file the
      declaration gives the fewest records to among the files that own at least two, with the comment above it stating
      that rule, the cost the pick removes and the case that holds it to the declaration.
      **Verified** by the case above passing (`evidence/green-case.log`, `PASS
      mutation#theFileSetLegScopesByTheCheapestFile`, 2026-11-15T21:02:13Z) and by the self-test's own run: `all 36
      cases passed` in 50.5 s (`evidence/self-test-leg.log`), where the base's leg was killed at its `timeout 900` (the
      item measured the same leg over two 26 min runs, issue #934).
- [x] 2.2 The leg's four assertions, its `timeout 900`, legs 1–6 and legs 8–10 are unchanged — the same 36 cases run
      and pass as on the base, and the whole self-test is now 50.5 s rather than the 26 min the item measured.
      **Verified** by the same run (`evidence/self-test-leg.log`, 2026-11-15T21:06:24Z→21:07:14Z, `all 36 cases
      passed`), taken again after the two records were added so the log is the final tree's.

## 3. The mutations

- [x] 3.1 `tools/loop/tests/loop-mutations.sh`: record `934.1`, which points the leg back at
      `tools/loop/state.sh` — the file that owns 39 of the declared records — so the case reds for the right reason
      rather than for a line that moved; and record `934.2`, which points it at a file the declaration gives one record
      to (`tools/loop/prompts/pre-gate-review.md`), so the arm that names a set of one instead of reading it as a set
      is exercised too.
      **Verified** `--check --only '934.1 934.2'` → `all 2 cases passed — every selected record applies to the file it
      names` (`evidence/mutation-934-check.log`), then the two records: `red-when-mutated: 2 · not-noticed: 0 ·
      restore-failures: 0`, each restore `green again` (2026-11-15T21:05:52Z, `evidence/mutation-934.log`) — `934.2`
      answering `the file-set leg scopes by tools/loop/prompts/pre-gate-review.md, which the declared set gives no two
      records to`.
- [x] 3.2 The declared set after the two records: `bash tools/loop/tests/loop-mutations.sh --list | grep -c .` → 403,
      from the 401 `evidence/per-file-counts.log` measured, and every one of the 403 applies — `bash
      tools/loop/tests/loop-mutations.sh --check` → `all 403 cases passed` (2026-11-15T21:06:21Z,
      `evidence/set-check.log`). The set check is the gate's step as well; it was taken here because both records this
      change adds are in the set it reads.

## 4. The delta and the prose that owns the rule

- [x] 4.1 `openspec/changes/fix-the-file-set-leg-runs-the-cheapest-file/specs/agent-issue-loop/spec.md`: one ADDED
      requirement, `The self-test's file set is scoped by the cheapest file the set declares`, with one scenario
      naming the case.
      **Verified** `LOOP-MEASURE-CHANGE … scenarios=1 cases=1 unnamed=0` (`tools/loop/measure.sh --change
      fix-the-file-set-leg-runs-the-cheapest-file`).
- [x] 4.2 No guideline edit: `sed -n '873,1108p' guidelines/Backlog.recipes.md | grep -n 'mutation'` answers four
      matches (lines 113, 203, 219 and 220 of the section) and none of them states which file the self-test's leg scopes
      by, so the rule's prose is the file's own comment and this change's `design.md`.
      **Verified** by the structural checks of the files this change edits — `tools/check-openspec-hygiene.sh` and
      `tools/check-portability.sh`, both exit 0 (2026-11-15T21:05:07Z), quoted in `evidence/structural-checks.log`.

## 5. The cases the delta reaches

- [x] 5.1 The cases whose declared mutation records name `tools/loop/tests/loop-cases.sh` — this case, `mutation#*`,
      `gate#theMapCoversEveryPipelineStep` and `run#theStopWaitIsDecidedByTheStateTheRunWrites` — plus the
      self-test's own 36 cases.
      **Verified** `PASS` each, and `loop-selftest: all 3 cases passed … selection: --case mutation#*`
      (`evidence/affected-cases.log`, 2026-11-15T21:04:01Z→21:04:13Z).
- [x] 5.2 The declared case count after the case is added: `bash tools/loop/tests/loop-selftest.sh --list | grep -c .`
      → `461`, from the block's `LOOP-MEASURE-SUITE cases=460` before it.

## 6. What this change cannot show

- The gate is not this worker's: the whole-suite selection `tools/loop/tests/loop-selftest.sh --from-git 8f84a8b8`
  (461 cases), `bash tools/loop/tests/loop-mutations.sh --check` over all 403 records, and the portability and issue
  tooling steps it owes are the gate child's, quoted from `tools/loop/measure.sh --change`'s `LOOP-MEASURE-CHECK`
  lines rather than run here. The evidence above is the red case, the affected cases and the changed file's own
  suite.
