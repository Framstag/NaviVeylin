# Tasks

Reference: `proposal.md` for the scope and the red run, `design.md` for why, and
`specs/shaping-inbox/spec.md` for the contract.

## 1. The red case

Requirement: "A shaping verdict carries the shaping label", scenario "The labels a skip leaves are the set the
profile names".

- [x] 1.1 Add `case_run_aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames` to `tools/loop-cases.sh`, registered in
      `LOOP_SHELL_CASES` and in the dispatch table, driving a `needs-diagnosis` skip through `run_loop` and comparing
      the labels the skip's own write adds (the edit that carries `--add-label status/on-hold`) with the set read from
      the profile: the decision label, the hold, and the shaping label only when `roles.shapingVerdicts` names the
      verdict. Its first line declares `tools/loop/fixtures/scenarios/`, `tools/loop/run.sh`, `tools/loop/labels.sh`,
      `tools/pi-child-events.sh` and `tools/tooling.json`. Verify: on the base `b0daba96` the case fails at its one
      assertion with `expected 'agent/needs-decision agent/needs-shaping status/on-hold', got 'agent/needs-decision
      status/on-hold'`, `1 of 1 cases failed`, 2026-11-07T20:30:57Z–20:31:04Z (`evidence/red-on-HEAD.log`; tally
      `evidence/red-on-HEAD.xml`, `tests=1 failures=1 errors=0`).

## 2. The profile and the requirement

- [x] 2.1 `tools/tooling.json:47`: `roles.shapingVerdicts` loses `needs-diagnosis`, so the list is
      `["unspecifiable", "too-wide", "not-spec-shaped"]` and declares no label the run never writes for that verdict.
- [x] 2.2 `specs/shaping-inbox/spec.md`: the shipped requirement "A shaping verdict carries the shaping label" is
      restated with three answers — the "one root cause does not explain this" answer is dropped — and gains the
      scenario "The labels a skip leaves are the set the profile names", whose case is 1.1, beside the three
      scenarios it already carried. Verify for 2.1–2.2: `./tools/loop/measure.sh --change
      fix-needs-diagnosis-is-no-shaping-verdict` reports `scenarios=4 cases=4 unnamed=0` and `cases=369` for
      `tools/loop-selftest.sh --list`, and 1.1 passes (§5.1).

## 3. The documents that counted four

- [x] 3.1 `guidelines/Backlog.md:194` (§5 "Who may write what"): the shaping-inbox paragraph says three verdicts,
      names them, and states that a `needs-diagnosis` skip is escalated with `agent/needs-decision` and writes no
      shaping label. Verify: `bash tools/check-doc-routes.sh` — `all 329 section references resolve, all 7 skill
      references resolve` (`evidence/doc-routes.log`).
- [x] 3.2 `tools/loop/labels.sh`: the transition comment (`:18`) names the three verdicts, and the write's comment
      (`:148`) drops the "or, for `needs-diagnosis`, the decision" parenthetical in favour of the sentence that the
      verdict states a question. No code line changes. Verify: `bash -n tools/loop/labels.sh`
      (`evidence/structural.log`).

## 4. The cases that asserted the old shape

- [x] 4.1 `labels#verdictToLabelMatrix` (`tools/loop-cases.sh:3630`) expects
      `agent/needs-decision status/on-hold` for `needs-diagnosis` and its comment says three; the verdict loop above
      it is unchanged. Verify: §5.2.
- [x] 4.2 `run#aShapingVerdictWritesTheLabel` (`tools/loop-cases.sh:4281`) expects no shaping label for
      `needs-diagnosis` (`grep -c 'needs-shaping'` = `0`) and its comment says the verdict keeps the decision label.
      Verify: §5.2, and `bash tools/loop-mutations.sh --only 75.1` still goes `RED` — the record mutates the shaping
      write the three verdicts in the list still owe, which this change leaves in place (§5.7).

- [x] 4.3 `tools/loop-mutations.sh`: one record, `261.1`, puts `needs-diagnosis` back in the profile's list so 1.1
      goes red. Verify: §5.3 and §5.4.

## 5. Verification

- [x] 5.0 The first check the plan's delta owes, run before the rest: `bash tools/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` — `PASS`, 2026-11-07T20:32:53Z–20:33:04Z (`evidence/first-check.log`),
      the case 1.1's declaration owes.
- [x] 5.1 The red case passes on the change: `bash tools/loop-selftest.sh --case
      run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames` — `PASS`, 2026-11-07T20:31:31Z
      (`evidence/green-on-change.log`), against the red run in `evidence/red-on-HEAD.log`; reverting the profile line
      is that red run.
- [x] 5.2 The cases the delta's paths declare, run one suite call each: `run#aShapingVerdictWritesTheLabel`,
      `run#aVerdictOutsideTheListWritesNoLabel`, `labels#verdictToLabelMatrix`,
      `report#escalatedSkipNamesTheLabelWritten`, `run#theShapingInboxDoesNotCountAgainstTheCeiling` — all `PASS`,
      2026-11-07T20:31:31Z–20:32:01Z (`evidence/green-on-change.log`) — and
      `shaping#anEmptyListIsAnEmptyQueue` in the tooling suite, `PASS` (`evidence/green-on-change.log`).
- [x] 5.3 The revert-check: `bash tools/loop-mutations.sh --only 261.1` — `RED    261.1
      run#aNeedsDiagnosisSkipCarriesTheLabelsTheProfileNames` with the assertion's own message, then `restore 261.1 …
      green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`, 2026-11-07T20:32:30Z–20:32:43Z
      (`evidence/mutation-261.1.log`).
- [x] 5.4 Every record still applies to the file it names: `bash tools/loop-mutations.sh --check` — `all 285 cases
      passed — every selected record applies to the file it names` (`evidence/mutation-check.log`).
- [x] 5.5 The structural checks of the three files the diff touches: `bash -n tools/loop-cases.sh`,
      `bash -n tools/loop/labels.sh`, `bash -n tools/loop-mutations.sh` and `jq empty tools/tooling.json`, all exit 0
      (`evidence/structural.log`).
- [x] 5.6 `bash tools/check-change-readiness.sh openspec/changes/fix-needs-diagnosis-is-no-shaping-verdict` reports
      `LOOP-CHANGE-READY: ok` (`evidence/readiness.log`); `bash tools/check-openspec-hygiene.sh` reports
      `OpenSpec artifacts are consistent: 9 open change(s) carry task markers, the operation guidance is quoted, and
      every capability spec states its Purpose` (`evidence/hygiene.log`); `bash tools/check-portability.sh` reports
      `OK — 56 engines and 7 templates name no project value (118 value(s) read from the project)`
      (`evidence/portability.log`); `bash tools/check-doc-routes.sh` is §3.1's run (`evidence/doc-routes.log`).

- [x] 5.7 The record the realigned case is named by still applies: `bash tools/loop-mutations.sh --only 75.1` —
      `RED    75.1  run#aShapingVerdictWritesTheLabel` on `the shaping label rides that write: '--add-label
      agent/needs-shaping' not found`, then `restore 75.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 ·
      restore-failures: 0`, 2026-11-07T20:33:26Z–20:33:31Z (`evidence/mutation-75.1.log`).

The gate is not this change's to run: it is deferred to the fresh child that applies the review's findings, so the
loop's whole self-test is run once, there.
