# Tasks

## 1. The step's base state is measured when a gate needs it (spec `agent-issue-loop`, "A step red on the run's base is subtracted from the item's tally and named")

- [x] 1.1 `tools/loop/gate.sh`: `--step <command>` parsed beside `--base-red`, refused outside it, and used after the owed
      set is built to keep the one pair whose command is exactly that one — a command no pair of the profile owes is a
      refusal, because a measurement of nothing would read as a base that was green. `base_red_on` reads
      `LOOP_BASE_RED` after the pre-flight override, so a measurement never measures inside a measurement.
      — done: `bash -n tools/loop/gate.sh` clean; `gate.sh --base-red --step './suite-one'` over the case's profile
      measures exactly one step.
- [x] 1.2 `tools/loop/gate.sh`: `base_tree` answers a clean tree whose `HEAD` is `$base` (the gate's own tree first, then
      `loop_main_worktree`), `measure_step_base` runs `gate.sh --base-red --step <cmd> --base <base>` there with the
      profile this gate read, keeps its output in `<state dir>/base-red.log`, writes the record through
      `state.sh base-red-step`, reloads `base_red_json`; a measurement that could not be taken, read or written prints
      `LOOP-GATE-BASERED-UNMEASURED:<step>` and leaves the red with the item.  `finish_step` calls it only on a red step
      the record does not hold.
      — done: the case's gate prints `LOOP-GATE-BASERED-MEASURE:./suite-one — measuring this step on <base> in <tree>`
      and subtracts the pair it measured; the second gate prints no measure line.
- [x] 1.3 `tools/loop/state.sh`: the jq reader of the `LOOP-BASERED-STEP:` / `LOOP-BASERED-CASE:` lines moves into
      `base_red_read`, and `base-red-step --from <file> --base <sha> [--step <command>]` merges one measurement into
      `run.baseRed` (shape unchanged) and sets `run.baseRedBase`; an output naming another command is refused.
      — done: `bash -n tools/loop/state.sh`; the case asserts `.run.baseRed ==
      {"./suite-one":["notes#aCaseThatWasRed"]}` and `.run.baseRedBase == <base>`.
- [x] 1.4 `tools/loop/run.sh`: `take_base_red` and its four guards removed; the block prints the mode only.
      — done: `grep -n 'take_base_red' tools/loop/run.sh` returns nothing; `bash -n tools/loop/run.sh` clean.
- [x] 1.5 `tools/loop/loop.conf`: the `LOOP_BASE_RED` comment states the lazy measurement, the figures the start's
      pre-flight cost, and what a measurement that cannot be taken does.
      — done: `tools/loop/loop.conf` records 1 772 s / 1 251 s and names `gate.sh --base-red --step`.

## 2. A worktree of the run's own repository carries the run's identity (spec `agent-issue-loop`, "A worktree of the run's own repository carries the run's identity")

- [x] 2.1 `tools/loop/lib.sh`: `loop_main_worktree` added, `loop_path_outside_tree` honours a path inside the main
      worktree of the repository it resolved. Measured before the change: `state.sh get run.baseRed` from
      `.pi/worktrees/loop` answered `no run state at .pi/worktrees/loop/.pi/issue-loop/run.json` (exit 2), and the live
      run's `.run.baseRedUsed` was `null` with a red step in `run.baseRed`.
      — done: the case's gate runs in a linked worktree of the fixture project and its subtraction reaches
      `.run.baseRedUsed.steps` in the project's state file.
- [x] 2.2 The other direction is still refused: `state#inheritedStatePathOutsideTheTreeIsIgnored` — a state path in a
      scratch directory outside the fixture repository is not written and the tree resolves its own.
      — done: `PASS state#inheritedStatePathOutsideTheTreeIsIgnored`.

## 3. The case and the records

- [x] 3.1 `tools/loop/tests/loop-cases.sh`: `gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt` — a project that stays
      at the base and an item in a linked worktree of it; asserts nothing is measured before the gate, that the gate
      measures only the red step (`./suite-one`) and not the green one (`./suite-two`), records it against the base
      commit, writes the run's own record and its log, and that a second gate measures nothing.
      — red on HEAD, recorded: `tests=3 failures=1 errors=0 (./suite-one 0s, ./suite-two 0s) — failing: ./suite-one:
      expected '0', got '1'` — the base's red charged to the item, which is the defect. Green on this change:
      `PASS gate#theBaseStateIsMeasuredWhenTheGateFirstNeedsIt`.
- [x] 3.2 `tools/loop/tests/loop-mutations.sh`: `944.1` removes the measurement, `944.2` removes the worktree half of the
      identity rule, `921.1` moved onto the line this change leaves.
      — done: `--only "921.1 944.1 944.2" --timeout 400` → `RED 921.1 …`, `RED 944.1 …`, `RED 944.2 …`,
      `restore … green again`, `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`; `--check` applies all
      three.
- [x] 3.3 The first half of `gate#aBaseRedStepIsSubtractedAndNamed` still takes the whole-set pre-flight by hand, which
      is what keeps `gate.sh --base-red` (with no `--step`) and `state.sh base-red` exercised.
      — done: `PASS gate#aBaseRedStepIsSubtractedAndNamed`.

## 4. The documents

- [x] 4.1 `guidelines/BuildEvidence.md` §4d: the base-red rule states the lazy measurement, the tree it runs in, the
      record it writes, and what a measurement that cannot be taken does.
      — done: `AGENTS.md`'s row for that cluster restated to `1.7–6.3 kB` and `tools/check-doc-routes.sh` green.
- [x] 4.2 The spec delta: MODIFIED "A step red on the run's base is subtracted from the item's tally and named" with
      every scenario the shipped spec has, and ADDED "A worktree of the run's own repository carries the run's
      identity".
      — done: `openspec validate fix-the-base-red-is-measured-when-a-gate-needs-it --strict` → valid.

## 5. Verification

- [x] 5.1 The group the change moves: `loop-selftest.sh --group gate` → `all 41 cases passed`.
- [x] 5.2 `tools/check-portability.sh`, `tools/check-openspec-hygiene.sh`, `tools/check-doc-routes.sh`,
      `tools/check-doc-values.sh`, `tools/check-todo-references.sh` — each green.
- [x] 5.3 The full self-test suite of the tree that lands, and `loop-mutations.sh --check` over the records the delta's
      files own — recorded in `evidence/`.
- [x] 5.4 No compiled file is touched: no Gradle build and no Android suite is owed (`guidelines/Build.md` §2, the scope
      block), and none was run.
- [x] 5.5 What the issue asks to be measured afterwards — the run's start to its first item, and `run.baseRed` keys
      against the steps its items actually ran — is stated in `evidence/what-was-not-measured.md`, because it needs a
      night of the loop and not this session.
