# Tasks

Reference: `proposal.md` for scope and the red run, `design.md` for why, `specs/agent-issue-loop/spec.md` for the
contract.

## 1. The red case

Requirement: "The run verifies rather than trusts", scenario "A phase that answers nothing is that phase's failure".

- [x] 1.1 `tools/loop-cases.sh`: `run#aPhaseThatAnswersNothingIsThatPhasesFailure` — `new_project` plus `stub_write`
      with `STUB_CHANGE_REPORT` (the diff marker and no verdict), a passing pre-gate round, an action that leaves the
      committed work in the tree, and `PI_STUB_REPORT_GATE` pointed at an empty file; then `run_loop --review on`
      asserted on the two gate invocations (`worker-gate-prompt.md`, `worker-gate-prompt-retry.md`), the `worker-gate`
      step (`fail`, note `exit 0 and no report`), the recorded block, the reason naming the phase and carrying neither
      of the two tree arms, no `worker-tail-report.md`, and nothing closed. Its list entry and its dispatch entry are
      added with it. Verify: on the base `8280d8aa` it fails (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`,
      2026-11-07T23:06:36Z), and on the change it passes (`evidence/green-red-case.log`); re-run on the tree as it
      stands in the correction round — `evidence/green-red-case-round2.log`, 2026-11-07T23:40:24Z.
- [x] 1.2 The case's first line declares `tools/loop/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh
      tools/tooling.json` — what `new_project`, `scenario` and `run_loop` reach. Verify:
      `bash tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
      (`evidence/green-declaration.log`); re-run on the tree as
      it stands in the correction round (`evidence/green-declaration-round2.log`, 2026-11-07T23:40:35Z).

## 2. The engine

- [x] 2.1 `tools/loop/run.sh`: `gate_answered` beside `answer_complete` — the completeness reading with the gate
      prompt's closing lines, excluding a report that carries `LOOP-TIMEOUT` or `LOOP-DEADLINE`. Verify: `bash -n
      tools/loop/run.sh`, then the red case green.
- [x] 2.2 The phase's own block: the prompt's values built once into `gate_prompt_values`, the retry rendered from
      that same list into `worker-gate-prompt-retry.md` and answered into `worker-gate-report-retry.md`, and the
      second silence setting `phase_failure` and the step's note `exit 0 and no report`, with the step's status read
      off the reading instead of the exit status. Verify: the new case asserts both invocations and the step.
- [x] 2.3 `phase_failure=""` beside `empty_tree_reading`; the tail's guard carries `[ -z "$phase_failure" ]`; the
      outcome block's new arm records the item `blocked` with `blocked_reason="$phase_failure"` after the tail's
      verdict arms. Verify: the new case, and `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` for the answer
      that claims nothing (the run's own stop is unchanged).
- [x] 2.4 `tools/loop-mutations.sh`: record 101.1 (`tools/loop/run.sh`, case
      `run#aPhaseThatAnswersNothingIsThatPhasesFailure`, the completeness reading replaced with `if false; then`),
      and 76.1 re-pointed at the tail guard line it quotes. Verify: `bash tools/loop-mutations.sh --check` → all 287
      records apply (`evidence/mutation-check.log`); 101.1 red-when-mutated and green on restore
      (`evidence/mutation-101.1.log`, 2026-11-07T23:10:18Z).

## 3. The prose the rule lives in

- [x] 3.1 The change's spec delta: "The run verifies rather than trusts" gains the scenario and the body clause that
      makes a phase's report that phase's answer, and carries the four scenarios the shipped requirement has, each
      with the case it already has. Verify: `tools/check-openspec-hygiene.sh`.
- [x] 3.2 `guidelines/Backlog.recipes.md:719` "Refusals, records and the landing": the paragraph on what the run does
      with a child that never started gains what it does with a phase that answers nothing — asked once more, then
      that phase's failure, with the phase's name in the item's reason. Verify: `tools/check-doc-routes.sh` and
      `tools/check-todo-references.sh`.

## 4. Verification

- [x] 4.1 The declaration case first, then the red case, then the group the reading can move —
      `bash tools/loop-selftest.sh --group run` → 135 cases, all passed in 159.83 s
      (`evidence/green-run-group.log`); re-run on the tree as it stands in the correction round → 135 cases, all passed
      in 166.37 s (`evidence/green-run-group-round2.log`, 2026-11-07T23:40:38Z → 23:43:25Z).
- [x] 4.2 The gate was the change child's in round 1 (the run deferred it there) and is this correction round's own
      session: `./tools/loop/gate.sh` run once from the change root → `LOOP-GATE: tests=953 failures=0 errors=1` (a shell-suite sum, not retained as one JUnit XML — `evidence/gate-round2.log` carries the print), the
      one error being `tools/check-rule-routes.sh` — red before this change and outside it
      (`evidence/gate-waived-check-rule-routes.md`). Its suite step selected and ran all 371 cases, the change's own
      among them (`evidence/gate-round2.log`; `evidence/gate-suite-tally.xml` — `tests="371" failures="0" errors="0"`).

## 5. The correction round's answers (review round 1)

The round before the gate refused this change with one blocking finding: the change's own case had never been executed
against the tree as it stands — the gate's suite step (`tools/loop-selftest.sh --from-git a3cefa93…`) printed
`selected 368 of 368 cases` and did not carry `run#aPhaseThatAnswersNothingIsThatPhasesFailure`
(`.pi/issue-loop/items/810/gate-steps/step-1.out`), and the case's own last run (`evidence/green-red-case.log`,
2026-11-07T23:07:10Z) predates the gate child's own edit to `tools/loop/run.sh` (2026-11-07T23:29:40Z). The
correction is the two runs the finding asks for — no new claim, case or assertion:

- [x] 5.1 `bash tools/loop-selftest.sh --case 'run#aPhaseThatAnswersNothingIsThatPhasesFailure'` on the tree as it
      stands → `PASS` (`evidence/green-red-case-round2.log`, 2026-11-07T23:40:24Z).
- [x] 5.2 `bash tools/loop-selftest.sh --group run` on the same tree → 135 cases, all passed in 166.37 s
      (`evidence/green-run-group-round2.log`).
- [x] 5.3 The wording the round's notes name: the exclusion of a bound-ended child is the exit status
      (`LOOP_CHILD_TIMEOUT`, `tools/loop/lib.sh:27`), not a line in the report — `guidelines/Backlog.recipes.md` §7d,
      `design.md` and `proposal.md` say so now; the waived step's record names the diff it reads
      (`review-pre-gate.diff`) rather than the whole change (`evidence/gate-waived-check-rule-routes.md`); and the
      proposal's "Verified in the tree" line names the base it was taken on.

## 6. The correction round's answers (review round 2)

The round before this one refused the change with one blocking finding: the change's spec delta grows the shipped
requirement "The run verifies rather than trusts" to 558 characters, over the 500 `AGENTS.md` and
`openspec/specs/documentation-ownership/spec.md` state a requirement's body stays at, so the change added a 37th
`Requirement text is very long (>500 characters)` warning to `openspec/specs/agent-issue-loop/spec.md` (36 at the base)
and its own evidence recorded it as accepted. The correction is subtractive — the scenario the change adds already
states the contract in full, so the body's sentence restates what the scenario says:

- [x] 6.1 The requirement's body, in the shipped spec (`openspec/specs/agent-issue-loop/spec.md:546`) and in the
      archived delta (`openspec/changes/archive/2026-11-08-fix-a-phase-that-answers-nothing/specs/agent-issue-loop/spec.md`),
      now reads the same text: the base's four lines and the clause "Each phase's own report SHALL be read as that
      phase's answer, and a phase whose child answers nothing is a failed phase." — 448 characters and 452 bytes, against
      the base's 327 and 331 (`evidence/doc-checks-round3.log`, the two bodies counted with awk over `sed -n` of each).
      Verify: `openspec validate agent-issue-loop --strict` → 36 warnings, none of them `requirements[5]`
      (`evidence/doc-checks-round3.log`); the base's 36 is recorded in `evidence/doc-checks-round2.log`.
- [x] 6.2 The wording the round's notes name: task 1.2's "it ran first, before any other line of this change" is
      gone — `evidence/green-declaration.log` carries no timestamp, so the ordering is not in the record; task 4.1's
      group-run window reads the log's own start (`2026-11-07T23:40:38Z → 23:43:25Z`); the `#755` figures
      (`11 m 49 s`, `13.0 Mtok`) are attributed in `design.md` to `#755`'s tracker text and the run's state record, the
      attribution the proposal and §7d already carry; and the mutation record 101.1 was run again on the tree as it
      stands — red when mutated, green on restore (`evidence/mutation-101.1-round3.log`, 2026-11-08T00:04:06Z). Its
      own restore leaves `tools/loop/run.sh` byte for byte what it was (`git status --porcelain` names no engine
      file), and the gate below ran after it (2026-11-08T00:04:32Z).
- [x] 6.3 The gate, run once on the corrected tree: `./tools/loop/gate.sh` → `LOOP-GATE: tests=953 failures=0 errors=1` (a shell-suite sum, not retained as one JUnit XML — `evidence/gate-round3.log` carries the print), 2026-11-08T00:04:32Z → 00:12:18Z — the one error being `tools/check-rule-routes.sh`,
      red before this change and outside it (`evidence/gate-waived-check-rule-routes.md`, whose round-3 section names
      this gate's own print).
- [x] 6.4 The three checks that read `openspec/**` were re-run after this round's last prose edits — the edits of
      tasks 6.2 and 6.3, and the waiver file's round-3 section, all markdown inside the change's own directory
      (`evidence/doc-checks-round3.log`, its final block) — and the pointer check over the archived change was run
      after them (`evidence/evidence-check-round3.log`): `evidence-check: PASS`.
