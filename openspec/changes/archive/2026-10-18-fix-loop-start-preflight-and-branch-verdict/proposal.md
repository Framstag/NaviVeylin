# Let the run's start see what a previous run left behind

## Why

Issues **#173** and **#175**. Two leftovers of a killed or blocked run are invisible to the run that follows
it, and both end that run without an item, a reason or a repair path.

**#173 — the root cause is the position of the worktree check, not the check.**
`tools/loop/worktree.sh` (`cmd_create`, `[ -z "$dirty" ] || loop_refuse "the worktree at $worktree is not
clean — the previous run left work behind"`, `tools/loop/worktree.sh:58`) refuses a kept worktree that is not
clean — correctly, because the previous run's unfinished work is not this run's to commit. But `run.sh`
reaches that call only after `loop_state init`, the decision-cap preflight, `prescreen.sh` and
`loop_state lock` (`bash "$LOOP_DIR/worktree.sh" create --branch "$branch" || exit $?`), so the refusal ends
the run with exit 5 **before any item exists**: the pre-screen is spent, no report is printed, no item is
recorded and no tracker write is attempted. Measured on HEAD (`.pi/tmp/head-engine`, the engine as committed;
`evidence/red-on-head.log`):

```
run 2 exit=5
pre-screen: 1 eligible, 0 excluded — gates 2-5 stay the worker's (design D6)
loop: the worktree at …/repo/.pi/worktrees/loop is not clean — the previous run left work behind:
?? leftover.txt
items recorded: 0  excluded: 0  worker processes: 0
the run printed a report: 0
```

`run#dirtyKeptWorktreeIsARecordedRefusal` and `run#resetWorktreeDiscardsWhatItNames` are the two cases that
are red on HEAD and green after this change.

**#175 — the root cause is that the pre-screen asks only the tracker.** Whether an item was already attempted
is held by git, not by the tracker: `worktree.sh start-item` answers it with
`git merge-base --is-ancestor "$branch" "$base"` (after `git rev-parse --verify refs/heads/$branch`), while
`prescreen.sh` decides every verdict it can from the author, the class, the status, the closed state and the
citation sensor and from nothing else. So an item whose branch already carries an attempt is only caught by
`start-item` — after it was queued, picked, labelled in progress and given a budget slot. Measured on HEAD
(`evidence/red-on-head.log`):

```
pre-screen exit=0
  #21 eligible                      # its branch fix/issue-21-a-previous-attempt carries a commit
queue: 1  excluded: 0  labels the pre-screen wrote: 0
…and what the run's start — the authoritative check — does with the same branch:
  loop: branch fix/issue-21-a-previous-attempt already exists and carries commits the base does not …
```

`prescreen#previousAttemptBranchIsADecision` and `run#leftoverBranchIsRecordedNotDropped` are the two cases
that are red on HEAD and green after this change.

**The requirements the defects contradict** are `agent-issue-loop`'s *"Version control belongs to the run's
script, not to the agent"* — the run owns the working tree and the branches, so it owes a name, a record and a
decision for what a previous run left, and its "A branch that carries a previous attempt is refused, named and
recorded" scenario describes exactly that — and *"The run survives interruption"* — the state "SHALL be
sufficient to continue after an interrupt", and a run that ends with nothing processed and no reason is not.

## What changes

**One fix, in two halves: the run's start looks at the tree and at git before it spends anything, and what it
finds is named, recorded and — when a person owes the decision — escalated.**

1. **A start-time pre-flight on the kept worktree** (`run.sh`, before `prescreen.sh` and before the lock).
   When the kept worktree is dirty, the run names the files, derives the item from the worktree's branch
   marker (`.pi/loop-branch`, which `worktree.sh start-item` now writes with the item's branch), records the
   refusal as a blocked item under that item, escalates it (`agent/needs-decision` + `status/on-hold`, the
   branch as evidence), prints the report and refuses with exit 5. A worktree a **live** run is working in is
   not leftovers, so the pre-flight stands aside when the run's own lock is held — that run refuses at the
   lock as it always did.
2. **A start-time repair flag `--reset-worktree`** that names what it discards before discarding it and
   leaves the commits on the previous branches alone. `--clean-worktree` stays the end-of-run form, and now
   holds wherever the run ends, including the "nothing eligible" exits the pre-screen can produce.
3. **The pre-screen's branch verdict** (`prescreen.sh`): the same question `start-item` asks, asked on the
   branch name's prefix (`fix/issue-<n>-`, because the slug is not known before the title is read) against the
   run's recorded base, as one more mechanical verdict beside the citation sensor. It lands in the run's
   `excluded[]` with the branch as the evidence, and the run escalates it before the first item is picked —
   `start-item`'s own check stays the authoritative one.
4. **Five cases** (four red on HEAD, one guarding the authoritative start check) and **eight mutations**.

## Impact

| file | change |
|---|---|
| `tools/loop/run.sh` | the start-time pre-flight on the kept worktree, `--reset-worktree`, the pre-screen verdicts that need a decision are escalated before any item, `end_run()` holds `--clean-worktree` wherever the run ends |
| `tools/loop/prescreen.sh` | the branch query beside the citation sensor: a branch that carries a commit the run's base does not is a decision, recorded in `excluded[]` with the branch as evidence |
| `tools/loop/state.sh` | an exclusion carries the one clause of evidence that produced it (the branch, or the stale path) |
| `tools/loop/worktree.sh` | `start-item` writes the item's branch into the worktree's branch marker; `create`'s own refusal stays, for `create` used on its own |
| `tools/loop-cases.sh` | `prescreen#previousAttemptBranchIsADecision`, `run#leftoverBranchIsRecordedNotDropped` (rewritten to the pre-screen path), `run#branchThatAppearsWhileTheRunIsOpenIsRecorded`, `run#dirtyKeptWorktreeIsARecordedRefusal`, `run#resetWorktreeDiscardsWhatItNames` |
| `tools/loop-mutations.sh` | `13.1`–`13.7`, and `12.7` now guards the start's own refusal on the new case |
| `tools/loop/fixtures/scenarios/two/issues.json` | a two-item scenario, so the case can exhaust a queue and exercise the pre-screen's second pass |
| `openspec/specs/agent-issue-loop/spec.md` | two requirements modified: the previous-attempt scenario extended, one scenario added to the interruption requirement |

No compiled file changes: the verification is `tools/loop-selftest.sh`, `tools/loop-mutations.sh`,
`tools/loop-mutations-selftest.sh` and `tools/check-portability.sh`, all offline.

**Additive**, not breaking: two flags and two verdict paths are added; `--clean-worktree`, the exit codes and
the pre-screen's existing verdicts keep their meaning.

**Rollback**: drop the pre-flight block, the `--reset-worktree` arm, the pre-screen's branch query, the
`start-item` marker write and the `end_run` call sites — the five cases then fail exactly as recorded in
`evidence/red-on-head.log`.

**Not claimed here**: nothing about a *stale* lock (a run killed with SIGKILL leaves both the lock and the
dirty tree; the pre-flight stands aside and the lock refusal reports it), and nothing about a leftover branch
whose commits are already in the run's base (both the pre-screen and the start answer "no work of its own"
and the item is worked) — both named in `design.md`, neither measured.
