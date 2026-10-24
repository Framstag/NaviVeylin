# Tasks

- [x] 1.1 `tools/tooling.json` → `roles`: `workedClasses` gains `feature`, `refusedClasses` is empty,
      `firstCategoryClasses: ["bug"]` and `firstCategoryEvery: 4` are declared. `tools/fixtures/foreign-project`
      declares both new keys with its own values. Verify: `jq -e '.roles | has("firstCategoryClasses") and
      has("firstCategoryEvery")' tools/tooling.json` and the foreign profile's own `jq` check.
- [x] 1.2 `tools/loop/lib.sh` (`LOOP_ROLE_KEYS`) requires both keys, so a half-adapted profile stops.
      Verify: `prescreen#anUndeclaredLeadingIntervalStopsTheScreen`.
- [x] 1.3 `tools/loop/prescreen.sh` ranks three streams: leading-category defects first, the rest in class
      order, and one leading-category non-defect after every `firstCategoryEvery` item. Verify:
      `prescreen#harnessItemsLeadTheQueue`, `prescreen#oneHarnessImprovementPerFourAppItems`,
      `prescreen#aRenamedLeadingCategoryLeads`, `prescreen#priorityCategoriesAreReadFromTheProfile`.
- [x] 1.4 A feature is a queued class, and a profile that refuses it still refuses it. Verify:
      `prescreen#ranksBugsFirstAndKeepsFeatures`, `prescreen#aRefusedClassStillStopsTheScreen`.
- [x] 1.5 `tools/loop/run.sh` reads the item's `class/*` label and renders it as `{{CLASS}}`; the worker and
      reviewer prompts state the propose-first path. Verify: `prompt#workerCarriesTheFeaturePath`,
      `prompt#workerNamesEveryPhaseAndSkill`.
- [x] 1.6 The new eligibility and ranking rules are stated once: the shipped spec delta carries the contract
      and its cases, and the worker's copy of the eligibility table is `tools/loop/prompts/worker.md`.
      `.pi/skills/fix-loop` is reduced to a pointer table plus `scripts/evidence-check.sh` — it no longer
      restates the rules.
- [x] 1.7 Run the gate for the touched paths: `bash tools/loop-selftest.sh` (the `tools/loop/*` gate),
      `bash tools/issue-tooling-selftest.sh` (`tools/tooling.json`), `bash tools/check-doc-routes.sh` (`*.md`)
      and `openspec validate loop-harness-bugs-and-features --strict`. Record the tallies in `evidence/`.
- [x] 1.8 Falsification owed for the new invariant: remove the leader split in `prescreen.sh`, run
      `prescreen#harnessItemsLeadTheQueue`, confirm it reddens, restore, and confirm the case is green again.
