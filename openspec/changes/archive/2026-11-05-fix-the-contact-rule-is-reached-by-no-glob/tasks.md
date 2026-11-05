# Tasks

- [x] 1. Confirm the claim on the base `feff2198`. `bash tools/check-rule-routes.sh` reports one rule reached by no
  glob and exits 1 (`evidence/red-check-rule-routes.txt`); `bash tools/loop-selftest.sh --case
  'gate#theMapCoversEveryPipelineStep'` prints `PASS` (`evidence/base-part-1-already-fixed.txt`), so the item's
  first observation is #781's landing and owes nothing here.
- [x] 2. Add the rule to the route whose glob covers the phone details code: `tools/doc-routes.json`, route
  `app/src/main/java/**`, beside the section's other four names. Verify:
  `jq -r '.routes[0].rules[]' tools/doc-routes.json | grep -c 'UIPhone.md §5'` prints `6`, up from `5`.
- [x] 3. Add the same rule to the guideline route `guidelines/UI*.md`, which repeats the section's names so an edit
  to `UIPhone.md` derives them. Verify: `jq -r '.routes[27].rules[]' tools/doc-routes.json | grep 'Contact'` names
  the rule.
- [x] 4. The structural check this delta owes, run over the tree: `bash tools/check-rule-routes.sh` exits 0 with
  `all 579 named rule(s) are reached`, up from 578 (`evidence/green-check-rule-routes.txt`). It is the red case of
  this change — the check the pipeline's `Check the rule routes` step runs — and it flips on the two rows alone.
- [x] 5. Falsify it (`guidelines/BuildGate.md` §2a, the revert-check): remove the two added rows, re-run the check
  — `1 rule(s) are reached by no glob`, exit 1 — restore, re-run, green (`evidence/revert-check.txt`).
- [x] 6. Name the section the map derives for this change's own paths, so the change's reading set is not shrunk:
  `bash tools/check-rule-routes.sh --diff origin/main --change openspec/changes/fix-the-contact-rule-is-reached-by-no-glob`
  reports `BacklogTooling.md §9` and the proposal names it.
- [x] 7. The gate the delta's own path asks for, with nothing added by hand: `bash tools/loop/gate.sh --base
  origin/main` plans exactly three commands, all from the map's `tools/doc-routes.json` rows, and reports
  `LOOP-GATE: tests=13 failures=0 errors=0` (`evidence/gate-summary.txt`). No build: the delta touches no compiled
  file, no test source and no guideline text.
- [x] 8. Archive the change with its evidence, merge it into `main`, push, and close #789 naming what the first
  observation was already fixed by.
