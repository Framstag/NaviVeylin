# Proposal — fix-gate-step-bound

## Why

One defect, verified on the run's base `abb6a8c4`, issue #311: **a gate step has no clock of its own, so one suite
that does not return spends the child's whole wall budget and the item is ended with its work uncommitted — even
when its gate was already green.**

**Root cause (one).** The gate ran each step as one unbounded unit: `run_step()` (`tools/loop/gate.sh:184` on the
base) started the command, waited for it with `wait "$step"` (`:195`) and returned its status, and nothing inside the
gate compared a step's wall clock against any recorded value. The two clocks that exist are outside it — the run's
`LOOP_CHILD_MAX_SECONDS` (`tools/loop/loop.conf:52`, 2700 s) around the child, and the self-test's
`LOOP_CASE_MAX_SECONDS` (`tools/loop/loop.conf:104`, 900 s) around one case — so a *step* could consume the child's
entire budget, and the run could only answer by ending the child, which commits nothing.

**Verified, not quoted from the tracker.** Measured on this base (2026-10-26):

```
$ git show abb6a8c4:tools/loop/gate.sh | grep -n 'run_step\|wait "\$step"\|tee "\$stream"'
184:run_step() { # <command> → the command's status
195:  wait "$step"
212:  run_step "$cmd"
$ git show abb6a8c4:tools/loop/loop.conf | grep -n 'LOOP_CHILD_MAX_SECONDS\|LOOP_CASE_MAX_SECONDS\|LOOP_GATE_STEP_MAX_SECONDS'
52:LOOP_CHILD_MAX_SECONDS=2700
104:LOOP_CASE_MAX_SECONDS=900
$ grep -rn 'LOOP_GATE_STEP_MAX_SECONDS' guidelines/ openspec/specs/ | wc -l
0
```

So: a step waits without a clock, the two bounds that exist sit outside the gate, and no key, guideline sentence or
requirement names a per-step bound (`evidence/surface-head.log`).

**The red test** — `gate#aStepIsBoundedAndTheOverrunNamesIt`, a throwaway project whose suite prints one line and
never returns, in a profile whose step bound is two seconds (`evidence/red-on-head.log`, tally
`evidence/red-on-head.xml`, `tests="1" failures="1"`):

```
$ bash tools/loop-selftest.sh --case gate#aStepIsBoundedAndTheOverrunNamesIt
  the step ran past its bound: still inside the gate 20s into a suite that never returns, with the bound set to 2s
FAIL gate#aStepIsBoundedAndTheOverrunNamesIt
loop-selftest: 1 of 1 cases failed
```

The case is red because the gate returns for nobody: on the base the step waits on the suite, not on a bound, and the
case's own watchdog is what ends the wait — 20 s into a suite that would run for ever.

**Owning capability and guideline section.** The behaviour is a new requirement of `agent-issue-loop`, beside *The
gate streams a suite's output while it runs* and *A suite that reported no summary invents no number*
(`openspec/specs/agent-issue-loop/spec.md`). The prose that owns the loop's three clocks in one place is
`guidelines/Backlog.md`, section "The loop" — the paragraph that already carries the child's cap, the case bound and the
gate's stream (`guidelines/Backlog.md:407`); no other guideline states how long a step may run, so no other section
is rewritten.

surface: 1 case added, 0 rewritten, 17 gate cases re-run green, 2 mutation records realigned + 1 added, 1 requirement
added, 1 profile key added, 1 guideline paragraph (and one stale clause in the paragraph beside it) — the counts are
`git show abb6a8c4:tools/loop-cases.sh | grep -c '^case_gate_'` (16 cases at the base, 17 after the new one),
`git show abb6a8c4:tools/loop-mutations.sh | grep -c '@-@tools/loop/gate.sh@'` (9 records quote the engine, 2 of them
a line inside `run_step`), and `grep -rn 'LOOP_GATE_STEP_MAX_SECONDS' guidelines/ openspec/specs/ | wc -l` (0 places
mention the bound the change adds) — `evidence/surface-head.log`.

## What changes

- `tools/loop/gate.sh` — a step is bounded. `run_step` starts the command under `set -m` (its own process group),
  reads the step's status from a record the step itself writes (a `wait` on a reaped pid answers nothing, issue
  #227), polls that record against `LOOP_GATE_STEP_MAX_SECONDS`, and ends the step's whole group with
  `loop_end_group` when the bound is reached. The overrunning step is named three times: the console line gains
  `— the step overran its bound of <n>s and was ended`, a `LOOP-GATE-STEP: <command> exceeded <n> s` line is appended
  to the stream the tally is written from, and the step counts as an error (no case invented), so the gate exits
  non-zero naming the step. The bound in force is printed once, before the first step (`LOOP-GATE-STEP-BOUND: <n>s
  per step …`), beside the profile line.
- `tools/loop/loop.conf` — the key `LOOP_GATE_STEP_MAX_SECONDS=1800`, with the measurement it is set against (~5× the
  slowest step this host recorded: 345 s for the whole build-and-test gate, `guidelines/Build.md` §2c, 2026-10-21;
  273 s for all 223 self-test cases the same week) and the 900 s it leaves of a 2700 s child.
- `tools/loop-cases.sh` — the case `gate#aStepIsBoundedAndTheOverrunNamesIt`, in the declared set, the dispatch table
  and its `# files:` declaration.
- `tools/loop-mutations.sh` — `63.2` makes the bound's guard never fire (the invariant, reddening the new case);
  `63.1` and `46.1` are realigned to the lines `run_step` now carries (the launch line, the follower's line), their
  invariants unchanged.
- `guidelines/Backlog.md`, section "The loop" — a paragraph for the step bound, its default and its measurement, beside the child's
  cap and the case bound; and the stale `tee`d clause in the paragraph beside it corrected to what the gate does now
  (the stream is the file the suite writes, issue #315).
- `openspec/specs/agent-issue-loop/spec.md` — one added requirement with two scenarios.

## Spec

`ADDED` **A gate step is bounded, and the step that overran is named** — the bound is `LOOP_GATE_STEP_MAX_SECONDS`
(default `1800`), measured from the step's start; a step that reaches it is ended with everything it started, named on
the console and in its own tally, counted as an error, and invented as no case; a step that returns inside the bound
keeps its own status and numbers. Body: 444 characters against the validator's 500. Scenarios: *A step that does not
return is ended and named* (`gate#aStepIsBoundedAndTheOverrunNamesIt`) and *A step that returns inside the bound keeps
its own status* (`gate#aFailingSuiteIsReadFromTheSameStream`).

No existing requirement changes: *The gate streams a suite's output while it runs* keeps its three scenarios and its
body, and the numbers a step reports are still the suite's own (*A suite that reported no summary invents no number*)
— a step the bound ended reports no summary, so it lands there as an error, which is what the new requirement says.

## Impact

`tools/loop/gate.sh`, `tools/loop/loop.conf`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `guidelines/Backlog.md`,
the spec delta. No compiled file, so the Gradle gate is owed only because the tree's diff against `origin/main` still
carries two earlier items' committed sources — the change's own files owe the shell suites the profile maps. Rollback:
revert the branch; the key disappears with the guard that reads it, and a gate started by an older engine writes the
same tallies.

## Open

The item's second candidate repair — *compare the step's measured baseline against the child budget and raise
`LOOP_CHILD_MAX_SECONDS` before the child starts* — is not taken, and the evidence eliminates it rather than ranking
it: the same gate measured 8m25s, 9m46s and 5m45s on three occasions and the same 5 680 tests took 3m47s on a quiet
host and spent 30 min at 194 MB free RAM (`guidelines/Build.md` §2c, and the measurement behind `LOOP_MIN_FREE_MIB`
in the profile), so a budget derived from the last run's number is a guess about the next one; and it answers nothing
for a step that never returns, because a `sleep`-shaped hang has no baseline to compare. A bound makes the overrun
*visible* — which is the other half of the item's title — and a raised budget would only move the wall it hits.
