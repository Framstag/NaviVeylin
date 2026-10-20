# Tasks — fix-loop-report-answers

## 1. The red case first

- [x] 1.1 Write `report#wallTimeAndReasonInTheTable` in `tools/loop-cases.sh`, register it in the `--list`
      array and in the dispatch map (spec: "One command starts a budgeted run, with no dialogue" — scenario
      "A run is recorded"); verify by running it on HEAD: it fails with `'| wall |' not found in: | issue |
      class | verdict | change | root cause | red test | gate | review | outcome |`, recorded in
      `evidence/red/wallTimeAndReasonInTheTable.txt`.
- [x] 1.2 Write `report#actionsNameDecisionAndBranch` (spec: scenario "The report names the actions the caller
      owes") and `report#emptyRunSaysNone` (spec: scenario "An action list is empty only when the state says
      so"), registered in both places; verify each fails on HEAD with `'decide #21' not found in:` and
      `'actions the caller owes:' not found in:` — `evidence/red/`.

## 2. The table carries the wall time and the reason

- [x] 2.1 `tools/loop/state.sh` `cmd_report`: append the `wall` column from `.metrics.wallSeconds`
      (`12m34s`, `42s`, `-`) and the `reason` column from `.note` with `.metrics.rootCause` as its fallback,
      single-lined, `-` for a closure; verify with `bash tools/loop-selftest.sh --case
      'report#headerAndTableShape'` (the earlier nine columns keep their place and a row per item is still
      printed) and `--case 'report#wallTimeAndReasonInTheTable'` (spec: scenario "A run is recorded").
- [x] 2.2 Confirm no existing reader loses a column: `--case 'report#yieldIsCountedFromState'` and the three
      decisions cases stay green — `evidence/green/`.

## 3. The tail is the actions the caller owes

- [x] 3.1 `tools/loop/report.sh`: build the `actions the caller owes:` block from the state and from git —
      decisions (item escalations and `decisionAgent` exclusions) with their question or the place it is;
      branches whose `rev-list --count base..branch` is above zero with their commit count and owning item;
      the kept worktree; items whose gate never completed with their transcript; `excludedStaleAgent`
      exclusions with `tools/issues/resolve-stale.sh --id <n>`; and `none` only when all five are empty
      (spec: scenarios "The report names the actions the caller owes" and "An action list is empty only when
      the state says so"). Verify with `--case 'report#actionsNameDecisionAndBranch'` and `--case
      'report#emptyRunSaysNone'`.
- [x] 3.2 A range git cannot answer is named rather than counted as zero, so `none` can never hide a branch;
      verify by the assertion in `report#actionsNameDecisionAndBranch` that the block does not print
      `none — no decision` while a branch is ahead of the base.
- [x] 3.3 Re-run the end-of-run path that prints the report: `--case 'run#stopReasonIsPrintedAndReported'`
      (spec: scenario "The run stops on its own authority" — the report the run prints is still the one the
      state agrees with).

## 4. Documents that quote the report

- [x] 4.1 `.pi/skills/fix-loop/SKILL.md`, "Report (end of run)": the template gains the `wall` and `reason`
      columns and the action list, and says the list is the answer to "what must I do now" (spec: scenarios
      "A run is recorded", "The report names the actions the caller owes"); verify by reading the template
      against `bash tools/loop-selftest.sh --case 'report#headerAndTableShape'` output.
- [x] 4.2 `guidelines/Backlog.md` §7 "The loop": state the three questions the report answers and the
      honesty rule behind `none` (spec: both action-list scenarios); verify with
      `grep -n 'answers three questions without the caller' guidelines/Backlog.md`.

## 5. Structural checks — the scope block (no compiled file is touched)

- [x] 5.1 `bash -n tools/loop/state.sh`, `bash -n tools/loop/report.sh`, `bash -n tools/loop-cases.sh`: no
      syntax error; `bash tools/check-portability.sh --quiet`: 42 engines name no project value;
      `openspec validate fix-loop-report-answers --strict` and `openspec doctor` clean — all recorded in
      `evidence/green/validation.txt`.
- [x] 5.2 No Gradle, no device: the change touches no Kotlin, Java, C++, manifest or resource file, so
      `guidelines/Build.md` §2's scope block names the structural checks instead of the gate. Exclusion:
      the full offline suite (`tools/loop-selftest.sh`) is not run here — the orchestrator runs it once on the
      merged tree.
- [x] 5.3 Evidence: the three new cases red on HEAD (`evidence/red/`) and green on the fix, together with the
      six existing cases on this surface (`evidence/green/`).
