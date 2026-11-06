# Evidence — the bound values and the refusal streak

Everything here was taken on this host on 2026-11-06, offline, against the tree this change is applied to.
Files in this directory:

| file | what it is |
|---|---|
| `role-census.txt` | the per-role census over the whole loop archive (`evidence/role-census.py` from change `fix-role-turn-bounds`, `python3 … .pi/issue-loop/items`) |
| `bound-census.txt` | the same census, per role, beside the old and the new bound and the share of sessions each one ends |
| `red-on-HEAD.log` | the two cases that own the new invariants, run against `62ab008d` (HEAD) with only `tools/loop-cases.sh` copied over — they fail, and each fails for its own reason |
| `gate-loop-selftest.log` | the loop suite on the fixed tree: `all 319 cases passed` |
| `mutations.log` | `tools/loop-mutations.sh --only "68.2 68.6 81.1 81.2"`: `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0` |
| `check-*.log` | the four structural gates this delta's paths owe: doc routes, portability, OpenSpec hygiene, TODO references |

## The values

```
role                 n    p50    p75    p90    p95    p99   max  old  new  >=old       >=new
worker             346   77.5  104.0  138.5  161.8  196.6   219  110  170   77 (22%)   11 (3%)
worker-gate         11   37.0   47.5   67.0   69.0   70.6    71   60   80    2 (18%)    0 (0%)
worker-tail         20   27.5   37.5   43.4   56.2   59.2    60   45   65    2 (10%)    0 (0%)
reviewer           234   31.0   40.0   52.7   57.7   68.0   107   55   75   18 (8%)     2 (1%)
reviewer-pre-gate   12   40.0   53.2   54.9   55.0   55.0    55   55   75    2 (17%)    0 (0%)
```

The point of the table is the two right-hand columns: the old values ended 22 % of the worker sessions the archive
records, the new ones 3 %. `worker-gate 60` and `worker-tail 45` sat **below** sessions that finished (67–71 and
56–60), which is a bound cutting work that converged.

## Red on HEAD, green after

`red-on-HEAD.log`, both cases run against `62ab008d` with only the case file copied in:

```
state#aBlockAtTheRunsBoundIsNoRefusal|   done #21 failed
loop: done: --stop-reason must be refused or unmergeable
FAIL state#aBlockAtTheRunsBoundIsNoRefusal

run#childPastItsTurnBoundIsBlocked|   the block records the run's own bound as its mechanism: expected 'bound', got ''
FAIL run#childPastItsTurnBoundIsBlocked
```

`gate-loop-selftest.log`: `loop-selftest: all 319 cases passed (JDK 27, offline, no token, no sandbox)`.

## What the run that motivated it cost

`.pi/issue-loop/run.json`, 2026-11-05T21:37:13Z, 480 min, 80 items, review on: stopped at 00:13:58 on `two
consecutive items ended blocked` after **156 min and 6 items**. `#788`, `#778` and `#759` each had their worker
ended at **exactly 110 turns** (415 s, 697 s, 522 s) and left a `leftover-from-killed-run.patch` of 6 193, 5 271
and 5 554 lines. Across the archive's runs, five of the last six stopped on the same streak with **758 of 3 211
budgeted minutes** used.

## The tracker

`#788`, `#786`, `#778` and `#759` — the four items the last run left blocked — were taken off
`agent/needs-decision` + `status/on-hold` and are queued again: `tools/loop/prescreen.sh --dry-run` lists them as
candidates. `#786`'s remaining half (record such a block as `cut`) is filed as `#790`; `#754` and `#760` are the
two neighbours this change does not reach.
