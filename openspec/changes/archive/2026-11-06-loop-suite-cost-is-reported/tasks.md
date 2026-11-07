# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/agent-issue-loop/spec.md` for the contract.

Applied 2026-11-06 in `/home/tim/projects/NaviVeylin/.pi/worktrees/loop` at the run's base `0fb7a80`, with the gate
deferred to a fresh child (the run's own line: this item's session ends at the diff). Every figure quoted below is a
command's output, and the command is beside it.

## 1. The red cases

Requirement: "The suite states what it cost as well as what it ran", scenarios "The summary names what each group
cost" and "A passing case's line keeps its shape".

- [x] 1.1 `driver#theSummaryNamesWhatEachGroupCost` added to `tools/loop-cases.sh`: a two-group stub suite
      (`alpha` 2 cases, `beta` 1) whose summary must carry one line per group with its elapsed time and count, and the
      selection's own line. Verify: it fails against the driver as base `0fb7a80` carries it —
      `: 'loop-selftest: alpha 2 cases ' not found in: …`, `FAIL`, `1 of 1 cases failed` (`evidence/red-on-HEAD.log`)
      — and passes with task 2.1 (`PASS driver#theSummaryNamesWhatEachGroupCost`, exit 0,
      `evidence/verification.txt`).
- [x] 1.2 `driver#theTimesFigureIsAskedForNotAssumed` added: the same suite without `--times` prints `PASS <name>`
      and nothing else, and with it the same name plus its seconds. Verify: it fails on base because `--times` is an
      unknown argument (`loop-selftest: unknown argument --times: expected '0', got '2'`, `red-on-HEAD.log`), and it
      passes with task 2.2.
- [x] 1.3 Both cases declare `# files: tools/issues/ tools/loop-cases.sh tools/loop-selftest.sh tools/loop/`, and two
      mutation records were added beside the summary's shape (`85.1` the per-group report, `85.2` the `--times`
      figure). Verify: `bash tools/loop-mutations.sh --check` reports every one of the 248 records applies,
      `--only '85.1 85.2'` reports `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`, and
      `driver#everyCaseDeclaresTheFilesItCanReach` passes over the two new bodies.

## 2. The report

- [x] 2.1 `tools/loop-selftest.sh` records each slot's start in milliseconds (`now_ms`, `case_slot_started`), reads the
      case's own cost in `finish_case` before the slot is emptied (`case_slot_ended`, `group_cases`, `group_ms`), keeps
      the per-group order, and prints one summary line per group plus the selection's own figure (`print_group_report`,
      `suite_figure`). Verify: the two cases of task 1 pass, a `--case` run of one named case still prints no summary
      (`PASS driver#theListIsFilteredTheSameWayASuiteRunIs` and nothing else), and one existing assertion was realigned
      to the line's new shape: `driver#aGroupFilterRunsOnlyItsOwnCases` read `— selection: --group alpha` and now reads
      `s, selection: --group alpha`, because the elapsed figure sits between them (it passes, this tree).
- [x] 2.2 `--times` added to the driver's argument loop, its header comment and its `--help` range. Verify: `--times`
      adds `(N.NN s)` to each `PASS` line and changes nothing else — `PASS driver#theListIsFilteredTheSameWayASuiteRunIs
      (1.22 s)` with it, `PASS driver#theListIsFilteredTheSameWayASuiteRunIs` without it (`evidence/report-sample.txt`).
- [x] 2.3 The gate's `LOOP-GATE:` tally carrying the selection's elapsed time is already in the tree at this base
      (`tools/loop/gate.sh:281` starts each step's clock, `:292-298` names every suite beside its seconds,
      `:330-331` writes that number as the tally's `time`, `:338`/`:341` print the entries beside the counts), so this
      task changed no file. The gate is not this item's; the gate child's own run is where the two figures are read
      side by side — its `LOOP-GATE:` line and the tally it writes under the gate's tally directory.

## 3. The trim

- [x] 3.1 Measured before merging: `--group state --jobs 1`, `--group report --jobs 1`, `--group prescreen --jobs 1`
      with the report of 2.1 in place — `state` 24 cases 39.47 s, `report` 20 cases 29.54 s, `prescreen` 30 cases
      32.96 s (`evidence/groups-before.txt`, the runs' own summary lines): 74 cases, 101.97 s of case time, 1.38 s a
      case.
- [x] 3.2 Measured the merge's worth before performing it, and did not perform it: the fixture the plan blamed
      (`new_project`, `tools/loop-cases.sh:804`) is 13 ms of a 1.10–1.64 s case — 0.8–1.2 % — so merging all 74 cases
      of the three groups saves 0.95 s of the 101.97 s they pay (0.9 %), and the edits would touch 74 case bodies.
      Verify: `evidence/trim.txt` carries the probe (`runs=5 total_ms=69 per_run_ms=13`) and the arithmetic, and
      `guidelines/Backlog.recipes.md`:458 "The gate and its suite" states the rule with these numbers.
- [x] 3.3 No case is merged, so nothing was re-pointed and the mapping is empty (`evidence/merged-cases.txt`). Verify:
      `bash tools/loop-mutations.sh --check` parses and applies the whole set (248 records), and the three groups of
      task 3.1 plus the `driver` group are green with the report in place (`evidence/groups-before.txt`,
      `evidence/verification.txt`).
