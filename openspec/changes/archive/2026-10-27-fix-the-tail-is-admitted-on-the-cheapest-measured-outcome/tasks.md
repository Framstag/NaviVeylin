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

- [x] 3.1 `tools/loop/run.sh`: read `LOOP-DEADLINE` from the item's worker and round-2 worker transcripts; an item
      cut by the deadline is recorded blocked with no escalation (`escalatedLabel` empty) and its leftover patch
      in the note. Verify: `run#childIsEndedAtTheRunsDeadline` passes.
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
      `tools/loop-mutations.sh --check` applies all 191 records. Verify: `evidence/mutations-check.log`.
- [x] 5.5 `adapter#theChildIsStartedWithTheResultBound` unsets `LOOP_TOOL_RESULT_TRIM_RECORD` with the event stream
      it is derived from — test isolation for a worker's inherited environment, no production behaviour. Verify: the
      case passes inside a child whose run exported the record.

## 6. The suite and the gate

- [x] 6.1 The cases the change's files declare pass: `tools/loop-selftest.sh --from-git <base>`. Verify:
      `evidence/focused-suite.log` and the tallies beside it.
- [x] 6.2 The gate once: `./tools/loop/gate.sh`. Verify: `evidence/gate.log` and `evidence/gate-tallies/`. Its
      loop-suite step is green (247 cases); its compiled step was selected by the stale default base
      (`origin/main`), not by this delta, and failed on a network-dependent test that passes alone — recorded
      and attributed in `evidence/gate-attribution.md`, not re-run.
- [x] 6.3 No compiled file is touched, so the profile's compiled step owes nothing and is not run: the structural
      checks above are the build task this change owes (`guidelines/Build.md` §2, the scope block).
- [x] 6.4 `bash tools/loop/evidence-check.sh openspec/changes/<change> --root .` resolves every pointer.
