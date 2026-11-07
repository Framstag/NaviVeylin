# Proposal — fix-refusal-before-the-first-item-records-its-stop

## Why

**The condition, verified in the tree at base `62ab008d` (2026-11-05).** The decision cap's refusal prints its two
counts and the candidate list (`tools/loop/run.sh:752`–`:757`) and then leaves with `exit "$LOOP_REFUSE"` (`:758`)
— no `stop_run`, so `run.stop` stays empty. The rule every other early refusal follows is stated in the tree
beside the function that records it: *"every path that leaves the run records its reason, so `report.sh` prints
the end the state file holds (#184)"* (`tools/loop/run.sh:558`–`:565`). The checkout guard (`:580`), the module
pin (`:732`) and the pre-screen arm (`:806`) all call it.

**Root cause, one sentence.** A refusal between `state.sh init` and the first item that leaves without `stop_run`
writes a state whose `run` block carries no `stop`, and `report.sh:103`–`:120` reads that shape as a process that
was killed: *"budget left unknown: the run that wrote this state is gone (pid …) — it was killed and recorded no
stop"*.

**A surface the item under-counted.** The item calls the cap *"the one early exit between `state.sh init` and
phase A that records no reason"*. Three exits share the mechanism, not one — the `--reset-worktree` repair that
cannot clean the tree (`:692`), the refused kept worktree that names the item whose work it holds (`:721`) and the
cap (`:758`) — while three record it (`:580`, `:732`, `:806`):

```
$ grep -n 'stop_run\|loop_refuse\|exit "\$LOOP_\|end_run' tools/loop/run.sh | awk -F: '$1>=549 && $1<=800'
562:stop_run() { # <reason> — printed and recorded, then the caller breaks or exits
580:  stop_run "the project tree is checked out on ${checkout_ref:-a detached HEAD}, not the base branch …
582:  exit "$LOOP_REFUSE"
692:      loop_refuse "the kept worktree is still not clean after --reset-worktree — nothing was started; …
721:    exit "$LOOP_REFUSE"
732:  stop_run "the base records a submodule pin no clone this host holds: …
734:  exit "$LOOP_REFUSE"
758:  exit "$LOOP_REFUSE"
764:  end_run
765:  exit "$LOOP_OK"
798:    end_run
799:    exit "$LOOP_OK"
```

Three refusals (`:692`, `:721`, `:758`) leave no reason. The two `end_run` lines are ends of a run that produced
what it could, not refusals, and this change leaves them alone (see `design.md`, "What is not changed").

**Why it matters.** A refusal and a crash read the same, and the reader's next step differs: one owes the
decisions the cap counted, the other an investigation into a window that produced nothing at all. The same defect
was repaired once on the pre-screen arm (#227, *"a run died 30 s in at exit 2 and the state blamed nobody"*); this
is the change that routes the remaining three through the one function that records it.

**The red case.** `run#theDecisionCapRecordsItsStopReason` — a state whose candidate decisions reach the cap
refuses the start, and the run's own record carries the refusal: `run.stop.reason` names both counts it compared
and the decisions it printed, and `report.sh`, read after that run's process is gone, prints that reason instead
of the killed-process sentence. It fails on HEAD at its first assertion: `run.stop.reason` is empty.

**Owning capability and guideline section.** The capability is `agent-issue-loop`; the requirements are *A pile of
open decisions stops the run* (`openspec/specs/agent-issue-loop/spec.md:657`, scenario *The cap is reached*
`:664`) and *The run survives interruption* (`:608`, scenario *A kept worktree a previous run left dirty is
attributed and refused* `:635`). The guideline section that owns what a refusal records is
`guidelines/Backlog.recipes.md`, its section `7d. Refusals, records and the landing`
(`grep -n '^## ' guidelines/Backlog.recipes.md`).

**Surface.** `grep -n 'stop_run\|loop_refuse\|exit "\$LOOP_' tools/loop/run.sh` — six refusals between `:555`
(`state.sh init`) and `:806`, three of which leave no reason; `grep -c 'decisionCapRefusesToStart'
tools/loop-mutations.sh` — one record, anchored on the comparison line at `:747` that this change keeps verbatim.

surface: 1 case added, 2 rewritten, 0 fixtures realigned, 1 mutation added — `grep -n 'stop_run\|loop_refuse\|exit "\$LOOP_' tools/loop/run.sh` (3 of 6 refusals in the window record no reason) and `grep -c 'theDecisionCapRecordsItsStopReason' tools/loop-mutations.sh` (1, the record `9.12` added for this change's guard)

## What Changes

- `tools/loop/run.sh`: the three refusals that leave between `state.sh init` and the first item record what they
  refused, through `stop_run` — the cap (`:758`), whose reason carries both counts it compared and the decisions
  it printed; the kept worktree a previous run left dirty (`:721`), whose reason names the refusal and the item
  whose work the tree holds; and the `--reset-worktree` repair that cannot make the tree clean (`:692`). Each
  prints its report, as the two neighbouring refusals already do.
- `tools/loop-cases.sh`: `run#theDecisionCapRecordsItsStopReason` — added, and registered in the case list and the
  dispatch. `run#dirtyKeptWorktreeIsARecordedRefusal` and `run#resetWorktreeNamesTheDirtItCannotDiscard` each gain
  the one assertion that the refusal is the run's recorded stop reason.
- The spec delta (`specs/agent-issue-loop/spec.md`) states the record in both scenarios: the cap's refusal is the
  run's stop reason naming both counts and the items, and a refused kept worktree is the run's stop reason rather
  than a run that ended without one.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop` — *A pile of open decisions stops the run*: the refusal records its reason, naming both counts
  it compared and the decisions it printed. *The run survives interruption*: a kept worktree refused for dirt a
  previous run left is recorded as the run's stop reason, not only as the item's block.

## Impact

`tools/loop/run.sh`, `tools/loop-cases.sh`, the shipped spec `agent-issue-loop`. No compiled file, no Gradle case,
no app behaviour, no tracker write. The gate for this path is `tools/loop-selftest.sh`, which is offline. A refusal
that already recorded its reason keeps the exact text it had; the three that did not now print one line more and
a report, which is the shape their two neighbours already have.
