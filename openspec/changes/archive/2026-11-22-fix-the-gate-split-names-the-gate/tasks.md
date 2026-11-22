# Tasks — the spend split is taken at the gate, and the suite's tail is stated beside it

## 1. The reading

- [x] 1.1 `spend#aFocusedSuiteRunIsNotTheItemsGate` written first and red on `1a5dede2` (`evidence/red-on-HEAD.log`,
      `evidence/red-on-HEAD.xml`): the fixture's `sed` over `gate.sh` is turn 0, so the old pattern reported the whole
      95 000 as the gate's tail.
- [x] 1.2 `run_word`, `program`, `is_gate_run` and `is_focused_suite` in `tools/loop-spend.py`, restating
      `tools/loop/lib.sh`'s `run_word` (`design.md`, alternative A: the shared jq definitions, eliminated on the cost
      of a subprocess per tool call).
- [x] 1.3 `GATE_RE` removed; the split taken twice (`first_verification`), both halves in `report()`, both in `--json`
      (`afterFirstGate`, `afterFirstSuite`) and in `LOOP-SPEND-GATE after= suite=`.
- [x] 1.4 `split_positions` computes the two splits once, so the report and the JSON cannot print different figures.
- [x] 1.5 The suite's own tail printed beside the gate's, each over the sessions that made that call
      (`spend#theFocusedRunsTailIsStatedBesideTheGates`), and both positions asserted against `--json`
      (`spend#jsonCarriesBothVerificationPositions`).

## 2. The sites that quote the figure

- [x] 2.1 `guidelines/Backlog.recipes.md` (four sites), `tools/loop/loop.conf` (two), `tools/loop/run.sh`,
      `tools/loop/tests/loop-cases.sh`, `tools/gate-attribute.sh`: each figure keeps its digits and gains the call it
      was taken at — the earlier of the item's first gate and its first focused suite run — because the window those
      readings came from is not in this tree (`design.md`, "The numbers the sites quote").
- [x] 2.2 `.pi/skills/loop-spend/SKILL.md` carries the current pair with its window and command.

## 3. Evidence

- [x] 3.1 The reading over the archive present: `after=15 643 757`, `suite=75 818 065`, 59 items, 254 sessions, and the
      token total as `evidence/spend-after.json` reads it (it rises while this item's own session is written),
      `evidence/spend-before.json`, `evidence/spend-quiet.log`.
- [x] 3.2 Mutation probe set `evidence/spend-mutations.json`, run with `tools/mutate.sh`, log
      `evidence/mutation-probe.log`: the suite admitted to the gate predicate reds the case, and so does the gate's
      program dropped from it; both restore byte-identically and re-run green.
- [ ] 3.3 The item's gate — run once by this change's own child after the archive, no step of it a case of this
      change; its `LOOP-GATE` line is the report's, and the tally under `app/build/test-results/loop-gate` is where
      the figures were taken.
