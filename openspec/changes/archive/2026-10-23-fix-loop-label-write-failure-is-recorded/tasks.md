# Tasks — the refused label write is recorded with the item

- [x] 1. Red cases first, on the base engine: `run#aFailedEscalationWriteIsNamed` and `run#aFailedUnpickIsNamed`
      drive the real `run.sh` with the stub `gh` failing one label write the way a socket does
      (`GH_STUB_TRANSIENT_FAIL`, `ISSUES_GH_RETRY_DELAYS="0 0 0"`). Verified red before any engine edit:
      `tools/loop-selftest.sh --case 'run#aFailed*'` → `2 of 2 cases failed`, `evidence/red-HEAD.log`,
      `evidence/red-HEAD.xml` (`tests="2" failures="2"`).
- [x] 2. `tools/loop/run.sh` blocked path: `label_failures` records `unpick` and `escalate`, the item's `--note`
      carries the same sentence, and `state.sh done` is handed `--label-failures`. Verified by the two cases.
- [x] 3. `tools/loop/state.sh`: `done --label-failures <transition>…` → `.labelFailures`, merged on a second
      record. Verified by `tools/loop-selftest.sh --group state` (14 cases) and by the two red cases' field
      assertion.
- [x] 4. `tools/loop/report.sh`: the sixth action source. Verified by the two cases' report assertions and by
      `tools/loop-selftest.sh --group report` (15 cases).
- [x] 5. Mutation records `14.6`, `14.7`, `14.8` — the falsification the change owes: each mutation lands, its
      named case goes red for the reason the record states, the restore goes green again. Verified:
      `bash tools/loop-mutations.sh --check` → `all 126 cases passed`; `bash tools/loop-mutations.sh
      --only "14.6 14.7 14.8"` → `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`
      (`evidence/revert-check.log`).
- [x] 6. The cases the delta's files declare, run before the gate: `--group run` (75 cases, all passed),
      `--group state` (14), `--group report` (15), `--group labels` (6).
- [x] 7. The guideline: `guidelines/Backlog.md` §7 names the refused write as the report's sixth action source and
      records it in the mid-item escalation paragraph. Verified by `tools/check-doc-routes.sh` in the gate.
- [x] 8. Gate once from the change root — `./tools/loop/gate.sh`, `evidence/gate.log`.
- [x] 9. Archive the change and confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.

## Verification, re-measured after the attempt's worker was killed

This attempt's worker was killed at 2700 s (exit 124) during the 126-record mutation sweep, and the tree it left
was committed with one mutation still applied: `tools/loop/report.sh` held `stop_reason=""`, which is record
16.3's replacement, so the committed tree read no recorded stop reason at all. The line is restored in its own
commit (`fix(loop): restore the stop reason the mutation sweep left un-restored (#279)`), and the work is
re-verified from that tree rather than taken on the killed worker's word:

- `tools/loop-selftest.sh --case 'run#aFailed*'` → `all 2 cases passed`.
- `tools/loop-selftest.sh --case 'run#stopReasonIsPrintedAndReported'` → `PASS` (it was red on the tree as committed).
- `tools/loop-mutations.sh --check` → `all 126 cases passed`.
- `tools/loop-mutations.sh --only "14.6 14.7 14.8 16.3"` → `red-when-mutated: 4 · not-noticed: 0 ·
  restore-failures: 0`.
- Full set, owed by a change that owns `tools/loop/**`: `201 of 204 cases passed` (`evidence/selftest-full.log`).
  The three red cases are not this change's: `run#prescreenAnnouncesItselfBeforeItReturns` and
  `run#aWriterTheRunCanPlaceBlocksTheItem` fail the same way on the base (`evidence/baseline-main-red.log`), and
  `run#childOutlastingTheWallClockIsBlocked` passes alone on both trees and fails only inside the full suite —
  the load-sensitive shape issues #259 and #260 own.
