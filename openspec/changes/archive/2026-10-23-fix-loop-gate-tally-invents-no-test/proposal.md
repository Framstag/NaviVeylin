# fix-loop-gate-tally-invents-no-test

## Why

A suite that executed no test at all is tallied as one failing test.

**Observed** (issue #268, 2026-10-23). The compiled step of a hand-run gate failed at configuration
(`Could not create task ':app:mergeNativeSbom'`, `BUILD FAILED in 21s`, `3 actionable tasks: 3 executed`, no
test task reached its action). The tally the gate wrote for that suite read
`<testsuite name="./gradlew test -PforceTests --no-build-cache" tests="1" failures="1">`. Reproduced on HEAD
against a suite that only writes `BUILD FAILED in 21s` and exits 1: the tally records
`tests="1" failures="1" errors="0"` and its `<system-out>` is the whole of what the suite wrote
(`evidence/red-HEAD.log`).

**Root cause, one sentence:** `tools/loop/gate.sh` derives a suite's case and failure counts from that suite's
own summary line (`all N cases passed` / `M of N cases failed`) and, when neither is found, fills the gap with
a fabricated number — `f=1` at `tools/loop/gate.sh:140` and `n=1` at `tools/loop/gate.sh:148` in the engine the
base commit `f5c32df2` carries (`git show f5c32df2:tools/loop/gate.sh`) — so "failed
without a summary" is recorded as "one test failed", although the file's own header says the tally is "derived
from the suite's summary, never invented" (`tools/loop/gate.sh:17-19`).

**Red test on HEAD, before the fix.** One case added to `tools/loop-cases.sh`,
`gate#aFailingSuiteWithNoSummaryInventsNoTest`, run against the engine as HEAD carries it
(2026-10-23T09:38:16Z, transcript `evidence/red-HEAD.log`, tally `evidence/red-HEAD.xml`, `tests="1"
failures="1"`):

```
  the gate's line states the suite reported no case, and one error: gate: bash /…/case.9Mqxzu/suite.sh
Could not create task :app:mergeNativeSbom
BUILD FAILED in 21s
… — failing: bash /…/case.9Mqxzu/suite.sh: 'LOOP-GATE: tests=0 failures=0 errors=1' not found
FAIL gate#aFailingSuiteWithNoSummaryInventsNoTest
```

— the gate's own line reported `failures=1` for a suite that reported nothing (the whole line is in
`evidence/red-HEAD.log`), and the tally above it invented the case as well. The reproduction against HEAD's
engine, with a probe suite whose command names its own directory:

```
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="bash <probe>/suite.sh" tests="1" failures="1" errors="0">
  <system-out>Could not create task :app:mergeNativeSbom BUILD FAILED in 21s</system-out>
</testsuite>
```

**Owning capability and guideline section.** The capability is `agent-issue-loop`, whose requirement "The gate
streams a suite's output while it runs" owns the numbers the gate reports and keeps; the delta adds one
requirement beside it. The guideline that owns the loop's mechanics is `guidelines/Backlog.md`, section
"7. The loop", whose paragraph on the streamed gate says the summary, the tally and the failure text are read
from one file. The result a person reads is `guidelines/Build.md`, section "4a. The verdict, the warnings and
the failures", which names a non-zero exit code as the failure — the fact this tally hid behind a number.

**Consequence.** The tally is what the run's own check reads for a green claim (`tools/loop/worktree.sh:248`
requires a tally whose `tests` is non-zero, whose `failures` is zero and whose `errors` is zero; `tools/loop/run.sh`
requires a test-results XML to exist at all), so a fabricated case is a tally meant to prove what executed
becoming evidence that something did.

## What Changes

- `tools/loop/gate.sh` — the case and failure counts are the suite's own, never filled in. A suite that fails
  without reporting a summary contributes zero cases and zero failures with one error to its tally
  (`errors="1"`; the attribute already exists and was always written as zero), and the gate's own line carries
  `errors=<n>` beside its case and failure counts. The `n=1`/`f=1` fallbacks are removed; the error count is the
  fact that replaces them, the gate still exits non-zero and still names the suite.
- `tools/loop-cases.sh` — one case added (`gate#aFailingSuiteWithNoSummaryInventsNoTest`), and the case that
  guards the reported half asserts `errors="0"`, so the two facts cannot be confused in either direction.
- `tools/loop-mutations.sh` — record `47.1` restores the fabrication and names the case that goes red.
- `tools/loop/prompts/worker.md` — the `LOOP-GATE` line a worker reports carries `errors=<n>`.
- `guidelines/Backlog.md`, section "7. The loop" — the streamed-gate paragraph says what a suite that reported
  no summary contributes, with the measurement behind it.

**Surface, counted before the plan was fixed.** `grep -n 'n=1\|f=1' tools/loop/gate.sh` names two lines before this change (`:140`, `:148`); `grep -n 'LOOP-GATE: tests' tools/loop-cases.sh` names three before this change (two
assertions of a suite's own numbers, one fixture line in a run case); `grep -c '^case_gate_' tools/loop-cases.sh`
counts four gate cases before this change; `grep -rn 'errors="' tools/loop/ tools/loop-cases.sh` names the one
hardcoded `errors="0"` the tally writes and nothing that reads it there; `grep -n 'LOOP-GATE' tools/loop/prompts/`
names one prompt line. Consumers that read the tally were re-read: `tools/loop/measure.sh:82`,
`.pi/skills/fix-loop/scripts/evidence-check.sh:91` (both take `tests=` then `failures=`, so a third attribute
after them is inert) and `tools/loop/worktree.sh:248` (already refuses a tally carrying a non-zero `errors`,
which is the half the fixed tally now feeds).

surface: 1 case added, 1 rewritten, 0 fixtures realigned — `grep -n 'n=1\|f=1' tools/loop/gate.sh` (two engine
lines before this change), `grep -n 'LOOP-GATE: tests' tools/loop-cases.sh` (three before), `grep -c '^case_gate_'
tools/loop-cases.sh` (four before); consumers re-read: `measure.sh`, `worktree.sh`, `evidence-check.sh`,
`tools/loop/prompts/worker.md`.
