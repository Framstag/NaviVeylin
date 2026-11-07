# Design — the adoption's refusal is a result the run keeps

## Root cause

`cmd_adopt_item` (`tools/loop/worktree.sh:478`) answers five for two different kinds of refusal and prints the
one it took: a branch that is not admissible (no archived change of its own, no green verification, a reviewer's
refusal, an unanswered round) and a branch that is admissible but cannot be merged *here* (the tree is not clean,
the tree is on another branch, the base moved, the replay conflicts). `adopt_finished_attempt`
(`tools/loop/run.sh:302`) folds the status into `5` for the caller, and `adopt_finished_exclusions`
(`tools/loop/run.sh:397`) discards it with `|| true`. What survives the refusal is the pre-screen's own record:
verdict `needs-decision` with the branch as evidence, which `escalate_prescreen_decisions`
(`tools/loop/run.sh:267`) turns into `agent/needs-decision` + `status/on-hold` under the item. So the run's one
reading of the branch is the pre-screen's guess about it, and the refusal that says *why* nothing was merged is
printed to a terminal nobody keeps.

## The single fix

The status the loop discards is already the answer, and the branch's admissibility is already a word:

1. `adopt_finished_attempt` keeps `adopt-item`'s output in `adoption_refusal` on the way out, the way
   `merge_committed_branch` already keeps `merge_refusal` (`run.sh:351`).
2. `adopt_finished_exclusions`, on a non-zero status, asks `worktree.sh branch-state` (`worktree.sh:458`) what the
   branch is. `adoptable` means the refusal was about the merge's inputs, and there the item is work: the run
   records the refusal with a new `state.sh adoption-refused` writer, which writes `mechanism:
   "unmergeable"` and `adoptionRefusal: <the printed text>` onto that item's entry in `.excluded` and touches
   nothing else in it. Any other state is the owner's, and the run does nothing — the existing escalation stands.
3. The two predicates that read an exclusion as a question the owner owes skip an entry that carries the
   mechanism: `escalate_prescreen_decisions` (`run.sh:272`) and `report.sh`'s decision list (`:181`, `:325`). The
   excluded line of both reports prints the mechanism, so the reader of the run's own output sees it. A pre-screen
   that runs again mid-run rewrites `.excluded` from the tracker and git, so `state.sh queue` carries the
   mechanism and the refusal over for the same issue — a branch excluded twice is the same branch, and the run
   does not escalate on its second reading what it recorded as work on its first.

No `.items` record is written, which is the whole point of choosing the exclusion over a block: the item was
never worked, so the run has no block to record, nothing enters the two-blocked streak, no decision slot is spent,
and the branch — the evidence — is left where the next pre-screen finds it.

## Why the exclusion and not a block

The item's text offers both shapes ("recorded as the item's mechanism `unmergeable` (or as a block whose reason is
the refusal, and no decision label)"). A block is the wrong one, eliminated by the tree's own rules:

- `state.sh` refuses a mechanism on an item record that is not a block (`state.sh done --stop-reason`), which is
  why the exclusion's mechanism is written by a command of its own instead of through `done`; the shipped
  vocabulary requirement now names that exclusion as the mechanism's one second home, so the word has one place
  beside the block and no free field. A block is also what `state.sh next` reads the two-blocked streak from
  (`state.sh:294`). An adoption the tree refused says nothing about the *next* item, and the item's own text
  asks for exactly that: "the two-block streak counts it only if the item's own worker finishes" — no worker ran.
- The run's own merge refusal is a different fact and keeps its block (`run.sh:2609`, case
  `run#doesNotCloseOnAMergeTheBaseCannotTake`): there the item *was* worked and the change exists. Recording the
  pre-screen's refusal as a block would make one word describe both a landed change that could not be merged and
  a branch nobody touched.

## Why all admissible refusals, and not only the dirty tree

The red case drives the dirty project tree because it is the cheapest refusal to produce, not a class of its own:
"the base moved" is the same kind — the branch is fine, the tree cannot take it now — and the same next run
resolves it (a clean tree, a base that has not moved). The replay's conflict is not that state: nothing in this
change rebases or reworks the branch, so a conflicting branch is the same work retried, refused and recorded again
by the next run and unresolved by it. Splitting
them would need `adopt-item` to publish a sixth word for its refusals, a second classification beside
`branch-state`'s, and the loop would then own two vocabularies for one question. `branch-state` already answers
the only distinction the run needs: is anything *on the branch* the owner's, or not.

## Blast radius

- `tools/loop/run.sh`: `adoption_refusal` on `adopt_finished_attempt`; the exclusion loop's status read, its
  `branch-state` question and its `state.sh` call; one `select` in `escalate_prescreen_decisions`.
- `tools/loop/state.sh`: a new `adoption-refused` writer (refusing an item that already has its `.items` record,
  and an issue that is not one of this run's exclusions), the excluded line of `report` printing the mechanism,
  and `queue` carrying it over when a second pre-screen rewrites the exclusions.
- `tools/loop/report.sh`: the same `select` in the decision count and in the decision list, and the mechanism on
  the "excluded before any work" line.
- `openspec/specs/agent-issue-loop/spec.md`: one requirement added, and two shipped requirements modified —
  "Version control belongs to the run's script, not to the agent" with its merge-and-close scenario, and "A block
  records the mechanism that stopped it" — because the shipped text is unconditional for a finished branch the
  tree refuses, and because the mechanism now has a home on the exclusion.
- `guidelines/Backlog.recipes.md`:586 (and `:725`'s excluded line) state the new record.
- `tools/loop-mutations.sh`: one record (86.1) for the new write, so the invariant has the case that fails when
  the record is dropped.

Not touched: `worktree.sh`'s refusals and `branch-state`'s vocabulary, the start-time adoption path
(`run.sh:1807`, which has an item record and records its refusal as a block), the decision cap, the hold, the
closure path and the sweep.

## Rollback

One `git revert` of the change: the state field is additive (a state written with it is read by a tree without it
as an exclusion with an extra key, and `jq` ignores it), the new `state.sh` command is only called from the
exclusion loop, and no tracker write is added — so reverting leaves the branch, the item and the tracker exactly
where the base had them.

## Alternatives eliminated

- **A decision label write removed by a second one** (escalate, then un-escalate): two tracker transitions for one
  fact, and #279's measured failure — a run that cannot remove the mark it took — is the shape this would add.
- **`adopt-item` returning a distinct status per refusal kind**: the exit status is the caller's answer and
  `worktree.sh`'s commands already answer `5` for every refusal; a second vocabulary would have to be kept in step
  with `branch-state`, which answers the same question for the pre-screen and the start.
- **Recording the refusal only in the reason string** (no mechanism): the reason is prose, and the report, the
  escalation predicate and the mechanism totals would each have to parse it.
