# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red case

Requirement: "A cut item is named to the caller as work to resume", scenario "A cut item is named once, and its kept
work travels on that line".

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: add `report#aCutItemIsNamedOnceAndItsKeptWorkWithIt` — a state holding the
      record of a bound-cut item with a kept patch (`cut`, `bound`, `--leftover`) and the record a tree wrote before
      `cut` existed for the same kind of end (`blocked` with `--leftover`) — asserting `resume #21` once, no
      `recover #21`, the patch path and the note's own bound on the resume line, and `recover #22` once with no
      `resume #22`. It is declared `# files: tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh
      tools/tooling.json` and registered in the suite's case list and its dispatcher. Verify: on HEAD `8243410e` the
      case fails on `'recover #21' should not appear in:` — the report prints `resume #21` and `recover #21` for one
      record — `tests=1 failures=1`, 2026-11-11T17:12:16Z (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`).

## 2. The report

- [x] 2.1 `tools/loop/report.sh`: the recovery arm's guard becomes `.result != "cut"` beside the patch test, so the two
      arms partition the records that carry a kept patch by the result the state records; the comment above it says
      why the guard is the result and not the patch.
- [x] 2.2 `tools/loop/report.sh`: the resume line names the kept patch (`${patch:+ · its work is kept at $patch}`), so the
      fact #674 put in the action list is on the line that names the item.
- [x] 2.3 `tools/loop/tests/loop-cases.sh`: rewrite `run#treeOfACapEndedChildIsKeptAsAPatch`'s report assertions from
      `recover #21` to `resume #21` with the patch path, plus the absence of `recover #21`. Verify: the record this
      round carries has no run of the rewritten case — `evidence/red-on-HEAD.log`/`.xml` run only the case added in
      1.1 and `evidence/green-report-group.log` runs the `report` group plus
      `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`; this case's own run is the gate's suite run.

## 3. The rule's own text

- [x] 3.1 `guidelines/Backlog.recipes.md`:1369 "a named action list": the action-list paragraph names the bound-cut line and the kept-patch
      line, states that an item is named once, and counts the seven sources the report now reads (it says five, which
      was last true before #674's recovery line and #788's resume line). Verify: `tools/check-doc-routes.sh` and
      `tools/check-openspec-hygiene.sh` over the changed files (`evidence/structural-checks.log`).
- [x] 3.2 The change's `specs/agent-issue-loop/spec.md`: one scenario on the existing requirement, pinning the one-line
      rule and the recovery line's own record, each naming exactly one case. Verify: the delta's scenario names
      `report#aCutItemIsNamedOnceAndItsKeptWorkWithIt`, which is the case added in 1.1, and
      `tools/check-change-readiness.sh openspec/changes/fix-a-cut-item-is-named-once` reads the delta against it —
      `LOOP-CHANGE-READY: ok` (`evidence/structural-checks.log`), beside
      `LOOP-MEASURE-CHANGE fix-a-cut-item-is-named-once scenarios=2 cases=2 unnamed=0`
      (`evidence/measure-block.log`).

## 4. Verification

- [x] 4.1 The red case passes on the change, with the whole `report` group: `report 34 cases 216.24 s`,
      `all 34 cases passed`, and `run#aReviewerEndedAtItsTurnBoundIsNotARefusal` PASS
      (`evidence/green-report-group.log`, 2026-11-11T17:13:38Z). The rewritten case
      `run#treeOfACapEndedChildIsKeptAsAPatch` carries no run in this record; the gate's suite run settles it.
- [x] 4.2 Mutation records `275.1` (the recovery arm's guard restored to the patch test) and `275.2` (the resume
      line's patch dropped) each turn the case red and green again after the restore — `red-when-mutated: 1 ·
      not-noticed: 0 · restore-failures: 0` for each (`evidence/mutations-275.log`, 2026-11-11T17:15:31Z and
      17:15:40Z).
- [x] 4.3 No compiled file is touched, so no build and no Gradle suite is owed: the structural checks of the files
      this delta edits stand in, and are named in 3.1.
- [x] 4.4 `./tools/loop/measure.sh --change fix-a-cut-item-is-named-once` quoted into
      `evidence/measure-block.log` — `LOOP-MEASURE-SUITE cases=410`, `LOOP-MEASURE-CHANGE
      fix-a-cut-item-is-named-once scenarios=2 cases=2 unnamed=0`, `LOOP-MEASURE-GATE seconds=-` (the gate has not run;
      this item's gate is the run's to take, after this diff is read).
