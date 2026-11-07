# Design — the reading moves to the commit, the arm keeps its place

## Open decisions

none

## Root cause

The run's accounting of what became of an item is a single chain of arms in the outcome block
(`tools/loop/run.sh:2332` onwards), and it is read after every child the item starts — the reviewer, the gate child and
the tail. Every fact it needs is not available earlier; one of them is: whether the tree carries a commit. That one was
therefore read as late as the rest, and an item whose child wrote nothing paid for a gate phase (the item's own
measurement: 20–22 min of a 40–50 min item, `runs/2026-11-06T11:44:21Z.json`) before the run looked at it.

In the shape the run works in by default (`LOOP_PRE_GATE_REVIEW_DEFAULT=on`), the run itself commits the change child's
tree at `run.sh:2065` and prints the HEAD it found — so the reading costs nothing there and had simply never been taken.

## The single fix

Four lines in `tools/loop/run.sh`, all inside the deferred-gate block:

1. `empty_tree_reading=""` beside the block's other declarations (`:2055`), so the two guards below read a variable
   that has a value in every shape.
2. After the commit and the HEAD it printed, `if [ "$head_now" = "$base" ]` records the item's own step (`empty-tree`)
   and the reading (`:2075`–`:2078`). Nothing else is decided here.
3. The tail is guarded on it (`:2188`): a tree the run has just found at the base has nothing to archive, so the child
   that writes the archive is not started.
4. The never-started arm is guarded on it (`:2271`): a child that answered the diff marker read the item, so its empty
   tree is the item's block — written by the arm that already owns that reason — and not the run's own stop.

The reviewer and the gate child are already guarded on the same HEAD (`:2080`, `:2136`), so they are not touched: the
change adds the one reading they were already gated on and the two guards the reading owns.

## Blast radius

- `tools/loop/run.sh` — the four sites above, in one file; no function signature, no prompt, no state schema changes.
- `tools/loop-cases.sh` — one case (list, dispatch, body).
- `tools/loop-mutations.sh` — one record (91.1).
- `openspec/specs/agent-issue-loop/spec.md` — one requirement modified: one added scenario, and the three scenarios it
  already carries given the case each of them has.
- `guidelines/Backlog.recipes.md`, the section "Refusals, records and the landing" — one paragraph's second half.

The item's state gains one step name (`empty-tree`); nothing reads a step by name except the cases, and a step name is
free-form (`tools/loop/state.sh:41`).

## Rollback

Revert the four `run.sh` sites: the arm at `:2361` still blocks the item, the tail still runs, and the shape is exactly
what HEAD does today. The case goes red with it (`tools/loop-mutations.sh --only 91.1`), which is how the two are kept
together.

## Alternatives the evidence eliminated

- **Move the arm itself.** It would write the reason at a second site, and the arm is the invariant every other shape
  is still decided by (a skipped verdict, a falsified premise, a bound-ended child, a placed write all reach it). The
  mutation record 9.5 anchors on that arm, and a reason written twice is a reason two records can disagree about.
  Eliminated.
- **Refuse the empty tree and let the tail run anyway.** The tail is a child with no archive to write: measured in the
  red run, its report carries no verdict and the run then reads its own "could not start" stop
  (`run.sh:2280`) — the item is not even recorded. Eliminated by the red run itself.
- **Take the reading before the commit.** `end-item` is the run's statement about the child's work (`run.sh:2231`); a
  tree the run has not committed is not a claim about the base, so reading HEAD first would refuse every uncommitted
  change. Eliminated by what a commit is in this run.
- **Do the same in the one-child shape.** There the change child runs the gate inside its own session
  (`prompts/gate-own.md`); there is no point before the gate the run controls, and committing that child's tree earlier
  than the outcome block would assert work the run has not read (issue #271). Eliminated as out of reach, and said so in
  the proposal.
- **Read the worktree's dirty state instead of HEAD.** An empty `git status` is not a change; the run's own commit is
  what the base either carries or does not. Eliminated.
