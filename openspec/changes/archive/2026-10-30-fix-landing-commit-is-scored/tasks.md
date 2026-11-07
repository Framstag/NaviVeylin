# Tasks — fix-landing-commit-is-scored

The change touches no compiled file, so there is no build task: the structural checks that stand in are
`openspec validate fix-landing-commit-is-scored --strict` over the change and the suites `tools/loop/gate.sh`
selects for `tools/loop/**`, `tools/loop-cases.sh` and `tools/loop-mutations.sh`
(`tools/loop/loop.conf`, `LOOP_GATE_SUITES`). `openspec/changes/*/` is gitignored while a change is open, so the
archive comes before the gate — until it sits under `openspec/changes/archive/`, the gate's pathspec owes nothing.

## 1. The red cases

- [x] 1.1 Add the fixture `tools/loop/fixtures/scenarios/landed/runs.json`: two push runs, one failed and one
      successful, newest first, plus a scheduled run of the same headSha as one of them. Verify:
      `GH_STUB_DIR=tools/loop/fixtures/scenarios/landed tools/issues/fixtures/gh run list --commit <sha> --json …`
      answers the push run and the schedule run for the sha that carries both, and `[]` for a sha it does not.
      *(spec: `agent-issue-loop` — A landing records the commit it landed, and a run scores it)*
- [x] 1.2 Add `state#doneRecordsTheCommitItLanded`, `state#theLandingScoreIsReadOnceAndStored` and
      `state#aLandingWithNoRunIsUnscored` to `tools/loop-cases.sh` and register them. Run all four the delta names —
      these three and `report#landingsAreTheStoredNumber` (task 4.1) — on HEAD with `evidence/red-on-head.sh`. Verify:
      four case failures, recorded in `evidence/red-on-head.xml` with `tests="4" failures="4"` and the timestamp.
      *(spec: `agent-issue-loop` — A landing records the commit it landed…: the three scenarios)*

## 2. The state's own record

- [x] 2.1 `state.sh done --merged-sha <sha>` records `.merged.sha`, refuses a value that is not 7–40 hex, and
      names the flag in the usage header. Verify: `tools/loop-selftest.sh --case state#doneRecordsTheCommitItLanded`
      prints `PASS`.
      *(spec: `agent-issue-loop` — A landing records the commit it landed…, scenario "The record carries the
      commit the landing made")*
- [x] 2.2 Add `state.sh score-landings`: one `gh run list --commit <sha>` per unscored landing, the push run
      chosen, `.landedReddenedMain` written `true`/`false`, and an unscored landing left null with its reason
      printed. Verify: `tools/loop-selftest.sh --case state#theLandingScoreIsReadOnceAndStored` and
      `--case state#aLandingWithNoRunIsUnscored` print `PASS`.
      *(spec: `agent-issue-loop` — A landing records the commit it landed…, scenarios "The score is read once per
      landing and stored" and "A landing with no run yet is unscored, not green")*

## 3. The run records and scores it

- [x] 3.1 `run.sh` passes the commit both landing paths hold (`base`) to `done --merged-sha`, and calls
      `state.sh score-landings` once after the merge, best-effort. Extend
      `run#mergesItsCommittedBranchBeforeClosing` with the two assertions — the record carries the base commit, and
      the fixture's landing is unscored rather than green. Verify:
      `tools/loop-selftest.sh --case run#mergesItsCommittedBranchBeforeClosing` prints `PASS`.
      *(spec: `agent-issue-loop` — A landing records the commit it landed…, scenario "The run records the commit
      of the merge it made")*

## 4. The report prints the number

- [x] 4.1 Rewrite `report#landingsNamedNotInvented` as `report#landingsAreTheStoredNumber`: a state with a
      reddened, a green, an unscored and a commitless landing, the line asserted, and the failing `gh` on `PATH`
      still uncalled. Verify: `tools/loop-selftest.sh --case report#landingsAreTheStoredNumber` prints `PASS`.
      *(spec: `agent-issue-loop` — The report states whether the loop is getting better, scenario "The landings
      that reddened main are the stored score")*
- [x] 4.2 Rename the case in mutation record 72.6 of `tools/loop-mutations.sh`. Verify:
      `bash tools/loop-mutations.sh --check` reports `0 problem(s)`.
      *(the case must stay capable of failing, and a record that names a case the suite does not have names
      nothing)*

## 5. Structural verification, evidence and archive

- [x] 5.1 Validate: `openspec validate fix-landing-commit-is-scored --strict`. Verify: exit 0, and
      `evidence/openspec-validate.log` carries the change's name and `is valid`.
- [x] 5.2 Archive: `openspec archive fix-landing-commit-is-scored --yes`. Verify: the directory exists under
      `openspec/changes/archive/2026-10-30-fix-landing-commit-is-scored/` and the delta's requirement is in
      `openspec/specs/agent-issue-loop/spec.md`.
- [x] 5.3 Run the gate once, from the change root: `./tools/loop/gate.sh`. Verify: its own summary
      (`LOOP-GATE: tests=… failures=0 errors=0`) is copied into `evidence/gate.log` with the tally XMLs, and
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-30-fix-landing-commit-is-scored --root .`
      reports no dangling pointer.
