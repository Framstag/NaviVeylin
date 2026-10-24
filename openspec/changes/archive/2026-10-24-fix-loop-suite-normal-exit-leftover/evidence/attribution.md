# Attributing the gate's red set (guidelines/Build.md §4d)

Measured 2026-10-24. The change's own gate is `tools/loop/gate.sh --base 49dfd7db`; its verdict line is

> LOOP-GATE: tests=377 failures=3 errors=0 (tools/loop-selftest.sh --from-git 49dfd7db 1329s, tools/loop-mutations-selftest.sh 173s, bash tools/loop-mutations.sh --check 1s) — failing: tools/loop-selftest.sh --from-git 49dfd7db — the tally is **not retained** as a JUnit XML: the gate is a shell suite, and the two full-suite logs below are what carry the cases.

The suite ran 210 of 210 cases (`--from-git` selects everything, because `tools/loop-cases.sh` is one of the
`loop_case_core_files`).

## The failing set of this change's run (`full-suite.log`)

| case | message |
|---|---|
| `prompt#workerNamesEveryPhaseAndSkill` | `the worker prompt was not rendered` |
| `prompt#workerCarriesTheFeaturePath` | `the worker prompt was not rendered` |
| `run#aWriterTheRunCanPlaceBlocksTheItem` | `a write the run can place in the child's own tree decides the item: expected 'blocked', got 'done'` |

## The control: the same suite on pristine `main` (49dfd7db, `baseline-main-full-suite.log`)

Run the same day, on the same host, in a detached worktree of `origin/main` — the change's base, without the
change. 209 cases (one fewer: the case this change adds), 3 failures:

| case | message |
|---|---|
| `run#prescreenAnnouncesItselfBeforeItReturns` | — |
| `run#childOutlastingTheWallClockIsBlocked` | — |
| `run#aWriterTheRunCanPlaceBlocksTheItem` | `a write the run can place in the child's own tree decides the item: expected 'blocked', got 'done'` |

## What the two sets say

- **One case is common to both runs**: `run#aWriterTheRunCanPlaceBlocksTheItem`. It is not this change's red — it
  is the documented red of open item **#288** on `main`'s tip ("4 of 4 runs red, with and without the untracked
  probe file"), and the change that fixes it (`fix-loop-attribution-is-read-before-the-sweep`) is open in another
  worktree. It is stable, so it is the one failure the gate's verdict carries.
- **The other two failures move between runs**: this change's run reddens two `prompt#` cases, the pristine
  control reddens two `run#` cases, and the intersection is empty apart from #288's. A failure that moves between
  runs, and that passes alone, is not the change's (`guidelines/Build.md` §4d).

## The isolated runs (`flake-isolation.log`)

`prompt#workerNamesEveryPhaseAndSkill` and `prompt#workerCarriesTheFeaturePath` were run alone on this change's
tree, three times each: **6 of 6 PASS** (`evidence/flake-isolation.log`). Neither case
touches a file this change edits: they read `tools/loop/run.sh`, `tools/pi-child-events.sh`, `tools/tooling.json`
and the scenario fixtures, and this change adds a case and a mutation record.

## The condition both runs were measured under (`concurrent-suites.log`)

Three independent harnesses were live in the **same repository** at 2026-10-24T08:47:31Z:

- `tools/loop/gate.sh --base 9e77cd69…` (the #288 change, run from the project tree),
- `tools/loop/run.sh --items 1 --minutes 90 --review off` (the issue-227 loop run),
- `tools/loop-mutations.sh --files tools/loop/run.sh` (the `fix-loop-child-outlives-its-deadline` change).

Each suite creates git worktrees and branches in the repository it runs in (`loop-<timestamp>`), so two suites in
one repository contend for the same ref namespace and git locks while the host also carries the load of the
others. That is the condition under which the two moving failures appeared; it is filed as **#295** rather than
argued away (see `tasks.md` 3.4, 3.7 and 3.8).

## The suite's own sweep fired, in both runs

Both full runs end with the sweep this change's case asserts:

```
ended <pid> — its working directory is inside loop-selftest.<…>
```

so the normal-exit sweep is not only exercised by the new case in isolation: it ends a real leftover of a
210-case suite as well.
