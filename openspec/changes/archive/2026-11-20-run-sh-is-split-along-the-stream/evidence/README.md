# Evidence — the split, measured on the tree it leaves

Every number below was taken on 2026-11-20 in the change root, on the tree this change leaves with its files
uncommitted, against the run's base `b63de90b1b413cd7fe9b0452ee95f54c1f55c8eb`. The logs are beside this file.

## The red case on HEAD

`driver#anOwnedFunctionIsInItsOwnersFile`, run against the base commit's engine (`git archive b63de90 tools/loop |
tar -x`, with this change's `layout.tsv` and `shared.tsv` laid over it, and `LOOP_ENGINE` naming that copy):
`base-red-b63de90.log`, taken 2026-11-20T10:41Z. It fails naming 146 functions — 73 that the table gives to
`child.sh`, `evidence.sh` or `leftovers.sh`, files HEAD does not have and `run.sh` still defines, and the same 73
that HEAD's `run.sh` defines and the table's entry row no longer owns, because on HEAD there is no boundary for
them to live behind. The project's gate is a shell suite and writes no JUnit XML, so the run is
`1 case selected, 1 failed` (`tests=1 failures=1`), not retained as XML; the shell suite writes none, and
`base-red-b63de90.log` is the record.

## The suite, whole and narrowed (tasks 5.1, 3.2)

| run | command | cases | wall |
|---|---|---|---|
| whole suite | `bash tools/loop/tests/loop-selftest.sh` | 502 of 502 | 877.61 s |
| one owner's leg | `bash tools/loop/tests/loop-selftest.sh --files tools/loop/evidence.sh` | 261 of 502 | — |

`full-suite.log` is the whole run, taken 2026-11-20T10:22Z. The narrowed leg's count is its `--list` selection
(261 cases, 52 % of the suite); its wall time is not re-measured here — the gate reports the wall time of the
legs it runs as `LOOP-MEASURE-GATE seconds=`, and the count is what the declaration owns.
`driver#theSelectionFollowsTheOwner` is the case that asserts the leg contains that owner's cases and is a strict
subset of the suite, and it runs the two `--list` calls itself, so the count above is that case's own subject.

Both runs were taken with the adapter's result-bound variables cleared
(`LOOP_TOOL_RESULT_LEDGER= LOOP_TOOL_RESULT_TRIM_RECORD= LOOP_REPEAT_READ= LOOP_TOOL_RESULT_MAX_CHARS=`): the
item's ledger already holds a read of `items/816/gate.log`, and `adapter#theResultBoundExemptsGateEvidence` reads
that path and asserts the text arrives whole — the trimmer's repeat arm answers a second read of a path the same
child was handed with a stub instead. The ledger is per child, so a fresh child (the gate's) reads the path once
and the case is green under it; the substitution removes this run's own memory, not a tree fact. Measured both
ways on this tree: the case is red inside a suite that inherits the ledger and green once the four variables are
cleared, and green as a single case with them cleared.

## The re-measurement the recorded decision asked for

The owner's decision of 2026-11-20 (option A) asked for one focused re-measurement of
`run#childUsageIsRecordedPerItem` under the gate's own conditions, because the previous gate red that case
(`expected '2', got '3'` — three child invocations where the fixture starts two) while two clean suite runs of the
same tree held it green. `child-usage-remeasure.log` is that measurement on this tree: three runs of the case
alone at the gate's pool width (`LOOP_SELFTEST_JOBS=4`), 2026-11-20T10:34Z–10:35Z, each ~17 s, each `PASS`, and the
whole suite's run above holds it green as well. No third invocation appears outside the gate, so the red names no
child this tree starts.

## Lengths and bounds (tasks 3.3, 1.1)

`bounds.tsv` carries the before/after count of each file the split produced and the bound its row records, which is
the after count + 25 % rounded up:

```
run.sh        4425 → 2666 lines, bound 3333
child.sh          - →  787 lines, bound  984
evidence.sh       - →  680 lines, bound  850
leftovers.sh      - →  422 lines, bound  528
```

The three new files hold 1889 lines against the entry's 2666, and the four hold 4555 against `run.sh`'s 4425, so
the move added 130 lines: the four file headers, the two-line source guard each new file carries, and the blank
lines between them. No function body was rewritten — `driver#theMovedEngineBehavesAsItDid` loads the declared
chain in one shell and asserts every function each row owns is defined once it loads, and the whole suite is the
behavioural proof.

## Revert-checks (tasks 5.2–5.7)

`revert-checks.log` is one run of `bash tools/loop/tests/loop-mutations.sh --only '892.1 892.2 892.3 892.4 892.5
892.6'`, taken 2026-11-20T10:36Z: each record mutates the file its rule is about, the named case goes red for the
reason its description states, the file is restored, and the case is green again.
`red-when-mutated: 6 · not-noticed: 0 · restore-failures: 0`.

| record | mutation | case |
|---|---|---|
| 892.1 | `layout.tsv`'s entry bound set one line below its length (3333 → 2665) | `driver#noEngineFileExceedsItsBound` |
| 892.2 | `child_cap` defined in the entry, whose row does not own it | `driver#anOwnedFunctionIsInItsOwnersFile` |
| 892.3 | `progress` assigned a second time, in `child.sh` | `driver#theSharedNamesAreDeclared` |
| 892.4 | the entry's `child.sh` source line replaced by a second `evidence.sh` | `driver#theSourceOrderIsDeclared` |
| 892.5 | a call to `child.sh`'s `child_cap` from `evidence.sh`, which is sourced before it | `driver#aCallToALaterFilesFunctionFails` |
| 892.6 | the driver's file selection widened to the whole suite | `driver#theSelectionFollowsTheOwner` |

Two of the tasks named a different arm than the tree has, and the change's `tasks.md` says why: 5.4's
`driver#aNameAssignedTwiceIsRefused` is the derivation's own refusal over a fixture (a tree mutation cannot reach
it — the arm that reads the engine is `driver#theSharedNamesAreDeclared`), and 5.7's single re-pointed declaration
stays consistent with the selection it produces, so the mutation is the widened selection rather than one case's
declaration.

## Defects the work found and did not fix (task 5.8)

Neither is in this change's scope; both are named here so the run can file them rather than a reader having to find
them again.

1. **A case declaring `*` is not selected by a file set.** `tools/loop/tests/loop-selftest.sh`'s `files_select`
   reads a case's declaration as `for tok in ${case_files[$1]:-}`, which glob-expands the one declaration that is
   a bare `*`: the two cases that declare it (`usage#noUnguardedValueFlagRemains`,
   `clean#endsARunWhoseTreeIsAlreadyDeleted`) are selected by no `--files`/`--from-git` leg at all, so a delta to a
   file they are about can skip them and still read green. Measured: `--list --files tools/loop/evidence.sh` is
   261 cases and holds neither, while the same list unfiltered is 502.
2. **The shared-name derivation cannot be read over the engine's entry scripts.** `layout_shared_awk`'s `doubles`
   arm scans every `*.sh` under `tools/loop/`, and each entry script's own top-level word (`cmd="${1:-}"`,
   `here=…`, `paths=…`) is such a word, so the arm reports eight names over today's tree that two *separate*
   processes each assign and a third spell for itself (`cmd`, `paths`, `here`, `worktree`, `base`, `may_close`,
   `change`, `LOOP_STATE_FILE`). The refusal itself is sound and is proven over a fixture by
   `driver#aNameAssignedTwiceIsRefused`; only its engine-wide reading is not, which is why this change's rule is
   asserted over the engine by the declaration's comparison instead.
