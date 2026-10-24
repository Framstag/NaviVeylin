# What each file here is, taken 2026-10-24

All runs are offline shell runs (`bash tools/loop-selftest.sh`, `bash tools/loop-mutations.sh`, `bash
tools/loop/gate.sh`) inside this worktree (`fix/issue-287-the-prescreen-case-samples-a-fixed`), on the host the
item was filed from. No token, no network, no device, no Gradle suite (nothing compiled changed).

| file | what it is |
|---|---|
| `red-before.log` | `bash tools/loop-selftest.sh --case 'run#prescreenAnnouncesItselfBeforeItReturns'` on the base `c4c727fb` (tree `.pi/worktrees/t288c`), before the fix — `FAIL`, `1 of 1 cases failed`, with the console the case read in the message; this reading's console is **empty**, the shape the item's own evidence quotes |
| `green-after.log` | the same command in this change root after the fix — `PASS run#prescreenAnnouncesItselfBeforeItReturns` |
| `green-repeat.log` | the same command three more times, in this change root, each `PASS` (the only file here with an in-file timestamp: `2026-10-24T09:50:42Z`, `09:50:54Z`, `09:51:09Z`) |
| `timing.log` | how long after the run starts the phase line reaches the console, on three trees, with the harness (`timing-probe.sh`) that took it |
| `timing-probe.sh` | the harness: it builds a throwaway project, replaces the pre-screen with the case's stand-in, starts `run.sh` and polls the console for the header and for `^loop: pre-screen · ` |
| `compile-cost.log` | the cost of the compile the driver pays inside the old reading's window |
| `mutation-34.1.log` | `bash tools/loop-mutations.sh --only 34.1` — the stream to the console replaced by a capture into the log: the case still goes red, and the restore goes green (the harness's own log carries the verdict, not the case's assertion text; the assertion that fired is the first-line one, and the substitution is quoted in `tools/loop-mutations.sh`) |
| `replay-probe.log` | a hand probe of the *other* broken shape: `run.sh` captures the pre-screen's whole output and prints it after the call returns (`LOOP_ENGINE=<probe>/tools/loop`) — the case goes red with `the text is streamed, not replayed` |
| `structural-checks.log` | the checks that stand in for a build for a delta that compiles nothing: `bash -n tools/loop-cases.sh`, `openspec validate … --strict`, `openspec doctor`, `tools/check-doc-routes.sh`, `evidence-check.sh` |
| `gate.log` | the delta's gate: `bash tools/loop/gate.sh --base c4c727fb` |
| `review.md` | the independent read-only review of the diff against the delta's scenarios |

## What is not asserted

- **The red reading is one invocation, and its delay is not pinned to one mechanism.** The two costs that push
  the phase line past a fixed 1.5 s sample — the driver's cold `javac` (4.140 s) and the run's own started-after
  preamble (0.628–0.760 s measured) — cover two different invocations; `red-before.log` is the first shape, and
  an earlier reading of the same command in this session (a console holding the header plus the
  `run: the pre-screen runs next …` line and no phase line, the shape the item reports from its full-suite run)
  is not retained as a file. The fix is the same under either cost, which is why the reading is not the verdict.
- **The added scenario's 30 s bound has no assertion of its own**: the wait's body is the assertion, and a phase
  that never prints fails the first-line assertion after the bound expires. No case exercises the path the design
  describes for a run that refuses before the pre-screen.
- **The writer case is not re-measured here.** `run#aWriterTheRunCanPlaceBlocksTheItem` is #288's red: its red
  reading is on record in this tree
  (`openspec/changes/archive/2026-10-23-fix-loop-label-write-failure-is-recorded/evidence/baseline-main-red.log`)
  and its green one is this change's own gate (`PASS run#aWriterTheRunCanPlaceBlocksTheItem`, `gate.log`).
- **No Gradle or device check.** The delta changes one shell case and one spec scenario: nothing compiles, no
  flavor is affected, and the loop's suite is the whole gate (`guidelines/Build.md` §2, the scope block).
- **`gate#theSuitesOutputReachesTheConsoleWhileItRuns` is not touched.** It reads a *suite's* console at a fixed
  instant of the same shape and is green in this change's gate; the requirement this change adds is stated for
  the run's phase line. It is filed as item #296 rather than folded in here.
