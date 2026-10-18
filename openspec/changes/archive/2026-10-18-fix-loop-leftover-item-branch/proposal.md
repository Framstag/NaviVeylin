# Let a run start an item whose branch a previous run left behind

## Why

Issue **#170**. The run after the #169 fix did not work its item — it ended with nothing recorded:

```
Schwerwiegend: Branch 'fix/issue-109-a-car-screen-s-observation-body-is-still' existiert bereits
loop: start-item: git switch failed
…
closed 0 · skipped 0 · blocked 0 · budget left 1 items / 90 min · spent 0 min
| … | nothing processed |
```

**Root cause**: `start-item` creates the item's branch unconditionally (`git switch -c fix/issue-<N>-<slug>`),
and `run.sh` treats any failure other than "base moved" with a bare `break`. Two consequences, both measured
(`evidence/red-on-head.log`, three cases that are red on HEAD and green after this change):

* an item whose branch a previous run left behind **can never be started again** — `git switch -c` fails
  because the name exists, whether or not that branch holds any work;
* the failure is **silent**: the item is not recorded, the tracker is not touched, and the report says
  "nothing processed" with no reason.

Leaving the branch is by design (D4: the branches stay until the run's commits are merged, and a blocked
item names its branch as the record of the work it did). What is not designed is that the branch makes the
item unstartable, and that the refusal leaves no trace — a blocked item is exactly the item a next run must
be able to take up again ("An interrupted item leaves no item looking in progress", and the pre-screen's
"a gated item is recorded, not silently dropped").

**The requirement the defect contradicts** is `agent-issue-loop`, "Version control belongs to the run's
script, not to the agent": the run owns the branch of each item, so it owes an answer for a branch that
already exists — and its answer may not destroy work the previous attempt recorded.

## What changes

1. **`worktree.sh start-item` answers the existing branch by what it carries.** When every commit on the
   branch is already in the run's base history it carries no work of its own — a blocked run that committed
   nothing leaves exactly that — so the item starts on it again, from the base (`switch -C`). When it carries
   a commit the base does not have, that commit is the previous attempt's work and the run's only record of
   it: the item is **refused** (`loop_refuse`, exit 5) with the branch and its commits named.
2. **`run.sh` records and escalates that refusal instead of breaking.** The item is written into the state
   as blocked with the refusal as its reason, the tracker gets `agent/needs-decision` + `status/on-hold` and
   one comment naming the branch, and the run takes the next candidate — a refusal a person has to resolve is
   never silent, and it never looks like "nothing processed".
3. **Three cases** and **three mutations** (one per check: reuse, refuse, record).

## Impact

| file | change |
|---|---|
| `tools/loop/worktree.sh` | `start-item` reuses a branch that carries nothing, refuses one that carries a previous attempt |
| `tools/loop/run.sh` | a `start-item` refusal on a leftover branch is recorded as blocked and escalated, then the run continues |
| `tools/loop-cases.sh` | `worktree#reusesABranchThatCarriesNoWorkOfItsOwn`, `worktree#refusesABranchCarryingAPreviousAttempt`, `run#leftoverBranchIsRecordedNotDropped` |
| `tools/loop-mutations.sh` | `12.5`, `12.6`, `12.7` |
| `openspec/specs/agent-issue-loop/spec.md` | one requirement modified: two scenarios on an existing branch |

No compiled file changes: the verification is the loop suite, the mutation set and the portability suite.

**Rollback**: `start-item`'s unconditional `switch -c` and the run's bare `break` — the three cases then fail
for the reasons recorded in `evidence/red-on-head.log`.

**Not claimed here**: nothing about a branch that is checked out in another worktree (git refuses the switch
itself and that refusal still breaks the run) — named in `design.md`, not measured.
