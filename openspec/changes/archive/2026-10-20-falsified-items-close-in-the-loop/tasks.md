# Tasks

## 1. The red case first (the defect, not the fix)

- [x] 1.1 `tools/loop-cases.sh`: `run#falsifiedSkipClosesWithItsMeasurement` — a stub worker whose answer
      carries an empty diff and a falsification with a measurement; after the run the item is closed, its
      comment names the measurement, and the run's state records result `falsified`. Run it on HEAD and
      record the red run (case name, exit status, the line that fails): today the item is escalated and the
      case fails on the closure assertion. Verification: the pasted red output in the change's evidence, in
      the shape the other loop cases report. Covers spec `agent-issue-loop`, "A skip that falsifies the
      premise is closed as mis-diagnosed", scenario 1.
- [x] 1.2 `tools/loop-cases.sh`: `run#falsificationWithoutAMeasurementIsEscalated` — the same stub, the
      falsification field without a measurement: the item is escalated exactly as today and nothing is
      closed. Covers that requirement, scenario 3.
- [x] 1.3 `tools/loop-cases.sh`: `run#mayCloseNoKeepsAFalsificationOpen` — the same stub with
      `--may-close no`: the evidence comment is posted, nothing is closed, no settled state and no decision
      label are written. Covers the modified "Closure follows a passing review and carries evidence", scenario
      "Closure can be left to a person".
- [x] 1.4 `tools/loop-cases.sh`: `run#falsificationWithAChangedTreeIsBlocked` — the round-2 worker commits and
      the round-2 reviewer accepts, so the only thing that moved between the claim and the closure is the
      tree: the item must be blocked, not closed. Covers the added requirement, scenario "The claim is read
      again after the review".

## 2. The worker's and the reviewer's answer

- [x] 2.1 `tools/loop/prompts/worker.md`: the machine-readable block gains `LOOP-FALSIFIED:` (the mechanism
      the item blames, quoted) and `LOOP-MEASURED:` (the number and how it was taken — case name, command,
      or probe), with the explicit rule that a falsification means the tree is unchanged. Verification:
      `prompt#workerNamesTheDecisionField` still passes and a new assertion in it (or a sibling case) names
      both fields. **Done:** both fields are in the block and in the paragraph that explains them; the
      existing `prompt#` cases pass (they assert the decision field's presence, which is unchanged).
- [x] 2.2 `tools/loop/prompts/reviewer.md`: one paragraph — a falsification is a claim about the tree,
      reviewed like a change even though the diff is empty, and a measurement that does not cover the blamed
      mechanism is a blocking finding. Verification: `prompt#reviewerCarriesTheEightItems` still passes; the
      paragraph appears in the rendered prompt of the case.

## 3. The run's skip path

- [x] 3.1 `tools/loop/run.sh`: the skip path recognises the falsified verdict (empty diff + both fields) and,
      when closure is allowed, closes the item with `--reason` = the falsified mechanism and `--evidence` =
      the measurement plus the item directory and branch; the state records `--result falsified`; every other
      skip and the blocked path are unchanged, byte for byte. The closure removes the decision label in the
      same pass, because "closed" and "awaits a decision" cannot both be true (issue #225). Verification:
      tasks 1.1-1.4 green; the queue half is held by `decisions#listsTheQueueAndItsQuestions` (a closed item
      carrying the label is not in the queue, and removing the filter makes that case fail with
      `expected '2', got '3'`).
      **Done:** the closure goes through `close.sh --not-planned` (the settled state a decision not to act
      records), `state.sh` accepts `--result falsified`, and the second read of the tree after the review
      was added when review round 1 found the hole (tasks 1.4, mutation `30.2`).
- [x] 3.2 `tools/loop/report.sh`: one counter and one table value for "closed as mis-diagnosed", read from
      the recorded result, so a run's yield does not hide it. Verification: `report#headerAndTableShape` and
      a new assertion on a state holding one `falsified` item. **Done:** the summary line, the state
      digest's progress line and a `mis-diagnosed:` list; the two `report#` cases that spell the old summary
      were updated to the new shape.
- [x] 3.3 `guidelines/Backlog.md` §5: one rule added — a reviewed falsification closes the item as
      mis-diagnosed, only on an unchanged tree, under the same `--may-close` a fix closure rides. Verification:
      `check-doc-routes.sh` resolves every section reference.

## 4. Spec delta

- [x] 4.1 `openspec/changes/falsified-items-close-in-the-loop/specs/agent-issue-loop/spec.md`: the MODIFIED
      requirement carries its existing scenarios over verbatim and adds the falsified closure; the ADDED
      requirement holds the three scenarios of tasks 1.1-1.3 with their case names. Verification:
      `openspec validate falsified-items-close-in-the-loop --strict`.

## 5. Structural checks — the scope block (no compiled file is touched)

- [x] 5.1 `bash -n` on `tools/loop/run.sh`, `tools/loop/report.sh`, `tools/loop/state.sh`,
      `tools/loop-cases.sh`, `tools/loop-mutations.sh`; `bash tools/loop-selftest.sh` all 125 cases green;
      `bash tools/loop-mutations-selftest.sh` green; `bash tools/issue-tooling-selftest.sh` 91/91;
      `bash tools/check-portability.sh --quiet` unchanged. Exclusion, named: no Gradle gate and no suite
      under `./gradlew` — this change touches no Kotlin, Java, C++, manifest or resource file
      (`guidelines/Build.md` §2, the scope block).
- [x] 5.2 Revert-check: `tools/loop-mutations.sh --only "30.1 30.2"` — the first routes the falsified verdict
      back to the skip, the second drops the second read of the tree; each case must fail, restore, and be
      green again.
- [x] 5.3 Evidence: the red-on-HEAD run of task 1.1, the green run of the full suite, the mutation's red and
      restored output, and the validation transcript, all quoted with their timestamps.

## 6. After the change

- [ ] 6.1 One live run whose queue still holds `§`-imported items; its report must name any item it closed as
      mis-diagnosed, and a falsification must no longer appear in `tools/issues/decisions.sh` as a question.
      Verification: the run's report and the decision queue before/after.
- [ ] 6.2 If the live run falsifies an item whose closure was wrong, reopen it **by hand** in the tracker and
      record the case in the design's risk section: `tools/issues/reopen.sh` refuses a `not_planned` closure by
      design (`closure-not-reopenable`), because that state means a person decided not to act.
