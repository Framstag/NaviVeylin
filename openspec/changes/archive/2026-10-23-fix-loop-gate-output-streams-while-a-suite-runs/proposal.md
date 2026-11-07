# fix-loop-gate-output-streams-while-a-suite-runs

## Why

The gate's console is silent for as long as the suite it runs, however long that is.

**Observed.** A gate run of change #262 wrote `gate: ./gradlew test -PforceTests --no-build-cache`, then
`gate: tools/loop-selftest.sh`, and nothing else — the log stayed at two lines while the self-test ran past
eleven minutes (`tools/loop-selftest.sh` at 666 s elapsed, `ps` sampled 2026-10-23 05:34). The per-case
`PASS`/`FAIL` lines, the Gradle progress lines and the failure text of a red suite were all invisible until
the command exited.

**Root cause, one sentence:** the gate captures each suite's whole output into a variable
(`out=$($cmd 2>&1)`, `tools/loop/gate.sh:111` on HEAD) and replays only what it re-derives from it, so the
console carries nothing while the suite runs and the tally keeps only the last three lines of what it held.

**Red test on HEAD, before the fix.** One case added to `tools/loop-cases.sh` and run against the gate restored
from HEAD (`git show HEAD:tools/loop/gate.sh`), transcript `evidence/red-HEAD.log`, tally `evidence/red-HEAD.xml`
(`tests="1" failures="1"`, 2026-10-23T09:15Z):

```
  the suite's own first line reaches the console while the suite runs: gate: bash /…/case.daFlIs/suite.sh: 'case: a note is read' not found in: gate: bash /…/case.daFlIs/suite.sh
FAIL gate#theSuitesOutputReachesTheConsoleWhileItRuns
```

— the log holds the gate's own announcement of the suite and nothing the suite wrote, 1.5 s into a suite that
prints immediately and works for three seconds. The second case,
`gate#aFailingSuiteIsReadFromTheSameStream`, passes on HEAD by design: it guards the half of the rule that a
`tee` could break (the tally and the console are one observation, and the verdict stays the suite's exit
status), exactly as `run#prescreenAnnouncesItselfBeforeItReturns` guards the pre-screen's own status.

**Owning capability and guideline section.** The capability is `agent-issue-loop`, and the delta adds one
requirement to it, "The gate streams a suite's output while it runs". The guideline that owns the loop's
mechanics is `guidelines/Backlog.md`, section "7. The loop", whose bounds paragraph states how long one child may run
(`LOOP_CHILD_MAX_SECONDS`, 45 min) — the wall clock this log never moved inside.

**The mechanism is not new here.** The run already streams the pre-screen the same way
(`tools/loop/run.sh:442`, `bash … | tee "$prescreen_log"`), and the requirement
"A run reports the wait it cannot get from the child" records why: the output is streamed rather than replayed,
and the same bytes are kept for a path that reads them afterwards. The gate now does for a suite what the run
does for a child phase.

## What Changes

- `tools/loop/gate.sh` — each suite's output is `tee`d to one file while it runs, so the console moves at the
  suite's own pace; the summary, the tally and the failure text are parsed from that same file, and the status
  is the suite's own (`${PIPESTATUS[0]}`, never the `tee`'s). `--quiet` keeps the console for the verdict and
  writes the same file.
- `tools/loop-cases.sh` — two cases: the suite's first line reaches the console before it exits
  (`gate#theSuitesOutputReachesTheConsoleWhileItRuns`) and a failing suite's numbers, failure text and exit
  status survive the stream (`gate#aFailingSuiteIsReadFromTheSameStream`).
- `tools/loop-mutations.sh` — record `46.1` restores the command substitution and names the case that goes red.
- `guidelines/Backlog.md`, section "7. The loop" — the bounds paragraph says the gate streams a suite's output and that
  the tally is read from that stream.

**Surface, counted before the plan was fixed.** `grep -rln 'out=\$(\$cmd 2>&1)' tools/ guidelines/` names two
files (`tools/loop/gate.sh`, `tools/loop-cases.sh`), and `grep -rln '2>&1 | tee' tools/` names the precedent in
`tools/loop/run.sh`. Six cases in `tools/loop-cases.sh` drove the gate, two of them through `--plan` only and
none through a suite that writes more than its summary. The worker prompt states no output rule and is
untouched.

surface: 2 cases added, 0 rewritten, 0 fixtures realigned — `grep -rln 'out=\$(\$cmd 2>&1)' tools/ guidelines/` (two files), `grep -rln '2>&1 | tee' tools/` (the precedent, `tools/loop/run.sh`), `grep -c case_gate_ tools/loop-cases.sh` (six before this change); engine side: one loop in `gate.sh`, one mutation record.
