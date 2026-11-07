# Evidence — a number a change quotes is a measurement, and the block carries the ones a worker used to guess

Issue **#244**. Every claim below is a command and its output, recorded in this directory.

## The symptom the item records, re-read from the reports

```
$ grep -h '^VERDICT:' .pi/issue-loop/items/*/reviewer-report-1.md | sort | uniq -c
     57 VERDICT: FAIL
     20 VERDICT: PASS
```

Round 1 over the items whose whole history is on disk (2026-10-21, the item's own measurement): 11 `PASS` against
29 `FAIL`, with 147 `UNPROVEN` claims — most of them a count or a timing the change asserts and no command
produced. The newest run after the block landed still fails round 1 on numbers the change does not back
(`.pi/issue-loop/items/{260,280,293,302}/reviewer-report-1.md`; `#297` and `#300` graded theirs `NOTE:`).

## Red on HEAD, green with the change

Each command is `bash tools/loop-selftest.sh --case <case>`, run against the engine as it stood before the fix
(the change's files stashed) and after it.

- `evidence/red-on-head-measure_printsTheSuiteCountAndTheGateTiming.log` — exit 1, the block carries no
  `LOOP-MEASURE-SUITE` line; `evidence/green-measure_printsTheSuiteCountAndTheGateTiming.log` — `PASS`.
- `evidence/red-on-head-prompt_workerIsHandedTheMeasureBlock.log` — exit 1, the worker's task does not state the
  number rule; `evidence/green-prompt_workerIsHandedTheMeasureBlock.log` — `PASS`.
- `evidence/red-on-head-prompt_reviewerIsBoundedToReading.log` — exit 1, the reviewer's task does not state it;
  `evidence/green-prompt_reviewerIsBoundedToReading.log` — `PASS`.

The red runs fail for the right reason, e.g.:

```
LOOP-MEASURE-END: 'LOOP-MEASURE-SUITE cases=3' not found in: LOOP-MEASURE-PROFILE …
the worker is told what makes a number a claim: 'A number the change quotes is the output of a command in its record' not found in: # Worker — one item, phases A–D
the reviewer is told a number with no command is not a claim: 'no command in the record produced is not a claim' not found in: # Reviewer — the fixed contract
```

## The falsification, one record per invariant

`evidence/revert-check-files.log` — the harness scoped to the files this change touches
(`bash tools/loop-mutations.sh --from-git 553d2112535b0a66b25b5a1ae6f3be35c99f7b9b`):

```
red-when-mutated: 18 · not-noticed: 0 · restore-failures: 0
```

`evidence/revert-check.log` — the four records this change adds, `--only '61.1 61.2 61.3 61.4'`:

```
red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0
```

`bash tools/loop-mutations.sh --check` (part of the gate) confirms every declared record applies to the file it
names.

## The gate

`evidence/gate.log` — run once, in a detached worktree under `/tmp` (`/tmp/nv244`, the recipe #303's evidence
records): the portability and engine suites refuse a `/.pi/` root, so the loop's own worktree cannot host them.

```
LOOP-GATE: tests=444 failures=0 errors=0 (tools/loop-selftest.sh --from-git 553d2112535b0a66b25b5a1ae6f3be35c99f7b9b 422s, bash tools/loop-mutations.sh --check 1s, tools/loop-mutations-selftest.sh 212s, tools/check-portability-selftest.sh 22s, tools/check-doc-routes.sh 0s, tools/check-doc-routes-selftest.sh 2s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 0s)   # not retained as an artifact of its own: the sum of the per-suite tallies
gate exit=0
```

The per-suite tallies are retained under `evidence/gate/` (loop-selftest `224`, the mutations `--check` `158`, the
mutations suite `34`, the portability suite `28`, and the rest). No compiled path is touched, so no Gradle suite
is owed.

## The block, after the change

```
$ bash tools/loop/measure.sh --base HEAD
LOOP-MEASURE-SUITE cases=224 cmd=tools/loop-selftest.sh --list
LOOP-MEASURE-GATE seconds=- reason=no tally under the gate's directory carries a time — the gate of this tree has not run
LOOP-MEASURE-TALLY …/bash_tools_loop-mutations.sh_--check.xml tests=158 failures=0 time=1s
```

## Pointers

`bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/archive/2026-10-25-fix-loop-numbers-are-measured --root .` →
`evidence-check: PASS`; `openspec validate fix-loop-numbers-are-measured --strict` → valid.
