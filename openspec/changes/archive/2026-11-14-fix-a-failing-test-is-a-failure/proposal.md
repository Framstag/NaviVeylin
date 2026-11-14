# Proposal — a step's own failing tests are its failures

## Why

**Root cause.** `finish_step` derives a step's failure count from one shape only: the summary line this tree's own
shell suites print. `f` is filled by the `sed` at `tools/loop/gate.sh:597` (base `4c4e6819`), which matches
`<name>: M of N cases failed` and nothing else, and everything a non-zero step reported that is not that line is
kept as an error (`tools/loop/gate.sh:604`, `[ "$rc" -eq 0 ] || [ "$f" -gt 0 ] || err=1`). A JVM test task writes no
such summary: it prints its `<task> FAILED`, one `<class> > <case> FAILED` per failing case and the error under it.
So a step whose tests failed is tallied `failures=0`, `errors=1`, and the gate line says the JVM fell over where a
test ran and failed.

**Observed, and what the item reported.** The issue text of #916 reports the gate of a change that broke two tests
reading `tests=46 failures=0 errors=2` (a number no command of this change's record produced — that run is not retained), and its `Evidence` line points at `.pi/issue-loop/items/898/gate.log`. That path in
the project's tree no longer carries the red run — it carries a later, green gate of the same item
(`LOOP-GATE: tests=181 failures=0 errors=0` at `.pi/issue-loop/items/898/gate.log:486` — that run's tally XML is not retained) — so the pointer is a
memory of a file that was overwritten, and this change does not rest on it. The mechanism is in the tree and is
proved below by a case, which is the fact the fix is measured against.

**The red test.** `gate#aTestFailureIsAFailureNotAnError` (`tools/loop/tests/loop-cases.sh`) runs a gate whose one
step prints a task line, two per-test `FAILED` lines, the two errors, and exits 1 — the shape a JVM test task
writes, with no summary. Against the engine at this item's base — the case as it stands in this change, the engine
copied from `git show 4c4e6819:tools/loop/gate.sh` — one
`bash tools/loop/tests/loop-selftest.sh --case 'gate#aTestFailureIsAFailureNotAnError'` run at
2026-11-14T19:12:18Z (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`):

```
LOOP-GATE: tests=0 failures=0 errors=1 (bash …/suite.sh 0s) — failing: bash …/suite.sh
FAIL gate#aTestFailureIsAFailureNotAnError
loop-selftest: 1 of 1 cases failed
```

`tests=1 failures=1 errors=0`. The same case against the fix passes — `evidence/red-case-green.log` carries
`PASS gate#aTestFailureIsAFailureNotAnError`, timestamped 2026-11-14T19:12:10Z in the item's `events.jsonl`
record `call_jfub8at4`, whose captured run held `LOOP-GATE: tests=0 failures=2 errors=0` — not retained in any
file; the passing `assert_contains` on the run's own output is its record.

## What changes

One function, one reading. `finish_step` keeps the summary's numbers where a suite printed one and, where a
non-zero step printed none, reads the failing cases the step itself named: a line that carries
`<class> > <case> FAILED` is one failure. The task's own
header begins with `>`, which is what keeps it from being counted as a case, and a step the bound ended, and a step
that named no case at all, keep the error #268 made of them — the case count (`tests=`) stays the count the suite
reported, which for such a step is none, because a tally SHALL NOT state a case the suite did not report.

## Surface

`tools/look.sh 'cases failed'` — 14 matches in 5 files: the rule's text and symbol are `tools/loop/gate.sh` (3),
`tools/loop/tests/loop-cases.sh` (6), `tools/loop/tests/loop-selftest.sh` (2), `tools/loop/tests/loop-mutations-selftest.sh`
(2) and `tools/loop/tests/loop-spend-selftest.sh` (1). `tools/look.sh 'errors='` — 90 matches in 14 files, of which
the ones that *assert* the tally are `tools/loop/tests/loop-cases.sh` and `tools/loop/tests/loop-mutations.sh`.

- 1 case added (`gate#aTestFailureIsAFailureNotAnError`), 0 rewritten; the two neighbours that own the rule it
  extends — `gate#aFailingSuiteIsReadFromTheSameStream` and `gate#aFailingSuiteWithNoSummaryInventsNoTest` — pass
  unchanged.
- 1 mutation record added (`916.1`), which is the record the new case is kept by.
- 2 documents realigned: `guidelines/Backlog.recipes.md` section 7c (the #268 paragraph, which now names the shape that
  is a failure) and this change's delta to `openspec/specs/agent-issue-loop/spec.md`, "A suite that reported no
  summary invents no number".
- 1 engine function changed: `finish_step`, `tools/loop/gate.sh`.
