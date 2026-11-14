# A signalled case ends on its own clock, and stages the signal it sends

## Why

`run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` ends a run with `SIGHUP` and asserts the stop the run recorded
(`tools/loop/tests/loop-cases.sh:14288`). In a pooled suite it did one of two things instead of deciding that
(issue #860, measured 2026-11-11 against the engine at `8361c0fe`):

- **it failed on a clock it does not control** — a 16-case pool over the engine's own files ran 245 of 246 green and
  this one red with `a run ended by HUP reports the signal it took: expected '129', got '0'`, because the run had
  already ended its item by the time the case signalled it, so the HUP was delivered to nothing and the run exited
  `0` by itself;
- **it never returned at all** — `--jobs 4` over the whole 413-case set left it in flight ten minutes later, its own
  `run.sh`, its stub child and the adapter above it still alive, so the case's stream was held open and the driver
  waited for an EOF that could not come. The suite was ended by hand, and the case's record carries the `137` of that
  `SIGKILL` rather than a verdict of its own.

Both are the same two gaps. The case **signals without staging**: it reads `run.ownerPid` and sends the HUP without
requiring the item to have answered, so whether the run is still there to take the signal is a race against the
child's own success. And the case **waits on the run with no clock of its own**: `wait "$first"` returns when the run
ends, and when the run does not end nothing answers until the suite's own bound — `LOOP_CASE_MAX_SECONDS`
(`tools/loop/loop.conf:377`, `900`) times the pool's width (`tools/loop/tests/loop-selftest.sh:196-198`), which is
four hours in the 16-case pool this was measured in.

Why it matters more than a red case: a `tools/loop/**` delta owes this suite, so every engine change pays the race,
and a gate that cannot end is worse than a gate that fails — the run that pays for it has no verdict to read and the
step bound is what finally ends it. The child that outlives its case is the second half: the driver's own case sweep
(`driver#aCaseThatLeavesAProcessBehindIsSweptWithIt`) cannot run, because the relay waits for the case's stream
first. Alone the case passes — 4 of 4 on the changed tree and 4 of 4 on `8361c0fe` — so the population, not the
tree, is what made it fatal. The three runs are the item's; the one that survives as a file is
`openspec/changes/archive/2026-11-11-fix-a-cut-child-is-continued-by-its-run/evidence/pool-head.log` (the case in a
four-way pool on the unmodified engine, `--case 'run#a*' --jobs 4`: 51 `PASS`, no verdict for this one).

## What Changes

1. **The HUP case stages what it signals** (`tools/loop/tests/loop-cases.sh:14324`): before `kill -HUP "$owner"` it
   asserts the item has answered nothing, so the HUP is the only thing that can end the run. The two facts it reads
   are both stages and not races — `run.ownerPid` is written after the traps are installed
   (`tools/loop/run.sh:718-721`, `:730`) and the stub child writes its pid inside its own action, before it blocks on
   the case's fifo (`tools/loop/tests/fixtures/pi:106`, `:114-118`).
2. **The wait is the case's own clock** (`:1246-1261`): `CASE_WAIT_TICKS=300` at `0.1 s` per tick — 30 s, thirty times
   the `1 s` `TERM`→`KILL` grace a run's trap gives its child (`guidelines/Backlog.recipes.md:724`) — and
   `wait_for_pid_end`, which keeps the status in `WAIT_PID_STATUS` because `wait` has to run in the case's own shell.
   A run that outlives the ticks is named with the process, the stop and the item the case read, then released and
   ended (`:14330-14337`), so nothing of it holds the case's stream when the case answers. The three early exits the
   rewritten case already had now end the run they started the same way (`wait_for_pid_end … 100 || kill -9`).
3. **A case stages the race deterministically** (`:14362`): `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`
   signals a pid that has already ended, so the HUP reaches nothing and the run goes on. The case's own wait must
   answer `124` inside its ticks, the run's record must still be empty, and the release-then-end that follows must
   end the run through its own trap — the item recorded `blocked`. That is the measured red of #860 on purpose, and
   it fails wherever the helper is absent.
4. **Two registry entries** (`:240`, `:958`) and one `# files:` line that now names
   `tools/loop/tests/fixtures/pi`.
5. **The rule, where the backlog routes it**: `guidelines/Backlog.recipes.md:746-753` (§7b) — the suite's bound is
   the backstop, not the clock a case keeps for itself — and the requirement's contract at
   `openspec/specs/agent-issue-loop/spec.md:2621` with its scenario at `:2640`.

## Impact

- `tools/loop/tests/loop-cases.sh` — the helper `wait_for_pid_end`, `CASE_WAIT_TICKS`, the staged and bounded HUP
  case, the new case, two registry entries, one `# files:` fix. **No engine file changes**: `tools/loop/run.sh`,
  `tools/loop/loop.conf` and `tools/loop/tests/loop-selftest.sh` are untouched.
- `guidelines/Backlog.recipes.md` — one paragraph in §7b, the section `Backlog.md` §7 routes the loop's procedure to.
- `openspec/specs/agent-issue-loop/spec.md` — one SHALL paragraph in the requirement that holds a case's bound, and
  its scenario. The requirement's first paragraph and its two other scenarios are carried verbatim.
- Additive: a case that never waits on a process is unaffected, and a case that does keeps the suite's bound behind
  its own. Rollback is the revert of these three files. No Android module, no native boundary and no UI surface is
  touched, and no `vcpkg/` or gradle step is owed.
- The delta touches no engine line, so it adds **no mutation record**: the falsification it owes is the new case
  failing where its helper is absent (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`), and the mutation a
  record would need — removing the bound from `wait_for_pid_end` — re-creates the hang this change removes, which is
  the one thing a suite cannot report as a red.