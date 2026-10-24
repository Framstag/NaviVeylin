# The pre-screen case reads the phase from the stream instead of at a fixed instant

## Why

`run#prescreenAnnouncesItselfBeforeItReturns` — the case the shipped requirement names for *The pre-screen is
announced and reported before it returns* — fails on the tree this change starts from, and it fails on an engine
that satisfies the requirement. Red on `c4c727fb` (the pushed tip of `fix/issue-288-…`, this change's base) and on
main's own tip; reproduce with

```
bash tools/loop-selftest.sh --case 'run#prescreenAnnouncesItselfBeforeItReturns'
```

which reports `1 of 1 cases failed` with
`the phase is announced on the console: 'loop: pre-screen' not found in: agent-issue-loop: items=1 minutes=90 …`
(`evidence/red-before.log`) — the console the case read held the run's header and nothing else. Open item: #287.

**The engine is not the defect.** Measured on this host, the run prints `loop: pre-screen · …s · started` 0.628 s,
0.741 s and 0.760 s after it starts, on `ea194c38` (the commit that added the line, #191), on `e12cf971` and on
this change's base (`evidence/timing.log`) — three trees, one reading each, taken by polling the console for the
line. The whole of that interval is the run's start-up: the lock, `state.sh init` and the decision cap's
`list.sh`, a JVM each, none of which is the pre-screen.

**The defect is the reading.** The case started `run.sh` in the background and read the console once, 1.5 s later:

```
( LOOP_ENGINE="$tools/loop" run_loop "$p" --items 1 --minutes 90 --review off ) > "$log" 2>&1 &
pid=$!
sleep 1.5
out=$(cat "$log")
```

Two costs sit in front of the line, and neither is the run the case means to measure:

- **The driver compiles the issue tooling's Java core inside the window.** `run_loop` calls `core_ensure`, and the
  case backgrounds the whole `( run_loop … )` subshell — so on an invocation whose selected cases have not asked
  for the core yet, `javac` runs inside the 1.5 s window and prints nothing to it. Measured 2026-10-24:
  `javac --release 17 -d <classes> tools/issues/core/*.java` for the tooling's ten core files costs 4.140 s wall
  (`evidence/compile-cost.log`). Running the case alone therefore read a console the compile had not let the run
  reach — the shape the item's own record describes, where the console was read at 1.5 s and
  `'loop: pre-screen' not found in:` was printed with nothing after the colon (the tracker item's
  *Observed*/*Evidence* sections).
- **Once the core exists — any suite run — the run's preamble remains.** The phase line lands 0.628–0.760 s in
  (`evidence/timing.log`), after the lock, the state write and the decision cap; the sample at 1.5 s leaves under
  a second of margin on a loaded host, and the red this change starts from held the header plus the `run: the
  pre-screen runs next …` line and no phase line (`evidence/red-before.log`).

So the case turned the host's start-up cost — a compile that belongs to the driver, a state write and a decision
count that belong to the run — into a verdict about the run's streamed phase. That is the whole defect, and the
single fix is to read the claim where it is made: wait, within a bound, for the line the pre-screen itself prints
while it runs, and keep every other assertion of the case unchanged.

**The guard is not weakened, and that is measured too** (the two forms a broken stream takes):

- `tools/loop-mutations.sh --only 34.1` — the shipped record that replaces the run's `| tee "$prescreen_log"`
  with a capture into the log, so the console never carries the pre-screen's own text — still turns the case red
  (`evidence/mutation-34.1.log`).
- A hand probe with a **true capture-and-replay** (`prescreen_out=$(bash prescreen.sh …)` and the text printed
  after the call returns, `evidence/replay-probe.log`) turns it red with
  `and the line it has not printed yet does not: the text is streamed, not replayed` — both records reach the
  console at once, which is exactly the shape the case exists to refuse.

**And the second red the item names is not this change's.** `run#aWriterTheRunCanPlaceBlocksTheItem` is red on
main — recorded in the tree as
`openspec/changes/archive/2026-10-23-fix-loop-label-write-failure-is-recorded/evidence/baseline-main-red.log`,
`a write the run can place in the child's own tree decides the item: expected 'blocked', got 'done'` — and green
on this change's base, where this change's own gate re-runs it (`PASS run#aWriterTheRunCanPlaceBlocksTheItem`,
`evidence/gate.log`). It is the defect #288 fixed, and this change is stacked on that fix. Both reds of #287 are
therefore green in this tree, each by the change that owns it.

## What Changes

- `tools/loop-cases.sh`, `case_run_prescreenAnnouncesItselfBeforeItReturns`: the single `sleep 1.5` reading is
  replaced by a bounded wait (`for _ in $(seq 600)` with `sleep 0.05`, so 30 s) for the stand-in pre-screen's own
  first line, after which the case's assertions run unchanged — the phase line and its `started` shape, the
  first line present, the second line absent, the kept bytes, and the exit status being the pre-screen's.
  The comment above the case records why the reading waits and what it cost not to.
- `openspec/specs/agent-issue-loop/spec.md` after archive: one `MODIFIED` requirement — *The run names its phases
  and the live view into it* — with one scenario added (*The phase is read from the stream, not at an instant*),
  the requirement's body and its other three scenarios repeated verbatim.

## Capabilities affected

`agent-issue-loop` — one scenario added to an existing requirement; no requirement text, no engine behaviour and
no interface changes. Additive: the run's phases, their format, the report, the state file and the exit statuses
are untouched; the case still asserts all of them.

Rollback path: revert the change — the case reads the console at a fixed instant again and is red again on a cold
host, which is the state #287 reports.

## Impact

| path | what changes |
|---|---|
| `tools/loop-cases.sh` | the reading in `case_run_prescreenAnnouncesItselfBeforeItReturns`, and its comment |
| `openspec/specs/agent-issue-loop/spec.md` | the added scenario, after archive |

No engine script, no mutation record, no profile and no `guidelines/**` file is touched: the defect is in how the
case reads an artifact the run already prints, and the rule the case states is owned by the requirement whose
`**Case**:` line names it. No `:app`, `:auto`, `:core`, `buildSrc`, native or Gradle path is touched — the delta
compiles nothing, so no Gradle suite is owed (`guidelines/Build.md` §2, the scope block); a delta to
`tools/loop-cases.sh` is a core file of the loop's suite, so the suite the gate owes is its whole set.

## Not in this change

- The pre-screen's phase line, the stream, the kept `prescreen.log` and the exit-status rule: unchanged, and
  guarded by the same case as before.
- `gate#theSuitesOutputReachesTheConsoleWhileItRuns` reads its suite's console at a fixed instant of the same
  shape, and it reads a *test suite's* stream rather than the run's phase line. It is green in this change's gate
  (`PASS gate#theSuitesOutputReachesTheConsoleWhileItRuns`, `evidence/gate.log`) and is therefore not this item's
  red; the requirement this change adds is stated for the run's phase line, which is what the case guards. The
  same fragility is filed as its own item (#296) rather than folded in here.
- The compile the driver pays inside a timed window (`core_ensure` called from the backgrounded subshell) is a
  property of `run_loop`, not of this case: a case whose reading waits for an observable does not care, and one
  that measured the driver's cost would be measuring the wrong thing.
