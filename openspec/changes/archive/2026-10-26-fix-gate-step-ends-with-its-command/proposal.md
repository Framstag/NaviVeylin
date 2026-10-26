# Proposal — fix-gate-step-ends-with-its-command

## Why

One defect, verified on the run's base `6e8b2889`, issue #315: **a gate step runs on past its suite's verdict, so a
supervised gate loses up to `LOOP_CASE_MAX_SECONDS` (900 s) of the caller's budget with nothing of the suite
running.**

**Root cause (one).** The step's output is a *pipe* into `tee` — `$cmd 2>&1 | tee "$stream"` at
`tools/loop/gate.sh:166` on the base — and the step's wall clock is taken after that pipeline
(`took=$((SECONDS - t0))`, `:170`). A pipe reaches EOF only when *every* writer that inherited the write end has let
go of it, so the step ends when the last process the suite left behind dies, not when the suite does. The suite the
item measured carries a per-case bound whose watcher is a `sleep` (`tools/loop-selftest.sh:493`, `LOOP_CASE_MAX_SECONDS`
in `tools/loop/loop.conf:104`); the watcher's own stdio is `/dev/null` (`:498`), but everything else a case starts
inherits the suite's stdout, and one such writer alive at the suite's exit holds the pipe open.

**Verified, not quoted from the tracker.** Measured on this base (2026-10-26):

- `git show 6e8b2889:tools/loop/gate.sh | grep -n 'tee "\$stream"\|PIPESTATUS'` → `166:    $cmd 2>&1 | tee "$stream"`,
  `167:    rc=${PIPESTATUS[0]}`; the clock is `:170`. `evidence/surface-head.log`.
- The red case reproduces it against a throwaway project whose suite states its verdict and leaves one writer
  behind, without touching the loop's own suites: `evidence/red-on-head.log`.

```
$ bash tools/loop-selftest.sh --case gate#aStepEndsWithItsCommandNotWithItsWatchers
  the step ran on after its command exited: still inside the gate 8s into a 20s writer that command left behind
FAIL gate#aStepEndsWithItsCommandNotWithItsWatchers
loop-selftest: 1 of 1 cases failed
exit=1
```

Its tally `evidence/red-on-head.xml` (`tests="1" failures="1"`) is the gate's own derivation from that summary line
(`loop-selftest: 1 of 1 cases failed`), copied in because the red run was taken outside the gate.

surface: 1 case added, 0 rewritten, 3 neighbouring cases re-run green, 1 mutation record rewritten + 1 added, 1
requirement modified in the spec, 1 engine site — `git show 6e8b2889:tools/loop/gate.sh | grep -n 'tee "\$stream"\|PIPESTATUS'`
(2 lines, one step), `grep -n 'case_gate_.*Stream\|case_gate_theSuitesOutput\|case_gate_theSummaryNames\|case_gate_aStepEnds' tools/loop-cases.sh`
(4 cases, 1 of them new), `grep -n 'the stream that carries\|follow_stream' tools/loop-mutations.sh` (2 records).

**Owning capability and guideline section.** The behaviour is the requirement *The gate streams a suite's output while
it runs* (`openspec/specs/agent-issue-loop/spec.md`), and the gate command a change runs is the row
`guidelines/Build.md` §3 carries for it (`guidelines/Build.md:394`). No guideline text asserts the behaviour that
changes — no document states how a step ends or how its wall clock is taken — so no guideline is rewritten; the spec
delta is the whole normative half.

## What changes

- `tools/loop/gate.sh` — a step stops being a pipeline. The command writes `$stream` itself, the console reads that
  file through `follow_stream` (a `tail -c +N` follower that prints what the file gained since its last pass, byte
  for byte, and ends once the step's command has ended *and* the file is drained), and the step's status and wall
  clock are the command's own. A writer that outlives the command holds a file open, which holds nothing else.
- `tools/loop-cases.sh` — the new case `gate#aStepEndsWithItsCommandNotWithItsWatchers`: a suite that prints its
  verdict, starts a 20 s writer, and exits; the gate must return in under 8 s and its own line must carry that
  number, and the tally must still be read from the stream the step wrote.
- `tools/loop-mutations.sh` — record `63.1` puts `| tee` back in the command's place, so the new case reads a step
  that is still running after its command exited; record `46.1`, which quoted the pipe, quotes the follower
  instead (its invariant, that the console sees the output while the suite runs, is unchanged).
- `openspec/specs/agent-issue-loop/spec.md` — the requirement *The gate streams a suite's output while it runs* gains
  the step's end and its clock, keeping both of its scenarios verbatim and adding the one the new case names.

## Spec

`MODIFIED` **The gate streams a suite's output while it runs** — the body gains "A step SHALL end with the command it
ran, not with a writer that outlived it" and stays inside the validator's 500-character limit (412 characters); the
scenarios *A suite's output reaches the console before the suite exits* and *The console and the tally are one
observation* are repeated verbatim, and *A step ends with the command it ran* is added with
`gate#aStepEndsWithItsCommandNotWithItsWatchers`.

No requirement about the wall clock changes: *A suite run selects what it was asked for and the gate records what it
cost* says the gate records each suite's wall clock in seconds, and a clock that measured the suite's leftovers was
not the suite's — the requirement's own scenario (`gate#theSummaryNamesWhatEachSuiteCost`) is what the fix keeps
true.

## Impact

`tools/loop/gate.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`, the spec delta. No compiled file, so no Gradle
build: the gate is the shell suites the pathspec owes (`tools/loop/**` owes the loop's own case set, the portability
check, the issue tooling and the mutation lists). Rollback: revert the branch; nothing outside the loop's engine
reads the stream's shape, and a tally written by an older gate is read the same way.

## Open

The item's two other candidate repairs — give the capture a pipe only the command's process group can hold, or make
the bound's watcher inherit nothing — are not taken: the watcher already sets its own stdio to `/dev/null`
(`tools/loop-selftest.sh:498`), so the leak is some *other* writer a case started, and no shape of pipe is safe
against a writer the gate cannot see. The file removes the question instead of narrowing it.
