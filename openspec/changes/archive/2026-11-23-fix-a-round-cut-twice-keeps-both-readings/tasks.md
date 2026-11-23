# Tasks — the two readings of a round the run cut twice are kept

## 1. The red case

- [x] 1.1 Add `run#aRoundCutTwiceKeepsBothReadingsFindings` to `LOOP_SHELL_CASES`, its dispatch arm and its body in
  `tools/loop/tests/loop-cases.sh`: the round before the gate is ended at the reviewer's turn bound, the run restarts
  it, the restart is ended at the same bound, and the case asserts the item is still `cut`, the file `review-findings.md`
  exists beside the item's reports, both readings' `BLOCKING:` lines stand in it under their own report names, the
  item's note names the file, and the report's resume line carries that note
- [x] 1.2 Run it against the base commit with `tools/loop/tests/loop-selftest.sh --case
  'run#aRoundCutTwiceKeepsBothReadingsFindings'` and keep the log: `evidence/red-on-HEAD.log`

## 2. The engine

- [x] 2.1 `tools/loop/evidence.sh`: `review_findings_part` (one reading's findings, quoted to the cap with the
  `[LOOP-CUT: …]` marker), `review_findings_evidence` (the one file, both named sections, silent when neither reading
  wrote a finding) and `review_findings_for_double_cut` (the report-name predicate)
- [x] 2.2 `tools/loop/run.sh`: `review_findings_file=""` in the per-item state block, reset beside `review_restart_line`
- [x] 2.3 `tools/loop/run.sh`: the four bound-cut arms — the pre-gate deadline arm, the pre-gate bound arm, the
  post-gate review-deadline arm and the post-gate reviewer-bound arm — call `review_findings_for_double_cut` with the
  report they already hold
- [x] 2.4 `tools/loop/run.sh`: the `cut)` arm's note gains the clause that names the file when one was written, so the
  report's one resume line carries it

## 3. The declarations

- [x] 3.1 `tools/loop/layout.tsv`: the three functions in `evidence.sh`'s `owns` cell
- [x] 3.2 `tools/loop/shared.tsv`: `review_findings_for_double_cut` assigned by `evidence.sh`, read by `run.sh`
- [x] 3.3 `tools/loop/events.tsv`: the three names declared as composers, because each prints a value a file or a
  caller takes

## 4. Evidence

- [x] 4.1 `tools/loop/tests/loop-selftest.sh --case 'run#aRoundCutTwiceKeepsBothReadingsFindings'` green:
  `evidence/green-on-fix.log`
- [x] 4.2 `tools/loop/tests/loop-selftest.sh --group driver` green — the three declaration checks the new functions
  owe (`driver#anOwnedFunctionIsInItsOwnersFile`, `driver#theSharedNamesAreDeclared`,
  `driver#everyPrintOnlySiteIsConverted`)
- [x] 4.3 `tools/loop/tests/loop-mutations.sh --only '987.1 987.2 987.3'` red-when-mutated: the note clause, the write
  of the two readings, and the report-name predicate — `evidence/mutations.log`
- [x] 4.4 `tools/loop/tests/loop-mutations.sh --check` — every record applies to the file it names
- [x] 4.5 The gate the delta owes, `tools/loop/gate.sh --base e3a5afd8`: its summary line reports 1260 cases and no
  failure across its 14 steps, and the whole selftest suite is green — `tests="545" failures="0"`, the tally
  `evidence/tools_loop_tests_loop-selftest.sh_--from-git_e3a5afd8.xml`, kept beside `evidence/gate.log`
- [x] 4.6 `tools/check-change-readiness.sh openspec/changes/fix-a-round-cut-twice-keeps-both-readings` reports
  `LOOP-CHANGE-READY: ok`, and `tools/check-openspec-hygiene.sh` reports no finding for this change
- [x] 4.7 `tools/loop/evidence-check.sh openspec/changes/fix-a-round-cut-twice-keeps-both-readings` — this change's
  evidence, in the form it travels into the archive
