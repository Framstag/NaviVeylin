# Tasks — fix-decision-cap-counts-candidates

- [x] 1. Red case first: `tools/loop/fixtures/scenarios/parked-decisions/issues.json` and
      `run#decisionCapCountsOnlyCandidates` in `tools/loop-cases.sh` — on the base tree the case fails on the
      refusal wording and on the parked item being printed (`evidence/red-on-HEAD.log`).
- [x] 2. `tools/loop/run.sh` step 6: count with `--eligible-only`, read the labelled total only on the refusal
      path, print `N of the M item(s) that await a decision are candidates, and the cap is X` and the candidate
      list.
- [x] 3. The case is green (`evidence/green.log`), and the neighbours that quote the refusal or read the
      `decisions` fixture stay green: `run#decisionCapRefusesToStart`,
      `run#theShapingInboxDoesNotCountAgainstTheCeiling`, `run#awaitingDecisionIsNotEscalatedAgain`,
      `prescreen#aHoldWithNoDecisionIsNotADecision`, `labels#closeRemovesTheDecisionLabel`.
- [x] 4. The case-registration checks: `--group driver` (including
      `driver#everyCaseDeclaresTheFilesItCanReach`) and the two mutation cases.
- [x] 5. Falsification: restore the base `tools/loop/run.sh` and the named case fails; restore the fix and it
      passes (`evidence/revert-check.log`).
- [x] 6. Spec delta restating *The cap is reached* and *The cap counts the questions a person can answer*;
      `openspec validate --strict` green.
- [x] 7. `guidelines/Backlog.md` §6 states that the ceiling counts candidates and what parking an item does.
- [x] 8. Archive the change into `openspec/changes/archive/` with its evidence.
