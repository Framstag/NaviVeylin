# Design — a refused escalation files the returned condition

## Root cause

`sensors_escalate` (`tools/sensors/run.sh:442-457`) discards the one guarded writer's exit code: its last
command is `rm -f "$bodyfile" "$commentfile"` (`tools/sensors/run.sh:456`), so the function returns `rm`'s
status. The escalation branch (`tools/sensors/run.sh:513-517`) reports `escalated` unconditionally. The writer
does refuse the write when the target carries a settled state — `tools/issues/update.sh:216-218` calls
`issues_status_settled_guard`, which exits 3 with `status-settled` (`tools/issues/lib.sh:408`) — so the run
prints an escalation, exits 0, and the label pair and any item at all are lost.

## The single fix

Two edits in `tools/sensors/run.sh`:

1. `sensors_escalate` keeps the writer's status across the temporary-file cleanup and returns it.
2. The escalation branch of `sensors_outcome_violation` reads it:
   - `0` — the escalation landed; report it exactly as today.
   - non-zero, the row's filing bound has room — file the returned condition as a new item, spending the same
     `SENSORS_FILED` bound the no-issue branch spends, and report `filed (the escalation of #<n> was refused:
     its state is settled)`.
   - non-zero, the bound leaves no room — `sensors_fail_row` with the refusal's status and the bound, so the
     run exits non-zero and the tick is red. This run records nothing about the returned condition: a summary
     is not written once a finding has failed, and widening the row's `maxFilings` files it on the next run.

A third, smaller edit follows from the second: `create.sh` refuses a second item for a fingerprint unless the
caller says the duplicate is meant (`tools/issues/create.sh:11-14`, exit 4), and this duplicate is the point —
the settled item records the fix, the new one records that it did not hold. `sensors_file` therefore takes the
writer's own flags after the body, and this call passes `--allow-duplicate`; every existing call site passes
nothing and is unchanged.

The refusal is not caught by an error message: the branch reads a return code the tooling already produced,
so a writer that fails for any other reason (a token without scope, a rate limit, a tracker fault) also
reaches the filing path instead of being credited with a write. That is the same rule `update.sh` follows for
`gh` (nothing is reported as written until the write landed).

## Alternatives the evidence eliminates

- **Pass `--displace-settled` to the escalation.** The write would land, and it would remove the settled
  `status/fixed-by` from the item — the state a recorded fix left. That reverses a settled decision on a
  routine write, which the profile's guard and the requirement "A check never reverses a person's decision"
  forbid; the flag exists for a caller that means that transition, and a sensor does not.
- **Make the writer's refusal exit 0.** It would silence the diagnostic that is the only trace of a lost
  write, and weaken every other caller (`close.sh`, the loop's label path) that reads exit 3 as "the item did
  not move". The writer is not the defect — it is the one component that reported the truth.
- **Reopen the settled issue instead of filing.** Same reversal as the first, and it would spend a reopen on
  an item whose fix is recorded as landed, leaving the tracker with no new fact.
- **Fail the row and write nothing.** The item permits it ("or the run fails so the tick is red"), but it
  records nothing about the returned condition: the next tick meets the same refusal, the settled issue stays
  the only one carrying the fingerprint, and a red main still has no open item. The red case requires the
  filing, so the failure is kept only where a filing is impossible (the row's bound).

## Blast radius

- `tools/sensors/run.sh` — two functions, no new key, no new label, no new file.
- Every escalation that lands is unchanged: the writer exits 0 and the report line is byte for byte today's.
- The new filing spends the row's `maxFilings` bound like any other filing; the bound's summary item is
  written by the no-issue branch, which this change does not touch.
- A later run finds the new item first (`sensors_issue_for_fingerprint` takes the first issue carrying the
  marker and `gh issue list` returns newest first), so the returned condition becomes ordinary open work
  instead of escalating again. That order is measured, not assumed:
  `run#theFiledConditionIsOpenWorkOnTheNextTick` runs the next tick against a tracker carrying the filed item
  beside the settled one and asserts that it annotates the open item, files no second item and leaves the
  settled one untouched.
- The duplicate sweep does not take the new item back: it closes a duplicate against the older item only when
  that item carries the authorisation label, and a settled item carries none (`guidelines/BacklogTooling.md`
  §9, "Beside the rows").

## Verification

- `run#aRefusedEscalationPastTheBoundFailsTheRun` — green after the fix: two conditions filed, the settled
  item untouched, `0` summaries, exit 8 and a report line naming the condition nothing recorded.
- `run#theFiledConditionIsOpenWorkOnTheNextTick` — green: the tick that meets the filed item annotates it,
  files nothing, and touches no settled state.
- `bash tools/sensor-selftest.sh` — the sensor capability's whole suite, which is what the gate owes this
  pathspec (`tools/loop/loop.conf`, `tools/sensors/*|tools/sensor-selftest.sh`); the gate itself is the gate
  child's.
- Structural check instead of a build: the change touches no compiled file, so `bash -n tools/sensors/run.sh`
  stands in for it.
- Revert-check: two mutations, each targeting one invariant the change adds, and the record names the edit
  actually made rather than a variant of it.
  1. `sensors_escalate`'s `return "$code"` replaced by `return 0  # REVERT-CHECK MUTATION` — credit a write
     that did not land: `run#anEscalationOnASettledIssueFilesANewItem` fails on the filing assertion
     (`expected '1', got '0'`), restore, case green.
  2. `sensors_issue_for_fingerprint`'s `then .[0]` replaced by `then .[-1]  # REVERT-CHECK MUTATION` — read the
     oldest item instead of the newest: `run#theFiledConditionIsOpenWorkOnTheNextTick` fails on
     `expected '0', got '1'` (the tick files a second duplicate), restore, case green.

## Rollback

Revert the two hunks in `tools/sensors/run.sh` and the case: the tree is the base again, with the settled
escalation writing nothing and reporting an escalation, exactly as before.
