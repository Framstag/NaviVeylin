# Tasks

- [x] 1. Confirm the claim on the base `51d7b695`. `bash tools/check-rule-routes.sh` reports six rules reached by
  no glob and exits 1 (`evidence/red-check-rule-routes.txt`); `tools/look.sh '<the six rule names>' --path
  guidelines` names the six `**Rule:` headings in `Backlog.md:176`, `MapCar.md:113`, `UICar.md:189`,
  `UICar.md:199`, `UI.md:97` and `UIPhone.md:40`, so each is a named rule of a section the map reaches.
- [x] 2. Add the `Backlog.md` §5 rule to route `guidelines/Backlog.*`, after the section's fifth name. Verify:
  `jq -r '.routes[] | select(.glob=="guidelines/Backlog.*") | .rules[]' tools/doc-routes.json | grep -c
  'the queue reads the whole labelled set'` prints `1`.
- [x] 3. Add the `MapCar.md` §14 rule to routes `app/src/main/java/**` and `guidelines/MapCar.*`, after
  `Diagnosing a frozen or degraded car map`. Verify: `jq -r '.routes[] | select(.glob=="guidelines/MapCar.*") |
  .rules[]' tools/doc-routes.json | grep -c 'app-drawn elements are the compass rose'` prints `1`, and the same
  over route `app/src/main/java/**` prints `1`.
- [x] 4. Add the two `UICar.md` §3c rules, the `UI.md` §8d rule and the `UIPhone.md` §5 rule to routes
  `app/src/main/java/**` and `guidelines/UI*.md`, each after its section's last sibling. Verify:
  `jq -r '.routes[] | select(.glob=="guidelines/UI*.md") | .rules[]' tools/doc-routes.json | grep -cE
  'the car..s reroute wait is a surface strip|the rows. words come from the surface|while navigation is active
  the wait is drawn|a failed reroute is reported on the map surface'` prints `4`, and the same over route
  `app/src/main/java/**` prints `4`.
- [x] 5. The structural check this delta owes, run over the tree: `bash tools/check-rule-routes.sh` exits 0 with
  `all 585 named rule(s) are reached`, up from 579 (`evidence/green-check-rule-routes.txt`). It is the red case
  for this change — the first of the three checks the map owes for this delta — and it flips on the eleven entries
  alone.
- [x] 6. Falsify it (the `revert-check` skill): remove the eleven entries, re-run the check — `6 rule(s) are
  reached by no glob`, exit 1 — restore them, re-run it green (`evidence/revert-check.txt`).
- [x] 7. Name the section the map derives for this change's own paths, so the change's reading set is not shrunk:
  `bash tools/check-rule-routes.sh --diff 51d7b695 --change
  openspec/changes/fix-six-rules-are-reached-by-no-glob` reports `BacklogTooling.md §9`, which the proposal
  names.
- [x] 8. Hand the applied diff over — the change writes no case, so no first-line case is owed, and the gate of
  this item is deferred to the run's own gate child.
