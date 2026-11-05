# Tasks — a refused escalation files the returned condition

## 1. The red case

- [x] 1.1 `tools/sensor-cases.sh` — `run#anEscalationOnASettledIssueFilesANewItem`: a closed issue carrying the
      condition's fingerprint and `status/fixed-by`, past the recurrence bound, must file a new item with the
      same fingerprint and must not report `escalated`. Verification: on the base
      `ec58cb91177f2d1c4621b87cdd26a9c7fced5117`, `bash tools/sensor-selftest.sh --case
      'run#anEscalationOnASettledIssueFilesANewItem'` reads `FAIL` with `expected '1', got '0'` on the filing
      assertion — runner tally `tests=1 failures=1`, `2026-11-05T07:52:12Z` (evidence:
      `evidence/red-on-HEAD.log` and its tally `evidence/red-on-HEAD.xml`, copied from
      `.pi/issue-loop/items/780/red-case.log`).
- [x] 1.2 `tools/sensor-cases.sh` — `run#theFiledConditionIsOpenWorkOnTheNextTick`: the tick after the filing
      meets a tracker carrying the filed item (open) beside the settled one and must annotate the filed item,
      file no second item and touch no settled state. Verification: the case passes on the fixed tree, and
      its own mutation (4.2, second mutation) turns it red — a measurement, not an assumption, is what
      `design.md`'s blast-radius claim now rests on.

## 2. The fix

- [x] 2.1 `tools/sensors/run.sh` — `sensors_escalate` returns the writer's exit code instead of `rm`'s.
      Verification: `bash -n tools/sensors/run.sh` passes, and the function's last statement is the cleanup
      followed by `return "$code"` (`grep -n 'return "\$code"' tools/sensors/run.sh`).
- [x] 2.2 `tools/sensors/run.sh` — the escalation branch of `sensors_outcome_violation` reports `escalated`
      only on `0`; a refusal files the condition within the row's `SENSORS_FILED` bound and reports
      `action=filed`, and fails the row when the bound leaves no room. Verification: task 1.1's case passes,
      and `grep -c 'escalated (past' tools/sensors/run.sh` = 1 (one report site, still the landed one).

## 3. The rule's owners

- [x] 3.1 `openspec/changes/fix-settled-escalation-files-an-item/specs/backlog-sensors/spec.md` — the modified
      requirement and the scenarios "A settled issue's escalation files the returned condition", "A refused
      escalation past the row's bound fails the run" and "The filed condition is ordinary work on the next
      tick", each scenario naming its case. Verification: `bash tools/check-openspec-hygiene.sh` exits 0 and
      every scenario in the delta names a case that exists in `tools/sensor-cases.sh`.
- [x] 3.2 `guidelines/BacklogTooling.md` §9 — one clause on the hysteresis rule: a refusal files the returned
      condition, it never reports the escalation. Verification: `grep -c 'refus' guidelines/BacklogTooling.md`
      = 1 in that section and `bash tools/check-rule-routes.sh` still passes.

## 4. Verification

- [x] 4.1 `bash tools/sensor-selftest.sh` — the sensor capability's own suite, all cases green. Verification:
      the suite's own summary line (`sensor-selftest: all <n> cases passed`), captured beside the case's
      evidence; the gate itself is the gate child's and is not run here.
- [x] 4.2 Revert-check, two mutations, each targeting one invariant and each named as the edit actually
      made. (a) `sensors_escalate`'s `return "$code"` replaced by `return 0  # REVERT-CHECK MUTATION`: task
      1.1's case fails on the filing assertion (`expected '1', got '0'`, the red run's message), restore,
      case green. (b) `sensors_issue_for_fingerprint`'s `then .[0]` replaced by `then .[-1]  # REVERT-CHECK
      MUTATION`: task 1.2's case fails on `expected '0', got '1'` (the tick files a second duplicate),
      restore, case green. Verification: `grep -c 'REVERT-CHECK MUTATION' tools/sensors/run.sh
      tools/sensors/lib.sh` = 0, 0, and `git status --porcelain` carries only the intended files.
- [x] 4.3 `bash -n tools/sensors/run.sh` and `bash -n tools/sensor-cases.sh` — no compiled file is touched, so
      the structural check stands in for a build (`guidelines/Build.md` §2, the scope block). Verification: both
      exit 0.
