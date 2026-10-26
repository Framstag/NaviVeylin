# Proposal — fix-loop-own-leftovers-do-not-stop-run

## Why

One defect, verified on this tree, issue #664: **the run stops on the dirt its own cap-ended child left, after it has
already written that work down — so one item's cap ends the whole run.**

**Root cause (one).** The mid-run start refusal cannot tell the dirt of *another* item from the dirt of the item the
run itself just ended: `worktree.sh start-item` refuses any tree `git status --porcelain` finds dirty
(`tools/loop/worktree.sh:156` on the base), and `run.sh`'s arm for that refusal reads the item out of the branch
marker and ends the run on it, arguing that the tree is still dirty and nothing is discarded without
`--reset-worktree` (`tools/loop/run.sh:1225` and `:1251` on the base). That argument is right for a previous run's
leftovers. It is wrong on the path this item measures: `run_child` ends a child at its cap, `keep_leftovers` writes
what that child left — tracked diff, each untracked file, the project's unarchived changes — to
`<item dir>/leftover-from-killed-run.patch` (`tools/loop/run.sh:1089` on the base), the item is recorded `blocked`, and
the *next* item's start then meets that same dirt and stops the run, although the record the refusal protects was
written one step earlier by the same process.

**Verified, not quoted from the tracker.** Reproduced in this tree 2026-10-26 by the case this change adds
(`evidence/red-on-head.log`, tally `evidence/red-on-head.xml`, `tests="1" failures="1"`, 09:13:35Z): two eligible items,
the first child overrunning a 2 s cap, the second child ready to work. The run writes the first item's patch and then

```
stop: the item could not be started: the worktree is not clean — the leftovers are #21's (branch fix/issue-21-a-bug),
so #22 and no item after it can start until they are dealt with: ?? half-applied.txt (the work is kept in
…/items/21/leftover-from-killed-run.patch) (repair: --reset-worktree discards them, and names them before it does)
```

— the patch is named in the same line that stops the run, and the second item is never worked.

**The red test.** `run#theStartAfterTheRunsOwnEndedChildIsNotRefused` — `evidence/red-on-head.log`, tally
`evidence/red-on-head.xml`, `tests="1" failures="1"`:

```
FAIL run#theStartAfterTheRunsOwnEndedChildIsNotRefused
  the item after the cap-ended one starts, so one item's cap no longer ends its run: expected '2', got '1'
loop-selftest: 1 of 1 cases failed
```

**Owning capability and guideline section.** The rule belongs to `agent-issue-loop`, in the requirement *A child the
run ended itself leaves no commit* — it is the one that promises the tree is kept as the child left it, and it is
where the sentence "nothing is discarded without `--reset-worktree`" is stated
(`openspec/specs/agent-issue-loop/spec.md:1992`–`2026`). The prose that owns the mid-run refusal is
`guidelines/Backlog.md`, section "The loop": *What it does with a start the worktree refuses* states the stop and its
argument (`guidelines/Backlog.md:515`–`517` on the base), so that paragraph gains the exception in the same change.

surface: 1 case added, 0 rewritten, 1 mutation record added (13.10), 6 records re-pointed at re-indented lines
(13.5, 13.8, 13.9, 53.1, 53.3, 62.6), 1 requirement added with 2 scenarios, 1 requirement modified with its 3
scenarios repeated verbatim — the counts are `grep -c theStartAfterTheRunsOwnEndedChildIsNotRefused
tools/loop-cases.sh` (`3`: the declared set, the dispatch table, the body — 237 cases against 236 before),
`bash tools/loop-mutations.sh --list | wc -l` (`176` against `175`) and
`bash tools/loop-selftest.sh --list | wc -l` (`237`); the six re-pointed records are the ones the re-indentation of
the extracted reset moved, and `bash tools/loop-mutations.sh --check --files tools/loop/run.sh` reports
`all 71 cases passed` on the change and `6 of 70 … no longer apply` on the base without them.

## What changes

- `tools/loop/run.sh` — the start-time repair of the `--reset-worktree` path is extracted, unchanged in behaviour,
  into `discard_kept_worktree` (`:509`), so the mid-run refusal can use the same verbs instead of a second copy of
  them. `run_child` records the items whose leftovers it wrote down in `own_leftovers` (`:1114`), and the mid-run
  `*"not clean"*` arm discards that tree and retries `start-item` once when the branch marker names one of them —
  `own_leftovers_of "$owner_item" && discard_kept_worktree "$leftovers"` (`:1262`). Every other refusal keeps its
  behaviour, including the attribution, the patch, the escalation and the stop.
- `tools/loop-cases.sh` — the case above, in the declared set, the dispatch table and the body.
- `tools/loop-mutations.sh` — `13.10` makes the retry never happen (reddens the case); the six records whose
  expressions quote the re-indented lines keep their assertions and their cases, only the leading whitespace and the
  indentation of their replacement line move.
- `guidelines/Backlog.md`, "The loop" — the paragraph that argues the stop gains the one exception and says which
  dirt it does not cover.
- `openspec/specs/agent-issue-loop/spec.md` — one added requirement with two scenarios, and one modified requirement
  that carries its three shipped scenarios verbatim.

## Spec

`ADDED` **The dirt a run ended itself does not stop it** — a start refused because the worktree is not clean is
retried, not stopped, when the branch marker names an item this run itself ended and wrote to that item's patch; a
retry refused again is attributed and stops the run as any unrecorded refusal does. Scenarios: *The item after the
run's own ended child starts* (`run#theStartAfterTheRunsOwnEndedChildIsNotRefused`) and *The dirt another item left
still stops the run* (`run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds`, shipped and unchanged).

`MODIFIED` **A child the run ended itself leaves no commit** — its body changes in one clause only: the tree is kept
exactly as the child left it, because nothing is discarded unrecorded — the flag discards it, and so does this run
before it starts its next item, the patch having been written first. All three shipped scenarios are repeated
verbatim, with the cases they name.

## Impact

`tools/loop/run.sh:499`–`513` (the three definitions), `:1110`–`1114` (the record), `:1262`–`1270` (the retry),
`:544` (the start-time caller); `tools/loop-cases.sh` (one case); `tools/loop-mutations.sh` (one record, six
re-pointed); `guidelines/Backlog.md`, "The loop"; the spec delta. No compiled file, so the Gradle suites are owed by nothing
this change touches; the shell suites the profile maps for `tools/loop/**` are owed, and the gate ran them once.
Rollback: revert the branch — the retry is one arm of one `case`, and an older engine stops exactly where it stopped
before.

## Open

The item's three candidate repairs, and the measurement that eliminates the two not taken (no option survives into
the design):

- *Reset the tree at the cap, right after `keep_leftovers` wrote the patch* — eliminated because it discards for the
  run whose budget the item ended as well, and such a run starts nothing else: three shipped scenarios and their
  cases (`run#treeOfAChildTheCapEndedIsNotCommitted`, `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`,
  `run#treeOfACapEndedChildIsKeptAsAPatch`) assert the tree is still carried. Discarding only when a next item
  exists is not available there: the queue, the item budget and the deadline are `state.sh next`'s answer, read
  after the item is recorded.
- *Keep the stop, and re-enter the loop after the reset the stop reason names* — eliminated because `.run.stop.reason`
  is the run's end for every reader (`report.sh`, `state.sh show`, the next run's pre-screen); recording it and then
  continuing makes the record false, and re-entering before it is recorded is this change's retry, one layer higher.
- *Have the stop reason carry the budget it spends* — eliminated because it does not move the number the item
  measures (234 of 400 minutes, one queued item, a hand landing of already-green work). It labels the loss instead of
  removing it, so the red case — *the next item starts* — stays red.
