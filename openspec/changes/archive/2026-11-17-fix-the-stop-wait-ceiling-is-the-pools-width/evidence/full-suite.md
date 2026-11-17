# Evidence: the full self-test suite

Host: this repository's measured host (sixteen CPUs, so the suite's default pool is sixteen cases at a time).
Command, three times: `bash tools/loop/tests/loop-selftest.sh` (no `--jobs`, no filters).

| run | tree | result |
|---|---|---|
| 1 — 2026-11-17, before task 1.7 | pool-1.1 … 1.6 | `2 of 475 cases failed — 1248.42 s`: `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` (`the run recorded the item its signal interrupted: expected 'blocked', got ''`) and `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced` (`LOOP-OUTSIDE-WRITE-ALIVE: (none) · attributed=none`; green focused and green in run 2 — a load flake of that case, not of this delta) |
| 2 — 2026-11-17, before task 1.7 | same | `1 of 475 cases failed — 1247.23 s`: `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`, the same assertion |
| 3 — 2026-11-17, after task 1.7 | pool-1.1 … 1.8 | `all 475 cases passed (JDK 21, offline, no token, no sandbox) — 1320.60 s`, exit `0` |

## The second site, measured on its own

`run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` run sixteen times concurrently on the tree before task 1.7:
`2 passed, 14 failed`, every failure `the run recorded the item its signal interrupted: expected 'blocked', got ''`.
The same case with the patient ending (task 1.7), sixteen concurrent instances: `16 passed, 0 failed`.
Four concurrent instances before task 1.7 passed 4 of 4 — the failure needs the pool the suite actually runs.

## Red on HEAD

With HEAD's `STOP_WAIT_*` block and `stop_wait_deadline` beside the case's new assertions
(`run#theStopWaitIsDecidedByTheStateTheRunWrites`, unchanged otherwise):

```
run#theStopWaitIsDecidedByTheStateTheRunWrites|   a pool of four allows 30 s per slot, in the ticks the wait sleeps in: expected '1200', got '300'
FAIL run#theStopWaitIsDecidedByTheStateTheRunWrites
```

## The mutations that hold the new invariant

```
$ tools/loop/tests/loop-mutations.sh --check --only "928.1 928.2"
loop-mutations: selected 2 of 406 records (labels: 928.1 928.2)
loop-mutations: all 2 cases passed — every selected record applies to the file it names
```

```
$ tools/loop/tests/loop-mutations.sh --only "928.1 928.2" --timeout 900
RED    928.1  run#theStopWaitIsDecidedByTheStateTheRunWrites
       2026-11-17T05:53:43Z — a pool of four allows 30 s per slot, in the ticks the wait sleeps in: expected '1200', got '300'
restore 928.1 run#theStopWaitIsDecidedByTheStateTheRunWrites green again
RED    928.2  run#aHupEndedRunRecordsItsStopAndUnpicksItsItem
       2026-11-17T05:51:35Z — a run that took HUP did not end inside 4800 ticks (30 s for each of the 16 case(s) the pool starts beside this one; status 124) — its stop was '' and its item ''
restore 928.2 run#aHupEndedRunRecordsItsStopAndUnpicksItsItem green again
red-when-mutated: 1 · not-noticed: 1 · restore-failures: 0
```

The `not-noticed: 1` is the `928.1` leg of that first pass, run before the record was moved onto the line this change
leaves (`MUTATION-DID-NOT-APPLY`); the second run above is the one on this tree.
