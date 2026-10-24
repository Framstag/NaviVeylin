# Design — the case waits for the line the suite prints

## The decision

`case_gate_theSuitesOutputReachesTheConsoleWhileItRuns` reads the gate's console by waiting, within a 30 s bound,
for the first line the stand-in suite prints while it runs, and takes every assertion of the case from that
reading. The rest of the case is unchanged: the `gate: bash` announcement, the suite's first line present and its
summary absent, the rest of the stream including its stderr, the gate's `LOOP-GATE:` line and the tally file.

The waited-for line is printed by the suite, after the gate's own `gate: <command>` announcement and while the
suite still runs. It is therefore the earliest moment a reader can tell a streamed suite from a replayed one, and
the reading does not depend on how long the gate's start-up took.

## The alternatives the root cause eliminated

- **Move the gate's announcement in front of its start-up**, so the fixed instant is met. Impossible: the
  announcement is `gate: $cmd`, and `$cmd` is the command the gate owes this delta — the profile's suite map
  matched against the paths the change touches, de-duplicated. Printing it before the paths are read would name a
  suite the gate has not decided to run, and the loop's own rule is that a line states a boundary that is crossed.
- **Widen the sample** (`sleep 4`, `sleep 10`). Impossible: the two ends bound each other. The stand-in holds 3 s
  after its first line and the case asserts the summary is *absent*, so the reading must land inside that hold,
  while the start-up the reading must also clear is unbounded — 43–141 ms measured on this host
  (`evidence/timing.log`), 0.6 s+ on the host shape of `evidence/host-bound-probe.log` and past 1.5 s on the
  contended host of `evidence/red-before.log`. No fixed instant clears both ends of that pair on every host.
- **Prime the gate before the window** (run the path resolution and `git status` once before starting it, as the
  convention is for a case that pays a compile). Insufficient, and the measurement is why: it removes one of the
  start-up's costs and leaves the rest, and it shortens the gate's start-up *for the case that is measuring the
  gate's stream*, which makes the case a measurement of its own preparation.
- **Keep the fixed instant and delete the case.** Impossible: the shipped scenario's `**Case**:` line names it,
  and nothing else guards the stream — `tools/loop-mutations.sh` record `46.1` (the stream replaced by a capture
  into the log) names this case as the only thing that must notice.

## Why the chosen form keeps the guard

| broken gate | what the case then reads | verdict |
|---|---|---|
| the console never carries the suite's text (mutation `46.1`) | the wait expires with no such line | red — the first line is missing from the console, after the 30 s bound (`evidence/mutation-46.1.log`) |
| the whole output is captured and printed after the command returned (hand probe) | both the suite's line and its summary appear in the one reading | red — `the output is streamed, not replayed` (`evidence/host-bound-probe.log`, run 3) |
| the gate streams (`tee`) | the first line is read inside the suite's 3 s hold, the summary is not there yet | green — plain, repeated, under contention, and on a host whose start-up was inflated (`evidence/green-after.log`, `green-repeat.log`, `green-contended.log`, `host-bound-probe.log`, run 2) |

The 30 s bound is a bound on the case, not on the run: it is ~200× the start-up measured on this host
(43–141 ms, `evidence/timing.log`), and it is a bound rather than a reading of the run's speed.

## Where the rule lives

The requirement whose `**Case**:` line names this case — *The gate streams a suite's output while it runs* — gains
one scenario stating that a case reading a suite's line waits for it instead of reading at a fixed instant, and
that the wait must not soften the guard. The nearest owning rule is `guidelines/Build.md` §6e, *Wall-clock waits
and the conversion measured* — a test that must wait instead drives the component's seam and awaits observable
state — and this change complies with its principle (the wait is on the gate's own observable output, not on a
duration) while the mechanism that enforces it, `checkNoWallClockWaits`, is wired into Gradle test-bearing
modules only and binds no shell case. No guideline is edited: the loop's shell cases are stated by
`agent-issue-loop` and by no guideline, and the case's own comment block carries the measurement beside the code,
which is where this repository keeps the evidence for a case.

## Risk

- **A gate that dies before it announces the suite** now costs the case its 30 s bound instead of failing at
  1.5 s. Bounded and small: the case is one of `tools/loop-selftest.sh`'s 209, and the failure message names the
  line it did not find. Measured, this is what the mutated gate costs today: `46.1` reports `RED` after the bound
  rather than after 1.5 s (`evidence/mutation-46.1.log`).
- **A slower bound on a slower host** is the point of the change rather than a risk of it: the wait does not
  shorten with a faster host, and it is not read as evidence about the gate's speed.
- **The case is green on this host both before and after the change** when the host is left alone
  (`evidence/red-before.log`, step 1). The change is justified by the *bound*, not by an observed flake, and the
  red it prevents is reproduced deliberately rather than waited for (`contend.sh`).
