# Proposal — the wait for a child ends when the child does

## Why

The loop's own gate costs 12 minutes of wall clock, and about half of it is sleep. The wait in `run_child`
(`tools/loop/run.sh:924-957`) polls for the child's own completion record with `sleep 1`, and
the heartbeat subshell the wait is joined through (`tools/loop/run.sh:890-916`) sleeps a whole second as well and
counts *iterations* (`ticks`) as if they were seconds — `[ $((ticks % interval)) -eq 0 ]` — so the poll's
granularity cannot be lowered without changing what `LOOP_PROGRESS_SECONDS` means. A child that answers at once
therefore still costs the run 1–2 s of pure sleep, and one run case drives up to three children.

Measured (issue #259, 2026-10-22, this machine): the self-test's 163 cases took 723 s and the 66 `run#*` cases
alone 481 s of that; a copy of the engine whose `sleep 1` became `sleep 0.05` ran
`run#recordsAFailedReviewOnTheBranch` in 9 s instead of 15 s — 40 % of that case was the sleep. Re-measured here
on HEAD, with the red case this change adds, the wait for a worker that answered in 150 ms cost **2 008 ms**
(`.pi/issue-loop/items/259/red-on-head-wait.log`, 2026-10-25T01:27:49Z, `tools/loop-selftest.sh --case
'run#theWaitEndsWhenTheChildDoes'` → `FAIL`, `loop-selftest: 1 of 1 cases failed`).

- **Root cause, one mechanism**: the poll granularity is a whole second in both the wait and the heartbeat it is
  joined through, and the heartbeat counts wake-ups instead of elapsed seconds, so the granularity cannot be
  lowered while the progress interval keeps its meaning.
- **Owning requirement**: `agent-issue-loop` — "A run reports the wait it cannot get from the child"
  (`openspec/specs/agent-issue-loop/spec.md:742`), whose cadence scenario this change extends with a rule about
  the wait's own granularity.
- **Owning guideline section**: `guidelines/Backlog.md`, section "7. The loop" — the bound on one child, where the
  deadline is described as read "on every tick".

## What Changes

- `run_child` polls for the completion record at `LOOP_POLL_SECONDS` (default `0.05`), validated like the two
  bounds it stands beside: empty, non-numeric or zero falls back to the default rather than stopping the wait.
- The cap and the run's deadline stay whole seconds (`date +%s`) and are read **once per second**, not once per
  look, so the finer granularity does not buy forks it cannot spend.
- The heartbeat counts **elapsed seconds** (`SECONDS` against its own base) and reports when the configured
  interval has passed, so `LOOP_PROGRESS_SECONDS` keeps meaning seconds of wall clock.
- The `TERM`→`KILL` grace after a cap or deadline firing stays one second: it is a grace, not a poll.
- One existing case is repaired because the finer poll exposes its latent race:
  `labels#trapRemovesInProgressOnSignal` released the blocked stub child by opening the case's fifo for writing
  (`printf '\n' > "$block"`) 0.2 s after it signalled the run. The run's trap used to fire only when the whole
  second of the poll it was sleeping through had passed, so the child outlived that window; with the poll at
  0.05 s the run ends the child first and the case's open waits for a reader that no longer exists — it hung the
  suite, and the gate behind it, for ever. The case now holds the fifo read-write (`exec 8<> "$block"`, an open
  that needs no partner) for its whole life and releases the child through that descriptor (issue #259; measured
  2026-10-25: the case passes in ~7 s where it had to be killed after 56 min).

Additive: no requirement is removed or modified in shape, one requirement is added to `agent-issue-loop`. The
rollback is the previous `sleep 1` in both places with the tick-counting heartbeat, i.e. reverting
`tools/loop/run.sh`; no state, no on-disk format and no caller's contract changes.

Surface, counted before the plan was fixed:
`grep -rn 'sleep 1$\|ticks\|LOOP_PROGRESS_SECONDS\|LOOP_POLL_SECONDS' tools/loop/run.sh tools/loop-cases.sh
tools/loop/prompts/ tools/loop/fixtures/ guidelines/Backlog.md openspec/specs/agent-issue-loop/spec.md
tools/loop/loop.conf` → 13 hits in 4 files (`run.sh`, `loop-cases.sh`, `Backlog.md`, the `agent-issue-loop`
spec). Three existing cases watch the heartbeat and are re-run but rewritten by nothing:
`run#childReportsWhileItWaits` (`LOOP_PROGRESS_SECONDS=1`, 3 s child), `run#aWriterTheRunCanPlaceBlocksTheItem`
and `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem` (`LOOP_PROGRESS_SECONDS=1`, where the tick is the
reading that places a write). No fixture, prompt or profile key asserts the cadence.

surface: 2 cases added, 0 rewritten, 0 fixtures realigned, 2 mutation records added — the command above.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop`: one requirement added — "A wait ends when the child does, not on the next whole second" —
  with two scenarios, one for the granularity and one for the cadence that has to survive it.

## Impact

- `tools/loop/run.sh` — `run_child`: the poll, the heartbeat's accounting, the once-per-second clock read.
- `tools/loop-cases.sh` — two cases added (`run#theWaitEndsWhenTheChildDoes`,
  `run#theProgressLineKeepsItsInterval`), registered in `LOOP_SHELL_CASES` and in the dispatch, and
  `labels#trapRemovesInProgressOnSignal` repaired: its fifo is held read-write so the release cannot wait for a
  reader the run has already ended.
- `tools/loop-mutations.sh` — records `60.1` (the poll's granularity) and `60.2` (the heartbeat's elapsed-second
  test), so the falsification is checked by every `--check` rather than remembered.
- `guidelines/Backlog.md`, section "7. The loop" — the wait's granularity named, and the deadline's read corrected from "every
  tick" to "once a second".
- `openspec/specs/agent-issue-loop/spec.md` — the added requirement, merged at archive.
- No Android module, no Gradle build, no native code, no `loop.conf` key, no on-disk format is touched: the
  delta is shell and its own cases, so the gate owes no compiled suite for it (the gate still runs the loop's
  own suites for a `tools/loop/**` path).
