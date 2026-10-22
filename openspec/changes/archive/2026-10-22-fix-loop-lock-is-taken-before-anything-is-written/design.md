# Design — fix-loop-lock-is-taken-before-anything-is-written

## The shape of the defect

The run has three writers of consequence: the state file (`state.sh`), the tracker (the labels) and git (the
branches and the merges). One lock is meant to keep one run per repository — invariant I12 in
`guidelines/Backlog.md` §4: *"Exactly one writer per file, per tree, per tracker field, at a time | one lock per
run"*. The lock is the mechanism, and the mechanism is taken at `tools/loop/run.sh:432`, after every writer
has already written.

```
run.sh:146   loop_state init …            → the state file is replaced
run.sh:391   prescreen.sh | tee …         → the queue and the exclusions are written into it
run.sh:403   adopt_finished_exclusions    → a branch is merged into main, an item is closed
run.sh:432   loop_state lock --owner "$$" → the refusal, if any
```

## D1 — where the lock is taken

**Chosen: the lock is taken before the run writes anything** — after the identity preflight (which writes
nothing) and before `state.sh init`.

The order becomes:

```
preflight (no writes) → lock → state init → kept-worktree check → decision cap → pre-screen → items
```

and the traps that release the lock are installed with it, so every later exit — a refusal, a signal, the end
of the item loop — releases it.

**Alternative B — keep the lock where it is and guard each write.** Eliminated by the evidence: the
pre-screen's adoption path *merges into `main`* and closes a tracker item. A guard before each write cannot
close the window between the guard and the write, and the number of write sites is not bounded by this
change. The incident's own timeline is the counter-example: the refused run wrote the state, wrote the queue
and merged the branch, and only then met the lock.

**Alternative C — one state file per run, with a pointer to the current one.** Eliminated: it does not fix
the merge (the adopt path would still merge a live run's branch), it changes the state path that the report,
the tooling and the self-test read in many places, and the archive in `runs/` already preserves every state a
run replaces. The incident needs the *order* fixed, not the storage.

## D2 — a lock whose owner is gone

**Chosen: the lock's owner is a process, and the lock is taken over when that process is not running.** The
takeover is announced and names the pid it took over from.

The check is `kill -0 <pid>`: a process that cannot be signalled does not exist. The owner is written into
`lock/owner` by `cmd_lock` and is the run's own pid (`$$`), which is the pid the report and the takeover
compare against.

**Alternative B — never take over; a person removes the lock.** Eliminated by the incident: the killed run
left exactly that tombstone, and every later run was refused by it after replacing the state and paying for a
pre-screen. A rule that needs a person is not a rule an unattended run can live under. The takeover is
announced, so the person still sees it in the run's output.

**Alternative C — a lease with a heartbeat and a timeout.** Eliminated: the heartbeat is a second writer of
the state — the very thing I12 forbids — and the timeout would have to outlast the longest child (45 min by
`LOOP_CHILD_MAX_SECONDS`), which is a clock the run does not consult while it waits.

## D3 — how the report tells a killed run from a running one

**Chosen: the state records the pid that wrote it, and the report asks whether that process is alive.** The
state gains one field, `.run.ownerPid`, written by `state.sh init` from the lock's owner.

- The report's budget line prints no budget left for a state whose owner is gone and which recorded no stop:
  the elapsed time since its start is not its spend, because the run is not spending anything any more. The
  wording is the one the spec already uses for the same mistake (`budget left unknown: these numbers belong to
  another run — …`).
- The stop line names the process that is not running instead of saying "the run is still going, or it ended
  without recording a reason" — the two are different facts and the report conflated them.

**Alternative B — the run's own trap records the stop.** Eliminated: `SIGKILL` cannot be trapped, and the
incident's run was killed that way (no `run.stop` in its kept state, the lock left behind).

**Alternative C — the report reads the lock's owner.** Eliminated: the lock is released by a run that ends
normally, so a finished run's state would have no owner to read. The owner belongs to the state, not to the
lock.

**Alternative D — a "last seen" timestamp in the state.** Eliminated: a second writer, written while a child
runs — the exact thing the case `labels#writesOnlyBetweenChildren` forbids.

## The kept-worktree check

The check that refuses a dirty worktree a previous run left behind carried the condition
`[ ! -e "$run_lock" ]` — "no run holds the lock, so this tree is not a live run's". With the lock taken
first, a live run is refused *at the lock*, so that condition is no longer the signal it was: the run that
reaches the check holds the lock, and the tree is leftovers. The condition goes; the refusal, the naming of
the item the tree's marker names, the escalation and the `--reset-worktree` repair stay as they are.

## Verification

- The loop's own suite is the unit test: `tools/loop-selftest.sh` (offline, no token, no model, no device).
  Three cases are added, each red on HEAD before the fix.
- The mutation harness makes the new invariants falsifiable: one mutation per invariant
  (`tools/loop-mutations.sh`, labels `41.x`).
- The project's gate for `tools/loop/**` is `./tools/loop/gate.sh`; its owed suites for this pathspec are the
  loop self-test, the mutation harness's self-test and the documentation-routes check.
- No device, no build and no Gradle suite is involved: the change touches no compiled file. The structural
  check that stands in for the build is the gate above plus `openspec validate --strict`.

## Risk

| risk | why it is bounded |
|---|---|
| a run that is refused by a live lock now leaves *no* state at all, so the refused run's own numbers are lost | the refusal is on the console and in the exit status; a refused run never owned a state, and the live run's state is what the report reads |
| the takeover of a dead owner's lock lets two runs work at once, if the owner is alive but its pid was reused | the pid is checked with `kill -0`, so a reused pid reads as alive and the run is refused — the failure mode is a refusal, not a collision |
| the report's new wording fires for a finished run | the wording applies only to a state that recorded **no** stop; a run that ended normally records one |
| a run killed between the lock and the trap leaves the lock behind again | the lock's owner is then gone, and the next run takes it over — the tombstone the incident produced cannot happen twice |
