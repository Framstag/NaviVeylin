# Evidence — fix-loop-selftest-selects-the-cases-a-delta-owns

Base commit `6683376e`. Every number below was copied from a run at the moment it was produced.

## The red case, against the base commit's driver and gate

`git show 6683376e:tools/loop-selftest.sh` and `…:tools/loop/gate.sh` were written over the working copies,
the two groups were run, and the working copies were restored (2026-10-23T12:05Z).

| run | tally | failures |
|---|---|---|
| `tools/loop-selftest.sh --group driver` | `red-driver-group.xml` (`tests="10" failures="4"`) | `driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns`, `driver#aFileSetThatNamesNoCaseIsRefused`, `driver#aCoreFileOwesEveryCase`, `driver#everyCaseDeclaresTheFilesItCanReach` — each `loop-selftest: unknown argument --files` / `--case-files` |
| `tools/loop-selftest.sh --group gate` | `red-gate-group.xml` (`tests="12" failures="1"`) | `gate#theDeltaHandsASuiteTheBaseItMeasured` — the plan carried the literal `{base}` |

Transcripts: `red-driver.log`, `red-gate.log`.

## The change's own invariants, falsified one at a time

`bash tools/loop-mutations.sh --only '53.1 53.2 53.3 53.4 53.5'` → `red-when-mutated: 5 · not-noticed: 0 ·
restore-failures: 0`, `revert-check-53.log`, `revert-check-53.xml`.

`bash tools/loop-mutations.sh --check` → `all 116 cases passed — every selected record applies to the file it
names`, recorded in the gate's own tally `gate-tallies/bash_tools_loop-mutations.sh_--check.xml`.

## The gate, run once

The environment the run handed this child carries `LOOP_CONF` from the project tree, so the gate was started
with `LOOP_CONF` naming the worktree's own profile — the profile beside the engine it was about to measure —
and its first line says so. `gate.log`, exit 0:

```
LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
```

Its summary named six suites and their seconds — the build 343 s, `tools/loop-selftest.sh --from-git
c03e7e0b66022218d797954099f1957aca34cb55` 731 s, `bash tools/loop-mutations.sh --check` 1 s,
`tools/loop-mutations-selftest.sh` 72 s, `bash tools/gen-feature-list.sh --check-classification` 2 s,
`tools/check-doc-routes.sh` 1 s — and counted 345 cases across them with no failure and no suite that reported
none. A case count is quoted below only where a tally carries it.

The compiled command was owed because the base the gate resolved (`c03e7e0b`, the merge-base with `origin/main`)
is older than the items already landed on this branch: the delta it measured carries `*.kt` sources of those
items. The self-test was owed whole — 195 cases, `tests="195" failures="0"` in
`gate-tallies/tools_loop-selftest.sh_--from-git_c03e7e0b66022218d797954099f1957aca34cb55.xml` — because this
change touches the case file, the driver, the profile and the library, all of which are core files.

## What the selection is worth, measured on this tree

| command | cases |
|---|---|
| `tools/loop-selftest.sh --list` | 195 |
| `--list --files tools/loop/worktree.sh` | 32 |
| `--list --files tools/loop/run.sh` | 110 |
| `--list --files tools/loop/lib.sh` | 195 (a core file) |
| `--list --files tools/loop/gate.sh` | 26 |

## The declaration

`bash tools/loop-selftest.sh --case-files` prints 195 rows and no `<undeclared>`;
`driver#everyCaseDeclaresTheFilesItCanReach` reads every body against its declaration and is green.
