# Tasks — a restart quotes the reading it replaced

## 1. The red cases

- [x] 1.1 `tools/loop/tests/loop-cases.sh`: `run#aRestartQuotesTheRetriedReadingItReplaced` — the round before the
  gate whose first answer was cut on the way to the file, whose retry ended at the reviewer's turn bound and whose
  restart ended at that bound; the case asserts the item is still `cut` and that the first section of
  `review-findings.md` quotes the retry's `NOTE:` line under `pre-gate-review-retry.md`, the restart's reading
  beside it, and no reading that wrote nothing.
- [x] 1.2 `tools/loop/tests/loop-cases.sh`: `run#aRetriedRestartQuotesTheReadingItRetried` — round 1 ended at the
  turn bound, its restart cut on the way to the file, the retry of that restart ended at the bound; the case
  asserts the first section quotes the restart's `NOTE:` line under `reviewer-report-1-restart.md`, the retry's
  reading beside it, and not the round's first answer.
- [x] 1.3 Both cases registered in `LOOP_SHELL_CASES` and in the `loop_run_shell_case` dispatch, each with its
  `# files:` declaration.
  *Verified*: `driver#everyCaseDeclaresTheFilesItCanReach` green (`evidence/green-declaration.log`).
- [x] 1.4 Run both against the base `251e7abe` with the engine unmodified, and keep the logs:
  `evidence/red-on-HEAD-aRestartQuotesTheRetriedReadingItReplaced.log`,
  `evidence/red-on-HEAD-aRetriedRestartQuotesTheReadingItRetried.log`.
  *Verified*: each log's last lines read `FAIL <case>` — the first section names the wrong report.

## 2. The engine

- [x] 2.1 `tools/loop/evidence.sh:811`: `review_findings_for_double_cut <item dir> <the reading's report> <the
  reading it replaced>` — the name still decides whether the reading is a restart, and quoting nothing when the
  replaced reading is empty or is the report itself.
- [x] 2.2 `tools/loop/run.sh:1522-1523`: `pre_gate_replaced_report` and `review_replaced_report` declared with the
  item's other per-item state.
- [x] 2.3 `tools/loop/run.sh:1643`, `:1664`, `:2328`, `:2358`: the report the run is about to stop reading is
  captured on each site that names the next attempt — the transport-cut retry and the restart, before the gate and
  after it — and `:2302` resets it per round.
- [x] 2.4 `tools/loop/run.sh:1696`, `:1709`, `:2406`, `:2433`: the four bound-cut arms hand the captured report to
  the evidence call instead of letting it derive one from the restart's name.
  *Verified*: both new cases green (`evidence/green-aRestartQuotesTheRetriedReadingItReplaced.log`,
  `evidence/green-aRetriedRestartQuotesTheReadingItRetried.log`), and the #987 case they stand beside still green
  (`run#aRoundCutTwiceKeepsBothReadingsFindings`).

## 3. The falsification

- [x] 3.1 `tools/loop/tests/loop-mutations.sh:1391`: `1045.1` reverts the predicate to the name derivation, so the
  restart quotes the attempt before the retry, and must redden `run#aRestartQuotesTheRetriedReadingItReplaced`.
- [x] 3.2 `tools/loop/tests/loop-mutations.sh:1392`: `1045.2` drops the retry's capture, so a retried restart
  records the attempt before the restart, and must redden `run#aRetriedRestartQuotesTheReadingItRetried`.
  *Verified*: `tools/loop/tests/loop-mutations.sh --only '1045.1 1045.2'` reports `red-when-mutated: 2 ·
  not-noticed: 0 · restore-failures: 0` (`evidence/mutations.log`), and `--check` reports every record applies.

## 4. The record

- [x] 4.1 `openspec/specs/agent-issue-loop/spec.md`: the delta modifies "The two readings of a round the run cut
  twice are kept" — the first of the two readings is the report the run held when it named the second, handed in by
  the caller — with one scenario per shape of the bug.
- [x] 4.2 `tools/loop/evidence.sh` and `tools/loop/run.sh` state why at the sites they carry: the name answers
  *whether*, the caller answers *which* (issue #1045).
  *Verified*: the two declaration tables need no row — the three functions keep their files, names and callers — and
  the change touches none of the three tables, so no declaration check is owed a repair.

## 5. The gate

- [x] 5.1 `openspec archive --yes` the change with the delta it merges, then run the gate once over the archived
  tree and copy the XMLs it produced beside its log, as `evidence/gate.log` and the tally files.
- [x] 5.2 `tools/loop/evidence-check.sh openspec/changes/archive/<the archived change>` reports no FAIL before the
  gate ran, and `tools/check-change-readiness.sh` reported `LOOP-CHANGE-READY: ok` on the unarchived change.
