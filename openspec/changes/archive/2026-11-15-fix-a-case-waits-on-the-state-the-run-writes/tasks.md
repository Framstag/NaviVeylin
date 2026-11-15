# Tasks — a signalled case's wait is decided by the state the run writes

The implementation of this change landed before the change was written, as commit `c1fd406d` (merged `c4982d8b`).
These tasks record what that commit does, where each part of it is, and what the change cannot show.

## 1. The bounds, each pricing one object

- [x] 1.1 `tools/loop/tests/loop-cases.sh:1326-1331`: `STOP_WAIT_STAGE_FACTOR` (`10`), `STOP_WAIT_TICK_FLOOR` (`300`),
  `STOP_WAIT_TICK_CEILING` (`1200`), `STOP_WAIT_END_TICKS` (`50`) and `STOP_WAIT_STATE_EVERY` (`3`), each with the
  object it prices in its own comment: the staging this case measured, the 30 s a quiet host needs, the most a case may
  hold its slot for, the 5 s the run's remaining end gets, and how often the state is read. — *spec: "A self-test case
  is held to a bound of its own", paragraph 3.*
- [x] 1.2 `tools/loop/tests/loop-cases.sh:1334-1341`: `stop_wait_deadline <the ms this case's staging took>` answers the
  ticks the wait allows — the staging in ticks of the step the wait sleeps in, times the factor, floored and capped. —
  *verified by `run#theStopWaitIsDecidedByTheStateTheRunWrites` asserting the four figures at `:15543-15546`.*
- [x] 1.3 No engine or configuration value moves: `LOOP_CASE_MAX_SECONDS` and the run's own `TERM`→`KILL` grace are
  untouched, because the repair is the object the case's clock prices, not the engine's bounds. — *verified by the
  commit's file list: the change touches the suite's case file, its mutation set and one guideline.*

## 2. The wait is decided by what the case asserts

- [x] 2.1 `tools/loop/tests/loop-cases.sh:1346-1347`: `run_stop_reason` reads `.run.stop.reason` and `run_item_result`
  reads `.items[0].result` out of the state the run writes, each answering empty on a failed read — a state caught
  mid-replacement is a run still working, and the wait reads it again. — *verified by the new case's second and third
  readings, which assert the empty state at `:15574` and the recorded one at `:15560-15561`.*
- [x] 2.2 `tools/loop/tests/loop-cases.sh:1357-1384`: `wait_for_run_stop <state file> <pid> [ticks]` polls the process
  and, every third tick, the state. Once both facts are there the run's remaining end gets `STOP_WAIT_END_TICKS` and the
  wait answers with the process's own status; at the deadline the state is read once more, after the last thing the run
  writes, and a run still standing answers `124` naming it, while a run that ended answers with what it ended with. —
  *spec: same requirement, scenario "The stop wait is decided by the state the run writes".*
- [x] 2.3 `tools/loop/tests/loop-cases.sh:15403-15446`: the HUP case times its own staging (`stage_started`,
  `stage_ms`), derives its deadline (`:15438`), waits on the state (`:15439`) and, when it must fail, names the
  deadline, the staging it was derived from and the stop and item it read (`:15445`). — *verified by that failure text
  and by the case's own assertions on the stop and the item after the wait (`:15448-15453`).*

## 3. The case that holds the derivation and the readings

- [x] 3.1 `tools/loop/tests/loop-cases.sh:15537`: new case `run#theStopWaitIsDecidedByTheStateTheRunWrites` asserts the
  derivation at four stagings (`:15543-15546`) — nothing to measure and 2.9 s both floor, 8 s derives ten times itself,
  60 s is capped — and then the three readings: a run that recorded both facts and ended answers with its own status and
  the state it read, inside a fraction of the deadline (`:15551-15563`); a run that ended having recorded neither answers
  at once with its status and the empty state (`:15567-15576`); a run that recorded both and stood is answered `124`
  with the state it read, inside the tail grace rather than the deadline (`:15581-15594`). — *spec: scenario "The stop
  wait is decided by the state the run writes".*
- [x] 3.2 Registry entries: `tools/loop/tests/loop-cases.sh:248` (the case set) and `:993` (the dispatcher arm), so the
  case runs by name and in the pool. — *verified by `driver#everyCaseIsNamedInTheRegistry`.*

## 4. The falsification

- [x] 4.1 `tools/loop/tests/loop-mutations.sh:1350`: record `928.1` — the constant floor put back where the derivation
  was, so every host waits the 30 s priced on a quiet one, a staging of 8 s included. It names
  `run#theStopWaitIsDecidedByTheStateTheRunWrites` as the case it must red. — *verified: the case's 8 s assertion reads
  the floored figure under it.*
- [x] 4.2 `tools/loop/tests/loop-mutations.sh:1354`: record `928.2` — the trap a signalled run answers HUP with removed,
  so the run genuinely never stops and the HUP case must still red at its derived deadline with an empty state named.
  That is the teeth a state-driven wait has to keep: a wait that answered `0` without waiting would go green over a run
  that took no signal. — *verified: it names `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem` as the case it must red.*

## 5. The rule, where its readers route it

- [x] 5.1 `guidelines/Backlog.recipes.md:815-832`: one paragraph — the clock a case keeps is measured on the host that
  keeps it — carrying the 2026-11-15 measurement, `stop_wait_deadline`'s derivation and its floor and cap, the two
  readings the deadline tells apart, and the two mutation records. — *verified by the paragraph's presence in the
  recipe document the loop's procedure section routes to.*

## 6. The requirement

- [x] 6.1 `openspec/changes/fix-a-case-waits-on-the-state-the-run-writes/specs/agent-issue-loop/spec.md`: a `MODIFIED`
  block that replaces "A self-test case is held to a bound of its own" (`openspec/specs/agent-issue-loop/spec.md:2656`)
  whole — its two shipped paragraphs and its three scenarios verbatim, in order, plus the paragraph on the derived
  deadline and the state it is decided by, and one scenario. — *verified by
  `openspec validate fix-a-case-waits-on-the-state-the-run-writes --strict` (`evidence/openspec-validate.txt`).*

## 7. The gate, and what it does and does not say

- [x] 7.1 The gate that landed `c1fd406d` is the loop's own run, and its per-suite tally is **not retained** in this
  checkout: the run record is gitignored and machine-local, and this change was written after the run. No figure is
  quoted here, because a tally no artifact carries is the claim `tools/loop/evidence-check.sh`'s C2 exists to refuse. A
  reader who needs it re-runs `bash tools/loop/tests/loop-selftest.sh`.
- [x] 7.2 What this change could take instead, and took: the five bounds and their comments, the derivation and the
  wait by line, the two case bodies and their assertions, the two mutation records verbatim from the suite's own set,
  and the structural checks of 8.1.

## 8. The checks this change owes

- [x] 8.1 `openspec validate fix-a-case-waits-on-the-state-the-run-writes --strict`, `openspec doctor`,
  `bash tools/check-doc-routes.sh`, `bash tools/check-openspec-hygiene.sh`, `bash -n
  tools/loop/tests/loop-cases.sh` and `bash tools/loop/evidence-check.sh <this change>` — each re-runnable and recorded
  in `evidence/structural-checks.txt` with its verdict.
- [x] 8.2 The delta touches no compiled file — a test suite, its mutation set and a guideline — so this change runs no
  build, no Android test and no native step.

## 9. What this change does not cover

- [x] 9.1 Not covered: the *other* waits in the suite. `wait_for_pid_end` keeps its own tick count for the cases that
  wait on a process they started and assert nothing about the state it writes; this change derives a deadline only where
  the case asserts a state, and leaves `CASE_WAIT_TICKS` as the default of a caller that passes no staging.
- [x] 9.2 Not covered: a host slower than the cap. A staging that would derive more than `1200` ticks is held at it, so a
  host whose staging exceeds two minutes will still hit the deadline; that is deliberate — the bound #860 exists for —
  and it is stated in the bound's own comment rather than mitigated.
- [x] 9.3 Not covered: the pool's own bound. `LOOP_CASE_MAX_SECONDS` times the pool's width is still what answers a case
  that never reaches its own wait, and this change neither widens nor narrows it.
