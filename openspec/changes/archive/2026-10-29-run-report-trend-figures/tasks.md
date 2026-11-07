# Tasks — run-report-trend-figures

## 1. The report's trend block (spec: "The report states whether the loop is getting better")

- [x] 1.1 `tools/loop/report.sh` — the `trend:` block in the third section, before `actions the caller owes:`,
      with its header comment naming issue #247 and the two sources (§D1): `review: <passed>/<decided> first
      rounds passed · <n> undecided · <n> never reviewed`, `wall:   median <n> s (min <n>, max <n>) · <n> items`,
      and the `landings:` pointer naming `gh issue list --label broken-main` without making any tracker call
      (§D4a). *Verified*: `bash tools/loop/report.sh` on the tree's `.pi/issue-loop/run.json` prints
      `review: - · this state records no review verdict — it was written before the report read one` and
      `wall:   median 174 s (min 25, max 2679) · 30 items` (`evidence/live-state.log`) — that state predates the
      verdict field, which is the rule §D3 keeps.
- [x] 1.2 The two derived figure rules: the wall median, minimum and maximum over the recorded walls only, the
      count beside them and the mean of the two middle values on an even count (§D2); the pass rate read from the
      round's recorded verdict, with the undecided bucket named apart (§D3). *Verified*: both are asserted by
      `report#trendFiguresFromState` (walls 41/200/403/900/1644 → `median 403 s (min 41, max 1644) · 5 items`;
      verdicts `PASS`, `NOTES`, `PASS` at round 2, `FAIL`, `round2-ended`, `cut-short`, `off` → `2/5 first rounds
      passed · 1 undecided · 2 never reviewed`; and a two-item state → `median 151 s` for the even count).
- [x] 1.3 `tools/loop-cases.sh` — `report#trendFiguresFromState`, `report#noRoundOrWallPrintsDashNotZero`,
      `report#landingsNamedNotInvented`, each with its `# files:` declaration, registered in `LOOP_SHELL_CASES`
      and in the case dispatch. *Verified*: `bash tools/loop-selftest.sh --files tools/loop/report.sh` →
      `all 55 cases passed` (`evidence/case-filter.log`); the full suite's green is
      `gate-tallies/tools_loop-selftest.sh_--from-git_357a4670….xml`; the final run's tally is `tests="284"
      failures="0"`, 283 cases plus this change's own.
- [x] 1.4 `tools/loop-mutations.sh` — one record per invariant of §1.2/§1.3, each naming the case that goes red:
      `72.1`…`72.8` for the report, `73.1` for the cut-round branch in `run.sh`. *Verified*: the single run in
      `evidence/mutations-72.log` — `--only '72.1 … 72.8 73.1'` → nine `RED`, nine restores,
      `not-noticed: 0 · restore-failures: 0`.

## 2. The red case on HEAD

- [x] 2.1 Before §1, the lines the three cases assert are absent on HEAD: the probe's own command is
      `grep -n "first round\|round 1\|reddened\|landings:" tools/loop/report.sh` → no match, exit 1
      (`.pi/issue-loop/items/247/absence-probe.log`, 2026-10-29T09:33:28Z), copied to `evidence/red-on-head.log`
      (the `\|trend` this file used to quote was added later by hand; the absence is the same either way).
      Taken once; not re-run.

## 3. The documents the statement changes

- [x] 3.1 `guidelines/Backlog.md`, the "report a run prints at its end" paragraph: the report now also answers
      whether the loop is getting better, naming the two figures, the rule each keeps, and the one it only points
      at (issue #247, and #709 for the attribution).
- [x] 3.2 `openspec validate run-report-trend-figures --strict` → `Change 'run-report-trend-figures' is valid`
      (`evidence/openspec-validate.log`).

## 4. The gate, once

- [x] 4.1 `bash tools/loop-selftest.sh --files tools/loop/report.sh` green (55/55), and
      `bash tools/loop-mutations.sh --only '72.1 … 73.1'` → `red-when-mutated: 9 · not-noticed: 0 ·
      restore-failures: 0`, exit 0 (`evidence/mutations-72.log`).
- [x] 4.2 `bash tools/loop/gate.sh` from the change root, after the round-2 corrections: this change's own step
      is green — the shell suite's tally `tests="284" failures="0" time="442"`, `bash tools/loop-mutations.sh
      --check` `tests="217" failures="0"`, and the doc/portability/tooling suites green in their own tallies under
      `evidence/gate-tallies/` (21 files, the directory emptied first). The Gradle step is red on **another
      session's** in-flight `LocationOptionsOverlayComposeTest.kt` (`errors="1"`, `gate.log:196`), and no compiled
      file is part of this delta. Two earlier runs of this change were red for reasons outside it, both recorded in
      `evidence/evidence.md`: a declaration the case list owes, and 13 `run#` cases failing while the host had 913
      MiB free against the loop's 1024 MiB floor.

## 5. Review round 1's finding (the blocking one)

- [x] 5.1 `LOOP-BLOCKING: 1` — the rate was counted from `reviewRounds`, which `run.sh:1680` sets before the
      reviewer runs, so a refused round 1 that blocked counted as a pass (`#16` and `#197` in the tree's own
      state), and a round 1 the item's turn bound **cut** (`#51`, `LOOP-TIMEOUT`, no `VERDICT:`) was read as a
      refusal. Answered per §D3: `state.sh done --review-verdict` (closed vocabulary), `run.sh` passes its own
      `review_verdict` at the five recording sites and records the turn-bound cut as `cut-short`,
      `report.sh` counts passes from the verdict and names the undecided bucket apart,
      `report#trendFiguresFromState` gains the refused-round arm, `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`
      covers the cut, and `72.1`/`73.1` state both defects as mutations. *Verified*: `bash tools/loop-mutations.sh
      --only '72.1 … 72.8'` → eight `RED`; `--only '72.1 73.1'` → both `RED`; restores green, `not-noticed: 0`.

## 6. The item the deferred figure becomes

- [x] 6.1 Filed as **#709** — "A landing's own commit is recorded and scored, so the landings that reddened main
      are a number the run state holds": `state.sh` gains `--merged-sha`, `run.sh` passes the merge commit, and
      one scorer pass reads `gh run list --branch main --commit <sha>` per landed item and writes
      `landedReddenedMain` back, so the `landings:` line prints a stored number. Filed, not worked.

## 7. Evidence and archive

- [x] 7.1 Every cited log and tally copied into `<change>/evidence/`; `evidence/measure.txt` holds the measure
      block of the tree the change is measured on — the change's own line in it is `LOOP-MEASURE-CHANGE
      run-report-trend-figures scenarios=6 cases=6 unnamed=0`, the rest of the block is the other session's
      in-flight paths (review round 3, NOTE). *Done*: `evidence/{evidence.md,red-on-head.log,case-filter.log,
      suite-rerun.log,gate.log,gate-tallies/,openspec-validate.log,evidence-check.log,mutations-72.log,
      count-numerator.log,live-state.log,issue-709.log,submodule-status.log,another-writer.log,head-moved.log,
      measure.txt}`.
- [x] 7.2 `bash tools/loop/evidence-check.sh openspec/changes/run-report-trend-figures --root .` →
      `evidence-check: PASS` (C1 3 §references, C2 4 pairs confirmed, C3 none, C4 none, C5 524 resolved, 9 unresolved — none of them this change's), and
      `openspec validate run-report-trend-figures --strict` → `is valid` (`evidence/evidence-check.log`,
      `evidence/openspec-validate.log`).
- [x] 7.3 Archived; the requirement and its six scenarios merged into `openspec/specs/agent-issue-loop/spec.md`.
      Review round 3 returned a zero blocking count and `VERDICT: PASS`, which licenses the archive.
- [x] 7.4 Both review rounds' findings answered (§5.1, plus the NOTE-class corrections: the `# files:` declarations,
      the two earlier red runs recorded with their causes, `gate-tallies/` emptied before the copy, the abridged
      gate quote marked as abridged, `submodule-status.log` for the dirty submodule, `issue-709.log` for the
      filed item, and the line references moved by this change's own edits).
