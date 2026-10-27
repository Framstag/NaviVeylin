# Proposal — fix-the-run-end-sweeps-the-branches-it-made

## Why

One defect, verified on this tree, issue #239: **the run's end leaves every branch the loop made that this run did
not merge, so a landed branch survives for ever unless a person remembers `loop-clean.sh --apply`.**

**Root cause (one).** `end_run` reports and strips the kept worktree, and nothing in it asks which branches the loop
names already have their patches in the base (`tools/loop/run.sh:315`, called at `:1952`). The item branch this run
merged is released a few lines after the merge (`tools/loop/run.sh:1786`), but the run's own `loop-*` branch — which
lands when `start-item` switches the worktree off it — and every earlier branch a manual landing left are untouched.
The only remover is `tools/loop/loop-clean.sh:170-213`, and nothing in the run calls it.

**Verified, not quoted from the tracker.** `loop-clean.sh` on the change root names what a run's end leaves:

```
$ ./tools/loop/loop-clean.sh
loop-clean: branches of the loop whose patches are in the base
  kept fix/issue-239-… — a worktree holds it
  kept fix/issue-263-… — 2 commit(s) the base does not have
  would remove fix/issue-255-… (its patches are in the base)
  …
  would remove loop-20261027-132345 (its patches are in the base)
loop-clean: would remove 1 path(s), 11419648 B and remove 28 branch(es) — nothing removed (pass --apply)
```

Twenty-eight branches are leftovers on the tree that carries this item, twelve of them `loop-*` run branches. The
condition the tracker reports therefore still holds, even though `loop-clean` now exists: the run never asks it.

**The red test** — one case, because the defect is one mechanism:

- `run#theEndSweepsTheLandedBranchesItMade` — `evidence/red-on-head.log`, tally `evidence/red-on-head.xml`,
  `tests="1" failures="1"` (2026-10-27T15:32:55Z):

```
run#theEndSweepsTheLandedBranchesItMade|   the landed branch survived the run's own end
FAIL run#theEndSweepsTheLandedBranchesItMade
loop-selftest: 1 of 1 cases failed
```

The case builds a landed `fix/issue-*` branch, a branch that still carries work, a landed branch whose
archived change carries `review-failed-round-1.md`, and a landed branch a worktree holds; it runs `prune-landed`
read-only first (which must name and remove nothing), then one item, and asserts the landed branch is gone, the
other three are kept and named — the held one as `kept fix/issue-4-held — checked out` — and the run's own `loop-*`
branch is gone once its work is in the base.

**Owning capability and guideline section.** The behaviour is owned by the requirement this change adds, *The run's
end sweeps the branches it made* (`openspec/specs/agent-issue-loop/spec.md:2587`). It is `ADDED` rather than a
`MODIFIED` of *A branch the loop made is removed when its work is in the base*
(`openspec/specs/agent-issue-loop/spec.md:1628`) because that
requirement states what the item branch and the manual command do — it says a landed branch "SHALL be named as
removable" and names no run's end — so the run's own sweep is a requirement the shipped spec did not hold. The
guideline is `guidelines/Backlog.md`, the section "The loop" (`guidelines/Backlog.md:237`), the paragraph *A branch
the run made is a leftover once its work is in the base* (`guidelines/Backlog.md:653`), which currently says only
the manual command removes the rest.

**The numbers the measure block carries.**

```
LOOP-MEASURE-SUITE cases=254 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=1110 suites=21
LOOP-MEASURE-CHANGE 2026-10-27-fix-the-run-end-sweeps-the-branches-it-made scenarios=1 cases=1 unnamed=0
LOOP-MEASURE-SURFACE surface: 1 case added, 2 engine files changed (`tools/loop/worktree.sh`, `tools/loop/run.sh`), 1 guideline paragraph
```

The block was run from this change root against the run's base, after the gate, and every line above is its own
output (the full block, with the per-path and per-tally lines, is `evidence/measure.txt`). `cases=254` is the
declared case count the suite's `--list` prints; the gate's `tests=645` is a different number (the cases the gate
executed, across its suites) and is not this one. The `SURFACE` line is the block echoing the `surface:` sentence
below, which carries the `grep -l` that produced it.

surface: 1 case added, 2 engine files changed (`tools/loop/worktree.sh`, `tools/loop/run.sh`), 1 guideline paragraph
realigned — `grep -l -E 'aMergedBranchIsRemoved|sweepsOnlyWhatIsInTheBase|landedBranchesAreNamedAsRemovable|release-branch|prune-landed|branch -D|branches whose patches' tools/*-cases.sh tools/loop/*.sh tools/loop/prompts/* tools/loop/fixtures/scenarios/*/*`
names `tools/loop-cases.sh tools/loop/loop-clean.sh tools/loop/run.sh tools/loop/worktree.sh` before the change.

## What changes

- `tools/loop/worktree.sh` — a `prune-landed [--apply]` subcommand. It names the branches the loop made whose work
  the base already carries, removes exactly those with `--apply`, and refuses `main`, a branch a worktree holds, a
  branch that carries unlanded work, and a branch whose change carries `review-failed-round-*.md`. The landed test
  is four independent readings — ancestry, patch identity (`git cherry`), identical content of every file the branch
  touched, and the branch's archived change present on the base — not ancestry alone, because the loop lands work as
  a fresh `fix: … (#N)` commit (issue #228).
- `tools/loop/run.sh` — `end_run` calls `prune-landed --apply` after the report and after the worktree step, unless
  the run is a dry run or ended before the pre-screen. The sweep runs last because the worktree step is what
  releases the branch a `--clean-worktree` run held: swept first, the run's own `loop-*` branch would be read as
  held and kept. The sweep's own lines name what it removed and why it kept the rest.
- `tools/loop-cases.sh` — the red case above.
- `guidelines/Backlog.md`'s section "The loop" — the branch paragraph says the run's own end sweeps the landed branches it did not
  merge, and names the refusals.

## Spec

`ADDED` **The run's end sweeps the branches it made** — when a run ends it SHALL remove every branch the loop names
whose patches the base already holds, unless a worktree holds it or the change it carries has a reviewer's refusal;
it SHALL keep and name the branches that still carry work, and print what it removed. Scenario: *A landed branch no
longer outlives the run that made it* (`run#theEndSweepsTheLandedBranchesItMade`).

## Impact

`tools/loop/worktree.sh`, `tools/loop/run.sh`, `tools/loop-cases.sh`, `guidelines/Backlog.md`, the spec delta. No
compiled file, so no Gradle suite is owed by anything this change touches; the shell suites the profile maps for
`tools/loop/**` and `*.md` are owed, and the gate ran them once:

```
LOOP-GATE: tests=645 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 249s, tools/loop-selftest.sh --from-git 6f61cffe4f82e91c38349f5a6787c28011466387 428s, bash tools/loop-mutations.sh --check 2s, tools/loop-mutations-selftest.sh 329s, tools/check-portability-selftest.sh 25s, bash tools/gen-feature-list.sh --check-classification 3s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 2s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/mutate-selftest.sh 0s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/issue-tooling-selftest.sh 69s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/close-mutations.json --list 1s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s)
```

The line is `evidence/gate-run-2-summary.txt` — the gate's own stdout, of which the worker kept its last line; the
rest of that transcript was piped through `tail` and is not in the record, so nothing here cites a `gate.log` line.
The tallies are in `evidence/gate-tallies/` (21 files, 197 records in the mutation check) and the summary is
`evidence/gate-summary.xml`. The `645` is shell cases only: the Gradle tally in `evidence/gate-tallies/` carries
`tests="0"` (`BUILD SUCCESSFUL`, 249s), which is the whole of what this change owes for a compiled file
— it touches none. The sweep's ref deletion is falsified once: `evidence/mutation-13.13.log` —
`RED 13.13 run#theEndSweepsTheLandedBranchesItMade`, `the branch that carries work was removed`
(2026-10-27T16:03:18Z), `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`. Rollback: remove the
`prune-landed` call from `end_run`; the subcommand is additive and `loop-clean` keeps its current manual path.

## Open

The item's suggested shape also deletes the item's own branch inside `merge-item`. That deletion already exists one
line after the merge (`tools/loop/run.sh:1786` → `worktree.sh release-branch`), and moving it into `merge-item` would
make the merge command release a worktree it does not otherwise own — it would release the tree the run is still
working in. The change is not made for that reason, not because the measurement showed the item branch already gone:
the red case stops at the landed branch before it reads the item branch, so nothing in this change's record shows
the item-branch half closed. What the evidence does show is that the branches the run leaves behind are the `loop-*`
run branches and the earlier landed `fix/issue-*` ones, which the new sweep covers.
