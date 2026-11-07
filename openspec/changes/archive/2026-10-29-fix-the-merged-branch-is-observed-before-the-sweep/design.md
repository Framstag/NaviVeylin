# Design — fix-the-merged-branch-is-observed-before-the-sweep

## Context

`run#aMergedBranchIsRemoved` (`tools/loop-cases.sh`) closes one item with the stub child and then asks git what
branches exist. The run it drove removed the item's branch twice over: once at `tools/loop/worktree.sh:698`
(`release-branch`, called from `tools/loop/run.sh:1885` right after the merge) and — had that not happened — again
at `tools/loop/worktree.sh:664` (`prune-landed --apply`, called from `tools/loop/run.sh:344` at the run's end).
Both deletes are correct; the case simply reads the state they share, so it can only fail when **both** are gone.
See `proposal.md` — Why for the record, the transcript and the owning requirement.

The one thing the run itself does between the two deletions is print its report (`tools/loop/report.sh`, called
from `end_run` before the sweep at `tools/loop/run.sh:328`, sweep at `:344`), and that report's `branches:` line is
a read of git at that instant (`tools/loop/report.sh:172`). With the release intact it names only the run's own
`loop-<timestamp>` branch; with `42.2` applied it names the item's `fix/issue-*` branch, which the sweep deletes
three lines of output later (`evidence/probe-42.2-run-output.log`).

## Goals / Non-Goals

**Goals**

- The record `42.2` (the release's `git branch -d`) is noticed: the named case goes red when that line is a no-op,
  and green again when it is restored.
- The observation is a fact about git, taken at a point in the run where the sweep cannot have answered for it.

**Non-Goals**

- Giving the end sweep's deletion (`tools/loop/worktree.sh:664`) a record of its own. It carries none today
  (`bash tools/loop-mutations.sh --files tools/loop/worktree.sh --list` prints 16 records and none mutates that
  line), and adding one would be a second invariant with a second case — out of this item's one-root-cause scope.
- Any change to `release-branch`, `prune-landed`, the report's wording, or the record's description.

## Decisions

**D1 — the observation is the run's own report line, not a stub.** The item's shape (a) asks for "a scenario whose
stub reads `git branch --list 'fix/issue-*'` before the run's end". The `gh` stub cannot: `tools/issues/fixtures/gh`
answers invocations from fixture files and records writes to `GH_STUB_RECORD`, and it has no hook that runs a
command of the scenario's (`grep -n 'HOOK\|hook' tools/issues/fixtures/gh` → no match); the `pi` stub's action runs
inside the worktree at worker time, which is *before* the merge, so it cannot see the release either. The run's
report is the read of git that already exists at exactly the point shape (a) wants — after the merge and the
release, before the sweep — so the case reads that line out of the run's output.

**D2 — the assertion is about absence in git, not about the message that reports it.** The alternative — assert the
release's own `released: branch … removed` line — was eliminated by the probe: with `42.2` applied, `release-branch`
prints `branch … was not removed — it carries work the base does not have` and the sweep then force-deletes the
branch, so the run's message names a state that never existed. A case quoting that sentence would pin a sentence,
not the tree, and the sentence is exactly what the mutation leaves standing. The `branches:` line is git's answer,
so the case fails on a fact.

**D3 — the case keeps its name, its file declaration and its other assertions.** `42.2` names
`run#aMergedBranchIsRemoved`, so the observation has to live in that case: a new case would leave the record
pointing at a case that still cannot notice it. The worktree-release assertion (`42.1`'s) and the base-content
assertion stay untouched, so each record still has its own adjacent assertion.

**D4 — a parse that finds nothing fails.** The assertion reads the last `branches:` line out of the run's output
and requires it to exist before asserting it names no `fix/issue-*` branch, so a reworded report line makes the
case red rather than vacuously green. `report.sh`'s wording already has its own owner (`42.4`,
`report#landedBranchesAreNamedAsRemovable`).

## Risks / Trade-offs

- **The assertion depends on a printed line's prefix.** Mitigated by D4 (a missing line fails) and by `42.4`, which
  makes the wording itself a falsifiable invariant.
- **The report's `branches:` line also lists the run's own `loop-<timestamp>` branch**, so it is never empty. The
  assertion therefore names the item's `fix/issue-` prefix rather than emptiness — the `loop-*` branch is the run's
  own and is removed by the sweep, which is `run#theEndSweepsTheLandedBranchesItMade`'s subject.
- **Blast radius**: one case body in `tools/loop-cases.sh`. No engine script, prompt, fixture or Gradle source, so
  no build and no flavour coverage is owed; the gate owes `tools/loop-selftest.sh --from-git <base>` and
  `bash tools/loop-mutations.sh --check` for the paths the change touches (`tools/loop/loop.conf`).
- **Cost**: the record's own run is `bash tools/loop-mutations.sh --only 42.2` (≈20 s for the two case runs,
  measured in `evidence/red-on-head.log`'s run).

## Migration Plan

No migration: additive assertion in one shell case, no data, no API, no configuration. Rollback is the revert of
that one case body; the record stays noticed only while the assertion is there, so a revert restores the defect.

## Open Questions

None.
