# Design — the case waits for the line the phase prints

## The decision

`case_run_prescreenAnnouncesItselfBeforeItReturns` reads the run's console by waiting, within a 30 s bound, for
the first line the stand-in pre-screen prints while it runs, and takes every assertion of the case from that
reading. The rest of the case is unchanged: the phase line and its `started` shape, the stand-in's first line
present and its second absent, the kept `prescreen.log`, and the exit status being the pre-screen's own.

## The alternatives the root cause eliminated

- **Move the run's phase line earlier**, in front of the lock, the state write and the decision cap, so the fixed
  instant is met. Impossible: the lock and the decision cap can both refuse (`exit 5`) before the pre-screen is
  ever called — the decision cap is read at `tools/loop/run.sh:432-442`, in front of `prescreen_args` at
  `tools/loop/run.sh:453` — so a line announcing the pre-screen as started would name a phase that never ran. A
  phase line states a boundary the run has crossed; moving it in front of a refusal would make it state one it
  has not.
- **Widen the sample** (`sleep 4`, `sleep 10`). Impossible: the two windows bound each other. The stand-in holds
  3 s after its first line and the case asserts the second line is *absent*, so the reading has to land inside
  that hold, while the preamble it must also clear is unbounded — 0.628–0.760 s measured on a loaded host
  (`evidence/timing.log`), against a 4.140 s compile that lands in the same window when the core is cold. No
  fixed instant clears both ends of that pair on every host.
- **Compile the core before the window** (`core_ensure` in the case body, which is the convention for a case that
  starts the engine). Insufficient, and the measurement is why: it removes the compile from the window and leaves
  the run's own preamble — the lock, `state.sh init` and the decision cap's `list.sh --json`, whose tracker read
  is the one `java` process in it (`tools/issues/lib.sh:222`) — which is what the red this change starts from
  held the header for. It would also shorten the run's start-up for the case that measures the run's phases,
  which is the case measuring its own preparation.
- **Keep the fixed instant and delete the case.** Impossible: the shipped scenario's `**Case**:` line names it,
  and nothing else guards the streamed phase — `tools/loop-mutations.sh` record `34.1` (the stream replaced by a
  capture into the log) names this case as the only thing that must notice.

## Why the chosen form keeps the guard

The wait's condition is the line the pre-screen itself prints, which is printed after the phase line the run
writes before the call and while the call still runs. So the reading is taken at the earliest moment a person can
tell a streamed phase from a replayed one, and the two assertions that carry the claim are read after it:

| broken engine | what the case then reads | verdict |
|---|---|---|
| the phase's text never reaches the console (mutation `34.1`) | the wait expires with no such line | red — the first line is missing from the console |
| the whole output is captured and printed after the call returns (hand probe) | both lines appear in the one reading | red — the second line is present, `the text is streamed, not replayed` |

The 30 s bound is a bound on the case, not on the run: it is ~40× the preamble measured on this host (0.628–
0.760 s, `evidence/timing.log`), and it is a bound rather than a wait on the run's speed.

## Where the rule lives

The requirement whose `**Case**:` line names this case — *The run names its phases and the live view into it* —
gains one scenario stating that a case reading a phase line waits for it instead of reading at a fixed instant,
and that the wait must not soften the guard. The nearest owning rule is `guidelines/Build.md` section 6e,
"Wall-clock waits and the conversion measured" — *a test that must wait instead drives the component's seam and
awaits observable state* — and this change complies with its principle (the wait is on the run's own observable
output, not on a duration) while the mechanism that enforces it, `checkNoWallClockWaits`, is wired into Gradle
test-bearing modules only and binds no shell case. No guideline is edited: the loop's phase lines are stated by
`agent-issue-loop` and by no guideline (checked: `grep -n 'phase line' guidelines/*.md` prints nothing), and the
case's own comment block carries the measurement beside the code, which is where this repository keeps the
evidence for a case.

## Risk

- **A run that dies before the pre-screen** now costs the case its 30 s bound instead of failing at 1.5 s. Bounded
  and small: the case is one of ~208 in a suite that runs for minutes, and the failure message names the phase
  line it did not find.
- **A slower bound on a slower host** is the point of the change rather than a risk of it: the wait does not
  shorten with a faster host, and it is not read as evidence about the run's speed.
