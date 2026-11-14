# Design — a landing merges over a difference it cannot place

## Root cause

`cmd_adopt_item` and `cmd_merge_item` each read `git status --porcelain` and refused on any line of it
(`tools/loop/worktree.sh:657` and `:697` on base `0848c35d`), with the reason "over uncommitted work the merge would
attribute that work to itself". That reason is right about the merge and wrong about the tree: `run.sh` already knows
who wrote a difference there. `outside_write_decides()` (`run.sh:1377`, issues #286 and #274) is the run's own rule —
a difference it can place in a process of its own tree decides the item, one it cannot place is recorded and decides
nothing — and the landing read the same tree without it. One tree, two rules; the milder one was applied to the worker
and the stricter one to the landing, which is where an item's whole price has already been paid.

## The same attribution clause, reused and not re-derived

The fix reads the clause the run has already computed rather than judging the tree a second time in `worktree.sh`.
Three reasons, each of them the evidence's:

- **A second judgement is what caused the defect.** If the landing derived its own answer from `git status`, it would
  need the process table the reading takes at a heartbeat (issue #704: the writer is gone by the time a transcript is
  written), the mark of the run's own child chain (issue #796's reading), and the difference's own first reading — and
  it would disagree with `run.sh` about them exactly as it does now. The reading is a seam the run already carries:
  `worker_write_note`/`gate_write_note`/`outside_write_note` and their `…_decides` flags, which the worker, gate,
  tail and review steps already record per item.
- **One home per concept.** `--outside-write-decides yes|no` is the clause `run.sh` computes, spelled once; the note is
  the line the phases already wrote, spelled once. `worktree.sh` gains no new vocabulary and no state field — the
  record of what the landing merged over is the `outside-write-landing` step (`run.sh:507`), the same shape as the
  `outside-write`, `outside-write-gate`, `outside-write-tail` and `outside-write-review-<round>` steps beside it.
- **The absence of a reading still refuses.** A call that passes neither option leaves `landing_decides` empty, and
  `landing_refuses_the_dirt` keeps the existing refusal (exit 5) with the existing sentence. So a landing that carries
  no reading behaves exactly as it did before this change, which is the conservative default: a difference the run
  never read is not one it can place. `--strict-outside-write yes` reaches the same arm from above — the run that
  declared the project tree exclusive made every difference a verdict, and the landing's is that verdict.

## The risk bound

Merging over a difference nothing placed is the risk this change takes, and it is bounded three ways, all of them
abort rather than swallow:

1. **`git merge --ff-only`** — the run's own landing is a fast-forward or nothing (`worktree.sh:754`; the branch is cut
   from the run's base and only grows). Git aborts it with a non-zero status over a staged change it would have to
   discard, and the call is `|| loop_die`, so the run stops instead of landing.
2. **`git cherry-pick`** — the adoption's replay (`:716`) aborts and then `git cherry-pick --abort` restores the tree,
   `loop_die` names the branch; a dirty tree that conflicts with a replay is not left half-applied by this change,
   because nothing in it writes to the index or the working tree.
3. **The diff carries no write at all but the refusal predicate.** `git diff` between the pre-correction commit and
   this item's head shows no executable line in `worktree.sh` beyond the two call sites and the two helpers; the merge
   commands, their `||` arms and the ancestry guard are untouched. The difference is still on the tree, uncommitted,
   after the merge — the case asserts exactly that (`?? demo-owned.txt` in
   `run#mergesOverADifferenceItCannotPlace`) — so a person's edit is not committed by the landing, only landed beside.

## Blast radius

`tools/loop/worktree.sh` (`landing_args`, `landing_refuses_the_dirt`, two call sites, two usage lines),
`tools/loop/run.sh` (two `land_args` builders, the `outside-write-landing` record),
`tools/loop/tests/loop-cases.sh` (two cases added, one rewritten in place, two folded away, their declarations and
dispatch entries), `tools/loop/tests/loop-mutations.sh` (records `58.2` and `86.1` re-pointed), the capability spec's
two MODIFIED requirements, `guidelines/Backlog.recipes.md` sections 7a and 7d, and one example in
`.pi/skills/create-issue/SKILL.md` that cited the case this change folds away. No compiled file changes, so no Gradle
build is owed; the structural checks of the files it edits stand in for one (`evidence/structural-checks.log`), beside
the loop's own gate, which the gate child runs over the finished tree (`evidence/gate-run.log`).

## Rollback

Revert the six hunks: the two helpers and their two call sites in `worktree.sh`, the two option builders and the
`outside-write-landing` record in `run.sh`, the cases and the two mutation records, the two spec requirements and the
two guideline paragraphs. A run that reverts to the old behaviour needs nothing else: a merge call that carries no
reading already refuses, so the reverted `run.sh` and the un-reverted `worktree.sh` cannot land anything the old code
would not have landed.

## Alternatives the evidence eliminated

- **A state field beside `stopReason`** carrying the landing's dirt: the readings are already steps of the item, and a
  second copy is a sentence two writers must keep in step. The run's own rule already lives in a function
  (`outside_write_decides`), which is what was reused.
- **Teaching `worktree.sh` to read the process table itself**: the reading is taken while a child is alive (#704) and
  the landing happens after every child is gone; a landing that took its own reading would place nothing at all and
  merge over every difference — the failure `run#aHostWithoutThePlacementReadingIsRefused` exists to prevent.
- **Keeping the refusal and printing the command that lands it** (the earlier sibling item's answer, #863, for a dirty
  refused landing): that is what makes a refused landing *readable*, and it leaves the item dropped. This change is
  the rule that stops the drop happening for a difference the run can place nowhere; the report keeps naming a landing
  when one is refused for a difference the run did place.

## Open decisions

none

The item's own direction settles the shape — reuse the recorded attribution, record and merge over a difference the
run placed nowhere, keep the refusal for one it placed — and the alternatives above are ones the evidence eliminated
rather than a fork a person has to choose. What this change leaves open is a measurement, not a decision: the arm has
not been read against a fresh run whose landing met a person's edit, because no such run exists on this base; the run
of 2026-11-11T20:36:10Z is read through its archived state, and the case is the reading that stands in for the run.