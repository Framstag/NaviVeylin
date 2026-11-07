# Tasks — fix-the-tail-is-admitted-on-the-cheapest-measured-outcome

## 1. The red case (do first)

- [x] 1.1 Rewrite `state#floorIsDerivedFromTheFinishedItemsWallTimes` in `tools/loop-cases.sh`: the floor follows the
      **cheapest** measured item (90 min over three 60-min items), a 10-min history admits a 28-min tail, a
      `[600, 3600, 3600]` history admits an 80-min tail (the median would refuse it), and the derived floor is
      below `LOOP_ITEM_GATE_SECONDS` (the #663 reversal). Verify: the case is red on the base —
      `evidence/red-on-base-floor.xml` (`tests=1 failures=1`, 2026-10-27T06:43Z).
- [x] 1.2 Extend `run#childIsEndedAtTheRunsDeadline`: the item the run's deadline cut carries no decision label
      and the report's decision list does not hold it. Verify: red on the base —
      `evidence/red-on-base-deadline.xml` (`tests=1 failures=1`).
- [x] 1.3 Extend `run#treeOfACapEndedChildIsKeptAsAPatch`: the item's state record names the patch and the
      report's action list prints the recovery. Verify: red on the base —
      `evidence/red-on-base-leftover.xml` (`tests=1 failures=1`).

## 2. The floor (`agent-issue-loop` — "The floor the run refuses by is what its own items cost")

- [x] 2.1 `tools/loop/state.sh` `cmd_next`: derive the floor from `min(wallSeconds) / 60 * 1.5`, rounded up, with no
      `max(…, LOOP_ITEM_FLOOR_MINUTES)`; keep the configured minimum for `n == 0`; the refusal names the cheapest
      wall time, the count and the configured minimum. Verify: `state#floorIsDerivedFromTheFinishedItemsWallTimes`
      and `state#floorWithNoHistoryIsTheConfiguredMinimum` pass.
- [x] 2.2 `tools/loop/state.sh` header comment and `tools/loop/loop.conf`'s `LOOP_ITEM_FLOOR_MINUTES` block state
      the new derivation and the accepted exposure. Verify: `state#defaultsComeFromLoopConf` and
      `state#projectKeysAreReadFromLoopConf` pass; `tools/loop-mutations.sh --check` applies `64.2`.

## 3. The clock cut (`agent-issue-loop` — "A child the run ended itself leaves no commit")

- [x] 3.1 `tools/loop/run.sh`: read `LOOP-DEADLINE` from the item's worker, the round-2 worker *and the round's
      reviewer* (task 7.1); an item cut by the deadline is recorded blocked with no escalation (`escalatedLabel`
      empty) and its leftover patch in the note. Verify: `run#childIsEndedAtTheRunsDeadline` and
      `run#reviewerEndedAtTheRunsDeadlineIsNotADecision` pass.
- [x] 3.2 `tools/loop/state.sh` `done --leftover PATH` records the patch on the item; `tools/loop/report.sh`
      prints `recover #<issue> — … kept at <path>` in its action list (and the "none" text names the sixth
      source). Verify: `run#treeOfACapEndedChildIsKeptAsAPatch` passes.

## 4. The guideline (`guidelines/Backlog.md` §7 "The loop")

- [x] 4.1 Rewrite "What an item costs, and the floor it is refused by" and "What bounds it" to the cheapest
      measured item, the no-history minimum, and the deadline-cut exposure with its bound. Verify:
      `tools/check-doc-routes.sh` and `tools/check-todo-references.sh` pass (the gate's steps).

## 5. Revert-check (every new invariant)

- [x] 5.1 `68.1` — `min` → `max` in the floor's derivation; `state#floorIsDerivedFromTheFinishedItemsWallTimes`
      red, restore green. Verify: `evidence/mutations.log`.
- [x] 5.2 `68.2` — the clock-cut guard never holds (deadline items escalated); `run#childIsEndedAtTheRunsDeadline`
      red, restore green. Verify: `evidence/mutations.log`.
- [x] 5.3 `68.3` — `leftover` dropped from the item record; `run#treeOfACapEndedChildIsKeptAsAPatch` red, restore
      green. Verify: `evidence/mutations.log`.
- [x] 5.4 The four blocked-path records (`14.3`, `14.5`, `14.6`, `19.3`) follow the re-indented escalation block;
      `tools/loop-mutations.sh --check` applies all 194 records. Verify: `evidence/mutations-check.log`.
- [x] 5.5 `adapter#theChildIsStartedWithTheResultBound` unsets `LOOP_TOOL_RESULT_TRIM_RECORD` with the event stream
      it is derived from — test isolation for a worker's inherited environment, no production behaviour. Verify: the
      case passes inside a child whose run exported the record.

## 6. The suite and the gate

- [x] 6.1 The cases the change's files declare pass: `tools/loop-selftest.sh --from-git <base>`. Verify:
      `evidence/focused-suite.log` (round 1's selection, 247 of 247) and the tallies beside it. This round: the
      groups whose cases the delta touches ran green before the gate — `state` (19), `report` (16) and `run` (92)
      — plus the new case alone and the adapter isolation case, and `tools/loop-mutations-selftest.sh` (34); the
      whole suite green on the merged tree is the gate's own step, 6.2.
- [x] 6.2 The gate once, on the merged tree: `./tools/loop/gate.sh --base main` — its aggregate line names 635
      checks and no failure over 20 steps, its loop-suite step `tools/loop-selftest.sh --from-git main` ran 610 s
      (`evidence/gate-tallies/tools_loop-selftest.sh_--from-git_main.xml`, `tests="248" failures="0"`) and its
      `tools/loop-mutations.sh --check` step 194 records
      (`evidence/gate-tallies/bash_tools_loop-mutations.sh_--check.xml`, `tests="194" failures="0"`). The
      transcript is `evidence/gate.log`, the tallies `evidence/gate-tallies/`. Round 2's red run is kept beside
      them: `evidence/gate-round2-red.log` and `evidence/gate-tallies-round2-red/` (one failure, record `31.1`
      stale at the time, re-pointed before this run). A first attempt of this round had two failures, both inside
      the loop suite, with the case names lost to the gate's `mktemp` stream; the same suite command re-run alone
      was green in full (`evidence/loop-suite-green-rerun.log`, 248 cases), and the next full run of the gate was
      green. `evidence/gate-attribution.md` "Round 3" states both.
- [x] 6.3 No compiled file is touched, so the profile's compiled step owes nothing: the structural checks above are
      the build task this change owes (`guidelines/Build.md` §2, the scope block). With `main` as the base the
      gate selected no compiled step at all (`gate.sh --base main --plan` lists the shell steps only); rounds 1
      and 2 saw Gradle because their base was an older `origin/main`.
- [x] 6.4 `bash tools/loop/evidence-check.sh openspec/changes/<change> --root .` passes (`evidence-check: PASS`):
      every tally, `/tmp` pointer and `§N` resolves, and its `C5` line reports **no** unresolved or ambiguous
      `file:line` (`618 resolved and in range, 0 unresolved or ambiguous` on the shipped tree) — round 1's one
      dangling pointer cited `run.sh` by its bare basename; it now names the file it lives in. `C4` scans the
      project's configured path only (`app/src`), so the
      restore of the files this change mutates is recorded apart, in `evidence/restore-clean.log`.

## 7. The reviewer the deadline cut, and the pass after it (round 1's review)

- [x] 7.1 Red case `run#reviewerEndedAtTheRunsDeadlineIsNotADecision` in `tools/loop-cases.sh`: the reviewer
      blocks on a fifo, the run's recorded deadline is moved past while it waits, and the case asserts the
      transcript's `LOOP-DEADLINE` line, no retry, no rework worker, no decision label and the report's `none`.
      Verify: red on the round-1 tree with the reviewer's `LOOP-DEADLINE` read removed —
      `evidence/red-on-base-reviewer-deadline.xml` (`tests=1 failures=1`): `the cut round was read as a refusal
      and bought a rework worker`.
- [x] 7.2 `tools/loop/run.sh`: read `LOOP-DEADLINE` from `reviewer-report-$round.md` (and from the retry's report),
      skip the cut-short retry once the clock ended the round, and break the round as `deadline` — blocked, no
      escalation, no failed-review marker. Verify: 7.1 green; `run#childIsEndedAtTheRunsDeadline` still green.
- [x] 7.3 `tools/loop/run.sh`: no second pre-screen pass once the deadline has ended a child (`deadline_cut`), so
      the pass cannot exclude the item whose branch the cut run had just made and make the report name a decision
      for it. Verify: 7.1's `decisions the owner owes: none` assertion is red when the guard is mutated
      (`68.5`), green as shipped.
- [x] 7.4 The delta's reviewer scenario, the queue-pass sentence and the action list's leftover source; the shipped
      `agent-issue-loop` spec carries the same text. Verify: `tools/check-openspec-hygiene.sh` and
      `tools/loop-selftest.sh --case 'run#reviewerEndedAtTheRunsDeadlineIsNotADecision'`.
- [x] 7.5 Revert-check records `68.4` (the reviewer's `LOOP-DEADLINE` read), `68.5` (the queue-pass guard) and
      `68.6` (the escalation a cap-ended item still owes, which round 1's review read as unasserted).
      Verify: `evidence/mutations.log` — each record RED with the failing assertion and its timestamp, restored
      green — and `evidence/mutations-check.log`.
