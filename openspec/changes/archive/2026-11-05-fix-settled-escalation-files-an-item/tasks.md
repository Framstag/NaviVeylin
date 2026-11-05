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
      requirement and the scenario "A settled issue's escalation files the returned condition", each scenario
      naming its case. Verification: `bash tools/check-openspec-hygiene.sh` exits 0 and every scenario in the
      delta names a case that exists in `tools/sensor-cases.sh`.
- [x] 3.2 `guidelines/BacklogTooling.md` §9 — one clause on the hysteresis rule: a refusal files the returned
      condition, it never reports the escalation. Verification: `grep -c 'refus' guidelines/BacklogTooling.md`
      = 1 in that section and `bash tools/check-rule-routes.sh` still passes.

## 4. Verification

- [x] 4.1 `bash tools/sensor-selftest.sh` — the sensor capability's own suite, all cases green. Verification:
      the suite's own summary line (`sensor-selftest: all <n> cases passed`), captured beside the case's
      evidence; the gate itself is the gate child's and is not run here.
- [x] 4.2 Revert-check: make `sensors_escalate`'s last command `rm -f …` again (the mutation), confirm task
      1.1's case fails on the filing assertion, restore, and re-run the case green. Verification: the case's
      `PASS` line after the restore, with no `REVERT-CHECK MUTATION` left in `tools/sensors/run.sh`.
- [x] 4.3 `bash -n tools/sensors/run.sh` and `bash -n tools/sensor-cases.sh` — no compiled file is touched, so
      the structural check stands in for a build (`guidelines/Build.md` §2, the scope block). Verification: both
      exit 0.
