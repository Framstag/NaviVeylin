# Tasks — the recorded repeat-read arm is the refusal

Every step is a step of this pass; the change is a recorded value, one case and the prose the value falsifies.

## 1. The red case, before the value moves

- [x] 1.1 Add `adapter#theRecordedRepeatArmIsRefuse` to `tools/loop/tests/loop-cases.sh` (the case list, the dispatch
  table, and a body whose first line declares `# files: tools/loop/loop.conf`): it reads `LOOP_REPEAT_READ` out of
  `$LOOP_CONF` with the adapter's own grammar and asserts the value is `refuse`. Requirement: scenario "The arm the
  project records is the refusal".
- [x] 1.2 Verify the case is red with the value still `measure`:
  `bash tools/loop/tests/loop-selftest.sh --case adapter#theRecordedRepeatArmIsRefuse` →
  `FAIL … expected 'refuse', got 'measure'`, `1 of 1 cases failed`, exit 1. Evidence: `evidence/red-on-HEAD.log`.

## 2. The value

- [x] 2.1 `tools/loop/loop.conf` — `LOOP_REPEAT_READ=measure` → `=refuse`, and the key's comment carries #976's
  paragraph: the archive's 25–76 % / median 66 % census, what a needed re-read is still answered with, `measure` as the
  fallback for an undeclared word and for a project that records no key, and the arm as an answer and never a verdict.
- [x] 2.2 Verify the case turns green and its neighbours stay green:
  `adapter#theRecordedRepeatArmIsRefuse`, `adapter#theChildIsStartedWithTheRepeatReadArm`,
  `adapter#aPathIsRefusedAfterItsRepeatBound` — `PASS` each. Evidence: `evidence/green-focused.log`.

## 3. The prose the value falsifies

- [x] 3.1 `tools/pi-trim-result.mjs` — the header's "`measure`, **the recorded default**" paragraph and
  `recordedRepeatArm`'s doc state the recorded value and why, and keep the fallback's reason.
- [x] 3.2 `tools/pi-child-events.sh` — the arm paragraph names `refuse` as the value the file records.
- [x] 3.3 `tools/loop/prompts/worker.md` — the repeat-rule paragraph names the recorded arm rather than a condition.
- [x] 3.4 `guidelines/Backlog.recipes.md` §7 — the arm paragraph names `refuse` as the recorded value and `measure` as
  the fallback.
- [x] 3.5 `tools/loop/tests/loop-cases.sh` — the neighbour case's comment no longer calls `measure` the recorded value,
  and `case_repeat_aRepeatReadIsCountedAndTheRefusalArmWithholdsIt`'s header says which arm is recorded.

## 4. The capability delta

- [x] 4.1 `specs/agent-issue-loop/spec.md` — one `MODIFIED` requirement, "A second read of a path a child was handed is
  counted, and the arm decides its answer", carrying its body and its three shipped scenarios verbatim plus the added
  scenario the new case names.
- [x] 4.2 Verify: `openspec validate fix-the-recorded-repeat-arm-is-refuse --strict`. Evidence:
  `evidence/openspec-validate.log`.

## 5. Falsification and the checks this delta owes

- [x] 5.1 Mutation record `976.1` in `tools/loop/tests/loop-mutations.sh` rewrites the recorded line back to
  `measure`; the case must go red and its restore green. Verified:
  `bash tools/loop/tests/loop-mutations.sh --check --only 976.1` and `--only 976.1`. Evidence:
  `evidence/mutations.log`.
- [x] 5.2 No compiled file is touched, so no build stands in for one; the structural checks of the files edited are
  `bash -n tools/loop/tests/loop-cases.sh`, `bash -n tools/loop/tests/loop-mutations.sh`,
  `bash -n tools/pi-child-events.sh`, `node --check tools/pi-trim-result.mjs`, `bash tools/check-case-names.sh`,
  `bash tools/check-doc-values.sh`, `bash tools/check-doc-routes.sh`, `bash tools/check-glossary.sh` and
  `bash tools/check-portability.sh` — all green. `bash tools/check-openspec-hygiene.sh` exits 1 on this tree for 19
  errors, **all of them other changes** (none names this one, `grep -c` reports 0), which is the state it is on the
  base and is recorded as such rather than claimed green. Evidence: `evidence/structural-checks.log`.
- [x] 5.3 The suite a `tools/loop/tests/loop-cases.sh` delta owes is not run whole here (546 cases, ~9 minutes at the
  recorded 545 s); the focused cases and the mutation are, and the full selection is named as unrun in
  `evidence/green-focused.log` rather than claimed green.

## 6. Measure after

- [ ] 6.1 The next run of the loop reports `metrics.repeatReads`, `metrics.dupChars` and `withheld.chars`
  (`tools/loop/report.sh` over the run's `state.json` and `items/*/tool-result-trims.log`), and no item buys an extra
  turn for a withheld repeat. This is a reading of a run, not of this change: name it in the item's closing comment.