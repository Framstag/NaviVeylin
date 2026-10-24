# fix-loop-lock-is-taken-before-anything-is-written

## Why

Two consecutive runs of the budgeted loop ended long before their own time budget, and neither recorded why
it ended. The evidence is in the run's own state directory, and it names one cause.

**What happened, with the files.** The run that started at 18:46:21 local time (`16:46:21Z`) had a 240-minute
budget and a deadline of 20:46:21Z. Its state is kept at `.pi/issue-loop/runs/2026-10-22T16:46:21Z.json`: two
steps, one item (`#250`, `skipped`), and **no `run.stop`**. At 19:19 the run's process group was killed while
its round-2 reviewer was still answering — `.pi/issue-loop/items/249/reviewer-report-2.md` is **0 bytes** — and
the kill left the run's lock behind: `.pi/issue-loop/lock/owner` named pid `3040779`, a process that no longer
existed.

The next run started at 19:34:14 (17:34:14Z) with the same 240-minute budget. It ended two minutes later.
Its state (`.pi/issue-loop/run.json`) carries no `run.stop` either, and no step — but it does carry the item
record of the run that was still alive when it started:

```
items[0].note: "adopted: branch fix/issue-249-guidelines-build-md-is-136-kb-and-its-fo -> 9f10749 · commits
                bb3c492…,918a0ea…,190ff4a…,9f10749… · change 2026-10-22-fix-build-md-routed-read-size"
```

and `git reflog main` names the moment that merge happened:

```
9f10749 main@{2026-10-22 19:35:52 +0200}: merge fix/issue-249-guidelines-build-md-is-136-kb-and-its-fo: Fast-forward
```

So the second run **merged the first run's unfinished item into `main` and closed it** — an item whose round-2
review had never answered — and then was refused.

**Why it could do that.** The lock that keeps one run per repository is taken at the *end* of the start-up
(`tools/loop/run.sh:432`). Everything that writes happens before it:

| step | file:line | what it writes |
|---|---|---|
| the state is replaced | `run.sh:146` (`loop_state init`) | the whole state file — the live run's record, its steps, its items |
| the pre-screen runs | `run.sh:391-400` | the queue and the exclusion list, into the same state file |
| a finished attempt is adopted | `run.sh:403` (`adopt_finished_exclusions`) | a merge into `main` and the item's closure |
| **the lock is taken** | `run.sh:432` | the lock directory — the refusal comes here |

A run that is refused therefore has already replaced the live run's state, rewritten its queue and merged a
branch. It is refused *after* the damage, and it records no stop reason of its own — it never owned a state, so
there is nowhere to record one — which is why the report of the state in place reads
`stop: not stopped — the run is still going, or it ended without recording a reason` with `217 min left`.

**The second half: a killed run's lock is a tombstone.** `cmd_lock` (`tools/loop/state.sh:543`) refuses
whenever the lock directory exists; it never asks whether the process that owns it is still running. A run
killed with `SIGKILL` — which no trap can catch — therefore leaves a lock that every later run is refused by,
after having replaced the state and paid for a pre-screen on the way in. That is the recurring shape: a run
that ends in two minutes with 217 minutes of budget left, for a reason it never wrote down.

**Root cause, one sentence:** the run's mutual exclusion is taken after the run has already written, so the
refusal that protects the live run comes after the damage, and the lock it leaves behind is never checked
against the liveness of its owner, so a run that was killed blocks every run that follows it.

**Red tests on HEAD, before the fix.** Three cases in `tools/loop-cases.sh`, added with this change and run
against the engine at `9f107493f4e6` — the evidence is in `evidence/red-HEAD.log` and `evidence/red-HEAD.xml`:

- `state#aRefusedRunLeavesTheLiveStateAlone` — a second run started while the first holds the lock replaces
  the first run's state file (the run's own `run.startedAt` is gone).
- `state#aDeadOwnersLockIsTakenOver` — a lock whose owner is gone is refused instead of taken over.
- `report#aKilledRunIsNotALiveRun` — the report of a state whose owner is gone prints a budget left and calls
  the run "still going".

**Owning capability and guideline section.** `agent-issue-loop` — the requirement "The run survives
interruption" owns the state and the lock, and "A run's state outlives the run that replaces it" owns what the
report may say about a run it did not watch. The guideline that owns the loop is `guidelines/Backlog.md` §7
"The loop"; its invariant I12 in §4 already states "one lock per run" — the code did not honour it.

**What is not fixed here.** Two findings this incident produced are separate defects with their own root
causes, and they are filed as tracker items instead of being folded in: the change merged for `#249` was
closed although its round-2 review never answered (the adopt path accepts a branch whose last review round is
unanswered), and the state never names the item a run is working on (`state.sh start` is never called by the
run, so `.current` carries a phase and no issue).

## What Changes

- `tools/loop/run.sh` — the lock is taken **before** the run writes anything: before the state is replaced,
  before the pre-screen and before the adoption path can merge a branch. The traps that release the lock are
  installed with it, so every later exit releases it.
- `tools/loop/run.sh` — the kept-worktree check loses the "no run holds the lock" condition: a live run is
  refused at the lock, so the tree it works in is never mistaken for leftovers.
- `tools/loop/state.sh` — `cmd_lock` asks whether the lock's owner is still running: a live owner is a
  refusal, a dead one is a takeover that is announced and named.
- `tools/loop/state.sh` — `cmd_init` records the run that wrote the state (`.run.ownerPid`).
- `tools/loop/report.sh`, `tools/loop/state.sh` — the report and the live view tell a run whose process is
  gone from one that is still going: no budget left is printed for a run that was killed, and the stop line
  names the process that is not running.
- `tools/loop-cases.sh` — the three cases above, and the registration and dispatch they need.
- `tools/loop-mutations.sh` — the mutations for the new invariants (the lock taken before the state is
  written, the liveness check, the report's dead-run wording) and the one whose pattern the reordering
  changed.
- `guidelines/Backlog.md` §7 "The loop" — the paragraph that states when the lock is taken and what a run
  finds when the lock's owner is gone.

**Two things this change carries that the defect did not ask for.** Both are named here because a change
that touches them owns them:

- The two mutation records `9.9` and `12.2` name a line the engine no longer has (the child's own
  completion record, issue #227) and reported `MUTATION-DID-NOT-APPLY` in the full set; their patterns are
  rewritten here, and the finding that the full set is in no gate is filed as issue #264.
- The feature list's classification of three capability ids introduced by **other** open changes
  (`search-name-matching`, `search-result-row-icon`, `start-map-selection`) was missing, so
  `gen-feature-list.sh --check-classification` — a suite every change under `openspec/` owes — was red for
  the whole tree. The three entries are added to `tools/feature-list/specs.json` (area, `userVisible` and the
  surfaces each change's own proposal states: `search`/phone+car, `search`/phone+car, `offline-maps`/phone).
  That is the classification the project reserves for a human; it was made on the owner's instruction, and
  the three changes keep their own wording.

## What the decision leaves out, and why this change does not touch it

The adopt path's own admissibility rule (a branch with one archived change of its own and a green
verification) is not changed: with the lock taken first, no live run's branch can reach it, and the case that
is left — a branch whose last review round never answered — is a different root cause and is filed as its own
item. The state file keeps its shape (one file, replaced by the next run): the archive in `runs/` already
preserves every replaced run, and a per-run state file would change the report's and the tooling's paths for
no gain this incident asks for.
