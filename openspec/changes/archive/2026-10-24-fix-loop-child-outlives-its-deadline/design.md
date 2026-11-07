# Design — fix-loop-child-outlives-its-deadline

## The shape of the defect

The run owns two clocks and enforces one of them:

```
state.sh init            → run.deadlineEpoch written once, with the budget
state.sh next            → now >= run.deadlineEpoch  → "deadline passed at …"   (between items only)
run.sh run_child (HEAD)  → while [ ! -e "$status_file" ]
                             if now - started >= LOOP_CHILD_MAX_SECONDS  → end the child
                             sleep 1
```

The child cap therefore overrides the budget in both directions: a child started 40 minutes before the deadline
may legally run 45 more (`LOOP_CHILD_MAX_SECONDS` default), and a child started with the floor's margin left may
run well past it. Nothing in the wait reads the deadline, so the budget is spent invisibly and no stop is
recorded; the item's own record of the stretch reads `items: 0` with no reason.

## D1 — the wait reads the deadline on every tick

**Chosen: read `run.deadlineEpoch` from the run's state on each pass of the wait, and end the child when it has
passed.** The state file is the authority `report.sh` and `state.sh next` already read, so the wait, the report
and `next` cannot disagree about the clock. The cost is one `jq` per second while a child runs (measured 2026-10-24:
5 ms on the development host); the alternative was a bound computed once at child start, which is cheaper but
makes the wait read a clock nobody else reads and cannot answer a state whose deadline the run has corrected.

**Alternative A — bound the child by `min(cap, deadlineEpoch − started)` computed once, before the wait.**
Eliminated as *the* rule because it re-derives the deadline into a second number the run then trusts: the report
and `next` read `run.deadlineEpoch`, and a run whose state was corrected (a resumed state, a person's repair)
would obey a bound nothing else agrees with. A derived bound is also where a second copy of the arithmetic
starts, and the loop's rule is that the state is the one authority (`state.sh` is the only writer of it).

**Alternative B — a watchdog process that ends the child at the deadline.** Eliminated: a second process would
have to write the run's stop and take its transcript with no lock, and the run already owns exactly one poll
per child. A watchdog that dies leaves nothing behind to notice — the failure mode this change is about.

**Alternative C — the run's own `timeout` around the child's subshell.** Eliminated: it cannot tell the deadline
from the cap, so the transcript could not say which clock ended the child (the item is about a run that could not
tell), and it would not end the descendants that gave themselves their own session — the sweep
`loop_end_pids_under` does, and which only the run can name in the transcript.

The transcript keeps `LOOP-TIMEOUT:` as its first line in both arms, because that is the one machine-readable
line the run's own caller reads as "the run ended this child" (`report_field … LOOP-TIMEOUT`); the deadline arm
adds `LOOP-DEADLINE:` with the clock and the elapsed seconds, so a reader — and the case — can tell which bound
fired. The deadline arm also records the stop through the same `stop_run` every other path uses.

**That `stop_run` is not the stop a later run reads on the ordinary path, and the change does not pretend it is:**
after the item is recorded the loop calls `state.sh next`, whose own reason (`deadline passed at …`, or the item
cap if that was reached first — `state.sh:257-258`) overwrites it. What the recorded stop guarantees is that a run
ending here is never replaced as "never recorded a stop reason"; the early write covers the run killed between the
child and `next`. A mutation of that line was written and removed (`23.3`), because the case cannot see it and a
mutation nobody notices is not evidence. The deadline's own case therefore asserts the deadline as the recorded
stop with the item cap out of reach (`--items 3`), and the spec scenario says the same.

## D2 — the completion record is cleared before the launch, not after

**Chosen: `rm -f "$status_file"` moves above the launch.** The wait ends on the child's own record; a record
cleared after the child can write it is a record that can be deleted after it was written, and the run then waits
for a child that has already finished. The window is the two forks between `&` and the `rm` — small, but its
failure mode is the exact symptom this change is about (a finished child reported as running for the cap's
length), and the fix is an ordering, not a mechanism.

**Alternative A — a unique record path per child (`<report>.status.<pid>.<n>`), no `rm` at all.** Viable and
race-free by construction; not chosen because it leaves a growing set of files in the item's evidence directory
where one stable path is what a person reads after a blocked item, and because the record's staleness across
attempts (the same report path is written by a retry) is then something a later reader has to reason about
instead of something the run rules out.

**No red case for D2, deliberately.** The window is sub-millisecond; a case that depends on winning it is red on
HEAD only sometimes, and a case that fails sometimes is not evidence. The red case is D1's, and D2 is recorded as
a hardening with its reason. Both arms are stated in `guidelines/Backlog.md`, its section "The loop" where the bound is described.

## Blast radius

| file | change |
|---|---|
| `tools/loop/run.sh` | the poll's clock and the deadline arm; the record's ordering |
| `tools/loop-cases.sh` | `run#childIsEndedAtTheRunsDeadline`, one entry in `LOOP_SHELL_CASES`, one dispatch |
| `tools/loop-mutations.sh` | `23.1` realigned to the rewritten poll line; `23.2` added |
| `guidelines/Backlog.md`, its section "The loop" | the bound a child carries, in words |
| `openspec/specs/agent-issue-loop/spec.md` | one ADDED requirement, two scenarios |

No prompt template, no configuration key and no state shape changes: an older engine reads this tree's state
exactly as it did, and this engine reads an older state (a state with no `deadlineEpoch` is treated as having no
deadline to enforce, which keeps a hand-written or resumed state runnable).

## Rollback

Revert the two `run.sh` edits, the case, the mutation records, the guideline paragraph and the spec delta. The
transcript lines are inert text; nothing in the state, the labels or an archive depends on them. The archived
change carries the red run, so a revert reintroduces the defect the case names.

## Verification

The change touches no compiled file. The structural checks are `tools/loop/gate.sh` — the profile maps
`tools/loop/**` to `tools/loop-selftest.sh`, `tools/loop-mutations.sh --check` and `tools/check-doc-routes.sh` —
and `openspec validate fix-loop-child-outlives-its-deadline --strict`. The new invariant is falsified with
`tools/loop-mutations.sh --only '23.1 23.2'`: `23.2` must make the new case red, `23.1` the cap case, and each
restore must be green. The descendant sweep the deadline arm shares with the cap arm is not asserted again by
the new case — it is the same code path, pinned by `run#detachedProcessInTheWorktreeIsEndedByTheCap`.
