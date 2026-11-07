# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red case

Requirement: "A cut item is named to the caller as work to resume", scenario "The action list names a cut item for
resume and asks for no gate".

- [x] 1.1 `tools/loop-cases.sh`: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal` gains two assertions on the report's
      "actions the caller owes" block — `resume #21` is there, `re-run the gate for #21` is not — and its `local` list
      gains `actions`. Verify: on HEAD's `tools/loop/report.sh` (the file without this change) the case fails at the
      first assertion, `'resume #21' not found in: actions the caller owes:`
      (`evidence/red-report-resume-action.log`); with the change it passes (`evidence/green-report-resume-action.log`).

## 2. The engine

- [x] 2.1 `tools/loop/report.sh`: the "no gate result" arm skips `cut` (`select(.result != "cut")`), with the comment
      that says why — the gate was never owed, because the run ended the child first.
- [x] 2.2 `tools/loop/report.sh`: a seventh source turns every item recorded `cut` into
      `resume #N — the run ended a child of it at a bound, so no decision is owed — <its note> · transcript <dir>`, and
      the `none` line names that source so an empty list still says what it covered.

## 3. Verification

- [x] 3.1 `bash tools/loop-selftest.sh --case 'run#aReviewerEndedAtItsTurnBoundIsNotARefusal'` and
      `--case 'report#emptyRunSaysNone'` both PASS (`evidence/green-report-resume-action.log`), and the two other
      report cases that read the action block are green in the gate.
- [x] 3.2 No compiled file is touched, so no build and no Gradle suite is owed: the gate is the loop's own suite over
      this change's pathspec.
- [ ] 3.3 The gate over the whole change, and its tallies, land in `evidence/` with the archive.
