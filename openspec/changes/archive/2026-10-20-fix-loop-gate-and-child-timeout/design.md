# Design — fix-loop-gate-and-child-timeout

## The root cause, and the two mechanisms it names

Measured, in this order:

1. `.pi/issue-loop/items/126/gate-results.txt` and `worker-report.md`: the configured gate
   (`tools/loop/loop.conf`, `LOOP_GATE_CMD`, `--rerun-tasks`) spent 30 min in `:app:kspMobileDebugUnitTestKotlin` at
   194 MB free RAM / 18 GB swap and never reached a suite; the retry died in
   `:app:testAutomotiveDebugUnitTest`. The **same 5680 tests** through `-PforceTests --no-build-cache`
   finished `BUILD SUCCESSFUL in 3m47s`. The worker phase timestamps span 21:19 → 22:42, i.e. 83 min.
2. `tools/loop/run.sh`, `run_child` (before this change): `( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1`
   in the foreground — no limit of any kind. The run's deadline lives in `state.sh next`, which the item loop
   calls between items, so during those 83 minutes no stop condition was ever evaluated.

So the defect is one mechanism with two faces: **a flag choice the host cannot pay for, and no bound on the
process that pays it.** Exactly one fix follows from that: take the gate invocation the host completes, and
make the recorded wall-clock of a child the run's own bound, recorded in the transcript the run already reads.

## The fix

- `loop.conf`: `LOOP_GATE_CMD="./gradlew test -PforceTests --no-build-cache"`, and `LOOP_CHILD_MAX_SECONDS=2700`
  beside the other budget defaults.
- `run.sh` `run_child`: the child is started with `set -m` in force for that one command (and turned off
  again), which makes a non-interactive shell's background job its own process group; a poll loop replaces
  the blocking wait; at the cap the group gets `TERM`, then `KILL` after a second; the transcript gets
  `LOOP-TIMEOUT: …` and the return status is `LOOP_CHILD_TIMEOUT` (124). Stdin of the child is `/dev/null`
  because a member of a background process group that reads a terminal is *stopped*, not run.

## Alternatives the root-cause evidence eliminated

**A. Keep `--rerun-tasks` and bound it more tightly (or let the worker retry once more).**
Eliminated by the measurement: the invocation did not reach a suite in 30 minutes at 194 MB free RAM, while
the cache-excluding invocation did the same 5680 tests in 3m47s. A tighter bound on a command that never
finishes converts a slow item into a blocked one; it does not produce a green gate. The cost was the flag,
not the number of attempts.

**B. Let the run's existing `--minutes` deadline end the item.**
Impossible by construction: `state.sh next` — the only place the deadline is compared — is called *between*
items, and `run_child` blocks inside one item. A 90-minute run with a 45-minute floor kept waiting through a
83-minute child; nothing in that path could ever have fired. A deadline that is only read at the top of the
loop cannot bound the body of the loop.

**C. Put the bound in the worker prompt only.**
Impossible: the child is print mode and unattended, its stream is silent until it exits (the reason commit
1fa8077 added the heartbeat), and a hung Gradle build cannot read a prompt, let alone obey it. The bound has to
live in the process that can signal the process — the run's script.

**D. Put `timeout 2700` into `LOOP_AGENT_CMD`, keeping the engine unchanged.**
Rejected, not merely less tidy: it bounds the child but it cannot write the one line the item keeps or return
a status the run can tell from a child's own failure, so the item would read `the worker process exited 124`
with a transcript that says nothing about why — the requirement is that the run *records* what it did. It would
also move an engine mechanic into a project value that every portability check and every prompt would have to
know about, and no self-test case could reach it without a real 45-minute child.

**E. Kill only the child's PID.**
Eliminated by the measurement's own mechanism: a Gradle gate leaves daemons and test workers behind, and the
18 GB of swap is theirs. Killing the direct process frees the wait but not the host, so the next item starts
into the same shortage. Hence the process group.

**F. Treat a timeout as `skipped` rather than `blocked`.**
No: nothing about the item was decided. A skip states a verdict about the item's eligibility; a timeout states
that the work could not be completed inside the budget. `blocked` is what the run's existing paths mean
(child exited non-zero, child wrote outside its tree), and it is what escalates the transcript to the owner.

## Blast radius and risk

Only the loop's engine, its prompt and its own configuration change; no app, native, Gradle or manifest file
is touched, so no build is run for this change (`guidelines/Build.md` §2 scope block — the delta compiles
nothing, and the structural check is the self-test gate for `tools/loop/**`, `tools/loop-selftest.sh`).

The one risk worth naming is the process-group kill: `set -m` for a single command could, if it failed
silently, leave the group unset and the kill would fall back to the child alone (the code carries that
fallback explicitly). The self-test's case proves the group path on this host; a host whose shell refuses
`set -m` degrades to the PID-only kill and still bounds the wait, which is the defect's symptom.

The second risk is the poll: it costs up to one second per child (86 cases, ~200 children in the suite) and
introduces no new state. A blocking `wait` cannot observe the cap, and a watchdog subshell that kills after
`sleep` could fire after the child was reaped, where the group id may be reused — the poll holds the unreaped
child, so the id cannot be recycled under it.

## Verification

- Red on HEAD: `bash tools/loop-selftest.sh --case 'run#childOutlastingTheWallClockIsBlocked'` with the
  pre-change `tools/loop/run.sh` fails (`the run was still waiting after 10 s on a child that outlasted its
  2 s cap`) — `evidence/red-on-head.txt`.
- Green on the fix: the same case passes and asserts the item's state is `blocked`, its note names the cap,
  the transcript carries `LOOP-TIMEOUT: the child exceeded 2 s`, and nothing is closed — `evidence/green-on-fix.txt`.
- The mechanical revert-check: mutation 23.1 in `tools/loop-mutations.sh` (the cap check replaced by `if false`)
  turns the case red and its restore turns it green; `bash tools/loop-mutations.sh --only '9.9 12.2 23.1'`
  reported `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`. Mutations 9.9 and 12.2 were re-pointed
  at the rewritten child start, because their patterns name the line this change had to change.
- The case alone on the rebased tree (`0205d83`): `PASS run#childOutlastingTheWallClockIsBlocked`, no stub
  left behind (`evidence/green-on-fix.txt`). The whole offline gate is the orchestrator's to run once on the
  merged tree — the one run made here was superseded (a second suite on the same host, 447 MB free) and is
  kept as `evidence/full-suite-superseded-under-load.txt` rather than presented as green.
- `openspec validate fix-loop-gate-and-child-timeout` and `openspec doctor`, both clean
  (`evidence/openspec.txt`).
- On-device: not applicable. No phone or car surface, no rendering, no log line and no coordinate is involved;
  the measurement this change is proved by is the offline self-test, not a device.
