# A child's placed write is recorded where it is read, and a child has one place to write

## Why

`run_child` starts every child **in the project's own tree** — deliberately, so that the project's tool policy
resolves at the directory the child starts in — and names the tree it may change in its prompt instead. A child that
writes with a project-relative path therefore writes the tree the landing step needs clean, and the run can already
say which process did it: `outside_write_seen` (`tools/loop/run.sh:1147` before this change) reads a pid's own
working directory out of `/proc` once per second of the child's life and places the processes of that child's tree
that stand in the project and outside the worktree.

**Root cause, in one sentence:** that reading was consulted only in the outcome block — `outside_write_decides`
(`tools/loop/run.sh:1132`) was one arm of it, after the gate — so the item carried no step of its own for the
reading that took it (the six items below each paid a whole item **and** its gate for one), and a child had no place
beside its change to write, so "write beside the change" was the only path it had.

**The tree today.** The arm is the third of the outcome block's, `tools/loop/run.sh:2301` before this change:

```sh
  elif outside_write_decides; then
    outcome="blocked"; blocked_reason="the $last_child wrote outside the worktree — the project's own tree changed while it ran: …"
```

and the reading itself is recorded only in the branch that does **not** decide (`run.sh:2264`, condition
`[ "$worker_write_decides" -eq 0 ]`). The gate child happens to be skipped for such an item as well, but only as an
incidental conjunct of the pre-gate condition (`[ "$worker_write_decides" -eq 0 ]`, `run.sh:2025`), with no scenario,
no case and no sentence in `guidelines/Backlog.recipes.md`, section 7a, saying so: the behaviour was nobody's rule.

**Measured on this loop's archive, 2026-10-20 to 2026-10-23** (the tracker entry, #198, #264, #276, #274, #285,
#270): six items carry "the … worker wrote outside the worktree — the project's own tree changed while it ran" in
their runs' records, and the price of each was a whole item **and** its gate. #798 is the pre-flight for the other
half — a tree a person dirtied before the run started is refused before a run begins.

**The red test, on the base commit `54373f19`** (`tools/loop-selftest.sh`, output kept in the item's directory as
`red-on-HEAD-placed-write.log` and `red-on-HEAD-scratch.log`):

```
run#aWriterTheRunCanPlaceBlocksTheItem|   the reading that ended the item is recorded as a step of its own: expected '1', got '0'
run#aChildIsGivenOneScratchDirectory|   the worker's prompt names no scratch directory
```

Two cases, one per half of the delta; the second one's stub child reads the scratch directory out of its own prompt
and writes a file there, which is what the acceptance the tracker entry names does.

**Owning capability and requirement.** `openspec/specs/agent-issue-loop/spec.md`, requirement "Each item is worked
by a fresh process, reviewed by a different one" — its scenarios "A process that writes outside its tree is caught"
(`run#childWriteOutsideTheWorktreeIsNoted`) and "A difference is attributed while the writer is alive"
(`run#aWriterTheRunCanPlaceBlocksTheItem`) state the reading; the delta adds the two requirements that say when it
decides and that a child has a scratch directory. Guideline section: `guidelines/Backlog.recipes.md`, section 7a "Running
one item: the script, the child and the package", which owns the reading, the placement and the narrow rule's
trade-offs.

**Surface, counted before the plan** (`./tools/look.sh 'outside the worktree'`): 67 matches in 47 files; of those,
5 are in `tools/loop-cases.sh`, 2 in `openspec/specs/agent-issue-loop/spec.md`, 4 in `tools/loop/run.sh` and 1 in
`tools/loop-mutations.sh`.

surface: 1 case added, 1 case rewritten, 0 fixtures realigned — `./tools/look.sh 'outside the worktree'`

## What changes

1. The worker's own reading is recorded **where it is taken**: the step named `outside-write` is recorded beside
   it (`tools/loop/run.sh`, right after `outside_write_decides`), with the sentence that says the item ends there and
   no gate child follows; the arm that recorded it only when it did not decide goes away.
2. Every child of an item's change is handed one scratch directory — `<item dir>/scratch`, created before the prompt
   that names it is rendered and named beside the change root in `prompts/worker.md`, `worker-gate.md` and
   `worker-tail.md` — and each prompt says that a write with a relative path lands in the project's own tree.

## Measured, quoted from the block this change was planned against

```
LOOP-MEASURE-CHECK tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach
LOOP-MEASURE-CHECK tools/loop-selftest.sh --from-git 6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db
LOOP-MEASURE-CHECK bash tools/loop-mutations.sh --check
LOOP-MEASURE-SUITE cases=342 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
```

`tools/loop-selftest.sh --list | wc -l` answers `343` after the case this change adds, which is the block's `342`
plus one.

## What this change does not do

Whether a child's write the placement **cannot** tie to it — its own shell, standing in the project, which the
narrow rule deliberately leaves unplaced (`run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, #704) — should end an
item whose branch and gate are sound is a decision the tracker entry asks for and this change does not take: the
recorded decision is `none`, so the rule stands as it is.
