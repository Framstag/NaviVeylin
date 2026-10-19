# fix-loop-review-budget-and-decision — tasks

- [x] 1.1 `LOOP_MAX_REVIEW_ROUNDS_DEFAULT` in `loop.conf`; `run.sh --max-review-rounds N`; recorded as
      `run.maxReviewRounds` by `state.sh init`; printed by `report.sh` and `state.sh show`.
- [x] 1.2 A case asserting the run takes the rounds it was budgeted and records the budget; red on HEAD
      (the literal 2).
- [x] 1.3 Zero rounds refused in both `run.sh` and `state.sh`, with a case and a mutation (20.4).
- [x] 2.1 `LOOP-CORRECTIONS-ONLY: yes` earns one more round, once; a second refusal blocks. Case
      `run#aCorrectiveRefusalEarnsOneMoreRound`; mutation 19.2.
- [x] 2.2 `LOOP-DECISION` rendered into the escalation as `**The decision:**`; case
      `run#escalationCarriesTheDecision`; mutation 19.3.
- [x] 2.3 The reviewer prompt asks for both fields and states what each must contain.
- [x] 3.1 Mutations 19.1, 19.2, 19.3, 20.4: four red, four green after the restore.
- [x] 4.1 The spec's "Review rounds are bounded" scenario replaced and the report scenario's decision
      sources extended; `SKILL.md` usage, flag table and failure table updated.
- [x] 4.2 `check-doc-routes` 89/89, `check-portability` OK.
