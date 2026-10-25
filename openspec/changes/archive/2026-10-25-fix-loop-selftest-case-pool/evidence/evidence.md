# Evidence — fix-loop-selftest-case-pool

The item is #260 (improvement): the loop's self-test ran its cases strictly one after another — 163 cases, 723 s
measured 2026-10-22 — though every case owns a throwaway repository, a scratch tree and a process group of its own,
and the item names the exit trap's `/proc` sweep as the thing that had to become per case before a pool would be safe.

| what | file | what it says |
|---|---|---|
| red case, pool | `red-on-head-pool.log` | HEAD `a2489ca7`'s driver, this tree's cases: `the suite ran its cases one after another (at most 1 was running at any moment)` → `1 of 1 cases failed` |
| red case, per-case sweep | `red-on-head-sweep.log` | HEAD's driver hands the case no scratch root of its own → `1 of 1 cases failed` |
| green | `green-cases.log` | both cases `PASS` on the tree |
| mutation records | `mutation-run-new-records.log` | `59.1` and `59.2` `RED` under their mutation and green after the restore; `48.2` repaired to the marker check it now names, `RED` and restored |
| the whole suite | `full-suite-pooled.log` | `all 220 cases passed` (the one failure of that run was this change's own `driver#aGroupFilterRunsOnlyItsOwnCases`, realigned and re-run green; see §"the run that found it") |
| the sweep's cost | `sweep-cost-probe.sh` | 664 processes, `loop_pids_under` 838 ms / 832 ms per pass |
| the gate, once | `gate.log`, `gate/` | `LOOP-GATE: tests=534 failures=0 errors=0`; `tools_loop-selftest.sh_--from-git_db888dd8….xml` records `tests="221" failures="0"` in 283 s |

## The run that found it

The first whole-suite run (pooled, before the gate) failed one case, and it was this change's own realignment:
`driver#aGroupFilterRunsOnlyItsOwnCases` compared the sorted log (`tr '\n' ' '`) against an expected value that still
carried a newline. Fixed, and the case then passed on its own:

```
$ bash tools/loop-selftest.sh --case 'driver#aGroupFilterRunsOnlyItsOwnCases'
PASS driver#aGroupFilterRunsOnlyItsOwnCases
```

`full-suite-pooled.log` is that earlier run and is kept as it was produced: it is the measurement of the pool
(221 cases, 4 min 24 s, 439 % CPU), and its single failure is the assertion that was repaired. The gate is the run
that carries the verdict, and it is green.

## What the pool costs and what it buys

- buys: `state`'s 18 cases 1:00,12 → 20,86 s; `worktree`'s 18 cases 1:07,79 → 20,06 s; the whole suite 4:24,11 at
  439 % CPU, against the item's 723 s for 163 cases run one at a time.
- costs: one `/proc` sweep per case instead of one per suite — 0,83 s per pass on this host of 664 processes, ~185 s
  over 221 cases, spread by the pool. `--jobs 1` is the pre-change shape and pays it serially; it exists for a reader
  who needs that, and `LOOP_SELFTEST_JOBS` in the profile bounds a host that wants fewer workers.
