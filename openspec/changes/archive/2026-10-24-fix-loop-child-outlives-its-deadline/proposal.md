# fix-loop-child-outlives-its-deadline

## Why

A run's budget is spent invisibly inside a child. `run_child` bounds the wait by the child's own wall clock
(`LOOP_CHILD_MAX_SECONDS`) and by nothing else, while the run's own deadline (`run.deadlineEpoch`, written by
`state.sh init`) is consulted only by `state.sh next` between items. A child started before the deadline can
therefore hold the run past the budget it was given, and because `stop_run` is only reached after the wait
returns, such a run records no stop at all.

Measured 2026-10-20 (issue #227): fifteen `run.sh` processes were alive at once, eleven of them past their own
`--minutes 90`; one wrote a heartbeat every 30 s for `291m30s` (606 lines), its transcript stayed at `log 0 B`,
and not one carried a `LOOP-TIMEOUT` line although the cap was 2 700 s. The state of that stretch holds
`items: 0` with no stop reason, and the next run printed `state: the state being replaced never recorded a stop
reason` over it. Two of the three readings the item listed are settled by the tree: the poll already waits on
the child's own completion record (`<report>.status`, written by the subshell that starts it) instead of a
recycled pid, so the "record pid + start time" half is superseded; what remains is the missing clock and one
ordering detail in the same wait.

**Root cause, one sentence:** the wait in `run_child` reads only the child cap and never the run's recorded
deadline, so the one clock a run is given cannot end the one process it is actually waiting for.

**Red test on HEAD, before the fix.** `run#childIsEndedAtTheRunsDeadline`, added to `tools/loop-cases.sh` and
run against the engine at `49dfd7db` with its own `tools/loop/run.sh` unchanged: a child that blocks forever
while the run's recorded deadline is moved two seconds away is still being waited for 60 s later —
`loop-selftest: 1 of 1 cases failed` (`evidence/red-HEAD.log`, `evidence/red-HEAD.xml`,
`tests="1" failures="1"`).

**Second defect in the same wait, fixed with it.** The completion record was cleared *after* the child was
started (`tools/loop/run.sh` at HEAD: launch at `:798`, `rm -f "$status_file"` at `:832`): a child that
finished inside that window had its own record deleted, and the run then waited the whole cap for a child that
had already exited — the same symptom ("a child that no longer exists is reported as running"), one fork wide.
It is fixed by clearing the record before the launch. It carries **no red case** on purpose: the window is
sub-millisecond, so a case for it would be red on HEAD only sometimes, and a flaky case is not evidence.

## What Changes

- `tools/loop/run.sh` — the wait reads `run.deadlineEpoch` from the run's own state on every tick and ends the
  child (and everything it started) when the deadline passes; the transcript gains
  `LOOP-DEADLINE: the run's deadline passed at <deadlineAt> — the child running then was ended after <n> s` and
  the `LOOP-TIMEOUT` line now names the clock that ended it; the run records the deadline as its stop through
  `stop_run`, so a run that ends there leaves a reason instead of being replaced as "never recorded a stop".
- `tools/loop/run.sh` — the completion record is cleared before the child is started, not after.
- `tools/loop-cases.sh` — one case, `run#childIsEndedAtTheRunsDeadline`: transcript, item verdict, item note and
  the recorded stop are asserted, and a child the cap (3 600 s) could not have ended.
- `tools/loop-mutations.sh` — `23.1` realigned to the poll's rewritten line; `23.2` (the deadline branch) added.
  A `23.3` for the `stop_run` the same arm writes was written and then **removed**: the case cannot see it,
  because `state.sh next` overwrites that stop with the deadline it names on the ordinary path (D1), and a
  mutation nobody notices is not evidence.
- `guidelines/Backlog.md`, its section "The loop" — the paragraph that states what bounds a child names the run's own
  deadline beside the child cap, and the completion record's ordering.
- `openspec/specs/agent-issue-loop/spec.md` — one ADDED requirement, "A child never outlives the run's
  deadline", with two scenarios (the deadline ends the child; the deadline is the stop a later run reads).

Additive. The change touches no compiled file: no Kotlin, Java or C++ path is edited, so no Android build is
owed. Rollback is a revert of the three script edits, of the guideline paragraph and of the spec delta — the
transcript lines are inert text for an older engine, and no state, label or archive shape changes.

## Impact

| file | what |
|---|---|
| `tools/loop/run.sh` | the wait's clock; the completion record's ordering |
| `tools/loop-cases.sh` | the new case, registered in `LOOP_SHELL_CASES` and dispatched |
| `tools/loop-mutations.sh` | `23.1` realigned, `23.2` added |
| `guidelines/Backlog.md`, its section "The loop" | the bound a child carries, in words |
| `openspec/specs/agent-issue-loop/spec.md` | one ADDED requirement, two scenarios |

The owning capability is `agent-issue-loop`; the guideline that owns the loop's mechanics is
`guidelines/Backlog.md`, its section "The loop". The verification the change owes is the loop's own shell gate
(`tools/loop-selftest.sh`, `tools/loop-mutations.sh`, `tools/check-doc-routes.sh`) — dispatched by
`tools/loop/gate.sh` from the profile — and not a Gradle run.
