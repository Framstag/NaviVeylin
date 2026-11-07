# Tasks — fix-child-cap-follows-what-one-items-gate-owes

## 1. The measurement the repair is built on

- [x] 1.1 Read the item that was ended green (#310) and the six others that carry `LOOP-TIMEOUT` as their first line:
      `.pi/issue-loop/items/310/events.jsonl` gives 972 000 ms, 957 000 ms and 233 000 ms of 2 342 000 ms of tool time,
      and `.../evidence/gate-summary.xml` carries `tests="601" failures="0" errors="0"`.
- [x] 1.2 Turn that into two recorded numbers: the floor a child may always run, and what one item's gate owes — one
      gate round (971 s measured over 23 suites) times the 1.5 spread the item floor's derivation uses, for two
      rounds, plus the phases the rounds are not (542 s measured, recorded as 900 s).

## 2. The engine reads the second number

- [x] 2.1 `tools/loop/loop.conf`: `LOOP_ITEM_GATE_SECONDS=3900`, and `LOOP_CHILD_MAX_SECONDS` documented as the floor.
- [x] 2.2 `tools/loop/run.sh`: `child_cap` returns the larger of the two; the header prints the bound the child is
      given, and `run_child` uses that value rather than re-reading the profile.
- [x] 2.3 `tools/loop-cases.sh`: `conf_with_child_cap`, and the eight cases that shorten the bound moved onto it.

## 3. The invariant and its falsification

- [x] 3.1 `run#theChildCapIsNeverBelowWhatOneItemsGateOwes`: the recorded number is not below the 2 704 s measurement,
      the header's bound is that number, a floor under a larger gate yields the gate, and a profile with no gate keeps
      the floor.
- [x] 3.2 `tools/loop-mutations.sh` `64.1` drops the derivation; the case goes red, the restore goes green
      (`evidence/mutation-64.1.log`).
- [x] 3.3 The seven cases whose bound the change touches re-run: `run#childOutlastingTheWallClockIsBlocked`,
      `run#childIsEndedAtTheRunsDeadline`, `run#treeOfAChildTheCapEndedIsNotCommitted`,
      `run#treeOfACapEndedChildIsKeptAsAPatch`, `run#detachedProcessInTheWorktreeIsEndedByTheCap`,
      `run#aRefusedStartKeepsTheLeftoversItFound`, `run#theStartAfterTheRunsOwnEndedChildIsNotRefused`.

## 4. Documents and the gate

- [x] 4.1 `guidelines/Backlog.md` section 7 states the bound as the larger of two recorded numbers with this
      measurement; the key table in section 11 gains `LOOP_ITEM_GATE_SECONDS`.
- [x] 4.2 The `agent-issue-loop` delta: the child scenario names both keys, one scenario added, every shipped scenario
      of the requirement carried verbatim; `openspec validate … --strict` passes.
- [x] 4.3 `bash tools/loop/evidence-check.sh openspec/changes/fix-child-cap-follows-what-one-items-gate-owes`.
- [x] 4.4 The gate round the profile maps for `tools/loop/**`, its summary kept as `evidence/gate-summary.xml`.
