# Tasks — the census, and the decision it produces

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`). Gate for every task:
`bash tools/loop/tests/loop-selftest.sh --files tools/loop/census.sh tools/loop/tests/fixtures/` while iterating, one
full run before the change is complete. No Gradle build is owed (`guidelines/Build.md` §2, the scope block).

**This change has a stop task (2.1) and is expected to be able to end there.** It retires nothing — the retirements
are `the-unheld-guards-are-retired`, which this change's number decides.

## 1. The census

- [x] 1.1 `tools/loop/census.sh`: read the engine's guard sites and the issue numbers their comments cite (the
  parenthesised and the bare form, never free text), query the tracker once for those issues' states, and print the
  three counts plus a total; `--json` carries the lists. A tracker failure prints the patron count as unread with its
  reason and still exits 0. Satisfies `The census counts the guards no open issue and no case holds`.
- [x] 1.2 Cases `census#theCensusIsANumber`, `census#aClosedPatronWithNoCaseIsCounted`,
  `census#anUnreadCountIsNamed`: run against stub-`gh` fixtures (one closed item, one open, one failure). Each
  declares `# files: tools/loop/census.sh tools/loop/tests/fixtures/census/ tools/loop/tests/loop-cases.sh`.
- [x] 1.3 Cases `census#everyGuardNamesItsPatron`, `census#aGuardWithoutAPatronIsCounted`,
  `census#theCitationFormIsRead`: fixture engine files with each citation shape. Satisfies `A guard names the issue
  that patronises it`.
- [x] 1.4 Cases `census#theCensusRefusesNothing`, `census#aRunDoesNotConsultTheCensus`: assert the state directory and
  the tracker are untouched, and drive a run with the census made to fail to prove the run does not read it.
  Satisfies `The census refuses nothing and stops no run`.

## 2. The decision

- [x] 2.1 Run the census on the committed tree and record the three numbers with the date and the command in the
  change's evidence directory (`evidence/census-2026-11-16.log`: 94 guards, 4 closed patrons, 72 no-case, 90
  no-patron, 2 unheld). The same command, re-taken on this tree by the item's second worker process, printed the
  same five lines (`evidence/census-retake-2026-11-16.log`).
- [x] 2.2 **Stop and decide.** The recorded unheld share is 2 of 94 (2.1 %), below the 10 % threshold in
  `proposal.md`. The number and the decision are in the change's report; this change archives with the census as its
  whole deliverable, and `the-unheld-guards-are-retired` stays unstarted.

## 3. Documents

- [x] 3.1 `guidelines/Backlog.md` section 7 names the census, what its three numbers mean, and that a retirement needs a
  closed patron *and* no case carrying the refusal (`guidelines/Backlog.recipes.md` section 7d carries the procedure).
  Verified with `bash tools/check-doc-routes.sh`.
- [x] 3.2 `guidelines/BuildEvidence.md` §4f gains the census as a re-takeable measurement with its command.

## 4. Verification

- [x] 4.1 The affected cases are green (`evidence/census-cases-green.log`); the run's gate child owns the full gate
  over this delta, and this change does not run `tools/loop/gate.sh` itself.
- [x] 4.2 Revert-check for the unread-count rule: make the census print `0` where the tracker failed and
  `census#anUnreadCountIsNamed` failed; restored, forced green (`evidence/mutation-4.2.log`).
- [x] 4.3 Revert-check for the citation form: make the census count a bare number in prose as a patron and
  `census#theCitationFormIsRead` failed; restored, forced green (`evidence/mutation-4.3.log`).
- [x] 4.4 Revert-check for the run's independence: make `run.sh` print the census's number in its header and
  `census#aRunDoesNotConsultTheCensus` failed; restored, forced green (`evidence/mutation-4.4.log`).
- [x] 4.5 No unrelated defect was found; no issue was filed.
