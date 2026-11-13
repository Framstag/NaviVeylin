# Design — an item a merge refused names its landing

## Root cause

`report.sh` builds its action list from seven readers, and the item the run stopped on `stopReason: unmergeable` is
read by none of them: it reaches the list only through the decision arm (`report.sh:307` on base `647b289a`, which
prints `decide #<n>` and the item's note), and the branch arm beside it (`report.sh:679`) prints a branch with what it
carries and no command. The refusal's own text and the branch it names are in the item's note; what the branch carries
is in the branch table the same report already builds; and the paths are in the refusal the run carried into the note.
Nothing was missing but the reader.

## The single fix

One block in `report.sh`, beside the branch arm and before it (`report.sh:674`–`:716` as this change leaves it): it
walks the items whose `stopReason` is `unmergeable`, takes the branch the refusal names (falling back to the branch
git still holds for the item when a state's note does not carry the sentence), takes what it carries from the branch
table by patch identity — `the branch is not in this tree` when the table has no row, `nothing — the base already
carries its patches` at zero — and prints one action:

```
land <branch> onto the base — <n> commit(s) the base does not have — item #<issue> — git -C <root> merge --ff-only <branch>
```

with, when the refusal listed the dirt it refused over, one clause more:

```
 — after the <k> path(s) its refusal named are cleared (<p1>, <p2>, …) — git -C <root> status --porcelain lists them
```

The branch arm below skips a branch this block named, so the landing is one line (#850) — the item's `decide` line
remains where the run escalated it, because that line is the question the run wrote and not a second landing — and
the `none` sentence names the eighth source. Everything printed is read from the state or from git; no state field, no engine line, no new
command (`openspec/specs/agent-issue-loop/spec.md`, the action-list scenario).

## Blast radius

`tools/loop/report.sh` (one block, one guard in the branch loop, one sentence), `tools/loop/tests/loop-cases.sh` (one
case added — `report#anUnmergeableItemNamesItsLanding` — and its name in the declared list and the dispatch table),
`tools/loop/tests/loop-mutations.sh` (one record, `863.1`), `guidelines/Backlog.recipes.md` section 7e (the arm, and
`seven` → `eight`), and this change's own spec delta. The delta compiles nothing, so no Gradle build is owed — the
structural checks of the files it edits stand in for one (`evidence/structural-checks.log`), beside the loop's own gate,
which the gate child runs over the finished tree. Nothing
in `run.sh`, `state.sh` or `worktree.sh` moves, so no run's behaviour changes — only what its report says.

## Rollback

Revert the four hunks: the block and the branch loop's guard in `report.sh`, the case, the mutation record, and the
section 7e paragraph. The `none` sentence's eighth clause goes with the block. A state written before this change is read
exactly as it was, because the block reads fields those states already carry.

## Alternatives the evidence eliminated

- **A state field beside `stopReason`** (`merged.refusedBranch`, `merged.refusedPaths`): the note already carries both,
  and `state.sh done` would then own a second copy of a sentence the report must keep in step. The item's own
  direction is "no state field, no engine contract", and the record of 2026-11-11T20:36:10Z shows the note is enough.
- **Extending the branch arm's line in place** instead of a block beside it: the branch arm reads branches, and the
  state's own record of a refused landing is the item `report.sh:307` already reads. An arm that reads `.items[]` and
  one that reads git would have to agree, in one line, about a branch the table may not hold — the second item of the
  red case is exactly that shape (`the branch is not in this tree`), and the branch arm cannot print it at all,
  because a branch git does not have is not in its table.
- **Printing the paths as two lines** (one per path): every action is one line, and the landing is one action. The
  refusal's own lines are joined, exactly as `git status --porcelain` prints them, so a caller can copy one out.
- **Printing `0 commit(s)` for a branch the tree no longer holds**: a zero reads as a measurement that was made
  (`report.sh`'s own rule for the spend columns, `report#zeroUsagePrintsDashNotZero`). The line says the branch is not
  in this tree instead.

## Open decisions

none

The item's own direction settles the shape — one block in `report.sh` beside the branch and commit lines, one case in
`tools/loop/tests/loop-cases.sh`, no state field and no engine contract — and the alternatives above are ones the
evidence eliminated rather than a fork a person has to choose. What the change leaves open is not a decision but a
measurement: the arm has not been read against a fresh run whose landing was refused, because no such run exists on
this base (the one this change cites is 2026-11-11T20:36:10Z, read through its archived state).
