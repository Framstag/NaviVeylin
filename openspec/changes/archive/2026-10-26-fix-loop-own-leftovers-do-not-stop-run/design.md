# Design — fix-loop-own-leftovers-do-not-stop-run

## Root cause

One mechanism, reproduced on this tree (`evidence/red-on-head.log`): **the mid-run start refusal has no way to tell
dirt this run ended and recorded from dirt that arrived some other way, so it stops the run on both.**

- `worktree.sh start-item` refuses any tree `git status --porcelain` finds dirty (`tools/loop/worktree.sh:156` on the
  base) — the "one writer per tree" rule, which is right.
- `run.sh`'s `*"not clean"*` arm then attributes the refusal by the branch marker and ends the run, on the argument
  that the tree is still dirty and nothing is discarded without `--reset-worktree` (`tools/loop/run.sh:1225`,
  `:1251` on the base). The argument holds for a *previous* run's leftovers.
- On the path the item measures, the dirt is this run's own: `run_child` ends a child at its cap, `keep_leftovers`
  writes that child's tracked diff, untracked files and the project's unarchived changes to
  `<item dir>/leftover-from-killed-run.patch` (`tools/loop/run.sh:1089` on the base), the item is recorded `blocked`
  — and the next start meets the same tree and stops the run, naming the patch it wrote itself.

The two facts the arm needs are both in the process: *which* items this run ended, and *whether* their record was
written. Neither is in the tree, and the tree is all the arm reads today.

## The fix

One change, in two halves that only make sense together:

1. **The run remembers what it recorded.** `run_child` appends the item to `own_leftovers` on the one path where
   `keep_leftovers` produced a patch (`tools/loop/run.sh:1114`). The patch's path is derived from the item and the
   stage directory, never stored — `own_leftovers_of <item>` (`:500`) answers "this run ended that item and the patch
   is there", so a patch a person deleted between the two steps reads as *no record* and the old stop applies.
2. **The refusal discards that tree and retries once.** In the `*"not clean"*` arm, before the attribution, the run
   discards the tree with the same verbs the `--reset-worktree` repair uses and calls `start-item` again
   (`:1262`–`:1270`). A retry that is refused again falls straight through into the existing attribution, patch,
   escalation and stop — unchanged — so a second refusal is reported exactly as it is today.

The verbs are one function, `discard_kept_worktree` (`:509`), extracted from the start-time path with its behaviour
unchanged (the same three lines, the same residual check, the same "is clean again" line, the same
`loop_refuse` when the residual is non-empty). A second copy of them would have been a second rule, which is what
`owner_of_marker`'s comment (`:489`–`491`) exists to prevent.

## Blast radius

| where | what |
|---|---|
| `tools/loop/run.sh:499`–`513` | `own_leftovers`, `own_leftovers_of`, `discard_kept_worktree` |
| `tools/loop/run.sh:544` | the start-time repair calls the shared function |
| `tools/loop/run.sh:1110`–`1114` | the item is recorded as this run's own when its patch is written |
| `tools/loop/run.sh:1262`–`1295` | the retry, and the unchanged attribution under it |
| `tools/loop-cases.sh` | one case, its declared set entry, its dispatch entry (237 cases against 236) |
| `tools/loop-mutations.sh` | `13.10` added; `13.5`, `13.8`, `13.9`, `53.1`, `53.3`, `62.6` re-pointed at re-indented lines |
| `guidelines/Backlog.md`, "The loop" | the stop's argument gains the exception |
| `openspec/specs/agent-issue-loop/spec.md` | one added requirement, one modified |

No compiled file, no dependency, no configuration key, no new log line that carries a coordinate: the discard line
names an item and a path inside the run's own state directory.

**What can be lost by the discard.** `reset --hard` and `clean -fd` (never `-x`) drop tracked modifications and
untracked files; the patch holds the tracked diff and every untracked file up to 262 144 bytes, naming the larger ones
instead of copying them. That residual is smaller than the sanctioned repair's: `--reset-worktree` discards the same
tree with no patch at all, which is the rule issue #173 keeps and this change does not touch.

## Risk

- *A tree could be discarded that a person wanted.* It is discarded only when this run ended the child that produced
  it, the item is recorded `blocked`, and the patch exists — and the alternative, today, is a run that spends 57 % of
  its budget and hands back work that is green and archived (issue #664's measurement).
- *A retry could loop.* It cannot: the retry is one call, not a loop, and a second refusal falls into the existing
  stop.
- *The reset could fail partially.* `discard_kept_worktree` returns non-zero and prints what is left; the arm then
  treats it as it treats any dirty refusal — attribution, patch, escalation, stop — so a tree it cannot clean is
  still reported, never announced as clean.

## Alternatives the evidence eliminated

- **Reset at the cap, immediately after `keep_leftovers`.** Eliminated: it also discards the tree of the run whose
  item budget or deadline ends with that item, and such a run starts nothing else — three shipped scenarios and their
  cases (`run#treeOfAChildTheCapEndedIsNotCommitted`, `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`,
  `run#treeOfACapEndedChildIsKeptAsAPatch`) assert that the tree still carries what the child left. Restricting it to
  "a run that has another item" is not readable at that point: the queue, the item budget and the deadline are
  `state.sh next`'s answer, computed after the item is recorded.
- **Keep the stop and re-enter the loop after the reset the stop reason names.** Eliminated: `.run.stop.reason` is
  the run's end for every reader — `report.sh`, `state.sh show`, the next run's pre-screen — so a run that records it
  and continues writes a false record; re-entering before it is recorded is this change's retry, one layer up, with a
  second copy of the loop's bookkeeping.
- **Have the stop reason carry the budget it spends.** Eliminated: it moves no number the item measures. The cost is
  234 of 400 minutes, one queued item and a hand landing of already-green work; naming the cost leaves the second
  item unworked, so the red case (*the next item starts*) stays red.

## Verification

- Red on the base, green on the change: `run#theStartAfterTheRunsOwnEndedChildIsNotRefused` —
  `evidence/red-on-head.log` / `evidence/red-on-head.xml` (`tests="1" failures="1"`), then
  `evidence/green-after-fix.log`.
- The refusal it must not weaken: `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds`,
  `run#aRefusedStartKeepsTheLeftoversItFound`, and the `run#` cases the delta's paths declare —
  `evidence/focused-run-cases.log` (`all 90 cases passed`).
- The invariant, one mutation per check: `13.10` makes the retry never happen and the new case goes red; `13.11` makes
  the retry's own refusal read as dirt and `run#aRetriedStartStopsWithItsOwnReason` goes red; the re-pointed records
  keep their cases red under their own mutation — `evidence/focused-mutations.log` (the round-1 set, `red-when-mutated:
  7`) and `evidence/focused-mutations-round2.log` (`red-when-mutated: 9 · not-noticed: 0 · restore-failures: 0`).
- The project's gate, once: `./tools/loop/gate.sh`.

## Round 2 — the two defects the first review round found

The review of round 1 refused this change on two paths the first round did not measure. Both are in the retry, and
both are recorded here because the shape of the fix changed with them.

1. **A retried start refused for another reason was recorded as the dirt.** `start-item` answers a base moved, a
   branch carrying a previous attempt, or a dirty tree; the retry's answer was printed and then dropped, so a start
   refused for its branch was reported as `the worktree is not clean — the leftovers are #N's …` with an empty list of
   files, on a tree the run had just cleaned itself. The arm now reads the retry's own output first: dirt keeps the
   attribution, everything else stops the run with that refusal's first line (`run.sh:1286`–`1291`). The reason the
   two are read in that order is the same one that made the attribution necessary: the dirt path still has to record
   the item and its patch, and only the dirt path may.
2. **The attribution's `keep_leftovers` replaced the record the discard relied on.** `keep_leftovers` writes its patch
   with `> "$patch"`, so the path reached when the discard could not finish (a residual inside the submodule, the
   dirt `run#resetWorktreeNamesTheDirtItCannotDiscard` measures) would have replaced a record of real work with a
   header and that residual — and that record is the only reason the dirt was discardable. An item whose work this
   run already wrote is not written again (`run.sh:1299`).

One expression, `leftover_patch_of` (`run.sh:502`), now names the patch for all three readers — `own_leftovers_of`,
`keep_leftovers` and the line that prints where the record is — because three derivations of one path is how a
reviewer's finding 4 ("the path literal triplicated") happened. The repair's own prefix is a parameter
(`discard_kept_worktree <label> <leftovers>`), so a mid-run discard prints `discard:` and not `reset-worktree:` on a
run nobody started with that flag.

New case: `run#aRetriedStartStopsWithItsOwnReason` — the first child leaves dirt *and* a branch of the second item
carrying an archived change of its own, then outlasts the cap; the second item's start is refused for the dirt, the
discard clears it, and the retry is refused for the branch. Asserted: the stop reason is the retry's, it names
neither `the worktree is not clean` nor another item's leftovers, the patch still carries the work the discard relied
on, and no second worker ran. Mutation `13.11` makes the dispatch read a constant that always looks like dirt and the
case goes red (`evidence/focused-mutations-round2.log`, `RED 13.11 run#aRetriedStartStopsWithItsOwnReason`).

Left open, and argued rather than fixed: the *residual* path of finding 2 has no fixture. Reaching it needs a tree
whose discard half-succeeds, which means a worktree whose submodule is initialised and carries a file of its own —
the shipped `run#resetWorktreeNamesTheDirtItCannotDiscard` seeds exactly that for the `--reset-worktree` path, and
reproducing it mid-run would need two runs and a module prepared between them. The guard is one conditional on a path
whose residual the shipped case already measures. Measured in round 2 rather than assumed: removing the guard
(`own_leftovers_of "$owner_item" || keep_leftovers "$owner_item"` → `keep_leftovers "$owner_item"`) leaves
`run#aRetriedStartStopsWithItsOwnReason` **green**, because the retry's own refusal stops the run before the
attribution is reached — so a `13.12` record would report `not-noticed`, and none is added.

Two further notes of that round, neither taken and both recorded rather than silently dropped: the naming line
(`discarding the work #N left …`) prints *after* `discard_kept_worktree` has already printed the paths it removes,
although the function's comment says the caller names the work first; and a retry refused with *carries commits the
base does not* stops the run where the first start's own arm would adopt the finished attempt and carry on. Both are
behaviour of a path that only opens after a discard, both are cosmetic relative to this change's two findings, and
both are cheaper as one change of their own.
