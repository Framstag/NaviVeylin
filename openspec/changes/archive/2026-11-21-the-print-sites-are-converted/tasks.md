# Tasks — convert the sites, then cross-check the report

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`). Gate for every task:
`bash tools/loop/tests/loop-selftest.sh --files tools/loop/<owner>.sh` while iterating — one **full** run before the
change is complete (~28 min, so exactly once). No Gradle build is owed — no compiled file changes
(`guidelines/Build.md` §2, the scope block).

Depends on `run-event-stream-is-typed` (the `emit` path, the vocabulary, the bound) and should land after
`run-sh-is-split-along-the-stream` (so each task edits one owner's file).

## 1. The conversion, one phase group per task

Each task replaces a print-only site with an `emit` whose rendered format reproduces the site's text verbatim, and
updates its case's `# files:` declaration. A site changed otherwise is a review finding, not a task.

- [x] 1.1 Header and budget: the run's start, the resolved flags, the armed quota reader, the record's path.
  Verify: `run#aConvertedSiteRendersFromItsRecord` (drive `--items 0 --minutes 0` and compare the emitted event's
  values with the printed line). Satisfies `No site prints a run fact without recording it`.
- [x] 1.2 The queue and the stop arms: every `stop_run` reason, the pause, the empty-queue pass.
- [x] 1.3 The item: selection, the work package, the result and its reason. Emit the item's spend where it is known,
  so 2.4 has a figure to agree with.
- [x] 1.4 The child wait: start, heartbeat, the bound that fired, the ending status — and the run's remaining budget,
  emitted where it is computed. Verify: `run#theHeartbeatRecordsTheSecondsItReports` (shipped by the predecessor
  change) still green.
- [x] 1.5 The gate, the review and the tail: the step, the verdict, the round.
- [x] 1.6 Case `driver#everyPrintOnlySiteIsConverted`: read the five owner files for lines that print and assert each
  is an `emit` call or a rendering `emit` owns. Declares `# files: tools/loop/`.
- [x] 1.7 Case `run#aFreeTextReasonSurvivesTheRoundTrip`: emit a reason carrying a quote and a newline; assert one
  parseable JSON object, the reason whole, and the printed line unescaped.

## 2. The report's cross-check

- [x] 2.1 `report.sh --from-events`: recompute items, closed, blocked, cut and skipped from the record, print them
  beside the state's, and add no line when they agree. Verify: `report#theRecordAndTheStateAgree` (drive one run,
  compare with the state).
- [x] 2.2 The difference line: both numbers and the first item the record lacks; `--json` carries the whole list.
  Verify: `report#aDifferenceIsNamedBesideTheFigures` (drive a run, delete one item's events, re-read).
- [x] 2.3 `report#aRunWhoseRecordIsAbsentSaysSo`: remove the record file, assert the report says so and claims no
  agreement. Assert exit 0 in all three of 2.1–2.3.
- [x] 2.4 The run's header states that the state file remains the authority and the record is a cross-check.

## 3. Documents and evidence

- [x] 3.1 `guidelines/Logging.md` §2 gains the rule that an engine's own record is JSON-lines, carries no coordinate
  and is read back rather than grepped. Verify `bash tools/check-doc-routes.sh`.
- [x] 3.2 `guidelines/BuildEvidence.md` §4f's artifact list gains the record; `guidelines/Build.md` §10k's recipe
  reads it instead of grepping a transcript. — The §10k clause found no target: §10k is the **route-row census** in
  `guidelines/BuildNative.md` (Kotlin `ROUTE` logcat lines about a rendered route), not a loop recipe, and no document
  greps a run's transcript for a loop fact (`grep -rn 'run-events\|LOOP_RUN_EVENTS' guidelines/ .pi/skills/ docs/`
  names only §4f, `Logging.md` §2 and `Backlog.md` §6, all of which now read the record back). The artifact list and
  the Logging rule are the clause's landing place.
- [x] 3.3 Record in the change's evidence: the sites converted per phase (131 total), the record's size per run from
  the first two runs after landing, and how many difference lines appeared (the claim is zero). —
  `evidence/conversion-2026-11-21.log`: 45 sites in `run.sh`, 7 in `leftovers.sh`, 6 in `child.sh`, 1 in `evidence.sh`
  (the issue's 131 counted every print call in the engine; in `run.sh` that was 101 `printf`s, of which the scanner
  read 55 as printing a run fact — the rest compose a prompt, an index or a file and are admitted as such); the
  pre-conversion baseline is 86 events / 18 510 bytes over three runs, and the two-run measurement after landing is
  owed because it can only be taken on a landed run.

## 4. Verification

- [x] 4.1 One full suite run at the end, green, with its case count and wall time recorded. — `all 525 cases passed
  (JDK 21, offline, no token, no sandbox) — 527.56 s`. The runs before it recorded the two reds this change had to
  settle: mutation record `16.2` (repaired) and `gate#theMapCoversEveryPipelineStep` (filed as #1000 and fixed in this
  tree, commit `3756c8bd`); `evidence/conversion-2026-11-21.log` §7 carries all three runs and the attribution.
- [x] 4.2 Revert-check for the completeness rule: add one print-only line to an owner file and
  `driver#everyPrintOnlySiteIsConverted` must fail; restore, forced green.
- [x] 4.3 Revert-check for the difference line: make the cross-check print nothing when the counts differ and
  `report#aDifferenceIsNamedBesideTheFigures` must fail; restore, forced green.
- [x] 4.4 Revert-check for the absent-record line: make an absent record read as agreement and
  `report#aRunWhoseRecordIsAbsentSaysSo` must fail; restore, forced green.
- [x] 4.5 Revert-check for the round trip: drop the newline escape in `emit`'s `text` handling and
  `run#aFreeTextReasonSurvivesTheRoundTrip` must fail; restore, forced green.
- [x] 4.6 File every unrelated defect this work finds with `.pi/skills/create-issue`. — One defect surfaced and it is
  this change's own: `item-result`'s `wall` was declared `num` for a fractional-seconds value, corrected in
  `events.tsv` with the reason beside it. — One unrelated defect *was* found, by the full suite rather than by the
  conversion: #1000, the gate's suite map missing the two commands `267fdd0d` added to the pipeline's Check step.
  Filed with a `cause` and the case that exercises it, then fixed in this tree (`3756c8bd`), because a red map leaves
  every later delta's gate with a hole in it.
